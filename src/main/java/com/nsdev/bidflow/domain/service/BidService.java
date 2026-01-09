package com.nsdev.bidflow.domain.service;

import com.nsdev.bidflow.domain.dto.BidRequest;
import com.nsdev.bidflow.domain.exception.AuctionClosedException;
import com.nsdev.bidflow.domain.exception.AuctionNotFoundException;
import com.nsdev.bidflow.domain.exception.BidProcessingException;
import com.nsdev.bidflow.domain.exception.InvalidBidAmountException;
import com.nsdev.bidflow.domain.model.Auction;
import com.nsdev.bidflow.domain.model.AuctionStatus;
import com.nsdev.bidflow.domain.model.Bid;
import com.nsdev.bidflow.domain.port.BidEventPublisher;
import com.nsdev.bidflow.domain.repository.AuctionRepository;
import com.nsdev.bidflow.domain.repository.BidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Core domain service responsible for orchestrating the bidding process.
 *
 * <p>This service implements a <b>Pessimistic Locking strategy</b> using Redis (Redisson)
 * to ensure data integrity across a distributed cluster. It guarantees that race conditions
 * (e.g., "Double Spending" or concurrent highest bids) are impossible, even under high load.
 *
 * <p><b>Concurrency & Transaction Strategy:</b>
 * Unlike traditional Spring services, this class deliberately avoids {@code @Transactional}
 * at the {@code placeBid} method level. Instead, it uses a finer-grained approach:
 * <ol>
 * <li>Acquire Distributed Lock (Network I/O)</li>
 * <li>Open DB Transaction (Connection Pool)</li>
 * <li>Validate & Persist</li>
 * <li>Commit Transaction</li>
 * <li>Release Lock</li>
 * </ol>
 * This prevents threads waiting for locks from holding onto valuable JDBC connections,
 * avoiding Connection Pool Starvation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final RedissonClient redissonClient;
    private final BidEventPublisher bidEventPublisher;
    private final TransactionTemplate transactionTemplate;

    private static final long LOCK_WAIT_TIME_MS = 500;
    private static final long LOCK_LEASE_TIME_MS = 5000;

    /**
     * Processes a new bid for a specific auction efficiently and safely.
     *
     * <p>This is the entry point for all write operations regarding bids.
     * It enforces the distributed lock boundary before delegating to the transactional layer.
     *
     * @param request The DTO containing the auction ID, bidder identity, and bid amount.
     * @return The fully persisted {@link Bid} entity.
     * @throws AuctionNotFoundException  if the target auction does not exist.
     * @throws AuctionClosedException    if the auction status is not OPEN or the time window has expired.
     * @throws InvalidBidAmountException if the bid amount is not strictly higher than the current max.
     * @throws BidProcessingException    if the distributed lock cannot be acquired (system under heavy load).
     */
    public Bid placeBid(BidRequest request) {
        String lockKey = String.format("auction:lock:%d", request.auctionId());
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Attempt to acquire the lock. Fail fast if the system is overloaded.
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME_MS, LOCK_LEASE_TIME_MS, TimeUnit.MILLISECONDS);
            if (!isLocked) {
                log.warn("High contention detected for AuctionID: {}. Bidder: {} rejected.",
                        request.auctionId(), request.bidderUsername());
                throw new BidProcessingException("System is currently busy processing other bids. Please try again.");
            }

            // Lock acquired -> Execute Transactional Logic
            Bid persistedBid = transactionTemplate.execute(status -> processBidTransaction(request));

            // Publish event strictly AFTER transaction commit (best effort)
            if (persistedBid != null) {
                bidEventPublisher.publishNewBid(persistedBid);
            }
            return persistedBid;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while waiting for lock on AuctionID: {}", request.auctionId(), e);
            throw new BidProcessingException("Bid processing was interrupted.", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Encapsulates the transactional boundary for bid processing.
     *
     * <p>This method runs inside a database transaction context provided by {@link TransactionTemplate}.
     * It performs the "Read-Modify-Write" cycle:
     * <ul>
     * <li>Reads the latest auction state (locked via Redis previously).</li>
     * <li>Validates business rules (Timing, Amount).</li>
     * <li>Updates the auction's highest bid.</li>
     * <li>Persists the new bid ledger entry.</li>
     * </ul>
     *
     * @param request The bid data.
     * @return The persisted entity.
     */
    private Bid processBidTransaction(BidRequest request) {
        Auction auction = auctionRepository.findById(request.auctionId())
                .orElseThrow(() -> new AuctionNotFoundException(request.auctionId()));

        validateAuctionEligibility(auction);
        validateBidAmount(auction, request);

        Bid newBid = Bid.builder()
                .auction(auction)
                .bidderUsername(request.bidderUsername())
                .amount(request.amount())
                .build();

        // Update Aggregate Root
        auction.setCurrentHighestBid(request.amount());

        bidRepository.save(newBid);
        auctionRepository.save(auction);

        log.info("Bid persisted: AuctionID={} | User={} | Amount={}",
                auction.getId(), request.bidderUsername(), request.amount());

        return newBid;
    }

    /**
     * Validates if the auction is legally open for bidding.
     * Checks both the status enum and the start/end timestamps.
     *
     * @param auction The auction entity to check.
     * @throws AuctionClosedException if any condition fails.
     */
    private void validateAuctionEligibility(Auction auction) {
        if (auction.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionClosedException(String.format("Auction %d is currently %s.",
                    auction.getId(), auction.getStatus()));
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(auction.getStartTime())) {
            throw new AuctionClosedException(String.format("Auction %d has not started yet. Starts at: %s",
                    auction.getId(), auction.getStartTime()));
        }

        if (now.isAfter(auction.getEndTime())) {
            throw new AuctionClosedException(String.format("Auction %d has already ended at: %s",
                    auction.getId(), auction.getEndTime()));
        }
    }

    /**
     * Validates if the proposed bid amount follows the increments rules.
     *
     * @param auction The auction entity.
     * @param request The incoming bid.
     * @throws InvalidBidAmountException if the amount is too low.
     */
    private void validateBidAmount(Auction auction, BidRequest request) {
        if (auction.getCurrentHighestBid() == null) {
            if (request.amount().compareTo(auction.getStartingPrice()) < 0) {
                throw new InvalidBidAmountException(String.format("Bid amount %.2f is lower than starting price %.2f",
                        request.amount(), auction.getStartingPrice()));
            }
            return;
        }

        if (request.amount().compareTo(auction.getCurrentHighestBid()) <= 0) {
            throw new InvalidBidAmountException(String.format("Bid amount %.2f must be higher than current highest bid %.2f",
                    request.amount(), auction.getCurrentHighestBid()));
        }
    }
}
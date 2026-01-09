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
     * Processes a new bid for a specific auction ensuring data consistency under high concurrency.
     * <p>
     * Implementation Detail:
     * This method deliberately avoids @Transactional at the method level.
     * We acquire the Distributed Lock (Redis) FIRST, and only then open the Database Transaction.
     * This prevents threads waiting for the lock from hogging JDBC connections (Connection Pool Exhaustion).
     *
     * @param request The DTO containing auction ID, bidder info, and amount.
     * @return The persisted Bid entity.
     * @throws AuctionNotFoundException  if the auction does not exist.
     * @throws AuctionClosedException    if the auction is not OPEN or the time window has expired.
     * @throws InvalidBidAmountException if the bid amount is not higher than the current max.
     * @throws BidProcessingException    if the system is under heavy load or interrupted.
     */
    public Bid placeBid(BidRequest request) {
        String lockKey = String.format("auction:lock:%d", request.auctionId());
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME_MS, LOCK_LEASE_TIME_MS, TimeUnit.MILLISECONDS);
            if (!isLocked) {
                log.warn("High contention detected for AuctionID: {}. Bidder: {} rejected.",
                        request.auctionId(), request.bidderUsername());
                throw new BidProcessingException("System is currently busy processing other bids. Please try again.");
            }

            Bid persistedBid = transactionTemplate.execute(status -> processBidTransaction(request));
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
     * Encapsulates the transactional logic (Read -> Validate -> Write).
     * This runs inside the database transaction context.
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

        auction.setCurrentHighestBid(request.amount());

        bidRepository.save(newBid);
        auctionRepository.save(auction);

        log.info("Bid persisted: AuctionID={} | User={} | Amount={}",
                auction.getId(), request.bidderUsername(), request.amount());

        return newBid;
    }

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
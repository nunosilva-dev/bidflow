package com.nsdev.bidflow.infra.bootstrap;

import com.nsdev.bidflow.domain.model.Auction;
import com.nsdev.bidflow.domain.model.AuctionStatus;
import com.nsdev.bidflow.domain.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!prod")
public class DemoDataSeeder implements CommandLineRunner {

    private final AuctionRepository auctionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (auctionRepository.count() > 0) {
            log.info("Database already populated. Skipping seeding.");
            return;
        }
        log.info("Seeding database with demo auction data...");

        LocalDateTime now = LocalDateTime.now();

        Auction smartphoneAuction = Auction.builder()
                .title("iPhone 16 Pro Max - Prototype")
                .startingPrice(new BigDecimal("999.00"))
                .currentHighestBid(null)
                .status(AuctionStatus.OPEN)
                .startTime(now.minusHours(1))
                .endTime(now.plusHours(24))
                .build();

        Auction vintageCarAuction = Auction.builder()
                .title("1967 Ford Mustang Shelby GT500")
                .startingPrice(new BigDecimal("150000.00"))
                .currentHighestBid(new BigDecimal("220000.00"))
                .status(AuctionStatus.CLOSED)
                .startTime(now.minusDays(7))
                .endTime(now.minusDays(1))
                .build();

        Auction futureSpaceTrip = Auction.builder()
                .title("SpaceX Ticket to Mars")
                .startingPrice(new BigDecimal("500000.00"))
                .currentHighestBid(null)
                .status(AuctionStatus.OPEN) // Status is open, but time prevents bidding
                .startTime(now.plusDays(5))
                .endTime(now.plusDays(10))
                .build();

        auctionRepository.saveAll(List.of(smartphoneAuction, vintageCarAuction, futureSpaceTrip));

        log.info("Database seeding completed successfully. Created 3 auctions.");
    }
}
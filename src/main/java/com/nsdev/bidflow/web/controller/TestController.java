package com.nsdev.bidflow.web.controller;

import com.nsdev.bidflow.domain.model.Auction;
import com.nsdev.bidflow.domain.repository.AuctionRepository;
import com.nsdev.bidflow.domain.repository.BidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/test/reset")
@RequiredArgsConstructor
@Slf4j
@Profile("!prod")
public class TestController {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    @PostMapping
    @Transactional
    public ResponseEntity<String> resetData() {
        log.warn("⚠️ EXECUTING DATA RESET via Test Controller");

        bidRepository.deleteAll();

        List<Auction> auctions = auctionRepository.findAll();
        for (Auction auction : auctions) {
            auction.setCurrentHighestBid(null);
        }
        auctionRepository.saveAll(auctions);

        return ResponseEntity.ok("Database reset: All bids deleted and auctions reset.");
    }
}
package com.nsdev.bidflow.domain.service;

import com.nsdev.bidflow.domain.dto.CreateAuctionRequest;
import com.nsdev.bidflow.domain.model.Auction;
import com.nsdev.bidflow.domain.model.AuctionStatus;
import com.nsdev.bidflow.domain.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuctionWriteService {

    private final AuctionRepository auctionRepository;

    @Transactional
    public Auction createAuction(CreateAuctionRequest request) {
        if (request.endTime().isBefore(request.startTime())) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        Auction auction = Auction.builder()
                .title(request.title())
                .startingPrice(request.startingPrice())
                .status(AuctionStatus.OPEN)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();

        return auctionRepository.save(auction);
    }
}
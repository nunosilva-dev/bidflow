package com.nsdev.bidflow.web.dto;

import com.nsdev.bidflow.domain.model.Auction;
import com.nsdev.bidflow.domain.model.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionResponse(
        Long id,
        String title,
        BigDecimal currentPrice,
        AuctionStatus status,
        LocalDateTime endTime
) {
    public static AuctionResponse fromEntity(Auction auction) {
        return new AuctionResponse(
                auction.getId(),
                auction.getTitle(),
                auction.getCurrentHighestBid() != null ? auction.getCurrentHighestBid() : auction.getStartingPrice(),
                auction.getStatus(),
                auction.getEndTime()
        );
    }
}
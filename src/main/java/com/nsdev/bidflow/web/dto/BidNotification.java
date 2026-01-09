package com.nsdev.bidflow.web.dto;

import com.nsdev.bidflow.domain.model.Bid;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BidNotification(
        Long auctionId,
        String bidderUsername,
        BigDecimal amount,
        LocalDateTime timestamp
) {
    public static BidNotification from(Bid bid) {
        return new BidNotification(
                bid.getAuction().getId(),
                bid.getBidderUsername(),
                bid.getAmount(),
                bid.getCreatedAt()
        );
    }
}
package com.nsdev.bidflow.domain.dto;

import java.math.BigDecimal;

public record BidRequest(
        Long auctionId,
        String bidderUsername,
        BigDecimal amount
) {
}
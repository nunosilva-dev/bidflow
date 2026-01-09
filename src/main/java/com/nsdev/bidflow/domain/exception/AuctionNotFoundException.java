package com.nsdev.bidflow.domain.exception;

public class AuctionNotFoundException extends RuntimeException {
    public AuctionNotFoundException(Long auctionId) {
        super(String.format("Auction with ID %d not found.", auctionId));
    }
}
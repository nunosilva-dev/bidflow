package com.nsdev.bidflow.domain.exception;

public class BidProcessingException extends RuntimeException {
    public BidProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public BidProcessingException(String message) {
        super(message);
    }
}
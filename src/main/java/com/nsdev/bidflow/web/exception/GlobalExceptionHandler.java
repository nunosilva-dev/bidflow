package com.nsdev.bidflow.web.exception;

import com.nsdev.bidflow.domain.exception.AuctionClosedException;
import com.nsdev.bidflow.domain.exception.AuctionNotFoundException;
import com.nsdev.bidflow.domain.exception.BidProcessingException;
import com.nsdev.bidflow.domain.exception.InvalidBidAmountException;
import com.nsdev.bidflow.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * Centralized exception handler for the REST API layer.
 *
 * <p>This component acts as an interceptor (AOP) for exceptions thrown by any controller.
 * Its primary responsibility is to translate Domain Exceptions (Business Logic failures)
 * into standardized, predictable HTTP responses conforming to a uniform JSON structure.
 *
 * <p><b>Key Responsibilities:</b>
 * <ul>
 * <li>Maps Domain Exceptions to appropriate HTTP Status Codes (404, 400, 503).</li>
 * <li>Masks internal server errors (500) to prevent leaking stack traces to clients.</li>
 * <li>Provides consistent timestamp and path information for debugging.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles cases where a requested resource does not exist.
     * Maps {@link AuctionNotFoundException} to <b>HTTP 404 Not Found</b>.
     */
    @ExceptionHandler(AuctionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(AuctionNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Handles business rule violations caused by client input.
     * Maps domain logic failures to <b>HTTP 400 Bad Request</b>.
     *
     * <p>Covered scenarios:
     * <ul>
     * <li>Bid amount is lower than current highest bid.</li>
     * <li>Bidding on a closed or not-yet-started auction.</li>
     * <li>Malformed arguments.</li>
     * </ul>
     */
    @ExceptionHandler({
            InvalidBidAmountException.class,
            AuctionClosedException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Handles transient system failures due to high concurrency.
     * Maps {@link BidProcessingException} to <b>HTTP 503 Service Unavailable</b>.
     *
     * <p><b>Architectural Significance:</b>
     * This explicitly signals to the client that the request was valid, but the
     * Distributed Lock could not be acquired due to contention.
     * Clients (or Load Balancers) can interpret the 503 status code as a signal
     * to apply a retry strategy (e.g., Exponential Backoff).
     */
    @ExceptionHandler(BidProcessingException.class)
    public ResponseEntity<ErrorResponse> handleProcessingError(BidProcessingException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    /**
     * Catch-all handler for unexpected internal errors.
     * Maps unknown exceptions to <b>HTTP 500 Internal Server Error</b>.
     *
     * <p>The error message is sanitized ("An unexpected error occurred") to avoid
     * exposing sensitive implementation details or stack traces to the public API.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    /**
     * Helper method to construct the standardized {@link ErrorResponse} DTO.
     *
     * @param status  The HTTP Status to return.
     * @param message The user-friendly error message.
     * @param request The current HTTP request (used to extract the URI path).
     * @return A ResponseEntity containing the structured error body.
     */
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(errorResponse);
    }
}
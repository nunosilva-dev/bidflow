package com.nsdev.bidflow.web.socket;

import com.nsdev.bidflow.domain.dto.BidRequest;
import com.nsdev.bidflow.domain.service.BidService;
import com.nsdev.bidflow.web.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BidWebSocketController {

    private final BidService bidService;

    /**
     * Entry point for WebSocket bids.
     * Client sends STOMP SEND to: /app/bid
     */
    @MessageMapping("/bid")
    public void handleBid(@Payload BidRequest request, Principal principal) {
        log.debug("Received WebSocket bid from user: {}", principal != null ? principal.getName() : "Anonymous");
        bidService.placeBid(request);
    }

    /**
     * Specific WebSockets exception handler.
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public ErrorResponse handleException(Exception ex) {
        log.warn("WebSocket Bid Error: {}", ex.getMessage());
        return new ErrorResponse(
                LocalDateTime.now(),
                400,
                "Bad Request",
                ex.getMessage(),
                "/app/bid"
        );
    }
}
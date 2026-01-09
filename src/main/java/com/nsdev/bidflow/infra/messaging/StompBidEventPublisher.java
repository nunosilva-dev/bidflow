package com.nsdev.bidflow.infra.messaging;

import com.nsdev.bidflow.domain.model.Bid;
import com.nsdev.bidflow.domain.port.BidEventPublisher;
import com.nsdev.bidflow.web.dto.BidNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompBidEventPublisher implements BidEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @Value("${bidflow.ws.broker-prefix:/topic}")
    private String brokerPrefix;

    @Override
    public void publishNewBid(Bid bid) {
        String destination = String.format("%s/auctions/%d", brokerPrefix, bid.getAuction().getId());
        BidNotification notification = BidNotification.from(bid);
        messagingTemplate.convertAndSend(destination, notification);
        log.debug("Broadcasted bid to topic: {}", destination);
    }
}
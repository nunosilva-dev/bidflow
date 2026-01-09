package com.nsdev.bidflow.infra.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nsdev.bidflow.web.dto.BidNotification;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Bridges Redis Pub/Sub messages to STOMP WebSocket destinations.
 *
 * <p>This component listens to Redis messages published as JSON strings,
 * deserializes them into {@link BidNotification} objects, and forwards them
 * to WebSocket clients via {@link SimpMessagingTemplate}.
 *
 * <p>This class acts as an infrastructure boundary:
 * <ul>
 *   <li>Redis handles message distribution</li>
 *   <li>Jackson handles explicit JSON deserialization</li>
 *   <li>STOMP handles client delivery</li>
 * </ul>
 *
 * <p>Using String-based messages ensures compatibility and avoids
 * polymorphic deserialization issues across services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisToStompRelay {

    private final RedissonClient redissonClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * STOMP broker destination prefix (e.g. /topic).
     */
    @Value("${bidflow.ws.broker-prefix:/topic}")
    private String brokerPrefix;

    /**
     * Redis Pub/Sub channel used to receive bid updates.
     */
    private static final String REDIS_CHANNEL = "bidflow-global-updates";

    /**
     * Starts listening to the Redis Pub/Sub channel after bean initialization.
     *
     * <p>Each received JSON message is deserialized into a
     * {@link BidNotification} and forwarded to a WebSocket destination
     * scoped by auction ID.
     */
    @PostConstruct
    public void startListening() {
        RTopic topic = redissonClient.getTopic(REDIS_CHANNEL);

        topic.addListener(String.class, (channel, jsonPayload) -> {
            try {
                log.debug("Received JSON from Redis: {}", jsonPayload);
                BidNotification notification = objectMapper.readValue(jsonPayload, BidNotification.class);
                String websocketDestination = String.format("%s/auctions/%d", brokerPrefix, notification.auctionId());
                messagingTemplate.convertAndSend(websocketDestination, notification);
            } catch (Exception e) {
                log.error("Failed to deserialize or forward message from Redis: {}", jsonPayload, e);
            }
        });
        log.info("Redis Pub/Sub → STOMP relay started. Channel: {}", REDIS_CHANNEL);
    }
}

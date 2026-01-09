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
 * Relay component that bridges the Redis Pub/Sub layer to the WebSocket (STOMP) layer.
 *
 * <p>This component acts as a cluster-aware listener. It subscribes to global
 * Redis events, deserializes the JSON payloads, and routes them to the
 * local WebSocket sessions connected to this specific instance.
 *
 * <p><b>Data Flow:</b>
 * <ol>
 * <li>Redis Topic {@code bidflow-global-updates} receives a JSON string.</li>
 * <li>This relay deserializes it into a {@link BidNotification}.</li>
 * <li>The notification is forwarded to the STOMP destination {@code /topic/auctions/{id}}.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisToStompRelay {

    private final RedissonClient redissonClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Value("${bidflow.ws.broker-prefix:/topic}")
    private String brokerPrefix;

    private static final String REDIS_CHANNEL = "bidflow-global-updates";

    /**
     * Initializes the Redis listener on startup.
     *
     * <p>The listener is fault-tolerant: malformed JSON or deserialization exceptions
     * are caught and logged, ensuring the subscription remains active for subsequent messages.
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
                log.error("Failed to relay message from Redis. Payload: {}", jsonPayload, e);
            }
        });
        log.info("Redis Pub/Sub → STOMP Relay active. Listening on channel: {}", REDIS_CHANNEL);
    }
}
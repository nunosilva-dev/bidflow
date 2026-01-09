package com.nsdev.bidflow.infra.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nsdev.bidflow.domain.model.Bid;
import com.nsdev.bidflow.domain.port.BidEventPublisher;
import com.nsdev.bidflow.web.dto.BidNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * Publishes bid-related domain events to Redis using Pub/Sub.
 *
 * <p>This implementation serializes {@link BidNotification} objects explicitly
 * into JSON strings and publishes them using Redisson {@link RTopic} with
 * {@link org.redisson.client.codec.StringCodec}.
 *
 * <p>Design rationale:
 * <ul>
 *   <li>Avoids Jackson polymorphic typing issues (@class)</li>
 *   <li>Ensures compatibility across services and versions</li>
 *   <li>Uses Redis purely as a transport layer</li>
 * </ul>
 *
 * <p>The published messages are later consumed by a Redis-to-STOMP relay
 * that forwards them to WebSocket clients.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompBidEventPublisher implements BidEventPublisher {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    /**
     * Redis Pub/Sub channel used to broadcast bid updates globally.
     */
    private static final String REDIS_CHANNEL = "bidflow-global-updates";

    /**
     * Publishes a newly placed bid to Redis as a JSON string.
     *
     * <p>The message is serialized manually to ensure full control over the
     * payload format and to avoid codec-level deserialization issues.
     *
     * @param bid the domain {@link Bid} that was created
     */
    @Override
    public void publishNewBid(Bid bid) {
        try {
            BidNotification notification = BidNotification.from(bid);
            String jsonPayload = objectMapper.writeValueAsString(notification);

            RTopic topic = redissonClient.getTopic(REDIS_CHANNEL);
            long clientsReceived = topic.publish(jsonPayload);

            log.debug(
                    "Broadcasted bid via Redis ({} subscriber(s)): {}",
                    clientsReceived,
                    jsonPayload
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize bid notification", e);
        }
    }
}

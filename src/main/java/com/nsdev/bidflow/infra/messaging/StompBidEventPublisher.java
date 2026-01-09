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
 * Publishes bid-related domain events to the Redis Cluster using Pub/Sub.
 *
 * <p>This implementation explicitly serializes {@link BidNotification} objects
 * into JSON strings before publishing via Redisson's {@link RTopic}.
 *
 * <p><b>Design Rationale:</b>
 * <ul>
 * <li><b>Decoupling:</b> Uses Redis strictly as a transport layer, avoiding Redisson's internal codecs.</li>
 * <li><b>Compatibility:</b> "Plain JSON" strings eliminate polymorphic typing issues (missing {@code @class}) and facilitate consumption by non-Java services.</li>
 * <li><b>Control:</b> Allows granular configuration of the JSON payload via the application's {@link ObjectMapper}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompBidEventPublisher implements BidEventPublisher {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    /**
     * The dedicated Redis Pub/Sub channel for global bid broadcasts.
     */
    private static final String REDIS_CHANNEL = "bidflow-global-updates";

    /**
     * Publishes a newly placed bid to Redis as a serialized JSON string.
     *
     * <p>The process handles serialization manually to ensure a schema-compliant
     * JSON payload. Any serialization errors are logged and suppressed to prevent
     * impacting the upstream transaction context.
     *
     * @param bid the persisted domain {@link Bid} entity to be broadcast.
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
            log.error("Failed to serialize bid notification for Auction ID: {}", bid.getAuction().getId(), e);
        }
    }
}
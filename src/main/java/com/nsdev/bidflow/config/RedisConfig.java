package com.nsdev.bidflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central configuration for Redis infrastructure and JSON Serialization.
 *
 * <p>This configuration enforces a <b>String-based transport strategy</b> for Redis,
 * deliberately bypassing Redisson's default object serialization to ensure
 * robustness in a distributed environment.
 *
 * <p><b>Key Architecture Decisions:</b>
 * <ul>
 * <li><b>StringCodec:</b> Forces Redisson to treat data as plain text, delegating serialization logic to the application layer.</li>
 * <li><b>No Polymorphism:</b> Disables default typing to prevent {@code InvalidTypeIdException} across service restarts or version mismatches.</li>
 * <li><b>Explicit Mapping:</b> Centralizes JSON rules in a custom {@link ObjectMapper}.</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Creates a {@link RedissonClient} configured for single-server Redis
     * using the {@link StringCodec}.
     *
     * @return a configured {@link RedissonClient} instance.
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
        // Enforce String-only communication to avoid ClassCast/TypeID issues
        config.setCodec(StringCodec.INSTANCE);
        return Redisson.create(config);
    }
}
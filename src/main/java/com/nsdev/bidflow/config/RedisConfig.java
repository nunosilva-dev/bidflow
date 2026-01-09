package com.nsdev.bidflow.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Central Redis and Jackson configuration.
 *
 * <p>This configuration intentionally uses {@link StringCodec} for Redis
 * communication, treating Redis as a message transport rather than a
 * serialization layer.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>No Jackson default typing</li>
 *   <li>No polymorphic deserialization</li>
 *   <li>Explicit JSON serialization at application boundaries</li>
 * </ul>
 *
 * <p>This avoids common issues with incompatible codecs, class metadata
 * (@class), and cross-service deserialization failures.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Creates a {@link RedissonClient} configured for single-server Redis
     * using {@link StringCodec}.
     *
     * @return a configured {@link RedissonClient}
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
        config.setCodec(StringCodec.INSTANCE);
        return Redisson.create(config);
    }

    /**
     * Primary {@link ObjectMapper} used across the application.
     *
     * <p>Configured for:
     * <ul>
     *   <li>Java time support</li>
     *   <li>ISO-8601 date serialization</li>
     *   <li>Graceful handling of unknown fields</li>
     *   <li>Exclusion of null values</li>
     * </ul>
     *
     * @return a customized {@link ObjectMapper}
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}

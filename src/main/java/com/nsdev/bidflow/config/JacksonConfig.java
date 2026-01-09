package com.nsdev.bidflow.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    /**
     * Provides the primary {@link ObjectMapper} for the application.
     *
     * <p>Configuration highlights:
     * <ul>
     * <li><b>JSR-310:</b> Native support for {@code LocalDateTime}.</li>
     * <li><b>ISO-8601:</b> Dates formatted as standard strings, not timestamps.</li>
     * <li><b>Robustness:</b> Ignores unknown properties to prevent failures on future schema expansions.</li>
     * <li><b>Efficiency:</b> Excludes null values to reduce payload size.</li>
     * </ul>
     *
     * @return a fully customized {@link ObjectMapper}.
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

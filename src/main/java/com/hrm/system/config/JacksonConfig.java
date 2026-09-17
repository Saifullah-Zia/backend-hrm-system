package com.hrm.system.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // ── JSR-310: support LocalDate, LocalDateTime, ZonedDateTime, etc. ──
        mapper.registerModule(new JavaTimeModule());

        // Serialize dates as ISO-8601 strings, not timestamps (arrays)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Tolerate extra fields from newer API versions or DTOs
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Don't blow up on empty POJOs
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        return mapper;
    }
}
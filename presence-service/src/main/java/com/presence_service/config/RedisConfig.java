package com.presence_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration.
 *
 * Key issue fixed: the default GenericJackson2JsonRedisSerializer creates an
 * ObjectMapper WITHOUT JavaTimeModule, which causes:
 *   InvalidDefinitionException: Java 8 date/time type java.time.Instant
 *   not supported by default
 *
 * Fix: provide a custom ObjectMapper with:
 *   - JavaTimeModule registered  (handles Instant, LocalDate, etc.)
 *   - WRITE_DATES_AS_TIMESTAMPS disabled  (store as ISO string, not epoch array)
 *   - Default typing enabled  (stores @class so Redis can deserialize back to PresenceRecord)
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        GenericJackson2JsonRedisSerializer serializer = buildSerializer();

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    private GenericJackson2JsonRedisSerializer buildSerializer() {
        ObjectMapper mapper = new ObjectMapper();

        // ── Register JavaTimeModule so Instant is serialized correctly ──
        mapper.registerModule(new JavaTimeModule());

        // Store Instant as ISO-8601 string (e.g. "2026-04-26T09:30:00Z")
        // NOT as a [seconds, nanos] array — easier to read in Redis CLI
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ── Enable default typing so Redis knows how to deserialize back ──
        // This adds "@class" field to the JSON — GenericJackson2Json needs it.
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}

package com.analytics.shared.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Shared Redis configuration for all microservices
 *
 * Provides connection factories and templates for:
 * - Caching (port 6379)
 * - Pub/Sub (port 6383)
 */
@Configuration
public class RedisConfig {

    // Caching Redis Configuration
    @Primary
    @Bean(name = "cacheRedisConnectionFactory")
    public RedisConnectionFactory cacheRedisConnectionFactory(
            @Value("${spring.redis.cache.host}") String host,
            @Value("${spring.redis.cache.port}") int port) {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Primary
    @Bean(name = "cacheRedisTemplate")
    public RedisTemplate<String, Object> cacheRedisTemplate(
            RedisConnectionFactory cacheRedisConnectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(cacheRedisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    // Pub/Sub Redis Configuration
    @Bean(name = "pubSubRedisConnectionFactory")
    public RedisConnectionFactory pubSubRedisConnectionFactory(
            @Value("${spring.redis.pubsub.host}") String host,
            @Value("${spring.redis.pubsub.port}") int port) {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "pubSubRedisTemplate")
    public RedisTemplate<String, Object> pubSubRedisTemplate(
            RedisConnectionFactory pubSubRedisConnectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(pubSubRedisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}

package com.analytics.flight.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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
            @Qualifier("cacheRedisConnectionFactory") RedisConnectionFactory factory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    // Session Store Redis Configuration
    @Bean(name = "sessionRedisConnectionFactory")
    public RedisConnectionFactory sessionRedisConnectionFactory(
            @Value("${spring.redis.session.host}") String host,
            @Value("${spring.redis.session.port}") int port) {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "sessionRedisTemplate")
    public RedisTemplate<String, Object> sessionRedisTemplate(
            @Qualifier("sessionRedisConnectionFactory") RedisConnectionFactory factory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    // Rate Limiting Redis Configuration
    @Bean(name = "rateLimitRedisConnectionFactory")
    public RedisConnectionFactory rateLimitRedisConnectionFactory(
            @Value("${spring.redis.ratelimit.host}") String host,
            @Value("${spring.redis.ratelimit.port}") int port) {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "rateLimitRedisTemplate")
    public StringRedisTemplate rateLimitRedisTemplate(
            @Qualifier("rateLimitRedisConnectionFactory") RedisConnectionFactory factory) {

        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        return template;
    }

    // Analytics Redis Configuration
    @Bean(name = "analyticsRedisConnectionFactory")
    public RedisConnectionFactory analyticsRedisConnectionFactory(
            @Value("${spring.redis.analytics.host}") String host,
            @Value("${spring.redis.analytics.port}") int port) {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "analyticsRedisTemplate")
    public RedisTemplate<String, Object> analyticsRedisTemplate(
            @Qualifier("analyticsRedisConnectionFactory") RedisConnectionFactory factory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
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
            @Qualifier("pubSubRedisConnectionFactory") RedisConnectionFactory factory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}

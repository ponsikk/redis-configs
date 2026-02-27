package com.analytics.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    // Rate Limiting Redis (port 6381)
    @Bean(name = "rateLimitRedisConnectionFactory")
    public RedisConnectionFactory rateLimitRedisConnectionFactory(
            @Value("${spring.redis.ratelimit.host}") String host,
            @Value("${spring.redis.ratelimit.port}") int port) {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "rateLimitRedisTemplate")
    public StringRedisTemplate rateLimitRedisTemplate(
            RedisConnectionFactory rateLimitRedisConnectionFactory) {

        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(rateLimitRedisConnectionFactory);
        return template;
    }

    // Session Store Redis (port 6380)
    @Bean(name = "sessionRedisConnectionFactory")
    public RedisConnectionFactory sessionRedisConnectionFactory(
            @Value("${spring.redis.session.host}") String host,
            @Value("${spring.redis.session.port}") int port) {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "sessionRedisTemplate")
    public RedisTemplate<String, Object> sessionRedisTemplate(
            RedisConnectionFactory sessionRedisConnectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(sessionRedisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}

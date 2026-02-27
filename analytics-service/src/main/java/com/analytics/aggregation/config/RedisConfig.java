package com.analytics.aggregation.config;

import com.analytics.aggregation.listener.EventSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.host}")
    private String analyticsHost;

    @Value("${spring.redis.port}")
    private int analyticsPort;

    @Value("${spring.redis-pubsub.host}")
    private String pubsubHost;

    @Value("${spring.redis-pubsub.port}")
    private int pubsubPort;

    // Analytics Redis (port 6382)
    @Bean
    public RedisConnectionFactory analyticsRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(analyticsHost, analyticsPort);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory analyticsRedisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(analyticsRedisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    // Pub/Sub Redis (port 6383) for event listening
    @Bean
    public RedisConnectionFactory pubSubRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(pubsubHost, pubsubPort);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory pubSubRedisConnectionFactory,
            MessageListenerAdapter listenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(pubSubRedisConnectionFactory);

        // Subscribe to booking and flight events
        container.addMessageListener(listenerAdapter, new PatternTopic("events:booking:*"));
        container.addMessageListener(listenerAdapter, new PatternTopic("events:flight:*"));

        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(EventSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }
}

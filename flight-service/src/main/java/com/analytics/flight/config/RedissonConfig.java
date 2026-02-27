package com.analytics.flight.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${redisson.address}")
    private String address;

    @Value("${redisson.connection-pool-size}")
    private int connectionPoolSize;

    @Value("${redisson.connection-minimum-idle-size}")
    private int connectionMinimumIdleSize;

    @Value("${redisson.timeout}")
    private int timeout;

    @Value("${redisson.retry-attempts}")
    private int retryAttempts;

    @Value("${redisson.retry-interval}")
    private int retryInterval;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(address)
                .setConnectionPoolSize(connectionPoolSize)
                .setConnectionMinimumIdleSize(connectionMinimumIdleSize)
                .setTimeout(timeout)
                .setRetryAttempts(retryAttempts)
                .setRetryInterval(retryInterval);

        return Redisson.create(config);
    }
}

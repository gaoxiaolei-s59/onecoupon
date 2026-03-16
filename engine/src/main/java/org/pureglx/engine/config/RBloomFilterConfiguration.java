package org.pureglx.engine.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RBloomFilterConfiguration {
    @Bean
    public RBloomFilter<String> couponTemplateRBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> couponTemplateRBloomFilter = redissonClient.getBloomFilter("couponTemplateRBloomFilter");
        couponTemplateRBloomFilter.tryInit(6400L, 0.001);
        return couponTemplateRBloomFilter;
    }
}

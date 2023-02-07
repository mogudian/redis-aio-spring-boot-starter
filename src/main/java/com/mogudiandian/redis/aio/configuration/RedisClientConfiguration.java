package com.mogudiandian.redis.aio.configuration;

import io.lettuce.core.RedisClient;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis客户端启动类
 * 需要排除掉 spring-boot-autoconfigure 里面的 org.springframework.boot.autoconfigure.data.redis 下的 RedisAutoConfiguration
 * @author sunbo
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({RedisClient.class, Redisson.class, RedisOperations.class})
@AutoConfigureBefore(RedisAutoConfiguration.class)
@EnableConfigurationProperties(RedisClientProperties.class)
public class RedisClientConfiguration extends RedisConnectionConfiguration {

    @Autowired
    private RedisClientProperties redisClientProperties;

    @Bean
    @ConditionalOnMissingBean
    public RedisConnectionFactory redisConnectionFactory(ObjectProvider<LettuceClientConfigurationBuilderCustomizer> builderCustomizers) {
        return super.createLettuceConnectionFactory(redisClientProperties, builderCustomizers);
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<?, ?> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<?, ?> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }

    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(redisConnectionFactory);
        return stringRedisTemplate;
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient() {
        return super.createRedissonClient(redisClientProperties);
    }

}
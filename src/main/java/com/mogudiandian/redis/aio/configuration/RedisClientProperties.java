package com.mogudiandian.redis.aio.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis的配置
 * @author sunbo
 */
@ConfigurationProperties(prefix = "redis.aio")
@Getter
@Setter
public class RedisClientProperties extends RedisProperties {

}

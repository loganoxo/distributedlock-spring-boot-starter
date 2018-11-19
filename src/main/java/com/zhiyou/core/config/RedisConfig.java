package com.zhiyou.core.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.support.ConnectionPoolSupport;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author QinHe at 2018-11-09
 */

@Configuration
public class RedisConfig {

    @Bean(name = "lettucePool")
    public GenericObjectPool lettucePool(RedisProperties redisProperties) {
        RedisURI.Builder builder = RedisURI.builder();
        builder.withPassword(redisProperties.getPassword());
        builder.withDatabase(12);
        RedisProperties.Sentinel sentinel = redisProperties.getSentinel();
        List<String> nodes = sentinel.getNodes();
        nodes.forEach(node -> {
            builder.withSentinel(HostAndPort.parse(node).getHostText(), HostAndPort.parse(node).getPort());
        });
        RedisURI redisURI = builder.build();
        RedisClient client = RedisClient.create(redisURI);
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setJmxEnabled(false);
        GenericObjectPool<StatefulRedisConnection> lettucePool = ConnectionPoolSupport
                .createGenericObjectPool(() -> client.connect(), config);
        return lettucePool;
    }

    @Bean
    public RedissonClient redisson(RedisProperties redisProperties) {
        Config config = new Config();
        if (redisProperties.getSentinel() != null) { //sentinel
            SentinelServersConfig sentinelServersConfig = config.useSentinelServers();
            sentinelServersConfig.setMasterName(redisProperties.getSentinel().getMaster());
            redisProperties.getSentinel().getNodes();
            sentinelServersConfig.addSentinelAddress((String[]) redisProperties.getSentinel().getNodes().toArray());
            sentinelServersConfig.setDatabase(0);
            if (redisProperties.getPassword() != null) {
                sentinelServersConfig.setPassword(redisProperties.getPassword());
            }
        } else { //single server
            SingleServerConfig singleServerConfig = config.useSingleServer();
            String schema = redisProperties.isSsl() ? "rediss://" : "redis://";
            singleServerConfig.setAddress(schema + redisProperties.getHost() + ":" + redisProperties.getPort());
            singleServerConfig.setDatabase(0);
            if (redisProperties.getPassword() != null) {
                singleServerConfig.setPassword(redisProperties.getPassword());
            }
        }
        return Redisson.create(config);
    }
}
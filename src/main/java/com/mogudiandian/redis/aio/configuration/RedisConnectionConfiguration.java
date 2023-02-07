package com.mogudiandian.redis.aio.configuration;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions.Builder;
import io.lettuce.core.resource.DefaultClientResources;
import lombok.SneakyThrows;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties.Lettuce.Cluster.Refresh;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties.Pool;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.LettuceClientConfigurationBuilder;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Redis连接的基类
 * @author sunbo
 */
public abstract class RedisConnectionConfiguration {

    protected final LettuceConnectionFactory createLettuceConnectionFactory(RedisClientProperties redisClientProperties,
                                                                            ObjectProvider<LettuceClientConfigurationBuilderCustomizer> builderCustomizers) {

        LettuceClientConfigurationBuilder builder = Optional.ofNullable(redisClientProperties.getLettuce().getPool())
                                                            .map(this::createLettuceClientConfigurationBuilder)
                                                            .orElseGet(LettuceClientConfiguration::builder);
        applyProperties(redisClientProperties, builder);
        if (StringUtils.hasText(redisClientProperties.getUrl())) {
            customizeConfigurationFromUrl(redisClientProperties, builder);
        }
        builder.clientOptions(initializeClientOptionsBuilder(redisClientProperties).timeoutOptions(TimeoutOptions.enabled()).build());
        builder.clientResources(DefaultClientResources.create());
        builderCustomizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
        LettuceClientConfiguration clientConfiguration = builder.build();

        RedisSentinelConfiguration sentinelConfig = getSentinelConfig(redisClientProperties);
        if (sentinelConfig != null) {
            return new LettuceConnectionFactory(sentinelConfig, clientConfiguration);
        }
        RedisClusterConfiguration clusterConfiguration = getClusterConfiguration(redisClientProperties);
        if (clusterConfiguration != null) {
            return new LettuceConnectionFactory(clusterConfiguration, clientConfiguration);
        }
        return new LettuceConnectionFactory(getStandaloneConfig(redisClientProperties), clientConfiguration);
    }

    private void applyProperties(RedisClientProperties redisClientProperties, LettuceClientConfigurationBuilder builder) {
        if (redisClientProperties.isSsl()) {
            builder.useSsl();
        }
        if (redisClientProperties.getTimeout() != null) {
            builder.commandTimeout(redisClientProperties.getTimeout());
        }
        if (redisClientProperties.getLettuce() != null) {
            RedisProperties.Lettuce lettuce = redisClientProperties.getLettuce();
            if (lettuce.getShutdownTimeout() != null && !lettuce.getShutdownTimeout().isZero()) {
                builder.shutdownTimeout(redisClientProperties.getLettuce().getShutdownTimeout());
            }
        }
        if (StringUtils.hasText(redisClientProperties.getClientName())) {
            builder.clientName(redisClientProperties.getClientName());
        }
    }

    private ClientOptions.Builder initializeClientOptionsBuilder(RedisClientProperties redisClientProperties) {
        if (redisClientProperties.getCluster() != null) {
            ClusterClientOptions.Builder builder = ClusterClientOptions.builder();
            Refresh refreshProperties = redisClientProperties.getLettuce().getCluster().getRefresh();
            Builder refreshBuilder = ClusterTopologyRefreshOptions.builder();
            if (refreshProperties.getPeriod() != null) {
                refreshBuilder.enablePeriodicRefresh(refreshProperties.getPeriod());
            }
            if (refreshProperties.isAdaptive()) {
                refreshBuilder.enableAllAdaptiveRefreshTriggers();
            }
            return builder.topologyRefreshOptions(refreshBuilder.build());
        }
        return ClientOptions.builder();
    }

    private void customizeConfigurationFromUrl(RedisClientProperties redisClientProperties, LettuceClientConfigurationBuilder builder) {
        ConnectionInfo connectionInfo = parseUrl(redisClientProperties.getUrl());
        if (connectionInfo.isUseSsl()) {
            builder.useSsl();
        }
    }

    private LettuceClientConfigurationBuilder createLettuceClientConfigurationBuilder(Pool properties) {
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(properties.getMaxActive());
        poolConfig.setMaxIdle(properties.getMaxIdle());
        poolConfig.setMinIdle(properties.getMinIdle());
        if (properties.getTimeBetweenEvictionRuns() != null) {
            poolConfig.setTimeBetweenEvictionRunsMillis(properties.getTimeBetweenEvictionRuns().toMillis());
        }
        if (properties.getMaxWait() != null) {
            poolConfig.setMaxWaitMillis(properties.getMaxWait().toMillis());
        }
        return LettucePoolingClientConfiguration.builder().poolConfig(poolConfig);
    }

    private RedisStandaloneConfiguration getStandaloneConfig(RedisClientProperties redisClientProperties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        if (StringUtils.hasText(redisClientProperties.getUrl())) {
            ConnectionInfo connectionInfo = parseUrl(redisClientProperties.getUrl());
            config.setHostName(connectionInfo.getHostName());
            config.setPort(connectionInfo.getPort());
            config.setPassword(RedisPassword.of(connectionInfo.getPassword()));
        } else {
            config.setHostName(redisClientProperties.getHost());
            config.setPort(redisClientProperties.getPort());
            config.setPassword(RedisPassword.of(redisClientProperties.getPassword()));
        }
        config.setDatabase(redisClientProperties.getDatabase());
        return config;
    }

    private RedisSentinelConfiguration getSentinelConfig(RedisClientProperties redisClientProperties) {
        RedisProperties.Sentinel sentinelProperties = redisClientProperties.getSentinel();
        if (sentinelProperties != null) {
            RedisSentinelConfiguration config = new RedisSentinelConfiguration();
            config.master(sentinelProperties.getMaster());
            config.setSentinels(createSentinels(sentinelProperties));
            if (redisClientProperties.getPassword() != null) {
                config.setPassword(RedisPassword.of(redisClientProperties.getPassword()));
            }
            if (sentinelProperties.getPassword() != null) {
                config.setSentinelPassword(RedisPassword.of(sentinelProperties.getPassword()));
            }
            config.setDatabase(redisClientProperties.getDatabase());
            return config;
        }
        return null;
    }

    /**
     * Create a {@link RedisClusterConfiguration} if necessary.
     *
     * @return {@literal null} if no cluster settings are set.
     */
    private RedisClusterConfiguration getClusterConfiguration(RedisClientProperties redisClientProperties) {
        if (redisClientProperties.getCluster() == null) {
            return null;
        }
        RedisProperties.Cluster clusterProperties = redisClientProperties.getCluster();
        RedisClusterConfiguration config = new RedisClusterConfiguration(clusterProperties.getNodes());
        if (clusterProperties.getMaxRedirects() != null) {
            config.setMaxRedirects(clusterProperties.getMaxRedirects());
        }
        if (redisClientProperties.getPassword() != null) {
            config.setPassword(RedisPassword.of(redisClientProperties.getPassword()));
        }
        return config;
    }

    private List<RedisNode> createSentinels(RedisProperties.Sentinel sentinel) {
        List<RedisNode> nodes = new ArrayList<>();
        for (String node : sentinel.getNodes()) {
            try {
                String[] parts = StringUtils.split(node, ":");
                Assert.state(parts != null && parts.length == 2, "Must be defined as 'host:port'");
                nodes.add(new RedisNode(parts[0], Integer.parseInt(parts[1])));
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Invalid redis sentinel property '" + node + "'", ex);
            }
        }
        return nodes;
    }

    protected RedissonClient createRedissonClient(RedisClientProperties redisClientProperties) {
        int timeout = Optional.ofNullable(redisClientProperties.getTimeout())
                              .map(Duration::toMillis)
                              .map(Math::toIntExact)
                              .orElse(10_000);

        Config redissonSentinelConfig = getRedissonSentinelConfig(redisClientProperties, timeout);
        if (redissonSentinelConfig != null) {
            return Redisson.create(redissonSentinelConfig);
        }
        Config redissonClusterConfig = getRedissonClusterConfig(redisClientProperties, timeout);
        if (redissonClusterConfig != null) {
            return Redisson.create(redissonClusterConfig);
        }
        return Redisson.create(getRedissonStandaloneConfig(redisClientProperties, timeout));
    }

    private Config getRedissonSentinelConfig(RedisClientProperties redisClientProperties, int timeout) {
        if (redisClientProperties.getSentinel() != null) {
            String[] nodes = convertNodes(redisClientProperties.getSentinel().getNodes());
            Config config = new Config();
            config.useSentinelServers()
                  .setMasterName(redisClientProperties.getSentinel().getMaster())
                  .addSentinelAddress(nodes)
                  .setDatabase(redisClientProperties.getDatabase())
                  .setConnectTimeout(timeout)
                  .setPassword(redisClientProperties.getPassword());
            return config;
        }
        return null;
    }

    private Config getRedissonClusterConfig(RedisClientProperties redisClientProperties, int timeout) {
        if (redisClientProperties.getCluster() != null) {
            String[] nodes = convertNodes(redisClientProperties.getCluster().getNodes());
            Config config = new Config();
            config.useClusterServers()
                  .addNodeAddress(nodes)
                  .setConnectTimeout(timeout)
                  .setPassword(redisClientProperties.getPassword());
            return config;
        }
        return null;
    }

    private Config getRedissonStandaloneConfig(RedisClientProperties redisClientProperties, int timeout) {
        Config config = new Config();
        if (StringUtils.hasText(redisClientProperties.getUrl())) {
            ConnectionInfo connectionInfo = parseUrl(redisClientProperties.getUrl());
            config.useSingleServer()
                  .setAddress(redisClientProperties.getUrl())
                  .setConnectTimeout(timeout)
                  .setDatabase(redisClientProperties.getDatabase())
                  .setPassword(connectionInfo.getPassword());
        } else {
            String prefix = "redis://";
            if (redisClientProperties.isSsl()) {
                prefix = "rediss://";
            }
            config.useSingleServer()
                  .setAddress(prefix + redisClientProperties.getHost() + ":" + redisClientProperties.getPort())
                  .setConnectTimeout(timeout)
                  .setDatabase(redisClientProperties.getDatabase())
                  .setPassword(redisClientProperties.getPassword());
        }
        return config;
    }

    private String[] convertNodes(List<String> nodeList) {
        List<String> nodes = new ArrayList<>(nodeList.size());
        for (String node : nodeList) {
            if (!node.startsWith("redis://") && !node.startsWith("rediss://")) {
                nodes.add("redis://" + node);
            } else {
                nodes.add(node);
            }
        }
        return nodes.toArray(new String[0]);
    }

    @SneakyThrows
    protected ConnectionInfo parseUrl(String url) {
        URI uri = new URI(url);
        String scheme = uri.getScheme();
        if (!"redis".equals(scheme) && !"rediss".equals(scheme)) {
            throw new URISyntaxException(url, "URL scheme must start with redis:// or rediss://");
        }
        boolean useSsl = ("rediss".equals(scheme));
        String password = null;
        if (uri.getUserInfo() != null) {
            password = uri.getUserInfo();
            int index = password.indexOf(':');
            if (index >= 0) {
                password = password.substring(index + 1);
            }
        }
        return new ConnectionInfo(uri, useSsl, password);
    }

    protected static class ConnectionInfo {

        private final URI uri;

        private final boolean useSsl;

        private final String password;

        ConnectionInfo(URI uri, boolean useSsl, String password) {
            this.uri = uri;
            this.useSsl = useSsl;
            this.password = password;
        }

        boolean isUseSsl() {
            return this.useSsl;
        }

        String getHostName() {
            return this.uri.getHost();
        }

        int getPort() {
            return this.uri.getPort();
        }

        String getPassword() {
            return this.password;
        }
    }

}

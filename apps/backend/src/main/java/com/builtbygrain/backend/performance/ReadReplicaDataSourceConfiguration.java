package com.builtbygrain.backend.performance;

import java.util.Map;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.datasource.read-replica.enabled", havingValue = "true")
public class ReadReplicaDataSourceConfiguration {

    @Bean(name = "primaryTargetDataSource", defaultCandidate = false, destroyMethod = "close")
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource primaryTargetDataSource(DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        dataSource.setPoolName("builtbygrain-primary");
        return dataSource;
    }

    @Bean(name = "readReplicaTargetDataSource", defaultCandidate = false, destroyMethod = "close")
    HikariDataSource readReplicaTargetDataSource(
        ReadReplicaDataSourceProperties replica,
        DataSourceProperties primary
    ) {
        if (!StringUtils.hasText(replica.getUrl())) {
            throw new IllegalStateException("READ_REPLICA_URL is required when read-replica routing is enabled");
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("builtbygrain-read-replica");
        dataSource.setJdbcUrl(replica.getUrl());
        dataSource.setUsername(orDefault(replica.getUsername(), primary.determineUsername()));
        dataSource.setPassword(orDefault(replica.getPassword(), primary.determinePassword()));
        dataSource.setDriverClassName(primary.determineDriverClassName());
        dataSource.setReadOnly(true);
        dataSource.setMaximumPoolSize(replica.getMaximumPoolSize());
        dataSource.setMinimumIdle(replica.getMinimumIdle());
        dataSource.setConnectionTimeout(replica.getConnectionTimeout().toMillis());
        return dataSource;
    }

    @Bean
    @Primary
    DataSource dataSource(
        @Qualifier("primaryTargetDataSource") DataSource primary,
        @Qualifier("readReplicaTargetDataSource") DataSource replica
    ) {
        ReplicaRoutingDataSource routing = new ReplicaRoutingDataSource();
        routing.setTargetDataSources(Map.of(
            ReplicaRoutingDataSource.PRIMARY, primary,
            ReplicaRoutingDataSource.REPLICA, new ReplicaFallbackDataSource(replica, primary)
        ));
        routing.setDefaultTargetDataSource(primary);
        routing.setLenientFallback(false);
        routing.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(routing);
    }

    private String orDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}

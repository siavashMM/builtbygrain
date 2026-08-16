package com.builtbygrain.backend.performance;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

class ReplicaFallbackDataSource extends AbstractDataSource {

    private static final Logger log = LoggerFactory.getLogger(ReplicaFallbackDataSource.class);

    private final DataSource replica;
    private final DataSource primary;

    ReplicaFallbackDataSource(DataSource replica, DataSource primary) {
        this.replica = replica;
        this.primary = primary;
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            return replica.getConnection();
        } catch (SQLException exception) {
            logFallback(exception);
            return primary.getConnection();
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        try {
            return replica.getConnection(username, password);
        } catch (SQLException exception) {
            logFallback(exception);
            return primary.getConnection(username, password);
        }
    }

    private void logFallback(SQLException exception) {
        log.warn("Could not acquire a read-replica connection; falling back to the primary: {}", exception.toString());
        log.debug("Read-replica connection failure details", exception);
    }
}

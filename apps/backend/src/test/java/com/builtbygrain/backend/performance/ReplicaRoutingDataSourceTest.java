package com.builtbygrain.backend.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ReplicaRoutingDataSourceTest {

    private final ReplicaRoutingDataSource routing = new ReplicaRoutingDataSource();

    @AfterEach
    void clearThreadState() {
        while (ReadReplicaContext.requested()) ReadReplicaContext.exit();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void defaultsToPrimary() {
        assertThat(routing.determineCurrentLookupKey()).isEqualTo(ReplicaRoutingDataSource.PRIMARY);
    }

    @Test
    void routesOnlyExplicitReadOnlyWorkToReplica() {
        ReadReplicaContext.enter();
        assertThat(routing.determineCurrentLookupKey()).isEqualTo(ReplicaRoutingDataSource.PRIMARY);

        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThat(routing.determineCurrentLookupKey()).isEqualTo(ReplicaRoutingDataSource.REPLICA);
    }

    @Test
    void nestedReplicaScopesAreRestoredSafely() {
        ReadReplicaContext.enter();
        ReadReplicaContext.enter();
        ReadReplicaContext.exit();

        assertThat(ReadReplicaContext.requested()).isTrue();

        ReadReplicaContext.exit();
        assertThat(ReadReplicaContext.requested()).isFalse();
    }

    @Test
    void connectionFailureFallsBackToPrimary() throws SQLException {
        DataSource replica = mock(DataSource.class);
        DataSource primary = mock(DataSource.class);
        Connection primaryConnection = mock(Connection.class);
        when(replica.getConnection()).thenThrow(new SQLException("replica unavailable"));
        when(primary.getConnection()).thenReturn(primaryConnection);

        assertThat(new ReplicaFallbackDataSource(replica, primary).getConnection()).isSameAs(primaryConnection);
    }
}

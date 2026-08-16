package com.builtbygrain.backend.performance;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ReplicaRoutingDataSource extends AbstractRoutingDataSource {

    static final String PRIMARY = "primary";
    static final String REPLICA = "replica";

    @Override
    protected Object determineCurrentLookupKey() {
        return ReadReplicaContext.requested() && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            ? REPLICA
            : PRIMARY;
    }
}

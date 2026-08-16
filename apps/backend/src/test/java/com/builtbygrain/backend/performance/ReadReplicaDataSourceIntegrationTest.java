package com.builtbygrain.backend.performance;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "app.datasource.read-replica.enabled=true",
    "app.datasource.read-replica.url=jdbc:h2:mem:read-replica;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.datasource.read-replica.username=sa",
    "app.datasource.read-replica.password="
})
@ActiveProfiles("test")
@Import(ReadReplicaDataSourceIntegrationTest.ProbeConfiguration.class)
class ReadReplicaDataSourceIntegrationTest {

    @Autowired
    @Qualifier("primaryTargetDataSource")
    DataSource primary;

    @Autowired
    @Qualifier("readReplicaTargetDataSource")
    DataSource replica;

    @Autowired
    ReplicaProbe probe;

    @BeforeEach
    void createProbeTables() {
        setMarker(primary, "primary");
        setMarker(replica, "replica");
    }

    @Test
    void markedReadOnlyMethodsUseReplicaWhileOtherWorkUsesPrimary() {
        assertThat(probe.primaryRead()).isEqualTo("primary");
        assertThat(probe.replicaRead()).isEqualTo("replica");

        probe.markedWrite("written-on-primary");

        assertThat(new JdbcTemplate(primary).queryForObject("SELECT marker FROM routing_probe", String.class))
            .isEqualTo("written-on-primary");
        assertThat(new JdbcTemplate(replica).queryForObject("SELECT marker FROM routing_probe", String.class))
            .isEqualTo("replica");
    }

    private void setMarker(DataSource dataSource, String marker) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS routing_probe(marker VARCHAR(100) NOT NULL)");
        jdbc.update("DELETE FROM routing_probe");
        jdbc.update("INSERT INTO routing_probe(marker) VALUES (?)", marker);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        ReplicaProbe replicaProbe(DataSource dataSource) {
            return new ReplicaProbe(new JdbcTemplate(dataSource));
        }
    }

    static class ReplicaProbe {
        private final JdbcTemplate jdbc;

        ReplicaProbe(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional(readOnly = true)
        String primaryRead() {
            return jdbc.queryForObject("SELECT marker FROM routing_probe", String.class);
        }

        @ReadFromReplica
        @Transactional(readOnly = true)
        String replicaRead() {
            return jdbc.queryForObject("SELECT marker FROM routing_probe", String.class);
        }

        @ReadFromReplica
        @Transactional
        void markedWrite(String marker) {
            jdbc.update("UPDATE routing_probe SET marker = ?", marker);
        }
    }
}

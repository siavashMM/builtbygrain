package com.builtbygrain.backend.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CustomerVersionPostgreSqlMigrationTest {

    private static final String FIRST_EMAIL = "migration-existing-one@example.test";
    private static final String SECOND_EMAIL = "migration-existing-two@example.test";
    private static final String THIRD_EMAIL = "migration-existing-three@example.test";
    private static final String ORIGINAL_HASH = "{noop}pre-v17-password";
    private static final String JDBC_HASH = "{noop}post-v17-jdbc-password";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static Long firstCustomerId;
    private static Long secondCustomerId;
    private static Long thirdCustomerId;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        preparePopulatedV16Database();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerRepository customers;
    @Autowired PasswordResetStore passwordResetStore;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void populatedV16DatabaseMigratesWithoutDataLossAndParticipatesInVersioning() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customers", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT version FROM customers ORDER BY id", Long.class))
            .containsExactly(0L, 0L, 0L);
        assertThat(jdbc.queryForObject(
            "SELECT is_nullable FROM information_schema.columns WHERE table_schema='public' "
                + "AND table_name='customers' AND column_name='version'",
            String.class
        )).isEqualTo("NO");

        assertThat(jdbc.queryForObject(
            "SELECT email FROM customers WHERE id=?",
            String.class,
            firstCustomerId
        )).isEqualTo(FIRST_EMAIL);
        assertThat(jdbc.queryForObject(
            "SELECT account_status FROM customers WHERE id=?",
            String.class,
            thirdCustomerId
        )).isEqualTo("DISABLED");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE customer_id=? AND city='Berlin'",
            Integer.class,
            firstCustomerId
        )).isOne();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_password_reset_tokens WHERE customer_id IN (?,?)",
            Integer.class,
            firstCustomerId,
            secondCustomerId
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_social_identities WHERE customer_id IN (?,?)",
            Integer.class,
            secondCustomerId,
            thirdCustomerId
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE principal_name IN (?,?)",
            Integer.class,
            FIRST_EMAIL,
            SECOND_EMAIL
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM spring_session_attributes",
            Integer.class
        )).isOne();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Customer customer = customers.findById(firstCustomerId).orElseThrow();
            customer.updateProfile(
                FIRST_EMAIL,
                FIRST_EMAIL,
                "Updated",
                "Migration",
                "+49 30 123456",
                "de"
            );
            customers.flush();
        });

        assertThat(jdbc.queryForObject(
            "SELECT version FROM customers WHERE id=?",
            Long.class,
            firstCustomerId
        )).isOne();
        assertThat(jdbc.queryForObject(
            "SELECT password_hash FROM customers WHERE id=?",
            String.class,
            firstCustomerId
        )).isEqualTo(ORIGINAL_HASH);

        transaction.executeWithoutResult(status -> {
            PasswordResetStore.CustomerPasswordState state = passwordResetStore
                .lockCustomerPassword(firstCustomerId)
                .orElseThrow();
            assertThat(state.version()).isOne();
            assertThat(state.passwordHash()).isEqualTo(ORIGINAL_HASH);
            assertThat(passwordResetStore.updatePasswordIfCurrent(
                state.id(), state.passwordHash(), state.version(), JDBC_HASH
            )).isTrue();
        });

        assertThat(jdbc.queryForObject(
            "SELECT version FROM customers WHERE id=?",
            Long.class,
            firstCustomerId
        )).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
            "SELECT password_hash FROM customers WHERE id=?",
            String.class,
            firstCustomerId
        )).isEqualTo(JDBC_HASH);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE customer_id=?",
            Integer.class,
            firstCustomerId
        )).isOne();
    }

    private static void preparePopulatedV16Database() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        dataSource.setDriverClassName("org.postgresql.Driver");

        Flyway.configure()
            .dataSource(dataSource)
            .target("16")
            .load()
            .migrate();

        JdbcTemplate fixture = new JdbcTemplate(dataSource);
        assertThat(fixture.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' "
                + "AND table_name='customers' AND column_name='version'",
            Integer.class
        )).isZero();

        firstCustomerId = insertCustomer(fixture, FIRST_EMAIL, ORIGINAL_HASH, "ACTIVE");
        secondCustomerId = insertCustomer(fixture, SECOND_EMAIL, null, "ACTIVE");
        thirdCustomerId = insertCustomer(fixture, THIRD_EMAIL, ORIGINAL_HASH, "DISABLED");

        fixture.update(
            """
            INSERT INTO customer_addresses(
                customer_id,recipient_name,company,street,house_number,address_line_2,
                postal_code,city,region,country_code,phone,default_shipping,default_billing,
                created_at,updated_at
            ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,
            firstCustomerId,
            "Existing Customer",
            "Grain GmbH",
            "Migration Street",
            "17",
            "Second floor",
            "10115",
            "Berlin",
            "Berlin",
            "DE",
            "+49 30 555555",
            true,
            true
        );
        fixture.update(
            """
            INSERT INTO customer_password_reset_tokens(
                customer_id,token_hash,expires_at,used_at,created_at
            ) VALUES(?,repeat('a',64),CURRENT_TIMESTAMP + INTERVAL '1 hour',NULL,CURRENT_TIMESTAMP)
            """,
            firstCustomerId
        );
        fixture.update(
            """
            INSERT INTO customer_password_reset_tokens(
                customer_id,token_hash,expires_at,used_at,created_at
            ) VALUES(?,repeat('b',64),CURRENT_TIMESTAMP - INTERVAL '1 hour',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,
            secondCustomerId
        );
        fixture.update(
            """
            INSERT INTO customer_social_identities(customer_id,provider,provider_subject,created_at)
            VALUES(?,'google','existing-google-subject',CURRENT_TIMESTAMP)
            """,
            secondCustomerId
        );
        fixture.update(
            """
            INSERT INTO customer_social_identities(customer_id,provider,provider_subject,created_at)
            VALUES(?,'apple','existing-apple-subject',CURRENT_TIMESTAMP)
            """,
            thirdCustomerId
        );

        String firstSession = insertSession(fixture, FIRST_EMAIL);
        insertSession(fixture, SECOND_EMAIL);
        fixture.update(
            """
            INSERT INTO spring_session_attributes(session_primary_id,attribute_name,attribute_bytes)
            VALUES(?,?,?)
            """,
            firstSession,
            "migration-attribute",
            "preserved".getBytes(StandardCharsets.UTF_8)
        );

        Flyway migrated = Flyway.configure().dataSource(dataSource).load();
        migrated.migrate();
        migrated.validate();
    }

    private static Long insertCustomer(
        JdbcTemplate fixture,
        String email,
        String passwordHash,
        String accountStatus
    ) {
        return fixture.queryForObject(
            """
            INSERT INTO customers(
                email,normalized_email,password_hash,first_name,last_name,locale,
                account_status,created_at,updated_at
            ) VALUES(?,?,?,?,?,? ,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            RETURNING id
            """,
            Long.class,
            email,
            email,
            passwordHash,
            "Existing",
            "Customer",
            "en",
            accountStatus
        );
    }

    private static String insertSession(JdbcTemplate fixture, String principalName) {
        String primaryId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        fixture.update(
            """
            INSERT INTO spring_session(
                primary_id,session_id,creation_time,last_access_time,
                max_inactive_interval,expiry_time,principal_name
            ) VALUES(?,?,?,?,?,?,?)
            """,
            primaryId,
            UUID.randomUUID().toString(),
            now,
            now,
            1800,
            now + 1_800_000,
            principalName
        );
        return primaryId;
    }
}

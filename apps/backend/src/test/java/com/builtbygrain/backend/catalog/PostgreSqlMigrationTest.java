package com.builtbygrain.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.sql.DataSource;

import com.builtbygrain.backend.product.ProductCardService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .load()
            .migrate();
    }

    @Test
    void postgresEnforcesRootSlugAndVariantIntegrity() throws Exception {
        try (var connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        )) {
            assertThatThrownBy(() -> connection.createStatement().executeUpdate("""
                INSERT INTO categories(name,slug,sort_order,active,created_at,updated_at)
                VALUES('Duplicate','uncategorized',1,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """))
                .isInstanceOf(SQLException.class);

            long categoryId;
            try (var result = connection.createStatement().executeQuery(
                "SELECT id FROM categories WHERE slug='uncategorized'"
            )) {
                result.next();
                categoryId = result.getLong(1);
            }

            long productId;
            try (var statement = connection.prepareStatement("""
                INSERT INTO products(name,slug,price_cents,currency,active,created_at,updated_at,in_stock,category_id)
                VALUES('Migration product','migration-product',1000,'EUR',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,TRUE,?)
                RETURNING id
                """)) {
                statement.setLong(1, categoryId);
                try (var result = statement.executeQuery()) {
                    result.next();
                    productId = result.getLong(1);
                }
            }

            assertThatThrownBy(() -> connection.createStatement().executeUpdate("""
                INSERT INTO product_variants(public_id,product_id,combination_key,regular_price_cents,
                    stock_quantity,availability_status,active,allow_backorder,created_at,updated_at)
                VALUES('invalid-status',%d,'invalid',1000,1,'UNKNOWN',TRUE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """.formatted(productId)))
                .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> connection.createStatement().executeUpdate("""
                INSERT INTO product_variants(public_id,product_id,combination_key,regular_price_cents,
                    sale_price_cents,stock_quantity,availability_status,active,allow_backorder,created_at,updated_at)
                VALUES('invalid-price',%d,'invalid-price',1000,1200,-1,'IN_STOCK',TRUE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """.formatted(productId)))
                .isInstanceOf(SQLException.class);

            connection.createStatement().executeUpdate("""
                INSERT INTO product_variants(public_id,product_id,combination_key,regular_price_cents,
                    stock_quantity,availability_status,active,allow_backorder,created_at,updated_at)
                VALUES('cascade-variant',%d,'cascade',1000,1,'IN_STOCK',TRUE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """.formatted(productId));
            connection.createStatement().executeUpdate("DELETE FROM products WHERE id=" + productId);

            try (var result = connection.createStatement().executeQuery(
                "SELECT COUNT(*) FROM product_variants WHERE product_id=" + productId
            )) {
                result.next();
                assertThat(result.getLong(1)).isZero();
            }
        }
    }

    @Test
    void productCardQueriesStayFixedAndFastForOneHundredPostgresProducts() {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
            dataSource.setUsername(POSTGRES.getUsername());
            dataSource.setPassword(POSTGRES.getPassword());
            dataSource.setMaximumPoolSize(4);
            JdbcTemplate fixture = new JdbcTemplate(dataSource);
            fixture.update("""
                INSERT INTO products(name,slug,price_cents,currency,active,created_at,updated_at,in_stock,category_id)
                SELECT 'Postgres fixture ' || n, 'postgres-fixture-' || n, 1000 + n, 'EUR', TRUE,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE,
                       (SELECT id FROM categories WHERE slug='uncategorized')
                FROM generate_series(1,100) AS n
                """);

            CountingJdbcTemplate counting = new CountingJdbcTemplate(dataSource);
            ProductCardService cards = new ProductCardService(counting);
            cards.activeCards();
            List<Long> timings = new ArrayList<>();
            for (int run = 0; run < 20; run++) {
                counting.reset();
                long started = System.nanoTime();
                assertThat(cards.activeCards()).hasSize(100);
                timings.add(System.nanoTime() - started);
                assertThat(counting.queryCount()).isEqualTo(4);
            }
            timings.sort(Comparator.naturalOrder());
            System.out.printf(
                "PostgreSQL 100-product projection p50=%.2fms p95=%.2fms, queries=4%n",
                timings.get(9) / 1_000_000.0,
                timings.get(18) / 1_000_000.0
            );
        }
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private int queryCount;

        private CountingJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCount++;
            return super.query(sql, rowMapper, args);
        }

        @Override
        public void query(String sql, RowCallbackHandler rowCallbackHandler, Object... args) {
            queryCount++;
            super.query(sql, rowCallbackHandler, args);
        }

        private int queryCount() { return queryCount; }
        private void reset() { queryCount = 0; }
    }
}

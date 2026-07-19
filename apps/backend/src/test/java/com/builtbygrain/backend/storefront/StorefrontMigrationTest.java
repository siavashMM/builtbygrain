package com.builtbygrain.backend.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StorefrontMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationCreatesDefaultSettingsWithoutReplacingCatalogData() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storefront_settings", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT hero_heading FROM storefront_settings WHERE id = 1", String.class))
            .isEqualTo("Handcrafted wooden goods, shaped for everyday use.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE slug = 'uncategorized'", Integer.class))
            .isEqualTo(1);
    }
}

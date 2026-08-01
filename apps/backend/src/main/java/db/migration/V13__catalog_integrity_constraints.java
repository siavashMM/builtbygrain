package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V13__catalog_integrity_constraints extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String database = connection.getMetaData().getDatabaseProductName();
        if (database.toLowerCase().contains("postgresql")) {
            try (Statement check = connection.createStatement();
                 ResultSet duplicates = check.executeQuery("SELECT slug FROM categories WHERE parent_id IS NULL GROUP BY slug HAVING COUNT(*) > 1")) {
                if (duplicates.next()) {
                    throw new IllegalStateException("Duplicate root category slug prevents integrity migration: " + duplicates.getString(1));
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE product_variants DROP CONSTRAINT fk_variants_product");
            statement.execute("ALTER TABLE product_variants ADD CONSTRAINT fk_variants_product "
                + "FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE");
            statement.execute("ALTER TABLE product_variants ADD CONSTRAINT ck_variants_availability "
                + "CHECK (availability_status IN ('IN_STOCK','LOW_STOCK','OUT_OF_STOCK','BACKORDER','PREORDER','DISCONTINUED'))");
            statement.execute("ALTER TABLE product_variants ADD CONSTRAINT ck_variants_sale_not_above_regular "
                + "CHECK (sale_price_cents IS NULL OR sale_price_cents <= regular_price_cents)");
            if (database.toLowerCase().contains("postgresql")) {
                statement.execute("CREATE UNIQUE INDEX uq_categories_root_slug ON categories(slug) WHERE parent_id IS NULL");
            }
        }
    }
}

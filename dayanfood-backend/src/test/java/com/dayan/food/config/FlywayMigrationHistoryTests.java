package com.dayan.food.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationHistoryTests {

    private static final String MIGRATION_ROOT = "db/migration/";

    @Test
    void publishedMigrationVersionsMatchProductionHistory() throws IOException {
        String footprint = readMigration("V10__add_recent_food_footprint_index.sql");
        String likes = readMigration("V11__add_food_like.sql");
        String signature = readMigration("V12__add_user_signature.sql");

        assertTrue(footprint.contains("idx_food_daily_visit_user_time"));
        assertTrue(likes.contains("CREATE TABLE food_like"));
        assertTrue(signature.contains("signature_pending"));

        ClassLoader loader = getClass().getClassLoader();
        assertNull(loader.getResource(MIGRATION_ROOT + "V10__add_food_like.sql"));
        assertNull(loader.getResource(MIGRATION_ROOT + "V11__add_user_signature.sql"));
        assertNull(loader.getResource(MIGRATION_ROOT + "V12__add_recent_food_footprint_index.sql"));
    }

    private String readMigration(String filename) throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream(MIGRATION_ROOT + filename)) {
            assertTrue(input != null, "Missing Flyway migration: " + filename);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

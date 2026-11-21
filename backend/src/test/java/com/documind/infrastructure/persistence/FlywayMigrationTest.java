
package com.documind.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class FlywayMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg17")
                    .withDatabaseName("documind")
                    .withUsername("documind")
                    .withPassword("documind");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void runMigrations(@Autowired DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .load();

        flyway.migrate();
    }

    @Test
    void flywayMigrations_applySuccessfully() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .load();

        var applied = flyway.info().applied();

        assertThat(applied).hasSize(10);

        assertThat(applied[0].getVersion().toString())
                .isEqualTo("1");
        assertThat(applied[0].getDescription())
                .isEqualTo("enable pgvector extension");

        assertThat(applied[1].getVersion().toString())
                .isEqualTo("2");
        assertThat(applied[1].getDescription())
                .isEqualTo("create auth and organization tables");

        assertThat(applied[2].getVersion().toString())
                .isEqualTo("3");
        assertThat(applied[2].getDescription())
                .isEqualTo("add security tables");

        assertThat(applied[3].getVersion().toString())
                .isEqualTo("4");
        assertThat(applied[3].getDescription())
                .isEqualTo("documents");

        assertThat(applied[4].getVersion().toString())
                .isEqualTo("5");
        assertThat(applied[4].getDescription())
                .isEqualTo("fix document sha256 type");

        assertThat(applied[5].getVersion().toString())
                .isEqualTo("6");
        assertThat(applied[5].getDescription())
                .isEqualTo("document chunks and fts");
        assertThat(applied[6].getVersion().toString()).isEqualTo("7");
        assertThat(applied[6].getDescription()).isEqualTo("document chunk embeddings");
    }

    @Test
    void flywayBaseline_isCreated() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history",
                Integer.class
        );

        assertThat(count).isEqualTo(10);
    }

    @Test
    void migrationIsRepeatable() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .load();

        // All migrations have already been applied.
        // Calling migrate() again should not create duplicates.
        flyway.migrate();

        var applied = flyway.info().applied();

        assertThat(applied).hasSize(10);
    }

    @Test
    void pgvectorExtensionEnabled_afterMigration() {
        String extension = jdbcTemplate.queryForObject(
                """
                SELECT extname
                FROM pg_extension
                WHERE extname = 'vector'
                """,
                String.class
        );

        assertThat(extension).isEqualTo("vector");
    }

    @Test
    void documentChunksTable_createdByV6() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'document_chunks'
                """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }
}

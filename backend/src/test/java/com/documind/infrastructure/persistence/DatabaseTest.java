
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
class DatabaseTest {

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
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void runMigrations(@Autowired DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .load();
        flyway.migrate();
    }

    @Test
    void databaseConnection_works() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1",
                Integer.class
        );

        assertThat(result).isEqualTo(1);
    }

    @Test
    void pgvectorExtension_isInstalled() {
        String result = jdbcTemplate.queryForObject(
                "SELECT extname FROM pg_extension WHERE extname = 'vector'",
                String.class
        );

        assertThat(result).isEqualTo("vector");
    }

    @Test
    void flywayBaselineTable_exists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history",
                Integer.class
        );

        assertThat(count).isEqualTo(10);
    }

    @Test
    void flywayBaselineTable_hasCorrectData() {
        String description = jdbcTemplate.queryForObject(
                """
                SELECT description
                FROM flyway_schema_history
                ORDER BY installed_rank DESC
                LIMIT 1
                """,
                String.class
        );

        assertThat(description)
                .isEqualTo("document processing jobs");
    }

    @Test
    void documentChunksTable_exists() {
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

    @Test
    void canCreateVectorColumn() {
        jdbcTemplate.execute(
                "CREATE TABLE test_vector " +
                        "(id SERIAL PRIMARY KEY, embedding vector(3))"
        );

        try {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_name = 'test_vector'
                      AND column_name = 'embedding'
                    """,
                    Integer.class
            );

            assertThat(count).isEqualTo(1);
        } finally {
            jdbcTemplate.execute("DROP TABLE IF EXISTS test_vector");
        }
    }

    @Test
    void canInsertAndQueryVector() {
        jdbcTemplate.execute(
                "CREATE TABLE test_vector " +
                        "(id SERIAL PRIMARY KEY, embedding vector(3))"
        );

        try {
            jdbcTemplate.execute(
                    """
                    INSERT INTO test_vector (embedding)
                    VALUES ('[1,2,3]'), ('[4,5,6]')
                    """
            );

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM test_vector",
                    Integer.class
            );

            assertThat(count).isEqualTo(2);
        } finally {
            jdbcTemplate.execute("DROP TABLE IF EXISTS test_vector");
        }
    }
}

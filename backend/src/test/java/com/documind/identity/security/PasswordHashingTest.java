package com.documind.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class PasswordHashingTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
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
    private PasswordEncoder passwordEncoder;

    @Test
    void passwordHashing_usesBCryptCost12() {
        // Verify the encoder is BCrypt with cost 12
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);

        BCryptPasswordEncoder bcryptEncoder = (BCryptPasswordEncoder) passwordEncoder;
        // BCryptPasswordEncoder doesn't expose cost directly, but we can verify by encoding
        String hash = bcryptEncoder.encode("testPassword123");

        // BCrypt hash format: $2a$<cost>$<salt><hash>
        // Cost 12 = $2a$12$
        assertThat(hash).startsWith("$2a$12$");
    }

    @Test
    void validPassword_verifiesSuccessfully() {
        String password = "SecureP@ssw0rd123!";
        String hash = passwordEncoder.encode(password);

        assertThat(passwordEncoder.matches(password, hash)).isTrue();
    }

    @Test
    void invalidPassword_rejected() {
        String password = "SecureP@ssw0rd123!";
        String wrongPassword = "WrongP@ssw0rd123!";
        String hash = passwordEncoder.encode(password);

        assertThat(passwordEncoder.matches(wrongPassword, hash)).isFalse();
    }

    @Test
    void samePassword_producesDifferentHashes() {
        String password = "SamePassword123!";
        String hash1 = passwordEncoder.encode(password);
        String hash2 = passwordEncoder.encode(password);

        // Each encoding should produce a different hash (different salt)
        assertThat(hash1).isNotEqualTo(hash2);
        // But both should verify
        assertThat(passwordEncoder.matches(password, hash1)).isTrue();
        assertThat(passwordEncoder.matches(password, hash2)).isTrue();
    }

    @Test
    void emptyPassword_rejected() {
        String hash = passwordEncoder.encode("validPassword123");

        assertThat(passwordEncoder.matches("", hash)).isFalse();
    }

    @Test
    void longPassword_handledCorrectly() {
        // BCrypt has a 72-byte limit and throws exception for longer passwords
        String longPassword = "a".repeat(100);

        assertThatThrownBy(() -> passwordEncoder.encode(longPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes");

        // But passwords up to 72 bytes should work
        String validLongPassword = "a".repeat(72);
        String hash = passwordEncoder.encode(validLongPassword);
        assertThat(passwordEncoder.matches(validLongPassword, hash)).isTrue();
    }

    @Test
    void specialCharacters_handledCorrectly() {
        String password = "P@ssw0rd!#$%^&*()_+-=[]{}|;':\",./<>?`~";
        String hash = passwordEncoder.encode(password);

        assertThat(passwordEncoder.matches(password, hash)).isTrue();
    }

    @Test
    void unicodePassword_handledCorrectly() {
        String password = "密码123🔐パスワード";
        String hash = passwordEncoder.encode(password);

        assertThat(passwordEncoder.matches(password, hash)).isTrue();
    }

    @Test
    void hashFormat_isStandardBCrypt() {
        String password = "testPassword123";
        String hash = passwordEncoder.encode(password);

        // Standard BCrypt format: $2a$12$<22-char-salt><31-char-hash>
        assertThat(hash).matches("^\\$2a\\$12\\$[./A-Za-z0-9]{53}$");
    }

    @Test
    void timingResistance_constantTimeComparison() {
        // Test that verification takes similar time for valid and invalid passwords
        String password = "testPassword123";
        String hash = passwordEncoder.encode(password);
        String wrongPassword = "wrongPassword456";

        // Warm up
        for (int i = 0; i < 10; i++) {
            passwordEncoder.matches(password, hash);
            passwordEncoder.matches(wrongPassword, hash);
        }

        long validTime = measureTime(() -> {
            for (int i = 0; i < 100; i++) {
                passwordEncoder.matches(password, hash);
            }
        });

        long invalidTime = measureTime(() -> {
            for (int i = 0; i < 100; i++) {
                passwordEncoder.matches(wrongPassword, hash);
            }
        });

        // Times should be similar (within 50% - BCrypt is intentionally slow)
        // This is a basic check; real timing attacks need more sophisticated analysis
        double ratio = (double) Math.max(validTime, invalidTime) / Math.min(validTime, invalidTime);
        assertThat(ratio).isLessThan(2.0);
    }

    private long measureTime(Runnable runnable) {
        long start = System.nanoTime();
        runnable.run();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
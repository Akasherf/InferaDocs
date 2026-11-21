package com.documind.identity.security;

import com.documind.common.security.TokenGenerator;
import com.documind.identity.domain.RefreshSession;
import com.documind.identity.repository.RefreshSessionRepository;
import com.documind.identity.service.AuthService;
import com.documind.identity.dto.AuthDtos;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class RefreshTokenConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("documind")
            .withUsername("documind")
            .withPassword("documind");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)))
            .withCommand("redis-server --appendonly yes");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    static void runMigrations(@Autowired DataSource dataSource) {
        Flyway flyway = Flyway.configure().dataSource(dataSource).load();
        flyway.migrate();
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    private HttpServletRequest createRequest() {
        return new MockHttpServletRequest();
    }

    private HttpServletResponse createResponse() {
        return new MockHttpServletResponse();
    }

    private void setRefreshTokenCookie(HttpServletRequest request, String refreshToken) {
        Cookie cookie = new Cookie("refresh_token", refreshToken);
        cookie.setPath("/api/v1");
        ((MockHttpServletRequest) request).setCookies(cookie);
    }

    private String extractRefreshTokenFromResponse(HttpServletResponse response) {
        String setCookieHeader = response.getHeader("Set-Cookie");
        if (setCookieHeader == null) {
            return null;
        }
        // Parse the Set-Cookie header to extract refresh_token value
        // Format: refresh_token=value; HttpOnly; Secure; SameSite=Strict; Path=/api/v1; Max-Age=604800
        String[] parts = setCookieHeader.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("refresh_token=")) {
                return part.substring("refresh_token=".length());
            }
        }
        return null;
    }

    @Test
    void refreshToken_rotationUnderConcurrency_atomic() throws InterruptedException {
        // Register and login to get refresh token
        String email = "concurrent" + System.currentTimeMillis() + "@example.com";
        String password = "SecureP@ssw0rd123!";

        HttpServletRequest req = createRequest();
        HttpServletResponse resp = createResponse();

        authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName("Concurrent User")
                .build(), req, resp);

        var loginResponse = authService.login(AuthDtos.LoginRequest.builder()
                .email(email)
                .password(password)
                .build(), req, resp);

        String refreshToken = extractRefreshTokenFromResponse(resp);
        assertThat(refreshToken).isNotBlank();

        // Simulate concurrent refresh attempts
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<String> lastRefreshToken = new AtomicReference<>(refreshToken);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String currentToken = lastRefreshToken.get();

                    HttpServletRequest reqInner = createRequest();
                    HttpServletResponse respInner = createResponse();
                    setRefreshTokenCookie(reqInner, currentToken);

                    var response = authService.refresh(currentToken, reqInner, respInner);
                    String newToken = extractRefreshTokenFromResponse(respInner);
                    if (newToken != null) {
                        lastRefreshToken.set(newToken);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Only ONE should succeed due to atomic rotation with reuse detection
        // The others should fail because the token was already rotated
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    void refreshToken_reuseDetection_revokesFamily() throws Exception {
        String email = "reuse" + System.currentTimeMillis() + "@example.com";
        String password = "SecureP@ssw0rd123!";

        HttpServletRequest req = createRequest();
        HttpServletResponse resp = createResponse();

        authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName("Reuse User")
                .build(), req, resp);

        var loginResponse = authService.login(AuthDtos.LoginRequest.builder()
                .email(email)
                .password(password)
                .build(), req, resp);

        String refreshToken = extractRefreshTokenFromResponse(resp);
        assertThat(refreshToken).isNotBlank();

        // First refresh - should succeed
        HttpServletRequest req1 = createRequest();
        HttpServletResponse resp1 = createResponse();
        setRefreshTokenCookie(req1, refreshToken);
        var response1 = authService.refresh(refreshToken, req1, resp1);
        assertThat(response1.getAccessToken()).isNotBlank();

        String newRefreshToken = extractRefreshTokenFromResponse(resp1);
        assertThat(newRefreshToken).isNotBlank();

        // Try to use the OLD refresh token again - should fail with reuse detection
        HttpServletRequest req2 = createRequest();
        HttpServletResponse resp2 = createResponse();
        setRefreshTokenCookie(req2, refreshToken);

        assertThatThrownBy(() -> authService.refresh(refreshToken, req2, resp2))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                .hasMessageContaining("reuse detected");

        // The new refresh token should still work
        HttpServletRequest req3 = createRequest();
        HttpServletResponse resp3 = createResponse();
        setRefreshTokenCookie(req3, newRefreshToken);

        var response2 = authService.refresh(newRefreshToken, req3, resp3);
        assertThat(response2.getAccessToken()).isNotBlank();
    }

    @Test
    @Transactional
    void refreshToken_expired_rejected() {
        String email = "expiredrefresh" + System.currentTimeMillis() + "@example.com";
        String password = "SecureP@ssw0rd123!";

        HttpServletRequest req = createRequest();
        HttpServletResponse resp = createResponse();

        authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName("Expired Refresh User")
                .build(), req, resp);

        var loginResponse = authService.login(AuthDtos.LoginRequest.builder()
                .email(email)
                .password(password)
                .build(), req, resp);

        String refreshToken = extractRefreshTokenFromResponse(resp);
        assertThat(refreshToken).isNotBlank();

        // Manually expire the refresh token
        String tokenHash = tokenGenerator.hashToken(refreshToken);
        var sessionOpt = refreshSessionRepository.findActiveByTokenHash(tokenHash, Instant.now());
        assertThat(sessionOpt).isPresent();
        RefreshSession session = sessionOpt.get();
        session.setExpiresAt(Instant.now().minusSeconds(3600));
        refreshSessionRepository.save(session);

        HttpServletRequest req2 = createRequest();
        HttpServletResponse resp2 = createResponse();
        setRefreshTokenCookie(req2, refreshToken);

        assertThatThrownBy(() -> authService.refresh(refreshToken, req2, resp2))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                .hasMessageContaining("reuse detected");
    }

    @Test
    @Transactional
    void refreshToken_revoked_rejected() {
        String email = "revokedrefresh" + System.currentTimeMillis() + "@example.com";
        String password = "SecureP@ssw0rd123!";

        HttpServletRequest req = createRequest();
        HttpServletResponse resp = createResponse();

        authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName("Revoked Refresh User")
                .build(), req, resp);

        var loginResponse = authService.login(AuthDtos.LoginRequest.builder()
                .email(email)
                .password(password)
                .build(), req, resp);

        String refreshToken = extractRefreshTokenFromResponse(resp);
        assertThat(refreshToken).isNotBlank();

        // Manually revoke the refresh token
        String tokenHash = tokenGenerator.hashToken(refreshToken);
        var sessionOpt = refreshSessionRepository.findActiveByTokenHash(tokenHash, Instant.now());
        assertThat(sessionOpt).isPresent();
        RefreshSession session = sessionOpt.get();
        session.setRevokedAt(Instant.now());
        refreshSessionRepository.save(session);

        HttpServletRequest req2 = createRequest();
        HttpServletResponse resp2 = createResponse();
        setRefreshTokenCookie(req2, refreshToken);

        assertThatThrownBy(() -> authService.refresh(refreshToken, req2, resp2))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                .hasMessageContaining("reuse detected");
    }

    @Test
    void refreshToken_invalidToken_rejected() {
        HttpServletRequest req = createRequest();
        HttpServletResponse resp = createResponse();
        setRefreshTokenCookie(req, "invalid.token.here");

        assertThatThrownBy(() -> authService.refresh("invalid.token.here", req, resp))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                .hasMessageContaining("Invalid refresh token");
    }
}
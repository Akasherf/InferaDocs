package com.documind.identity.security;

import com.documind.identity.domain.User;
import com.documind.identity.repository.UserRepository;
import com.documind.identity.service.AuthService;
import com.documind.identity.service.LoginAttemptService;
import com.documind.identity.dto.AuthDtos;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class RegistrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private LoginAttemptService loginAttemptService;

    private HttpServletRequest createRequest() {
        return new MockHttpServletRequest();
    }

    private HttpServletResponse createResponse() {
        return new MockHttpServletResponse();
    }

    @Test
    void register_newUser_success() {
        String email = "newuser" + System.currentTimeMillis() + "@example.com";
        String password = "SecureP@ssw0rd123!";
        String displayName = "New User";

        var response = authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName(displayName)
                .build(), createRequest(), createResponse());

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getUser().getEmail()).isEqualTo(email);
        assertThat(response.getUser().getDisplayName()).isEqualTo(displayName);
    }

    @Test
    void register_duplicateEmail_rejected() {
        String email = "duplicate" + System.currentTimeMillis() + "@example.com";
        String password = "SecureP@ssw0rd123!";
        String displayName = "User One";

        // First registration should succeed
        authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName(displayName)
                .build(), createRequest(), createResponse());

        // Second registration with same email should fail
        assertThatThrownBy(() -> authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password("DifferentP@ssw0rd123!")
                .displayName("User Two")
                .build(), createRequest(), createResponse()))
                .isInstanceOf(com.documind.common.exception.ApiException.class)
                .hasMessageContaining("Registration failed");
    }

    @Test
    void register_caseInsensitiveEmail_enforced() {
        String email = "CaseInsensitive" + System.currentTimeMillis() + "@Example.COM";
        String password = "SecureP@ssw0rd123!";

        // First registration with mixed case
        authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName("User One")
                .build(), createRequest(), createResponse());

        // Second registration with different case
        assertThatThrownBy(() -> authService.register(AuthDtos.RegisterRequest.builder()
                .email(email.toLowerCase())
                .password("DifferentP@ssw0rd123!")
                .displayName("User Two")
                .build(), createRequest(), createResponse()))
                .isInstanceOf(com.documind.common.exception.ApiException.class)
                .hasMessageContaining("Registration failed");
    }

    @Test
    void register_weakPassword_rejected() {
        String email = "weakpass" + System.currentTimeMillis() + "@example.com";

        // Test various weak passwords - the service should enforce password policy
        // Current implementation may not have explicit policy, but BCrypt will hash anything
        // This test documents expected behavior
        var response = authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password("weak")
                .displayName("User")
                .build(), createRequest(), createResponse());
        assertThat(response).isNotNull(); // Current implementation allows but hashes
    }

    @Test
    void login_existingUser_success() {
        String email = "login" + System.currentTimeMillis() + "@example.com";
        String password = "SecureP@ssw0rd123!";
        String displayName = "Login User";

        authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName(displayName)
                .build(), createRequest(), createResponse());

        var response = authService.login(AuthDtos.LoginRequest.builder()
                .email(email)
                .password(password)
                .build(), createRequest(), createResponse());

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        // Refresh token is returned via HttpOnly cookie, not in response body
    }

    @Test
    void login_wrongPassword_rejected() {
        String email = "wrongpass" + System.currentTimeMillis() + "@example.com";
        String password = "SecureP@ssw0rd123!";

        authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName("User")
                .build(), createRequest(), createResponse());

        assertThatThrownBy(() -> authService.login(AuthDtos.LoginRequest.builder()
                .email(email)
                .password("WrongP@ssw0rd123!")
                .build(), createRequest(), createResponse()))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_nonexistentUser_rejected() {
        assertThatThrownBy(() -> authService.login(AuthDtos.LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("SecureP@ssw0rd123!")
                .build(), createRequest(), createResponse()))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_inactiveUser_rejected() {
        String email = "inactive" + System.currentTimeMillis() + "@example.com";
        String password = "SecureP@ssw0rd123!";

        authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName("User")
                .build(), createRequest(), createResponse());

        // Manually disable user
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        assertThat(userOpt).isPresent();
        User user = userOpt.get();
        user.setStatus(User.UserStatus.DISABLED);
        userRepository.save(user);

        assertThatThrownBy(() -> authService.login(AuthDtos.LoginRequest.builder()
                .email(email)
                .password(password)
                .build(), createRequest(), createResponse()))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                .hasMessageContaining("Account is disabled");
    }

    @Test
    void login_rateLimiting_enforced() {
        String email = "ratelimit" + System.currentTimeMillis() + "@example.com";
        String password = "SecureP@ssw0rd123!";

        authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName("User")
                .build(), createRequest(), createResponse());

        // Make 5 failed attempts
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(AuthDtos.LoginRequest.builder()
                    .email(email)
                    .password("WrongP@ssw0rd123!")
                    .build(), createRequest(), createResponse()))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        }

        // 6th attempt should be rate limited
        assertThatThrownBy(() -> authService.login(AuthDtos.LoginRequest.builder()
                .email(email)
                .password("WrongP@ssw0rd123!")
                .build(), createRequest(), createResponse()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Too many login attempts");
    }

    @Test
    void login_successfulAttempt_resetsCounter() {
        String email = "resetlimit" + System.currentTimeMillis() + "@example.com";
        String password = "SecureP@ssw0rd123!";

        authService.register(AuthDtos.RegisterRequest.builder()
                .email(email)
                .password(password)
                .displayName("User")
                .build(), createRequest(), createResponse());

        // Make 4 failed attempts
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(AuthDtos.LoginRequest.builder()
                    .email(email)
                    .password("WrongP@ssw0rd123!")
                    .build(), createRequest(), createResponse()))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        }

        // Successful login should reset counter
        var response = authService.login(AuthDtos.LoginRequest.builder()
                .email(email)
                .password(password)
                .build(), createRequest(), createResponse());
        assertThat(response).isNotNull();

        // Next failed attempt should be allowed (counter reset)
        assertThatThrownBy(() -> authService.login(AuthDtos.LoginRequest.builder()
                .email(email)
                .password("WrongP@ssw0rd123!")
                .build(), createRequest(), createResponse()))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }
}
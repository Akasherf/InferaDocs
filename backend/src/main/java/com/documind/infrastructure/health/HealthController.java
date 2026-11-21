package com.documind.infrastructure.health;

import com.documind.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${info.app.version}")
    private String applicationVersion;

    public HealthController(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        HealthCheckResult dbHealth = checkDatabase();
        boolean dbUp = dbHealth.up();

        HealthResponse response = HealthResponse.builder()
                .status(dbUp ? "UP" : "DOWN")
                .application(applicationName)
                .version(applicationVersion)
                .timestamp(Instant.now())
                .checks(Map.of("database", dbHealth.details()))
                .build();

        HttpStatus httpStatus = dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(ApiResponse.success(response));
    }

    @GetMapping("/live")
    public ResponseEntity<ApiResponse<LivenessResponse>> liveness() {
        return ResponseEntity.ok(ApiResponse.success(
                LivenessResponse.builder()
                        .status("UP")
                        .timestamp(Instant.now())
                        .build()
        ));
    }

    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<ReadinessResponse>> readiness() {
        HealthCheckResult dbHealth = checkDatabase();
        boolean ready = dbHealth.up();

        ReadinessResponse response = ReadinessResponse.builder()
                .status(ready ? "READY" : "NOT_READY")
                .timestamp(Instant.now())
                .checks(Map.of("database", dbHealth.details()))
                .build();

        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.success(response));
    }

    private HealthCheckResult checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            Map<String, Object> details = new HashMap<>();
            details.put("database", "PostgreSQL");
            details.put("status", "UP");
            return new HealthCheckResult(true, details);
        } catch (Exception e) {
            Map<String, Object> details = new HashMap<>();
            details.put("database", "PostgreSQL");
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            return new HealthCheckResult(false, details);
        }
    }

    record HealthCheckResult(boolean up, Map<String, Object> details) {}

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HealthResponse {
        private String status;
        private String application;
        private String version;
        private Instant timestamp;
        private Map<String, Object> checks;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LivenessResponse {
        private String status;
        private Instant timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReadinessResponse {
        private String status;
        private Instant timestamp;
        private Map<String, Object> checks;
    }
}
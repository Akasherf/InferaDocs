package com.documind.infrastructure.health;

import com.documind.common.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void healthCheckResult_isCreatedCorrectly() {
        // Test the internal HealthCheckResult record directly
        Map<String, Object> details = new HashMap<>();
        details.put("database", "PostgreSQL");
        details.put("status", "UP");

        var result = new HealthController.HealthCheckResult(true, details);

        assertThat(result.up()).isTrue();
        assertThat(result.details()).containsEntry("database", "PostgreSQL");
        assertThat(result.details()).containsEntry("status", "UP");
    }

    @Test
    void healthCheckResult_downIsCreatedCorrectly() {
        Map<String, Object> details = new HashMap<>();
        details.put("database", "PostgreSQL");
        details.put("status", "DOWN");
        details.put("error", "Connection refused");

        var result = new HealthController.HealthCheckResult(false, details);

        assertThat(result.up()).isFalse();
        assertThat(result.details()).containsEntry("status", "DOWN");
        assertThat(result.details()).containsEntry("error", "Connection refused");
    }
}
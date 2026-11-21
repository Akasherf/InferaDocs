package com.documind.infrastructure.health;

import com.documind.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/system")
public class SystemInfoController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${info.app.version}")
    private String applicationVersion;

    @Value("${info.app.description}")
    private String applicationDescription;

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<SystemInfoResponse>> systemInfo() {
        SystemInfoResponse response = SystemInfoResponse.builder()
                .name(applicationName)
                .version(applicationVersion)
                .description(applicationDescription)
                .timestamp(Instant.now())
                .javaVersion(System.getProperty("java.version"))
                .javaVendor(System.getProperty("java.vendor"))
                .osName(System.getProperty("os.name"))
                .osVersion(System.getProperty("os.version"))
                .osArch(System.getProperty("os.arch"))
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SystemInfoResponse {
        private String name;
        private String version;
        private String description;
        private Instant timestamp;
        private String javaVersion;
        private String javaVendor;
        private String osName;
        private String osVersion;
        private String osArch;
    }
}
package com.documind.audit.service;

import com.documind.audit.domain.AuditEvent;
import com.documind.audit.repository.AuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Transactional
    public void recordEvent(String eventType, AuditEvent.AuditOutcome outcome, UUID actorUserId, UUID organizationId,
                            UUID requestId, HttpServletRequest request, Map<String, Object> metadata,
                            String targetEntityType, UUID targetEntityId) {
        try {
            String sourceIp = extractClientIp(request);

            AuditEvent event = AuditEvent.builder()
                    .actorUserId(actorUserId)
                    .organizationId(organizationId)
                    .eventType(eventType)
                    .outcome(outcome)
                    .requestId(requestId)
                    .metadata(metadata)
                    .sourceIp(sourceIp)
                    .targetEntityType(targetEntityType)
                    .targetEntityId(targetEntityId)
                    .build();

            auditEventRepository.save(event);
        } catch (Exception e) {
            // Audit logging must never break the main flow
            log.error("Failed to record audit event: {}", eventType, e);
        }
    }

    @Transactional
    public void recordAuthEvent(String eventType, AuditEvent.AuditOutcome outcome, UUID actorUserId,
                                UUID requestId, HttpServletRequest request, Map<String, Object> metadata) {
        recordEvent(eventType, outcome, actorUserId, null, requestId, request, metadata, null, null);
    }

    @Transactional
    public void recordOrganizationEvent(String eventType, AuditEvent.AuditOutcome outcome, UUID actorUserId,
                                        UUID organizationId, UUID requestId, HttpServletRequest request,
                                        Map<String, Object> metadata, String targetEntityType, UUID targetEntityId) {
        recordEvent(eventType, outcome, actorUserId, organizationId, requestId, request, metadata, targetEntityType, targetEntityId);
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
package com.documind.workspace;

import com.documind.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/organizations/{organizationId}")
public class WorkspaceOperationsController {
    private final WorkspacePreferencesService preferences;
    private final JdbcTemplate jdbc;

    public WorkspaceOperationsController(WorkspacePreferencesService preferences, JdbcTemplate jdbc) {
        this.preferences = preferences;
        this.jdbc = jdbc;
    }

    private static UUID user(HttpServletRequest request) {
        return (UUID)request.getAttribute("userId");
    }

    @GetMapping("/preferences")
    public ApiResponse<WorkspacePreferencesService.Preferences> get(
        @PathVariable("organizationId") UUID org, HttpServletRequest req) {
        return ApiResponse.success(preferences.getForMember(org,user(req)));
    }

    @PutMapping("/preferences")
    public ApiResponse<WorkspacePreferencesService.Preferences> update(
        @PathVariable("organizationId") UUID org,
        @RequestBody WorkspacePreferencesService.Preferences body,
        HttpServletRequest req) {
        return ApiResponse.success(preferences.save(org,user(req),body));
    }

    public record Counters(long documents, long chunks, long embeddedChunks, long conversations,
                           long messages, long activeMembers, long queuedJobs,
                           long runningJobs, long failedJobs, long completedJobs) {}
    public record RecentEvent(String eventType, String outcome, Instant createdAt) {}
    public record Overview(Counters counters, List<RecentEvent> recentEvents) {}

    private long count(String sql, UUID org) {
        Long n = jdbc.queryForObject(sql,Long.class,org);
        return n == null ? 0 : n;
    }

    @GetMapping("/admin/overview")
    public ApiResponse<Overview> overview(@PathVariable("organizationId") UUID org, HttpServletRequest req) {
        preferences.authorize(org,user(req),true);
        var stats = new Counters(
            count("SELECT count(*) FROM documents WHERE organization_id=? AND status='ACTIVE'",org),
            count("SELECT count(*) FROM document_chunks dc JOIN documents d ON d.id=dc.document_id AND d.organization_id=dc.organization_id WHERE dc.organization_id=? AND d.status='ACTIVE'",org),
            count("SELECT count(*) FROM document_chunks dc JOIN documents d ON d.id=dc.document_id AND d.organization_id=dc.organization_id WHERE dc.organization_id=? AND d.status='ACTIVE' AND dc.embedding IS NOT NULL",org),
            count("SELECT count(*) FROM ai_conversations WHERE organization_id=?",org),
            count("SELECT count(*) FROM ai_messages m JOIN ai_conversations c ON c.id=m.conversation_id WHERE c.organization_id=?",org),
            count("SELECT count(*) FROM organization_memberships WHERE organization_id=? AND status='ACTIVE'",org),
            count("SELECT count(*) FROM document_processing_jobs WHERE organization_id=? AND status='QUEUED'",org),
            count("SELECT count(*) FROM document_processing_jobs WHERE organization_id=? AND status='RUNNING'",org),
            count("SELECT count(*) FROM document_processing_jobs WHERE organization_id=? AND status='FAILED'",org),
            count("SELECT count(*) FROM document_processing_jobs WHERE organization_id=? AND status='SUCCEEDED'",org));
        var events = jdbc.query("""
            SELECT event_type,outcome,created_at FROM audit_events
            WHERE organization_id=? ORDER BY created_at DESC LIMIT 15
            """, (rs,i)->new RecentEvent(rs.getString(1),rs.getString(2),rs.getTimestamp(3).toInstant()),org);
        return ApiResponse.success(new Overview(stats,events));
    }

    public record RetentionPreview(int retentionDays, long eligibleDocuments, String action) {}
    @GetMapping("/admin/retention/preview")
    public ApiResponse<RetentionPreview> retentionPreview(@PathVariable("organizationId") UUID org,
                                                           HttpServletRequest req) {
        preferences.authorize(org,user(req),true);
        int days = preferences.getForMember(org,user(req)).retentionDays();
        long eligible = days == 0 ? 0 : jdbc.queryForObject("""
            SELECT count(*) FROM documents WHERE organization_id=? AND status='ACTIVE'
              AND created_at < now() - (INTERVAL '1 day' * ?)
            """,Long.class,org,days);
        return ApiResponse.success(new RetentionPreview(days,eligible,
            "Manual soft-delete only; stored objects are not physically purged"));
    }

    public record RetentionOutcome(long softDeletedDocuments, String note) {}
    @PostMapping("/admin/retention/apply")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public ApiResponse<RetentionOutcome> applyRetention(@PathVariable("organizationId") UUID org,
                                                         HttpServletRequest req) {
        preferences.authorize(org,user(req),true);
        int days = preferences.getForMember(org,user(req)).retentionDays();
        if(days == 0) return ApiResponse.success(new RetentionOutcome(0,"Retention disabled"));
        int updated = jdbc.update("""
            UPDATE documents SET status='DELETED',deleted_at=now()
            WHERE organization_id=? AND status='ACTIVE'
              AND created_at < now() - (INTERVAL '1 day' * ?)
            """,org,days);
        jdbc.update("INSERT INTO audit_events(actor_user_id,organization_id,event_type,outcome) VALUES (?,?,'WORKSPACE_RETENTION_APPLIED','SUCCESS')",user(req),org);
        // This matches the existing DocumentService soft-delete behavior.
        // Object-storage physical deletion is intentionally outside this operation.
        return ApiResponse.success(new RetentionOutcome(updated,
            "Documents hidden from active retrieval. Original objects are not physically purged."));
    }
}

package com.documind.workspace;

import com.documind.common.exception.ApiException;
import com.documind.organization.domain.OrganizationMembership;
import com.documind.organization.repository.OrganizationMembershipRepository;
import com.documind.organization.repository.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WorkspacePreferencesService {
    public record Preferences(int retentionDays, int maxUploadMb, String aiModel,
                              double aiTemperature, int aiTopK, boolean aiRerank) {}

    private static final Preferences DEFAULTS = new Preferences(0,20,"llama3.1:8b",0.0,6,false);
    private final JdbcTemplate jdbc;
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;

    public WorkspacePreferencesService(JdbcTemplate jdbc, OrganizationRepository organizations,
                                       OrganizationMembershipRepository memberships) {
        this.jdbc = jdbc;
        this.organizations = organizations;
        this.memberships = memberships;
    }

    public OrganizationMembership authorize(UUID org, UUID user, boolean adminOnly) {
        if (organizations.findActiveById(org).isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND,"WORKSPACE_NOT_FOUND","Workspace not found");
        var member = memberships.findActiveByOrganizationIdAndUserId(org,user)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"WORKSPACE_NOT_FOUND","Workspace not found"));
        if (adminOnly && !member.isAdminOrAbove())
            throw new ApiException(HttpStatus.FORBIDDEN,"ADMIN_REQUIRED","Workspace owner or admin access required");
        return member;
    }

    @Transactional(readOnly=true)
    public Preferences getForMember(UUID org, UUID user) {
        authorize(org,user,false);
        return jdbc.query("""
            SELECT retention_days,max_upload_mb,ai_model,ai_temperature,ai_top_k,ai_rerank
            FROM workspace_preferences WHERE organization_id=?
            """, (rs,i)->new Preferences(rs.getInt(1),rs.getInt(2),rs.getString(3),
                rs.getDouble(4),rs.getInt(5),rs.getBoolean(6)), org)
                .stream().findFirst().orElse(DEFAULTS);
    }

    private static void validate(Preferences p) {
        if (p == null || p.retentionDays()<0 || p.retentionDays()>3650 ||
            p.maxUploadMb()<1 || p.maxUploadMb()>20 ||
            p.aiTemperature()<0 || p.aiTemperature()>1 || !Double.isFinite(p.aiTemperature()) ||
            p.aiTopK()<1 || p.aiTopK()>12 || p.aiModel()==null ||
            !p.aiModel().matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,119}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_WORKSPACE_PREFERENCES",
                "Retention 0–3650 days; upload 1–20 MB; temperature 0–1; evidence 1–12; valid Ollama model name required");
        }
    }

    @Transactional
    public Preferences save(UUID org, UUID user, Preferences p) {
        authorize(org,user,true);
        validate(p);
        jdbc.update("""
            INSERT INTO workspace_preferences(organization_id,retention_days,max_upload_mb,
              ai_model,ai_temperature,ai_top_k,ai_rerank,updated_at)
            VALUES (?,?,?,?,?,?,?,now())
            ON CONFLICT (organization_id) DO UPDATE SET
              retention_days=EXCLUDED.retention_days,max_upload_mb=EXCLUDED.max_upload_mb,
              ai_model=EXCLUDED.ai_model,ai_temperature=EXCLUDED.ai_temperature,
              ai_top_k=EXCLUDED.ai_top_k,ai_rerank=EXCLUDED.ai_rerank,updated_at=now()
            """,org,p.retentionDays(),p.maxUploadMb(),p.aiModel(),p.aiTemperature(),p.aiTopK(),p.aiRerank());
        jdbc.update("INSERT INTO audit_events(actor_user_id,organization_id,event_type,outcome) VALUES (?,?,'WORKSPACE_PREFERENCES_UPDATED','SUCCESS')",user,org);
        return getForMember(org,user);
    }
}

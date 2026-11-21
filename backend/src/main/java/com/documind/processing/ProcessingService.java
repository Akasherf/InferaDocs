package com.documind.processing;

import com.documind.common.exception.ApiException;
import com.documind.document.service.DocumentService;
import com.documind.organization.domain.OrganizationMembership;
import com.documind.organization.repository.OrganizationMembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessingService {
    private final JdbcTemplate jdbc;
    private final DocumentService documents;
    private final OrganizationMembershipRepository memberships;
    private final TransactionTemplate tx;

    public ProcessingService(JdbcTemplate jdbc, DocumentService documents,
            OrganizationMembershipRepository memberships,
            org.springframework.transaction.PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.documents=documents; this.memberships=memberships;
        this.tx = new TransactionTemplate(manager);
    }

    public record Job(UUID id, UUID documentId, String status, String stage, int attempts,
                      int maxAttempts, Integer chunkCount, String lastError, Instant createdAt,
                      Instant startedAt, Instant finishedAt) {}

    private static Job map(ResultSet rs, int n) throws SQLException {
        return new Job((UUID)rs.getObject("id"),(UUID)rs.getObject("document_id"),
                rs.getString("status"),rs.getString("stage"),rs.getInt("attempts"),
                rs.getInt("max_attempts"),(Integer)rs.getObject("chunk_count"),
                rs.getString("last_error"),rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("started_at")==null?null:rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at")==null?null:rs.getTimestamp("finished_at").toInstant());
    }
    private void authorize(UUID org,UUID user,UUID doc,boolean write) {
        documents.get(org,user,doc);
        if(write) {
            var membership=memberships.findActiveByOrganizationIdAndUserId(org,user).orElseThrow(
                    () -> new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Workspace not found"));
            if(membership.getRole()==OrganizationMembership.MembershipRole.VIEWER)
                throw new ApiException(HttpStatus.FORBIDDEN,"DOCUMENT_PERMISSION_DENIED","Viewer cannot process documents");
        }
    }
    public Job enqueue(UUID org,UUID user,UUID doc) {
        authorize(org,user,doc,true);
        return tx.execute(status -> {
            var existing=jdbc.query("SELECT * FROM document_processing_jobs WHERE organization_id=? AND document_id=? AND status IN ('QUEUED','RUNNING') ORDER BY created_at DESC LIMIT 1",ProcessingService::map,org,doc);
            if(!existing.isEmpty()) return existing.get(0);
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO document_processing_jobs(id,organization_id,document_id,requested_by) VALUES (?,?,?,?)",id,org,doc,user);
            return jdbc.queryForObject("SELECT * FROM document_processing_jobs WHERE id=?",ProcessingService::map,id);
        });
    }
    public List<Job> list(UUID org,UUID user,UUID doc) {
        authorize(org,user,doc,false);
        return jdbc.query("SELECT * FROM document_processing_jobs WHERE organization_id=? AND document_id=? ORDER BY created_at DESC LIMIT 30",ProcessingService::map,org,doc);
    }
    public Job get(UUID org,UUID user,UUID doc,UUID jobId) {
        authorize(org,user,doc,false);
        return jdbc.query("SELECT * FROM document_processing_jobs WHERE organization_id=? AND document_id=? AND id=?",ProcessingService::map,org,doc,jobId)
            .stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"JOB_NOT_FOUND","Processing job not found"));
    }
    public Job retry(UUID org,UUID user,UUID doc,UUID jobId) {
        authorize(org,user,doc,true);
        return tx.execute(status -> {
            int changed=jdbc.update("UPDATE document_processing_jobs SET status='QUEUED',stage='QUEUED',attempts=0,last_error=NULL,finished_at=NULL,next_attempt_at=now(),updated_at=now() WHERE id=? AND organization_id=? AND document_id=? AND status='FAILED'",jobId,org,doc);
            if(changed==0) throw new ApiException(HttpStatus.CONFLICT,"JOB_NOT_FAILED","Only failed jobs can be retried");
            return get(org,user,doc,jobId);
        });
    }
    public record Work(UUID id,UUID org,UUID doc,UUID user,int attempts,int maxAttempts) {}
    public Work claim() {
        return tx.execute(status -> jdbc.query("""
            WITH picked AS (
              SELECT id FROM document_processing_jobs
              WHERE status='QUEUED' AND next_attempt_at <= now()
              ORDER BY next_attempt_at, created_at
              FOR UPDATE SKIP LOCKED LIMIT 1
            )
            UPDATE document_processing_jobs j SET status='RUNNING',stage='INDEXING',
              attempts=j.attempts+1,started_at=now(),updated_at=now()
            FROM picked WHERE j.id=picked.id
            RETURNING j.id,j.organization_id,j.document_id,j.requested_by,j.attempts,j.max_attempts
            """,(rs,n)->new Work((UUID)rs.getObject(1),(UUID)rs.getObject(2),
                (UUID)rs.getObject(3),(UUID)rs.getObject(4),rs.getInt(5),rs.getInt(6)))
            .stream().findFirst().orElse(null));
    }
    public void stage(UUID id,String stage) {
        jdbc.update("UPDATE document_processing_jobs SET stage=?,updated_at=now() WHERE id=? AND status='RUNNING'",stage,id);
    }
    public void success(UUID id,int chunks) {
        jdbc.update("UPDATE document_processing_jobs SET status='SUCCEEDED',stage='COMPLETE',chunk_count=?,last_error=NULL,finished_at=now(),updated_at=now() WHERE id=? AND status='RUNNING'",chunks,id);
    }
    public void failure(Work w,Exception cause) {
        boolean again=w.attempts()<w.maxAttempts();
        // Persist failure details, but do not expose credentials, stack traces or document contents.
        jdbc.update("""
            UPDATE document_processing_jobs SET status=?,stage=?,last_error=?,
              next_attempt_at=now()+(? * interval '1 second'),
              finished_at=CASE WHEN ? THEN NULL ELSE now() END,updated_at=now()
            WHERE id=? AND status='RUNNING'
            """,again?"QUEUED":"FAILED",again?"RETRY_WAIT":"FAILED",
             "Processing failed; inspect server logs ("+cause.getClass().getSimpleName()+")",
             Math.min(300,15*w.attempts()*w.attempts()),again,w.id());
    }
    public int recoverInterrupted() {
        return jdbc.update("UPDATE document_processing_jobs SET status='QUEUED',stage='RECOVERED',next_attempt_at=now(),updated_at=now() WHERE status='RUNNING' AND started_at < now()-interval '10 minutes'");
    }
}

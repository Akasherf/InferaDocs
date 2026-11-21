CREATE TABLE document_processing_jobs (
 id uuid PRIMARY KEY,
 organization_id uuid NOT NULL REFERENCES organizations(id),
 document_id uuid NOT NULL REFERENCES documents(id),
 requested_by uuid NOT NULL REFERENCES users(id),
 status varchar(20) NOT NULL DEFAULT 'QUEUED',
 stage varchar(30) NOT NULL DEFAULT 'QUEUED',
 attempts integer NOT NULL DEFAULT 0,
 max_attempts integer NOT NULL DEFAULT 3,
 chunk_count integer,
 last_error varchar(600),
 created_at timestamptz NOT NULL DEFAULT now(),
 started_at timestamptz,
 finished_at timestamptz,
 next_attempt_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT processing_job_status_chk CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED'))
);
CREATE INDEX idx_doc_processing_ready ON document_processing_jobs(status,next_attempt_at,created_at);
CREATE INDEX idx_doc_processing_tenant ON document_processing_jobs(organization_id,document_id,created_at DESC);

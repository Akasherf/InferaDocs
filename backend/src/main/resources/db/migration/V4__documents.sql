CREATE TABLE documents (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 organization_id UUID NOT NULL REFERENCES organizations(id),
 uploaded_by UUID NOT NULL REFERENCES users(id),
 original_filename VARCHAR(255) NOT NULL,
 content_type VARCHAR(100) NOT NULL,
 file_size BIGINT NOT NULL CHECK (file_size > 0 AND file_size <= 20971520),
 object_key VARCHAR(320) NOT NULL UNIQUE,
 sha256 CHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DELETED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_documents_org_created ON documents(organization_id, created_at DESC);
CREATE INDEX idx_documents_org_status ON documents(organization_id, status);

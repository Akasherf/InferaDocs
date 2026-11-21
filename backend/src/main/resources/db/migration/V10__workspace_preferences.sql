-- Additive workspace preferences; no existing documents are deleted or altered.
-- V9 must exist and be successfully migrated before V10 runs.
CREATE TABLE workspace_preferences (
  organization_id UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
  retention_days INTEGER NOT NULL DEFAULT 0 CHECK (retention_days BETWEEN 0 AND 3650),
  max_upload_mb INTEGER NOT NULL DEFAULT 20 CHECK (max_upload_mb BETWEEN 1 AND 20),
  ai_model VARCHAR(120) NOT NULL DEFAULT 'llama3.1:8b',
  ai_temperature NUMERIC(3,2) NOT NULL DEFAULT 0 CHECK (ai_temperature BETWEEN 0 AND 1),
  ai_top_k INTEGER NOT NULL DEFAULT 6 CHECK (ai_top_k BETWEEN 1 AND 12),
  ai_rerank BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

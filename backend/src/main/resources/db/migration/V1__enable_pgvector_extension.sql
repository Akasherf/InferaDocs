-- Enable pgvector extension for vector similarity search
-- This is the baseline migration for DocuMind AI database schema

CREATE EXTENSION IF NOT EXISTS vector;

-- Verify extension is installed
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RAISE EXCEPTION 'pgvector extension failed to install';
    END IF;
END $$;

-- Create a placeholder table to establish baseline
-- Actual schema tables will be added in future stages
CREATE TABLE IF NOT EXISTS flyway_baseline (
    id BIGSERIAL PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO flyway_baseline (description) VALUES ('Initial baseline for DocuMind AI');
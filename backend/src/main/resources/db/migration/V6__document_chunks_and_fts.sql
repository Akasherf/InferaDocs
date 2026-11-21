-- Stage 4: lexical document retrieval. No embeddings in this migration.
CREATE TABLE document_chunks (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 organization_id UUID NOT NULL REFERENCES organizations(id),
 document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
 chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
 content TEXT NOT NULL CHECK (length(content) > 0),
 search_vector tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
 CONSTRAINT uq_document_chunk_index UNIQUE(document_id,chunk_index)
);
CREATE INDEX idx_document_chunks_organization ON document_chunks(organization_id, document_id);
CREATE INDEX idx_document_chunks_fts ON document_chunks USING GIN(search_vector);

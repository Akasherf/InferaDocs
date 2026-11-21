-- 768 dimensions for nomic-embed-text. Do not change this migration after deployment.
ALTER TABLE document_chunks ADD COLUMN embedding vector(768);
ALTER TABLE document_chunks ADD COLUMN embedding_model varchar(120);
CREATE INDEX idx_document_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

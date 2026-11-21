-- V5: Align documents.sha256 with the JPA entity mapping.
-- Convert fixed-length CHAR(64) to VARCHAR(64).

ALTER TABLE documents
    ALTER COLUMN sha256 TYPE VARCHAR(64)
    USING TRIM(TRAILING FROM sha256);
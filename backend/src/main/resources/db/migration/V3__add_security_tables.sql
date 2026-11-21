-- V3: Add security tables for Stage 2 hardening
-- Revoked tokens (JWT denylist) and login attempts

-- ============================================
-- REVOKED_TOKENS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS revoked_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti VARCHAR(36) NOT NULL UNIQUE,
    revoked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(100)
);

-- Index for JTI lookups (already unique, but explicit for clarity)
CREATE INDEX IF NOT EXISTS revoked_tokens_jti_idx ON revoked_tokens (jti);

-- Index for cleanup of old revoked tokens
CREATE INDEX IF NOT EXISTS revoked_tokens_revoked_at_idx ON revoked_tokens (revoked_at);

-- ============================================
-- LOGIN_ATTEMPTS TABLE (optional - using Redis instead)
-- This table is created for environments without Redis
-- ============================================
CREATE TABLE IF NOT EXISTS login_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier VARCHAR(255) NOT NULL,  -- email:ip or just ip
    attempts INTEGER NOT NULL DEFAULT 1,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for identifier lookups
CREATE INDEX IF NOT EXISTS login_attempts_identifier_idx ON login_attempts (identifier);

-- Index for cleanup
CREATE INDEX IF NOT EXISTS login_attempts_window_idx ON login_attempts (window_start);

-- Apply updated_at trigger
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_login_attempts_updated_at') THEN
        CREATE TRIGGER update_login_attempts_updated_at
            BEFORE UPDATE ON login_attempts
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;
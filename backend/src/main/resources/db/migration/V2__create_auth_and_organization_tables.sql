-- V2: Create authentication and organization tables for Stage 2
-- Users, Organizations, Memberships, Refresh Sessions, Audit Events

-- ============================================
-- USERS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE
);

-- Case-insensitive unique index on email
CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_idx ON users (LOWER(email));

-- Index for status lookups
CREATE INDEX IF NOT EXISTS users_status_idx ON users (status);

-- ============================================
-- ORGANIZATIONS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Globally unique slug
CREATE UNIQUE INDEX IF NOT EXISTS organizations_slug_idx ON organizations (slug);

-- Index for creator lookups
CREATE INDEX IF NOT EXISTS organizations_created_by_idx ON organizations (created_by);

-- Index for status
CREATE INDEX IF NOT EXISTS organizations_status_idx ON organizations (status);

-- ============================================
-- ORGANIZATION_MEMBERSHIPS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS organization_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Unique constraint: one membership per user per organization
CREATE UNIQUE INDEX IF NOT EXISTS organization_memberships_org_user_idx
    ON organization_memberships (organization_id, user_id);

-- Index for user's memberships
CREATE INDEX IF NOT EXISTS organization_memberships_user_idx ON organization_memberships (user_id);

-- Index for organization's memberships
CREATE INDEX IF NOT EXISTS organization_memberships_org_idx ON organization_memberships (organization_id);

-- Index for active memberships
CREATE INDEX IF NOT EXISTS organization_memberships_active_idx
    ON organization_memberships (organization_id, user_id)
    WHERE status = 'ACTIVE';

-- ============================================
-- REFRESH_SESSIONS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS refresh_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,  -- SHA-256 hex = 64 chars
    token_family_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE,
    user_agent TEXT,
    device_label VARCHAR(255),
    rotation_count INTEGER NOT NULL DEFAULT 0
);

-- Index for active session lookup by token hash (for validation)
CREATE INDEX IF NOT EXISTS refresh_sessions_token_hash_idx ON refresh_sessions (token_hash);

-- Index for user's sessions
CREATE INDEX IF NOT EXISTS refresh_sessions_user_idx ON refresh_sessions (user_id);

-- Index for token family (for rotation/reuse detection)
CREATE INDEX IF NOT EXISTS refresh_sessions_family_idx ON refresh_sessions (token_family_id);

-- Index for cleanup of expired sessions
CREATE INDEX IF NOT EXISTS refresh_sessions_expires_idx ON refresh_sessions (expires_at);

-- ============================================
-- AUDIT_EVENTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    event_type VARCHAR(100) NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE', 'ERROR')),
    request_id UUID,
    metadata JSONB,
    source_ip VARCHAR(255),
    target_entity_type VARCHAR(100),
    target_entity_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for actor's audit trail
CREATE INDEX IF NOT EXISTS audit_events_actor_idx ON audit_events (actor_user_id);

-- Index for organization audit trail
CREATE INDEX IF NOT EXISTS audit_events_org_idx ON audit_events (organization_id);

-- Index for event type queries
CREATE INDEX IF NOT EXISTS audit_events_type_idx ON audit_events (event_type);

-- Index for time-range queries
CREATE INDEX IF NOT EXISTS audit_events_created_idx ON audit_events (created_at DESC);

-- Index for request correlation
CREATE INDEX IF NOT EXISTS audit_events_request_idx ON audit_events (request_id);

-- ============================================
-- UPDATED_AT TRIGGER FUNCTION
-- ============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply updated_at triggers (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_users_updated_at') THEN
        CREATE TRIGGER update_users_updated_at
            BEFORE UPDATE ON users
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_organizations_updated_at') THEN
        CREATE TRIGGER update_organizations_updated_at
            BEFORE UPDATE ON organizations
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_organization_memberships_updated_at') THEN
        CREATE TRIGGER update_organization_memberships_updated_at
            BEFORE UPDATE ON organization_memberships
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;
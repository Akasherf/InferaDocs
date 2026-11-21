-- Stage 5: organization-scoped persistent AI conversations
CREATE TABLE ai_conversations (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
 owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 title VARCHAR(120) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(id, organization_id, owner_user_id)
);
CREATE INDEX idx_ai_conversations_owner ON ai_conversations(organization_id, owner_user_id, updated_at DESC);
CREATE TABLE ai_messages (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
 role VARCHAR(12) NOT NULL CHECK (role IN ('USER','ASSISTANT')),
 content TEXT NOT NULL CHECK(length(content) BETWEEN 1 AND 20000),
 grounded BOOLEAN,
 citations_json JSONB NOT NULL DEFAULT '[]'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_messages_conversation ON ai_messages(conversation_id,created_at,id);

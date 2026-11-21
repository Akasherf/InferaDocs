package com.documind.rag;

import com.documind.common.exception.ApiException;
import com.documind.organization.repository.OrganizationMembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {
    public record Conversation(UUID id, String title, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record Message(UUID id, String role, String content, Boolean grounded, List<RagService.Citation> citations, OffsetDateTime createdAt) {}
    public record Turn(RagService.AskResponse answer, UUID conversationId) {}
    private final JdbcTemplate jdbc;
    private final OrganizationMembershipRepository memberships;
    private final RagService rag;
    private final JsonMapper json;
    private final TransactionTemplate tx;
    public ConversationService(JdbcTemplate jdbc, OrganizationMembershipRepository memberships, RagService rag, JsonMapper json, TransactionTemplate tx) {
        this.jdbc=jdbc; this.memberships=memberships; this.rag=rag; this.json=json; this.tx=tx;
    }
    private void member(UUID org, UUID user) {
        if(user==null || memberships.findActiveByOrganizationIdAndUserId(org,user).isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Workspace not found");
    }
    private void owner(UUID org,UUID user,UUID conversation) {
        member(org,user);
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversations WHERE id=? AND organization_id=? AND owner_user_id=?",Integer.class,conversation,org,user);
        if(n==null||n==0) throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Conversation not found");
    }
    @Transactional(readOnly=true)
    public List<Conversation> list(UUID org, UUID user) {
        member(org,user);
        return jdbc.query("SELECT id,title,created_at,updated_at FROM ai_conversations WHERE organization_id=? AND owner_user_id=? ORDER BY updated_at DESC LIMIT 100",
            (rs,i)->new Conversation((UUID)rs.getObject(1),rs.getString(2),rs.getObject(3,OffsetDateTime.class),rs.getObject(4,OffsetDateTime.class)),org,user);
    }
    @Transactional
    public Conversation create(UUID org, UUID user, String title) {
        member(org,user);
        UUID id=UUID.randomUUID(); String name=validTitle(title);
        jdbc.update("INSERT INTO ai_conversations(id,organization_id,owner_user_id,title) VALUES (?,?,?,?)",id,org,user,name);
        return jdbc.queryForObject("SELECT id,title,created_at,updated_at FROM ai_conversations WHERE id=?",
            (rs,i)->new Conversation((UUID)rs.getObject(1),rs.getString(2),rs.getObject(3,OffsetDateTime.class),rs.getObject(4,OffsetDateTime.class)),id);
    }
    private String validTitle(String input) {
        if(input==null||input.isBlank()||input.strip().length()>120) throw ApiException.badRequest("INVALID_TITLE","Title must contain 1–120 characters");
        return input.strip();
    }
    @Transactional
    public void rename(UUID org,UUID user,UUID id,String title) {
        owner(org,user,id);
        jdbc.update("UPDATE ai_conversations SET title=?, updated_at=now() WHERE id=? AND organization_id=? AND owner_user_id=?",validTitle(title),id,org,user);
    }
    @Transactional
    public void delete(UUID org,UUID user,UUID id) {
        owner(org,user,id);
        jdbc.update("DELETE FROM ai_conversations WHERE id=? AND organization_id=? AND owner_user_id=?",id,org,user);
    }
    @Transactional(readOnly=true)
    public List<Message> messages(UUID org,UUID user,UUID id) {
        owner(org,user,id);
        return jdbc.query("SELECT id,role,content,grounded,citations_json::text,created_at FROM ai_messages WHERE conversation_id=? ORDER BY created_at,id LIMIT 500",
            (rs,i)->new Message((UUID)rs.getObject(1),rs.getString(2),rs.getString(3),(Boolean)rs.getObject(4),parseCitations(rs.getString(5)),rs.getObject(6,OffsetDateTime.class)),id);
    }
    private List<RagService.Citation> parseCitations(String jsonText) {
        try { return json.readValue(jsonText,json.getTypeFactory().constructCollectionType(List.class,RagService.Citation.class)); }
        catch(Exception ex) {throw new IllegalStateException("Stored citations cannot be decoded",ex);}
    }
    // LLM is called before writes: provider errors never create incomplete conversation turns.
    // Re-authorize when saving to prevent writes after membership revocation during generation.
    public RagService.AskResponse ask(UUID org,UUID user,UUID id,String question) {
        owner(org,user,id);
        // Read from the same authorized conversation, never from a browser-provided history.
        List<RagService.ContextTurn> context = recentContext(org,user,id);
        RagService.AskResponse answer=rag.askContextual(org,user,new RagService.AskRequest(question),context);
        saveTurn(org,user,id,question,answer);
        return answer;
    }
    @Transactional(readOnly=true)
    public List<RagService.ContextTurn> recentContext(UUID org, UUID user, UUID id) {
        owner(org,user,id);
        // Explicit ordering: the latest eight messages are re-ordered chronologically.
        return jdbc.query("SELECT role,content FROM (SELECT id,role,content,created_at " +
            "FROM ai_messages WHERE conversation_id=? ORDER BY created_at DESC,id DESC LIMIT 8) latest " +
            "ORDER BY created_at ASC,id ASC",
            (rs,i) -> new RagService.ContextTurn(rs.getString(1),rs.getString(2)),id);
    }
    public void saveTurn(UUID org,UUID user,UUID id,String question,RagService.AskResponse answer) {
        tx.executeWithoutResult(status -> {
        owner(org,user,id);
        try {
            jdbc.update("INSERT INTO ai_messages(conversation_id,role,content) VALUES (?,'USER',?)",id,question.strip());
            jdbc.update("INSERT INTO ai_messages(conversation_id,role,content,grounded,citations_json) VALUES (?,'ASSISTANT',?,?,CAST(? AS jsonb))",
                id,answer.answer(),answer.grounded(),json.writeValueAsString(answer.citations()));
            jdbc.update("UPDATE ai_conversations SET updated_at=now() WHERE id=? AND organization_id=? AND owner_user_id=?",id,org,user);
        } catch(com.documind.common.exception.ApiException ex) {throw ex;}
        catch(Exception ex) {throw new IllegalStateException("Could not save AI conversation",ex);}
        });
    }
}

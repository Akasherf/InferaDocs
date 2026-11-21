package com.documind.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContextRetrievalTest {
    @Test void enforcesSearchServiceMaxQueryLength() {
        String longQuery = "PostgreSQL project ".repeat(30);
        assertEquals(250, RagService.boundedSearchQuery(longQuery).length());
        assertFalse(RagService.boundedSearchQuery("   PostgreSQL project  ").isBlank());
    }
    @Test void longAssistantAnswerDoesNotOverflowAnchorQuery() {
        var history = List.of(new RagService.ContextTurn("ASSISTANT", "ShopKart ".repeat(75)));
        var query = RagService.contextSearchQuery("Explain that project in detail", history);
        assertTrue(query.length() <= 250);
        assertTrue(query.contains("Explain that project in detail"));
    }
    @Test void detectsFollowupReferences() {
        assertTrue(RagService.isContextDependent("Which one uses PostgreSQL?"));
        assertTrue(RagService.isContextDependent("Explain that project in detail."));
        assertFalse(RagService.isContextDependent("List all projects in my resume."));
    }

    @Test void anchorsPronounsToLastSupportedAnswer() {
        var history = List.of(new RagService.ContextTurn("USER", "List projects"),
            new RagService.ContextTurn("ASSISTANT", "ShopKart — E-Commerce Backend [2]"));
        var query = RagService.contextSearchQuery("Explain that project", history);
        assertTrue(query.contains("ShopKart"));
        assertFalse(query.contains("[2]"));
    }
    @Test void doesNotTreatInsufficientAnswerAsAnchor() {
        var history = List.of(new RagService.ContextTurn("USER", "What projects?"),
            new RagService.ContextTurn("ASSISTANT", "I couldn't find enough information in the indexed documents to answer that question."));
        assertTrue(RagService.contextSearchQuery("Explain that project", history).contains("What projects?"));
    }
}

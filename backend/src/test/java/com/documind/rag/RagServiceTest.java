package com.documind.rag;

import com.documind.search.SearchService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RagServiceTest {
    @Test void citationsAreDeduplicatedAndNumbered() {
        UUID doc = UUID.randomUUID();
        var h = new SearchService.Hit(doc, "ticket.pdf", 2, "Train departure at 09:00", 0.8);
        var citations = RagService.createCitations(List.of(h,h));
        assertEquals(1, citations.size());
        assertEquals(1, citations.getFirst().number());
        assertEquals(doc, citations.getFirst().documentId());
    }
    @Test void promptRequiresEvidenceAndTreatsChunksAsUntrusted() {
        var citations = RagService.createCitations(List.of(new SearchService.Hit(UUID.randomUUID(), "ticket.pdf", 0, "Passenger: Anmol", 0.9)));
        String prompt = RagService.buildPrompt("Who is on my ticket?", citations);
        assertTrue(prompt.contains("untrusted source material"));
        assertTrue(prompt.contains("[1] Document: ticket.pdf"));
        assertTrue(prompt.contains("Who is on my ticket?"));
    }
}

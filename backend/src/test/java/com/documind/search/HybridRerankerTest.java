package com.documind.search;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HybridRerankerTest {
    private static final UUID DOC = UUID.fromString("64c23f2c-0b5a-4861-8183-c8c3d6e02727");

    @Test void maintainsOriginalIdentityAndDoesNotInventHits() {
        var a = new SearchService.Hit(DOC, "resume.pdf", 0, "Software development overview", .032);
        var b = new SearchService.Hit(DOC, "resume.pdf", 1, "ShopKart payment confirmation", .031);
        var results = HybridReranker.rerank("ShopKart payment", List.of(a,b), 2);
        assertEquals(2, results.size());
        assertTrue(results.contains(a));
        assertTrue(results.contains(b));
    }

    @Test void handlesEmptyEvidenceAndLimit() {
        assertTrue(HybridReranker.rerank("question", List.of(), 5).isEmpty());
        var hit = new SearchService.Hit(DOC, "resume.pdf", 1, "", .05);
        assertEquals(List.of(hit), HybridReranker.rerank("question", List.of(hit), 1));
        assertThrows(IllegalArgumentException.class, () -> HybridReranker.rerank("q", List.of(hit), 0));
    }

    @Test void preservesOrderWhenQueryHasNoContentWords() {
        var a = new SearchService.Hit(DOC, "resume.pdf", 0, "first", .032);
        var b = new SearchService.Hit(DOC, "resume.pdf", 1, "second", .031);
        assertEquals(List.of(a), HybridReranker.rerank("what is the", List.of(a,b), 1));
    }
}

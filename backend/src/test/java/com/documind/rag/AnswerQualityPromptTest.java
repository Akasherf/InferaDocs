package com.documind.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AnswerQualityPromptTest {
    private String prompt(String question) {
        var cites = List.of(new RagService.Citation(1, UUID.randomUUID(), "resume.pdf", 1,
            "Projects ShopKart — E-Commerce Backend Java, Spring Boot, MySQL. " +
            "Team Task Manager Java, Spring Boot, MySQL. " +
            "Skills: MySQL, PostgreSQL. Developed REST APIs and authentication."));
        return RagService.buildPrompt(question, cites);
    }
    @Test void distinguishesProjectsFromImplementationBullets() {
        String p = prompt("Which projects use Java?");
        assertTrue(p.contains("PROJECT-ENTITY EXTRACTION RULES (Stage 6A)"));
        assertTrue(p.contains("Never list individual feature bullets as additional projects"));
        assertTrue(p.contains("Deduplicate repeated project names"));
        assertTrue(p.contains("[1] Document: resume.pdf"));
    }
    @Test void requiresProjectSpecificTechnologyEvidence() {
        String p = prompt("Which project uses PostgreSQL?");
        assertTrue(p.contains("general Skills section is NOT evidence"));
        assertTrue(p.contains("explain the missing link"));
        assertTrue(p.contains("never choose a nearby project by assumption"));
    }
    @Test void contextualPromptPreservesGrounding() {
        var c = List.of(new RagService.Citation(1, UUID.randomUUID(), "resume.pdf", 1,
            "ShopKart uses MySQL"));
        String p = RagService.buildContextualAnswerPrompt("Explain it", "Explain ShopKart", c);
        assertTrue(p.contains("PROJECT-ENTITY EXTRACTION RULES (Stage 6A)"));
        assertTrue(p.contains("REFERENT HINT"));
        assertTrue(p.contains("Use only SOURCE EXCERPTS as factual evidence"));
    }
}

package com.documind.rag;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContextPromptTest {
    @Test void includesLastEightTurnsOnly() {
        List<RagService.ContextTurn> history = new ArrayList<>();
        for (int i=0;i<10;i++) history.add(new RagService.ContextTurn("USER", "question" + i));
        String prompt = RagService.buildRewritePrompt("Which one uses PostgreSQL?", history);
        assertFalse(prompt.contains("question0"));
        assertFalse(prompt.contains("question1"));
        assertTrue(prompt.contains("question2"));
        assertTrue(prompt.contains("question9"));
        assertTrue(prompt.contains("CURRENT QUESTION: Which one uses PostgreSQL?"));
    }

    @Test void labelsHistoryAsUntrustedAndLimitsMessageSize() {
        String longMessage = "X".repeat(1500);
        String prompt = RagService.buildRewritePrompt("Explain it", List.of(new RagService.ContextTurn("ASSISTANT",longMessage)));
        assertTrue(prompt.contains("untrusted"));
        assertTrue(prompt.contains("X".repeat(1200)));
        assertFalse(prompt.contains("X".repeat(1201)));
    }
}

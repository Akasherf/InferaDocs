package com.documind.search;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {
    @Test void vectorSerialization() {
        assertEquals("[0.5,-1.25,0.0]", EmbeddingService.vectorLiteral(new float[]{0.5f,-1.25f,0f}));
    }
    @Test void invalidVectorRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EmbeddingService.vectorLiteral(new float[]{Float.NaN}));
    }
}

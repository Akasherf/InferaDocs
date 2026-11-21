package com.documind.search;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class BoundaryChunkingTest {
 @Test void splitsAtWordBoundaryWhenAvailable() {
   var chunks = new TextChunker().chunk(("alpha ").repeat(300));
   assertThat(chunks).hasSizeGreaterThan(1);
   assertThat(chunks.get(0)).endsWith("alpha");
   assertThat(chunks.get(1)).startsWith("alpha");
 }
}

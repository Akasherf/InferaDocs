package com.documind.search;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class TextChunkerTest {
 private final TextChunker chunker=new TextChunker();
 @Test void emptyTextProducesNoChunks(){assertThat(chunker.chunk(" ")).isEmpty();}
 @Test void shortTextHasOneChunk(){assertThat(chunker.chunk("hello world")).containsExactly("hello world");}
 @Test void longTextHasOverlappingChunks(){var chunks=chunker.chunk("x".repeat(1600));assertThat(chunks).hasSize(2);assertThat(chunks.get(0)).hasSize(1200);assertThat(chunks.get(1)).hasSize(600);}
}

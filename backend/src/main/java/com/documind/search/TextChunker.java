package com.documind.search;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {
    // Character-budgeted chunks with overlap aligned to word boundaries.
    public List<String> chunk(String text) {
        List<String> output = new ArrayList<>();
        if (text == null || text.isBlank()) return output;
        final int size = 1200, overlap = 200, maxChunks = 2500;
        String normalized = text.replaceAll("\\s+", " ").trim();
        int start = 0;
        while (start < normalized.length()) {
            if (output.size() >= maxChunks) throw new IllegalArgumentException("Too many chunks");
            int end = Math.min(start + size, normalized.length());
            if (end < normalized.length()) {
                // Prefer ending after a complete word when close to the target size.
                int boundary = normalized.lastIndexOf(' ', end);
                if (boundary > start + size * 85 / 100) end = boundary;
            }
            output.add(normalized.substring(start, end));
            if (end == normalized.length()) break;

            int nextStart = Math.max(start + 1, end - overlap);
            // If the overlap starts inside a word, advance to the next word.
            if (nextStart > 0 && normalized.charAt(nextStart - 1) != ' ') {
                int nextSpace = normalized.indexOf(' ', nextStart);
                if (nextSpace >= 0 && nextSpace + 1 < end) {
                    nextStart = nextSpace + 1;
                } else {
                    // Very long token: ensure forward progress, even without a word boundary.
                    nextStart = Math.max(start + 1, end - overlap);
                }
            }
            start = nextStart;
        }
        return output;
    }
}

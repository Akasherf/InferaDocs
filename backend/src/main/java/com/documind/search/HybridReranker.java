package com.documind.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Experimental, deterministic lexical reranker for authorized hybrid-search candidates.
 * It does not fetch documents, generate text, or change source/citation identifiers.
 * A low lexical weight retains the original RRF ranking as a strong prior.
 */
public final class HybridReranker {
    private HybridReranker() {}

    static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "and", "are", "as", "at", "be", "by", "do", "does", "for", "from",
        "how", "in", "is", "it", "of", "on", "or", "the", "their", "to", "what",
        "when", "where", "which", "who", "with", "your", "my", "use", "uses", "used"
    );

    private static Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        if (value == null) return result;
        for (String word : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}+#.]+")) {
            if (word.length() > 1 && !STOP_WORDS.contains(word)) result.add(word);
        }
        return result;
    }

    public static List<SearchService.Hit> rerank(String query, List<SearchService.Hit> candidates, int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        if (candidates == null || candidates.isEmpty()) return List.of();
        Set<String> queryWords = tokens(query);
        if (queryWords.isEmpty()) return List.copyOf(candidates.subList(0, Math.min(limit, candidates.size())));
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            SearchService.Hit hit = candidates.get(i);
            Set<String> evidenceWords = tokens(hit.filename() + " " + hit.excerpt());
            long matched = queryWords.stream().filter(evidenceWords::contains).count();
            double coverage = (double) matched / queryWords.size();
            // Original RRF order dominates; lexical coverage breaks close ranks.
            double rankPrior = 1.0 / (1.0 + i);
            double score = 0.7 * rankPrior + 0.3 * coverage;
            scored.add(new Scored(hit, i, score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparingInt(Scored::index));
        return scored.stream().limit(limit).map(Scored::hit).toList();
    }

    private record Scored(SearchService.Hit hit, int index, double score) {}
}

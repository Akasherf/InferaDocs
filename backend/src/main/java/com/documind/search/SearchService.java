package com.documind.search;

import com.documind.common.exception.ApiException;
import com.documind.document.service.DocumentService;
import com.documind.document.storage.DocumentStorage;
import com.documind.organization.repository.OrganizationMembershipRepository;
import com.documind.organization.domain.OrganizationMembership;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class SearchService {
    private final EmbeddingService embeddings;
    private final DocumentService documents;
    private final DocumentStorage storage;
    private final ContentExtractor extractor;
    private final TextChunker chunker;
    private final JdbcTemplate jdbc;
    private final OrganizationMembershipRepository memberships;

    public SearchService(DocumentService documents, DocumentStorage storage, ContentExtractor extractor, TextChunker chunker, JdbcTemplate jdbc, OrganizationMembershipRepository memberships, EmbeddingService embeddings) {
        this.documents=documents; this.storage=storage; this.extractor=extractor; this.chunker=chunker; this.jdbc=jdbc; this.memberships=memberships; this.embeddings=embeddings;
    }
    public record IndexResult(UUID documentId, int chunks) {}
    public record Hit(UUID documentId, String filename, int chunkIndex, String excerpt, double score) {}

    // Only members with write privileges can initiate indexing; source is always the authorized document.
    @Transactional
    public IndexResult index(UUID organizationId, UUID userId, UUID documentId) {
        var document=documents.get(organizationId, userId, documentId);
        var member=memberships.findActiveByOrganizationIdAndUserId(organizationId,userId)
            .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Workspace not found"));
        if(member.getRole()==OrganizationMembership.MembershipRole.VIEWER)
            throw new ApiException(HttpStatus.FORBIDDEN,"DOCUMENT_PERMISSION_DENIED","Viewer cannot index documents");
        byte[] content;
        try { content=storage.get(document.getObjectKey()); }
        catch (Exception e) { throw new ApiException(HttpStatus.BAD_GATEWAY,"STORAGE_ERROR","Unable to load document from object storage"); }
        String text=extractor.extract(content, document.getContentType());
        if (text.isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"NO_EXTRACTABLE_TEXT","No text was found. Scanned PDFs require OCR, which is not yet implemented.");
        List<String> chunks;
        try { chunks=chunker.chunk(text); }
        catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"CHUNK_LIMIT","Too many text chunks"); }
        jdbc.update("DELETE FROM document_chunks WHERE organization_id = ? AND document_id = ?", organizationId, documentId);
        for (int i=0;i<chunks.size();i++) {
            jdbc.update("INSERT INTO document_chunks (organization_id, document_id, chunk_index, content) VALUES (?, ?, ?, ?)", organizationId, documentId, i, chunks.get(i));
        }
        return new IndexResult(documentId,chunks.size());
    }

    // Explicit backfill supports existing Stage 4A chunks and avoids requiring Ollama for keyword indexing.
    @Transactional
    public IndexResult embedDocument(UUID organizationId, UUID userId, UUID documentId) {
        var document = documents.get(organizationId, userId, documentId);
        var member = memberships.findActiveByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Workspace not found"));
        if (member.getRole() == OrganizationMembership.MembershipRole.VIEWER)
            throw new ApiException(HttpStatus.FORBIDDEN, "DOCUMENT_PERMISSION_DENIED", "Viewer cannot embed documents");
        var rows = jdbc.query("""
            SELECT id, content FROM document_chunks WHERE organization_id=? AND document_id=? ORDER BY chunk_index
            """, (rs, n) -> new ChunkRow(rs.getLong("id"), rs.getString("content")), organizationId, documentId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "NOT_INDEXED", "Index document text before creating embeddings");
        for (ChunkRow row : rows) {
            String vector = EmbeddingService.vectorLiteral(embeddings.embed(row.content()));
            jdbc.update("UPDATE document_chunks SET embedding=CAST(? AS vector), embedding_model=? WHERE id=? AND organization_id=? AND document_id=?",
                    vector, embeddings.model(), row.id(), organizationId, documentId);
        }
        return new IndexResult(documentId, rows.size());
    }

    private record ChunkRow(long id, String content) {}

    @Transactional(readOnly=true)
    public List<Hit> semanticSearch(UUID organizationId, UUID userId, String query, int limit) {
        validateSearch(organizationId, userId, query, limit);
        String vector = EmbeddingService.vectorLiteral(embeddings.embed(query));
        return jdbc.query("""
            SELECT dc.document_id, d.original_filename, dc.chunk_index, left(dc.content,350) AS excerpt,
                   1 - (dc.embedding <=> CAST(? AS vector)) AS score
            FROM document_chunks dc JOIN documents d
              ON d.id=dc.document_id AND d.organization_id=dc.organization_id
            WHERE dc.organization_id=? AND d.status='ACTIVE' AND dc.embedding IS NOT NULL
              AND dc.embedding_model=?
            ORDER BY dc.embedding <=> CAST(? AS vector), dc.document_id, dc.chunk_index LIMIT ?
            """, (rs,n)->new Hit((UUID)rs.getObject("document_id"),rs.getString("original_filename"),
                    rs.getInt("chunk_index"),rs.getString("excerpt"),rs.getDouble("score")),
                vector, organizationId, embeddings.model(), vector, limit);
    }

    @Transactional(readOnly=true)
    public List<Hit> hybridSearch(UUID organizationId, UUID userId, String query, int limit) {
        validateSearch(organizationId, userId, query, limit);
        List<Hit> keyword = search(organizationId, userId, query, 50);
        List<Hit> semantic = semanticSearch(organizationId, userId, query, 50);
        java.util.Map<String, Hit> hits = new java.util.LinkedHashMap<>();
        java.util.Map<String, Double> scores = new java.util.HashMap<>();
        accumulate(keyword, hits, scores);
        accumulate(semantic, hits, scores);
        return hits.entrySet().stream().map(e -> {
            Hit h=e.getValue();
            return new Hit(h.documentId(), h.filename(), h.chunkIndex(), h.excerpt(), scores.get(e.getKey()));
        }).sorted(java.util.Comparator.comparingDouble(Hit::score).reversed()
          .thenComparing(Hit::documentId).thenComparingInt(Hit::chunkIndex)).limit(limit).toList();
    }

    /** Optional reranking for experiments; the existing hybridSearch path remains the default. */
    @Transactional(readOnly=true)
    public List<Hit> hybridSearch(UUID organizationId, UUID userId, String query, int limit, boolean rerank) {
        if (!rerank) return hybridSearch(organizationId, userId, query, limit);
        validateSearch(organizationId, userId, query, limit);
        // Candidate retrieval remains subject to the existing tenant and active-document filters.
        List<Hit> candidates = hybridSearch(organizationId, userId, query, Math.min(50, Math.max(limit, 20)));
        return HybridReranker.rerank(query, candidates, limit);
    }

    private void accumulate(List<Hit> ranked, java.util.Map<String, Hit> hits, java.util.Map<String, Double> scores) {
        for (int i=0; i<ranked.size(); i++) {
            Hit h=ranked.get(i); String key=h.documentId()+":"+h.chunkIndex();
            hits.putIfAbsent(key,h);
            scores.merge(key,1.0/(60+i+1),Double::sum);
        }
    }

    /** Full authorized evidence for RAG; UI search previews intentionally remain 350 characters.
     * Includes a neighboring chunk when available, preserving citations to the actual chunk.
     * Never retrieves a chunk without both tenant and ACTIVE-document constraints.
     */
    @Transactional(readOnly=true)
    public List<Hit> evidence(UUID organizationId, UUID userId, List<Hit> rankedHits, int limit) {
        documents.list(organizationId, userId); // same membership guard as public search
        if (limit < 1 || limit > 20) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "Evidence limit must be between 1 and 20");
        java.util.LinkedHashMap<String, Hit> expanded = new java.util.LinkedHashMap<>();
        // Fetch all direct matches first, so adjacent context cannot displace relevant hits.
        for (Hit hit : rankedHits) {
            if (expanded.size() >= limit) break;
            addEvidence(organizationId, hit, hit.chunkIndex(), expanded, limit);
        }
        for (Hit hit : rankedHits) {
            if (expanded.size() >= limit) break;
            if (hit.chunkIndex() > 0) addEvidence(organizationId, hit, hit.chunkIndex() - 1, expanded, limit);
            if (expanded.size() < limit) addEvidence(organizationId, hit, hit.chunkIndex() + 1, expanded, limit);
        }
        return new java.util.ArrayList<>(expanded.values());
    }

    private void addEvidence(UUID org, Hit hit, int chunkIndex,
                             java.util.Map<String, Hit> found, int limit) {
        if (found.size() >= limit || chunkIndex < 0) return;
        String key = hit.documentId() + ":" + chunkIndex;
        if (found.containsKey(key)) return;
        jdbc.query("""
            SELECT dc.document_id, d.original_filename, dc.chunk_index, dc.content AS excerpt
            FROM document_chunks dc JOIN documents d
              ON d.id=dc.document_id AND d.organization_id=dc.organization_id
            WHERE dc.organization_id=? AND dc.document_id=? AND dc.chunk_index=?
              AND d.status='ACTIVE'
            """, rs -> {
                found.putIfAbsent(key, new Hit((UUID)rs.getObject("document_id"),
                    rs.getString("original_filename"), rs.getInt("chunk_index"),
                    rs.getString("excerpt"), hit.score()));
            }, org, hit.documentId(), chunkIndex);
    }

    private void validateSearch(UUID organizationId, UUID userId, String query, int limit) {
        documents.list(organizationId,userId);
        if (query==null || query.isBlank() || query.length()>250)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","Search query must contain 1–250 characters");
        if (limit<1 || limit>50)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_LIMIT","Limit must be between 1 and 50");
    }

    @Transactional(readOnly=true)
    public List<Hit> search(UUID organizationId,UUID userId,String query,int limit) {
        validateSearch(organizationId,userId,query,limit);
        return jdbc.query("""
            SELECT dc.document_id, d.original_filename, dc.chunk_index,
                   left(dc.content, 350) AS excerpt,
                   ts_rank_cd(dc.search_vector, websearch_to_tsquery('english', ?)) AS score
            FROM document_chunks dc
            JOIN documents d ON d.id=dc.document_id AND d.organization_id=dc.organization_id
            WHERE dc.organization_id=? AND d.status='ACTIVE'
              AND dc.search_vector @@ websearch_to_tsquery('english', ?)
            ORDER BY score DESC, dc.document_id, dc.chunk_index
            LIMIT ?
            """, (rs,row)->new Hit((UUID)rs.getObject("document_id"),rs.getString("original_filename"),rs.getInt("chunk_index"),rs.getString("excerpt"),rs.getDouble("score")), query,organizationId,query,limit);
    }
}

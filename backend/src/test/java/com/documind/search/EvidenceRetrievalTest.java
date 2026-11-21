package com.documind.search;

import com.documind.document.service.DocumentService;
import com.documind.document.storage.DocumentStorage;
import com.documind.organization.repository.OrganizationMembershipRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EvidenceRetrievalTest {
    @Test void evidenceQueriesKeepTenantAndActiveDocumentConstraints() {
        var docs = mock(DocumentService.class);
        var jdbc = mock(JdbcTemplate.class);
        var svc = new SearchService(docs, mock(DocumentStorage.class),
            mock(ContentExtractor.class), mock(TextChunker.class), jdbc,
            mock(OrganizationMembershipRepository.class), mock(EmbeddingService.class));
        UUID org = UUID.randomUUID(), user = UUID.randomUUID(), document = UUID.randomUUID();
        var hit = new SearchService.Hit(document, "resume.pdf", 2, "preview", 0.5);
        assertThat(svc.evidence(org, user, List.of(hit), 12)).isEmpty();
        verify(docs).list(org, user);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(3)).query(sql.capture(), any(RowCallbackHandler.class),
            eq(org), eq(document), anyInt());
        assertThat(sql.getAllValues()).allSatisfy(q -> {
            assertThat(q).contains("dc.organization_id=?", "dc.document_id=?", "d.status='ACTIVE'");
        });
    }
}

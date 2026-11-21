package com.documind.processing;

import com.documind.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessingWorker {
    private static final Logger log=LoggerFactory.getLogger(ProcessingWorker.class);
    private final ProcessingService jobs;
    private final SearchService search;
    public ProcessingWorker(ProcessingService jobs,SearchService search){this.jobs=jobs;this.search=search;}

    @Scheduled(fixedDelayString="${documind.processing.poll-ms:2000}")
    public void poll() {
        try {
            var job=jobs.claim();
            if(job==null) return;
            try {
                // Do not recreate existing chunks when only embeddings are missing.
                jobs.stage(job.id(),"INDEXING");
                int chunks=search.index(job.org(),job.user(),job.doc()).chunks();
                jobs.stage(job.id(),"EMBEDDING");
                search.embedDocument(job.org(),job.user(),job.doc());
                jobs.success(job.id(),chunks);
            } catch(Exception e) {
                log.warn("Document processing job {} failed: {}",job.id(),e.toString());
                jobs.failure(job,e);
            }
        } catch(Exception e) { log.error("Processing worker poll failed",e); }
    }
}

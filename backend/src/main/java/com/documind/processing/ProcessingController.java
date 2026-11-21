package com.documind.processing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/organizations/{organizationId}/documents/{documentId}/processing")
public class ProcessingController {
    private final ProcessingService jobs;
    public ProcessingController(ProcessingService jobs){this.jobs=jobs;}
    private UUID user(HttpServletRequest req){return (UUID)req.getAttribute("userId");}

    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public ProcessingService.Job enqueue(@PathVariable("organizationId") UUID organizationId,@PathVariable("documentId") UUID documentId,HttpServletRequest req){
        return jobs.enqueue(organizationId,user(req),documentId);
    }
    @GetMapping
    public List<ProcessingService.Job> list(@PathVariable("organizationId") UUID organizationId,@PathVariable("documentId") UUID documentId,HttpServletRequest req){
        return jobs.list(organizationId,user(req),documentId);
    }
    @GetMapping("/{jobId}")
    public ProcessingService.Job get(@PathVariable("organizationId") UUID organizationId,@PathVariable("documentId") UUID documentId,@PathVariable("jobId") UUID jobId,HttpServletRequest req){
        return jobs.get(organizationId,user(req),documentId,jobId);
    }
    @PostMapping("/{jobId}/retry")
    public ProcessingService.Job retry(@PathVariable("organizationId") UUID organizationId,@PathVariable("documentId") UUID documentId,@PathVariable("jobId") UUID jobId,HttpServletRequest req){
        return jobs.retry(organizationId,user(req),documentId,jobId);
    }
}

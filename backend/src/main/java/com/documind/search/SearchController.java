package com.documind.search;

import com.documind.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/organizations/{organizationId}")
public class SearchController {
    private final SearchService service;
    public SearchController(SearchService service) { this.service=service; }
    private UUID user(HttpServletRequest request) { return (UUID) request.getAttribute("userId"); }

    @PostMapping("/documents/{documentId}/index")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<SearchService.IndexResult> index(@PathVariable("organizationId") UUID organizationId, @PathVariable("documentId") UUID documentId, HttpServletRequest request) {
        return ApiResponse.success(service.index(organizationId, user(request), documentId));
    }
    @PostMapping("/documents/{documentId}/embed")
    public ApiResponse<SearchService.IndexResult> embed(@PathVariable("organizationId") UUID organizationId,
            @PathVariable("documentId") UUID documentId, HttpServletRequest request) {
        return ApiResponse.success(service.embedDocument(organizationId, user(request), documentId));
    }

    @GetMapping("/semantic-search")
    public ApiResponse<List<SearchService.Hit>> semantic(@PathVariable("organizationId") UUID organizationId,
            @RequestParam("q") String q, @RequestParam(value="limit", defaultValue="10") int limit,
            HttpServletRequest request) {
        return ApiResponse.success(service.semanticSearch(organizationId, user(request), q, limit));
    }

    @GetMapping("/hybrid-search")
    public ApiResponse<List<SearchService.Hit>> hybrid(@PathVariable("organizationId") UUID organizationId,
            @RequestParam("q") String q, @RequestParam(value="limit", defaultValue="10") int limit,
            @RequestParam(value="rerank", defaultValue="false") boolean rerank,
            HttpServletRequest request) {
        return ApiResponse.success(service.hybridSearch(organizationId, user(request), q, limit, rerank));
    }

    @GetMapping("/search")
    public ApiResponse<List<SearchService.Hit>> search(@PathVariable("organizationId") UUID organizationId, @RequestParam("q") String q, @RequestParam(value = "limit", defaultValue = "10") int limit, HttpServletRequest request) {
        return ApiResponse.success(service.search(organizationId, user(request), q, limit));
    }
}

package com.documind.rag;

import com.documind.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/organizations/{organizationId}/ai")
public class RagController {
    private final RagService rag;
    public RagController(RagService rag) { this.rag = rag; }

    @PostMapping("/ask")
    public ApiResponse<RagService.AskResponse> ask(@PathVariable("organizationId") UUID organizationId,
            @RequestBody RagService.AskRequest body, HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        return ApiResponse.success(rag.ask(organizationId, userId, body));
    }
}

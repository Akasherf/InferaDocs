package com.documind.rag;
import com.documind.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/organizations/{organizationId}/ai/conversations")
public class ConversationController {
    public record TitleRequest(String title) {}
    private final ConversationService service;
    public ConversationController(ConversationService service) {this.service=service;}
    private UUID user(HttpServletRequest request) { return (UUID)request.getAttribute("userId"); }
    @GetMapping
    public ApiResponse<List<ConversationService.Conversation>> list(@PathVariable("organizationId") UUID org,HttpServletRequest request) {
        return ApiResponse.success(service.list(org,user(request)));
    }
    @PostMapping
    public ApiResponse<ConversationService.Conversation> create(@PathVariable("organizationId") UUID org,@RequestBody TitleRequest body,HttpServletRequest request) {
        return ApiResponse.success(service.create(org,user(request),body==null?null:body.title()));
    }
    @PatchMapping("/{conversationId}")
    public ApiResponse<String> rename(@PathVariable("organizationId") UUID org,@PathVariable("conversationId") UUID id,@RequestBody TitleRequest body,HttpServletRequest request) {
        service.rename(org,user(request),id,body==null?null:body.title());return ApiResponse.success("updated");
    }
    @DeleteMapping("/{conversationId}")
    public ApiResponse<String> delete(@PathVariable("organizationId") UUID org,@PathVariable("conversationId") UUID id,HttpServletRequest request) {
        service.delete(org,user(request),id);return ApiResponse.success("deleted");
    }
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<ConversationService.Message>> messages(@PathVariable("organizationId") UUID org,@PathVariable("conversationId") UUID id,HttpServletRequest request) {
        return ApiResponse.success(service.messages(org,user(request),id));
    }
    @PostMapping("/{conversationId}/ask")
    public ApiResponse<RagService.AskResponse> ask(@PathVariable("organizationId") UUID org,@PathVariable("conversationId") UUID id,@RequestBody RagService.AskRequest body,HttpServletRequest request) {
        return ApiResponse.success(service.ask(org,user(request),id,body==null?null:body.question()));
    }
}

package com.documind.document.controller;
import com.documind.document.service.DocumentService;
import com.documind.processing.ProcessingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/organizations/{organizationId}/documents")
public class DocumentController {
 private final DocumentService docs;
 private final ProcessingService jobs;
 private UUID user(HttpServletRequest request){return (UUID)request.getAttribute("userId");}
 @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public ResponseEntity<DocumentService.DocumentView> upload(@PathVariable("organizationId") UUID organizationId, @RequestParam("file") MultipartFile file, HttpServletRequest request){var created=docs.upload(organizationId,user(request),file);
  // Upload remains successful if enqueue fails; client may enqueue via /processing.
  try {jobs.enqueue(organizationId,user(request),created.id());}
  catch (RuntimeException e) {org.slf4j.LoggerFactory.getLogger(DocumentController.class)
      .warn("Document {} uploaded but automatic processing could not be queued", created.id(), e);}
  return ResponseEntity.status(HttpStatus.CREATED).body(created);}
 @GetMapping
 public List<DocumentService.DocumentView> list(@PathVariable("organizationId") UUID organizationId, HttpServletRequest request){return docs.list(organizationId,user(request));}
 @GetMapping("/{id}")
 public DocumentService.DocumentView get(@PathVariable("organizationId") UUID organizationId, @PathVariable("id") UUID id, HttpServletRequest request){return DocumentService.DocumentView.of(docs.get(organizationId,user(request),id));}
 @GetMapping("/{id}/download")
 public ResponseEntity<byte[]> download(@PathVariable("organizationId") UUID organizationId, @PathVariable("id") UUID id, HttpServletRequest request){
  var d=docs.get(organizationId,user(request),id);var data=docs.download(organizationId,user(request),id);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.getContentType()))
   .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+java.net.URLEncoder.encode(d.getOriginalFilename(),java.nio.charset.StandardCharsets.UTF_8).replace("+","%20"))
   .header("X-Content-Type-Options","nosniff").body(data);
 }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable("organizationId") UUID organizationId, @PathVariable("id") UUID id, HttpServletRequest request){docs.delete(organizationId,user(request),id);}
}

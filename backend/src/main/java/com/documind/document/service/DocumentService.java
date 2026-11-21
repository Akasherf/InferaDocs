package com.documind.document.service;
import com.documind.common.exception.ApiException;
import com.documind.workspace.WorkspacePreferencesService;
import org.springframework.beans.factory.annotation.Autowired;
import com.documind.document.domain.Document;
import com.documind.document.repository.DocumentRepository;
import com.documind.document.storage.DocumentStorage;
import com.documind.organization.domain.OrganizationMembership;
import com.documind.organization.repository.OrganizationMembershipRepository;
import com.documind.organization.repository.OrganizationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class DocumentService {
 private final DocumentRepository repo;
 private final DocumentStorage storage;
 private final OrganizationMembershipRepository memberships;
 private final OrganizationRepository organizations;
 @Autowired(required=false) private WorkspacePreferencesService workspacePreferences;

 @PersistenceContext
 private EntityManager entityManager;

 private static final long MAX_SIZE=20L*1024*1024;
 public record DocumentView(UUID id,UUID organizationId,String filename,String contentType,long size,String sha256,Instant createdAt) {
  public static DocumentView of(Document d){return new DocumentView(d.getId(),d.getOrganizationId(),d.getOriginalFilename(),d.getContentType(),d.getFileSize(),d.getSha256(),d.getCreatedAt());}
 }
 private OrganizationMembership authorized(UUID org,UUID user,boolean write){
  if(organizations.findActiveById(org).isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Workspace not found");
  var m=memberships.findActiveByOrganizationIdAndUserId(org,user).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Workspace not found"));
  if(write && m.getRole()==OrganizationMembership.MembershipRole.VIEWER)
   throw new ApiException(HttpStatus.FORBIDDEN,"DOCUMENT_PERMISSION_DENIED","Viewer cannot modify documents");
  return m;
 }
 @Transactional
 public DocumentView upload(UUID org,UUID user,MultipartFile file){
  authorized(org,user,true);
  if(file==null||file.isEmpty()||file.getSize()> (workspacePreferences==null ? MAX_SIZE : (long)workspacePreferences.getForMember(org,user).maxUploadMb()*1024*1024)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILE","File must not exceed this workspace upload limit");
  String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("document");
  filename=filename.replace('\\','/');filename=filename.substring(filename.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");
  if(filename.isBlank()||filename.length()>255) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILENAME","Invalid filename");
  try {
   byte[] data=file.getBytes();
   String mime=determineType(data);
   if(mime==null) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"UNSUPPORTED_FILE","Only PDF, DOCX and UTF-8 text are accepted");
   String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
   String key=org+"/"+UUID.randomUUID();
   storage.put(key,data,mime);
   try {
    Document d=repo.saveAndFlush(Document.builder().organizationId(org).uploadedBy(user).originalFilename(filename).contentType(mime).fileSize(data.length).objectKey(key).sha256(sha).status("ACTIVE").build());
    entityManager.flush();
            return DocumentView.of(d);
   } catch(RuntimeException e){try{storage.delete(key);}catch(Exception cleanup){e.addSuppressed(cleanup);}throw e;}
  }catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(HttpStatus.BAD_GATEWAY,"STORAGE_ERROR","Document storage failed");}
 }
 private String determineType(byte[] d){
  if(d.length>=5 && d[0]=='%'&&d[1]=='P'&&d[2]=='D'&&d[3]=='F'&&d[4]=='-') return "application/pdf";
  if(d.length>=4 && d[0]=='P'&&d[1]=='K'){
   try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(d))){
    java.util.zip.ZipEntry e;int entries=0;
    while((e=zip.getNextEntry())!=null && ++entries<10000){if(e.getName().equals("[Content_Types].xml"))return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";}
   }catch(Exception ignored){} return null;
  }
  try{java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(d));
   for(byte b:d)if(b==0)return null;
   return "text/plain";
  }catch(Exception ignored){return null;}
 }
 @Transactional(readOnly=true)
 public List<DocumentView> list(UUID org,UUID user){authorized(org,user,false);return repo.findByOrganizationIdAndStatusOrderByCreatedAtDesc(org,"ACTIVE").stream().map(DocumentView::of).toList();}
 @Transactional(readOnly=true)
 public Document get(UUID org,UUID user,UUID id){authorized(org,user,false);return repo.findByIdAndOrganizationIdAndStatus(id,org,"ACTIVE").orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"DOCUMENT_NOT_FOUND","Document not found"));}
 public byte[] download(UUID org,UUID user,UUID id){Document d=get(org,user,id);try{return storage.get(d.getObjectKey());}catch(Exception e){throw new ApiException(HttpStatus.BAD_GATEWAY,"STORAGE_ERROR","Document storage unavailable");}}
 @Transactional
 public void delete(UUID org,UUID user,UUID id){authorized(org,user,true);Document d=repo.findByIdAndOrganizationIdAndStatus(id,org,"ACTIVE").orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"DOCUMENT_NOT_FOUND","Document not found"));d.setStatus("DELETED");d.setDeletedAt(Instant.now());repo.save(d);}
}

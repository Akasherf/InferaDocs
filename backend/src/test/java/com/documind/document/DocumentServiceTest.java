package com.documind.document;

import com.documind.common.exception.ApiException;
import com.documind.document.repository.DocumentRepository;
import com.documind.document.service.DocumentService;
import com.documind.document.storage.DocumentStorage;
import com.documind.organization.domain.Organization;
import com.documind.organization.domain.OrganizationMembership;
import com.documind.organization.repository.OrganizationMembershipRepository;
import com.documind.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.UUID;

class DocumentServiceTest {
 private DocumentRepository documents;
 private DocumentStorage storage;
 private OrganizationMembershipRepository memberships;
 private OrganizationRepository organizations;
 private DocumentService service;
 private final UUID org = UUID.randomUUID();
 private final UUID user = UUID.randomUUID();
 @BeforeEach void setUp(){
  documents=mock(DocumentRepository.class);storage=mock(DocumentStorage.class);
  memberships=mock(OrganizationMembershipRepository.class);organizations=mock(OrganizationRepository.class);
  service=new DocumentService(documents,storage,memberships,organizations);
  when(organizations.findActiveById(org)).thenReturn(Optional.of(mock(Organization.class)));
 }
 @Test void nonMemberCannotList(){
  assertThrows(ApiException.class,()->service.list(org,user));
  verifyNoInteractions(documents,storage);
 }
 @Test void viewerCannotUpload(){
  var membership=OrganizationMembership.builder().organizationId(org).userId(user)
      .status(OrganizationMembership.MembershipStatus.ACTIVE).role(OrganizationMembership.MembershipRole.VIEWER).build();
  when(memberships.findActiveByOrganizationIdAndUserId(org,user)).thenReturn(Optional.of(membership));
  assertThrows(ApiException.class,()->service.upload(org,user,new MockMultipartFile("file","example.txt","text/plain","test".getBytes())));
  verifyNoInteractions(documents,storage);
 }
 @Test void unownedDocumentNotVisible(){
  var membership=OrganizationMembership.builder().organizationId(org).userId(user)
      .status(OrganizationMembership.MembershipStatus.ACTIVE).role(OrganizationMembership.MembershipRole.MEMBER).build();
  when(memberships.findActiveByOrganizationIdAndUserId(org,user)).thenReturn(Optional.of(membership));
  var id=UUID.randomUUID();
  assertThrows(ApiException.class,()->service.get(org,user,id));
  verify(documents).findByIdAndOrganizationIdAndStatus(id,org,"ACTIVE");
  verifyNoInteractions(storage);
 }
}

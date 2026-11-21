package com.documind.organization.service;

import com.documind.audit.domain.AuditEvent;
import com.documind.audit.service.AuditService;
import com.documind.common.exception.ApiException;
import com.documind.identity.domain.User;
import com.documind.identity.repository.UserRepository;
import com.documind.organization.domain.Organization;
import com.documind.organization.domain.OrganizationMembership;
import com.documind.organization.dto.OrganizationDtos;
import com.documind.organization.repository.OrganizationMembershipRepository;
import com.documind.organization.repository.OrganizationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public OrganizationDtos.OrganizationResponse createOrganization(UUID actorUserId, OrganizationDtos.CreateOrganizationRequest request, HttpServletRequest httpRequest) {
        // Check if slug is already taken
        if (organizationRepository.existsBySlug(request.getSlug())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORGANIZATION_SLUG_EXISTS", "Organization slug already exists");
        }

        // Create organization
        Organization organization = Organization.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .status(Organization.OrganizationStatus.ACTIVE)
                .createdBy(actorUserId)
                .build();
        organization = organizationRepository.save(organization);
        entityManager.flush(); // Ensure @CreationTimestamp is generated

        // Create owner membership for the creator
        OrganizationMembership ownerMembership = OrganizationMembership.builder()
                .organizationId(organization.getId())
                .userId(actorUserId)
                .role(OrganizationMembership.MembershipRole.OWNER)
                .status(OrganizationMembership.MembershipStatus.ACTIVE)
                .build();
        membershipRepository.save(ownerMembership);

        // Audit log
        auditService.recordOrganizationEvent(
                "ORGANIZATION_CREATED",
                AuditEvent.AuditOutcome.SUCCESS,
                actorUserId,
                organization.getId(),
                null,
                httpRequest,
                Map.of("name", request.getName(), "slug", request.getSlug()),
                "organization",
                organization.getId()
        );

        return toResponse(organization, OrganizationMembership.MembershipRole.OWNER);
    }

    @Transactional(readOnly = true)
    public OrganizationDtos.OrganizationResponse getOrganization(UUID actorUserId, UUID organizationId) {
        Organization organization = organizationRepository.findActiveById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization not found"));

        // Verify user is a member
        OrganizationMembership membership = membershipRepository.findActiveByOrganizationIdAndUserId(organizationId, actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_ACCESS_DENIED", "Not a member of this organization"));

        return toResponse(organization, membership.getRole());
    }

    @Transactional(readOnly = true)
    public List<OrganizationDtos.OrganizationResponse> getUserOrganizations(UUID actorUserId) {
        List<OrganizationMembership> memberships = membershipRepository.findActiveByUserId(actorUserId);
        return memberships.stream()
                .map(membership -> {
                    Organization organization = organizationRepository.findActiveById(membership.getOrganizationId())
                            .orElse(null);
                    if (organization == null) {
                        return null;
                    }
                    return toResponse(organization, membership.getRole());
                })
                .filter(org -> org != null)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrganizationDtos.OrganizationResponse updateOrganization(UUID actorUserId, UUID organizationId, OrganizationDtos.UpdateOrganizationRequest request, HttpServletRequest httpRequest) {
        Organization organization = organizationRepository.findActiveById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization not found"));

        // Check permission: only OWNER or ADMIN can update
        OrganizationMembership membership = membershipRepository.findActiveByOrganizationIdAndUserId(organizationId, actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_ACCESS_DENIED", "Not a member of this organization"));

        if (!membership.isAdminOrAbove()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INSUFFICIENT_PERMISSIONS", "Only owners and admins can update organization");
        }

        boolean changed = false;
        if (request.getName() != null && !request.getName().equals(organization.getName())) {
            organization.setName(request.getName());
            changed = true;
        }
        if (request.getStatus() != null && request.getStatus() != organization.getStatus()) {
            organization.setStatus(request.getStatus());
            changed = true;
        }

        if (changed) {
            organization = organizationRepository.save(organization);

            Map<String, Object> auditDetails = new java.util.HashMap<>();
            if (request.getName() != null) auditDetails.put("name", request.getName());
            if (request.getStatus() != null) auditDetails.put("status", request.getStatus().name());

            auditService.recordOrganizationEvent(
                    "ORGANIZATION_UPDATED",
                    AuditEvent.AuditOutcome.SUCCESS,
                    actorUserId,
                    organizationId,
                    null,
                    httpRequest,
                    auditDetails,
                    "organization",
                    organizationId
            );
        }

        return toResponse(organization, membership.getRole());
    }

    @Transactional
    public void deleteOrganization(UUID actorUserId, UUID organizationId, HttpServletRequest httpRequest) {
        Organization organization = organizationRepository.findActiveById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization not found"));

        // Check permission: only OWNER can delete
        OrganizationMembership membership = membershipRepository.findActiveByOrganizationIdAndUserId(organizationId, actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_ACCESS_DENIED", "Not a member of this organization"));

        if (!membership.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INSUFFICIENT_PERMISSIONS", "Only owners can delete organization");
        }

        // Soft delete by setting status to SUSPENDED
        organization.setStatus(Organization.OrganizationStatus.SUSPENDED);
        organizationRepository.save(organization);

        auditService.recordOrganizationEvent(
                "ORGANIZATION_DELETED",
                AuditEvent.AuditOutcome.SUCCESS,
                actorUserId,
                organizationId,
                null,
                httpRequest,
                Map.of("name", organization.getName()),
                "organization",
                organizationId
        );
    }

    @Transactional(readOnly = true)
    public OrganizationDtos.MembershipListResponse getMembers(UUID actorUserId, UUID organizationId) {
        // Verify actor is a member
        membershipRepository.findActiveByOrganizationIdAndUserId(organizationId, actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_ACCESS_DENIED", "Not a member of this organization"));

        List<OrganizationMembership> memberships = membershipRepository.findActiveByOrganizationId(organizationId);

        List<OrganizationDtos.MemberResponse> members = memberships.stream()
                .map(this::toMemberResponse)
                .collect(Collectors.toList());

        return OrganizationDtos.MembershipListResponse.builder()
                .members(members)
                .total(members.size())
                .build();
    }

    @Transactional
    public OrganizationDtos.MemberResponse addMember(UUID actorUserId, UUID organizationId, OrganizationDtos.AddMemberRequest request, HttpServletRequest httpRequest) {
        Organization organization = organizationRepository.findActiveById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization not found"));

        // Check permission: only OWNER or ADMIN can add members
        OrganizationMembership actorMembership = membershipRepository.findActiveByOrganizationIdAndUserId(organizationId, actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_ACCESS_DENIED", "Not a member of this organization"));

        if (!actorMembership.isAdminOrAbove()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INSUFFICIENT_PERMISSIONS", "Only owners and admins can add members");
        }

        // Cannot add another OWNER
        if (request.getRole() == OrganizationMembership.MembershipRole.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Cannot assign OWNER role");
        }

        // Find user by email
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        // Check if already a member
        if (membershipRepository.existsByOrganizationIdAndUserId(organizationId, user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "User is already a member of this organization");
        }

        OrganizationMembership membership = OrganizationMembership.builder()
                .organizationId(organizationId)
                .userId(user.getId())
                .role(request.getRole())
                .status(OrganizationMembership.MembershipStatus.ACTIVE)
                .build();
        membership = membershipRepository.save(membership);

        auditService.recordOrganizationEvent(
                "MEMBER_ADDED",
                AuditEvent.AuditOutcome.SUCCESS,
                actorUserId,
                organizationId,
                null,
                httpRequest,
                Map.of("memberId", user.getId().toString(), "email", user.getEmail(), "role", request.getRole().toString()),
                "membership",
                membership.getId()
        );

        return toMemberResponse(membership);
    }

    @Transactional
    public OrganizationDtos.MemberResponse updateMember(UUID actorUserId, UUID organizationId, UUID memberId, OrganizationDtos.UpdateMemberRequest request, HttpServletRequest httpRequest) {
        Organization organization = organizationRepository.findActiveById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization not found"));

        // Check permission: only OWNER or ADMIN can update members
        OrganizationMembership actorMembership = membershipRepository.findActiveByOrganizationIdAndUserId(organizationId, actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_ACCESS_DENIED", "Not a member of this organization"));

        if (!actorMembership.isAdminOrAbove()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INSUFFICIENT_PERMISSIONS", "Only owners and admins can update members");
        }

        OrganizationMembership membership = membershipRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Member not found"));

        if (!membership.getOrganizationId().equals(organizationId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Member not found in this organization");
        }

        // Cannot demote the last owner
        if (membership.isOwner() && request.getRole() != null && request.getRole() != OrganizationMembership.MembershipRole.OWNER) {
            long ownerCount = membershipRepository.countActiveOwners(organizationId);
            if (ownerCount <= 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_OWNER", "Cannot demote the last owner");
            }
        }

        // Cannot change own role
        if (membership.getUserId().equals(actorUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_MODIFICATION", "Cannot modify your own membership");
        }

        boolean changed = false;
        if (request.getRole() != null && request.getRole() != membership.getRole()) {
            membership.setRole(request.getRole());
            changed = true;
        }
        if (request.getStatus() != null && request.getStatus() != membership.getStatus()) {
            membership.setStatus(request.getStatus());
            changed = true;
        }

        if (changed) {
            membership = membershipRepository.save(membership);

            auditService.recordOrganizationEvent(
                    "MEMBER_UPDATED",
                    AuditEvent.AuditOutcome.SUCCESS,
                    actorUserId,
                    organizationId,
                    null,
                    httpRequest,
                    Map.of("memberId", membership.getUserId().toString(), "role", request.getRole(), "status", request.getStatus()),
                    "membership",
                    membership.getId()
            );
        }

        return toMemberResponse(membership);
    }

    @Transactional
    public void removeMember(UUID actorUserId, UUID organizationId, UUID memberId, HttpServletRequest httpRequest) {
        Organization organization = organizationRepository.findActiveById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization not found"));

        // Check permission: only OWNER or ADMIN can remove members
        OrganizationMembership actorMembership = membershipRepository.findActiveByOrganizationIdAndUserId(organizationId, actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_ACCESS_DENIED", "Not a member of this organization"));

        if (!actorMembership.isAdminOrAbove()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INSUFFICIENT_PERMISSIONS", "Only owners and admins can remove members");
        }

        // Lock the membership row to prevent concurrent last-owner race
        OrganizationMembership membership = membershipRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Member not found"));

        if (!membership.getOrganizationId().equals(organizationId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Member not found in this organization");
        }

        // Cannot remove the last owner (check while holding lock)
        if (membership.isOwner()) {
            long ownerCount = membershipRepository.countActiveOwners(organizationId);
            if (ownerCount <= 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_OWNER", "Cannot remove the last owner");
            }
        }

        // Cannot remove self
        if (membership.getUserId().equals(actorUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_REMOVAL", "Cannot remove yourself; transfer ownership first");
        }

        membershipRepository.delete(membership);

        auditService.recordOrganizationEvent(
                "MEMBER_REMOVED",
                AuditEvent.AuditOutcome.SUCCESS,
                actorUserId,
                organizationId,
                null,
                httpRequest,
                Map.of("memberId", membership.getUserId().toString()),
                "membership",
                membership.getId()
        );
    }

    private OrganizationDtos.OrganizationResponse toResponse(Organization organization, OrganizationMembership.MembershipRole role) {
        return OrganizationDtos.OrganizationResponse.builder()
                .id(organization.getId().toString())
                .name(organization.getName())
                .slug(organization.getSlug())
                .status(organization.getStatus().name())
                .createdBy(organization.getCreatedBy().toString())
                .createdAt(organization.getCreatedAt().toString())
                .updatedAt(organization.getUpdatedAt().toString())
                .currentUserMembership(OrganizationDtos.MembershipInfo.builder()
                        .role(role.name())
                        .status(OrganizationMembership.MembershipStatus.ACTIVE.name())
                        .build())
                .build();
    }

    private OrganizationDtos.MemberResponse toMemberResponse(OrganizationMembership membership) {
        User user = userRepository.findById(membership.getUserId())
                .orElse(null);

        return OrganizationDtos.MemberResponse.builder()
                .id(membership.getUserId().toString())
                .email(user != null ? user.getEmail() : "unknown")
                .displayName(user != null ? user.getDisplayName() : "Unknown User")
                .role(membership.getRole().name())
                .status(membership.getStatus().name())
                .joinedAt(membership.getCreatedAt().toString())
                .build();
    }
}
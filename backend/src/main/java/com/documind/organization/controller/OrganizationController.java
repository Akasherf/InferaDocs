package com.documind.organization.controller;

import com.documind.common.response.ApiResponse;
import com.documind.organization.dto.OrganizationDtos;
import com.documind.organization.service.OrganizationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrganizationDtos.OrganizationResponse>> createOrganization(
            @RequestAttribute("userId") UUID userId,
            @RequestBody OrganizationDtos.CreateOrganizationRequest request,
            HttpServletRequest httpRequest) {
        OrganizationDtos.OrganizationResponse response = organizationService.createOrganization(userId, request, httpRequest);
        return ResponseEntity.status(201).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrganizationDtos.OrganizationResponse>>> getUserOrganizations(
            @RequestAttribute("userId") UUID userId) {
        List<OrganizationDtos.OrganizationResponse> organizations = organizationService.getUserOrganizations(userId);
        return ResponseEntity.ok(ApiResponse.success(organizations));
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<OrganizationDtos.OrganizationResponse>> getOrganization(
            @RequestAttribute("userId") UUID userId,
            @PathVariable("organizationId") UUID organizationId) {
        OrganizationDtos.OrganizationResponse response = organizationService.getOrganization(userId, organizationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<OrganizationDtos.OrganizationResponse>> updateOrganization(
            @RequestAttribute("userId") UUID userId,
            @PathVariable("organizationId") UUID organizationId,
            @RequestBody OrganizationDtos.UpdateOrganizationRequest request,
            HttpServletRequest httpRequest) {
        OrganizationDtos.OrganizationResponse response = organizationService.updateOrganization(userId, organizationId, request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<Void>> deleteOrganization(
            @RequestAttribute("userId") UUID userId,
            @PathVariable("organizationId") UUID organizationId,
            HttpServletRequest httpRequest) {
        organizationService.deleteOrganization(userId, organizationId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Membership endpoints
    @GetMapping("/{organizationId}/members")
    public ResponseEntity<ApiResponse<OrganizationDtos.MembershipListResponse>> getMembers(
            @RequestAttribute("userId") UUID userId,
            @PathVariable("organizationId") UUID organizationId) {
        OrganizationDtos.MembershipListResponse response = organizationService.getMembers(userId, organizationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{organizationId}/members")
    public ResponseEntity<ApiResponse<OrganizationDtos.MemberResponse>> addMember(
            @RequestAttribute("userId") UUID userId,
            @PathVariable("organizationId") UUID organizationId,
            @RequestBody OrganizationDtos.AddMemberRequest request,
            HttpServletRequest httpRequest) {
        OrganizationDtos.MemberResponse response = organizationService.addMember(userId, organizationId, request, httpRequest);
        return ResponseEntity.status(201).body(ApiResponse.success(response));
    }

    @PatchMapping("/{organizationId}/members/{memberId}")
    public ResponseEntity<ApiResponse<OrganizationDtos.MemberResponse>> updateMember(
            @RequestAttribute("userId") UUID userId,
            @PathVariable("organizationId") UUID organizationId,
            @PathVariable("memberId") UUID memberId,
            @RequestBody OrganizationDtos.UpdateMemberRequest request,
            HttpServletRequest httpRequest) {
        OrganizationDtos.MemberResponse response = organizationService.updateMember(userId, organizationId, memberId, request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{organizationId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @RequestAttribute("userId") UUID userId,
            @PathVariable("organizationId") UUID organizationId,
            @PathVariable("memberId") UUID memberId,
            HttpServletRequest httpRequest) {
        organizationService.removeMember(userId, organizationId, memberId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
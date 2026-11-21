package com.documind.organization.dto;

import com.documind.organization.domain.Organization;
import com.documind.organization.domain.OrganizationMembership;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class OrganizationDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrganizationRequest {
        @NotBlank
        @Size(min = 1, max = 255)
        private String name;

        @NotBlank
        @Size(min = 1, max = 100)
        private String slug;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateOrganizationRequest {
        @Size(min = 1, max = 255)
        private String name;

        private Organization.OrganizationStatus status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationResponse {
        private String id;
        private String name;
        private String slug;
        private String status;
        private String createdBy;
        private String createdAt;
        private String updatedAt;
        private MembershipInfo currentUserMembership;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MembershipInfo {
        private String role;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberResponse {
        private String id;
        private String email;
        private String displayName;
        private String role;
        private String status;
        private String joinedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddMemberRequest {
        @NotBlank
        @Size(max = 320)
        private String email;

        @NotBlank
        private OrganizationMembership.MembershipRole role;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateMemberRequest {
        private OrganizationMembership.MembershipRole role;
        private OrganizationMembership.MembershipStatus status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MembershipListResponse {
        private List<MemberResponse> members;
        private long total;
    }
}
package com.documind.common.security;

import java.util.UUID;

public interface TenantAware {
    UUID getOrganizationId();
    void setOrganizationId(UUID organizationId);
}
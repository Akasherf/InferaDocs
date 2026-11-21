package com.documind.common.security;

import java.util.UUID;

public class TenantContext {

    private static final ThreadLocal<UUID> currentOrganizationId = new ThreadLocal<>();

    private TenantContext() {}

    public static void setOrganizationId(UUID organizationId) {
        currentOrganizationId.set(organizationId);
    }

    public static UUID getOrganizationId() {
        return currentOrganizationId.get();
    }

    public static void clear() {
        currentOrganizationId.remove();
    }

    public static boolean hasOrganizationId() {
        return currentOrganizationId.get() != null;
    }
}
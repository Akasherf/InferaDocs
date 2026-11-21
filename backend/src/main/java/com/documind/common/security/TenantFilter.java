package com.documind.common.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import jakarta.persistence.PersistenceContext;
import java.util.UUID;

@Component
public class TenantFilter {

    @PersistenceContext
    private EntityManager entityManager;

    public void enableTenantFilter(UUID organizationId) {
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.enableFilter("tenantFilter");
        filter.setParameter("organizationId", organizationId);
    }

    public void disableTenantFilter() {
        Session session = entityManager.unwrap(Session.class);
        if (session.getEnabledFilter("tenantFilter") != null) {
            session.disableFilter("tenantFilter");
        }
    }

    public void applyTenantFilterIfNeeded(UUID organizationId) {
        if (organizationId != null) {
            enableTenantFilter(organizationId);
        }
    }
}
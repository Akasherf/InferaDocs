package com.documind.common.security;

import com.documind.common.exception.ApiException;
import com.documind.identity.domain.User;
import com.documind.identity.repository.RevokedTokenRepository;
import com.documind.identity.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RevokedTokenRepository revokedTokenRepository;
    private final UserRepository userRepository;
    private final TenantFilter tenantFilter;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    // Public endpoints that don't require authentication
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/health",
            "/api/v1/health/live",
            "/api/v1/health/ready",
            "/api/v1/system/info",
            "/api/v1/actuator/**",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Skip authentication for public paths
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Parse and validate token (includes signature, iss, aud, exp, nbf, jti, sub)
            var validatedJwt = jwtService.parseAndValidateAndReturn(token);

            String jti = validatedJwt.getJWTClaimsSet().getStringClaim("jti");
            UUID userId = UUID.fromString(validatedJwt.getJWTClaimsSet().getSubject());
            String role = validatedJwt.getJWTClaimsSet().getStringClaim("role");
            UUID organizationId = null;
            String orgIdClaim = validatedJwt.getJWTClaimsSet().getStringClaim("org_id");
            if (orgIdClaim != null) {
                organizationId = UUID.fromString(orgIdClaim);
            }

            // Check if token is revoked (denylist)
            if (jti != null && revokedTokenRepository.existsByJti(jti)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED", "Token has been revoked");
            }

            // Verify user is still active
            Optional<User> userOpt = userRepository.findActiveById(userId);
            if (userOpt.isEmpty()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "User account is disabled or locked");
            }

            // Set tenant context for multi-tenancy enforcement
            if (organizationId != null) {
                TenantContext.setOrganizationId(organizationId);
                // Enable Hibernate tenant filter for repository-level isolation
                tenantFilter.enableTenantFilter(organizationId);
            }

            // Set authentication in security context
            List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, authorities
            );

            // Add custom attributes for downstream use
            request.setAttribute("userId", userId);
            request.setAttribute("userRole", role);
            request.setAttribute("organizationId", organizationId);
            request.setAttribute("accessTokenJti", jti);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (JwtService.JwtValidationException e) {
            log.debug("JWT validation failed for path {}: {}", path, e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid access token");
        } catch (java.text.ParseException | IllegalArgumentException e) {
            log.debug("JWT parse failed for path {}: {}", path, e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid access token");
        } catch (ApiException e) {
            response.sendError(e.getStatus().value(), e.getMessage());
        } finally {
            // Clear context after request to prevent leakage across pooled threads
            SecurityContextHolder.clearContext();
            TenantContext.clear();
            tenantFilter.disableTenantFilter();
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return isPublicPath(path);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(publicPath -> {
            if (publicPath.endsWith("/**")) {
                String prefix = publicPath.substring(0, publicPath.length() - 3);
                return path.startsWith(prefix);
            }
            return path.equals(publicPath) || path.startsWith(publicPath + "/");
        });
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
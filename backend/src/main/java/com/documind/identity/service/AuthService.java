package com.documind.identity.service;

import com.documind.audit.domain.AuditEvent;
import com.documind.common.exception.ApiException;
import com.documind.audit.service.AuditService;
import com.documind.common.security.JwtProperties;
import com.documind.common.security.JwtService;
import com.documind.common.security.TokenGenerator;
import com.documind.identity.domain.RefreshSession;
import com.documind.identity.domain.RevokedToken;
import com.documind.identity.domain.User;
import com.documind.identity.dto.AuthDtos;
import com.documind.identity.repository.RefreshSessionRepository;
import com.documind.identity.repository.RevokedTokenRepository;
import com.documind.identity.repository.UserRepository;
import com.documind.organization.domain.OrganizationMembership;
import com.documind.organization.repository.OrganizationMembershipRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;
    private final Environment environment;

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 7 * 24 * 60 * 60; // 7 days
    private static final String REFRESH_COOKIE_PATH = "/api/v1";

    private boolean isSecureCookie() {
        // Disable Secure flag for local/test profiles (HTTP development)
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("local".equals(profile) || "test".equals(profile) || "development".equals(profile)) {
                return false;
            }
        }
        return true; // Secure in production
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request, HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            auditService.recordAuthEvent("USER_REGISTER", AuditEvent.AuditOutcome.FAILURE, null,
                    UUID.randomUUID(), httpRequest, Map.of("email", normalizedEmail, "reason", "DUPLICATE_EMAIL"));
            throw ApiException.conflict("REGISTRATION_FAILED", "Registration failed");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName().trim())
                .status(User.UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);

        auditService.recordAuthEvent("USER_REGISTER", AuditEvent.AuditOutcome.SUCCESS, user.getId(),
                UUID.randomUUID(), httpRequest, Map.of("email", normalizedEmail));

        // Generate tokens for immediate login after registration
        return generateAuthResponse(user, null, null, httpRequest, httpResponse);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, HttpServletRequest httpRequest,
                                        HttpServletResponse httpResponse) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "unknown";
        String attemptKey = normalizedEmail + ":" + clientIp;
        UUID requestId = UUID.randomUUID();

        // Check for lockout before attempting authentication
        loginAttemptService.checkAndThrowIfLockedOut(attemptKey);

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);

        if (userOpt.isEmpty()) {
            // Use constant-time verification to prevent user enumeration
            passwordEncoder.encode("dummy");
            loginAttemptService.recordFailedAttempt(attemptKey);
            auditService.recordAuthEvent("USER_LOGIN", AuditEvent.AuditOutcome.FAILURE, null,
                    requestId, httpRequest, Map.of("email", normalizedEmail, "reason", "INVALID_CREDENTIALS"));
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = userOpt.get();

        // Check if user is disabled
        if (user.getStatus() == User.UserStatus.DISABLED) {
            loginAttemptService.recordFailedAttempt(attemptKey);
            auditService.recordAuthEvent("USER_LOGIN", AuditEvent.AuditOutcome.FAILURE, user.getId(),
                    requestId, httpRequest, Map.of("reason", "ACCOUNT_DISABLED"));
            throw new BadCredentialsException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailedAttempt(attemptKey);
            auditService.recordAuthEvent("USER_LOGIN", AuditEvent.AuditOutcome.FAILURE, user.getId(),
                    requestId, httpRequest, Map.of("reason", "INVALID_PASSWORD"));
            throw new BadCredentialsException("Invalid credentials");
        }

        // Success - reset attempt counter
        loginAttemptService.recordSuccess(attemptKey);

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditService.recordAuthEvent("USER_LOGIN", AuditEvent.AuditOutcome.SUCCESS, user.getId(),
                requestId, httpRequest, Map.of("email", normalizedEmail));

        return generateAuthResponse(user, null, null, httpRequest, httpResponse);
    }

    @Transactional
    public AuthDtos.RefreshResponse refresh(String refreshToken, HttpServletRequest httpRequest,
                                             HttpServletResponse httpResponse) {
        UUID requestId = UUID.randomUUID();

        if (refreshToken == null || refreshToken.isBlank()) {
            auditService.recordAuthEvent("TOKEN_REFRESH", AuditEvent.AuditOutcome.FAILURE, null,
                    requestId, httpRequest, Map.of("reason", "MISSING_TOKEN"));
            throw new BadCredentialsException("Missing refresh token");
        }

        String tokenHash = tokenGenerator.hashToken(refreshToken);
        Instant now = Instant.now();

        Optional<RefreshSession> sessionOpt = refreshSessionRepository.findActiveByTokenHash(tokenHash, now);

        if (sessionOpt.isEmpty()) {
            // Check if token exists but is revoked/expired (reuse detection)
            Optional<RefreshSession> anySession = refreshSessionRepository.findByTokenHash(tokenHash);
            if (anySession.isPresent()) {
                RefreshSession revokedSession = anySession.get();
                // Reuse detected - revoke entire family
                refreshSessionRepository.revokeFamily(revokedSession.getTokenFamilyId(), now);
                auditService.recordAuthEvent("TOKEN_REFRESH", AuditEvent.AuditOutcome.FAILURE, revokedSession.getUserId(),
                        requestId, httpRequest, Map.of("reason", "TOKEN_REUSE_DETECTED", "family_id", revokedSession.getTokenFamilyId()));
                throw new BadCredentialsException("Token reuse detected - session revoked");
            }

            auditService.recordAuthEvent("TOKEN_REFRESH", AuditEvent.AuditOutcome.FAILURE, null,
                    requestId, httpRequest, Map.of("reason", "INVALID_TOKEN"));
            throw new BadCredentialsException("Invalid refresh token");
        }

        RefreshSession currentSession = sessionOpt.get();

        // Check if user is still active
        Optional<User> userOpt = userRepository.findActiveById(currentSession.getUserId());
        if (userOpt.isEmpty()) {
            refreshSessionRepository.revokeFamily(currentSession.getTokenFamilyId(), now);
            auditService.recordAuthEvent("TOKEN_REFRESH", AuditEvent.AuditOutcome.FAILURE, currentSession.getUserId(),
                    requestId, httpRequest, Map.of("reason", "USER_INACTIVE"));
            throw new BadCredentialsException("User account is disabled");
        }

        User user = userOpt.get();

        // Atomic rotation: revoke current, create new
        refreshSessionRepository.revokeById(currentSession.getId(), now);

        String newRefreshToken = tokenGenerator.generateRefreshToken();
        String newTokenHash = tokenGenerator.hashToken(newRefreshToken);

        RefreshSession newSession = RefreshSession.builder()
                .userId(user.getId())
                .tokenHash(newTokenHash)
                .tokenFamilyId(currentSession.getTokenFamilyId())
                .expiresAt(now.plusSeconds(jwtProperties.getRefreshTokenExpiryDays() * 24L * 60 * 60))
                .rotationCount(currentSession.getRotationCount() + 1)
                .userAgent(httpRequest.getHeader("User-Agent"))
                .build();

        refreshSessionRepository.save(newSession);

        // Set new refresh cookie
        setRefreshCookie(httpResponse, newRefreshToken);

        // Get current organization membership for token claims
        OrganizationMembership membership = getCurrentMembership(user.getId());

        String accessToken = jwtService.encodeAccessToken(
                user.getId(),
                membership != null ? membership.getOrganizationId() : null,
                membership != null ? membership.getRole().name() : null
        );

        auditService.recordAuthEvent("TOKEN_REFRESH", AuditEvent.AuditOutcome.SUCCESS, user.getId(),
                requestId, httpRequest, Map.of("rotation_count", newSession.getRotationCount()));

        return AuthDtos.RefreshResponse.builder()
                .accessToken(accessToken)
                .expiresIn(jwtProperties.getAccessTokenExpiryMinutes() * 60)
                .build();
    }

    @Transactional
    public void logout(String refreshToken, String accessTokenJti, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();

        // Add access token JTI to denylist for immediate revocation
        if (accessTokenJti != null && !accessTokenJti.isBlank()) {
            RevokedToken revokedToken = RevokedToken.builder()
                    .jti(accessTokenJti)
                    .revokedAt(now)
                    .reason("USER_LOGOUT")
                    .build();
            revokedTokenRepository.save(revokedToken);
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            String tokenHash = tokenGenerator.hashToken(refreshToken);

            Optional<RefreshSession> sessionOpt = refreshSessionRepository.findByTokenHash(tokenHash);
            if (sessionOpt.isPresent()) {
                RefreshSession session = sessionOpt.get();
                refreshSessionRepository.revokeFamily(session.getTokenFamilyId(), now);
                auditService.recordAuthEvent("USER_LOGOUT", AuditEvent.AuditOutcome.SUCCESS, session.getUserId(),
                        requestId, httpRequest, Map.of("family_id", session.getTokenFamilyId()));
            }
        }

        clearRefreshCookie(httpResponse);
    }

    @Transactional(readOnly = true)
    public AuthDtos.MeResponse getCurrentUser(UUID userId) {
        Optional<User> userOpt = userRepository.findActiveById(userId);
        if (userOpt.isEmpty()) {
            throw new BadCredentialsException("User not found");
        }

        User user = userOpt.get();
        return AuthDtos.MeResponse.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .status(user.getStatus().name())
                .build();
    }

    @Transactional(readOnly = true)
    public java.util.List<AuthDtos.SessionInfo> getUserSessions(UUID userId) {
        Instant now = Instant.now();
        return refreshSessionRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(s -> AuthDtos.SessionInfo.builder()
                        .id(s.getId().toString())
                        .deviceLabel(s.getDeviceLabel())
                        .userAgent(s.getUserAgent())
                        .createdAt(s.getCreatedAt().toString())
                        .lastUsedAt(s.getLastUsedAt() != null ? s.getLastUsedAt().toString() : null)
                        .expiresAt(s.getExpiresAt().toString())
                        .current(s.isActive())
                        .build())
                .toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId, String accessTokenJti, HttpServletRequest httpRequest) {
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();

        Optional<RefreshSession> sessionOpt = refreshSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty() || !sessionOpt.get().getUserId().equals(userId)) {
            auditService.recordAuthEvent("SESSION_REVOKE", AuditEvent.AuditOutcome.FAILURE, userId,
                    requestId, httpRequest, Map.of("session_id", sessionId, "reason", "NOT_FOUND"));
            throw new BadCredentialsException("Session not found");
        }

        RefreshSession session = sessionOpt.get();
        refreshSessionRepository.revokeFamily(session.getTokenFamilyId(), now);

        // Add access token JTI to denylist
        if (accessTokenJti != null && !accessTokenJti.isBlank()) {
            RevokedToken revokedToken = RevokedToken.builder()
                    .jti(accessTokenJti)
                    .revokedAt(now)
                    .reason("SESSION_REVOKE")
                    .build();
            revokedTokenRepository.save(revokedToken);
        }

        auditService.recordAuthEvent("SESSION_REVOKE", AuditEvent.AuditOutcome.SUCCESS, userId,
                requestId, httpRequest, Map.of("session_id", sessionId, "family_id", session.getTokenFamilyId()));
    }

    @Transactional
    public void revokeAllSessions(UUID userId, String accessTokenJti, HttpServletRequest httpRequest) {
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();

        int revoked = refreshSessionRepository.revokeAllForUser(userId, now);

        // Add access token JTI to denylist
        if (accessTokenJti != null && !accessTokenJti.isBlank()) {
            RevokedToken revokedToken = RevokedToken.builder()
                    .jti(accessTokenJti)
                    .revokedAt(now)
                    .reason("SESSION_REVOKE_ALL")
                    .build();
            revokedTokenRepository.save(revokedToken);
        }

        auditService.recordAuthEvent("SESSION_REVOKE_ALL", AuditEvent.AuditOutcome.SUCCESS, userId,
                requestId, httpRequest, Map.of("sessions_revoked", revoked));
    }

    private AuthDtos.AuthResponse generateAuthResponse(User user, OrganizationMembership membership,
                                                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return generateAuthResponse(user, membership, UUID.randomUUID(), httpRequest, httpResponse);
    }

    private AuthDtos.AuthResponse generateAuthResponse(User user, OrganizationMembership membership,
                                                        UUID requestId, HttpServletRequest httpRequest,
                                                        HttpServletResponse httpResponse) {
        String refreshToken = tokenGenerator.generateRefreshToken();
        String tokenHash = tokenGenerator.hashToken(refreshToken);
        UUID familyId = tokenGenerator.generateTokenFamilyId();
        Instant now = Instant.now();

        RefreshSession session = RefreshSession.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .tokenFamilyId(familyId)
                .expiresAt(now.plusSeconds(jwtProperties.getRefreshTokenExpiryDays() * 24L * 60 * 60))
                .rotationCount(0)
                .userAgent(httpRequest != null ? httpRequest.getHeader("User-Agent") : null)
                .build();

        refreshSessionRepository.save(session);

        if (httpResponse != null) {
            setRefreshCookie(httpResponse, refreshToken);
        }

        String accessToken = jwtService.encodeAccessToken(
                user.getId(),
                membership != null ? membership.getOrganizationId() : null,
                membership != null ? membership.getRole().name() : null
        );

        return AuthDtos.AuthResponse.builder()
                .accessToken(accessToken)
                .expiresIn(jwtProperties.getAccessTokenExpiryMinutes() * 60)
                .user(AuthDtos.AuthResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .displayName(user.getDisplayName())
                        .build())
                .build();
    }

    private OrganizationMembership getCurrentMembership(UUID userId) {
        // For now, return the first active membership
        // In future, this could be based on a selected workspace
        return membershipRepository.findActiveByUserId(userId).stream().findFirst().orElse(null);
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        String secureFlag = isSecureCookie() ? "Secure; " : "";
        String cookie = String.format("%s=%s; HttpOnly; %sSameSite=Strict; Path=%s; Max-Age=%d",
                REFRESH_COOKIE_NAME, token, secureFlag, REFRESH_COOKIE_PATH, REFRESH_COOKIE_MAX_AGE_SECONDS);
        response.addHeader("Set-Cookie", cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        String secureFlag = isSecureCookie() ? "Secure; " : "";
        String cookie = String.format("%s=; HttpOnly; %sSameSite=Strict; Path=%s; Max-Age=0",
                REFRESH_COOKIE_NAME, secureFlag, REFRESH_COOKIE_PATH);
        response.addHeader("Set-Cookie", cookie);
    }
}
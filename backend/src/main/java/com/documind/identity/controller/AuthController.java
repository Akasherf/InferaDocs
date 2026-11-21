package com.documind.identity.controller;

import com.documind.common.response.ApiResponse;
import com.documind.common.security.JwtService;
import com.documind.identity.dto.AuthDtos;
import com.documind.identity.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthDtos.AuthResponse response = authService.register(request, httpRequest, httpResponse);
        return ResponseEntity.status(201).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthDtos.AuthResponse response = authService.login(request, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthDtos.RefreshResponse>> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String refreshToken = extractRefreshToken(httpRequest);
        AuthDtos.RefreshResponse response = authService.refresh(refreshToken, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestAttribute("accessTokenJti") String accessTokenJti,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String refreshToken = extractRefreshToken(httpRequest);
        authService.logout(refreshToken, accessTokenJti, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthDtos.MeResponse>> me(
            @RequestAttribute("userId") UUID userId) {
        AuthDtos.MeResponse response = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<java.util.List<AuthDtos.SessionInfo>>> sessions(
            @RequestAttribute("userId") UUID userId) {
        java.util.List<AuthDtos.SessionInfo> sessions = authService.getUserSessions(userId);
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute("accessTokenJti") String accessTokenJti,
            @PathVariable UUID sessionId,
            HttpServletRequest httpRequest) {
        authService.revokeSession(userId, sessionId, accessTokenJti, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<ApiResponse<Void>> revokeAllSessions(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute("accessTokenJti") String accessTokenJti,
            HttpServletRequest httpRequest) {
        authService.revokeAllSessions(userId, accessTokenJti, httpRequest);
        return ResponseEntity.noContent().build();
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
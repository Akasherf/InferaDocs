package com.documind.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Component
@Slf4j
public class TokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32; // 256 bits
    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Generates a cryptographically secure random refresh token (256 bits / 32 bytes).
     * Returns the raw token (URL-safe Base64 encoded) for delivery to the client.
     */
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Generates a token family ID for refresh token rotation tracking.
     */
    public UUID generateTokenFamilyId() {
        return UUID.randomUUID();
    }

    /**
     * Hashes a refresh token using SHA-256 for secure storage.
     * Returns hex-encoded hash (64 characters).
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new IllegalStateException("Hash algorithm unavailable", e);
        }
    }

    /**
     * Constant-time comparison to prevent timing attacks (though less critical for high-entropy tokens).
     */
    public boolean verifyToken(String rawToken, String storedHash) {
        String computedHash = hashToken(rawToken);
        return MessageDigest.isEqual(computedHash.getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
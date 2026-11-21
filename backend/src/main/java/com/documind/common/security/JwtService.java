package com.documind.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final JwtProperties jwtProperties;

    public String encodeAccessToken(UUID userId, UUID organizationId, String role) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(jwtProperties.getAccessTokenExpiryMinutes() * 60L);

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer(jwtProperties.getIssuer())
                    .audience(jwtProperties.getAudience())
                    .subject(userId.toString())
                    .claim("org_id", organizationId != null ? organizationId.toString() : null)
                    .claim("role", role)
                    .claim("jti", UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .notBeforeTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwtProperties.getIssuer()).build(),
                    claimsSet
            );

            RSASSASigner signer = new RSASSASigner(jwtProperties.getPrivateKey());
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            log.error("Failed to encode access token", e);
            throw new IllegalStateException("Failed to encode access token", e);
        }
    }

    public SignedJWT parseAndValidate(String token) throws ParseException {
        return parseAndValidateAndReturn(token);
    }

    public SignedJWT parseAndValidateAndReturn(String token) throws ParseException {
        SignedJWT signedJWT = SignedJWT.parse(token);

        RSASSAVerifier verifier = new RSASSAVerifier(jwtProperties.getPublicKey());
        try {
            if (!signedJWT.verify(verifier)) {
                throw new JwtValidationException("Invalid token signature");
            }
        } catch (JOSEException e) {
            throw new JwtValidationException("Signature verification failed: " + e.getMessage());
        }

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        // Validate issuer
        if (!jwtProperties.getIssuer().equals(claims.getIssuer())) {
            throw new JwtValidationException("Invalid issuer");
        }

        // Validate audience
        if (claims.getAudience() == null || !claims.getAudience().contains(jwtProperties.getAudience())) {
            throw new JwtValidationException("Invalid audience");
        }

        // Validate expiration
        if (claims.getExpirationTime() != null && claims.getExpirationTime().before(Date.from(Instant.now()))) {
            throw new JwtValidationException("Token expired");
        }

        // Validate not-before
        if (claims.getNotBeforeTime() != null && claims.getNotBeforeTime().after(Date.from(Instant.now()))) {
            throw new JwtValidationException("Token not yet valid");
        }

        // Validate required claims
        if (claims.getSubject() == null) {
            throw new JwtValidationException("Missing subject claim");
        }
        if (claims.getClaim("jti") == null) {
            throw new JwtValidationException("Missing jti claim");
        }

        return signedJWT;
    }

    public UUID getUserIdFromToken(String token) {
        try {
            SignedJWT jwt = parseAndValidate(token);
            return UUID.fromString(jwt.getJWTClaimsSet().getSubject());
        } catch (ParseException e) {
            throw new JwtValidationException("Invalid token format: " + e.getMessage());
        }
    }

    public UUID getOrganizationIdFromToken(String token) {
        try {
            SignedJWT jwt = parseAndValidate(token);
            String orgId = jwt.getJWTClaimsSet().getStringClaim("org_id");
            return orgId != null ? UUID.fromString(orgId) : null;
        } catch (ParseException e) {
            throw new JwtValidationException("Invalid token format: " + e.getMessage());
        }
    }

    public String getRoleFromToken(String token) {
        try {
            SignedJWT jwt = parseAndValidate(token);
            return jwt.getJWTClaimsSet().getStringClaim("role");
        } catch (ParseException e) {
            throw new JwtValidationException("Invalid token format: " + e.getMessage());
        }
    }

    public String getJtiFromToken(String token) {
        try {
            SignedJWT jwt = parseAndValidate(token);
            return jwt.getJWTClaimsSet().getStringClaim("jti");
        } catch (ParseException e) {
            throw new JwtValidationException("Invalid token format: " + e.getMessage());
        }
    }

    public static class JwtValidationException extends RuntimeException {
        public JwtValidationException(String message) {
            super(message);
        }
    }
}
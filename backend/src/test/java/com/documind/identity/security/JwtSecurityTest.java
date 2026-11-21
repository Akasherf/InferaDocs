package com.documind.identity.security;

import com.documind.common.security.JwtService;
import com.documind.common.security.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class JwtSecurityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("documind")
            .withUsername("documind")
            .withPassword("documind");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void validToken_withCorrectSignature_accepted() throws ParseException {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = jwtService.encodeAccessToken(userId, orgId, "MEMBER");

        var validated = jwtService.parseAndValidateAndReturn(token);

        assertThat(validated.getJWTClaimsSet().getSubject()).isEqualTo(userId.toString());
        assertThat(validated.getJWTClaimsSet().getStringClaim("role")).isEqualTo("MEMBER");
        assertThat(validated.getJWTClaimsSet().getStringClaim("org_id")).isEqualTo(orgId.toString());
        assertThat(validated.getJWTClaimsSet().getStringClaim("jti")).isNotNull();
    }

    @Test
    void expiredToken_rejected() {
        // Create an expired token manually using app's public key
        RSAPrivateKey privateKey = jwtProperties.getPrivateKey();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(jwtProperties.getIssuer())
                .audience(jwtProperties.getAudience())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(1800))) // Expired 30 min ago
                .claim("role", "MEMBER")
                .claim("org_id", UUID.randomUUID().toString())
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwtProperties.getIssuer()).build(),
                claims
        );
        try {
            signedJWT.sign(new RSASSASigner(privateKey));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String expiredToken = signedJWT.serialize();

        assertThatThrownBy(() -> jwtService.parseAndValidateAndReturn(expiredToken))
                .isInstanceOf(JwtService.JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokenWithWrongIssuer_rejected() {
        RSAPrivateKey privateKey = jwtProperties.getPrivateKey();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer("wrong-issuer")
                .audience(jwtProperties.getAudience())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                .claim("role", "MEMBER")
                .claim("org_id", UUID.randomUUID().toString())
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwtProperties.getIssuer()).build(),
                claims
        );
        try {
            signedJWT.sign(new RSASSASigner(privateKey));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String token = signedJWT.serialize();

        assertThatThrownBy(() -> jwtService.parseAndValidateAndReturn(token))
                .isInstanceOf(JwtService.JwtValidationException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void tokenWithWrongAudience_rejected() {
        RSAPrivateKey privateKey = jwtProperties.getPrivateKey();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(jwtProperties.getIssuer())
                .audience("wrong-audience")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                .claim("role", "MEMBER")
                .claim("org_id", UUID.randomUUID().toString())
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwtProperties.getIssuer()).build(),
                claims
        );
        try {
            signedJWT.sign(new RSASSASigner(privateKey));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String token = signedJWT.serialize();

        assertThatThrownBy(() -> jwtService.parseAndValidateAndReturn(token))
                .isInstanceOf(JwtService.JwtValidationException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void tokenWithInvalidSignature_rejected() {
        // Sign with a different key
        KeyPairGenerator keyGen;
        try {
            keyGen = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        keyGen.initialize(2048);
        KeyPair differentKeyPair = keyGen.generateKeyPair();
        RSAPrivateKey differentPrivateKey = (RSAPrivateKey) differentKeyPair.getPrivate();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(jwtProperties.getIssuer())
                .audience(jwtProperties.getAudience())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                .claim("role", "MEMBER")
                .claim("org_id", UUID.randomUUID().toString())
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwtProperties.getIssuer()).build(),
                claims
        );
        try {
            signedJWT.sign(new RSASSASigner(differentPrivateKey));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String token = signedJWT.serialize();

        assertThatThrownBy(() -> jwtService.parseAndValidateAndReturn(token))
                .isInstanceOf(JwtService.JwtValidationException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void tokenMissingJtiClaim_rejected() {
        RSAPrivateKey privateKey = jwtProperties.getPrivateKey();

        // Token missing 'jti' claim (required)
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(jwtProperties.getIssuer())
                .audience(jwtProperties.getAudience())
                // No jwtID set
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                .claim("role", "MEMBER")
                .claim("org_id", UUID.randomUUID().toString())
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwtProperties.getIssuer()).build(),
                claims
        );
        try {
            signedJWT.sign(new RSASSASigner(privateKey));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String token = signedJWT.serialize();

        assertThatThrownBy(() -> jwtService.parseAndValidateAndReturn(token))
                .isInstanceOf(JwtService.JwtValidationException.class)
                .hasMessageContaining("jti");
    }

    @Test
    void tokenWithNotBeforeInFuture_rejected() {
        RSAPrivateKey privateKey = jwtProperties.getPrivateKey();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(jwtProperties.getIssuer())
                .audience(jwtProperties.getAudience())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .notBeforeTime(Date.from(Instant.now().plusSeconds(60))) // nbf in future
                .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                .claim("role", "MEMBER")
                .claim("org_id", UUID.randomUUID().toString())
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwtProperties.getIssuer()).build(),
                claims
        );
        try {
            signedJWT.sign(new RSASSASigner(privateKey));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String token = signedJWT.serialize();

        assertThatThrownBy(() -> jwtService.parseAndValidateAndReturn(token))
                .isInstanceOf(JwtService.JwtValidationException.class)
                .hasMessageContaining("not yet valid");
    }

    @Test
    void malformedToken_rejected() {
        assertThatThrownBy(() -> jwtService.parseAndValidateAndReturn("not.a.valid.token"))
                .isInstanceOfAny(ParseException.class, JwtService.JwtValidationException.class);
    }

    @Test
    void tokenWithoutOrgId_accepted() throws ParseException {
        UUID userId = UUID.randomUUID();
        String token = jwtService.encodeAccessToken(userId, null, "MEMBER");

        var validated = jwtService.parseAndValidateAndReturn(token);

        assertThat(validated.getJWTClaimsSet().getSubject()).isEqualTo(userId.toString());
        assertThat(validated.getJWTClaimsSet().getStringClaim("org_id")).isNull();
    }

    @Test
    void tokenRoleClaim_isPreserved() throws ParseException {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        for (String role : new String[]{"OWNER", "ADMIN", "MEMBER", "VIEWER"}) {
            String token = jwtService.encodeAccessToken(userId, orgId, role);
            var validated = jwtService.parseAndValidateAndReturn(token);
            assertThat(validated.getJWTClaimsSet().getStringClaim("role")).isEqualTo(role);
        }
    }
}
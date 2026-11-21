package com.documind.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class JwtKeyConfig implements InitializingBean {

    private final JwtProperties jwtProperties;

    @Override
    public void afterPropertiesSet() {
        try {
            // Load or generate keys
            if (jwtProperties.getPrivateKeyPem() != null && !jwtProperties.getPrivateKeyPem().isBlank()
                    && jwtProperties.getPublicKeyPem() != null && !jwtProperties.getPublicKeyPem().isBlank()) {
                // Load from configured PEM
                jwtProperties.setPrivateKey(loadPrivateKey(jwtProperties.getPrivateKeyPem()));
                jwtProperties.setPublicKey(loadPublicKey(jwtProperties.getPublicKeyPem()));
                log.info("Loaded JWT signing keys from configuration");
            } else {
                // Generate ephemeral keys for development only
                generateDevelopmentKeys();
                log.warn("Generated ephemeral JWT signing keys for development. DO NOT USE IN PRODUCTION.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize JWT keys", e);
            throw new IllegalStateException("JWT key initialization failed", e);
        }
    }

    private void generateDevelopmentKeys() throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        // Generate a simple RSA key pair for development
        // In production, use keys from a secure key management system
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        java.security.KeyPair keyPair = keyGen.generateKeyPair();

        jwtProperties.setPrivateKey((RSAPrivateKey) keyPair.getPrivate());
        jwtProperties.setPublicKey((RSAPublicKey) keyPair.getPublic());

        // Log the public key for development use
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        log.info("Development JWT Public Key:\n{}", publicKeyPem);
    }

    private RSAPrivateKey loadPrivateKey(String pem) throws Exception {
        String privateKeyPEM = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(spec);
    }

    private RSAPublicKey loadPublicKey(String pem) throws Exception {
        String publicKeyPEM = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }
}
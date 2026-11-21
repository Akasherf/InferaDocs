package com.documind.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Component
@ConfigurationProperties(prefix = "app.jwt")
@Data
public class JwtProperties {

    private String issuer = "documind-ai";
    private String audience = "documind-ai-client";
    private int accessTokenExpiryMinutes = 15;
    private int refreshTokenExpiryDays = 7;
    private String privateKeyPem;
    private String publicKeyPem;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
}
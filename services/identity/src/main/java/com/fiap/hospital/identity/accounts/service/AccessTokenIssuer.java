package com.fiap.hospital.identity.accounts.service;

import com.fiap.hospital.identity.config.JwtProperties;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SignatureAlgorithm signatureAlgorithm;
    private final String keyId;

    public AccessTokenIssuer(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock, RSAKey identitySigningKey) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.signatureAlgorithm = SignatureAlgorithm.from(identitySigningKey.getAlgorithm().getName());
        if (this.signatureAlgorithm == null) {
            throw new IllegalStateException("Unsupported JWS algorithm on identity signing key: " + identitySigningKey.getAlgorithm());
        }
        this.keyId = identitySigningKey.getKeyID();
    }

    public String issue(AccountPrincipal principal) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(jwtProperties.getAccessTokenTtl());

        JwsHeader header = JwsHeader.with(signatureAlgorithm).keyId(keyId).build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(expiresAt)
                .issuer(jwtProperties.getIssuer())
                .audience(java.util.List.of(jwtProperties.getAudience()))
                .subject(principal.getId().toString())
                .claim("role", principal.getRole().name())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}

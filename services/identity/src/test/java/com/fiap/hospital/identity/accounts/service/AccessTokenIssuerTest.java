package com.fiap.hospital.identity.accounts.service;

import com.fiap.hospital.identity.accounts.domain.Role;
import com.fiap.hospital.identity.accounts.domain.User;
import com.fiap.hospital.identity.config.JwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccessTokenIssuerTest {

    private AccessTokenIssuer accessTokenIssuer;
    private JwtProperties jwtProperties;
    private RSAKey testKey;
    private Clock fixedClock;
    private UUID doctorId;

    @BeforeEach
    void setUp() throws JOSEException {
        doctorId = UUID.fromString("00000000-0000-4000-8000-000000000001");

        testKey = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("test-key-id")
                .generate();

        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(testKey));
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);

        jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("identity");
        jwtProperties.setAudience("hospital-management");
        jwtProperties.setAlgorithm("RS256");
        jwtProperties.setAccessTokenTtl(Duration.ofMinutes(15));

        Instant fixedInstant = Instant.parse("2026-08-23T10:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        accessTokenIssuer = new AccessTokenIssuer(jwtEncoder, jwtProperties, fixedClock);
    }

    @Test
    void shouldIssueTokenWithCorrectClaims() throws Exception {
        User doctor = new User(doctorId, "39053344705", "Dra. Helena Prado",
                "helena.prado@hospital.local", Role.DOCTOR, "ACTIVE",
                "{bcrypt}$2a$10$Ohe35KOw0NnVp9y/vxzYve9IAAMPxd.RvAL738azvksAUaKyQKZY.");

        AccountPrincipal principal = new AccountPrincipal(doctor);
        String token = accessTokenIssuer.issue(principal);

        assertNotNull(token);
        SignedJWT jwt = SignedJWT.parse(token);

        assertEquals("RS256", jwt.getHeader().getAlgorithm().getName());
        assertEquals("identity", jwt.getJWTClaimsSet().getIssuer());
        assertEquals(doctorId.toString(), jwt.getJWTClaimsSet().getSubject());
        assertEquals("DOCTOR", jwt.getJWTClaimsSet().getClaim("role"));
        assertTrue(jwt.getJWTClaimsSet().getAudience().contains("hospital-management"));

        Instant expectedExp = Instant.parse("2026-08-23T10:15:00Z");
        assertEquals(expectedExp.getEpochSecond(), jwt.getJWTClaimsSet().getExpirationTime().toInstant().getEpochSecond());
    }

    @Test
    void shouldIssueTokenWithCorrectKid() throws Exception {
        User patient = new User(UUID.fromString("00000000-0000-4000-8000-000000000003"),
                "52998224725", "Marcos Vieira", "marcos.vieira@exemplo.com",
                Role.PATIENT, "ACTIVE",
                "{bcrypt}$2a$10$OBVnfyEXzi1DbDDVBhboeek2td/O5UDFUsVicvV.u6YTiIgg95.vK");

        AccountPrincipal principal = new AccountPrincipal(patient);
        String token = accessTokenIssuer.issue(principal);

        SignedJWT jwt = SignedJWT.parse(token);
        assertEquals("test-key-id", jwt.getHeader().getKeyID());
    }
}

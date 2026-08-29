package com.fiap.hospital.scheduling.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

public final class JwtTestSupport {

    private JwtTestSupport() {}

    public static RSAKey newSigningKey(String keyId) throws JOSEException {
        return new RSAKeyGenerator(2048)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID(keyId)
            .generate();
    }

    public static HttpServer serveJwks(RSAKey signingKey) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
            "/.well-known/jwks.json",
            new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    byte[] response = new JWKSet(signingKey.toPublicJWK())
                        .toString()
                        .getBytes(StandardCharsets.UTF_8);
                    exchange
                        .getResponseHeaders()
                        .add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(response);
                    }
                }
            }
        );
        server.start();
        return server;
    }

    public static String issueToken(
        RSAKey signingKey,
        UUID subject,
        String role
    ) throws JOSEException {
        Date now = new Date();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("identity")
            .audience("hospital-management")
            .subject(subject.toString())
            .claim("role", role)
            .issueTime(now)
            .expirationTime(new Date(now.getTime() + 5 * 60_000L))
            .build();

        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKey.getKeyID())
                .build(),
            claims
        );

        jwt.sign(new RSASSASigner(signingKey.toRSAPrivateKey()));
        return jwt.serialize();
    }
}

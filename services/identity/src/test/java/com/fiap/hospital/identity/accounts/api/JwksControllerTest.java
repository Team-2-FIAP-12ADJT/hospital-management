package com.fiap.hospital.identity.accounts.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwksControllerTest {

    private static final List<String> RSA_PRIVATE_FIELDS = List.of("d", "p", "q", "dp", "dq", "qi");

    private final RSAKey signingKey = new RSAKeyGenerator(2048)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID(UUID.randomUUID().toString())
            .generate();

    JwksControllerTest() throws JOSEException {
    }

    @Test
    void publishesNoPrivateKeyMaterial() throws ParseException {
        Map<String, Object> body = new JwksController(signingKey).jwks();

        JWKSet published = JWKSet.parse(body);

        assertEquals(1, published.getKeys().size());
        JWK key = published.getKeys().getFirst();
        assertFalse(key.isPrivate());
        assertFalse(key.toRSAKey().isPrivate());
    }

    @SuppressWarnings("unchecked")
    @Test
    void serializedResponseCarriesNoPrivateFields() {
        Map<String, Object> body = new JwksController(signingKey).jwks();

        List<Map<String, Object>> keys = (List<Map<String, Object>>) body.get("keys");

        assertEquals(1, keys.size());
        RSA_PRIVATE_FIELDS.forEach(field ->
                assertFalse(keys.getFirst().containsKey(field), "campo privado publicado: " + field));
    }

    @Test
    void publishesTheKeyIdUsedToSign() throws ParseException {
        Map<String, Object> body = new JwksController(signingKey).jwks();

        JWKSet published = JWKSet.parse(body);

        assertEquals(signingKey.getKeyID(), published.getKeys().getFirst().getKeyID());
    }
}

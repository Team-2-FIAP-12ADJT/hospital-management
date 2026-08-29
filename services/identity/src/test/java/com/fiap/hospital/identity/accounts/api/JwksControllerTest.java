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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Test
    void carriesNoPrivateFieldAnywhereInTheResponse() {
        Map<String, Object> body = new JwksController(signingKey).jwks();

        Set<String> everyFieldName = fieldNamesIn(body);

        RSA_PRIVATE_FIELDS.forEach(field ->
                assertFalse(everyFieldName.contains(field),
                        "campo privado publicado em algum ponto da resposta: " + field));
    }

    @Test
    void carriesNothingBesidesTheKeySet() {
        Map<String, Object> body = new JwksController(signingKey).jwks();

        assertEquals(Set.of("keys"), body.keySet(),
                "a resposta ganhou um campo de topo além de `keys`");
    }

    @Test
    void publishesTheKeyIdUsedToSign() throws ParseException {
        Map<String, Object> body = new JwksController(signingKey).jwks();

        JWKSet published = JWKSet.parse(body);

        assertEquals(signingKey.getKeyID(), published.getKeys().getFirst().getKeyID());
    }

    @Test
    void publishesTheKeyMaterialThatSigns() throws ParseException {
        Map<String, Object> body = new JwksController(signingKey).jwks();

        JWKSet published = JWKSet.parse(body);
        RSAKey publishedKey = published.getKeys().getFirst().toRSAKey();

        assertEquals(signingKey.getModulus(), publishedKey.getModulus(),
                "o módulo publicado não é o da chave que assina");
        assertEquals(signingKey.getPublicExponent(), publishedKey.getPublicExponent(),
                "o expoente publicado não é o da chave que assina");
        assertEquals(signingKey.toPublicJWK(), publishedKey,
                "a chave publicada difere da pública da chave que assina");
    }

    private static Set<String> fieldNamesIn(Object node) {
        Set<String> found = new HashSet<>();
        collectFieldNames(node, found);
        return found;
    }

    private static void collectFieldNames(Object node, Set<String> into) {
        switch (node) {
            case Map<?, ?> map -> map.forEach((key, value) -> {
                into.add(String.valueOf(key));
                collectFieldNames(value, into);
            });
            case List<?> list -> list.forEach(item -> collectFieldNames(item, into));
            case null, default -> {
            }
        }
    }
}

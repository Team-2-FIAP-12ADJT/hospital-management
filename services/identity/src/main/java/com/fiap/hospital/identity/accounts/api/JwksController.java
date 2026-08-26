package com.fiap.hospital.identity.accounts.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final RSAKey identitySigningKey;

    public JwksController(RSAKey identitySigningKey) {
        this.identitySigningKey = identitySigningKey;
    }

    // toPublicJWK() é explícito de propósito: construir o JWKSet a partir da chave
    // privada e confiar no filtro default de toJSONObject() apostaria a chave
    // privada num detalhe da biblioteca, e o vazamento seria silencioso — o
    // endpoint continuaria respondendo 200.
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(identitySigningKey.toPublicJWK()).toJSONObject();
    }
}

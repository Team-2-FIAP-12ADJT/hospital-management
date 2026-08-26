package com.fiap.hospital.gateway.probe;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sonda TEMPORÁRIA do ticket 12: existe só para tornar visível que o gateway
 * valida o token offline e que os claims chegam legíveis para a autorização por
 * papel do ticket 25. O ticket 33 remove esta classe quando as rotas reais do
 * gateway entrarem.
 */
@RestController
@RequestMapping("/probe")
public class TokenProbeController {

    @GetMapping("/whoami")
    public Map<String, Object> whoami(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "sub", jwt.getSubject(),
                "role", jwt.getClaimAsString("role"),
                "iss", jwt.getClaimAsString("iss"),
                "kid", String.valueOf(jwt.getHeaders().get("kid")));
    }
}

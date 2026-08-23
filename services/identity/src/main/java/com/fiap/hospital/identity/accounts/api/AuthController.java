package com.fiap.hospital.identity.accounts.api;

import com.fiap.hospital.identity.accounts.service.AccessTokenIssuer;
import com.fiap.hospital.identity.accounts.service.AccountPrincipal;
import com.fiap.hospital.identity.config.JwtProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AccessTokenIssuer accessTokenIssuer;
    private final JwtProperties jwtProperties;

    public AuthController(AccessTokenIssuer accessTokenIssuer, JwtProperties jwtProperties) {
        this.accessTokenIssuer = accessTokenIssuer;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@AuthenticationPrincipal AccountPrincipal principal) {
        String accessToken = accessTokenIssuer.issue(principal);
        long expiresIn = jwtProperties.getAccessTokenTtl().getSeconds();

        LoginResponse response = new LoginResponse(accessToken, "Bearer", expiresIn);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}

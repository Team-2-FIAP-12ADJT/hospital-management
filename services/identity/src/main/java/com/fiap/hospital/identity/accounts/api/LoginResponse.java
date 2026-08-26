package com.fiap.hospital.identity.accounts.api;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}

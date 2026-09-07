package com.fiap.hospital.identity.activation.api;

public record ActivateRequest(String token, String password) {}

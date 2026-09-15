package com.fiap.hospital.identity.activation.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class WeakPasswordException extends ResponseStatusException {

    public WeakPasswordException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }
}

package com.fiap.hospital.identity.activation.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InvalidActivationTokenException extends ResponseStatusException {

    public InvalidActivationTokenException() {
        super(HttpStatus.BAD_REQUEST, ActivateAccount.INVALID_TOKEN);
    }
}

package com.fiap.hospital.identity.activation.api;

import com.fiap.hospital.identity.activation.service.ActivateAccount;
import com.fiap.hospital.identity.activation.service.InvalidActivationTokenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Activation", description = "Ativação de conta")
public class ActivationController {

    private final ActivateAccount activateAccount;

    public ActivationController(ActivateAccount activateAccount) {
        this.activateAccount = activateAccount;
    }

    @PostMapping("/activate")
    @Operation(summary = "Define a senha a partir do token de ativação")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conta ativa"),
        @ApiResponse(responseCode = "400", description = "Token inválido, expirado ou já usado")
    })
    public ResponseEntity<Void> activate(@RequestBody(required = false) ActivateRequest request) {
        activateAccount.activate(
            request == null ? null : request.token(),
            request == null ? null : request.password()
        );
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(InvalidActivationTokenException.class)
    ResponseEntity<ActivationError> invalidActivation(InvalidActivationTokenException exception) {
        return ResponseEntity.status(exception.getStatusCode())
            .body(new ActivationError(ActivateAccount.INVALID_TOKEN));
    }
}

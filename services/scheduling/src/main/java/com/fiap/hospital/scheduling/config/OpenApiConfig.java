package com.fiap.hospital.scheduling.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// Declara o esquema "bearerAuth" para o Swagger UI enviar o JWT no "Try it out";
// sem ele, rota protegida por @PreAuthorize não tem como ser exercitada na UI.
// Precisa ser um bean: o springdoc lê esta anotação nos beans do contexto, não
// em classes soltas no classpath.
@Configuration
@OpenAPIDefinition(info = @Info(title = "scheduling", version = "v1"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class OpenApiConfig {}

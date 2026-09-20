package com.telusko.part29springsecex.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body de /login y /registrar. A proposito no tiene id ni role:
// el cliente no puede elegir sobre que fila se guarda ni con que rol.
public record AuthRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 4, max = 72) String password) {

    @Override
    public String toString() {
        return "AuthRequest{username=" + username + "}";
    }
}

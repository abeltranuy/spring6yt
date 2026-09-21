package com.telusko.part29springsecex.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telusko.part29springsecex.response.MessageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Reemplaza al entry point de HTTP Basic: 401/403 en JSON y sin cabecera WWW-Authenticate,
// asi el navegador no abre su popup de usuario y password.
@Component
public class JsonAuthErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: missing, invalid or expired token");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        // Un token CSRF ausente o invalido tambien llega aqui como AccessDeniedException
        String message = accessDeniedException instanceof CsrfException
                ? "Forbidden: missing or invalid CSRF token"
                : "Forbidden: insufficient permissions";
        write(response, HttpServletResponse.SC_FORBIDDEN, message);
    }

    public void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new MessageResponse(message));
    }
}

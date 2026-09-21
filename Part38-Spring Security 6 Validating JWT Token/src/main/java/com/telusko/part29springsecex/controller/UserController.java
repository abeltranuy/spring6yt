package com.telusko.part29springsecex.controller;

import com.telusko.part29springsecex.model.Users;
import com.telusko.part29springsecex.request.AuthRequest;
import com.telusko.part29springsecex.service.UserService;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Ya no hay POST /login ni POST /logout aqui:
//   - el login lo hace el servidor de autorizacion (GET /oauth2/authorization/auth-server lo inicia)
//   - el logout lo atiende el LogoutFilter de Spring (ver SecurityConfig)
@RestController
public class UserController {

    @Autowired
    private UserService service;

    @PostMapping("/registrar")
    public ResponseEntity<Users> register(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
    }

    // Las paginas ya no guardan nada en localStorage: preguntan aqui quien esta conectado.
    // Sin sesion ni Bearer valido responde 401, y el JavaScript manda al login.
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .sorted()
                .toList();
        return Map.of("username", authentication.getName(), "roles", roles);
    }

}

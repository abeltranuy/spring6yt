package com.telusko.part29springsecex.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

// El logout tiene dos mitades: cerrar la sesion de ESTA app (lo hace Spring antes de llegar aqui)
// y cerrar la del servidor de autorizacion, que exige llevar el navegador a su /connect/logout.
// Como usuarios2.js llama a /logout con fetch, en vez de un redirect (que fetch no puede seguir
// hacia otro origen) se devuelve esa URL en JSON y el JavaScript navega hacia ella.
final class JsonOidcLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {

    private final ObjectMapper objectMapper;

    JsonOidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository, ObjectMapper objectMapper) {
        super(clientRegistrationRepository);
        this.objectMapper = objectMapper;
        setPostLogoutRedirectUri("{baseUrl}/login.html");
        setDefaultTargetUrl("/login.html");
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String logoutUrl = determineTargetUrl(request, response, authentication);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("logoutUrl", logoutUrl));
    }
}

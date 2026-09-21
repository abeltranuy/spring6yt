package com.telusko.authserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    // Cadena 1: los endpoints del protocolo (/oauth2/authorize, /oauth2/token, /oauth2/jwks,
    // /connect/logout, /.well-known/openid-configuration...)
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults()); // OpenID Connect: id_token, discovery, logout
        http
                // Un navegador sin sesion que llega a /oauth2/authorize se manda al formulario de login
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    // Cadena 2: el formulario de login de este servidor. Es el UNICO lugar donde se escribe la clave.
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .formLogin(Customizer.withDefaults());
        return http.build();
    }

    // Misma codificacion que usa la app de Part38 al registrar usuarios
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(
            PasswordEncoder passwordEncoder,
            @Value("${app.web-client.id}") String webClientId,
            @Value("${app.web-client.secret}") String webClientSecret,
            @Value("${app.web-client.redirect-uri}") String webRedirectUri,
            @Value("${app.web-client.post-logout-redirect-uri}") String webPostLogoutRedirectUri,
            @Value("${app.api-client.id}") String apiClientId,
            @Value("${app.api-client.secret}") String apiClientSecret,
            @Value("${app.token.access-minutes}") long accessMinutes,
            @Value("${app.token.refresh-hours}") long refreshHours) {

        TokenSettings tokenSettings = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(accessMinutes))
                .refreshTokenTimeToLive(Duration.ofHours(refreshHours))
                // Rotacion: cada uso del refresh token entrega uno nuevo y anula el anterior
                .reuseRefreshTokens(false)
                .build();

        RegisteredClient webClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(webClientId)
                .clientSecret(passwordEncoder.encode(webClientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(webRedirectUri)
                .postLogoutRedirectUri(webPostLogoutRedirectUri)
                .scope(OidcScopes.OPENID)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true) // PKCE obligatorio
                        .requireAuthorizationConsent(false) // es nuestra propia app: sin pantalla de consentimiento
                        .build())
                .tokenSettings(tokenSettings)
                .build();

        RegisteredClient apiClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(apiClientId)
                .clientSecret(passwordEncoder.encode(apiClientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("students.read")
                .tokenSettings(tokenSettings)
                .build();

        return new InMemoryRegisteredClientRepository(webClient, apiClient);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(@Value("${app.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    // Firma asimetrica (RS256): la clave privada solo vive aqui. Los demas validan con la publica,
    // que este servidor publica en /oauth2/jwks.
    @Bean
    public JWKSource<SecurityContext> jwkSource(@Value("${app.jwk.path}") String jwkPath) {
        JWKSet jwkSet = new JWKSet(JwkFileStore.loadOrCreate(Path.of(jwkPath)));
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    // Agrega los roles del usuario (ROLE_USER, ROLE_ADMIN) al access token y al id_token,
    // para que la app de Part38 pueda autorizar sin consultar la base en cada peticion.
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> rolesClaimCustomizer() {
        return context -> {
            boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
            boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
            if (!accessToken && !idToken) {
                return;
            }
            Set<String> roles = context.getPrincipal().getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .collect(Collectors.toSet());
            context.getClaims().claim("roles", roles);
        };
    }
}

package com.telusko.part29springsecex.config;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Esta app ya no valida claves ni emite tokens: eso lo hace el modulo auth-server.
// Acepta a un usuario por dos caminos:
//   1. Navegador (paginas de static/): login OpenID Connect contra auth-server. El navegador solo
//      guarda una cookie de sesion HttpOnly; los tokens quedan en el servidor (patron BFF).
//   2. Programas (Postman, otro servicio): cabecera "Authorization: Bearer <jwt>" con un token
//      firmado por auth-server. Spring lo valida como Resource Server, sin filtro propio.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize("hasRole('ADMIN')") en controladores y servicios
public class SecurityConfig {

    public static final String REGISTRATION_ID = "auth-server";

    @Autowired
    private JsonAuthErrorHandler jsonAuthErrorHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrations) throws Exception {

        // PKCE tambien para este cliente confidencial (auth-server lo exige con requireProofKey)
        DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        authorizationRequestResolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        // Una peticion con Bearer no usa cookies, asi que no es atacable por CSRF
        RequestMatcher bearerRequest = request -> {
            String header = request.getHeader(AUTHORIZATION);
            return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
        };

        return http
                // Usa el bean corsConfigurationSource(); sin esta linea el CorsFilter no entra en la cadena
                .cors(Customizer.withDefaults())
                // Con sesion por cookie el CSRF vuelve a ser necesario: el token va en la cookie
                // XSRF-TOKEN y el JavaScript lo devuelve en la cabecera X-XSRF-TOKEN
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(bearerRequest))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(request -> request
                        // /error publico: si no, cualquier 404/500 se convierte en un 401 vacio
                        .requestMatchers("/registrar", "/error").permitAll()
                        // Frontend estatico (SB Admin 2): solo html y assets, no package.json ni gulpfile
                        .requestMatchers("/*.html", "/css/**", "/js/**", "/img/**", "/vendor/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                // Las paginas son publicas y piden los datos con fetch: ante una peticion sin
                // autenticar se responde 401 en JSON (no un redirect) y el JavaScript decide
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(jsonAuthErrorHandler)
                        .accessDeniedHandler(jsonAuthErrorHandler))
                // Camino 1: navegador. El boton de login.html lleva a /oauth2/authorization/auth-server
                .oauth2Login(login -> login
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
                        .defaultSuccessUrl("/usuarios.html", true)
                        .failureUrl("/login.html?error"))
                // Camino 2: programas con Bearer. Reemplaza al JwtFilter escrito a mano
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(jsonAuthErrorHandler))
                // POST /logout: Spring cierra la sesion local y el handler devuelve la URL para cerrar
                // tambien la del servidor de autorizacion
                .logout(logout -> logout
                        .logoutSuccessHandler(new JsonOidcLogoutSuccessHandler(clientRegistrations, objectMapper)))
                .build();
    }

    // Como esta app se presenta ante auth-server. Los endpoints se declaran a mano en lugar de
    // usar issuer-uri para que la app pueda arrancar aunque auth-server todavia no este levantado.
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.auth-server.url}") String authServerUrl,
            @Value("${app.auth-server.client-id}") String clientId,
            @Value("${app.auth-server.client-secret}") String clientSecret) {

        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientName("Auth Server")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(OidcScopes.OPENID)
                .issuerUri(authServerUrl)
                .authorizationUri(authServerUrl + "/oauth2/authorize")
                .tokenUri(authServerUrl + "/oauth2/token")
                .jwkSetUri(authServerUrl + "/oauth2/jwks")
                .userNameAttributeName("sub")
                // Lo usa el logout para saber a donde llevar el navegador
                .providerConfigurationMetadata(Map.of("end_session_endpoint", authServerUrl + "/connect/logout"))
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    // Valida los Bearer: firma con la clave publica que auth-server publica en /oauth2/jwks
    // (se descarga en la primera peticion), expiracion y que el emisor sea el esperado.
    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.auth-server.url}") String authServerUrl) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(authServerUrl + "/oauth2/jwks").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(authServerUrl));
        return decoder;
    }

    // Bearer: los roles vienen en el claim "roles" del access token (ya traen el prefijo ROLE_)
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return converter;
    }

    // Navegador: los roles vienen en el claim "roles" del id_token
    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>();
            for (GrantedAuthority authority : authorities) {
                if (authority instanceof OidcUserAuthority oidcAuthority) {
                    List<String> roles = oidcAuthority.getIdToken().getClaimAsStringList("roles");
                    if (roles != null) {
                        roles.forEach(role -> mapped.add(new SimpleGrantedAuthority(role)));
                    }
                }
            }
            return mapped;
        };
    }

    // Solo para cifrar la clave al registrar usuarios; la comparacion en el login la hace auth-server
    @Bean
    public static PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    // Para un frontend en otro origen que llame a la API con Bearer
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200")); // Angular
        configuration.setAllowedMethods(Arrays.asList("GET", "PUT", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(AUTHORIZATION, CONTENT_TYPE));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

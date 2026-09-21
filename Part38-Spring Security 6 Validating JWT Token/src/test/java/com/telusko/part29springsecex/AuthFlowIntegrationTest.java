package com.telusko.part29springsecex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telusko.part29springsecex.config.SecurityConfig;
import com.telusko.part29springsecex.enums.RoleList;
import com.telusko.part29springsecex.model.Users;
import com.telusko.part29springsecex.repo.UserRepo;

import jakarta.servlet.http.Cookie;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// La autenticacion se simula con spring-security-test, asi que no hace falta auth-server:
//   browserUser() = usuario del navegador con sesion OIDC (camino BFF)
//   bearer()      = programa con "Authorization: Bearer" (camino Resource Server)
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String PASSWORD = "Secreta-1234";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ClientRegistrationRepository clientRegistrations;

    private String newUsername() {
        return "user" + SEQ.incrementAndGet();
    }

    private static String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    // Sustituye la validacion de firma contra auth-server: "token-user" y "token-admin" son validos
    // y cualquier otro valor es rechazado. Lo demas del camino Bearer es el real, incluida la
    // lectura del claim "roles".
    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void stubTokens() {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("token invalido"));
        doReturn(jwtWithRole("ROLE_USER")).when(jwtDecoder).decode("token-user");
        doReturn(jwtWithRole("ROLE_ADMIN")).when(jwtDecoder).decode("token-admin");
    }

    private static Jwt jwtWithRole(String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("simulado").header("alg", "RS256").subject("ana")
                .claim("roles", List.of(role)).issuedAt(now).expiresAt(now.plusSeconds(600)).build();
    }

    private RequestPostProcessor browserUser(String role) {
        return oidcLogin().idToken(token -> token.subject("ana"))
                .clientRegistration(clientRegistrations.findByRegistrationId(SecurityConfig.REGISTRATION_ID))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private static RequestPostProcessor bearer(String role) {
        String token = "ROLE_ADMIN".equals(role) ? "token-admin" : "token-user";
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    // Hace lo mismo que comun.js en el navegador: abre una pagina, lee la cookie XSRF-TOKEN que
    // deja el servidor y la devuelve tal cual en la cabecera X-XSRF-TOKEN.
    // No usar el atajo csrf() de spring-security-test en esta clase: sustituye el repositorio de
    // cookie por uno de sesion para todos los tests siguientes y la cookie deja de emitirse.
    private RequestPostProcessor csrfLikeTheBrowser() throws Exception {
        Cookie cookie = mvc.perform(get("/login.html")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isFalse(); // el JavaScript tiene que poder leerla
        return request -> {
            request.setCookies(cookie);
            request.addHeader("X-XSRF-TOKEN", cookie.getValue());
            return request;
        };
    }

    // ---------- Registro (sigue viviendo en esta app) ----------

    @Test
    void registerIsPublicAndNeverReturnsThePassword() throws Exception {
        String username = newUsername();
        mvc.perform(post("/registrar").with(csrfLikeTheBrowser()).contentType(MediaType.APPLICATION_JSON).content(body(username, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        // La clave queda cifrada con BCrypt, que es lo que auth-server sabe comparar
        assertThat(passwordEncoder.matches(PASSWORD, userRepo.findByUsername(username).getPassword())).isTrue();
    }

    @Test
    void registerIgnoresIdAndRoleSentByTheClient() throws Exception {
        String victim = newUsername();
        mvc.perform(post("/registrar").with(csrfLikeTheBrowser()).contentType(MediaType.APPLICATION_JSON).content(body(victim, PASSWORD)))
                .andExpect(status().isCreated());
        int victimId = userRepo.findByUsername(victim).getId();
        String attacker = newUsername();

        String takeover = "{\"id\":" + victimId + ",\"username\":\"" + attacker + "\",\"password\":\"Otra-clave-99\",\"role\":\"ROLE_ADMIN\"}";
        mvc.perform(post("/registrar").with(csrfLikeTheBrowser()).contentType(MediaType.APPLICATION_JSON).content(takeover))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_USER"));

        assertThat(userRepo.findByUsername(attacker).getId()).isNotEqualTo(victimId);
        assertThat(userRepo.findById(victimId).orElseThrow().getUsername()).isEqualTo(victim);
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String username = newUsername();
        mvc.perform(post("/registrar").with(csrfLikeTheBrowser()).contentType(MediaType.APPLICATION_JSON).content(body(username, PASSWORD)))
                .andExpect(status().isCreated());
        mvc.perform(post("/registrar").with(csrfLikeTheBrowser()).contentType(MediaType.APPLICATION_JSON).content(body(username, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void usernameIsUniqueAtDatabaseLevel() {
        String username = newUsername();
        Users first = new Users();
        first.setUsername(username);
        first.setPassword(passwordEncoder.encode(PASSWORD));
        first.setRole(RoleList.ROLE_USER);
        userRepo.saveAndFlush(first);

        Users second = new Users();
        second.setUsername(username);
        second.setPassword(passwordEncoder.encode(PASSWORD));
        second.setRole(RoleList.ROLE_USER);
        assertThatThrownBy(() -> userRepo.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidInputIsRejectedWith400() throws Exception {
        mvc.perform(post("/registrar").with(csrfLikeTheBrowser()).contentType(MediaType.APPLICATION_JSON).content(body("", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/registrar").with(csrfLikeTheBrowser()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Esta app ya no hace login con clave ----------

    @Test
    void thereIsNoPasswordLoginEndpointAnymore() throws Exception {
        // POST /login con usuario y clave ya no existe: sin autenticar, 401
        mvc.perform(post("/login").with(csrfLikeTheBrowser()).contentType(MediaType.APPLICATION_JSON).content(body("ana", PASSWORD)))
                .andExpect(status().isUnauthorized());
        // Y HTTP Basic tampoco se acepta
        mvc.perform(get("/students").with(httpBasic("ana", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginButtonRedirectsToTheAuthorizationServerWithPkce() throws Exception {
        mvc.perform(get("/oauth2/authorization/auth-server"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("http://localhost:9000/oauth2/authorize")))
                .andExpect(header().string("Location", containsString("client_id=part38-web")))
                .andExpect(header().string("Location", containsString("code_challenge=")))
                .andExpect(header().string("Location", containsString("code_challenge_method=S256")));
    }

    // ---------- Rutas protegidas: los dos caminos ----------

    @Test
    void protectedEndpointAnswers401JsonWithoutAuthentication() throws Exception {
        mvc.perform(get("/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserSessionAndBearerTokenBothReachProtectedEndpoints() throws Exception {
        mvc.perform(get("/students").with(browserUser("ROLE_USER")))
                .andExpect(status().isOk());
        mvc.perform(get("/students").with(bearer("ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    void meReportsUsernameAndRolesFromTheToken() throws Exception {
        mvc.perform(get("/me").with(browserUser("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
        mvc.perform(get("/").with(bearer("ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ana")));
    }

    @Test
    void malformedBearerTokenReturns401() throws Exception {
        mvc.perform(get("/students").header("Authorization", "Bearer esto.no.esunjwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void adminRoutesRequireAdminRole() throws Exception {
        mvc.perform(get("/api/admin/ping").with(browserUser("ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forbidden: insufficient permissions"));
        mvc.perform(get("/api/admin/ping").with(bearer("ROLE_USER")))
                .andExpect(status().isForbidden());
        // La ruta no existe todavia: un ADMIN pasa la autorizacion y recibe 404, no 403
        mvc.perform(get("/api/admin/ping").with(browserUser("ROLE_ADMIN")))
                .andExpect(status().isNotFound());
    }

    // ---------- CSRF: obligatorio con cookie de sesion, innecesario con Bearer ----------

    @Test
    void browserWritesNeedTheCsrfTokenButBearerWritesDoNot() throws Exception {
        String student = "{\"id\":901,\"name\":\"Temporal\",\"marks\":50}";

        mvc.perform(post("/students").with(browserUser("ROLE_USER")).contentType(MediaType.APPLICATION_JSON).content(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forbidden: missing or invalid CSRF token"));
        mvc.perform(post("/students").with(browserUser("ROLE_USER")).with(csrfLikeTheBrowser())
                        .contentType(MediaType.APPLICATION_JSON).content(student))
                .andExpect(status().isOk());

        // Un Bearer no viaja en cookies: no necesita token CSRF
        mvc.perform(delete("/students/901").with(bearer("ROLE_USER")))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/students/901").with(browserUser("ROLE_USER")).with(csrfLikeTheBrowser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void registerWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/registrar").contentType(MediaType.APPLICATION_JSON).content(body(newUsername(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // ---------- Logout ----------

    @Test
    void logoutClosesTheSessionAndReturnsTheAuthorizationServerLogoutUrl() throws Exception {
        mvc.perform(post("/logout").with(browserUser("ROLE_USER")).with(csrfLikeTheBrowser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoutUrl", containsString("http://localhost:9000/connect/logout")))
                .andExpect(jsonPath("$.logoutUrl", containsString("id_token_hint=")))
                .andExpect(jsonPath("$.logoutUrl", containsString("post_logout_redirect_uri=")));
    }

    // ---------- CORS ----------

    @Test
    void corsPreflightAllowsAuthorizationHeaderFromAngular() throws Exception {
        mvc.perform(options("/students")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));

        mvc.perform(options("/students")
                        .header("Origin", "http://sitio-ajeno.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}

package com.telusko.part29springsecex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telusko.part29springsecex.enums.RoleList;
import com.telusko.part29springsecex.model.Users;
import com.telusko.part29springsecex.repo.UserRepo;
import com.telusko.part29springsecex.service.JWTService;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Cada test cubre uno de los hallazgos de la revision de seguridad JWT.
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
    private JWTService jwtService;

    private String newUsername() {
        return "user" + SEQ.incrementAndGet();
    }

    private static String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private MvcResult register(String username) throws Exception {
        return mvc.perform(post("/registrar").contentType(MediaType.APPLICATION_JSON).content(body(username, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(body(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        String authHeader = result.getResponse().getHeader("Authorization");
        assertThat(authHeader).startsWith("Bearer ");
        return authHeader.substring(7);
    }

    @Test
    void registerIsPublicAndNeverReturnsThePassword() throws Exception {
        String username = newUsername();
        mvc.perform(post("/registrar").contentType(MediaType.APPLICATION_JSON).content(body(username, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void loginReturnsTokenAndNeverReturnsThePassword() throws Exception {
        String username = newUsername();
        register(username);

        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(body(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Authorization", containsString("Bearer ")))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_USER")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("$2a$"))));
    }

    @Test
    void registerIgnoresIdAndRoleSentByTheClient() throws Exception {
        String victim = newUsername();
        register(victim);
        int victimId = userRepo.findByUsername(victim).getId();
        String attacker = newUsername();

        // Antes esto sobreescribia la fila del usuario victima (toma de cuenta)
        String takeover = "{\"id\":" + victimId + ",\"username\":\"" + attacker + "\",\"password\":\"Otra-clave-99\",\"role\":\"ROLE_ADMIN\"}";
        mvc.perform(post("/registrar").contentType(MediaType.APPLICATION_JSON).content(takeover))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_USER"));

        assertThat(userRepo.findByUsername(attacker).getId()).isNotEqualTo(victimId);
        assertThat(userRepo.findById(victimId).orElseThrow().getUsername()).isEqualTo(victim);
        loginAndGetToken(victim, PASSWORD);
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String username = newUsername();
        register(username);

        mvc.perform(post("/registrar").contentType(MediaType.APPLICATION_JSON).content(body(username, PASSWORD)))
                .andExpect(status().isConflict());

        loginAndGetToken(username, PASSWORD);
    }

    @Test
    void usernameIsUniqueAtDatabaseLevel() {
        String username = newUsername();
        Users first = new Users();
        first.setUsername(username);
        first.setPassword(passwordEncoder.encode(PASSWORD));
        first.setRole(RoleList.ROLE_USER);
        userRepo.saveAndFlush(first);

        // Saltandose el chequeo de UserService: la restriccion unique de la tabla tambien lo impide
        Users second = new Users();
        second.setUsername(username);
        second.setPassword(passwordEncoder.encode(PASSWORD));
        second.setRole(RoleList.ROLE_USER);
        assertThatThrownBy(() -> userRepo.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidInputIsRejectedWith400() throws Exception {
        mvc.perform(post("/registrar").contentType(MediaType.APPLICATION_JSON).content(body("", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/registrar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongCredentialsReturn401WithMessageAndNoBasicChallenge() throws Exception {
        String username = newUsername();
        register(username);

        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(body(username, "incorrecta")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.message").value("Invalid Username or Password"));

        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(body("noexiste", "incorrecta")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid Username or Password"));
    }

    @Test
    void protectedEndpointRequiresValidToken() throws Exception {
        String username = newUsername();
        register(username);
        String token = loginAndGetToken(username, PASSWORD);

        mvc.perform(get("/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void malformedOrTamperedTokenReturns401InsteadOfException() throws Exception {
        String username = newUsername();
        register(username);
        String token = loginAndGetToken(username, PASSWORD);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        mvc.perform(get("/students").header("Authorization", "Bearer esto.no.esunjwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/students").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staleTokenDoesNotBlockLogin() throws Exception {
        String username = newUsername();
        register(username);

        mvc.perform(post("/login").header("Authorization", "Bearer esto.no.esunjwt")
                        .contentType(MediaType.APPLICATION_JSON).content(body(username, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void httpBasicIsNotAcceptedOnProtectedEndpoints() throws Exception {
        String username = newUsername();
        register(username);

        mvc.perform(get("/students").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic(username, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutReachesControllerAndRevokesTheToken() throws Exception {
        String username = newUsername();
        register(username);
        String token = loginAndGetToken(username, PASSWORD);

        mvc.perform(post("/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged Out Successfully"));

        mvc.perform(get("/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token is blacklisted"));

        // Un login nuevo sigue funcionando
        String newToken = loginAndGetToken(username, PASSWORD);
        assertThat(newToken).isNotEqualTo(token);
    }

    @Test
    void logoutWithInvalidTokenDoesNotFail() throws Exception {
        mvc.perform(post("/logout").header("Authorization", "Bearer esto.no.esunjwt"))
                .andExpect(status().isOk());
        mvc.perform(post("/logout"))
                .andExpect(status().isOk());
    }

    @Test
    void corsPreflightAllowsAuthorizationHeaderFromAngular() throws Exception {
        mvc.perform(options("/students")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("authorization")));

        mvc.perform(options("/students")
                        .header("Origin", "http://sitio-ajeno.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginExposesAuthorizationHeaderToTheBrowser() throws Exception {
        String username = newUsername();
        register(username);

        mvc.perform(post("/login").header("Origin", "http://localhost:4200")
                        .contentType(MediaType.APPLICATION_JSON).content(body(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Authorization")));
    }

    @Test
    void adminRoutesRequireAdminRole() throws Exception {
        String username = newUsername();
        register(username);
        String userToken = loginAndGetToken(username, PASSWORD);

        Users admin = new Users();
        admin.setUsername(newUsername());
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        admin.setRole(RoleList.ROLE_ADMIN);
        userRepo.save(admin);
        String adminToken = loginAndGetToken(admin.getUsername(), PASSWORD);

        mvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
        // La ruta no existe todavia: un ADMIN pasa la autorizacion y recibe 404, no 403
        mvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void greetDoesNotCreateAnHttpSession() throws Exception {
        String username = newUsername();
        register(username);
        String token = loginAndGetToken(username, PASSWORD);

        mvc.perform(get("/").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(username)))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    void tokensSurviveARestartBecauseTheKeyComesFromConfiguration() {
        String secret = "dGVzdC1vbmx5LWtleS1kby1ub3QtdXNlLWluLXByb2QtMDEyMzQ1Njc4OQ==";
        JWTService beforeRestart = new JWTService(secret, 30);
        JWTService afterRestart = new JWTService(secret, 30);

        String token = beforeRestart.generateToken("alguien");

        assertThat(afterRestart.extractUserName(token)).isEqualTo("alguien");
        // Y el bean real usa esa misma clave de configuracion
        assertThat(jwtService.extractUserName(token)).isEqualTo("alguien");
    }
}

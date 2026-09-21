package com.telusko.authserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthServerIntegrationTest {

    private static final String PASSWORD = "Secreta-1234";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.issuer}")
    private String issuer;

    @Value("${app.web-client.id}")
    private String webClientId;

    @Value("${app.web-client.secret}")
    private String webClientSecret;

    @Value("${app.web-client.redirect-uri}")
    private String webRedirectUri;

    @Value("${app.api-client.id}")
    private String apiClientId;

    @Value("${app.api-client.secret}")
    private String apiClientSecret;

    @BeforeEach
    void seedUser() {
        jdbc.update("delete from users");
        jdbc.update("insert into users (username, password, role) values (?, ?, ?)",
                "ana", passwordEncoder.encode(PASSWORD), "ROLE_ADMIN");
    }

    @Test
    void discoveryDocumentAnnouncesTheIssuerAndEndpoints() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value(issuer))
                .andExpect(jsonPath("$.jwks_uri").value(issuer + "/oauth2/jwks"))
                .andExpect(jsonPath("$.end_session_endpoint").value(issuer + "/connect/logout"));
    }

    @Test
    void jwksPublishesOnlyThePublicKey() throws Exception {
        mvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test
    void usersFromTheDatabaseCanLogInWithTheirBcryptPassword() throws Exception {
        mvc.perform(formLogin("/login").user("ana").password(PASSWORD))
                .andExpect(authenticated().withUsername("ana"));
        mvc.perform(formLogin("/login").user("ana").password("incorrecta"))
                .andExpect(unauthenticated());
        mvc.perform(formLogin("/login").user("noexiste").password(PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    void browserWithoutSessionIsSentToTheLoginForm() throws Exception {
        mvc.perform(get("/oauth2/authorize").accept(MediaType.TEXT_HTML)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", webClientId)
                        .queryParam("scope", "openid")
                        .queryParam("redirect_uri", webRedirectUri)
                        .queryParam("code_challenge", challengeFor("x".repeat(50)))
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void clientCredentialsIssueASignedJwtAndRejectAWrongSecret() throws Exception {
        MvcResult result = mvc.perform(post("/oauth2/token").with(httpBasic(apiClientId, apiClientSecret))
                        .param("grant_type", "client_credentials")
                        .param("scope", "students.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();

        Jwt jwt = jwtDecoder.decode(json(result).get("access_token").asText());
        assertThat(jwt.getIssuer().toString()).isEqualTo(issuer);
        assertThat(jwt.getSubject()).isEqualTo(apiClientId);

        mvc.perform(post("/oauth2/token").with(httpBasic(apiClientId, "otro-secreto"))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorizationCodeFlowWithPkceReturnsTokensCarryingTheUserRoles() throws Exception {
        String verifier = "v".repeat(64);

        MvcResult authorize = mvc.perform(get("/oauth2/authorize").with(user("ana").roles("ADMIN"))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", webClientId)
                        .queryParam("scope", "openid")
                        .queryParam("state", "estado-123")
                        .queryParam("redirect_uri", webRedirectUri)
                        .queryParam("code_challenge", challengeFor(verifier))
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = authorize.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(webRedirectUri);
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
        assertThat(code).isNotBlank();

        MvcResult token = mvc.perform(post("/oauth2/token").with(httpBasic(webClientId, webClientSecret))
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", webRedirectUri)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.id_token").isNotEmpty())
                .andReturn();

        Jwt accessToken = jwtDecoder.decode(json(token).get("access_token").asText());
        assertThat(accessToken.getSubject()).isEqualTo("ana");
        assertThat(accessToken.getClaimAsStringList("roles")).containsExactly("ROLE_ADMIN");

        // Sin el code_verifier correcto (PKCE) el codigo no sirve, y ademas es de un solo uso
        mvc.perform(post("/oauth2/token").with(httpBasic(webClientId, webClientSecret))
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", webRedirectUri)
                        .param("code_verifier", verifier))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authorizeWithoutPkceIsRejected() throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorize").with(user("ana").roles("ADMIN"))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", webClientId)
                        .queryParam("scope", "openid")
                        .queryParam("redirect_uri", webRedirectUri))
                .andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location == null || !location.contains("code=")).isTrue();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String challengeFor(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}

package com.telusko.part29springsecex;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

// Ejemplo de un programa (no un navegador) usando la API: pide un token a auth-server con
// client_credentials y llama a la app con "Authorization: Bearer".
// Prueba manual: necesita auth-server en localhost:9000 y la app en 127.0.0.1:8080.
// Deshabilitada para que `mvn test` no dependa de eso; los tests automaticos estan en AuthFlowIntegrationTest.
@Disabled("Requiere auth-server (9000) y la app (8080) levantados")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RestClientTest {
    private final RestClient authServer = RestClient.builder().baseUrl("http://localhost:9000").build();
    private final RestClient app = RestClient.builder().baseUrl("http://127.0.0.1:8080").build();

    @Order(1)
    @Test
    public void callTheApiWithAClientCredentialsToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "students.read");

        JsonNode tokenResponse = authServer.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("part38-api-client", "dev-only-api-client-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String accessToken = tokenResponse.get("access_token").asText();

        ResponseEntity<String> students = app.get()
                .uri("/students")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(String.class);

        System.out.println(students.getBody());
        assertThat(students.getStatusCode().is2xxSuccessful()).isTrue();
    }

}

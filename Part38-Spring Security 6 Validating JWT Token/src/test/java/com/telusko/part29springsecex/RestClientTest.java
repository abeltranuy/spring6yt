package com.telusko.part29springsecex;
import com.telusko.part29springsecex.request.AuthRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

// Prueba manual contra un servidor ya levantado en localhost:8080 (con MySQL y el usuario admin/admin).
// Deshabilitada para que `mvn test` no dependa de eso; los tests automaticos estan en AuthFlowIntegrationTest.
@Disabled("Requiere el servidor levantado en localhost:8080")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RestClientTest {
    private final RestClient restClient;

    public RestClientTest() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:8080")
                .build();
    }

    @Order(1)
    @Test
    public void createEmployee() {
        AuthRequest newEmployee = new AuthRequest("admin", "admin");

        //Entity<String> entity = template.getForEntity("https://localhost:8080/login", String.class);
        //String body = entity.getBody();
        //MediaType contentType = entity.getHeaders().getContentType();
        //HttpStatus statusCode = entity.getStatusCode();

        ResponseEntity<String> savedEmployee = restClient.post()
                .uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(newEmployee)
                .retrieve()
                .toEntity(String.class);

        System.out.println("All Headers:");
        savedEmployee.getHeaders().forEach((key, value) -> System.out.println(key + ": " + value));
        System.out.println(savedEmployee.getBody());

        assertThat(savedEmployee.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(savedEmployee.getHeaders().getFirst("Authorization")).startsWith("Bearer ");
    }

}

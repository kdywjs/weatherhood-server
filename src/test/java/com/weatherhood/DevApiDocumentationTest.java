package com.weatherhood;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DevApiDocumentationTest {
    private static final String DOCUMENTATION_ORIGIN = "https://weatherhood-dev-docs.ansandy.co.kr";

    @LocalServerPort
    int port;

    @Test
    void documentationUsesPublicHttpsApi() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs"))
                    .GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"url\":\"https://weatherhood-dev-api.ansandy.co.kr\"");
        }
    }

    @Test
    void corsAllowsDocumentationApiMethodsWithoutCookies() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var allowed = client.send(preflight(DOCUMENTATION_ORIGIN, "GET"),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(allowed.statusCode()).isEqualTo(200);
            assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains(DOCUMENTATION_ORIGIN);
            assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
            var otherOrigin = client.send(preflight("https://example.com", "GET"),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(otherOrigin.statusCode()).isEqualTo(403);
            var writeRequest = client.send(preflight(DOCUMENTATION_ORIGIN, "POST"),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(writeRequest.statusCode()).isEqualTo(200);
            var authenticatedRequest = client.send(HttpRequest.newBuilder(
                                    URI.create("http://localhost:" + port + "/api/v1/me/regions"))
                            .header("Origin", DOCUMENTATION_ORIGIN)
                            .header("Access-Control-Request-Method", "PUT")
                            .header("Access-Control-Request-Headers", "Authorization,Content-Type")
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(authenticatedRequest.statusCode()).isEqualTo(200);
            assertThat(authenticatedRequest.headers().firstValue("Access-Control-Allow-Headers").orElseThrow())
                    .containsIgnoringCase("authorization");
            var rejected = client.send(preflight(DOCUMENTATION_ORIGIN, "PATCH"),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(rejected.statusCode()).isEqualTo(403);
        }
    }

    private HttpRequest preflight(String origin, String method) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/weather/today"))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
    }
}

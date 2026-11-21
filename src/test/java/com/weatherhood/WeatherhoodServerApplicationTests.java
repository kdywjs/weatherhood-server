package com.weatherhood;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;


@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WeatherhoodServerApplicationTests {
    @Autowired
    DataSource dataSource;
    @Autowired
    Flyway flyway;
    @Autowired
    EntityManagerFactory entityManagerFactory;
    @LocalServerPort
    int port;
    @Autowired
    ApplicationContext applicationContext;

    @Test
    void readinessExposesOnlyStatusAndRejectsTrafficWhenUnavailable() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/healthz"))
                    .GET().build();
            var healthy = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(healthy.statusCode()).isEqualTo(200);
            assertThat(healthy.body()).isEqualTo("{\"status\":\"UP\"}");
            try {
                AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
                var unavailable = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(unavailable.statusCode()).isEqualTo(503);
                assertThat(unavailable.body()).doesNotContain("components", "details", "database");
            } finally {
                AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
            }
        }
    }

    @Test
    void postgresMigrationsAndWeatherEntitiesInitialize() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var tables = statement.executeQuery("select tablename from pg_tables where schemaname = 'public'")) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            var names = new java.util.HashSet<String>();
            while (tables.next()) {
                names.add(tables.getString(1));
            }
            assertThat(names).contains("flyway_schema_history", "weather_value", "weather_region",
                    "weather_collection", "user_account", "user_session", "login_challenge", "saved_region");
        }
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("5");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(entityManagerFactory.getMetamodel().getEntities()).hasSize(6);
    }

    @Test
    void apiDocumentationIncludesTodayWeather() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var spec = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(spec.statusCode()).isEqualTo(200);
            assertThat(spec.body()).contains("\"openapi\"", "/api/v1/weather/today");
            var ui = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/swagger-ui/index.html")).GET()
                            .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(ui.statusCode()).isEqualTo(200);
            assertThat(ui.body()).contains("Swagger UI");
        }
    }
}

package com.weatherhood;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WeatherhoodServerApplicationTests {
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;
    @LocalServerPort int port;

    @Test
    void postgresMigrationAndJpaInitializeWithoutBusinessTables() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var tables = statement.executeQuery("select tablename from pg_tables where schemaname = 'public'")) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(tables.next()).isTrue();
            assertThat(tables.getString(1)).isEqualTo("flyway_schema_history");
            assertThat(tables.next()).isFalse();
        }
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(entityManagerFactory.getMetamodel().getEntities()).isEmpty();
    }

    @Test
    void apiDocumentationIsServedWithoutFeatureEndpoints() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var spec = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(spec.statusCode()).isEqualTo(200);
            assertThat(spec.body()).contains("\"openapi\"", "\"paths\":{}");
            var ui = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/swagger-ui/index.html")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(ui.statusCode()).isEqualTo(200);
            assertThat(ui.body()).contains("Swagger UI");
        }
    }
}

package com.weatherhood.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.weatherhood.TestcontainersConfiguration;
import com.weatherhood.auth.service.ExpiredCredentialCleaner;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthRegionIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");
    private static final String REGION = "51bf94b3-e51d-5ef7-b00f-45769ea0e8cb";
    private static final String SECOND_REGION = "b2757731-02fd-5add-923e-582572a7fcbd";
    private static final IdentityProviderStub PROVIDER = createProvider();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;
    @Autowired
    JdbcTemplate db;
    @Autowired
    JsonMapper json;
    @Autowired
    ExpiredCredentialCleaner cleaner;
    @MockitoBean
    Clock clock;

    @DynamicPropertySource
    static void configureProviders(DynamicPropertyRegistry properties) {
        properties.add("auth.apple.jwk-set-uri", () -> PROVIDER.baseUrl() + "/auth/keys");
        properties.add("auth.apple.base-url", PROVIDER::baseUrl);
        properties.add("auth.apple.team-id", () -> "test-team");
        properties.add("auth.apple.key-id", () -> "client-test-key");
        properties.add("auth.apple.private-key-path", () -> PROVIDER.clientKeyFile.toString());
        properties.add("auth.token-encryption-key", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        properties.add("location.kakao.base-url", PROVIDER::baseUrl);
        properties.add("location.kakao.api-key", () -> "test-kakao-key");
    }

    @BeforeEach
    void resetDatabaseAndClock() {
        db.update("delete from user_account");
        db.update("delete from login_challenge");
        PROVIDER.resetFailures();
        setTime(NOW);
    }

    @AfterAll
    static void closeProvider() throws Exception {
        HTTP.close();
        PROVIDER.close();
    }

    @Test
    void loginUsesRealSignatureCodeExchangeAndEncryptedStorage() throws Exception {
        var result = login("apple-user");
        assertThat(result.statusCode()).isEqualTo(200);
        var grant = data(result);
        String token = grant.path("accessToken").asString();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(grant.path("expiresAt").asString()).isEqualTo(NOW.plus(Duration.ofDays(30)).toString());
        assertThat(result.headers().firstValue("Cache-Control")).contains("no-store");
        String hash = db.queryForObject("select token_hash from user_session", String.class);
        assertThat(hash).hasSize(64).doesNotContain(token);
        String ciphertext = db.queryForObject("select refresh_token_ciphertext from user_account", String.class);
        assertThat(ciphertext).doesNotContain("refresh-");
        var current = request("GET", "/api/v1/me", null, token);
        assertThat(current.statusCode()).isEqualTo(200);
        assertThat(data(current).path("id")).isEqualTo(grant.path("user").path("id"));
        assertThat(result.body()).doesNotContain("apple-user", "refresh-", "identityToken");
    }

    @Test
    void sameAppleSubjectKeepsAccountAndLogoutOnlyEndsCurrentSession() throws Exception {
        var first = data(login("same-user"));
        String firstToken = first.path("accessToken").asString();
        request("PUT", "/api/v1/me/regions/" + REGION, null, firstToken);
        var second = data(login("same-user"));
        String secondToken = second.path("accessToken").asString();
        assertThat(second.path("user")).isEqualTo(first.path("user"));
        assertThat(db.queryForObject("select count(*) from user_account", Long.class)).isEqualTo(1);
        assertThat(data(request("GET", "/api/v1/me/regions", null, secondToken)).path("items").size()).isEqualTo(1);
        assertThat(request("POST", "/api/v1/auth/logout", null, firstToken).statusCode()).isEqualTo(200);
        assertError(request("GET", "/api/v1/me", null, firstToken), 401, "AUTH001");
        assertThat(request("GET", "/api/v1/me", null, secondToken).statusCode()).isEqualTo(200);
    }

    @Test
    void challengeNonceExpiryAndReplayFailWithoutCreatingAccounts() throws Exception {
        var challenge = challenge();
        String nonce = challenge.path("nonce").asString();
        String identity = PROVIDER.issueIdentity("replay-user", "wrong-nonce");
        String code = PROVIDER.registerCode("replay-user", identity);
        int before = PROVIDER.exchanges.get();
        assertError(submitLogin(challenge, identity, code), 401, "AUTH002");
        assertThat(PROVIDER.exchanges.get()).isEqualTo(before);
        identity = PROVIDER.issueIdentity("replay-user", nonce);
        code = PROVIDER.registerCode("replay-user", identity);
        assertThat(submitLogin(challenge, identity, code).statusCode()).isEqualTo(200);
        assertError(submitLogin(challenge, identity, code), 401, "AUTH002");
        var expired = challenge();
        setTime(NOW.plusSeconds(300));
        assertError(submitLogin(expired, identity, code), 401, "AUTH002");
        assertThat(db.queryForObject("select count(*) from user_session", Long.class)).isEqualTo(1);
    }

    @Test
    void codeExchangeSubjectMismatchAndProviderFailureNeverIssueSessions() throws Exception {
        var challenge = challenge();
        String nonce = challenge.path("nonce").asString();
        String identity = PROVIDER.issueIdentity("user-one", nonce);
        String other = PROVIDER.issueIdentity("user-two", nonce);
        assertError(submitLogin(challenge, identity, PROVIDER.registerCode("user-two", other)), 401, "AUTH002");
        assertError(submitLogin(challenge, identity, "invalid-code"), 401, "AUTH002");
        PROVIDER.tokenStatus = 503;
        assertError(submitLogin(challenge, identity, "unused-code"), 503, "AUTH003");
        assertThat(db.queryForObject("select count(*) from user_account", Long.class)).isZero();
    }

    @Test
    void anonymousReadsWorkAndPrivateRequestsUseCommonUnauthorizedErrors() throws Exception {
        assertThat(request("GET", "/api/v1/regions/" + REGION, null, null).statusCode()).isEqualTo(200);
        assertError(request("GET", "/api/v1/weather/today?regionId=missing", null, null), 404, "REGION_NOT_FOUND");
        var denied = request("GET", "/api/v1/me/regions", null, null);
        assertError(denied, 401, "AUTH001");
        assertThat(denied.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertError(request("GET", "/api/v1/me", null, "not-a-token"), 401, "AUTH001");
        var invalid = request("POST", "/api/v1/auth/apple", Map.of("challengeId", "not-uuid"), null);
        assertError(invalid, 400, "INVALID_REQUEST");
    }

    @Test
    void expiryIsAbsoluteAndAppleRevocationEndsAllSessions() throws Exception {
        String first = token(login("expiry-user"));
        setTime(NOW.plus(Duration.ofDays(30)));
        assertError(request("GET", "/api/v1/me", null, first), 401, "AUTH001");
        setTime(NOW);
        String second = token(login("revoked-user"));
        String third = token(login("revoked-user"));
        setTime(NOW.plus(Duration.ofDays(1)));
        PROVIDER.refreshInvalid = true;
        assertError(request("GET", "/api/v1/me", null, second), 401, "AUTH001");
        assertError(request("GET", "/api/v1/me", null, third), 401, "AUTH001");
        assertThat(db.queryForObject("select count(*) from user_session", Long.class)).isEqualTo(1);
    }

    @Test
    void dailyValidationRunsOnceAndFailuresDoNotExtendSession() throws Exception {
        String token = token(login("daily-user"));
        setTime(NOW.plus(Duration.ofDays(1)));
        int checks = PROVIDER.refreshChecks.get();
        assertThat(request("GET", "/api/v1/me", null, token).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/api/v1/me", null, token).statusCode()).isEqualTo(200);
        assertThat(PROVIDER.refreshChecks.get()).isEqualTo(checks + 1);
        setTime(NOW.plus(Duration.ofDays(2)));
        PROVIDER.tokenStatus = 503;
        assertError(request("GET", "/api/v1/me", null, token), 503, "AUTH003");
        assertThat(db.queryForObject("select expires_at from user_session", java.sql.Timestamp.class).toInstant())
                .isEqualTo(NOW.plus(Duration.ofDays(30)));
        PROVIDER.tokenStatus = 200;
        assertThat(request("GET", "/api/v1/me", null, token).statusCode()).isEqualTo(200);
    }

    @Test
    void savedRegionsAreIsolatedOrderedAndDefaultDeletionRestoresCurrentLocation() throws Exception {
        String a = token(login("owner-a"));
        String b = token(login("owner-b"));
        assertThat(data(request("GET", "/api/v1/me/regions", null, a)).path("defaultLocation").path("region").isNull())
                .isTrue();
        request("PUT", "/api/v1/me/regions/" + REGION, null, a);
        request("PUT", "/api/v1/me/regions/" + SECOND_REGION, null, a);
        var duplicate = data(request("PUT", "/api/v1/me/regions/" + REGION, null, a));
        assertThat(duplicate.path("items").size()).isEqualTo(2);
        assertThat(duplicate.path("items").get(0).path("id").asString()).isEqualTo(REGION);
        assertThat(data(request("GET", "/api/v1/me/regions", null, b)).path("items").size()).isZero();
        assertError(request("PUT", "/api/v1/me/default-location",
                Map.of("type", "saved_region", "regionId", REGION), b), 409, "REGION006");
        var reordered = request("PUT", "/api/v1/me/regions/order", Map.of("regionIds", List.of(SECOND_REGION, REGION)),
                a);
        assertThat(data(reordered).path("items").get(0).path("id").asString()).isEqualTo(SECOND_REGION);
        assertError(request("PUT", "/api/v1/me/regions/order", Map.of("regionIds", List.of(REGION, REGION)), a),
                409, "REGION005");
        request("PUT", "/api/v1/me/default-location", Map.of("type", "saved_region", "regionId", REGION), a);
        var deleted = data(request("DELETE", "/api/v1/me/regions/" + REGION, null, a));
        assertThat(deleted.path("defaultLocation").path("type").asString()).isEqualTo("current_location");
        assertThat(deleted.path("defaultLocation").path("region").isNull()).isTrue();
        assertThat(deleted.path("items").size()).isEqualTo(1);
        assertThat(request("DELETE", "/api/v1/me/regions/" + REGION, null, a).statusCode()).isEqualTo(200);
    }

    @Test
    void concurrentAddsPreserveLimitAndDoNotDuplicateRegions() throws Exception {
        String token = token(login("concurrent-user"));
        List<String> ids = db.queryForList("select id from weather_region order by id limit 12", String.class);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (String id : ids) {
                tasks.add(() -> request("PUT", "/api/v1/me/regions/" + id, null, token).statusCode());
            }
            List<Integer> statuses = new ArrayList<>();
            for (var future : pool.invokeAll(tasks)) {
                statuses.add(future.get());
            }
            assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(10);
            assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(2);
            var items = data(request("GET", "/api/v1/me/regions", null, token)).path("items");
            String existing = items.get(0).path("id").asString();
            List<Callable<Integer>> duplicates = List.of(
                    () -> request("PUT", "/api/v1/me/regions/" + existing, null, token).statusCode(),
                    () -> request("PUT", "/api/v1/me/regions/" + existing, null, token).statusCode());
            for (var future : pool.invokeAll(duplicates)) {
                assertThat(future.get()).isEqualTo(200);
            }
        }
        assertThat(db.queryForObject("select count(*) from saved_region", Long.class)).isEqualTo(10);
    }

    @Test
    void accountDeletionRevokesAppleAndCascadesOnlyPersonalData() throws Exception {
        String token = token(login("delete-user"));
        String other = token(login("surviving-user"));
        request("PUT", "/api/v1/me/regions/" + REGION, null, token);
        PROVIDER.revokeStatus = 503;
        assertError(request("DELETE", "/api/v1/me", null, token), 503, "AUTH003");
        assertThat(request("GET", "/api/v1/me", null, token).statusCode()).isEqualTo(200);
        PROVIDER.revokeStatus = 200;
        int revocations = PROVIDER.revocations.get();
        assertThat(request("DELETE", "/api/v1/me", null, token).statusCode()).isEqualTo(200);
        assertThat(PROVIDER.revocations.get()).isEqualTo(revocations + 1);
        assertError(request("GET", "/api/v1/me", null, token), 401, "AUTH001");
        assertThat(request("GET", "/api/v1/me", null, other).statusCode()).isEqualTo(200);
        assertThat(db.queryForObject("select count(*) from saved_region", Long.class)).isZero();
        assertThat(db.queryForObject("select count(*) from weather_region", Long.class)).isEqualTo(3565);
    }

    @Test
    void searchPreservesInnerSpacesEscapesWildcardsAndPaginates() throws Exception {
        var page = search("  서울특별시  ", 0);
        assertThat(page.path("items").size()).isEqualTo(20);
        assertThat(page.path("hasNext").asBoolean()).isTrue();
        var next = search("서울특별시", 1);
        assertThat(next.path("items").get(0)).isNotEqualTo(page.path("items").get(0));
        assertThat(search("종로구 청운효자동", 0).path("items").size()).isEqualTo(1);
        assertThat(search("종로구  청운효자동", 0).path("items").size()).isZero();
        assertThat(search("%_", 0).path("items").size()).isZero();
        assertError(request("GET", "/api/v1/regions?query=a", null, null), 400, "INVALID_REQUEST");
        assertError(request("GET", "/api/v1/regions?query=ab&page=-1", null, null), 400, "INVALID_REQUEST");
    }

    @Test
    void coordinatesOnlyMapAdministrativeCodesAndReportDistinctFailures() throws Exception {
        var coordinates = Map.of("latitude", 37.4012191, "longitude", 127.1086228);
        var resolved = request("POST", "/api/v1/regions/resolve", coordinates, null);
        assertThat(data(resolved).path("id").asString()).isEqualTo(REGION);
        assertThat(PROVIDER.lastKakaoQuery).isEqualTo("input_coord=WGS84&x=127.1086228&y=37.4012191");
        assertThat(db.queryForObject("select count(*) from saved_region", Long.class)).isZero();
        assertError(request("POST", "/api/v1/regions/resolve", Map.of("latitude", 91, "longitude", 0), null),
                400, "INVALID_REQUEST");
        PROVIDER.kakaoBody = "{\"documents\":[{\"region_type\":\"B\",\"code\":\"4113510900\"}]}";
        assertError(request("POST", "/api/v1/regions/resolve", coordinates, null), 422, "REGION001");
        PROVIDER.kakaoBody = "{\"documents\":[{\"region_type\":\"H\",\"code\":\"9999999999\"}]}";
        assertError(request("POST", "/api/v1/regions/resolve", coordinates, null), 503, "REGION002");
        PROVIDER.kakaoStatus = 401;
        assertError(request("POST", "/api/v1/regions/resolve", coordinates, null), 503, "REGION003");
        PROVIDER.kakaoStatus = 200;
        PROVIDER.kakaoBody = "malformed";
        assertError(request("POST", "/api/v1/regions/resolve", coordinates, null), 503, "REGION003");
    }

    @Test
    void concurrentLoginsForSameSubjectCreateOneAccount() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.<Callable<HttpResponse<String>>>of(
                    () -> login("same-concurrent-subject"), () -> login("same-concurrent-subject")));
            for (var result : results) {
                assertThat(result.get().statusCode()).isEqualTo(200);
            }
        }
        assertThat(db.queryForObject("select count(*) from user_account", Long.class)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from user_session", Long.class)).isEqualTo(2);
    }

    @Test
    void concurrentChallengeConsumptionCreatesExactlyOneSession() throws Exception {
        var challenge = challenge();
        String identity = PROVIDER.issueIdentity("one-challenge", challenge.path("nonce").asString());
        String firstCode = PROVIDER.registerCode("one-challenge", identity);
        String secondCode = PROVIDER.registerCode("one-challenge", identity);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = pool.invokeAll(List.<Callable<Integer>>of(
                    () -> submitLogin(challenge, identity, firstCode).statusCode(),
                    () -> submitLogin(challenge, identity, secondCode).statusCode()));
            assertThat(List.of(futures.get(0).get(), futures.get(1).get())).containsExactlyInAnyOrder(200, 401);
        }
        assertThat(db.queryForObject("select count(*) from user_session", Long.class)).isEqualTo(1);
    }

    @Test
    void expiredCredentialCleanupLeavesUsersAndSavedRegions() throws Exception {
        String token = token(login("cleanup-user"));
        request("PUT", "/api/v1/me/regions/" + REGION, null, token);
        challenge();
        setTime(NOW.plus(Duration.ofDays(30)));
        cleaner.deleteExpiredCredentials();
        assertThat(db.queryForObject("select count(*) from user_session", Long.class)).isZero();
        assertThat(db.queryForObject("select count(*) from login_challenge", Long.class)).isZero();
        assertThat(db.queryForObject("select count(*) from user_account", Long.class)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from saved_region", Long.class)).isEqualTo(1);
    }

    @Test
    void openApiDocumentsBearerWithoutExposingInternalPrincipal() throws Exception {
        var response = request("GET", "/v3/api-docs", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var spec = json.readTree(response.body());
        assertThat(spec.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asString())
                .isEqualTo("bearer");
        assertThat(spec.path("paths").path("/api/v1/me/regions").path("get").path("security").size()).isEqualTo(1);
        assertThat(spec.path("paths").path("/api/v1/regions").path("get").path("security").isMissingNode()).isTrue();
        assertThat(response.body()).doesNotContain("SessionIdentity", "tokenHash", "refreshTokenCiphertext");
    }

    private JsonNode search(String query, int page) throws Exception {
        var response = request("GET", "/api/v1/regions?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&page=" + page, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return data(response);
    }

    private JsonNode challenge() throws Exception {
        var response = request("POST", "/api/v1/auth/apple/challenges", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return data(response);
    }

    private HttpResponse<String> login(String subject) throws Exception {
        var challenge = challenge();
        String identity = PROVIDER.issueIdentity(subject, challenge.path("nonce").asString());
        return submitLogin(challenge, identity, PROVIDER.registerCode(subject, identity));
    }

    private HttpResponse<String> submitLogin(JsonNode challenge, String identity, String code) throws Exception {
        return request("POST", "/api/v1/auth/apple", Map.of("challengeId", challenge.path("challengeId").asString(),
                "identityToken", identity, "authorizationCode", code), null);
    }

    private String token(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return data(response).path("accessToken").asString();
    }

    private HttpResponse<String> request(String method, String path, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) {
        return json.readTree(response.body()).path("data");
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        var body = json.readTree(response.body());
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(body.path("error").path("code").asString()).isEqualTo(code);
    }

    private void setTime(Instant instant) {
        when(clock.instant()).thenReturn(instant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        PROVIDER.now = instant;
    }

    private static IdentityProviderStub createProvider() {
        try {
            return new IdentityProviderStub();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}

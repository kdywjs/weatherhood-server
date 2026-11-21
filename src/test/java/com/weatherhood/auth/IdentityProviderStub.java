package com.weatherhood.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

/**
 * 네트워크·서명·코드 일회성까지 확인하는 로컬 Apple/Kakao HTTP 대역. 실제 키를 읽지 않는다.
 */
public final class IdentityProviderStub implements AutoCloseable {
    public static final String CLIENT_ID = "co.kr.ansandy.weatherhood";
    public final RSAKey signingKey;
    public final ECKey clientKey;
    public final Path clientKeyFile;
    public final AtomicInteger exchanges = new AtomicInteger();
    public final AtomicInteger refreshChecks = new AtomicInteger();
    public final AtomicInteger revocations = new AtomicInteger();
    public volatile int revokeStatus = 200;
    public volatile int tokenStatus = 200;
    public volatile boolean refreshInvalid;
    public volatile String kakaoBody;
    public volatile int kakaoStatus = 200;
    public volatile String lastKakaoQuery;
    private final Map<String, Grant> codes = new ConcurrentHashMap<>();
    private final Map<String, String> refreshSubjects = new ConcurrentHashMap<>();
    private final HttpServer server;
    private final JsonMapper json = new JsonMapper();
    public volatile Instant now = Instant.parse("2026-11-21T03:00:00Z");

    public IdentityProviderStub() throws IOException, JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("apple-test-key").generate();
        clientKey = new ECKeyGenerator(Curve.P_256).keyID("client-test-key").generate();
        clientKeyFile = Files.createTempFile("weatherhood-test-apple-", ".p8");
        Files.writeString(clientKeyFile, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(clientKey.toPrivateKey().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/keys", exchange -> respond(exchange, 200,
                new JWKSet(signingKey.toPublicJWK()).toString()));
        server.createContext("/auth/token", this::exchangeToken);
        server.createContext("/auth/revoke", exchange -> {
            revocations.incrementAndGet();
            respond(exchange, revokeStatus, "{}");
        });
        server.createContext("/v2/local/geo/coord2regioncode.json", exchange -> {
            lastKakaoQuery = exchange.getRequestURI().getQuery();
            if (!"KakaoAK test-kakao-key".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                respond(exchange, 401, "{}");
                return;
            }
            respond(exchange, kakaoStatus, kakaoBody);
        });
        resetFailures();
        server.start();
    }

    public void resetFailures() {
        revokeStatus = 200;
        tokenStatus = 200;
        refreshInvalid = false;
        kakaoStatus = 200;
        kakaoBody = """
                {"documents":[{"region_type":"B","code":"4113510900"},
                    {"region_type":"H","code":"4113565500"}]}
                """;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String issueIdentity(String subject, String nonce) throws JOSEException {
        return signClaims(new JWTClaimsSet.Builder().issuer("https://appleid.apple.com").audience(CLIENT_ID)
                .subject(subject).issueTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(600))).claim("nonce", nonce).build());
    }

    public String signClaims(JWTClaimsSet claims) throws JOSEException {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    public String registerCode(String subject, String identity) {
        String code = "code-" + UUID.randomUUID();
        String refresh = "refresh-" + UUID.randomUUID();
        codes.put(code, new Grant(identity, refresh));
        refreshSubjects.put(refresh, subject);
        return code;
    }

    private void exchangeToken(HttpExchange exchange) throws IOException {
        if (tokenStatus != 200) {
            respond(exchange, tokenStatus, "{\"error\":\"temporarily_unavailable\"}");
            return;
        }
        Map<String, String> form = Arrays.stream(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8).split("&"))
                .map(part -> part.split("=", 2)).collect(Collectors.toMap(part -> part[0],
                        part -> URLDecoder.decode(part[1], StandardCharsets.UTF_8)));
        if (!validClientSecret(form)) {
            respond(exchange, 400, "{\"error\":\"invalid_client\"}");
            return;
        }
        if ("refresh_token".equals(form.get("grant_type"))) {
            refreshChecks.incrementAndGet();
            String subject = refreshSubjects.get(form.get("refresh_token"));
            if (refreshInvalid || subject == null) {
                respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
                return;
            }
            try {
                respond(exchange, 200, json.writeValueAsString(Map.of("id_token", issueIdentity(subject, "unused"))));
            } catch (JOSEException exception) {
                throw new IOException("Test signing failed");
            }
            return;
        }
        exchanges.incrementAndGet();
        Grant grant = codes.remove(form.get("code"));
        if (grant == null) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        respond(exchange, 200, json.writeValueAsString(Map.of("id_token", grant.identity(),
                "refresh_token", grant.refresh())));
    }

    private boolean validClientSecret(Map<String, String> form) {
        try {
            var jwt = SignedJWT.parse(form.get("client_secret"));
            var claims = jwt.getJWTClaimsSet();
            return CLIENT_ID.equals(form.get("client_id")) && !form.containsKey("redirect_uri")
                    && jwt.verify(new ECDSAVerifier(clientKey.toPublicJWK()))
                    && jwt.getHeader().getKeyID().equals("client-test-key")
                    && claims.getIssuer().equals("test-team") && claims.getSubject().equals(CLIENT_ID)
                    && claims.getAudience().equals(java.util.List.of("https://appleid.apple.com"))
                    && claims.getExpirationTime().toInstant().equals(now.plusSeconds(300));
        } catch (ParseException | JOSEException exception) {
            return false;
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Override
    public void close() throws IOException {
        server.stop(0);
        Files.deleteIfExists(clientKeyFile);
    }

    private record Grant(String identity, String refresh) {
    }
}

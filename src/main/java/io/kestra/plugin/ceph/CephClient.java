package io.kestra.plugin.ceph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.SslOptions;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared HTTP execution layer for the Ceph Dashboard REST API. Centralised here so both tasks
 * (via {@link AbstractCephConnection}) and the polling trigger (which extends {@code AbstractTrigger}
 * and cannot extend a {@code Task} subclass) can reuse the same authentication, header, and
 * error-handling logic.
 */
public final class CephClient {

    public static final ObjectMapper MAPPER = JacksonMapper.ofJson(false);

    /**
     * The Ceph Dashboard API requires a versioned Accept header on every request, including
     * the initial authentication call, or it responds with HTTP 406.
     */
    private static final String API_VERSION_HEADER = "application/vnd.ceph.api.v1.0+json";

    private CephClient() {
    }

    public static String baseUrl(String host, int port) {
        return "https://" + host + ":" + port + "/api";
    }

    /**
     * Resolves an authenticated session, preferring a pre-obtained {@code token} over
     * username/password. When {@code token} is present it is used as-is (no call to
     * {@code POST /api/auth}); otherwise {@code username}/{@code password} are exchanged for a
     * JWT via {@code POST /api/auth}. The token is never logged.
     */
    public static CephSession connect(
        RunContext runContext, String host, int port, String username, String password, String token, boolean skipSsl
    ) throws IOException, IllegalVariableEvaluationException, HttpClientException {
        var url = baseUrl(host, port);

        if (token != null && !token.isBlank()) {
            return new CephSession(runContext, url, token, skipSsl);
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Ceph authentication requires either 'token' or 'password'.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required when authenticating with 'password'.");
        }

        var resolvedToken = authenticate(runContext, url, username, password, skipSsl);
        return new CephSession(runContext, url, resolvedToken, skipSsl);
    }

    private static String authenticate(RunContext runContext, String baseUrl, String username, String password, boolean skipSsl)
        throws IOException, IllegalVariableEvaluationException, HttpClientException {
        var response = execute(
            runContext,
            "POST",
            baseUrl + "/auth",
            null,
            skipSsl,
            Map.of("username", username, "password", password),
            false
        );

        if (response.getBody() == null || response.getBody().isBlank()) {
            throw new IllegalStateException("Ceph authentication succeeded but the response body was empty.");
        }

        var auth = MAPPER.readValue(response.getBody(), AuthResponse.class);
        if (auth.token() == null || auth.token().isBlank()) {
            throw new IllegalStateException("Ceph authentication response did not include a token.");
        }
        return auth.token();
    }

    /**
     * Executes a single Ceph Dashboard API request, applying the required Accept header and
     * bearer token, and surfacing non-2xx responses as a clear, actionable exception.
     *
     * @param allowNotFound when {@code true}, an HTTP 404 response is returned to the caller
     *                      instead of being thrown, so callers such as delete operations can
     *                      distinguish "resource did not exist" from a hard failure.
     */
    static HttpResponse<String> execute(
        RunContext runContext,
        String method,
        String uri,
        String token,
        boolean skipSsl,
        Object body,
        boolean allowNotFound
    ) throws IOException, IllegalVariableEvaluationException, HttpClientException {
        var response = send(runContext, method, uri, token, skipSsl, body, API_VERSION_HEADER);
        var status = response.getStatus() != null ? response.getStatus().getCode() : 0;

        // The Ceph API is versioned per endpoint: some endpoints require a newer version than the
        // default 1.0 and answer 415 stating the version they expect (e.g. "endpoint is '2.0'").
        // Retry once with the version the server names so callers do not need per-endpoint knowledge.
        if (status == 415) {
            var required = parseRequiredVersion(response.getBody());
            if (required != null) {
                response = send(runContext, method, uri, token, skipSsl, body,
                    "application/vnd.ceph.api.v" + required + "+json");
                status = response.getStatus() != null ? response.getStatus().getCode() : 0;
            }
        }

        if (status == 401 || status == 403) {
            throw new IllegalStateException(
                "Ceph Dashboard API rejected the request (HTTP " + status + "). Verify that the configured credentials (username/password, or token) are correct, have API access, and that the token has not expired."
            );
        }

        if (status == 404 && allowNotFound) {
            return response;
        }

        if (status < 200 || status >= 300) {
            throw new IllegalStateException(
                "Ceph Dashboard API request failed (HTTP " + status + ") for " + method + " " + uri + ": " + truncate(response.getBody())
            );
        }

        return response;
    }

    /**
     * Sends a single request with the given Accept version header. allowFailed=true so non-2xx
     * responses are returned normally (with the body parsed as String) rather than thrown as an
     * HttpClientResponseException whose wrapped body type is not guaranteed to match.
     */
    private static HttpResponse<String> send(
        RunContext runContext,
        String method,
        String uri,
        String token,
        boolean skipSsl,
        Object body,
        String acceptHeader
    ) throws IOException, IllegalVariableEvaluationException, HttpClientException {
        var requestBuilder = HttpRequest.builder()
            .method(method)
            .uri(URI.create(uri))
            .addHeader("Accept", acceptHeader);

        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
        }

        if (body != null) {
            requestBuilder
                .addHeader("Content-Type", "application/json")
                .body(HttpRequest.JsonRequestBody.builder().content(body).build());
        }

        var config = HttpConfiguration.builder()
            .allowFailed(Property.ofValue(true))
            .ssl(SslOptions.builder().insecureTrustAllCertificates(Property.ofValue(skipSsl)).build())
            .build();

        try (var client = new HttpClient(runContext, config)) {
            return client.request(requestBuilder.build(), String.class);
        }
    }

    /**
     * Extracts the major.minor version from a 415 body, e.g. {@code "endpoint is '2.0'"} yields
     * {@code "2.0"}. Returns {@code null} if no version can be parsed.
     */
    private static String parseRequiredVersion(String body) {
        if (body == null) {
            return null;
        }
        var matcher = java.util.regex.Pattern.compile("endpoint is '([0-9]+\\.[0-9]+)'").matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String truncate(String body) {
        if (body == null || body.isBlank()) {
            return "<empty response body>";
        }
        var collapsed = body.strip().replaceAll("\\s+", " ");
        return collapsed.length() > 500 ? collapsed.substring(0, 500) + "..." : collapsed;
    }

    /**
     * Parses the payload shared by {@code /health/full} and {@code /health/minimal}. Both endpoints
     * wrap the actual health report under a top-level {@code health} object (the rest of the response
     * carries unrelated cluster metadata such as {@code df}, {@code pools}, {@code mon_status}); the
     * {@code status} and {@code checks} fields live inside that nested object. {@code checks} is a
     * JSON array of check objects, each carrying a {@code type} and a {@code summary.message}. Older
     * Ceph releases (and a raw {@code ceph health detail} dump) instead return {@code checks} as a
     * map keyed by check type, so that shape is also supported. Ceph does not return a flat
     * human-readable summary, so one is derived here from each check's message.
     */
    public static CephHealth parseHealth(Map<String, Object> raw) {
        if (raw == null) {
            return new CephHealth(null, List.of(), null);
        }

        var health = asMap(raw.get("health"));
        var source = health != null ? health : raw;

        var status = (String) source.get("status");
        var checks = source.get("checks");
        return new CephHealth(status, extractSummary(checks), checks);
    }

    public static boolean isDegraded(String status) {
        return "HEALTH_WARN".equals(status) || "HEALTH_ERR".equals(status);
    }

    /**
     * Builds the composite {@code image_spec} path segment (`{pool_name}/{image_name}`) used by the
     * RBD image and snapshot endpoints, percent-encoding each part individually and joining them
     * with an encoded slash (`%2F`) as the API expects.
     */
    public static String imageSpec(String poolName, String imageName) {
        return pathSegment(poolName) + "%2F" + pathSegment(imageName);
    }

    /**
     * Percent-encodes a single value for safe use as one path segment (or query parameter value) in
     * a Ceph Dashboard API URI. {@link URLEncoder#encode(String, java.nio.charset.Charset)} is built
     * for form bodies and encodes a space as {@code +}, which is invalid in a URI path, so it is
     * replaced with the correct {@code %20} afterwards.
     */
    public static String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static List<String> extractSummary(Object checks) {
        var messages = new ArrayList<String>();

        if (checks instanceof List<?> list) {
            for (var entry : list) {
                var check = asMap(entry);
                if (check == null) {
                    continue;
                }
                var summary = asMap(check.get("summary"));
                if (summary != null && summary.get("message") != null) {
                    messages.add(check.get("type") + ": " + summary.get("message"));
                }
            }
        } else if (checks instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                var check = asMap(entry.getValue());
                if (check == null) {
                    continue;
                }
                var summary = asMap(check.get("summary"));
                if (summary != null && summary.get("message") != null) {
                    messages.add(entry.getKey() + ": " + summary.get("message"));
                }
            }
        }

        return messages;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    public static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AuthResponse(String token) {
    }

    public record CephHealth(String status, List<String> summary, Object checks) {
    }
}

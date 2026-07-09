package io.kestra.plugin.ceph;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.runners.RunContext;

import java.io.IOException;

/**
 * An authenticated session against a Ceph Dashboard instance, holding the JWT obtained once from
 * {@code POST /api/auth} for the duration of a single task execution. All requests issued through
 * this session carry the {@code Authorization} and {@code Accept} headers required by the API.
 */
public final class CephSession {

    /**
     * Default bound for {@link #getWithRetry(String, TypeReference)}: 10 attempts, 1 second apart
     * (~10s total), enough to ride out the Ceph Dashboard task manager processing a create
     * operation asynchronously without stalling a flow indefinitely on a genuinely missing resource.
     */
    public static final int DEFAULT_RETRY_ATTEMPTS = 10;
    public static final long DEFAULT_RETRY_DELAY_MILLIS = 1000L;

    private final RunContext runContext;
    private final String baseUrl;
    private final String token;
    private final boolean skipSsl;

    CephSession(RunContext runContext, String baseUrl, String token, boolean skipSsl) {
        this.runContext = runContext;
        this.baseUrl = baseUrl;
        this.token = token;
        this.skipSsl = skipSsl;
    }

    public <T> T get(String path, TypeReference<T> type) throws IOException, IllegalVariableEvaluationException, HttpClientException {
        return parse(exec("GET", path, null, false), type);
    }

    /**
     * Fetches {@code path} using the default retry bound. See
     * {@link #getWithRetry(String, TypeReference, int, long)}.
     */
    public <T> T getWithRetry(String path, TypeReference<T> type) throws IOException, IllegalVariableEvaluationException, HttpClientException {
        return getWithRetry(path, type, DEFAULT_RETRY_ATTEMPTS, DEFAULT_RETRY_DELAY_MILLIS);
    }

    /**
     * Fetches {@code path}, tolerating a transient HTTP 404 for up to {@code attempts} tries. The
     * Ceph Dashboard processes some create operations asynchronously via its task manager, so a GET
     * issued right after a POST can briefly still 404 before the resource becomes visible. Sleeps
     * {@code delayMillis} between attempts and returns the parsed body as soon as it appears;
     * rethrows once attempts are exhausted.
     */
    public <T> T getWithRetry(String path, TypeReference<T> type, int attempts, long delayMillis)
        throws IOException, IllegalVariableEvaluationException, HttpClientException {
        HttpResponse<String> response;
        var attempt = 1;
        while (true) {
            response = exec("GET", path, null, true);
            if (response.getStatus().getCode() != 404 || attempt >= attempts) {
                break;
            }
            runContext.logger().debug("Resource not yet available at '{}', retrying in {}ms (attempt {}/{})", path, delayMillis, attempt, attempts);
            sleep(delayMillis);
            attempt++;
        }

        if (response.getStatus().getCode() == 404) {
            throw new IllegalStateException(
                "Ceph Dashboard resource still not found after " + attempts + " attempts (~" + (attempts * delayMillis) + "ms) waiting for asynchronous creation: GET " + path
            );
        }

        return parse(response, type);
    }

    private static void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a Ceph Dashboard resource to become available", e);
        }
    }

    public <T> T post(String path, Object body, TypeReference<T> type) throws IOException, IllegalVariableEvaluationException, HttpClientException {
        return parse(exec("POST", path, body, false), type);
    }

    public <T> T put(String path, Object body, TypeReference<T> type) throws IOException, IllegalVariableEvaluationException, HttpClientException {
        return parse(exec("PUT", path, body, false), type);
    }

    /**
     * Deletes the resource at {@code path}. Returns {@code false} instead of throwing when the
     * server responds with HTTP 404, so callers can distinguish "already gone" from a real failure.
     */
    public boolean delete(String path) throws IOException, IllegalVariableEvaluationException, HttpClientException {
        return exec("DELETE", path, null, true).getStatus().getCode() != 404;
    }

    private HttpResponse<String> exec(String method, String path, Object body, boolean allowNotFound)
        throws IOException, IllegalVariableEvaluationException, HttpClientException {
        return CephClient.execute(runContext, method, baseUrl + path, token, skipSsl, body, allowNotFound);
    }

    private <T> T parse(HttpResponse<String> response, TypeReference<T> type) throws IOException {
        if (type == null || response.getBody() == null || response.getBody().isBlank()) {
            return null;
        }
        return CephClient.MAPPER.readValue(response.getBody(), type);
    }
}

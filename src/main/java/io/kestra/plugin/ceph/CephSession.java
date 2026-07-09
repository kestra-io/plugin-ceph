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

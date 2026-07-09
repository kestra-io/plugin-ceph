package io.kestra.plugin.ceph;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.kestra.core.models.property.Property;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Shared WireMock setup for Ceph plugin unit tests. The Ceph Dashboard API only serves HTTPS
 * (self-signed by default), so every test spins up a WireMock instance on a dynamic HTTPS port
 * and connects with {@code skipSsl(true)}, mirroring how a real Ceph deployment is reached.
 */
public final class CephWireMock {

    public static final String DEFAULT_TOKEN = "test-jwt-token";

    private CephWireMock() {
    }

    public static WireMockServer start() {
        var server = new WireMockServer(WireMockConfiguration.options().dynamicPort().dynamicHttpsPort());
        server.start();
        stubAuth(server, DEFAULT_TOKEN);
        return server;
    }

    public static void stubAuth(WireMockServer server, String token) {
        server.stubFor(post(urlEqualTo("/api/auth"))
            .willReturn(okJson("{\"token\": \"" + token + "\"}")));
    }

    public static void stubAuthFailure(WireMockServer server, int status) {
        server.stubFor(post(urlEqualTo("/api/auth"))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(status)));
    }

    /**
     * Applies the standard connection properties (localhost, WireMock's HTTPS port, test
     * credentials, TLS verification disabled) to any task builder extending
     * {@link AbstractCephConnection}.
     */
    public static <T extends AbstractCephConnection.AbstractCephConnectionBuilder<?, ?>> T withConnection(T builder, int httpsPort) {
        builder.host(Property.ofValue("localhost"));
        builder.port(Property.ofValue(httpsPort));
        builder.username(Property.ofValue("admin"));
        builder.password(Property.ofValue("password"));
        builder.skipSsl(Property.ofValue(true));
        return builder;
    }

    public static void verifyAuthHeader(WireMockServer server, String path) {
        server.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo(path))
            .withHeader("Authorization", equalTo("Bearer " + DEFAULT_TOKEN))
            .withHeader("Accept", equalTo("application/vnd.ceph.api.v1.0+json")));
    }

    public static void verifyAuthHeader(WireMockServer server, String path, String token) {
        server.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo(path))
            .withHeader("Authorization", equalTo("Bearer " + token))
            .withHeader("Accept", equalTo("application/vnd.ceph.api.v1.0+json")));
    }

    /**
     * Applies the connection properties for token-based auth (localhost, WireMock's HTTPS port,
     * a pre-obtained JWT, TLS verification disabled), leaving username/password unset.
     */
    public static <T extends AbstractCephConnection.AbstractCephConnectionBuilder<?, ?>> T withTokenConnection(T builder, int httpsPort, String token) {
        builder.host(Property.ofValue("localhost"));
        builder.port(Property.ofValue(httpsPort));
        builder.token(Property.ofValue(token));
        builder.skipSsl(Property.ofValue(true));
        return builder;
    }

    public static void verifyNoAuthCall(WireMockServer server) {
        server.verify(exactly(0), postRequestedFor(urlEqualTo("/api/auth")));
    }
}

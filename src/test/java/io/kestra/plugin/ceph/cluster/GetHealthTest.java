package io.kestra.plugin.ceph.cluster;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.ceph.CephWireMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class GetHealthTest {

    @Inject
    RunContextFactory runContextFactory;

    private WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = CephWireMock.start();
    }

    @AfterEach
    void stopWireMock() {
        wireMock.stop();
    }

    @Test
    void happyPath() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/health/full"))
            .willReturn(okJson("""
                {
                  "client_perf": {}, "df": {}, "fs_map": {}, "hosts": 1,
                  "health": {
                    "status": "HEALTH_WARN",
                    "mutes": [],
                    "checks": [
                      {
                        "severity": "HEALTH_WARN",
                        "summary": {"message": "mon a is low on available space", "count": 1},
                        "detail": [{"message": "mon.a is low on available space"}],
                        "muted": false,
                        "type": "MON_DISK_LOW"
                      }
                    ]
                  },
                  "mgr_map": {}, "mon_status": {}, "osd_map": {}, "pg_info": {}, "pools": [], "rgw": 0
                }
                """)));

        var task = CephWireMock.withConnection(GetHealth.builder().id("getHealth" + System.nanoTime()).type(GetHealth.class.getName()), wireMock.httpsPort())
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.status(), is("HEALTH_WARN"));
        assertThat(output.summary(), hasSize(1));
        assertThat(output.summary(), contains("MON_DISK_LOW: mon a is low on available space"));
        assertThat(output.checks(), instanceOf(java.util.List.class));
    }

    @Test
    void authFailure_throwsWithoutLeakingPassword() {
        CephWireMock.stubAuthFailure(wireMock, 401);

        var secret = "s3cr3t-do-not-leak";
        var task = CephWireMock.withConnection(GetHealth.builder().id("getHealthAuthFail" + System.nanoTime()).type(GetHealth.class.getName()), wireMock.httpsPort())
            .password(Property.ofValue(secret))
            .build();

        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContextFactory.of()));
        assertThat(ex.getMessage().contains(secret), is(false));
        assertThat(ex.getMessage().contains("401"), is(true));
    }

    @Test
    void token_authenticatesWithoutCallingApiAuth() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/health/full"))
            .willReturn(okJson("""
                { "health": { "status": "HEALTH_OK", "checks": [] } }
                """)));

        var token = "pre-obtained-jwt";
        var task = CephWireMock.withTokenConnection(GetHealth.builder().id("getHealthToken" + System.nanoTime()).type(GetHealth.class.getName()), wireMock.httpsPort(), token)
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.status(), is("HEALTH_OK"));
        CephWireMock.verifyAuthHeader(wireMock, "/api/health/full", token);
        CephWireMock.verifyNoAuthCall(wireMock);
    }

    @Test
    void neitherTokenNorPassword_throwsActionableMessage() {
        var task = GetHealth.builder()
            .id("getHealthNoAuth" + System.nanoTime())
            .type(GetHealth.class.getName())
            .host(Property.ofValue("localhost"))
            .port(Property.ofValue(wireMock.httpsPort()))
            .skipSsl(Property.ofValue(true))
            .build();

        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of()));
        assertThat(ex.getMessage(), is("Ceph authentication requires either 'token' or 'password'."));
        CephWireMock.verifyNoAuthCall(wireMock);
    }

    @Test
    void nonTwoXx_throwsWithStatusAndBody() {
        wireMock.stubFor(get(urlEqualTo("/api/health/full"))
            .willReturn(aResponse().withStatus(500).withBody("internal dashboard error")));

        var task = CephWireMock.withConnection(GetHealth.builder().id("getHealth500" + System.nanoTime()).type(GetHealth.class.getName()), wireMock.httpsPort())
            .build();

        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContextFactory.of()));
        assertThat(ex.getMessage().contains("500"), is(true));
        assertThat(ex.getMessage().contains("internal dashboard error"), is(true));
    }
}

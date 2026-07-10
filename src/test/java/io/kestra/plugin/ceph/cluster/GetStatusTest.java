package io.kestra.plugin.ceph.cluster;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.ceph.CephWireMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@KestraTest
class GetStatusTest {

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
    void happyPath_healthy() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/health/minimal"))
            .willReturn(okJson("""
                {"health": {"status": "HEALTH_OK", "mutes": [], "checks": []}}
                """)));

        var task = CephWireMock.withConnection(GetStatus.builder().id("getStatus" + System.nanoTime()).type(GetStatus.class.getName()), wireMock.httpsPort())
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.status(), is("HEALTH_OK"));
        assertThat(output.summary(), empty());
    }

    @Test
    void happyPath_degraded_derivesSummaryFromCheckArray() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/health/minimal"))
            .willReturn(okJson("""
                {
                  "health": {
                    "status": "HEALTH_WARN",
                    "mutes": [],
                    "checks": [
                      {
                        "severity": "HEALTH_WARN",
                        "summary": {"message": "mon is allowing insecure global_id reclaim", "count": 1},
                        "detail": [{"message": "mon.a has auth_allow_insecure_global_id_reclaim set to true"}],
                        "muted": false,
                        "type": "AUTH_INSECURE_GLOBAL_ID_RECLAIM_ALLOWED"
                      },
                      {
                        "severity": "HEALTH_WARN",
                        "summary": {"message": "1 monitors have not enabled msgr2", "count": 1},
                        "detail": [{"message": "mon.a is not bound to a msgr2 port"}],
                        "muted": false,
                        "type": "MON_MSGR2_NOT_ENABLED"
                      }
                    ]
                  }
                }
                """)));

        var task = CephWireMock.withConnection(GetStatus.builder().id("getStatusDegraded" + System.nanoTime()).type(GetStatus.class.getName()), wireMock.httpsPort())
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.status(), is("HEALTH_WARN"));
        assertThat(output.summary(), contains(
            "AUTH_INSECURE_GLOBAL_ID_RECLAIM_ALLOWED: mon is allowing insecure global_id reclaim",
            "MON_MSGR2_NOT_ENABLED: 1 monitors have not enabled msgr2"
        ));
    }
}

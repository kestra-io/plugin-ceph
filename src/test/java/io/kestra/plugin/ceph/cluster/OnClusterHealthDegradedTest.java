package io.kestra.plugin.ceph.cluster;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.kestra.plugin.ceph.CephWireMock.stubAuth;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class OnClusterHealthDegradedTest {

    @Inject
    RunContextFactory runContextFactory;

    private WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort().dynamicHttpsPort());
        wireMock.start();
        stubAuth(wireMock, "test-jwt-token");
    }

    @AfterEach
    void stopWireMock() {
        wireMock.stop();
    }

    // The trigger id is randomized per test so the namespace-KV state key (which includes it)
    // never collides with a previous test run's leftover local storage.
    private OnClusterHealthDegraded defaultTrigger() {
        return OnClusterHealthDegraded.builder()
            .id("clusterDegraded" + System.nanoTime())
            .type(OnClusterHealthDegraded.class.getName())
            .host(Property.ofValue("localhost"))
            .port(Property.ofValue(wireMock.httpsPort()))
            .username(Property.ofValue("admin"))
            .password(Property.ofValue("password"))
            .skipSsl(Property.ofValue(true))
            .interval(Duration.ofMinutes(5))
            .build();
    }

    private void stubHealth(String status) {
        wireMock.stubFor(get(urlEqualTo("/api/health/minimal"))
            .willReturn(okJson("""
                {
                  "health": {
                    "status": "%s",
                    "mutes": [],
                    "checks": [
                      {
                        "severity": "%s",
                        "summary": {"message": "degraded", "count": 1},
                        "detail": [{"message": "cluster is degraded"}],
                        "muted": false,
                        "type": "CHECK"
                      }
                    ]
                  }
                }
                """.formatted(status, status))));
    }

    @Test
    void transitionIntoDegraded_fires() throws Exception {
        stubHealth("HEALTH_WARN");

        var trigger = defaultTrigger();
        var mockEntry = TestsUtils.mockTrigger(runContextFactory, trigger);

        var result = trigger.evaluate(mockEntry.getKey(), mockEntry.getValue());

        assertThat(result.isPresent(), is(true));
        var vars = result.get().getTrigger().getVariables();
        assertThat(vars.get("status"), is("HEALTH_WARN"));
    }

    @Test
    void healthy_doesNotFire() throws Exception {
        stubHealth("HEALTH_OK");

        var trigger = defaultTrigger();
        var mockEntry = TestsUtils.mockTrigger(runContextFactory, trigger);

        var result = trigger.evaluate(mockEntry.getKey(), mockEntry.getValue());

        assertThat(result.isPresent(), is(false));
    }

    @Test
    void stayingDegraded_firesOnlyOnce() throws Exception {
        stubHealth("HEALTH_WARN");

        var trigger = defaultTrigger();
        var mockEntry = TestsUtils.mockTrigger(runContextFactory, trigger);

        var first = trigger.evaluate(mockEntry.getKey(), mockEntry.getValue());
        var second = trigger.evaluate(mockEntry.getKey(), mockEntry.getValue());

        assertThat(first.isPresent(), is(true));
        assertThat(second.isPresent(), is(false));
    }

    @Test
    void recoveringThenDegradingAgain_firesTwice() throws Exception {
        var trigger = defaultTrigger();
        var mockEntry = TestsUtils.mockTrigger(runContextFactory, trigger);

        stubHealth("HEALTH_WARN");
        var first = trigger.evaluate(mockEntry.getKey(), mockEntry.getValue());

        stubHealth("HEALTH_OK");
        var recovered = trigger.evaluate(mockEntry.getKey(), mockEntry.getValue());

        stubHealth("HEALTH_ERR");
        var second = trigger.evaluate(mockEntry.getKey(), mockEntry.getValue());

        assertThat(first.isPresent(), is(true));
        assertThat(recovered.isPresent(), is(false));
        assertThat(second.isPresent(), is(true));
    }
}

package io.kestra.plugin.ceph.pools;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.ceph.CephWireMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class UpdateTest {

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
        wireMock.stubFor(put(urlEqualTo("/api/pool/archive")).willReturn(noContent()));
        wireMock.stubFor(get(urlEqualTo("/api/pool/archive"))
            .willReturn(okJson("""
                {"pool": 3, "pool_name": "archive", "type": "replicated", "size": 3, "pg_num": 64, "pg_placement_num": 64, "application_metadata": []}
                """)));

        var task = CephWireMock.withConnection(Update.builder().id("updatePool" + System.nanoTime()).type(Update.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("archive"))
            .pgNum(Property.ofValue(64))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.pgNum(), is(64));
        wireMock.verify(putRequestedFor(urlEqualTo("/api/pool/archive"))
            .withRequestBody(equalToJson("{\"pg_num\": 64}")));
    }

    @Test
    void noFieldsSet_throws() {
        var task = CephWireMock.withConnection(Update.builder().id("updatePoolEmpty" + System.nanoTime()).type(Update.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("archive"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of()));
    }
}

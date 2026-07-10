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
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class CreateTest {

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
        wireMock.stubFor(post(urlEqualTo("/api/pool")).willReturn(noContent()));
        wireMock.stubFor(get(urlEqualTo("/api/pool/archive"))
            .willReturn(okJson("""
                {"pool": 3, "pool_name": "archive", "type": "replicated", "size": 3, "pg_num": 32, "pg_placement_num": 32, "application_metadata": []}
                """)));

        var task = CephWireMock.withConnection(Create.builder().id("createPool" + System.nanoTime()).type(Create.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("archive"))
            .poolType(Property.ofValue(Create.PoolType.REPLICATED))
            .pgNum(Property.ofValue(32))
            .size(Property.ofValue(3))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.poolName(), is("archive"));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/pool"))
            .withRequestBody(equalToJson("""
                {"pool": "archive", "pool_type": "replicated", "pg_num": 32, "size": 3}
                """)));
    }

    @Test
    void poolNeverAppears_throwsAfterRetriesExhausted() {
        wireMock.stubFor(post(urlEqualTo("/api/pool")).willReturn(noContent()));
        wireMock.stubFor(get(urlEqualTo("/api/pool/archive")).willReturn(notFound()));

        var task = CephWireMock.withConnection(Create.builder().id("createPoolMissing" + System.nanoTime()).type(Create.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("archive"))
            .poolType(Property.ofValue(Create.PoolType.REPLICATED))
            .pgNum(Property.ofValue(32))
            .size(Property.ofValue(3))
            .build();

        assertThrows(IllegalStateException.class, () -> task.run(runContextFactory.of()));
    }
}

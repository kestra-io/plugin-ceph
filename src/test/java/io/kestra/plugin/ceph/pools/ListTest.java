package io.kestra.plugin.ceph.pools;

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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@KestraTest
class ListTest {

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
        wireMock.stubFor(get(urlEqualTo("/api/pool"))
            .willReturn(okJson("""
                [
                  {"pool": 1, "pool_name": "rbd", "type": "replicated", "size": 3, "pg_num": 32, "pg_placement_num": 32, "application_metadata": ["rbd"]},
                  {"pool": 2, "pool_name": "archive", "type": "erasure", "size": 1, "pg_num": 16, "pg_placement_num": 16, "application_metadata": []}
                ]
                """)));

        var task = CephWireMock.withConnection(List.builder().id("listPools" + System.nanoTime()).type(List.class.getName()), wireMock.httpsPort())
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getPools(), hasSize(2));
        assertThat(output.getPools().getFirst().poolName(), is("rbd"));
    }
}

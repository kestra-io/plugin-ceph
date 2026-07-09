package io.kestra.plugin.ceph.rgw;

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
import static org.hamcrest.Matchers.is;

@KestraTest
class ListBucketsTest {

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
        wireMock.stubFor(get(urlEqualTo("/api/rgw/bucket"))
            .willReturn(okJson("[\"backups\", \"logs\"]")));

        var task = CephWireMock.withConnection(ListBuckets.builder().id("listBuckets" + System.nanoTime()).type(ListBuckets.class.getName()), wireMock.httpsPort())
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getBuckets(), contains("backups", "logs"));
    }
}

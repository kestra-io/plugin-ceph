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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class DeleteTest {

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
    void happyPath_deleted() throws Exception {
        wireMock.stubFor(delete(urlEqualTo("/api/pool/archive")).willReturn(noContent()));

        var task = CephWireMock.withConnection(Delete.builder().id("deletePool" + System.nanoTime()).type(Delete.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("archive"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getDeleted(), is(true));
    }

    @Test
    void notFound_treatedAsAlreadyDeleted() throws Exception {
        wireMock.stubFor(delete(urlEqualTo("/api/pool/missing")).willReturn(aResponse().withStatus(404)));

        var task = CephWireMock.withConnection(Delete.builder().id("deletePoolMissing" + System.nanoTime()).type(Delete.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("missing"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getDeleted(), is(false));
    }
}

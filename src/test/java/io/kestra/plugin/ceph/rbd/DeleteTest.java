package io.kestra.plugin.ceph.rbd;

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
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
        wireMock.stubFor(delete(urlEqualTo("/api/block/image/rbd%2Fdata-volume")).willReturn(noContent()));

        var task = CephWireMock.withConnection(Delete.builder().id("deleteRbd" + System.nanoTime()).type(Delete.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("rbd"))
            .imageName(Property.ofValue("data-volume"))
            .build();

        task.run(runContextFactory.of());

        wireMock.verify(deleteRequestedFor(urlEqualTo("/api/block/image/rbd%2Fdata-volume")));
    }

    @Test
    void notFound_treatedAsAlreadyDeleted() {
        wireMock.stubFor(delete(urlEqualTo("/api/block/image/rbd%2Fmissing")).willReturn(aResponse().withStatus(404)));

        var task = CephWireMock.withConnection(Delete.builder().id("deleteRbdMissing" + System.nanoTime()).type(Delete.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("rbd"))
            .imageName(Property.ofValue("missing"))
            .build();

        assertDoesNotThrow(() -> task.run(runContextFactory.of()));
        wireMock.verify(deleteRequestedFor(urlEqualTo("/api/block/image/rbd%2Fmissing")));
    }
}

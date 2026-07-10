package io.kestra.plugin.ceph.rbd.snapshots;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.ceph.CephWireMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@KestraTest
class RollbackTest {

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
        wireMock.stubFor(post(urlEqualTo("/api/block/image/rbd%2Fdata-volume/snap/before-migration/rollback")).willReturn(noContent()));

        var task = CephWireMock.withConnection(Rollback.builder().id("rollback" + System.nanoTime()).type(Rollback.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("rbd"))
            .imageName(Property.ofValue("data-volume"))
            .snapshotName(Property.ofValue("before-migration"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getMessage(), containsString("before-migration"));
        wireMock.verify(postRequestedFor(urlEqualTo("/api/block/image/rbd%2Fdata-volume/snap/before-migration/rollback")));
    }
}

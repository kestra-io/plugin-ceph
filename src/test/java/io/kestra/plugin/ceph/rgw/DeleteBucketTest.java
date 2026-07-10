package io.kestra.plugin.ceph.rgw;

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
class DeleteBucketTest {

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
        wireMock.stubFor(delete(urlEqualTo("/api/rgw/bucket/backups")).willReturn(noContent()));

        var task = CephWireMock.withConnection(DeleteBucket.builder().id("deleteBucket" + System.nanoTime()).type(DeleteBucket.class.getName()), wireMock.httpsPort())
            .bucketName(Property.ofValue("backups"))
            .build();

        task.run(runContextFactory.of());

        wireMock.verify(deleteRequestedFor(urlEqualTo("/api/rgw/bucket/backups")));
    }

    @Test
    void notFound_treatedAsAlreadyDeleted() {
        wireMock.stubFor(delete(urlEqualTo("/api/rgw/bucket/missing")).willReturn(aResponse().withStatus(404)));

        var task = CephWireMock.withConnection(DeleteBucket.builder().id("deleteBucketMissing" + System.nanoTime()).type(DeleteBucket.class.getName()), wireMock.httpsPort())
            .bucketName(Property.ofValue("missing"))
            .build();

        assertDoesNotThrow(() -> task.run(runContextFactory.of()));
        wireMock.verify(deleteRequestedFor(urlEqualTo("/api/rgw/bucket/missing")));
    }
}

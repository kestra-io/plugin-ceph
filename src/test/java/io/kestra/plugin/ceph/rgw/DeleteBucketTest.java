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

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
        wireMock.stubFor(delete(urlEqualTo("/api/rgw/bucket/backups?purge_objects=true")).willReturn(noContent()));

        var task = CephWireMock.withConnection(DeleteBucket.builder().id("deleteBucket" + System.nanoTime()).type(DeleteBucket.class.getName()), wireMock.httpsPort())
            .bucketName(Property.ofValue("backups"))
            .purgeObjects(Property.ofValue(true))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getDeleted(), is(true));
    }
}

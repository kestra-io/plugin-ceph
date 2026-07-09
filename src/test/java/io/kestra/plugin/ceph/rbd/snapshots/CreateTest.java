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

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
        wireMock.stubFor(post(urlEqualTo("/api/block/image/rbd%2Fdata-volume/snap")).willReturn(noContent()));
        wireMock.stubFor(get(urlEqualTo("/api/block/image/rbd%2Fdata-volume"))
            .willReturn(okJson("""
                {"name": "data-volume", "pool_name": "rbd", "snapshots": [
                  {"id": 1, "name": "before-migration", "size": 10737418240, "timestamp": "Mon Jan  1 00:00:00 2024", "is_protected": false}
                ]}
                """)));

        var task = CephWireMock.withConnection(Create.builder().id("createSnap" + System.nanoTime()).type(Create.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("rbd"))
            .imageName(Property.ofValue("data-volume"))
            .snapshotName(Property.ofValue("before-migration"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.name(), is("before-migration"));
        assertThat(output.isProtected(), is(false));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/block/image/rbd%2Fdata-volume/snap"))
            .withRequestBody(equalToJson("{\"snapshot_name\": \"before-migration\"}")));
    }
}

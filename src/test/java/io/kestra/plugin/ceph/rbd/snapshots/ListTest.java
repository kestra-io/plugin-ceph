package io.kestra.plugin.ceph.rbd.snapshots;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.ceph.CephWireMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

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
        wireMock.stubFor(get(urlEqualTo("/api/block/image/rbd%2Fdata-volume"))
            .willReturn(okJson("""
                {"name": "data-volume", "pool_name": "rbd", "snapshots": [
                  {"id": 1, "name": "snap-a", "size": 1024, "timestamp": "t1", "is_protected": false},
                  {"id": 2, "name": "snap-b", "size": 2048, "timestamp": "t2", "is_protected": true}
                ]}
                """)));

        var task = CephWireMock.withConnection(List.builder().id("listSnaps" + System.nanoTime()).type(List.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("rbd"))
            .imageName(Property.ofValue("data-volume"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getSnapshots(), hasSize(2));
        assertThat(output.getSnapshots().getLast().isProtected(), is(true));
    }

    @Test
    void noSnapshots_returnsEmpty() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/block/image/rbd%2Fempty-volume"))
            .willReturn(okJson("""
                {"name": "empty-volume", "pool_name": "rbd"}
                """)));

        var task = CephWireMock.withConnection(List.builder().id("listSnapsEmpty" + System.nanoTime()).type(List.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("rbd"))
            .imageName(Property.ofValue("empty-volume"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getTotal(), is(0));
    }

    @Test
    void store_writesSnapshotsToInternalStorage() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/block/image/rbd%2Fdata-volume"))
            .willReturn(okJson("""
                {"name": "data-volume", "pool_name": "rbd", "snapshots": [
                  {"id": 1, "name": "snap-a", "size": 1024, "timestamp": "t1", "is_protected": false},
                  {"id": 2, "name": "snap-b", "size": 2048, "timestamp": "t2", "is_protected": true}
                ]}
                """)));

        var task = CephWireMock.withConnection(List.builder().id("listSnapsStore" + System.nanoTime()).type(List.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("rbd"))
            .imageName(Property.ofValue("data-volume"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getSnapshots(), is(empty()));
        assertThat(output.getUri(), is(notNullValue()));

        var stored = FileSerde.readAll(
                new InputStreamReader(runContext.storage().getFile(output.getUri()), StandardCharsets.UTF_8),
                SnapshotInfo.class)
            .collectList()
            .block();
        assertThat(stored, hasSize(2));
        assertThat(stored.getLast().isProtected(), is(true));
    }
}

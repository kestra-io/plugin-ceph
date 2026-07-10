package io.kestra.plugin.ceph.rbd.images;

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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
    void happyPath_flattensGroupedResponse() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/block/image?pool_name=rbd"))
            .willReturn(okJson("""
                [
                  {"pool_name": "rbd", "value": [
                    {"name": "data-volume", "size": 10737418240, "obj_size": 4194304},
                    {"name": "logs-volume", "size": 5368709120, "obj_size": 4194304}
                  ]}
                ]
                """)));

        var task = CephWireMock.withConnection(List.builder().id("listRbd" + System.nanoTime()).type(List.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("rbd"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getImages(), hasSize(2));
        assertThat(output.getImages().getFirst().poolName(), is("rbd"));
        assertThat(output.getImages().getFirst().name(), is("data-volume"));
    }

    @Test
    void versionMismatch_retriesWithServerRequiredAcceptHeader() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/block/image"))
            .withHeader("Accept", equalTo("application/vnd.ceph.api.v1.0+json"))
            .willReturn(aResponse().withStatus(415).withBody("API endpoint is '2.0'")));
        wireMock.stubFor(get(urlEqualTo("/api/block/image"))
            .withHeader("Accept", equalTo("application/vnd.ceph.api.v2.0+json"))
            .willReturn(okJson("[]")));

        var task = CephWireMock.withConnection(List.builder().id("listRbdVersion" + System.nanoTime()).type(List.class.getName()), wireMock.httpsPort())
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getTotal(), is(0));
        wireMock.verify(getRequestedFor(urlEqualTo("/api/block/image"))
            .withHeader("Accept", equalTo("application/vnd.ceph.api.v2.0+json")));
    }

    @Test
    void store_writesImagesToInternalStorage() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/block/image?pool_name=rbd"))
            .willReturn(okJson("""
                [
                  {"pool_name": "rbd", "value": [
                    {"name": "data-volume", "size": 10737418240, "obj_size": 4194304},
                    {"name": "logs-volume", "size": 5368709120, "obj_size": 4194304}
                  ]}
                ]
                """)));

        var task = CephWireMock.withConnection(List.builder().id("listRbdStore" + System.nanoTime()).type(List.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("rbd"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getImages(), is(empty()));
        assertThat(output.getUri(), is(notNullValue()));

        var stored = FileSerde.readAll(
                new InputStreamReader(runContext.storage().getFile(output.getUri()), StandardCharsets.UTF_8),
                RbdImageInfo.class)
            .collectList()
            .block();
        assertThat(stored, hasSize(2));
        assertThat(stored.getFirst().name(), is("data-volume"));
    }
}

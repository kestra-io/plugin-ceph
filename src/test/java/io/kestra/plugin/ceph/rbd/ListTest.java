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
}

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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class GetTest {

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
        wireMock.stubFor(get(urlEqualTo("/api/pool/rbd"))
            .willReturn(okJson("""
                {"pool": 1, "pool_name": "rbd", "type": "replicated", "size": 3, "pg_num": 32, "pg_placement_num": 32, "application_metadata": ["rbd"]}
                """)));

        var task = CephWireMock.withConnection(Get.builder().id("getPool" + System.nanoTime()).type(Get.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("rbd"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.poolName(), is("rbd"));
        assertThat(output.size(), is(3));
        assertThat(output.pgNum(), is(32));
    }

    @Test
    void poolNameWithSpecialCharacters_isUrlEncoded() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/pool/my%20pool"))
            .willReturn(okJson("""
                {"pool": 2, "pool_name": "my pool", "type": "replicated", "size": 3, "pg_num": 32, "pg_placement_num": 32, "application_metadata": []}
                """)));

        var task = CephWireMock.withConnection(Get.builder().id("getPoolEncoded" + System.nanoTime()).type(Get.class.getName()), wireMock.httpsPort())
            .poolName(Property.ofValue("my pool"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.poolName(), is("my pool"));
    }
}

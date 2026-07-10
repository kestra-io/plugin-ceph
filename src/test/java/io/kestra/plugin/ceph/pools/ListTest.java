package io.kestra.plugin.ceph.pools;

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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

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

    private void stubTwoPools() {
        wireMock.stubFor(get(urlEqualTo("/api/pool"))
            .willReturn(okJson("""
                [
                  {"pool": 1, "pool_name": "rbd", "type": "replicated", "size": 3, "pg_num": 32, "pg_placement_num": 32, "application_metadata": ["rbd"]},
                  {"pool": 2, "pool_name": "archive", "type": "erasure", "size": 1, "pg_num": 16, "pg_placement_num": 16, "application_metadata": []}
                ]
                """)));
    }

    private List buildTask(FetchType fetchType) {
        var builder = CephWireMock.withConnection(List.builder().id("listPools" + System.nanoTime()).type(List.class.getName()), wireMock.httpsPort());
        if (fetchType != null) {
            builder.fetchType(Property.ofValue(fetchType));
        }
        return builder.build();
    }

    @Test
    void happyPath() throws Exception {
        stubTwoPools();

        var output = buildTask(null).run(runContextFactory.of());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getPools(), hasSize(2));
        assertThat(output.getPools().getFirst().poolName(), is("rbd"));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void fetchOne_returnsFirstPoolOnly() throws Exception {
        stubTwoPools();

        var output = buildTask(FetchType.FETCH_ONE).run(runContextFactory.of());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getPools(), hasSize(1));
        assertThat(output.getPools().getFirst().poolName(), is("rbd"));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void none_returnsCountOnly() throws Exception {
        stubTwoPools();

        var output = buildTask(FetchType.NONE).run(runContextFactory.of());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getPools(), is(empty()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void store_writesPoolsToInternalStorage() throws Exception {
        stubTwoPools();

        var runContext = runContextFactory.of();
        var output = buildTask(FetchType.STORE).run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getPools(), is(empty()));
        assertThat(output.getUri(), is(notNullValue()));

        var stored = FileSerde.readAll(
                new java.io.InputStreamReader(runContext.storage().getFile(output.getUri()), java.nio.charset.StandardCharsets.UTF_8),
                PoolInfo.class)
            .collectList()
            .block();
        assertThat(stored, hasSize(2));
        assertThat(stored.getFirst().poolName(), is("rbd"));
    }
}

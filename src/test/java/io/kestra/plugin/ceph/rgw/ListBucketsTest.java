package io.kestra.plugin.ceph.rgw;

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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
class ListBucketsTest {

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
        wireMock.stubFor(get(urlEqualTo("/api/rgw/bucket"))
            .willReturn(okJson("[\"backups\", \"logs\"]")));

        var task = CephWireMock.withConnection(ListBuckets.builder().id("listBuckets" + System.nanoTime()).type(ListBuckets.class.getName()), wireMock.httpsPort())
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getBuckets(), contains("backups", "logs"));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void store_writesBucketNamesToInternalStorage() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/rgw/bucket"))
            .willReturn(okJson("[\"backups\", \"logs\"]")));

        var task = CephWireMock.withConnection(ListBuckets.builder().id("listBuckets" + System.nanoTime()).type(ListBuckets.class.getName()), wireMock.httpsPort())
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getBuckets(), is(empty()));
        assertThat(output.getUri(), is(notNullValue()));

        var stored = FileSerde.readAll(
                new InputStreamReader(runContext.storage().getFile(output.getUri()), StandardCharsets.UTF_8),
                String.class)
            .collectList()
            .block();
        assertThat(stored, contains("backups", "logs"));
    }
}

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

@KestraTest
class ListUsersTest {

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
        wireMock.stubFor(get(urlEqualTo("/api/rgw/user"))
            .willReturn(okJson("[\"svc-backups\"]")));

        var task = CephWireMock.withConnection(ListUsers.builder().id("listUsers" + System.nanoTime()).type(ListUsers.class.getName()), wireMock.httpsPort())
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getTotal(), is(1));
        assertThat(output.getUsers(), contains("svc-backups"));
    }

    @Test
    void store_writesUserIdsToInternalStorage() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/rgw/user"))
            .willReturn(okJson("[\"svc-backups\", \"svc-logs\"]")));

        var task = CephWireMock.withConnection(ListUsers.builder().id("listUsers" + System.nanoTime()).type(ListUsers.class.getName()), wireMock.httpsPort())
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getUsers(), is(empty()));
        assertThat(output.getUri(), is(notNullValue()));

        var stored = FileSerde.readAll(
                new InputStreamReader(runContext.storage().getFile(output.getUri()), StandardCharsets.UTF_8),
                String.class)
            .collectList()
            .block();
        assertThat(stored, contains("svc-backups", "svc-logs"));
    }
}

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

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class CreateUserTest {

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
        wireMock.stubFor(post(urlEqualTo("/api/rgw/user"))
            .willReturn(okJson("""
                {"user_id": "svc-backups", "display_name": "Backup service account", "email": "backups@company.team"}
                """)));

        var task = CephWireMock.withConnection(CreateUser.builder().id("createUser" + System.nanoTime()).type(CreateUser.class.getName()), wireMock.httpsPort())
            .uid(Property.ofValue("svc-backups"))
            .displayName(Property.ofValue("Backup service account"))
            .email(Property.ofValue("backups@company.team"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.userId(), is("svc-backups"));
        assertThat(output.displayName(), is("Backup service account"));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/rgw/user"))
            .withRequestBody(equalToJson("""
                {"uid": "svc-backups", "display_name": "Backup service account", "email": "backups@company.team"}
                """)));
    }
}

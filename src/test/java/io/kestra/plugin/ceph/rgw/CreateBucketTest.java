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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class CreateBucketTest {

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
        wireMock.stubFor(post(urlEqualTo("/api/rgw/bucket")).willReturn(noContent()));
        wireMock.stubFor(get(urlEqualTo("/api/rgw/bucket/backups"))
            .willReturn(okJson("""
                {"bucket": "backups", "owner": "svc-backups", "id": "abc123"}
                """)));

        var task = CephWireMock.withConnection(CreateBucket.builder().id("createBucket" + System.nanoTime()).type(CreateBucket.class.getName()), wireMock.httpsPort())
            .bucketName(Property.ofValue("backups"))
            .owner(Property.ofValue("svc-backups"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.name(), is("backups"));
        assertThat(output.owner(), is("svc-backups"));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/rgw/bucket"))
            .withRequestBody(equalToJson("{\"bucket\": \"backups\", \"uid\": \"svc-backups\"}")));
    }
}

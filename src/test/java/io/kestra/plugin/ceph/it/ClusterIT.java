package io.kestra.plugin.ceph.it;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.ceph.cluster.GetHealth;
import io.kestra.plugin.ceph.cluster.GetStatus;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Smoke tests against a live Ceph cluster (see {@code .github/setup-unit.sh}). Skipped unless
 * {@code CEPH_IT=true}, which only CI sets: the demo image's OSD does not come up on an arm64 Mac.
 */
@KestraTest
@EnabledIfEnvironmentVariable(named = "CEPH_IT", matches = "true")
class ClusterIT {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void health() throws Exception {
        var output = CephIT.withConnection(GetHealth.builder().id("clusterItHealth" + System.nanoTime()).type(GetHealth.class.getName()))
            .build()
            .run(runContextFactory.of());

        assertThat(output.status(), anyOf(is("HEALTH_OK"), is("HEALTH_WARN"), is("HEALTH_ERR")));
        assertThat(output.summary(), notNullValue());
    }

    @Test
    void status() throws Exception {
        var output = CephIT.withConnection(GetStatus.builder().id("clusterItStatus" + System.nanoTime()).type(GetStatus.class.getName()))
            .build()
            .run(runContextFactory.of());

        assertThat(output.status(), anyOf(is("HEALTH_OK"), is("HEALTH_WARN"), is("HEALTH_ERR")));
    }
}

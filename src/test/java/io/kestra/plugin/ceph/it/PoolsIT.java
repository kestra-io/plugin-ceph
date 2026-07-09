package io.kestra.plugin.ceph.it;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.ceph.pools.Create;
import io.kestra.plugin.ceph.pools.Delete;
import io.kestra.plugin.ceph.pools.Get;
import io.kestra.plugin.ceph.pools.List;
import io.kestra.plugin.ceph.pools.Update;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Pool lifecycle against a live Ceph cluster (see {@code .github/setup-unit.sh}). Skipped unless
 * {@code CEPH_IT=true}. Uses {@code size=2} with min_size 1 (set in the bootstrap) so the pool is active and
 * writable on the single-OSD demo cluster; the Dashboard rejects an explicit size of 1.
 */
@KestraTest
@EnabledIfEnvironmentVariable(named = "CEPH_IT", matches = "true")
class PoolsIT {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void poolLifecycle() throws Exception {
        var runContext = runContextFactory.of();
        var poolName = "it-pool-" + System.nanoTime();

        var created = CephIT.withConnection(Create.builder().id("poolsItCreate" + System.nanoTime()).type(Create.class.getName()))
            .poolName(Property.ofValue(poolName))
            .poolType(Property.ofValue(Create.PoolType.REPLICATED))
            .pgNum(Property.ofValue(8))
            .size(Property.ofValue(2))
            .build()
            .run(runContext);
        assertThat(created.poolName(), is(poolName));

        try {
            var get = CephIT.withConnection(Get.builder().id("poolsItGet" + System.nanoTime()).type(Get.class.getName()))
                .poolName(Property.ofValue(poolName))
                .build()
                .run(runContext);
            assertThat(get.poolName(), is(poolName));
            assertThat(get.size(), is(2));

            CephIT.withConnection(Update.builder().id("poolsItUpdate" + System.nanoTime()).type(Update.class.getName()))
                .poolName(Property.ofValue(poolName))
                .pgNum(Property.ofValue(16))
                .build()
                .run(runContext);

            // pg_num reconciliation (splitting PGs to the new target) happens in the background on
            // the OSDs; the field the Dashboard API returns is the configured target, so this is not
            // expected to race, but it's retried defensively since it's the one property this whole
            // plugin can update in place.
            var updated = Eventually.eventually(
                () -> CephIT.withConnection(Get.builder().id("poolsItUpdateGet" + System.nanoTime()).type(Get.class.getName()))
                    .poolName(Property.ofValue(poolName))
                    .build()
                    .run(runContext),
                pool -> pool.pgNum() != null && pool.pgNum() == 16,
                10,
                1000L
            );
            assertThat(updated.pgNum(), is(16));

            var list = Eventually.eventually(
                () -> CephIT.withConnection(List.builder().id("poolsItList" + System.nanoTime()).type(List.class.getName()))
                    .build()
                    .run(runContext),
                l -> l.getPools().stream().anyMatch(p -> poolName.equals(p.poolName())),
                10,
                1000L
            );
            assertThat(list.getPools().stream().anyMatch(p -> poolName.equals(p.poolName())), is(true));
        } finally {
            var deleted = CephIT.withConnection(Delete.builder().id("poolsItDelete" + System.nanoTime()).type(Delete.class.getName()))
                .poolName(Property.ofValue(poolName))
                .build()
                .run(runContext);
            assertThat(deleted.getDeleted(), is(true));
        }
    }
}

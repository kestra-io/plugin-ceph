package io.kestra.plugin.ceph.it;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.ceph.rbd.images.Create;
import io.kestra.plugin.ceph.rbd.images.Delete;
import io.kestra.plugin.ceph.rbd.images.List;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * RBD image and snapshot lifecycle against a live Ceph cluster (see {@code .github/setup-unit.sh}).
 * Skipped unless {@code CEPH_IT=true}. Self-provisions a dedicated pool tagged for the {@code rbd}
 * application, since Ceph expects RBD images to live in a pool with that application enabled.
 *
 * <p>Class names collide across {@code io.kestra.plugin.ceph.rbd.images} and
 * {@code io.kestra.plugin.ceph.rbd.snapshots} (both have {@code Create}, {@code Delete},
 * {@code List}): the image-level tasks are imported directly and the snapshot-level and pool-level
 * tasks are referenced by fully qualified name to resolve the conflict.
 */
@KestraTest
@EnabledIfEnvironmentVariable(named = "CEPH_IT", matches = "true")
class RbdIT {

    private static final long IMAGE_SIZE = 10L * 1024 * 1024;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void rbdAndSnapshotLifecycle() throws Exception {
        var runContext = runContextFactory.of();
        var poolName = "it-rbd-pool-" + System.nanoTime();
        var imageName = "it-image-" + System.nanoTime();
        var snapshotName = "it-snap-" + System.nanoTime();
        var cloneImageName = "it-clone-" + System.nanoTime();

        CephIT.withConnection(
                io.kestra.plugin.ceph.pools.Create.builder()
                    .id("rbdItPool" + System.nanoTime())
                    .type(io.kestra.plugin.ceph.pools.Create.class.getName())
            )
            .poolName(Property.ofValue(poolName))
            .poolType(Property.ofValue(io.kestra.plugin.ceph.pools.Create.PoolType.REPLICATED))
            .pgNum(Property.ofValue(8))
            .size(Property.ofValue(2))
            .applicationMetadata(Property.ofValue(java.util.List.of("rbd")))
            .build()
            .run(runContext);

        try {
            var image = CephIT.withConnection(Create.builder().id("rbdItCreate" + System.nanoTime()).type(Create.class.getName()))
                .poolName(Property.ofValue(poolName))
                .imageName(Property.ofValue(imageName))
                .size(Property.ofValue(IMAGE_SIZE))
                .build()
                .run(runContext);
            assertThat(image.name(), is(imageName));

            var list = Eventually.eventually(
                () -> CephIT.withConnection(List.builder().id("rbdItList" + System.nanoTime()).type(List.class.getName()))
                    .poolName(Property.ofValue(poolName))
                    .build()
                    .run(runContext),
                l -> l.getImages().stream().anyMatch(i -> imageName.equals(i.name())),
                10,
                1000L
            );
            assertThat(list.getImages().stream().anyMatch(i -> imageName.equals(i.name())), is(true));

            var snapshotCreated = CephIT.withConnection(
                    io.kestra.plugin.ceph.rbd.snapshots.Create.builder()
                        .id("rbdItSnapCreate" + System.nanoTime())
                        .type(io.kestra.plugin.ceph.rbd.snapshots.Create.class.getName())
                )
                .poolName(Property.ofValue(poolName))
                .imageName(Property.ofValue(imageName))
                .snapshotName(Property.ofValue(snapshotName))
                .build()
                .run(runContext);
            assertThat(snapshotCreated.name(), is(snapshotName));

            var snapshotList = CephIT.withConnection(
                    io.kestra.plugin.ceph.rbd.snapshots.List.builder()
                        .id("rbdItSnapList" + System.nanoTime())
                        .type(io.kestra.plugin.ceph.rbd.snapshots.List.class.getName())
                )
                .poolName(Property.ofValue(poolName))
                .imageName(Property.ofValue(imageName))
                .build()
                .run(runContext);
            assertThat(snapshotList.getSnapshots().stream().anyMatch(s -> snapshotName.equals(s.name())), is(true));

            var rollback = CephIT.withConnection(
                    io.kestra.plugin.ceph.rbd.snapshots.Rollback.builder()
                        .id("rbdItRollback" + System.nanoTime())
                        .type(io.kestra.plugin.ceph.rbd.snapshots.Rollback.class.getName())
                )
                .poolName(Property.ofValue(poolName))
                .imageName(Property.ofValue(imageName))
                .snapshotName(Property.ofValue(snapshotName))
                .build()
                .run(runContext);
            assertThat(rollback.getMessage(), containsString(snapshotName));

            try {
                var clone = CephIT.withConnection(
                        io.kestra.plugin.ceph.rbd.snapshots.Clone.builder()
                            .id("rbdItClone" + System.nanoTime())
                            .type(io.kestra.plugin.ceph.rbd.snapshots.Clone.class.getName())
                    )
                    .poolName(Property.ofValue(poolName))
                    .imageName(Property.ofValue(imageName))
                    .snapshotName(Property.ofValue(snapshotName))
                    .childPoolName(Property.ofValue(poolName))
                    .childImageName(Property.ofValue(cloneImageName))
                    .build()
                    .run(runContext);
                assertThat(clone.name(), is(cloneImageName));
            } finally {
                CephIT.withConnection(Delete.builder().id("rbdItCloneDelete" + System.nanoTime()).type(Delete.class.getName()))
                    .poolName(Property.ofValue(poolName))
                    .imageName(Property.ofValue(cloneImageName))
                    .build()
                    .run(runContext);
            }

            CephIT.withConnection(
                    io.kestra.plugin.ceph.rbd.snapshots.Delete.builder()
                        .id("rbdItSnapDelete" + System.nanoTime())
                        .type(io.kestra.plugin.ceph.rbd.snapshots.Delete.class.getName())
                )
                .poolName(Property.ofValue(poolName))
                .imageName(Property.ofValue(imageName))
                .snapshotName(Property.ofValue(snapshotName))
                .build()
                .run(runContext);

            CephIT.withConnection(Delete.builder().id("rbdItImageDelete" + System.nanoTime()).type(Delete.class.getName()))
                .poolName(Property.ofValue(poolName))
                .imageName(Property.ofValue(imageName))
                .build()
                .run(runContext);
        } finally {
            CephIT.withConnection(
                    io.kestra.plugin.ceph.pools.Delete.builder()
                        .id("rbdItPoolDelete" + System.nanoTime())
                        .type(io.kestra.plugin.ceph.pools.Delete.class.getName())
                )
                .poolName(Property.ofValue(poolName))
                .build()
                .run(runContext);
        }
    }
}

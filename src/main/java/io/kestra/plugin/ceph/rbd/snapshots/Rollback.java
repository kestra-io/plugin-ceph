package io.kestra.plugin.ceph.rbd.snapshots;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ceph.AbstractCephConnection;
import io.kestra.plugin.ceph.CephClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Roll back an RBD image to a snapshot",
    description = "Calls `POST /api/block/image/{image_spec}/snap/{snapshot_name}/rollback`, reverting the image's data to the snapshot's state. Destructive: any writes made to the image after the snapshot was taken are discarded."
)
@Plugin(
    examples = {
        @Example(
            title = "Roll back an RBD image to a previous snapshot",
            full = true,
            code = """
                id: rollback_rbd_image
                namespace: company.team

                tasks:
                  - id: rollback
                    type: io.kestra.plugin.ceph.rbd.snapshots.Rollback
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    poolName: "rbd"
                    imageName: "data-volume"
                    snapshotName: "before-migration"
                """
        )
    }
)
public class Rollback extends AbstractCephConnection implements RunnableTask<Rollback.Output> {

    @Schema(
        title = "Pool name",
        description = "Pool the image belongs to."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Schema(
        title = "Image name",
        description = "Name of the RBD image to roll back."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> imageName;

    @Schema(
        title = "Snapshot name",
        description = "Name of the snapshot to roll back to."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> snapshotName;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            var rPoolName = runContext.render(poolName).as(String.class).orElseThrow(() -> new IllegalArgumentException("poolName is required"));
            var rImageName = runContext.render(imageName).as(String.class).orElseThrow(() -> new IllegalArgumentException("imageName is required"));
            var rSnapshotName = runContext.render(snapshotName).as(String.class).orElseThrow(() -> new IllegalArgumentException("snapshotName is required"));

            var spec = CephClient.imageSpec(rPoolName, rImageName);

            logger.info("Rolling back RBD image '{}/{}' to snapshot '{}'", rPoolName, rImageName, rSnapshotName);
            session.post("/block/image/" + spec + "/snap/" + CephClient.pathSegment(rSnapshotName) + "/rollback", null, null);

            return Output.builder()
                .message("Image '" + rPoolName + "/" + rImageName + "' rolled back to snapshot '" + rSnapshotName + "'.")
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Message",
            description = "Human-readable confirmation of the rollback."
        )
        private final String message;
    }
}

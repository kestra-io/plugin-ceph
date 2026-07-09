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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create an RBD snapshot",
    description = """
        Calls `POST /api/block/image/{image_spec}/snap` and returns the resulting snapshot. Some \
        Ceph versions process snapshot creation asynchronously via the Dashboard task manager, so \
        the new snapshot may briefly be missing from the parent image's snapshot list right after \
        the POST; this task retries the lookup for up to ~10 seconds (10 attempts, 1s apart).
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Snapshot an RBD image",
            full = true,
            code = """
                id: create_rbd_snapshot
                namespace: company.team

                tasks:
                  - id: snapshot
                    type: io.kestra.plugin.ceph.rbd.snapshots.Create
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
public class Create extends AbstractCephConnection implements RunnableTask<SnapshotInfo> {

    @Schema(title = "Pool name", description = "Pool the image belongs to.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Schema(title = "Image name", description = "Name of the RBD image to snapshot.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> imageName;

    @Schema(title = "Snapshot name", description = "Name to give the new snapshot.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> snapshotName;

    @Override
    public SnapshotInfo run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var session = connect(runContext);

        var rPoolName = runContext.render(poolName).as(String.class).orElseThrow(() -> new IllegalArgumentException("poolName is required"));
        var rImageName = runContext.render(imageName).as(String.class).orElseThrow(() -> new IllegalArgumentException("imageName is required"));
        var rSnapshotName = runContext.render(snapshotName).as(String.class).orElseThrow(() -> new IllegalArgumentException("snapshotName is required"));

        var spec = CephClient.imageSpec(rPoolName, rImageName);

        logger.info("Creating snapshot '{}' of RBD image '{}/{}'", rSnapshotName, rPoolName, rImageName);
        session.post("/block/image/" + spec + "/snap", Map.of("snapshot_name", rSnapshotName), null);

        return SnapshotSupport.findWithRetry(session, runContext, spec, rSnapshotName);
    }
}

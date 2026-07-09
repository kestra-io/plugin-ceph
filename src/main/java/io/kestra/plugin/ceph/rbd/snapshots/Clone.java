package io.kestra.plugin.ceph.rbd.snapshots;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ceph.AbstractCephConnection;
import io.kestra.plugin.ceph.CephClient;
import io.kestra.plugin.ceph.rbd.RbdImageInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Clone an RBD snapshot into a new image",
    description = """
        Calls `POST /api/block/image/{image_spec}/snap/{snapshot_name}/clone`, creating a new \
        copy-on-write image backed by the snapshot. Some Ceph versions process this asynchronously \
        via the Dashboard task manager; this task issues the request and immediately fetches the \
        resulting child image, which may briefly reflect an in-progress state until Ceph finishes cloning it.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Clone an RBD snapshot into a new image",
            full = true,
            code = """
                id: clone_rbd_snapshot
                namespace: company.team

                tasks:
                  - id: clone
                    type: io.kestra.plugin.ceph.rbd.snapshots.Clone
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    poolName: "rbd"
                    imageName: "data-volume"
                    snapshotName: "before-migration"
                    childPoolName: "rbd"
                    childImageName: "data-volume-clone"
                """
        )
    }
)
public class Clone extends AbstractCephConnection implements RunnableTask<RbdImageInfo> {

    @Schema(title = "Pool name", description = "Pool of the source image holding the snapshot.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Schema(title = "Image name", description = "Name of the source RBD image holding the snapshot.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> imageName;

    @Schema(title = "Snapshot name", description = "Name of the snapshot to clone from.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> snapshotName;

    @Schema(title = "Destination pool name", description = "Pool the new cloned image is created in.")
    @NotNull
    @PluginProperty(group = "destination")
    private Property<String> childPoolName;

    @Schema(title = "Destination image name", description = "Name of the new cloned image.")
    @NotNull
    @PluginProperty(group = "destination")
    private Property<String> childImageName;

    @Override
    public RbdImageInfo run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var session = connect(runContext);

        var rPoolName = runContext.render(poolName).as(String.class).orElseThrow(() -> new IllegalArgumentException("poolName is required"));
        var rImageName = runContext.render(imageName).as(String.class).orElseThrow(() -> new IllegalArgumentException("imageName is required"));
        var rSnapshotName = runContext.render(snapshotName).as(String.class).orElseThrow(() -> new IllegalArgumentException("snapshotName is required"));
        var rChildPoolName = runContext.render(childPoolName).as(String.class).orElseThrow(() -> new IllegalArgumentException("childPoolName is required"));
        var rChildImageName = runContext.render(childImageName).as(String.class).orElseThrow(() -> new IllegalArgumentException("childImageName is required"));

        var spec = CephClient.imageSpec(rPoolName, rImageName);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("child_pool_name", rChildPoolName);
        body.put("child_image_name", rChildImageName);

        logger.info(
            "Cloning snapshot '{}' of RBD image '{}/{}' into '{}/{}'",
            rSnapshotName, rPoolName, rImageName, rChildPoolName, rChildImageName
        );
        session.post("/block/image/" + spec + "/snap/" + rSnapshotName + "/clone", body, null);

        var childSpec = CephClient.imageSpec(rChildPoolName, rChildImageName);
        return session.get("/block/image/" + childSpec, new com.fasterxml.jackson.core.type.TypeReference<RbdImageInfo>() {
        }).withPoolName(rChildPoolName);
    }
}

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
    title = "List RBD snapshots",
    description = """
        The Ceph Dashboard API has no dedicated snapshot-listing endpoint: this task calls \
        `GET /api/block/image/{image_spec}` and returns the `snapshots` array embedded in the image detail.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List snapshots of an RBD image",
            full = true,
            code = """
                id: list_rbd_snapshots
                namespace: company.team

                tasks:
                  - id: snapshots
                    type: io.kestra.plugin.ceph.rbd.snapshots.List
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    poolName: "rbd"
                    imageName: "data-volume"
                """
        )
    }
)
public class List extends AbstractCephConnection implements RunnableTask<List.Output> {

    @Schema(title = "Pool name", description = "Pool the image belongs to.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Schema(title = "Image name", description = "Name of the RBD image whose snapshots are listed.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> imageName;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var session = connect(runContext);

        var rPoolName = runContext.render(poolName).as(String.class).orElseThrow(() -> new IllegalArgumentException("poolName is required"));
        var rImageName = runContext.render(imageName).as(String.class).orElseThrow(() -> new IllegalArgumentException("imageName is required"));

        logger.info("Listing snapshots of RBD image '{}/{}'", rPoolName, rImageName);
        var snapshots = SnapshotSupport.fetchSnapshots(session, CephClient.imageSpec(rPoolName, rImageName));

        return Output.builder()
            .total(snapshots.size())
            .snapshots(snapshots)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Total", description = "Number of snapshots returned.")
        private final Integer total;

        @Schema(title = "Snapshots", description = "Summary of each snapshot on the image.")
        private final java.util.List<SnapshotInfo> snapshots;
    }
}

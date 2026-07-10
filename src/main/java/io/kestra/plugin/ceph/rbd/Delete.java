package io.kestra.plugin.ceph.rbd;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete an RBD image",
    description = "Calls `DELETE /api/block/image/{image_spec}`. An image that no longer exists is treated as already deleted rather than an error."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an RBD image",
            full = true,
            code = """
                id: delete_rbd_image
                namespace: company.team

                tasks:
                  - id: delete_image
                    type: io.kestra.plugin.ceph.rbd.Delete
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    poolName: "rbd"
                    imageName: "data-volume"
                """
        )
    }
)
public class Delete extends AbstractCephConnection implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Pool name",
        description = "Pool the image belongs to."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Schema(
        title = "Image name",
        description = "Name of the RBD image to delete."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> imageName;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            var rPoolName = runContext.render(poolName).as(String.class).orElseThrow(() -> new IllegalArgumentException("poolName is required"));
            var rImageName = runContext.render(imageName).as(String.class).orElseThrow(() -> new IllegalArgumentException("imageName is required"));

            logger.info("Deleting RBD image '{}/{}'", rPoolName, rImageName);
            session.delete("/block/image/" + CephClient.imageSpec(rPoolName, rImageName));

            return null;
        }
    }
}

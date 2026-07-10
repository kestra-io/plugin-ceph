package io.kestra.plugin.ceph.rbd.images;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ceph.AbstractCephConnection;
import io.kestra.plugin.ceph.CephClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
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
    title = "Create an RBD image",
    description = """
        Calls `POST /api/block/image`. Some Ceph versions process image creation asynchronously via \
        the Dashboard task manager, so the follow-up fetch of the resulting image may briefly 404 \
        right after the POST; this task retries it for up to ~10 seconds (10 attempts, 1s apart) \
        before failing.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Create a 10 GiB RBD image",
            full = true,
            code = """
                id: create_rbd_image
                namespace: company.team

                tasks:
                  - id: create_image
                    type: io.kestra.plugin.ceph.rbd.images.Create
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    poolName: "rbd"
                    imageName: "data-volume"
                    size: 10737418240
                """
        )
    }
)
public class Create extends AbstractCephConnection implements RunnableTask<RbdImageInfo> {

    @Schema(
        title = "Pool name",
        description = "Pool the image is created in."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Schema(
        title = "Image name",
        description = "Name of the RBD image to create."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> imageName;

    @Schema(
        title = "Size",
        description = "Image size in bytes, e.g. `10737418240` for 10 GiB."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<@Min(1) Long> size;

    @Override
    public RbdImageInfo run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            var rPoolName = runContext.render(poolName).as(String.class).orElseThrow(() -> new IllegalArgumentException("poolName is required"));
            var rImageName = runContext.render(imageName).as(String.class).orElseThrow(() -> new IllegalArgumentException("imageName is required"));
            var rSize = runContext.render(size).as(Long.class).orElseThrow(() -> new IllegalArgumentException("size is required"));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pool_name", rPoolName);
            body.put("name", rImageName);
            body.put("size", rSize);

            logger.info("Creating RBD image '{}/{}' ({} bytes)", rPoolName, rImageName, rSize);
            session.post("/block/image", body, null);

            var spec = CephClient.imageSpec(rPoolName, rImageName);
            return session.getWithRetry("/block/image/" + spec, new TypeReference<RbdImageInfo>() {
            }).withPoolName(rPoolName);
        }
    }
}

package io.kestra.plugin.ceph.rbd;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ceph.AbstractCephConnection;
import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "List RBD images",
    description = "Calls `GET /api/block/image`, optionally scoped to a single pool, and flattens the pool-grouped response into a single list."
)
@Plugin(
    examples = {
        @Example(
            title = "List RBD images in a pool",
            full = true,
            code = """
                id: list_rbd_images
                namespace: company.team

                tasks:
                  - id: images
                    type: io.kestra.plugin.ceph.rbd.List
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    poolName: "rbd"
                """
        )
    }
)
public class List extends AbstractCephConnection implements RunnableTask<List.Output> {

    @Schema(title = "Pool name", description = "Restrict the listing to this pool. When omitted, images from every pool are returned.")
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var session = connect(runContext);

        var rPoolName = poolName != null ? runContext.render(poolName).as(String.class).orElse(null) : null;
        var path = rPoolName != null ? "/block/image?pool_name=" + rPoolName : "/block/image";

        logger.info("Listing RBD images{}", rPoolName != null ? " in pool '" + rPoolName + "'" : "");
        java.util.List<PoolImageGroup> groups = session.get(path, new TypeReference<>() {
        });

        var images = groups.stream()
            .flatMap(group -> group.value().stream().map(image -> image.withPoolName(group.poolName())))
            .toList();

        return Output.builder()
            .total(images.size())
            .images(images)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Total", description = "Number of images returned.")
        private final Integer total;

        @Schema(title = "Images", description = "Summary of each RBD image.")
        private final java.util.List<RbdImageInfo> images;
    }
}

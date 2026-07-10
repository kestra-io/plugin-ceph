package io.kestra.plugin.ceph.rbd.images;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ceph.AbstractCephConnection;
import io.kestra.plugin.ceph.CephClient;
import io.kestra.plugin.ceph.CephFetch;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;

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
                    type: io.kestra.plugin.ceph.rbd.images.List
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    poolName: "rbd"
                """
        )
    }
)
public class List extends AbstractCephConnection implements RunnableTask<List.Output> {

    @Schema(
        title = "Pool name",
        description = "Restrict the listing to this pool. When omitted, images from every pool are returned."
    )
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Schema(
        title = "How results are returned",
        description = "`FETCH` (default) returns the images inline under `images`, `STORE` writes them to Kestra " +
            "internal storage as Ion and returns a `uri`, `FETCH_ONE` returns only the first image, `NONE` returns " +
            "just the count. Use `STORE` on clusters with a large number of images."
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            var rPoolName = poolName != null ? runContext.render(poolName).as(String.class).orElse(null) : null;
            var rFetchType = runContext.render(fetchType).as(FetchType.class).orElse(FetchType.FETCH);
            var path = rPoolName != null ? "/block/image?pool_name=" + CephClient.pathSegment(rPoolName) : "/block/image";

            logger.info("Listing RBD images{}", rPoolName != null ? " in pool '" + rPoolName + "'" : "");
            java.util.List<PoolImageGroup> groups = session.get(path, new TypeReference<>() {
            });

            var images = groups.stream()
                .flatMap(group -> group.value().stream().map(image -> image.withPoolName(group.poolName())))
                .toList();

            var output = Output.builder().total(images.size());
            CephFetch.apply(runContext, rFetchType, images, output::images, output::uri);
            return output.build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Total",
            description = "Number of images returned."
        )
        private final Integer total;

        @Schema(
            title = "Images",
            description = "Summary of each RBD image. Empty when `fetchType` is `STORE` or `NONE`."
        )
        private final java.util.List<RbdImageInfo> images;

        @Schema(
            title = "URI",
            description = "Storage URI of the Ion-serialized images. Set only when `fetchType` is `STORE`."
        )
        private final URI uri;
    }

    /**
     * {@code GET /api/block/image} groups images by pool: one entry per pool, each carrying the list
     * of images in it. This mirrors that shape so the task can flatten it into a single list.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PoolImageGroup(
        @JsonProperty("pool_name") String poolName,
        java.util.List<RbdImageInfo> value
    ) {
    }
}

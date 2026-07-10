package io.kestra.plugin.ceph.pools;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ceph.AbstractCephConnection;
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
    title = "List Ceph pools",
    description = "Calls `GET /api/pool` and returns a summary of every pool in the cluster."
)
@Plugin(
    examples = {
        @Example(
            title = "List all Ceph pools",
            full = true,
            code = """
                id: list_ceph_pools
                namespace: company.team

                tasks:
                  - id: pools
                    type: io.kestra.plugin.ceph.pools.List
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                """
        )
    }
)
public class List extends AbstractCephConnection implements RunnableTask<List.Output> {

    @Schema(
        title = "How results are returned",
        description = "`FETCH` (default) returns the pools inline under `pools`, `STORE` writes them to Kestra " +
            "internal storage as Ion and returns a `uri`, `FETCH_ONE` returns only the first pool, `NONE` returns " +
            "just the count. Use `STORE` on clusters with a large number of pools."
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            var rFetchType = runContext.render(fetchType).as(FetchType.class).orElse(FetchType.FETCH);

            logger.info("Listing Ceph pools");
            java.util.List<PoolInfo> pools = session.get("/pool", new TypeReference<>() {
            });

            var output = Output.builder().total(pools.size());
            CephFetch.apply(runContext, rFetchType, pools, output::pools, output::uri);
            return output.build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Total",
            description = "Number of pools returned."
        )
        private final Integer total;

        @Schema(
            title = "Pools",
            description = "Summary of each pool in the cluster. Empty when `fetchType` is `STORE` or `NONE`."
        )
        private final java.util.List<PoolInfo> pools;

        @Schema(
            title = "URI",
            description = "Storage URI of the Ion-serialized pools. Set only when `fetchType` is `STORE`."
        )
        private final URI uri;
    }
}

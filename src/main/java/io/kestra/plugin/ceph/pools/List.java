package io.kestra.plugin.ceph.pools;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
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

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var session = connect(runContext);

        logger.info("Listing Ceph pools");
        java.util.List<PoolInfo> pools = session.get("/pool", new TypeReference<>() {
        });

        return Output.builder()
            .total(pools.size())
            .pools(pools)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Total", description = "Number of pools returned.")
        private final Integer total;

        @Schema(title = "Pools", description = "Summary of each pool in the cluster.")
        private final java.util.List<PoolInfo> pools;
    }
}

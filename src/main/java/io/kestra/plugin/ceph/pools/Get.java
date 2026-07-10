package io.kestra.plugin.ceph.pools;

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
    title = "Get a Ceph pool",
    description = "Calls `GET /api/pool/{pool_name}` and returns its details."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch details of a Ceph pool",
            full = true,
            code = """
                id: get_ceph_pool
                namespace: company.team

                tasks:
                  - id: pool
                    type: io.kestra.plugin.ceph.pools.Get
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    poolName: "rbd"
                """
        )
    }
)
public class Get extends AbstractCephConnection implements RunnableTask<PoolInfo> {

    @Schema(title = "Pool name", description = "Name of the Ceph pool to fetch.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Override
    public PoolInfo run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            var rPoolName = runContext.render(poolName).as(String.class).orElseThrow(() -> new IllegalArgumentException("poolName is required"));

            logger.info("Fetching Ceph pool '{}'", rPoolName);
            return session.get("/pool/" + CephClient.pathSegment(rPoolName), new TypeReference<PoolInfo>() {
            });
        }
    }
}

package io.kestra.plugin.ceph.pools;

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
    title = "Delete a Ceph pool",
    description = "Calls `DELETE /api/pool/{pool_name}`. A pool that no longer exists is treated as already deleted rather than an error."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a Ceph pool",
            full = true,
            code = """
                id: delete_ceph_pool
                namespace: company.team

                tasks:
                  - id: delete_pool
                    type: io.kestra.plugin.ceph.pools.Delete
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    poolName: "archive"
                """
        )
    }
)
public class Delete extends AbstractCephConnection implements RunnableTask<Delete.Output> {

    @Schema(title = "Pool name", description = "Name of the pool to delete.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var session = connect(runContext);

        var rPoolName = runContext.render(poolName).as(String.class).orElseThrow(() -> new IllegalArgumentException("poolName is required"));

        logger.info("Deleting Ceph pool '{}'", rPoolName);
        var deleted = session.delete("/pool/" + CephClient.pathSegment(rPoolName));

        return Output.builder()
            .deleted(deleted)
            .message(deleted ? "Pool '" + rPoolName + "' deleted." : "Pool '" + rPoolName + "' did not exist.")
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Deleted", description = "`true` if the pool existed and was deleted, `false` if it was already absent.")
        private final Boolean deleted;

        @Schema(title = "Message", description = "Human-readable outcome of the deletion.")
        private final String message;
    }
}

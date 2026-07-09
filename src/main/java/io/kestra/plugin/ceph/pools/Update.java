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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Update a Ceph pool",
    description = """
        Calls `PUT /api/pool/{pool_name}` with only the fields explicitly set, then returns the \
        updated pool. Some Ceph versions process pool updates asynchronously via the Dashboard task \
        manager, so the follow-up fetch may briefly 404 right after the PUT; this task retries it \
        for up to ~10 seconds (10 attempts, 1s apart) before failing.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Grow a Ceph pool's placement group count",
            full = true,
            code = """
                id: update_ceph_pool
                namespace: company.team

                tasks:
                  - id: update_pool
                    type: io.kestra.plugin.ceph.pools.Update
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    poolName: "archive"
                    pgNum: 64
                """
        )
    }
)
public class Update extends AbstractCephConnection implements RunnableTask<PoolInfo> {

    @Schema(title = "Pool name", description = "Name of the pool to update.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Schema(title = "Replica size", description = "New number of data replicas. Only sent if set.")
    @Min(1)
    @Max(10)
    @PluginProperty(group = "main")
    private Property<Integer> size;

    @Schema(title = "Placement group count", description = "New number of placement groups. Only sent if set.")
    @Min(1)
    @Max(65536)
    @PluginProperty(group = "main")
    private Property<Integer> pgNum;

    @Schema(title = "Application metadata", description = "Application tags to set on the pool. Only sent if set.")
    @PluginProperty(group = "advanced")
    private Property<List<String>> applicationMetadata;

    @Override
    public PoolInfo run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var session = connect(runContext);

        var rPoolName = runContext.render(poolName).as(String.class).orElseThrow(() -> new IllegalArgumentException("poolName is required"));

        Map<String, Object> body = new LinkedHashMap<>();
        if (size != null) {
            runContext.render(size).as(Integer.class).ifPresent(v -> body.put("size", v));
        }
        if (pgNum != null) {
            runContext.render(pgNum).as(Integer.class).ifPresent(v -> body.put("pg_num", v));
        }
        if (applicationMetadata != null) {
            var rApplicationMetadata = runContext.render(applicationMetadata).asList(String.class);
            if (!rApplicationMetadata.isEmpty()) {
                body.put("application_metadata", rApplicationMetadata);
            }
        }

        if (body.isEmpty()) {
            throw new IllegalArgumentException("At least one of size, pgNum, or applicationMetadata must be set to update a pool.");
        }

        logger.info("Updating Ceph pool '{}' with {}", rPoolName, body.keySet());
        var pathSegment = CephClient.pathSegment(rPoolName);
        session.put("/pool/" + pathSegment, body, null);

        return session.getWithRetry("/pool/" + pathSegment, new TypeReference<PoolInfo>() {
        });
    }
}

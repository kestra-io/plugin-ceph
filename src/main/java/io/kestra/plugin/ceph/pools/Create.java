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
import lombok.Builder;
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
    title = "Create a Ceph pool",
    description = """
        Calls `POST /api/pool`. Some Ceph versions process pool creation asynchronously via the \
        Dashboard task manager, so the follow-up fetch of the resulting pool may briefly 404 right \
        after the POST; this task retries it for up to ~10 seconds (10 attempts, 1s apart) before \
        failing.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Create a replicated Ceph pool",
            full = true,
            code = """
                id: create_ceph_pool
                namespace: company.team

                tasks:
                  - id: create_pool
                    type: io.kestra.plugin.ceph.pools.Create
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    poolName: "archive"
                    poolType: REPLICATED
                    size: 3
                    pgNum: 32
                """
        )
    }
)
public class Create extends AbstractCephConnection implements RunnableTask<PoolInfo> {

    @Schema(title = "Pool name", description = "Name of the pool to create.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> poolName;

    @Schema(title = "Pool type", description = "Replication strategy. Defaults to `REPLICATED`.")
    @NotNull
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<PoolType> poolType = Property.ofValue(PoolType.REPLICATED);

    @Schema(
        title = "Placement group count",
        description = "Number of placement groups for the pool. Must be a positive power of two for optimal distribution. Defaults to `32`."
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<@Min(1) @Max(65536) Integer> pgNum = Property.ofValue(32);

    @Schema(
        title = "Replica size",
        description = "Number of data replicas for a `REPLICATED` pool. Ignored for `ERASURE` pools. Defaults to `3`."
    )
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<@Min(1) @Max(10) Integer> size = Property.ofValue(3);

    @Schema(
        title = "Application metadata",
        description = "Application tags to enable on the pool, e.g. `[\"rbd\"]` or `[\"rgw\"]`."
    )
    @PluginProperty(group = "advanced")
    private Property<List<String>> applicationMetadata;

    @Override
    public PoolInfo run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            var rPoolName = runContext.render(poolName).as(String.class).orElseThrow(() -> new IllegalArgumentException("poolName is required"));
            var rPoolType = runContext.render(poolType).as(PoolType.class).orElse(PoolType.REPLICATED);
            var rPgNum = runContext.render(pgNum).as(Integer.class).orElse(32);
            var rSize = runContext.render(size).as(Integer.class).orElse(3);
            var rApplicationMetadata = applicationMetadata != null ? runContext.render(applicationMetadata).asList(String.class) : List.<String>of();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pool", rPoolName);
            body.put("pool_type", rPoolType.name().toLowerCase());
            body.put("pg_num", rPgNum);
            if (rPoolType == PoolType.REPLICATED) {
                body.put("size", rSize);
            }
            if (!rApplicationMetadata.isEmpty()) {
                body.put("application_metadata", rApplicationMetadata);
            }

            logger.info("Creating Ceph pool '{}' (type={}, pgNum={})", rPoolName, rPoolType, rPgNum);
            session.post("/pool", body, null);

            return session.getWithRetry("/pool/" + CephClient.pathSegment(rPoolName), new TypeReference<PoolInfo>() {
            });
        }
    }

    public enum PoolType {
        REPLICATED,
        ERASURE
    }
}

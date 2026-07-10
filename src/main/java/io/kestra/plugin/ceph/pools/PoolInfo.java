package io.kestra.plugin.ceph.pools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Curated view of a Ceph pool as returned by {@code GET /api/pool} and {@code GET /api/pool/{pool_name}}.
 * The Ceph Dashboard API returns dozens of additional internal fields (flags, crush rule details, PG
 * autoscale state, ...); only the attributes useful for orchestration are surfaced here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PoolInfo(
    @Schema(
        title = "Pool ID",
        description = "Numeric identifier Ceph assigned to the pool."
    )
    @JsonProperty("pool") Integer poolId,

    @Schema(
        title = "Pool name",
        description = "Name of the pool."
    )
    @JsonProperty("pool_name") String poolName,

    @Schema(
        title = "Pool type",
        description = "Replication strategy: `replicated` or `erasure`."
    )
    @JsonProperty("type") String poolType,

    @Schema(
        title = "Replica size",
        description = "Number of data replicas for a replicated pool."
    )
    Integer size,

    @Schema(
        title = "Placement group count",
        description = "Number of placement groups."
    )
    @JsonProperty("pg_num") Integer pgNum,

    @Schema(
        title = "Placement group placement count",
        description = "Number of placement groups used for placement."
    )
    @JsonProperty("pg_placement_num") Integer pgpNum,

    @Schema(
        title = "Application metadata",
        description = "Application tags enabled on the pool, e.g. `rbd` or `rgw`."
    )
    @JsonProperty("application_metadata") List<String> applicationMetadata
) implements io.kestra.core.models.tasks.Output {
}

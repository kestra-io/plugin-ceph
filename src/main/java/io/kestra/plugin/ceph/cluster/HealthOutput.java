package io.kestra.plugin.ceph.cluster;

import io.kestra.plugin.ceph.CephClient;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Output shape shared by the cluster health tasks ({@link GetHealth}, {@link GetStatus}) and the
 * {@link HealthTrigger}: the cluster status, a derived human-readable summary, and the raw per-check
 * details as returned by the Ceph Dashboard API.
 */
public record HealthOutput(
    @Schema(
        title = "Cluster status",
        description = "Overall Ceph cluster status: `HEALTH_OK`, `HEALTH_WARN`, or `HEALTH_ERR`."
    )
    String status,

    @Schema(
        title = "Summary",
        description = "Human-readable messages derived from each active health check."
    )
    List<String> summary,

    @Schema(
        title = "Checks",
        description = "Raw per-check details as returned by the Ceph Dashboard API: a list of check objects."
    )
    Object checks
) implements io.kestra.core.models.tasks.Output {

    static HealthOutput from(CephClient.CephHealth health) {
        return new HealthOutput(health.status(), health.summary(), health.checks());
    }
}

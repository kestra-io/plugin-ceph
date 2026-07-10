package io.kestra.plugin.ceph.cluster;

import io.kestra.plugin.ceph.CephClient;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Output shape shared by the cluster health tasks ({@link GetHealth}, {@link GetStatus}) and the
 * {@link HealthTrigger}: the cluster status, a derived human-readable summary, and the raw per-check
 * details as returned by the Ceph Dashboard API.
 */
@Builder
@Getter
public class HealthOutput implements io.kestra.core.models.tasks.Output {

    @Schema(
        title = "Cluster status",
        description = "Overall Ceph cluster status: `HEALTH_OK`, `HEALTH_WARN`, or `HEALTH_ERR`."
    )
    private final String status;

    @Schema(
        title = "Summary",
        description = "Human-readable messages derived from each active health check."
    )
    private final List<String> summary;

    @Schema(
        title = "Checks",
        description = "Raw per-check details as returned by the Ceph Dashboard API: a list of check objects."
    )
    private final Object checks;

    static HealthOutput from(CephClient.CephHealth health) {
        return HealthOutput.builder()
            .status(health.status())
            .summary(health.summary())
            .checks(health.checks())
            .build();
    }
}

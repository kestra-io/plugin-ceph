package io.kestra.plugin.ceph.cluster;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ceph.AbstractCephConnection;
import io.kestra.plugin.ceph.CephClient;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get the full Ceph cluster health report",
    description = "Calls `GET /api/health/full` and returns the cluster status, a derived human-readable summary, and the raw per-check details."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch the full Ceph cluster health report",
            full = true,
            code = """
                id: ceph_cluster_health
                namespace: company.team

                tasks:
                  - id: health
                    type: io.kestra.plugin.ceph.cluster.GetHealth
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                """
        )
    }
)
public class GetHealth extends AbstractCephConnection implements RunnableTask<GetHealth.Output> {

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            logger.info("Fetching full Ceph cluster health report");
            Map<String, Object> raw = session.get("/health/full", CephClient.MAP_TYPE);
            var health = CephClient.parseHealth(raw);

            logger.info("Ceph cluster health status: {}", health.status());

            return Output.builder()
                .status(health.status())
                .summary(health.summary())
                .checks(health.checks())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

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
            description = "Raw per-check details as returned by the Ceph Dashboard API; a list of check objects."
        )
        private final Object checks;
    }
}

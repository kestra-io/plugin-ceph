package io.kestra.plugin.ceph.cluster;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ceph.AbstractCephConnection;
import io.kestra.plugin.ceph.CephClient;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

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
public class GetHealth extends AbstractCephConnection implements RunnableTask<HealthOutput> {

    @Override
    public HealthOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            logger.info("Fetching full Ceph cluster health report");
            Map<String, Object> raw = session.get("/health/full", CephClient.MAP_TYPE);
            var health = CephClient.parseHealth(raw);

            logger.info("Ceph cluster health status: {}", health.status());

            return HealthOutput.from(health);
        }
    }
}

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
    title = "Get a quick Ceph cluster health status",
    description = "Calls `GET /api/health/minimal`, a lighter-weight alternative to `GetHealth` suited for frequent polling."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch a quick Ceph cluster health status",
            full = true,
            code = """
                id: ceph_cluster_status
                namespace: company.team

                tasks:
                  - id: status
                    type: io.kestra.plugin.ceph.cluster.GetStatus
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                """
        )
    }
)
public class GetStatus extends AbstractCephConnection implements RunnableTask<HealthOutput> {

    @Override
    public HealthOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            logger.info("Fetching Ceph cluster health status");
            Map<String, Object> raw = session.get("/health/minimal", CephClient.MAP_TYPE);
            var health = CephClient.parseHealth(raw);

            logger.info("Ceph cluster health status: {}", health.status());

            return HealthOutput.from(health);
        }
    }
}

package io.kestra.plugin.ceph.cluster;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.ceph.CephClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Polls Ceph cluster health and fires an execution the moment the cluster transitions into a
 * degraded state ({@code HEALTH_WARN} or {@code HEALTH_ERR}). Extends {@code AbstractTrigger}
 * rather than {@code AbstractCephConnection} because triggers cannot extend {@code Task}; the
 * connection properties and HTTP logic are duplicated here and delegated to {@link CephClient},
 * the same shared utility used by the tasks in this plugin.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow when the Ceph cluster health becomes degraded",
    description = """
        Polls `GET /api/health/minimal` on the configured `interval` and fires exactly once when the \
        cluster status transitions from `HEALTH_OK` (or unknown) into `HEALTH_WARN` or `HEALTH_ERR`. \
        The trigger does not fire again on subsequent polls while the cluster remains degraded, and \
        arms itself again once the cluster recovers to `HEALTH_OK`.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Alert when the Ceph cluster health degrades",
            full = true,
            code = """
                id: ceph_health_alert
                namespace: company.ops

                triggers:
                  - id: cluster_degraded
                    type: io.kestra.plugin.ceph.cluster.OnClusterHealthDegraded
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    interval: PT5M

                tasks:
                  - id: alert
                    type: io.kestra.plugin.core.log.Log
                    message: "Ceph cluster health degraded: {{ trigger.status }}"
                """
        )
    }
)
public class OnClusterHealthDegraded extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<OnClusterHealthDegraded.Output> {

    private static final String STATE_KEY_PREFIX = "ceph-cluster-health-";

    @Schema(
        title = "Ceph Dashboard host",
        description = "Hostname or IP address of the Ceph Manager Dashboard. Must be reachable from the Kestra worker."
    )
    @NotNull
    @PluginProperty(group = "connection")
    private Property<String> host;

    @Schema(
        title = "Ceph Dashboard port",
        description = "TCP port of the Ceph Manager Dashboard REST API. Defaults to `8443`."
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<Integer> port = Property.ofValue(8443);

    @Schema(
        title = "Username",
        description = "Ceph Dashboard account used to obtain a session token from `POST /api/auth`. Required unless `token` is set."
    )
    @PluginProperty(group = "connection")
    private Property<String> username;

    @Schema(
        title = "Password",
        description = "Password for the Ceph Dashboard account. Never logged. Required unless `token` is set; ignored otherwise."
    )
    @ToString.Exclude
    @PluginProperty(secret = true, group = "connection")
    private Property<String> password;

    @Schema(
        title = "Pre-obtained JWT",
        description = "A JWT obtained from `POST /api/auth`, used directly instead of username/password. It expires " +
            "(Ceph default 8h TTL) and is NOT auto-renewed: since this trigger polls indefinitely, do not use `token` " +
            "here, use `username`/`password` instead so the session is re-authenticated on every poll."
    )
    @ToString.Exclude
    @PluginProperty(secret = true, group = "connection")
    private Property<String> token;

    @Schema(
        title = "Skip TLS certificate verification",
        description = """
            When `true`, disables TLS certificate verification. Useful when the Ceph Manager Dashboard \
            uses a self-signed certificate, which is the default on most Ceph deployments. Defaults to \
            `false` (secure): enable it explicitly and only for trusted networks.
            """
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Boolean> skipSsl = Property.ofValue(false);

    @Schema(
        title = "Interval between polling.",
        description = """
            The interval between 2 different polls of schedule, this can avoid to overload the remote system with too many calls. For most of the triggers that depend on external systems, a minimal interval must be at least PT30S.
            See [ISO_8601 Durations](https://en.wikipedia.org/wiki/ISO_8601#Durations) for more information of available interval values."""
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "reliability")
    private Duration interval = Duration.ofMinutes(5);

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var logger = runContext.logger();

        var rHost = runContext.render(host).as(String.class).orElseThrow(() -> new IllegalArgumentException("host is required"));
        var rPort = runContext.render(port).as(Integer.class).orElse(8443);
        var rUsername = runContext.render(username).as(String.class).orElse(null);
        var rPassword = runContext.render(password).as(String.class).orElse(null);
        var rToken = runContext.render(token).as(String.class).orElse(null);
        var rSkipSsl = runContext.render(skipSsl).as(Boolean.class).orElse(false);

        try (var session = CephClient.connect(runContext, rHost, rPort, rUsername, rPassword, rToken, rSkipSsl)) {
            Map<String, Object> raw = session.get("/health/minimal", CephClient.MAP_TYPE);
            var health = CephClient.parseHealth(raw);

            // Key includes flowId and triggerId so multiple flows/triggers polling different clusters don't clobber each other's state.
            var kvKey = STATE_KEY_PREFIX + context.getFlowId() + "-" + context.getTriggerId();
            var kv = runContext.namespaceKv(context.getNamespace());

            var lastStatus = kv.getValue(kvKey).map(value -> String.valueOf(value.value())).orElse(null);
            var currentlyDegraded = CephClient.isDegraded(health.status());
            var previouslyDegraded = CephClient.isDegraded(lastStatus);

            // Refreshed every poll while the trigger is active, so a 10x-interval TTL never expires a
            // live status but ages out an orphaned entry a few polls after the trigger stops.
            kv.put(kvKey, new KVValueAndMetadata(new KVMetadata(null, interval.multipliedBy(10)), health.status()));

            if (!currentlyDegraded || previouslyDegraded) {
                logger.debug("Ceph cluster health status={} (previous={}), not firing", health.status(), lastStatus);
                return Optional.empty();
            }

            logger.info("Ceph cluster health degraded: status={}", health.status());

            var output = Output.builder()
                .status(health.status())
                .summary(health.summary())
                .checks(health.checks())
                .build();

            return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Cluster status", description = "The degraded status that triggered this execution: `HEALTH_WARN` or `HEALTH_ERR`.")
        private final String status;

        @Schema(title = "Summary", description = "Human-readable messages derived from each active health check.")
        private final List<String> summary;

        @Schema(title = "Checks", description = "Raw per-check details as returned by the Ceph Dashboard API; a list of check objects.")
        private final Object checks;
    }
}

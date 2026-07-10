package io.kestra.plugin.ceph;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
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

import java.io.IOException;

/**
 * Base connection surface shared by every Ceph task: the Ceph Dashboard host/port, the account
 * used to authenticate, and the self-signed TLS opt-out. Subclasses call {@link #connect(RunContext)}
 * once per execution to obtain an authenticated {@link CephSession}.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractCephConnection extends Task {

    @Schema(
        title = "Ceph Dashboard host",
        description = "Hostname or IP address of the Ceph Manager Dashboard. Must be reachable from the Kestra worker."
    )
    @NotNull
    @PluginProperty(group = "connection")
    protected Property<String> host;

    @Schema(
        title = "Ceph Dashboard port",
        description = "TCP port of the Ceph Manager Dashboard REST API. Defaults to `8443`."
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "connection")
    protected Property<@Min(1) @Max(65535) Integer> port = Property.ofValue(8443);

    @Schema(
        title = "Username",
        description = "Ceph Dashboard account used to obtain a session token from `POST /api/auth`. Required unless `token` is set."
    )
    @PluginProperty(group = "connection")
    protected Property<String> username;

    @Schema(
        title = "Password",
        description = "Password for the Ceph Dashboard account. Never logged. Required unless `token` is set; ignored otherwise."
    )
    @ToString.Exclude
    @PluginProperty(secret = true, group = "connection")
    protected Property<String> password;

    @Schema(
        title = "Pre-obtained JWT",
        description = "A JWT obtained from `POST /api/auth`, used directly instead of username/password. Reused as-is for " +
            "every request in the execution. It expires (Ceph default 8h TTL) and is NOT auto-renewed, so do not use it " +
            "in triggers or scheduled flows; use username/password there instead. Mutually exclusive with `password`: " +
            "when set, `username`/`password` are ignored."
    )
    @ToString.Exclude
    @PluginProperty(secret = true, group = "connection")
    protected Property<String> token;

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
    protected Property<Boolean> skipSsl = Property.ofValue(false);

    /**
     * Renders the connection properties and resolves an authenticated session: a pre-obtained
     * {@code token} is used as-is when present, otherwise {@code username}/{@code password} are
     * exchanged for a JWT via {@code POST /api/auth}.
     */
    protected CephSession connect(RunContext runContext) throws IllegalVariableEvaluationException, IOException, HttpClientException {
        var rHost = runContext.render(host).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("host is required"));
        var rPort = runContext.render(port).as(Integer.class).orElse(8443);
        var rUsername = runContext.render(username).as(String.class).orElse(null);
        var rPassword = runContext.render(password).as(String.class).orElse(null);
        var rToken = runContext.render(token).as(String.class).orElse(null);
        var rSkipSsl = runContext.render(skipSsl).as(Boolean.class).orElse(false);

        return CephClient.connect(runContext, rHost, rPort, rUsername, rPassword, rToken, rSkipSsl);
    }
}

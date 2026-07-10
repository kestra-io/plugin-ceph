package io.kestra.plugin.ceph.rgw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ceph.AbstractCephConnection;
import io.kestra.plugin.ceph.CephClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create an RGW user",
    description = "Calls `POST /api/rgw/user` and returns the resulting user, including the generated S3 " +
        "access keys. The secret key is returned only at creation time and is encrypted in the outputs."
)
@Plugin(
    examples = {
        @Example(
            title = "Create an RGW user",
            full = true,
            code = """
                id: create_rgw_user
                namespace: company.team

                tasks:
                  - id: create_user
                    type: io.kestra.plugin.ceph.rgw.CreateUser
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    uid: "svc-backups"
                    displayName: "Backup service account"
                    email: "backups@company.team"
                """
        )
    }
)
public class CreateUser extends AbstractCephConnection implements RunnableTask<UserInfo> {

    @Schema(
        title = "User ID",
        description = "Unique identifier for the new RGW user."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> uid;

    @Schema(
        title = "Display name",
        description = "Human-readable name for the user."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> displayName;

    @Schema(
        title = "Email",
        description = "Optional contact email for the user."
    )
    @PluginProperty(group = "main")
    private Property<String> email;

    @Override
    public UserInfo run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            var rUid = runContext.render(uid).as(String.class).orElseThrow(() -> new IllegalArgumentException("uid is required"));
            var rDisplayName = runContext.render(displayName).as(String.class).orElseThrow(() -> new IllegalArgumentException("displayName is required"));
            var rEmail = email != null ? runContext.render(email).as(String.class).orElse(null) : null;

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("uid", rUid);
            body.put("display_name", rDisplayName);
            if (rEmail != null) {
                body.put("email", rEmail);
            }

            logger.info("Creating RGW user '{}'", rUid);
            // RGW user creation returns the full user synchronously in the POST body, unlike pool,
            // image, and bucket creation which the Dashboard task manager may process asynchronously.
            // Some Ceph versions still return an empty body, so fall back to fetching the user, with
            // the same retry the sibling Create tasks use for the async-creation 404 window.
            var raw = session.post("/rgw/user", body, new TypeReference<RawUser>() {
            });
            if (raw == null) {
                raw = session.getWithRetry("/rgw/user/" + CephClient.pathSegment(rUid), new TypeReference<RawUser>() {
                });
            }

            return toUserInfo(raw, runContext);
        }
    }

    /**
     * Builds the task output from the raw dashboard response, encrypting each S3 secret key via
     * {@link EncryptedString} so it is not stored in cleartext in the execution outputs.
     */
    private static UserInfo toUserInfo(RawUser raw, RunContext runContext) throws GeneralSecurityException {
        var keys = new ArrayList<UserInfo.S3Key>();
        if (raw.keys() != null) {
            for (var key : raw.keys()) {
                var secretKey = key.secretKey() != null ? EncryptedString.from(key.secretKey(), runContext) : null;
                keys.add(new UserInfo.S3Key(key.accessKey(), secretKey));
            }
        }
        return new UserInfo(raw.userId(), raw.displayName(), raw.email(), keys);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawUser(
        @JsonProperty("user_id") String userId,
        @JsonProperty("display_name") String displayName,
        String email,
        @JsonProperty("keys") List<RawKey> keys
    ) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record RawKey(
            @JsonProperty("access_key") String accessKey,
            @JsonProperty("secret_key") String secretKey
        ) {
        }
    }
}

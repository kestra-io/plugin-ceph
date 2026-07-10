package io.kestra.plugin.ceph.rgw;

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
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create an RGW bucket",
    description = """
        Calls `POST /api/rgw/bucket` and returns the resulting bucket. Some Ceph versions process \
        bucket creation asynchronously via the Dashboard task manager, so the follow-up fetch may \
        briefly 404 right after the POST; this task retries it for up to ~10 seconds (10 attempts, \
        1s apart) before failing.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Create an RGW bucket owned by an existing user",
            full = true,
            code = """
                id: create_rgw_bucket
                namespace: company.team

                tasks:
                  - id: create_bucket
                    type: io.kestra.plugin.ceph.rgw.CreateBucket
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    bucketName: "backups"
                    owner: "svc-backups"
                """
        )
    }
)
public class CreateBucket extends AbstractCephConnection implements RunnableTask<BucketInfo> {

    @Schema(
        title = "Bucket name",
        description = "Name of the bucket to create."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> bucketName;

    @Schema(
        title = "Owner",
        description = "UID of the RGW user that owns the bucket."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> owner;

    @Override
    public BucketInfo run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            var rBucketName = runContext.render(bucketName).as(String.class).orElseThrow(() -> new IllegalArgumentException("bucketName is required"));
            var rOwner = runContext.render(owner).as(String.class).orElseThrow(() -> new IllegalArgumentException("owner is required"));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("bucket", rBucketName);
            body.put("uid", rOwner);

            logger.info("Creating RGW bucket '{}' owned by '{}'", rBucketName, rOwner);
            session.post("/rgw/bucket", body, null);

            return session.getWithRetry("/rgw/bucket/" + CephClient.pathSegment(rBucketName), new TypeReference<BucketInfo>() {
            });
        }
    }
}

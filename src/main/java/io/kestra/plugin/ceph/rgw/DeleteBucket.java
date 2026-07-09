package io.kestra.plugin.ceph.rgw;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import io.kestra.plugin.ceph.AbstractCephConnection;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete an RGW bucket",
    description = "Calls `DELETE /api/rgw/bucket/{bucket}`. A bucket that no longer exists is treated as already deleted rather than an error."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an RGW bucket",
            full = true,
            code = """
                id: delete_rgw_bucket
                namespace: company.team

                tasks:
                  - id: delete_bucket
                    type: io.kestra.plugin.ceph.rgw.DeleteBucket
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                    bucketName: "backups"
                """
        )
    }
)
public class DeleteBucket extends AbstractCephConnection implements RunnableTask<DeleteBucket.Output> {

    @Schema(title = "Bucket name", description = "Name of the bucket to delete.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> bucketName;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var session = connect(runContext);

        var rBucketName = runContext.render(bucketName).as(String.class).orElseThrow(() -> new IllegalArgumentException("bucketName is required"));

        logger.info("Deleting RGW bucket '{}'", rBucketName);
        var deleted = session.delete("/rgw/bucket/" + rBucketName);

        return Output.builder()
            .deleted(deleted)
            .message(deleted ? "Bucket '" + rBucketName + "' deleted." : "Bucket '" + rBucketName + "' did not exist.")
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Deleted", description = "`true` if the bucket existed and was deleted, `false` if it was already absent.")
        private final Boolean deleted;

        @Schema(title = "Message", description = "Human-readable outcome of the deletion.")
        private final String message;
    }
}

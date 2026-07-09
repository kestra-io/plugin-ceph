package io.kestra.plugin.ceph.rgw;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ceph.AbstractCephConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List RGW buckets",
    description = "Calls `GET /api/rgw/bucket` and returns the bucket names in the Object Gateway."
)
@Plugin(
    examples = {
        @Example(
            title = "List RGW buckets",
            full = true,
            code = """
                id: list_rgw_buckets
                namespace: company.team

                tasks:
                  - id: buckets
                    type: io.kestra.plugin.ceph.rgw.ListBuckets
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                """
        )
    }
)
public class ListBuckets extends AbstractCephConnection implements RunnableTask<ListBuckets.Output> {

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var session = connect(runContext);

        logger.info("Listing RGW buckets");
        List<String> buckets = session.get("/rgw/bucket", new TypeReference<>() {
        });

        return Output.builder()
            .total(buckets.size())
            .buckets(buckets)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Total", description = "Number of buckets returned.")
        private final Integer total;

        @Schema(title = "Buckets", description = "Names of the buckets in the Object Gateway.")
        private final List<String> buckets;
    }
}

package io.kestra.plugin.ceph.rgw;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ceph.AbstractCephConnection;
import io.kestra.plugin.ceph.CephFetch;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
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

    @Schema(
        title = "How results are returned",
        description = "`FETCH` (default) returns the bucket names inline under `buckets`, `STORE` writes them to Kestra " +
            "internal storage as Ion and returns a `uri`, `FETCH_ONE` returns only the first bucket, `NONE` returns " +
            "just the count. Use `STORE` on gateways with a large number of buckets."
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        try (var session = connect(runContext)) {
            var rFetchType = runContext.render(fetchType).as(FetchType.class).orElse(FetchType.FETCH);

            logger.info("Listing RGW buckets");
            List<String> buckets = session.get("/rgw/bucket", new TypeReference<>() {
            });

            var output = Output.builder().total(buckets.size());
            CephFetch.apply(runContext, rFetchType, buckets, output::buckets, output::uri);
            return output.build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Total",
            description = "Number of buckets returned."
        )
        private final Integer total;

        @Schema(
            title = "Buckets",
            description = "Names of the buckets in the Object Gateway. Empty when `fetchType` is `STORE` or `NONE`."
        )
        private final List<String> buckets;

        @Schema(
            title = "URI",
            description = "Storage URI of the Ion-serialized bucket names. Set only when `fetchType` is `STORE`."
        )
        private final URI uri;
    }
}

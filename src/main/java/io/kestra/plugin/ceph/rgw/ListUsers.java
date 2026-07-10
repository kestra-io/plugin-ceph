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
    title = "List RGW users",
    description = "Calls `GET /api/rgw/user` and returns the user IDs in the Object Gateway."
)
@Plugin(
    examples = {
        @Example(
            title = "List RGW users",
            full = true,
            code = """
                id: list_rgw_users
                namespace: company.team

                tasks:
                  - id: users
                    type: io.kestra.plugin.ceph.rgw.ListUsers
                    host: "ceph-mgr.internal"
                    username: "admin"
                    password: "{{ secret('CEPH_DASHBOARD_PASSWORD') }}"
                """
        )
    }
)
public class ListUsers extends AbstractCephConnection implements RunnableTask<ListUsers.Output> {

    @Schema(
        title = "How results are returned",
        description = "`FETCH` (default) returns the user IDs inline under `users`, `STORE` writes them to Kestra " +
            "internal storage as Ion and returns a `uri`, `FETCH_ONE` returns only the first user, `NONE` returns " +
            "just the count. Use `STORE` on gateways with a large number of users."
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

            logger.info("Listing RGW users");
            List<String> users = session.get("/rgw/user", new TypeReference<>() {
            });

            var output = Output.builder().total(users.size());
            CephFetch.apply(runContext, rFetchType, users, output::users, output::uri);
            return output.build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Total",
            description = "Number of users returned."
        )
        private final Integer total;

        @Schema(
            title = "Users",
            description = "User IDs registered in the Object Gateway. Empty when `fetchType` is `STORE` or `NONE`."
        )
        private final List<String> users;

        @Schema(
            title = "URI",
            description = "Storage URI of the Ion-serialized user IDs. Set only when `fetchType` is `STORE`."
        )
        private final URI uri;
    }
}

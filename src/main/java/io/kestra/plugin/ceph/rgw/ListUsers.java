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

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var session = connect(runContext);

        logger.info("Listing RGW users");
        List<String> users = session.get("/rgw/user", new TypeReference<>() {
        });

        return Output.builder()
            .total(users.size())
            .users(users)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Total", description = "Number of users returned.")
        private final Integer total;

        @Schema(title = "Users", description = "User IDs registered in the Object Gateway.")
        private final List<String> users;
    }
}

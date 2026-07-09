package io.kestra.plugin.ceph.rgw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Curated view of an RGW user as returned by {@code GET /api/rgw/user/{uid}} and
 * {@code POST /api/rgw/user}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserInfo(
    @Schema(title = "User ID", description = "Unique identifier of the RGW user.")
    @JsonProperty("user_id") String userId,

    @Schema(title = "Display name", description = "Human-readable name of the user.")
    @JsonProperty("display_name") String displayName,

    @Schema(title = "Email", description = "Contact email of the user, if set.")
    String email
) implements io.kestra.core.models.tasks.Output {
}

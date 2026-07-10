package io.kestra.plugin.ceph.rgw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Curated view of an RGW bucket as returned by {@code GET /api/rgw/bucket/{bucket}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BucketInfo(
    @Schema(
        title = "Bucket name",
        description = "Name of the bucket."
    )
    @JsonProperty("bucket") String name,

    @Schema(
        title = "Owner",
        description = "UID of the RGW user that owns the bucket."
    )
    String owner,

    @Schema(
        title = "Bucket ID",
        description = "Internal identifier Ceph assigned to the bucket."
    )
    String id
) implements io.kestra.core.models.tasks.Output {
}

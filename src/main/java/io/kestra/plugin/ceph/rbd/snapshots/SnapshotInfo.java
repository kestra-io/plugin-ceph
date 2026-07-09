package io.kestra.plugin.ceph.rbd.snapshots;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Curated view of an RBD snapshot as returned within the {@code snapshots} array of
 * {@code GET /api/block/image/{image_spec}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SnapshotInfo(
    @Schema(title = "Snapshot ID", description = "Numeric identifier Ceph assigned to the snapshot.")
    Long id,

    @Schema(title = "Snapshot name", description = "Name of the snapshot.")
    String name,

    @Schema(title = "Size", description = "Size of the image at the time the snapshot was taken, in bytes.")
    Long size,

    @Schema(title = "Timestamp", description = "Timestamp of when the snapshot was created, as returned by Ceph.")
    String timestamp,

    @Schema(title = "Protected", description = "Whether the snapshot is protected from deletion.")
    @JsonProperty("is_protected") Boolean isProtected
) implements io.kestra.core.models.tasks.Output {
}

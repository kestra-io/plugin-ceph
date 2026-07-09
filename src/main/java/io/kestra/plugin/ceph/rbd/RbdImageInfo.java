package io.kestra.plugin.ceph.rbd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Curated view of an RBD image as returned by {@code GET /api/block/image} and
 * {@code GET /api/block/image/{image_spec}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RbdImageInfo(
    @Schema(title = "Image name", description = "Name of the RBD image.")
    String name,

    @Schema(title = "Pool name", description = "Name of the pool the image belongs to.")
    @JsonProperty("pool_name") String poolName,

    @Schema(title = "Size", description = "Size of the image in bytes.")
    Long size,

    @Schema(title = "Object size", description = "Size of the RADOS objects backing the image, in bytes.")
    @JsonProperty("obj_size") Long objectSize
) implements io.kestra.core.models.tasks.Output {

    /** Returns a copy with {@code poolName} filled in when the image object itself omits it. */
    public RbdImageInfo withPoolName(String fallbackPoolName) {
        return poolName != null ? this : new RbdImageInfo(name, fallbackPoolName, size, objectSize);
    }
}

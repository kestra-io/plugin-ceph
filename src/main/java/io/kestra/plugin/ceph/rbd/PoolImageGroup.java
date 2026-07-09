package io.kestra.plugin.ceph.rbd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code GET /api/block/image} groups images by pool: one entry per pool, each carrying the list
 * of images in it. This mirrors that shape so the task can flatten it into a single list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PoolImageGroup(
    @JsonProperty("pool_name") String poolName,
    List<RbdImageInfo> value
) {
}

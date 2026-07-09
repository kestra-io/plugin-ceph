package io.kestra.plugin.ceph.rbd.snapshots;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.plugin.ceph.CephClient;
import io.kestra.plugin.ceph.CephSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The Ceph Dashboard API has no dedicated "list snapshots" endpoint: snapshots are embedded in the
 * {@code snapshots} array of the parent image's detail response. Centralised here so every
 * snapshot task shares the same lookup and parsing logic.
 */
final class SnapshotSupport {

    private SnapshotSupport() {
    }

    static List<SnapshotInfo> fetchSnapshots(CephSession session, String imageSpec)
        throws IOException, IllegalVariableEvaluationException, HttpClientException {
        Map<String, Object> image = session.get("/block/image/" + imageSpec, CephClient.MAP_TYPE);
        var raw = image.get("snapshots");
        if (raw == null) {
            return List.of();
        }
        return CephClient.MAPPER.convertValue(raw, new com.fasterxml.jackson.core.type.TypeReference<List<SnapshotInfo>>() {
        });
    }
}

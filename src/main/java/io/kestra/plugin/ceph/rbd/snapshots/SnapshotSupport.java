package io.kestra.plugin.ceph.rbd.snapshots;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.runners.RunContext;
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
        return CephClient.MAPPER.convertValue(raw, new TypeReference<List<SnapshotInfo>>() {
        });
    }

    /**
     * Looks up a just-created snapshot by name, retrying for up to
     * {@link CephSession#DEFAULT_RETRY_ATTEMPTS} tries: the parent image already exists so its GET
     * never 404s, but Ceph's task manager can process the snapshot creation asynchronously, so the
     * new entry can briefly be missing from the {@code snapshots} array. Throws once attempts are
     * exhausted rather than fabricating a placeholder result for a snapshot that never appeared.
     */
    static SnapshotInfo findWithRetry(CephSession session, RunContext runContext, String imageSpec, String snapshotName)
        throws IOException, IllegalVariableEvaluationException, HttpClientException {
        var attempts = CephSession.DEFAULT_RETRY_ATTEMPTS;
        var delayMillis = CephSession.DEFAULT_RETRY_DELAY_MILLIS;

        for (var attempt = 1; attempt <= attempts; attempt++) {
            var found = fetchSnapshots(session, imageSpec).stream()
                .filter(snapshot -> snapshotName.equals(snapshot.name()))
                .findFirst();

            if (found.isPresent()) {
                return found.get();
            }

            if (attempt == attempts) {
                break;
            }

            runContext.logger().debug(
                "Snapshot '{}' not yet visible on image '{}', retrying in {}ms (attempt {}/{})",
                snapshotName, imageSpec, delayMillis, attempt, attempts
            );
            sleep(delayMillis);
        }

        throw new IllegalStateException(
            "Snapshot '" + snapshotName + "' did not appear on image '" + imageSpec + "' after " + attempts + " attempts (~" + (attempts * delayMillis) + "ms) waiting for asynchronous creation."
        );
    }

    private static void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a Ceph Dashboard snapshot to become visible", e);
        }
    }
}

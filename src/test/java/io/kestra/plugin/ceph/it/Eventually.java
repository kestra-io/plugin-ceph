package io.kestra.plugin.ceph.it;

import java.util.function.Predicate;

/**
 * Bounded retry for GET-after-write assertions against the live cluster. Several Ceph Dashboard
 * endpoints apply a change through a background task manager or via cluster map propagation (pool
 * and RBD image creation, PG count changes, ...); retrying only here keeps the task's own
 * behavior (issue the request, return the best-effort current state) untouched while keeping the
 * suite stable against a brief lag on a freshly booted single-node demo cluster.
 */
final class Eventually {

    private Eventually() {
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    static <T> T eventually(ThrowingSupplier<T> supplier, Predicate<T> condition, int attempts, long delayMillis) throws Exception {
        T last = null;
        Exception lastException = null;

        for (var i = 0; i < attempts; i++) {
            try {
                last = supplier.get();
                lastException = null;
                if (condition.test(last)) {
                    return last;
                }
            } catch (Exception e) {
                lastException = e;
            }
            Thread.sleep(delayMillis);
        }

        if (lastException != null) {
            throw lastException;
        }
        return last;
    }
}

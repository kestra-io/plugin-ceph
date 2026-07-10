package io.kestra.plugin.ceph.it;

import io.kestra.core.models.property.Property;
import io.kestra.plugin.ceph.AbstractCephConnection;

/**
 * Shared connection helper for the live-cluster integration test suite. Reads connection details
 * from the environment so the same tests run unmodified against the CI-provisioned Ceph demo
 * cluster (see {@code .github/setup-unit.sh}), defaulting to a dashboard reachable on localhost as
 * a convenience for anyone who spins the same demo image up manually on an amd64 host.
 */
final class CephIT {

    static final String HOST = System.getenv().getOrDefault("CEPH_HOST", "localhost");
    static final int PORT = Integer.parseInt(System.getenv().getOrDefault("CEPH_PORT", "8443"));
    static final String USERNAME = System.getenv().getOrDefault("CEPH_USERNAME", "admin");
    static final String PASSWORD = System.getenv().getOrDefault("CEPH_PASSWORD", "password");

    private CephIT() {
    }

    static <T extends AbstractCephConnection.AbstractCephConnectionBuilder<?, ?>> T withConnection(T builder) {
        builder.host(Property.ofValue(HOST));
        builder.port(Property.ofValue(PORT));
        builder.username(Property.ofValue(USERNAME));
        builder.password(Property.ofValue(PASSWORD));
        builder.skipSsl(Property.ofValue(true));
        return builder;
    }
}

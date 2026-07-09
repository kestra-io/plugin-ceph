# Kestra Ceph Plugin

## What

- Provides plugin components under `io.kestra.plugin.ceph`, talking to the Ceph Manager Dashboard REST API (`https://{host}:{port}/api/`).
- `cluster`: `GetHealth`, `GetStatus`, and the `OnClusterHealthDegraded` polling trigger.
- `pools`: `List`, `Get`, `Create`, `Update`, `Delete`.
- `rbd`: `List`, `Create`, `Delete`.
- `rbd.snapshots`: `Create`, `List`, `Delete`, `Rollback`, `Clone`.
- `rgw`: `ListBuckets`, `CreateBucket`, `DeleteBucket`, `ListUsers`, `CreateUser`.

## Why

- What user problem does this solve? Teams running Ceph need to manage cluster health, pools, RBD images, and the Object Gateway as part of their Kestra orchestration flows, instead of scripting the Dashboard API by hand.
- Why would a team adopt this plugin in a workflow? It gives storage and platform teams a declarative way to provision pools and RBD images, snapshot and roll back volumes, manage RGW buckets and users, and alert on degraded cluster health.
- What operational/business outcome does it enable? It reduces manual `ceph` CLI/Dashboard operations, makes storage provisioning repeatable and auditable as code, and lets teams react to cluster health issues automatically.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin.ceph`:

- `ceph` — `AbstractCephConnection` (shared connection properties + JWT auth), `CephClient` (shared HTTP execution/error handling, used by both tasks and the trigger), `CephSession` (per-execution authenticated session).
- `ceph.cluster` — cluster health tasks and the degraded-health trigger.
- `ceph.pools` — pool CRUD tasks.
- `ceph.rbd` — RBD block image tasks.
- `ceph.rbd.snapshots` — RBD snapshot tasks.
- `ceph.rgw` — Object Gateway bucket and user tasks.

Authentication: every task/trigger renders `host`/`port`/`username`/`password`/`token`/`skipSsl`. If `token` is set it is used as-is (no call to `/api/auth`); otherwise `username`/`password` are exchanged for a JWT via `POST /api/auth` once per execution. Neither `token` nor `password` set is a hard failure. No token caching across executions, and a `token` is never renewed, so it is only fit for one-off tasks, not triggers. Every request carries `Accept: application/vnd.ceph.api.v1.0+json`.

### Key Plugin Classes

- `io.kestra.plugin.ceph.AbstractCephConnection`
- `io.kestra.plugin.ceph.CephClient`
- `io.kestra.plugin.ceph.CephSession`
- `io.kestra.plugin.ceph.cluster.OnClusterHealthDegraded`

### Project Structure

```
plugin-ceph/
├── src/main/java/io/kestra/plugin/ceph/
│   ├── AbstractCephConnection.java, CephClient.java, CephSession.java
│   ├── cluster/
│   ├── pools/
│   ├── rbd/
│   │   └── snapshots/
│   └── rgw/
├── src/test/java/io/kestra/plugin/ceph/  (WireMock-based unit tests, mirroring src/main structure)
├── build.gradle
└── README.md
```

## Local rules

- Base the wording on the implemented packages and classes, not on template README text.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines

# How to use the Ceph plugin

Manage a [Ceph](https://ceph.io) cluster from Kestra flows through the Ceph Manager Dashboard REST API (`https://{host}:{port}/api/`, default port `8443`).

## Authentication

Every task and the health trigger share the same connection properties:

- `host` (required): hostname or IP of the Ceph Manager Dashboard.
- `port` (optional, default `8443`): TCP port of the Dashboard REST API.
- `username` and `password` (secret): the Dashboard account used to obtain a session token from `POST /api/auth`. Required unless `token` is set. The password is masked in logs and the UI; store it as a [secret](https://kestra.io/docs/concepts/secret) and reference it with `{{ secret('CEPH_DASHBOARD_PASSWORD') }}`.
- `token` (optional, secret): a JWT already obtained from `POST /api/auth`, used as-is instead of `username`/`password`. It is masked in logs and the UI, same as `password`. It is mutually exclusive with `password`: when set, `username`/`password` are ignored and no call to `/api/auth` is made. Ceph's default JWT TTL is 8 hours and this plugin does **not** renew it, so only use `token` for one-off tasks; use `username`/`password` in triggers or scheduled flows, where the session is re-authenticated on every run.
- `skipSsl` (optional, default `false`): disables TLS certificate verification. Ceph Manager Dashboards use a self-signed certificate by default, so most deployments need `skipSsl: true` unless a trusted certificate has been configured. It is off by default so flows fail closed rather than silently trust an unverified endpoint.

When neither `token` nor `password` is set, tasks fail fast with an actionable error. A new session token is requested once per task execution (or per trigger poll) when authenticating with `username`/`password`; tokens are not cached or reused across executions. Every request also carries the versioned `Accept: application/vnd.ceph.api.v1.0+json` header the Dashboard API requires.

You can set these properties once via [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults) instead of repeating them on every task.

## Tasks

### Cluster health (`cluster`)

- `cluster.GetHealth`: calls `GET /api/health/full` for the complete health report.
- `cluster.GetStatus`: calls `GET /api/health/minimal` for a lighter-weight status check, suited for frequent polling.

Both return `status` (`HEALTH_OK`, `HEALTH_WARN`, or `HEALTH_ERR`), a `summary` of human-readable messages derived from each active check, and the raw `checks` map.

### Pools (`pools`)

`pools.List`, `pools.Get`, `pools.Create`, `pools.Update`, and `pools.Delete` manage Ceph storage pools over `/api/pool`. `Create` requires `poolName`; `poolType` (`REPLICATED` or `ERASURE`), `pgNum`, and `size` have sensible defaults. `Update` sends only the fields explicitly set (`size`, `pgNum`, `applicationMetadata`). `Delete` treats an already-absent pool as successfully deleted rather than an error.

Some Ceph versions process pool creation and updates asynchronously via the Dashboard task manager; `Create` and `Update` retry the follow-up fetch of the resulting pool for up to ~10 seconds instead of failing on a transient 404.

### RBD images (`rbd`)

`rbd.List`, `rbd.Create`, and `rbd.Delete` manage RBD block images over `/api/block/image`. Images are identified by `poolName` + `imageName`; internally this plugin builds the composite `image_spec` (`{pool}/{image}`, percent-encoded) the API expects, so you never need to build it yourself.

### RBD snapshots (`rbd.snapshots`)

`rbd.snapshots.Create`, `List`, `Delete`, `Rollback`, and `Clone` manage snapshots over `/api/block/image/{image_spec}/snap`. The Dashboard API has no dedicated snapshot-listing endpoint: `List` fetches the parent image's detail and returns its embedded `snapshots` array. `Rollback` is destructive: it discards any writes made to the image after the snapshot was taken. `Clone` creates a new copy-on-write image (`childPoolName` + `childImageName`) backed by the snapshot.

### Object Gateway (`rgw`)

`rgw.ListBuckets`, `rgw.CreateBucket`, `rgw.DeleteBucket`, `rgw.ListUsers`, and `rgw.CreateUser` manage RGW buckets and users over `/api/rgw/bucket` and `/api/rgw/user`. Every bucket must have an `owner` (an existing RGW user's `uid`). `DeleteBucket` deletes the bucket via `DELETE /api/rgw/bucket/{bucket}` and does not take a purge option.

## Triggers

`cluster.HealthTrigger` polls `GET /api/health/minimal` on the configured `interval` (default `PT5M`) and fires exactly once when the cluster status transitions from `HEALTH_OK` (or unknown) into `HEALTH_WARN` or `HEALTH_ERR`. It does not fire again on subsequent polls while the cluster stays degraded, and re-arms once the cluster recovers to `HEALTH_OK`. Outputs: `status`, `summary`, and `checks`, same shape as the health tasks.

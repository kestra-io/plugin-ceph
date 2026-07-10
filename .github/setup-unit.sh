#!/usr/bin/env bash
# Starts the Ceph cluster (via ceph-bootstrap.sh) and points the integration tests at it. amd64 CI.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
compose="$(cd "${script_dir}/.." && pwd)/docker-compose-ci.yml"
container="ceph"

docker compose -f "${compose}" up -d

# Wait for the container (running sleep infinity) before exec'ing the bootstrap into it.
for _ in $(seq 30); do
    if [ "$(docker inspect -f '{{.State.Running}}' "${container}" 2>/dev/null)" = "true" ]; then
        break
    fi
    sleep 2
done

# The base image ships no orchestration, so bring up mon + mgr + OSD + dashboard + RGW explicitly.
echo "Bootstrapping the Ceph cluster..."
docker cp "${script_dir}/ceph-bootstrap.sh" "${container}:/ceph-bootstrap.sh"
docker exec "${container}" bash /ceph-bootstrap.sh

# The cluster is ready once the Dashboard API issues a JWT.
echo "Waiting for the Ceph Dashboard API on https://localhost:8443 ..."
for _ in $(seq 30); do
    if curl -sk -X POST https://localhost:8443/api/auth \
        -H "Accept: application/vnd.ceph.api.v1.0+json" \
        -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"password"}' | grep -q '"token"'; then
        ready=1
        break
    fi
    sleep 3
done
if [ "${ready:-}" != "1" ]; then
    echo "Ceph Dashboard API did not become ready" >&2
    docker exec "${container}" ceph -s || true
    exit 1
fi
echo "Ceph Dashboard API is ready."

# Tell the Gradle step to run the integration tests, and how to reach the cluster.
if [ -n "${GITHUB_ENV:-}" ]; then
    {
        echo "CEPH_IT=true"
        echo "CEPH_HOST=localhost"
        echo "CEPH_PORT=8443"
        echo "CEPH_USERNAME=admin"
        echo "CEPH_PASSWORD=password"
    } >> "${GITHUB_ENV}"
fi

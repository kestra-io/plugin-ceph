#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

CEPH_CONTAINER="ceph-demo"

docker compose -f "${repo_root}/docker-compose-ci.yml" up -d

# Wait for the container (sleep infinity) to be running before exec'ing into it.
echo "Waiting for the ${CEPH_CONTAINER} container..."
timeout=60
elapsed=0
until [ "$(docker inspect -f '{{.State.Running}}' "${CEPH_CONTAINER}" 2>/dev/null)" = "true" ]; do
    if [ "${elapsed}" -ge "${timeout}" ]; then
        echo "Container ${CEPH_CONTAINER} did not start within ${timeout}s" >&2
        docker compose -f "${repo_root}/docker-compose-ci.yml" logs --tail=120 || true
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done

# The base image ships no orchestration, so bring up mon + mgr + OSD + dashboard + RGW explicitly.
echo "Bootstrapping the Ceph cluster..."
docker cp "${script_dir}/ceph-bootstrap.sh" "${CEPH_CONTAINER}:/ceph-bootstrap.sh"
docker exec "${CEPH_CONTAINER}" bash /ceph-bootstrap.sh

echo "Waiting for the dashboard service to be published by the manager..."
timeout=120
elapsed=0
until docker exec "${CEPH_CONTAINER}" ceph mgr services 2>/dev/null | grep -q "dashboard"; do
    if [ "${elapsed}" -ge "${timeout}" ]; then
        echo "Ceph dashboard did not come up within ${timeout}s" >&2
        docker exec "${CEPH_CONTAINER}" ceph mgr services || true
        docker exec "${CEPH_CONTAINER}" ceph -s || true
        exit 1
    fi
    sleep 5
    elapsed=$((elapsed + 5))
done

echo "Waiting for the Ceph Dashboard API to accept authentication on localhost:8443..."
timeout=60
elapsed=0
until curl -sk -X POST https://localhost:8443/api/auth \
    -H "Accept: application/vnd.ceph.api.v1.0+json" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"password"}' | grep -q '"token"'; do
    if [ "${elapsed}" -ge "${timeout}" ]; then
        echo "Ceph Dashboard API did not accept authentication within ${timeout}s" >&2
        exit 1
    fi
    sleep 3
    elapsed=$((elapsed + 3))
done

echo "Ceph Dashboard API is ready on https://localhost:8443"

if [ -n "${GITHUB_ENV:-}" ]; then
    {
        echo "CEPH_IT=true"
        echo "CEPH_HOST=localhost"
        echo "CEPH_PORT=8443"
        echo "CEPH_USERNAME=admin"
        echo "CEPH_PASSWORD=password"
    } >> "$GITHUB_ENV"
fi

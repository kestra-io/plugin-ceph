#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

CEPH_CONTAINER="ceph-demo"

docker compose -f "${repo_root}/docker-compose-ci.yml" up -d

echo "Waiting for the Ceph cluster to reach HEALTH_OK or HEALTH_WARN..."
timeout=180
elapsed=0
until docker exec "${CEPH_CONTAINER}" ceph -s >/tmp/ceph-status.log 2>&1 && grep -Eq "HEALTH_OK|HEALTH_WARN" /tmp/ceph-status.log; do
    if [ "${elapsed}" -ge "${timeout}" ]; then
        echo "Ceph cluster did not become healthy within ${timeout}s" >&2
        docker exec "${CEPH_CONTAINER}" ceph -s || true
        docker compose -f "${repo_root}/docker-compose-ci.yml" logs || true
        exit 1
    fi
    sleep 5
    elapsed=$((elapsed + 5))
done
echo "Ceph cluster is healthy:"
head -3 /tmp/ceph-status.log

echo "Configuring the Ceph Dashboard..."
docker exec "${CEPH_CONTAINER}" ceph mgr module enable dashboard
docker exec "${CEPH_CONTAINER}" ceph config set mgr mgr/dashboard/server_addr 0.0.0.0
docker exec "${CEPH_CONTAINER}" ceph config set mgr mgr/dashboard/ssl true
docker exec "${CEPH_CONTAINER}" ceph dashboard create-self-signed-cert

# The dashboard module is not reliably ready to accept ac-user-create right after being enabled on
# the demo image, and ac-user-create requires the password to come from a file rather than argv.
docker exec "${CEPH_CONTAINER}" sh -c "echo password > /tmp/ceph-dashboard-password.txt"
docker exec "${CEPH_CONTAINER}" ceph dashboard ac-user-create admin -i /tmp/ceph-dashboard-password.txt administrator --force-password

# Bounce the module so it picks up the server_addr/ssl config and the new admin account
# deterministically, instead of relying on whatever state it booted with.
docker exec "${CEPH_CONTAINER}" ceph mgr module disable dashboard
docker exec "${CEPH_CONTAINER}" ceph mgr module enable dashboard

echo "Waiting for the dashboard service to be published by the manager..."
timeout=180
elapsed=0
until docker exec "${CEPH_CONTAINER}" ceph mgr services 2>/dev/null | grep -q "dashboard"; do
    if [ "${elapsed}" -ge "${timeout}" ]; then
        echo "Ceph dashboard did not come up within ${timeout}s" >&2
        docker exec "${CEPH_CONTAINER}" ceph mgr services || true
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

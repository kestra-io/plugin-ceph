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

# Diagnostic (non-fatal): smoke the data-plane operations the integration tests exercise and, on any
# 5xx, dump the mgr dashboard traceback so server-side failures can be root-caused from CI logs.
echo "Data-plane smoke test..."
SMOKE_TOKEN="$(curl -sk -X POST https://localhost:8443/api/auth -H "Accept: application/vnd.ceph.api.v1.0+json" -H "Content-Type: application/json" -d '{"username":"admin","password":"password"}' | sed -n 's/.*"token": *"\([^"]*\)".*/\1/p')"

# Version-aware request: retry once with the version the endpoint names on a 415. Echoes the code.
capi() {
    local method="$1" path="$2" body="${3:-}"
    local base=(-sk -o /tmp/capi.out -w '%{http_code}' -X "${method}" "https://localhost:8443/api${path}" -H "Authorization: Bearer ${SMOKE_TOKEN}")
    [ -n "${body}" ] && base+=(-H "Content-Type: application/json" -d "${body}")
    local code
    code="$(curl "${base[@]}" -H "Accept: application/vnd.ceph.api.v1.0+json")"
    if [ "${code}" = "415" ]; then
        local ver
        ver="$(sed -n "s/.*endpoint is '\\([0-9.]*\\)'.*/\\1/p" /tmp/capi.out)"
        code="$(curl "${base[@]}" -H "Accept: application/vnd.ceph.api.v${ver}+json")"
    fi
    echo "${code}"
}

smoke_fail=0
echo "smoke pool: $(capi POST /pool '{"pool":"smoke-pool","pool_type":"replicated","pg_num":8,"size":2,"application_metadata":["rbd"]}')"
echo "smoke image: $(capi POST /block/image '{"pool_name":"smoke-pool","name":"smoke-img","size":10485760}')"
sleep 2
SNAP_CODE="$(capi POST '/block/image/smoke-pool%2Fsmoke-img/snap' '{"snapshot_name":"smoke-snap","mirrorImageSnapshot":false}')"
echo "smoke snapshot: ${SNAP_CODE} $(cat /tmp/capi.out 2>/dev/null)"
[ "${SNAP_CODE:0:1}" = "5" ] && smoke_fail=1
curl -sk -X POST https://localhost:8443/api/rgw/user -H "Accept: application/vnd.ceph.api.v1.0+json" -H "Authorization: Bearer ${SMOKE_TOKEN}" -H "Content-Type: application/json" -d '{"uid":"smoke","display_name":"smoke"}' >/dev/null 2>&1 || true
capi POST /rgw/bucket '{"bucket":"smoke-bucket","uid":"smoke"}' >/dev/null
sleep 2
DEL_CODE="$(capi DELETE /rgw/bucket/smoke-bucket)"
echo "smoke bucket delete: ${DEL_CODE} $(cat /tmp/capi.out 2>/dev/null)"
[ "${DEL_CODE:0:1}" = "5" ] && smoke_fail=1
if [ "${smoke_fail}" = "1" ]; then
    echo "--- mgr dashboard log tail (traceback) ---"
    docker exec "${CEPH_CONTAINER}" bash -c 'cat /var/log/ceph/ceph-mgr.*.log 2>/dev/null | tail -300' | grep -iE 'traceback|typeerror|error|exception|rgw|bucket|snap' | tail -50 || true
fi

if [ -n "${GITHUB_ENV:-}" ]; then
    {
        echo "CEPH_IT=true"
        echo "CEPH_HOST=localhost"
        echo "CEPH_PORT=8443"
        echo "CEPH_USERNAME=admin"
        echo "CEPH_PASSWORD=password"
    } >> "$GITHUB_ENV"
fi

#!/usr/bin/env bash
# Bring up a single-node Ceph cluster (mon + mgr + one BlueStore OSD + dashboard + RGW) inside the
# quay.io/ceph/ceph base image. Used only by CI on an amd64 runner. The base image is used instead
# of quay.io/ceph/demo because the demo image ships a dashboard with a broken cherrypy dependency.
set -euo pipefail

HOST="$(hostname -s)"
MON_IP="$(hostname -i | awk '{print $1}')"
PUBNET="172.30.0.0/24"
FSID="$(uuidgen)"
DASH_PASS="password"
RGW_NAME="demo"

cat > /etc/ceph/ceph.conf <<EOF
[global]
fsid = $FSID
mon initial members = $HOST
mon host = $MON_IP
public network = $PUBNET
auth cluster required = cephx
auth service required = cephx
auth client required = cephx
osd crush chooseleaf type = 0
osd pool default size = 1
osd pool default min size = 1
mon allow pool delete = true
mon warn on pool no redundancy = false
# Clone from unprotected snapshots (v2), so the snapshot Clone task works without a protect step.
rbd default clone format = 2

[client.rgw.$RGW_NAME]
rgw frontends = beast port=8080
EOF

# keyrings
ceph-authtool --create-keyring /etc/ceph/ceph.mon.keyring --gen-key -n mon. --cap mon 'allow *'
ceph-authtool --create-keyring /etc/ceph/ceph.client.admin.keyring --gen-key -n client.admin \
  --cap mon 'allow *' --cap osd 'allow *' --cap mds 'allow *' --cap mgr 'allow *'
mkdir -p /var/lib/ceph/bootstrap-osd
ceph-authtool --create-keyring /var/lib/ceph/bootstrap-osd/ceph.keyring --gen-key -n client.bootstrap-osd \
  --cap mon 'profile bootstrap-osd' --cap mgr 'allow r'
ceph-authtool /etc/ceph/ceph.mon.keyring --import-keyring /etc/ceph/ceph.client.admin.keyring
ceph-authtool /etc/ceph/ceph.mon.keyring --import-keyring /var/lib/ceph/bootstrap-osd/ceph.keyring

# monmap + mon fs
monmaptool --create --add "$HOST" "$MON_IP" --fsid "$FSID" /etc/ceph/monmap
mkdir -p "/var/lib/ceph/mon/ceph-$HOST"
chown -R ceph:ceph /var/lib/ceph/mon /etc/ceph/ceph.mon.keyring /etc/ceph/monmap
ceph-mon --mkfs -i "$HOST" --monmap /etc/ceph/monmap --keyring /etc/ceph/ceph.mon.keyring \
  --setuser ceph --setgroup ceph
ceph-mon -i "$HOST" --setuser ceph --setgroup ceph
sleep 3

# mgr
mkdir -p "/var/lib/ceph/mgr/ceph-$HOST"
ceph auth get-or-create "mgr.$HOST" mon 'allow profile mgr' osd 'allow *' mds 'allow *' \
  > "/var/lib/ceph/mgr/ceph-$HOST/keyring"
chown -R ceph:ceph /var/lib/ceph/mgr
ceph-mgr -i "$HOST" --setuser ceph --setgroup ceph
sleep 5

# OSD on a loop-backed file (BlueStore, manual deploy)
mkdir -p /var/lib/ceph/osd
dd if=/dev/zero of=/var/lib/ceph/osd/block.img bs=1M count=8192
LOOP="$(losetup -f --show /var/lib/ceph/osd/block.img)"
OSD_UUID="$(uuidgen)"
OSD_SECRET="$(ceph-authtool --gen-print-key)"
OSD_ID="$(echo "{\"cephx_secret\": \"$OSD_SECRET\"}" | ceph osd new "$OSD_UUID" -i - \
  -n client.bootstrap-osd -k /var/lib/ceph/bootstrap-osd/ceph.keyring)"
mkdir -p "/var/lib/ceph/osd/ceph-$OSD_ID"
ln -sf "$LOOP" "/var/lib/ceph/osd/ceph-$OSD_ID/block"
ceph-authtool --create-keyring "/var/lib/ceph/osd/ceph-$OSD_ID/keyring" \
  --name "osd.$OSD_ID" --add-key "$OSD_SECRET"
chown -R ceph:ceph "/var/lib/ceph/osd/ceph-$OSD_ID"
chown ceph:ceph "$LOOP"
ceph-osd -i "$OSD_ID" --mkfs --osd-uuid "$OSD_UUID" --setuser ceph --setgroup ceph
ceph-osd -i "$OSD_ID" --setuser ceph --setgroup ceph
sleep 5

# RGW first, so its system-user credentials are in place before the dashboard activates and needs
# them. radosgw auto-creates its pools on first start.
mkdir -p "/var/lib/ceph/radosgw/ceph-rgw.$RGW_NAME"
ceph auth get-or-create "client.rgw.$RGW_NAME" mon 'allow rw' osd 'allow rwx' mgr 'allow rw' \
  > "/var/lib/ceph/radosgw/ceph-rgw.$RGW_NAME/keyring"
chown -R ceph:ceph "/var/lib/ceph/radosgw"
radosgw -n "client.rgw.$RGW_NAME" --setuser ceph --setgroup ceph
sleep 5

# A system RGW user, whose keys the dashboard uses for all bucket/user admin ops (bucket delete
# needs write caps the auto-provisioned dashboard user does not have).
radosgw-admin user create --uid=dashboard --display-name="dashboard admin" --system \
  --access-key=dashboardaccesskey --secret-key=dashboardsecretkey >/dev/null 2>&1 || true

# dashboard (base image has a working cherrypy, unlike the demo image)
ceph mgr module enable dashboard
ceph config set mgr mgr/dashboard/server_addr 0.0.0.0
ceph config set mgr mgr/dashboard/ssl true
ceph dashboard create-self-signed-cert
printf '%s' "dashboardaccesskey" > /tmp/rgw-ak
printf '%s' "dashboardsecretkey" > /tmp/rgw-sk
ceph dashboard set-rgw-api-access-key -i /tmp/rgw-ak
ceph dashboard set-rgw-api-secret-key -i /tmp/rgw-sk
rm -f /tmp/rgw-ak /tmp/rgw-sk
printf '%s' "$DASH_PASS" > /tmp/dashpass
ceph dashboard ac-user-create admin -i /tmp/dashpass administrator --force-password
rm -f /tmp/dashpass
# Bounce so the dashboard activates with the RGW api credentials set above.
ceph mgr module disable dashboard
ceph mgr module enable dashboard
sleep 6

echo "===== STATUS ====="
ceph -s
ceph mgr services

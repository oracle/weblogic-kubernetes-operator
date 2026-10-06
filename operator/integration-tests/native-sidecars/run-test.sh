#!/bin/bash
# Copyright (c) 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Requires a dedicated namespace watched by an operator built from this branch, the updated CRDs,
# native-sidecar capable Kubernetes, and images available to the node. Creates no operator or CRD.
set -euo pipefail
: "${KUBE_CONTEXT:?Set the explicit test cluster context}"
: "${TEST_NAMESPACE:?Set a dedicated, empty namespace watched by the test operator}"
: "${WLS_IMAGE:?Set a WebLogic image}"
: "${AUX_IMAGE:?Set an auxiliary image containing WDT, without other domain models}"
: "${JAVA_HOME:?Set a JDK with the jfr command}"
if [ ! -x "$JAVA_HOME/bin/jfr" ]; then
  echo "JAVA_HOME must contain an executable bin/jfr." >&2
  exit 1
fi
for tool in kubectl jq; do command -v "$tool" >/dev/null; done
SIDECAR_IMAGE=${SIDECAR_IMAGE:-alpine:latest}
RESULTS_DIR=${RESULTS_DIR:-/tmp/wko-native-sidecars-results}
TEST_DIR=$(cd "$(dirname "$0")" && pwd -P)
mkdir -p "$RESULTS_DIR"

k() { kubectl --context "$KUBE_CONTEXT" -n "$TEST_NAMESPACE" "$@"; }
wait_for() {
  local description=$1
  shift
  local attempt
  for attempt in $(seq 1 180); do
    if "$@"; then return 0; fi
    sleep 5
  done
  echo "Timed out: $description" >&2
  k get pods
  return 1
}
snapshot() {
  k get pods -l weblogic.domainUID=native-sidecars -o json | jq -S '
    [.items[] | select(.metadata.labels["weblogic.serverName"] != null) |
      {name: .metadata.name, uid: .metadata.uid,
       wls: (.status.containerStatuses[]? | select(.name == "weblogic-server") | .containerID)}]
    | sort_by(.name)'
}
servers_ready() {
  k get pods -l weblogic.domainUID=native-sidecars -o json | jq -e '
    [.items[] | select(.metadata.labels["weblogic.serverName"] != null)] |
    length == 3 and all(.[]; any(.status.conditions[]?; .type == "Ready" and .status == "True"))' >/dev/null
}
assert_stable() {
  snapshot > "$RESULTS_DIR/current.json"
  diff -u "$RESULTS_DIR/before.json" "$RESULTS_DIR/current.json"
}
managed_unready() {
  k get pods -l weblogic.domainUID=native-sidecars -o json | jq -e '
    [.items[] | select(.metadata.labels["weblogic.serverName"] // "" | startswith("managed-server-"))] |
    length == 2 and all(.[];
      any(.status.conditions[]?; .type == "Ready" and .status == "False") and
      any(.status.containerStatuses[]?; .name == "weblogic-server" and .ready == true))' >/dev/null
}
managed_rolls_queued() {
  managed_unready && k get pods -l weblogic.clusterName=cluster-1 -o json | jq -e '
    .items | length == 2 and all(.[]; .metadata.annotations["weblogic.awaitingPodRoll"] == "true")' >/dev/null
}
all_replaced() {
  snapshot > "$RESULTS_DIR/current.json"
  servers_ready && jq -e --slurpfile before "$RESULTS_DIR/before.json" '
    . as $now | all($before[0][]; .uid as $uid | all($now[]; .uid != $uid))' \
    "$RESULTS_DIR/current.json" >/dev/null
}
servers_gone() {
  k get pods -l weblogic.domainUID=native-sidecars -o json | jq -e '
    [.items[] | select(.metadata.labels["weblogic.serverName"] != null)] | length == 0' >/dev/null
}
check_archives() {
  local uid
  while read -r uid; do
    k exec archive-reader -- sh -c \
      'test -s "/archives/$1.jfr" && test -f "/archives/$1.regular" && test -f "/archives/$1.completed"' sh "$uid"
  done < <(jq -r '.[].uid' "$RESULTS_DIR/before.json")
}
scaled_down() {
  k get pods -l weblogic.domainUID=native-sidecars -o json | jq -e '
    [.items[] | select(.metadata.labels["weblogic.serverName"] != null)] |
    length == 2 and all(.[]; any(.status.conditions[]?; .type == "Ready" and .status == "True"))' >/dev/null
}

# Do not overwrite an existing domain or other test artifacts.
if k get domain native-sidecars >/dev/null 2>&1 || k get pvc native-sidecars-archives >/dev/null 2>&1; then
  echo "The test namespace already contains native-sidecars artifacts; choose a fresh namespace." >&2
  exit 1
fi
k create secret generic credentials --from-literal=username=weblogic --from-literal=password=Welcome1Welcome1
k create secret generic encryption --from-literal=password=NativeSidecarsEncryption123
k create configmap native-sidecars-model --from-file=model.yaml="$TEST_DIR/model.yaml"
k create configmap native-sidecars-scripts --from-file=uploader.sh="$TEST_DIR/uploader.sh"
k apply -f - <<'EOF'
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: native-sidecars-archives
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 256Mi
---
apiVersion: weblogic.oracle/v1
kind: Cluster
metadata:
  name: native-sidecars-cluster
spec:
  clusterName: cluster-1
  replicas: 2
  maxUnavailable: 1
EOF
cat <<EOF | k apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: archive-reader
spec:
  containers:
  - name: reader
    image: ${SIDECAR_IMAGE}
    imagePullPolicy: IfNotPresent
    command: [sh, -c, 'sleep 86400']
    volumeMounts:
    - name: archives
      mountPath: /archives
  volumes:
  - name: archives
    persistentVolumeClaim:
      claimName: native-sidecars-archives
EOF
k create --dry-run=client -f "$TEST_DIR/domain.yaml" -o json | jq \
  --arg wls "$WLS_IMAGE" --arg aux "$AUX_IMAGE" --arg sidecar "$SIDECAR_IMAGE" '
    .spec.image = $wls |
    .spec.configuration.model.auxiliaryImages[0].image = $aux |
    .spec.serverPod.initContainers[0].image = $sidecar |
    .spec.serverPod.containers[0].image = $sidecar |
    .spec.introspector.serverPod.initContainers[0].image = $sidecar' | k create -f -
wait_for "initial domain including native introspector sidecar" servers_ready
snapshot > "$RESULTS_DIR/before.json"
k get pod native-sidecars-managed-server-1 -o json | jq -e '
  .spec.terminationGracePeriodSeconds == 90 and
  any(.spec.initContainers[]; .name == "jfr-uploader" and .restartPolicy == "Always" and
      .resources.requests.cpu == "10m") and
  any(.spec.containers[]; .name == "regular-companion")' >/dev/null

# Crash both managed sidecars, force make-right through a specification update, and prove isolation.
for server in 1 2; do
  k exec "native-sidecars-managed-server-$server" -c weblogic-server -- touch /recordings/fail
done
wait_for "native sidecar outages with healthy WebLogic containers" managed_unready
k patch domain native-sidecars --type merge -p \
  '{"spec":{"serverPod":{"terminationGracePeriodSeconds":150,"annotations":{"test.example.com/reconcile":"1"}}}}'
for attempt in $(seq 1 6); do sleep 5; assert_stable; done
k get pod native-sidecars-managed-server-1 -o json | jq -e \
  '.spec.terminationGracePeriodSeconds == 90 and any(.status.initContainerStatuses[]; .name == "jfr-uploader" and .restartCount > 0)' >/dev/null
echo "PASS: native sidecar crashes and make-right preserve all Pod UIDs and WebLogic container IDs"

# A requested roll must wait rather than immediately deleting all sidecar-unready managed servers.
k patch domain native-sidecars --type merge -p '{"spec":{"restartVersion":"native-sidecar-test"}}'
jq '[.[] | select(.name | contains("managed-server"))]' "$RESULTS_DIR/before.json" \
  > "$RESULTS_DIR/managed-before.json"
# RestartVersion can cause re-introspection and an admin roll before the managed roll is queued.
wait_for "managed rolls queued during native sidecar failure" managed_rolls_queued
for attempt in $(seq 1 6); do
  sleep 5
  managed_rolls_queued
  snapshot | jq '[.[] | select(.name | contains("managed-server"))]' > "$RESULTS_DIR/managed-current.json"
  diff -u "$RESULTS_DIR/managed-before.json" "$RESULTS_DIR/managed-current.json"
done
echo "PASS: queued managed rolls preserve Pod and WebLogic identities throughout the sidecar outage"
for server in 1 2; do
  k exec "native-sidecars-managed-server-$server" -c weblogic-server -- rm /recordings/fail
done
wait_for "controlled roll after sidecar recovery" all_replaced
check_archives
k get pod native-sidecars-managed-server-1 -o json | jq -e \
  '.spec.terminationGracePeriodSeconds == 150' >/dev/null
echo "PASS: rolling waits for native sidecar recovery; final JFRs survive the controlled roll"

snapshot > "$RESULTS_DIR/before.json"
k patch cluster native-sidecars-cluster --type merge -p '{"spec":{"replicas":1}}'
wait_for "scale-down" scaled_down
snapshot > "$RESULTS_DIR/after-scale-down.json"
jq --slurpfile after "$RESULTS_DIR/after-scale-down.json" '
  [.[] | . as $old | select(all($after[0][]; .uid != $old.uid))]' "$RESULTS_DIR/before.json" \
  > "$RESULTS_DIR/scaled-down.json"
cp "$RESULTS_DIR/before.json" "$RESULTS_DIR/before-scale-down.json"
cp "$RESULTS_DIR/scaled-down.json" "$RESULTS_DIR/before.json"
check_archives
jq -e --slurpfile before "$RESULTS_DIR/before-scale-down.json" '
  all(.[]; . as $now | any($before[0][]; . == $now))' "$RESULTS_DIR/after-scale-down.json" >/dev/null
k patch cluster native-sidecars-cluster --type merge -p '{"spec":{"replicas":2}}'
wait_for "scale-up" servers_ready
echo "PASS: scale-down drains the native sidecar and preserves the remaining WebLogic containers"

snapshot > "$RESULTS_DIR/before.json"
k patch domain native-sidecars --type merge -p '{"spec":{"serverStartPolicy":"Never"}}'
wait_for "domain shutdown" servers_gone
check_archives
k cp archive-reader:/archives "$RESULTS_DIR/archives"
for recording in "$RESULTS_DIR"/archives/*.jfr; do
  "$JAVA_HOME/bin/jfr" summary "$recording" > "$recording.summary"
  "$JAVA_HOME/bin/jfr" print --events jdk.JVMInformation "$recording" > "$recording.jvm"
  grep -F 'weblogic.Server' "$recording.jvm" >/dev/null
done
echo "PASS: native cleanup archives valid final JFRs after WebLogic and regular companion shutdown"
echo "Results: $RESULTS_DIR (test resources retained for inspection)"

# Native sidecar lifecycle test

Run against a dedicated test cluster with the operator and CRDs built from this branch. The operator must watch
an empty namespace supplied by `TEST_NAMESPACE`. Kubernetes must support native sidecars. A default storage class
and scheduling all test Pods on the same node are required for the shared ReadWriteOnce archive volume.

Provide a WebLogic image and an auxiliary image containing WDT without existing domain models; the test supplies
its own model in a ConfigMap. Images must be available to the cluster. Requires Bash, kubectl, jq, and a JDK with
the `jfr` command. The test creates a two-member cluster, an administration server, secrets, ConfigMaps, and a PVC.
It leaves those resources in the dedicated namespace for inspection; delete that namespace to clean up.

```bash
KUBE_CONTEXT=kind-wko-native-sidecars \
TEST_NAMESPACE=native-sidecars-test \
WLS_IMAGE=your-weblogic-image:tag \
AUX_IMAGE=your-wdt-only-image:tag \
JAVA_HOME=/path/to/jdk \
bash operator/integration-tests/native-sidecars/run-test.sh
```

The test checks native introspector Job completion, independent native resources, restart isolation during
make-right, grace changes without rolling existing Pods, cluster rolling admission during sidecar failure,
recovery and a controlled roll, scale-down and scale-up, and full domain shutdown. It records Pod UIDs and
WebLogic container IDs.
WebLogic produces an actual JFR with `dumponexit`; a native uploader waits five seconds after termination and
copies the final recording to the archive PVC. A regular companion writes its final marker at shutdown, and the
test verifies that marker and validates each archived JFR. The PVC stands in for the destination object store;
this test does not validate object store credentials, network retries, or upload protocols.

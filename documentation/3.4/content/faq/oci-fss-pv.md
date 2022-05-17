---
title: "Using OCI File Storage (FSS) for persistent volumes"
date: 2020-02-12T12:12:12-05:00
draft: false
weight: 7
description: "If you are running your Kubernetes cluster on Oracle Container Engine
for Kubernetes (OKE), and you use OCI File Storage (FSS)
for persistent volumes to store the WebLogic domain home, then the file system
handling, as demonstrated in the operator persistent volume sample, will require
an update to properly initialize the file ownership on the persistent volume
when the domain is initially created."
---

If you are running your Kubernetes cluster on Oracle Container Engine
for Kubernetes (commonly known as OKE), and you use OCI File Storage (FSS)
for persistent volumes to store the WebLogic domain home, then the file system
handling, as demonstrated in the operator persistent volume sample, will require
an update to properly initialize the file ownership on the persistent volume
when the domain is initially created.

{{% notice note %}}
File permission handling on persistent volumes can differ between
cloud providers and even with the underlying storage handling on
Linux based systems. These instructions provide one option to
update file ownership used by the standard Oracle images where
UID `1000` and GID `1000` typically represent the `oracle` or `opc` user.
For more information on persistent volume handling,
see [Persistent storage]({{< relref "/userguide/managing-domains/persistent-storage/_index.md" >}}).
{{% /notice %}}


#### Failure during domain creation with persistent volume sample

The existing sample for [creation of a domain home on persistent volume](https://github.com/oracle/weblogic-kubernetes-operator/tree/main/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv)
uses a Kubernetes Job to create the domain. The sample uses an
`initContainers` section to change the file ownership which will
fail for OCI FSS created volumes used with an OKE cluster.

The OCI FSS volume contains some files that are not modifiable thus
causing the Kubernetes Job to fail. The failure is seen in the
description of the Kubernetes Job pod:
```shell
$ kubectl describe -n domain1-ns pod domain1-create-weblogic-sample-domain-job-wdkvs
```
```
Init Containers:
  fix-pvc-owner:
    Container ID:  docker://7051b6abdc296c76e937246df03d157926f2f7477e63b6af3bf65f6ae1ceddee
    Image:         container-registry.oracle.com/middleware/weblogic:12.2.1.3
    Image ID:      docker-pullable://container-registry.oracle.com/middleware/weblogic@sha256:47dfd4fdf6b56210a6c49021b57dc2a6f2b0d3b3cfcd253af7a75ff6e7421498
    Port:          <none>
    Host Port:     <none>
    Command:
      sh
      -c
      chown -R 1000:0 /shared
    State:          Terminated
      Reason:       Error
      Exit Code:    1
      Started:      Wed, 12 Feb 2020 18:28:53 +0000
      Finished:     Wed, 12 Feb 2020 18:28:53 +0000
    Ready:          False
    Restart Count:  0
    Environment:    <none>
```

#### Updating the domain on persistent volume sample
In the following snippet of the [create-domain-job-template.yaml](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain-job-template.yaml),
you can see the updated `command` for the init container:
```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: %DOMAIN_UID%-create-weblogic-sample-domain-job
  namespace: %NAMESPACE%
spec:
  template:
    metadata:
    ...
    spec:
      restartPolicy: Never
      initContainers:
        - name: fix-pvc-owner
          image: %WEBLOGIC_IMAGE%
          command: ["sh", "-c", "chown 1000:0 %DOMAIN_ROOT_DIR%/. && find %DOMAIN_ROOT_DIR%/. -maxdepth 1 ! -name '.snapshot' ! -name '.' -print0 | xargs -r -0 chown -R 1000:0"]
          volumeMounts:
          - name: weblogic-sample-domain-storage-volume
            mountPath: %DOMAIN_ROOT_DIR%
          securityContext:
            runAsUser: 0
            runAsGroup: 0
      containers:
        - name: create-weblogic-sample-domain-job
          image: %WEBLOGIC_IMAGE%
...
```
Use this new `command` in your copy of this template file. This will result in
the ownership being updated for the expected files only, before the WebLogic
domain is created on the persistent volume.

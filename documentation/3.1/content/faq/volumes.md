---
title: "Providing access to a PersistentVolumeClaim"
date: 2020-01-07T15:02:28-05:00
draft: false
weight: 9
description: "I need to provide an instance with access to a PersistentVolumeClaim."
---
> I need to provide an instance with access to a PersistentVolumeClaim.

Some applications need access to a file, either to read data or to provide additional logging beyond what is
built into the operator. One common way of doing that within Kubernetes is to create a
[PersistentVolumeClaim](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#persistentvolumeclaims) (PVC) and map
it to a file. The domain configuration can then be used to provide access to the claim across the domain,
within a single cluster, or for a single server.
In each case, the access is configured within the ``serverPod`` element of the configuration of the
desired scope.

For example, here is
a read-only `PersistentVolumeClaim` specification. Note that its name is `myclaim`.

```
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: myclaim
spec:
  accessModes:
    - ReadOnlyMany
  volumeMode: Filesystem
  resources:
    requests:
      storage: 8Gi
  storageClassName: slow
```

To provide access to this claim to all Managed Servers in the `cluster-1` cluster, specify the following
in your [Domain](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md):

```
  clusters:
  - clusterName: cluster-1
    serverPod:
      volumes:
      - name: my-volume-1
        persistentVolumeClaim:
          claimName: myclaim
      volumeMounts:
      - name: my-volume-1
        mountPath: /weblogic-operator/my/volume1

```
Note the use of the claim name in the `claimName` field of the `volume` entry. Both a `volume` and a
`volumeMount` entry are required, and must have the same name. The `volume` entry associates that name with the claim,
while the `volumeMount` entry defines the path to it that the application can use to access the file.

**NOTE**: If the PVC is mapped either across the domain or to a cluster,
its access mode must be either `ReadOnlyMany` or `ReadWriteMany`.

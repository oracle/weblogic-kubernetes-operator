---
title: "Use OCI File Storage (FSS) for persistent volumes"
date: 2020-02-12T12:12:12-05:00
draft: false
weight: 2
description: "If you are running your Kubernetes cluster on Oracle Container Engine
for Kubernetes (OKE), and you use Oracle Cloud Infrastructure File Storage (FSS)
for persistent volumes to store the WebLogic domain home, then the file system
handling, as demonstrated in the operator persistent volume sample, will require
an update to properly initialize the file ownership on the persistent volume
when the domain is initially created."
---

Oracle recommends using Oracle Cloud Infrastructure File Storage (FSS) for persistent volumes to store
the WebLogic domain home or log files when running the Kubernetes cluster on Oracle Container Engine
for Kubernetes (OKE). When using the FSS with OKE for domain home or log files,  the file system
handling will require an update to properly initialize the file ownership on the persistent volume
when the domain is initially created.  

{{% notice note %}}
File permission handling on persistent volumes can differ between
cloud providers and even with the underlying storage handling on
Linux-based systems.
The operator requires permission to create directories on the persistent volume under the shared mount path.
The following instructions provide an option to update the file ownership and permissions.
{{% /notice %}}


#### Updating the permissions of shared directory on persistent storage
The operator provides a utility script, `pv-pvc-helper.sh`, as part of the lifecycle scripts to change the ownership and permissions of the shared directory on the persistent storage.

This script launches a Pod and mounts the specified PVC in the Pod containers at the specified mount path. You can then `exec` in the Pod and manually change the permissions or ownership.

See the `pv-pvc-helper.sh` in "Examine, change permissions or delete PV contents" section in the [README](https://github.com/oracle/weblogic-kubernetes-operator/tree/{{< latestMinorVersion >}}/kubernetes/samples/scripts/domain-lifecycle/README.md) file for the script details.

For example, run the following command to create the Pod.

```
$ pv-pvc-helper.sh -n sample-domain1-ns -r -c sample-domain1-weblogic-sample-pvc -m /shared
```

The script will create a Pod with the following specifications.
```
apiVersion: v1
kind: Pod
metadata:
  name: pvhelper
  namespace: sample-domain1-ns
spec:
  containers:
  - args:
    - sleep
    - infinity
    image: ghcr.io/oracle/oraclelinux:8-slim
    name: pvhelper
    volumeMounts:
    - name: pv-volume
      mountPath: /shared
  volumes:
  - name: pv-volume
    persistentVolumeClaim:
      claimName: wko-domain-on-pv-pvc
```

Run the following command to `exec` into the Pod.
```
$ kubectl -n sample-domain1-ns exec -it pvhelper -- /bin/sh
```

After you get a shell to the running Pod container, change the directory to `/shared`, and you can change the ownership or permissions using the appropriate `chown` or `chmod` commands. For example,

```
$ chown 1000:0 /shared/. && find /shared/. -maxdepth 1 ! -name '.snapshot' ! -name '.' -print0 | xargs -r -0 chown -R 1000:0
```

#### References

- [Provisioning PVCs on the File Storage Service (FSS)](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingpersistentvolumeclaim_Provisioning_PVCs_on_FSS.htm#Provisioning_Persistent_Volume_Claims_on_the_FileStorageService) in the OCI documentation.
- [Setting up storage for Kubernetes clusters](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingpersistentvolumeclaim.htm) in the OCI documentation.

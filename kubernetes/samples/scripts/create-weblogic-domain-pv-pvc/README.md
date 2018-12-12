# Sample persistent volume and persistent volume claim

The sample scripts demonstrate the creation of a Kubernetes persistent volume (PV) and persistent volume claim (PVC), which can then be used in a domain resource as a persistent storage for the WebLogic domain home or log files.

A PV and PVC can be shared by multiple WebLogic domains or dedicated to a particular domain.

## Prerequisites

Please read this guide before proceeding.
* [Persistent Volumes](../../../../site/persistent-volumes.md)

## Using the scripts to create a PV and PVC  

Prior to running the `create-pv-pvc.sh` script, make a copy of the `create-pv-pvc-inputs.yaml` file, and uncomment and explicitly configure the `weblogicDomainStoragePath` property in the inputs file.

Run the create script, pointing it at your inputs file and an output directory:

```
  ./create-pv-pvc.sh \
  -i create-pv-pvc-inputs.yaml \
  -o /path/to/output-directory
```

The `create-pv-pvc.sh` script will create a subdirectory `pv-pvcs` under the given `/path/to/output-directory` directory. By default, the script generates two YAML files, namely `weblogic-sample-pv.yaml` and `weblogic-sample-pvc.yaml`, in the `/path/to/output-directory` `directory/pv-pvcs`. These two YAML files can be used to create the Kubernetes resources using the `kubectl create -f` command.

```
  kubectl create -f weblogic-sample-pv.yaml
  kubectl create -f weblogic-sample-pvc.yaml

```

As a convenience, the script can optionally create the PV and PVC resources using the `-e` option.

The usage of the create script is as follows:

```
$ sh create-pv-pvc.sh -h
usage: create-pv-pvc.sh -i file -o dir [-e] [-h]
  -i Parameter inputs file, must be specified.
  -o Output directory for the generated yaml files, must be specified.
  -e Also create the Kubernetes objects using the generated yaml files
  -h Help
```

If you copy the sample scripts to a different location, make sure that you copy everything in the `<weblogic-kubernetes-operator-project>/kubernetes/samples/scripts` directory together into the target directory, maintaining the original directory hierarchy.

## Configuration parameters

The PV and PVC creation inputs can be customized by editing the `create-pv-pvc-inputs.yaml` file.

| Parameter | Definition | Default |
| --- | --- | --- |
| `domainUID` | ID of the domain resource to which the generated PV and PVC will be dedicated. Leave it empty if the PV and PVC are going to be shared by multiple domains. | no default |
| `namespace` | Kubernetes namespace to create the PVC. | `default` |
| `baseName` | Base name of the PV and PVC. The generated PV and PVC will be `<baseName>-pv` and `<baseName>-pvc` respectively. | `weblogic-sample` |
| `weblogicDomainStoragePath` | Physical path of the storage for the PV. | no default |
| `weblogicDomainStorageReclaimPolicy` | Kubernetes PVC policy for the persistent storage. The valid values are: `Retain`, `Delete`, and `Recycle`. | `Retain` |
| `weblogicDomainStorageSize` | Total storage allocated for the PVC. | `10Gi` |
| `weblogicDomainStorageType` | Type of storage. Legal values are `NFS` and `HOST_PATH`. If using `NFS`, `weblogicDomainStorageNFSServer` must be specified. | `HOST_PATH` |
| `weblogicDomainStorageNFSServer`| Name of the IP address of the NFS server. This setting only applies if `weblogicDomainStorateType` is `NFS`.  | no default |

## Shared versus dedicated PVC

By default, the `domainUID` is left empty in the inputs file so that the generated PV and PVC can be shared by multiple domain resources in the same Kubernetes namespaces.

For the use cases where a dedicated PV and PVC is desired for a particular domain, the `domainUID` can be set, which will cause the generated PV and PVC to be associated with the specified `domainUID`. In the per domain PV and PVC case, the names of the generated YAML files and the Kubernetes PV and PVC objects are decorated with the `domainUID`, and the PV and PVC objects are also labeled with the `domainUID`.

## Verify the results

The create script will verify that the PV and PVC were created, and will report a failure if there was any error.  However, it may be desirable to manually verify the PV and PVC, even if just to gain familiarity with the various Kubernetes objects that were created by the script.

### Generated YAML files with the default inputs

The content of the generated `weblogic-sample-pvc.yaml`:

```
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

kind: PersistentVolumeClaim
apiVersion: v1
metadata:
  name: weblogic-sample-pvc
  namespace: default
  labels:
    weblogic.resourceVersion: domain-v2

  storageClassName: weblogic-sample-storage-class
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 10Gi
```

The content of the generated `weblogic-sample-pv.yaml`:
```
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

apiVersion: v1
kind: PersistentVolume
metadata:
  name: weblogic-sample-pv
  labels:
    weblogic.resourceVersion: domain-v2
    # weblogic.domainUID:
spec:
  storageClassName: weblogic-sample-storage-class
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteMany
  # Valid values are Retain, Delete or Recycle
  persistentVolumeReclaimPolicy: Retain
  hostPath:
  # nfs:
    # server: %SAMPLE_STORAGE_NFS_SERVER%
    path: "/scratch/k8s_dir"

```

### Generated YAML files for dedicated PV and PVC

The content of the generated `domain1-weblogic-sample-pvc.yaml` when `domainUID` is set to `domain1`:

```
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

kind: PersistentVolumeClaim
apiVersion: v1
metadata:
  name: domain1-weblogic-sample-pvc
  namespace: default
  labels:
    weblogic.resourceVersion: domain-v2
    weblogic.domainUID: domain1
spec:
  storageClassName: domain1-weblogic-sample-storage-class
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 10Gi
```

The content of the generated `domain1-weblogic-sample-pv.yaml` when `domainUID` is set to `domain1`:
```
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

apiVersion: v1
kind: PersistentVolume
metadata:
  name: domain1-weblogic-sample-pv
  labels:
    weblogic.resourceVersion: domain-v2
    weblogic.domainUID: domain1
spec:
  storageClassName: domain1-weblogic-sample-storage-class
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteMany
  # Valid values are Retain, Delete or Recycle
  persistentVolumeReclaimPolicy: Retain
  hostPath:
  # nfs:
    # server: %SAMPLE_STORAGE_NFS_SERVER%
    path: "/scratch/k8s_dir"
```

### Verify the PV and PVC objects

```
$ kubectl describe pv weblogic-sample-pv
Name:            weblogic-sample-pv
Labels:          weblogic.resourceVersion=domain-v2
Annotations:     pv.kubernetes.io/bound-by-controller=yes
StorageClass:    weblogic-sample-storage-class
Status:          Bound
Claim:           default/weblogic-sample-pvc
Reclaim Policy:  Retain
Access Modes:    RWX
Capacity:        10Gi
Message:         
Source:
    Type:          HostPath (bare host directory volume)
    Path:          /scratch/k8s_dir
    HostPathType:  
Events:            <none>

```

```
$ kubectl describe pvc weblogic-sample-pvc
Name:          weblogic-sample-pvc
Namespace:     default
StorageClass:  weblogic-sample-storage-class
Status:        Bound
Volume:        weblogic-sample-pv
Labels:        weblogic.resourceVersion=domain-v2
Annotations:   pv.kubernetes.io/bind-completed=yes
               pv.kubernetes.io/bound-by-controller=yes
Finalizers:    []
Capacity:      10Gi
Access Modes:  RWX
Events:        <none>

```

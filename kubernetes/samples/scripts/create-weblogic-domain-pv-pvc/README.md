# Sample Persistent Volume and Persistent Volume Claim

The sample scripts demonstrate the creation of a Kubernetes persistent volume (PV) and persistent volume claim (PVC), which can then be used in a domain custom resource as a persistent storage for the WebLogic domain home or log files.

A PV/PVC can be shared by multiple WebLogic domains or dedicated to a particular domain.

## Prerequisites

The following prerequisites must be handled prior to running the create script:
* Create a Kubernetes namespace for the persistent volume claim unless the intention is to use the default namespace. Note that a PVC has to be in the same namespace as the domain resource that uses it.
* Make sure that the host directory that will be used as the persistent volume already exists and has the appropriate file permissions set.

## Using the scripts to create a PV and PVC  

Prior to running the `create-pv-pvc.sh` script, make a copy of the `create-pv-pvc-inputs.yaml` file, and uncommented and explicitly configure the `weblogicDomainStoragePath` property in the inputs file.

Run the create script, pointing it at your inputs file and an output directory:

```
  ./create-pv-pvc.sh \
  -i create-pv-pvc-inputs.yaml \
  -o /path/to/output-directory
```

The `create-pv-pvc.sh` script will create a subdirectory `pv-pvcs` under the given `/path/to/output-directory` directory. By default, the script generates two yaml files, namely `weblogic-sample-pv.yaml` and `weblogic-sample-pvc.yaml`, in the `/path/to/output-directory` directory/pv-pvcs`. These two yaml files can be used to create the Kubernetes resources using the `kubectl create -f` command.

```
  kubectl create -f weblogic-sample-pv.yaml
  kubectl create -f weblogic-sample-pvc.yaml

```

As a convenience, the script can optionally create the PV and PVC resources as well using the `-e` option.

The usage of the create script is as follows.

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
| `domainUID` | ID of the domain resource that the generated PV and PVC will be dedicated to. Leave it empty if the PV and PVC are going to be shared by multiple domains. | no default |
| `namespace` | Kubernetes namespace to create the PVC. | `default` |
| `baseName` | Base name of the PV and PVC. The generated PV and PVC will be <baseName>-pv and <baseName>-pvc respectively | `weblogic-sample` |
| `weblogicDomainStoragePath` | Physical path of the storage for the PV. | no default |
| `weblogicDomainStorageReclaimPolicy` | Kubernetes PVC policy for the persistent storage. The valid values are: 'Retain', 'Delete', and 'Recycle' | `Retain` |
| `weblogicDomainStorageSize` | Total storage allocated for the PVC. | `10Gi` |
| `weblogicDomainStorageType` | Type of storage. Legal values are `NFS` and `HOST_PATH`. If using 'NFS', weblogicDomainStorageNFSServer must be specified | `HOST_PATH` |
| `weblogicDomainStorageNFSServer`| Name of the IP address of the NFS server. This setting only applies if weblogicDomainStorateType is NFS  | no default |

## Shared vs dedicated PVC

By default, the `domainUID` is left empty in the inputs file so that the generated PV and PVC can be shared by multiple domain resources in the same Kubernetes namespaces. 

For the use cases where dedicated PV and PVC are desired for a particular domain, the `domainUID` can be set, which will cause the generated PV and PVC associated with the specified `domainUID`. In the per domain PV and PVC case, the names of the generated yaml files and the Kubernetes PV and PVC objects are all decorated with the `domainUID`, and the PV and PVC objects are also labeled with the `domainUID`.

## Common problems

This section provides details of common problems that occur while running the script  and how to resolve them.

### Persistent volume provider not configured correctly

Possibly the most common problem experienced during testing was incorrect configuration of the persistent volume provider.  The persistent volume must be accessible to all Kubernetes nodes, and must be able to be mounted as Read/Write/Many.  If this is not the case, the PV/PVC creation will fail.

The simplest case is where the `HOST_PATH` provider is used.  This can be either with one Kubernetes node, or with the `HOST_PATH` residing in shared storage available at the same location on every node (for example, on an NFS mount).  In this case, the path used for the persistent volume must have its permission bits set to 777.

## Verify the results 

The create script will verify that the PV and PVC was created, and will report failure if there was any error.  However, it may be desirable to manually verify the PV and PVC, even if just to gain familiarity with the various Kubernetes objects that were created by the script.

### Generated yaml files with the default inputs

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
    weblogic.resourceVersion: domain-v1

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
    weblogic.resourceVersion: domain-v1
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

### Generated yaml files for dedicated PV and PVC

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
    weblogic.resourceVersion: domain-v1
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
    weblogic.resourceVersion: domain-v1
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

To confirm that the PV and PVC were created, use these commands:

```
kubectl describe pv
kubectl describe pvc -n NAMESPACE
```

Replace `NAMESPACE` with the namespace that the PVC was created in.  The output of this command will provide details of the PV, as shown in this example:

```
$ kubectl describe pv weblogic-sample-pv
Name:            weblogic-sample-pv
Labels:          weblogic.resourceVersion=domain-v1
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
Labels:        weblogic.resourceVersion=domain-v1
Annotations:   pv.kubernetes.io/bind-completed=yes
               pv.kubernetes.io/bound-by-controller=yes
Finalizers:    []
Capacity:      10Gi
Access Modes:  RWX
Events:        <none>

```


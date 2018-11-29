
# Persistent Volumes

This guide outlines how to set up a Kubernetes persistent volume (PV) and persistent volume claim (PVC), which can then be used in a domain custom resource as a persistent storage for the WebLogic domain home or log files. A PV and PVC can be shared by multiple WebLogic domains or dedicated to a particular domain.

## Prerequisites

The following prerequisites must be fulfilled before proceeding with the creation of the persistent volume.
* Create a Kubernetes namespace for the persistent volume claim unless the intention is to use the default namespace. Note that a PVC has to be in the same namespace as the domain resource that uses it.
* Make sure that the host directory that will be used as the persistent volume already exists and has the appropriate file permissions set.

# Persistent volume YAML files

For sample YAML templates, please refer to this example.
* [Persistent Volumes example](../kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/README.md)

## Naming conventions

Persistent volumes and claims and their properties are described in YAML files. For each PV, you should create one PV YAML file and one PVC YAML file. In the example above, you will find 2 YAML templates, one for the PV and one for the PVC. The example suggest that you name the PV and PVC [domainUID]-[baseName]-pv and [domainUID]-[baseName]-pvc respectively, where domainUID is the ID of the domain resource to which the generated PV and PVC will be dedicated, and baseName is the base name of the PV and PVC.

## Storage locations
PVs can point to different storage locations, for example NFS server storage or local path. Please read the [Kubernetes documentation](https://kubernetes.io/docs/concepts/storage/persistent-volumes/) regarding on how to configure this.

# Kubernetes resources

After you have written your YAML files, please use them to create the PV by creating Kubernetes resources using the `kubectl create -f` command.

```
  kubectl create -f pv.yaml
  kubectl create -f pvc.yaml

```

## Shared versus dedicated PVC

By default, the `domainUID` is left empty in the inputs file so that the generated PV and PVC can be shared by multiple domain resources in the same Kubernetes namespaces.

For the use cases where a dedicated PV and PVC is desired for a particular domain, the `domainUID` can be set, which will cause the generated PV and PVC to be associated with the specified `domainUID`. In the per domain PV and PVC case, the names of the generated YAML files and the Kubernetes PV and PVC objects are decorated with the `domainUID`, and the PV and PVC objects are also labeled with the `domainUID`.

# Common problems

This section provides details of common problems that might occur while running the script and how to resolve them.

### Persistent volume provider not configured correctly

Possibly the most common problem experienced during testing was the incorrect configuration of the persistent volume provider.  The persistent volume must be accessible to all Kubernetes nodes, and must be able to be mounted as Read/Write/Many.  If this is not the case, the PV/PVC creation will fail.

The simplest case is where the `HOST_PATH` provider is used.  This can be either with one Kubernetes node, or with the `HOST_PATH` residing in shared storage available at the same location on every node (for example, on an NFS mount).  In this case, the path used for the persistent volume must have its permission bits set to 777.

# Verify the results

To confirm that the PV and PVC were created, use these commands:

```
kubectl describe pv [PV name]
kubectl describe pvc -n NAMESPACE [PVC name]
```

Replace `NAMESPACE` with the namespace that the PVC was created in.

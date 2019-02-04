

# Persistent volumes

In this guide, we outline how to set up a Kubernetes persistent volume and persistent volume claim which can be used as storage for WebLogic domain homes and log files. A persistent volume can be shared by multiple WebLogic domains or dedicated to a particular domain.

## Prerequisites

The following prerequisites must be fulfilled before proceeding with the creation of the volume.
* Create a Kubernetes namespace for the persistent volume claim unless the intention is to use the default namespace. Note that a persistent volume claim has to be in the same namespace as the domain resource that uses it.
* Make sure that all the servers in the WebLogic domain are able to reach the storage location.
* Make sure that the host directory that will be used, already exists and has the appropriate file permissions set.

## Storage locations
Persistent volumes can point to different storage locations, for example NFS servers or a local directory path. The list of available options is listed in the [Kubernetes documentation](https://kubernetes.io/docs/concepts/storage/persistent-volumes/).

**Note regarding HostPath**:
In a single-node Kubernetes cluster, such as may be used for testing or proof of concept activities, `HOST_PATH` provides the simplest configuration.  In a multinode Kubernetes cluster, a `HOST_PATH` that is located on shared storage mounted by all nodes in the Kubernetes cluster is the simplest configuration.  If nodes do not have shared storage, then NFS is probably the most widely available option.  There are other options listed in the referenced table.

The persistent volume for the domain must be created using the appropriate tools before running the script to create the domain.  In the simplest case, namely the `HOST_PATH` provider, this means creating a directory on the Kubernetes master and ensuring that it has the correct permissions:

```
$ mkdir -m 777 -p /path/to/domain1PersistentVolume
```

**Note regarding NFS**:
In the current GA version, the OCI Container Engine for Kubernetes supports network block storage that can be shared across nodes with access permission RWOnce (meaning that only one can write, others can read only). At this time, the WebLogic on Kubernetes domain created by the WebLogic Server Kubernetes Operator, requires a shared file system to store the WebLogic domain configuration, which MUST be accessible from all the pods across the nodes. As a workaround, you need to install an NFS server on one node and share the file system across all the nodes.

Currently, we recommend that you use NFS version 3.0 for running WebLogic Server on OCI Container Engine for Kubernetes. During certification, we found that when using NFS 4.0, the servers in the WebLogic domain went into a failed state intermittently. Because multiple threads use NFS (default store, diagnostics store, Node Manager, logging, and `domain_home`), there are issues when accessing the file store. These issues are removed by changing the NFS to version 3.0.

# YAML files

Persistent volumes and claims are described in YAML files. For each persistent volume, you should create one persistent volume YAML file and one persistent volume claim YAML file. In the example below, you will find two YAML templates, one for the volume and one for the claim. As stated above, they either can be dedicated to a specific domain, or shared across multiple domains. For the use cases where a volume will be dedicated to a particular domain, it is a best practice to label it with `weblogic.domainUID=[domain name]`. This makes it easy to search for, and clean up resources associated with that particular domain.

For sample YAML templates, refer to the [Persistent volumes example](../kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/README.md).

# Kubernetes resources

After you have written your YAML files, you use them to create the persistent volume by creating Kubernetes resources using the `kubectl create -f` command.

```
$ kubectl create -f pv.yaml
$ kubectl create -f pvc.yaml

```

# Verify the results

To confirm that the persistent volume was created, use these commands:

```
$ kubectl describe pv [persistent volume name]
$ kubectl describe pvc -n NAMESPACE [persistent volume claim name]
```

# Common problems

This section provides details of common problems that might occur while running the script and how to resolve them.

### Persistent volume provider not configured correctly

Possibly the most common problem experienced during testing was the incorrect configuration of the persistent volume provider. The persistent volume must be accessible to all Kubernetes nodes, and must be able to be mounted as Read/Write/Many. If this is not the case, the persistent volume creation will fail.

The simplest case is where the `HOST_PATH` provider is used. This can be either with one Kubernetes node, or with the `HOST_PATH` residing in shared storage available at the same location on every node (for example, on an NFS mount). In this case, the path used for the persistent volume must have its permission bits set to 777.

# Further reading

* See the blog, "[How to run WebLogic clusters on the Oracle Cloud Infrastructure Container Engine for Kubernetes](https://blogs.oracle.com/weblogicserver/how-to-run-weblogic-clusters-on-the-oracle-cloud-infrastructure-container-engine-for-kubernetes)."

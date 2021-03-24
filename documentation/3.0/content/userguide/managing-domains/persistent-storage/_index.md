+++
title = "Persistent storage"
date = 2019-02-23T16:45:09-05:00
weight = 3
pre = "<b> </b>"
+++

This document outlines how to set up a Kubernetes PersistentVolume and PersistentVolumeClaim which can be used as storage for WebLogic domain homes and log files. A PersistentVolume can be shared by multiple WebLogic domains or dedicated to a particular domain.

#### Prerequisites

The following prerequisites must be fulfilled before proceeding with the creation of the volume:

* Create a Kubernetes Namespace for the PersistentVolumeClaim unless the intention is to use the default namespace. Note that a PersistentVolumeClaim has to be in the same namespace as the Domain that uses it.
* Make sure that all the servers in the WebLogic domain are able to reach the storage location.
* Make sure that the host directory that will be used, already exists and has the appropriate file permissions set.

### Storage locations
PersistentVolumes can point to different storage locations, for example NFS servers or a local directory path. The list of available options is listed in the [Kubernetes documentation](https://kubernetes.io/docs/concepts/storage/persistent-volumes/).

**Note regarding HostPath**: In a single-node Kubernetes cluster, such as may be used for testing or proof of concept activities, `HOST_PATH` provides the simplest configuration.  In a multinode Kubernetes cluster, a `HOST_PATH` that is located on shared storage mounted by all nodes in the Kubernetes cluster is the simplest configuration.  If nodes do not have shared storage, then NFS is probably the most widely available option.  There are other options listed in the referenced table.

The PersistentVolume for the domain must be created using the appropriate tools before running the script to create the domain.  In the simplest case, namely the `HOST_PATH` provider, this means creating a directory on the Kubernetes master and ensuring that it has the correct permissions:

```bash
$ mkdir -m 777 -p /path/to/domain1PersistentVolume
```

**Note regarding NFS**: In the current GA version, the OCI Container Engine for Kubernetes supports network block storage that can be shared across nodes with access permission RWOnce (meaning that only one can write, others can read only). At this time, the WebLogic on Kubernetes domain created by the WebLogic Server Kubernetes Operator, requires a shared file system to store the WebLogic domain configuration, which MUST be accessible from all the pods across the nodes. As a workaround, you need to install an NFS server on one node and share the file system across all the nodes.

Currently, we recommend that you use NFS version 3.0 for running WebLogic Server on OCI Container Engine for Kubernetes. During certification, we found that when using NFS 4.0, the servers in the WebLogic domain went into a failed state intermittently. Because multiple threads use NFS (default store, diagnostics store, Node Manager, logging, and `domain_home`), there are issues when accessing the file store. These issues are removed by changing the NFS to version 3.0.

#### PersistentVolume GID annotation

The `HOST_PATH` directory permissions can be made more secure by using a Kubernetes annotation on the
PersistentVolume that provides the group identifier (GID) which will be added to pods using the PersistentVolume.

For example, if the GID of the directory is `6789`, then the directory can be updated to remove permissions
other than for the user and group along with the PersistentVolume being annotated with the specified GID:

```bash
$ chmod 770 /path/to/domain1PersistentVolume
$ kubectl annotate pv domain1-weblogic-sample-pv pv.beta.kubernetes.io/gid=6789
```

After the domain is created and servers are running, the group ownership of the PersistentVolume files
can be updated to the specified GID which will provide read access to the group members. Typically,
files created from a pod onto the PersistentVolume will have UID `1000` and GID `1000` which is the
`oracle` user from the WebLogic Docker image.

An example of updating the group ownership on the PersistentVolume would be as follows:

```bash
$ cd /path/to/domain1PersistentVolume
$ sudo chgrp 6789 applications domains logs stores
$ sudo chgrp -R 6789 domains/
$ sudo chgrp -R 6789 logs/
```

### YAML files

Persistent volumes and claims are described in YAML files. For each PersistentVolume, you should create one PersistentVolume YAML file and one PersistentVolumeClaim YAML file. In the example below, you will find two YAML templates, one for the volume and one for the claim. As stated above, they either can be dedicated to a specific domain, or shared across multiple domains. For the use cases where a volume will be dedicated to a particular domain, it is a best practice to label it with `weblogic.domainUID=[domain name]`. This makes it easy to search for, and clean up resources associated with that particular domain.

For sample YAML templates, refer to the [PersistentVolumes example]({{< relref "/samples/simple/storage/_index.md" >}}).

### Kubernetes resources

After you have written your YAML files, use them to create the PersistentVolume by creating Kubernetes resources using the `kubectl create -f` command:

```
$ kubectl create -f pv.yaml
$ kubectl create -f pvc.yaml

```

#### Verify the results

To confirm that the PersistentVolume was created, use these commands:

```
$ kubectl describe pv [persistent volume name]
$ kubectl describe pvc -n NAMESPACE [persistent volume claim name]
```

### Common problems

This section provides details of common problems that might occur while running the script and how to resolve them.

#### PersistentVolume provider not configured correctly

Possibly the most common problem experienced during testing was the incorrect configuration of the PersistentVolume provider. The PersistentVolume must be accessible to all Kubernetes Nodes, and must be able to be mounted as Read/Write/Many. If this is not the case, the PersistentVolume creation will fail.

The simplest case is where the `HOST_PATH` provider is used. This can be either with one Kubernetes Node, or with the `HOST_PATH` residing in shared storage available at the same location on every node (for example, on an NFS mount). In this case, the path used for the PersistentVolume must have its permission bits set to 777.

#### Further reading

* See the blog, [How to run WebLogic clusters on the Oracle Cloud Infrastructure Container Engine for Kubernetes](https://blogs.oracle.com/weblogicserver/how-to-run-weblogic-clusters-on-the-oracle-cloud-infrastructure-container-engine-for-kubernetes).

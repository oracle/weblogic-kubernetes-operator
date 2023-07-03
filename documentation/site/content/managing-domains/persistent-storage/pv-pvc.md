+++
title = "PersistentVolumes and PersistentVolumeClaims"
date = 2019-02-23T16:45:09-05:00
weight = 1
description = "Use a Kubernetes PersistentVolume (PV) and PersistentVolumeClaim (PVC) to store WebLogic domain homes and log files."
+++

This document show you how to set up a Kubernetes PersistentVolume and PersistentVolumeClaim, which can be used as storage for WebLogic domain homes and log files. A PersistentVolume can be shared by multiple WebLogic domains or dedicated to a particular domain.

#### Prerequisites

The following prerequisites must be fulfilled before proceeding with the creation of the volume:

* Create a Kubernetes Namespace for the PersistentVolumeClaim unless the intention is to use the default namespace. Note that a PersistentVolumeClaim has to be in the same namespace as the Domain that uses it.
* Make sure that all the servers in the WebLogic domain are able to reach the storage location.
* Make sure that the host directory that will be used, already exists and has the appropriate file permissions set.

### Persistent volume storage locations
PersistentVolumes can point to different storage locations, for example NFS servers or a local directory path. For a list of available options, see the [Kubernetes documentation](https://kubernetes.io/docs/concepts/storage/persistent-volumes/).

**Note regarding HostPath**: In a single-node Kubernetes cluster, such as may be used for testing or proof of concept activities, `HOST_PATH` provides the simplest configuration.  In a multinode Kubernetes cluster, a `HOST_PATH` that is located on shared storage mounted by all nodes in the Kubernetes cluster is the simplest configuration.  If nodes do not have shared storage, then NFS is probably the most widely-available option.  There are other options listed in the referenced table.

The operator provides a sample script to create the PersistentVolume and PersistentVolumeClaim for the domain. This script must be executed before creating the domain.  Beginning with operator version 4.1.0, for the Domain on PV [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}), the operator provides options to create the PV and PVC during the domain initialization. See the [Domain on PV documentation]({{< relref "/managing-domains/domain-on-pv/_index.md" >}}) or the `domain.spec.configuration.initializeDomainOnPV` section in the domain resource [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md) for more details.

#### Persistent volumes using HostPath approach
The `HOST_PATH` provider is the simplest case for creating a PersistentVolume. It requires creating a directory on the Kubernetes master and ensuring that it has the correct permissions:

```shell
$ mkdir -m 777 -p /path/to/domain1PersistentVolume
```

### YAML files

Persistent volumes and claims are described in YAML files. For each PersistentVolume, you should create one PersistentVolume YAML file and one PersistentVolumeClaim YAML file. In the following example, you will find two YAML templates, one for the volume and one for the claim. As stated previously, they either can be dedicated to a specific domain, or shared across multiple domains. For the use cases where a volume will be dedicated to a particular domain, it is a best practice to label it with `weblogic.domainUID=[domain name]`. This makes it easy to search for, and clean up resources associated with that particular domain.

For sample YAML templates, refer to the [PersistentVolumes example]({{< relref "/samples/storage/_index.md" >}}).

For more details, refer to Kubernetes PV/PVC examples [here](https://github.com/kubernetes/examples/tree/master/staging/volumes).

#### Verify the results

To confirm that the PersistentVolume was created, use these commands:

```shell
$ kubectl describe pv <persistent volume name>
```
```shell
$ kubectl describe pvc -n NAMESPACE <persistent volume claim name>
```

### Common problems

This section provides details of common problems that might occur while running the script and how to resolve them.

#### PersistentVolume provider not configured correctly

Possibly the most common problem experienced during testing was the incorrect configuration of the PersistentVolume provider. The PersistentVolume must be accessible to all Kubernetes Nodes, and must be able to be mounted as `Read/Write/Many`. If this is not the case, the PersistentVolume creation will fail.

The simplest case is where the `HOST_PATH` provider is used. This can be either with one Kubernetes Node, or with the `HOST_PATH` residing in shared storage available at the same location on every node (for example, on an NFS mount). In this case, the path used for the PersistentVolume must have its permission bits set to 777.

---
title: "Architecture"
date: 2019-02-23T20:51:45-05:00
draft: false
weight: 3
description: "The operator consists of several parts: the operator runtime, the model for a Kubernetes custom resource definition (CRD), a Helm chart for installing the operator, a variety of sample shell scripts for preparing or packaging WebLogic domains for running in Kubernetes, and sample Helm charts or shell scripts for conditionally exposing WebLogic endpoints outside the Kubernetes cluster."
---

The operator consists of the following parts:

* The operator runtime, a process that runs in a container deployed into a Kubernetes Pod and which performs the actual management tasks.
* The model for a Kubernetes custom resource definition (CRD) that when installed in a Kubernetes cluster allows the Kubernetes API server to manage instances of this new type representing the operational details and status of WebLogic domains.
* A Helm chart for installing the operator runtime and related resources.
* A variety of sample shell scripts for preparing or packaging  WebLogic domains for running in Kubernetes.
* A variety of sample Helm charts or shell scripts for conditionally exposing WebLogic endpoints outside the Kubernetes cluster.

The operator is packaged in a [container image](https://github.com/orgs/oracle/packages/container/package/weblogic-kubernetes-operator) which you can access using the following `docker pull` commands:  

```
$ docker pull ghcr.io/oracle/weblogic-kubernetes-operator:3.2.0
```

For more details on acquiring the operator image and prerequisites for installing the operator, consult the [Quick Start guide]({{< relref "/quickstart/_index.md" >}}).

The operator registers a Kubernetes custom resource definition called `domain.weblogic.oracle` (shortname `domain`, plural `domains`).  More details about the Domain type defined by this CRD, including its schema, are available [here]({{< relref "/userguide/managing-domains/domain-resource.md" >}}).

The diagram below shows the general layout of high-level components, including optional components, in a Kubernetes cluster that is hosting WebLogic domains and the operator:

![High level architecture](/weblogic-kubernetes-operator/images/high-level-architecture.png)

The Kubernetes cluster has several namespaces.  Components may be deployed into namespaces as follows:

*	The operator is deployed into its own namespace.  If the Elastic Stack integration option is configured, then a Logstash pod will also be deployed in the operator’s namespace.
*	WebLogic domains will be deployed into various namespaces.  There can be more than one domain in a namespace, if desired.  There is no limit on the number of domains or namespaces that an operator can manage.  Note that there can be more than one operator in a Kubernetes cluster, but each operator is configured with a list of the specific namespaces that it is responsible for.  The operator will not take any action on any domain that is not in one of the namespaces the operator is configured to manage.
* Customers are responsible for load balancer configuration, which will typically be in the same namespace with domains or in a system, shared namespace such as the `kube-system` namespace.
*	Customers are responsible for Elasticsearch and Kibana deployment, which are typically deployed in the `default` namespace.

### Domain architecture

The diagram below shows how the various parts of a WebLogic domain are manifest in Kubernetes by the operator.

![Domain architecture](/weblogic-kubernetes-operator/images/domain-architecture2.png)

This diagram shows the following details:

*	An optional, persistent volume is created by the customer using one of the available providers.  If the persistent volume is shared across the domain or members of a cluster, then the chosen provider must support “Read Write Many” access mode.  The shared state on the persistent volume may include the `domain` directory, the `applications` directory, a directory for storing logs, and a directory for any file-based persistence stores.
*	A pod is created for the WebLogic Server Administration Server.  This pod is labeled with `weblogic.domainUID`, `weblogic.serverName`, and `weblogic.domainName`.  One container runs in this pod.  WebLogic Node Manager and Administration Server processes are run inside this container.  The Node Manager process is used as an internal implementation detail for the liveness probe, for patching, and to provide monitoring and control capabilities to the Administration Console.  It is not intended to be used for other purposes, and it may be removed in some future release.
*	A `ClusterIP` type service is created for the Administration Server pod.  This service provides a stable, well-known network (DNS) name for the Administration Server.  This name is derived from the `domainUID` and the Administration Server name, and it is known before starting up any pod.  The Administration Server `ListenAddress` is set to this well-known name.  `ClusterIP` type services are only visible inside the Kubernetes cluster.  They are used to provide the well-known names that all of the servers in a domain use to communicate with each other.  This service is labeled with `weblogic.domainUID` and `weblogic.domainName`.
*	A `NodePort` type service is optionally created for the Administration Server pod.  This service provides HTTP access to the Administration Server to clients that are outside the Kubernetes cluster.  This service is intended to be used to access the WebLogic Server Administration Console or for the T3 protocol for WLST connections.  This service is labeled with `weblogic.domainUID` and `weblogic.domainName`.
*	A pod is created for each WebLogic Server Managed Server.  These pods are labeled with `weblogic.domainUID`, `weblogic.serverName`, and `weblogic.domainName`.  One container runs in each pod.  WebLogic Node Manager and Managed Server processes are run inside each of these containers.  The Node Manager process is used as an internal implementation detail for the liveness probe.  It is not intended to be used for other purposes, and it may be removed in some future release.
*	A `ClusterIP` type service is created for each Managed Server pod that contains a Managed Server that is not part of a WebLogic cluster.  These services are intended to be used to access applications running on the Managed Servers.  These services are labeled with `weblogic.domainUID` and `weblogic.domainName`.  Customers must expose these services using a load balancer or `NodePort` type service to expose these endpoints outside the Kubernetes cluster.
*	A `PodDisruptionBudget` is created for each WebLogic cluster. These pod disruption budgets are labeled with `weblogic.domainUID`, `weblogic.clusterName` and `weblogic.domainName`.
*	An Ingress may optionally be created by the customer for each WebLogic cluster.  An Ingress provides load balanced HTTP access to all Managed Servers in that WebLogic cluster.  The load balancer updates its routing table for an Ingress every time a Managed Server in the WebLogic cluster becomes “ready” or ceases to be able to service requests, such that the Ingress always points to just those Managed Servers that are able to handle user requests.

{{% notice note %}}
Kubernetes requires that the names of some resource types follow the DNS label standard as defined in [DNS Label Names](https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#dns-label-names) and [RFC 1123](https://tools.ietf.org/html/rfc1123). Therefore, the operator enforces that the names of the Kubernetes resources do not exceed Kubernetes limits (see [Meet Kubernetes resource name restrictions]({{< relref "/userguide/managing-domains/_index.md#meet-kubernetes-resource-name-restrictions" >}})).
{{% /notice %}}

The diagram below shows the components inside the containers running WebLogic Server instances:

![Inside a container](/weblogic-kubernetes-operator/images/inside-a-container.png)

The Domain specifies a container image, defaulting to `container-registry.oracle.com/middleware/weblogic:12.2.1.4`. All containers running WebLogic Server use this same image. Depending on the use case, this image could contain the WebLogic Server product binaries or also include the domain directory.
{{% notice note %}}
During a rolling event caused by a change to the Domain's `image` field, containers will be using a mix of the updated value of the `image` field and its previous value.
{{% /notice %}}
Within the container, the following aspects are configured by the operator:

*	The `ENTRYPOINT` is configured by a script that starts up a Node Manager process, and then uses WLST to request that Node Manager start the server.  Node Manager is used to start servers so that the socket connection to the server will be available to obtain server status even when the server is unresponsive.  This is used by the liveness probe.
* The liveness probe is configured to check that a server is alive by querying the Node Manager process.  By default, the liveness probe is configured to check liveness every 45 seconds and to timeout after 5 seconds.  If a pod fails the liveness probe, Kubernetes will restart that container. For details about liveness probe customization, see [Liveness probe customization]({{< relref "/userguide/managing-domains/domain-lifecycle/liveness-readiness-probe-customization#liveness-probe-customization" >}}).
*	The readiness probe is configured to use the WebLogic Server ReadyApp framework.  The readiness probe determines if a server is ready to accept user requests.  The readiness probe is used to determine when a server should be included in a load balancer's endpoints, in the case of a rolling restart, when a restarted server is fully started, and for various other purposes. For details about readiness probe customization, see [Readiness probe customization]({{< relref "/userguide/managing-domains/domain-lifecycle/liveness-readiness-probe-customization#readiness-probe-customization" >}}).
*	A shutdown hook is configured that will execute a script that performs a graceful shutdown of the server.  This ensures that servers have an opportunity to shut down cleanly before they are killed.

### Domain state stored outside container images
The operator expects (and requires) that all state be stored outside of the images that are used to run the domain.  This means either in a persistent file system, or in a database.  The WebLogic configuration, that is, the domain directory and the applications directory may come from the image or a persistent volume.  However, other state, such as file-based persistent stores, and such, must be stored on a persistent volume or in a database.  All of the containers that are participating in the WebLogic domain use the same image, and take on their personality; that is, which server they execute, at startup time. Each Pod mounts storage, according to the Domain, and has access to the state information that it needs to fulfill its role in the domain.

It is worth providing some background information on why this approach was adopted, in addition to the fact that this separation is consistent with other existing operators (for other products) and the Kubernetes “cattle, not pets” philosophy when it comes to containers.

The external state approach allows the operator to treat the images as essentially immutable, read-only, binary images.  This means that the image needs to be pulled only once, and that many domains can share the same image.  This helps to minimize the amount of bandwidth and storage needed for WebLogic Server images.

This approach also eliminates the need to manage any state created in a running container, because all of the state that needs to be preserved is written into either the persistent volume or a database backend. The containers and pods are completely throwaway and can be replaced with new containers and pods, as necessary.  This makes handling failures and rolling restarts much simpler because there is no need to preserve any state inside a running container.

When users wish to apply a binary patch to WebLogic Server, it is necessary to create only a single new, patched image.  If desired, any domains that are running may be updated to this new patched image with a rolling restart, because there is no state in the containers.

It is envisaged that in some future release of the operator, it will be desirable to be able to “move” or “copy” domains in order to support scenarios like Kubernetes federation, high availability, and disaster recovery.  Separating the state from the running containers is seen as a way to greatly simplify this feature, and to minimize the amount of data that would need to be moved over the network, because the configuration is generally much smaller than the size of WebLogic Server images.

The team developing the operator felt that these considerations provided adequate justification for adopting the external state approach.

### Network name predictability

The operator uses services to provide stable, well-known DNS names for each server.  These names are known in advance of starting up a pod to run a server, and are used in the `ListenAddress` fields in the WebLogic Server configuration to ensure that servers will always be able to find each other.  This also eliminates the need for pod names or the actual WebLogic Server instance names to be the same as the DNS addresses.

### Draining a node and PodDisruptionBudget
A Kubernetes cluster administrator can [drain a Node](https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/) for repair, upgrade, or scaling down the Kubernetes cluster.

Beginning in version 3.2, the operator takes advantage of the [PodDisruptionBudget](https://kubernetes.io/docs/concepts/workloads/pods/disruptions/#pod-disruption-budgets) feature offered by Kubernetes for high availability during a Node drain operation. The operator creates a PodDisruptionBudget (PDB) for each WebLogic cluster in the Domain namespace to limit the number of WebLogic Server pods simultaneously evicted when draining a node. The maximum number of WebLogic cluster's server pods evicted simultaneously is determined by the `maxUnavailable` field on the Domain resource. The `.spec.minAvailable` field of the PDB for a cluster is calculated from the difference of the current `replicas` count and `maxUnavailable` value configured for the cluster. For example, if you have a WebLogic cluster with three replicas and a `maxUnavailable` of 1, the `.spec.minAvailable` for PDB is set to 2. In this case, Kubernetes ensures that at least two pods for the WebLogic cluster's Managed Servers are available at any given time, and it only evicts a pod when all three pods are ready. For details about safely draining a node and the PodDisruptionBudget concept, see [Safely Drain a Node](https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/) and [PodDisruptionBudget](https://kubernetes.io/docs/concepts/workloads/pods/disruptions/).

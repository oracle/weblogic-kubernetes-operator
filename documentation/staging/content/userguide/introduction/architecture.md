---
title: "Architecture"
date: 2019-02-23T20:51:45-05:00
draft: false
weight: 3
description: "An architectural overview of the operator runtime and related resources."
---

### Contents

- [Overall architecture](#overall-architecture)
- [Domain architecture](#domain-architecture)
- [Domain UID](#domain-uid)
- [Network name predictability](#network-name-predictability)
- [Domain state stored outside container images](#domain-state-stored-outside-container-images)

### Overall architecture

The operator consists of the following parts:

* The operator runtime, which is a process that:
  - Runs in a container deployed
    into a Kubernetes Pod and that monitors one
    or more Kubernetes namespaces.
  - Performs the actual management tasks
    for domain resources deployed to these namespaces.
* A Helm chart for installing the operator runtime and its related resources.
* A Kubernetes custom resource definition (CRD) that,
  when installed, enables the Kubernetes API server
  and the operator to monitor and manage domain resource instances.
* Domain resources that reference WebLogic 
  domain configuration, a WebLogic install, and
  anything else necessary to run the domain.
* A variety of samples for preparing or packaging
  WebLogic domains for running in Kubernetes.

The operator is packaged in a [container image](https://github.com/orgs/oracle/packages/container/package/weblogic-kubernetes-operator) which you can access using the following `docker pull` commands:  

```shell
$ docker pull ghcr.io/oracle/weblogic-kubernetes-operator:3.2.5
```

For more details on acquiring the operator image and prerequisites for installing the operator, consult the [Quick Start guide]({{< relref "/quickstart/_index.md" >}}).

The operator registers a Kubernetes custom resource definition called `domain.weblogic.oracle` (shortname `domain`, plural `domains`).  More details about the Domain type defined by this CRD, including its schema, are available [here]({{< relref "/userguide/managing-domains/domain-resource.md" >}}).

The diagram below shows the general layout of high-level components, including optional components, in a Kubernetes cluster that is hosting WebLogic domains and the operator:

{{< img "High level architecture" "images/high-level-architecture.png" >}}

The Kubernetes cluster has several namespaces. Components may be deployed into namespaces as follows:

* One or more operators, each deployed into its own namespace.
  * There can be more than one operator in a Kubernetes cluster but
    only a single operator per namespace.
  * Each operator is configured with
    the specific namespaces that it is responsible for.
    * The operator will not take any action on any domain
      that is not in one of the namespaces the operator is configured to manage.
    * Multiple operators cannot manage the same namespace.
    * The operator can be configured to monitor
      its own namespace.
  * There is no limit on the number of domains or namespaces that an operator can manage.  
  * If the Elastic Stack integration option is configured to monitor the operator,
    then a Logstash pod will also be deployed in the operator’s namespace.
* WebLogic domain resources deployed into various namespaces. 
  * There can be more than one domain in a namespace, if desired.
  * Every domain resource must be configured with a [domain unique identifier](domain-uid).
* Customers are responsible for load balancer configuration, which will typically be in the same namespace with domains or in a shared namespace.
* Customers are responsible for Elasticsearch and Kibana deployments that may be used to monitor WebLogic server and pod logs.

### Domain UID

Every domain resource must be configured with a domain unique identifier
which is a string and may also be called a `Domain UID`,
`domainUID`, or `DOMAIN_UID` depending on the context.
This value is distinct and need not match the domain name from
the WebLogic domain configuration. The operator will
use this as a name prefix for the domain related resources 
that it creates for you (such as services and pods).

A Domain UID is set on a domain resource using `spec.domainUID`,
and defaults to the value of `metadata.name`. The
`spec.domainUID` domain resource field is usually 
left unset in order to take advantage of this default.

It is recommended that a Domain UID be configured to be unique 
across all Kubernetes namespaces and even across different Kubernetes
clusters in order to assist in future work to identify 
related domains in active-passive scenarios across data centers;
however, it is only required that this value
be unique within a namespace, similarly to the names of Kubernetes
resources.

As a convention, any resource that is associated with a particular Domain UID
is typically given a Kubernetes label named `weblogic.domainUID` that
is assigned to that UID. If the operator creates a resource for
you on behalf of a particular domain, it will follow this 
convention. For example, to see all pods created with
the `weblogic.domainUID` label in a Kubernetes cluster try:
`kubectl get pods -l weblogic.domainUID --all-namespaces=true --show-labels=true`.

A Domain UID may be up to 45 characters long. For
more details about Domain UID name requirements, see
[Meet Kubernetes resource name restrictions]({{< relref "/userguide/managing-domains/_index.md#meet-kubernetes-resource-name-restrictions" >}}).

### Domain architecture

The diagram below shows how the various parts of a WebLogic domain are manifest in Kubernetes by the operator.

{{< img "Domain architecture" "images/domain-architecture2.png" >}}

This diagram shows the following details:

*	An optional, persistent volume is created by the customer using one of the available providers.  If the persistent volume is shared across the domain or members of a cluster, then the chosen provider must support “Read Write Many” access mode.  The shared state on the persistent volume may include the `domain` directory, the `applications` directory, a directory for storing logs, and a directory for any file-based persistence stores.
*	A pod is created for the WebLogic Server Administration Server.  This pod is named `DOMAIN_UID-wlservername` and is labeled with `weblogic.domainUID`, `weblogic.serverName`, and `weblogic.domainName`.  One container runs in this pod.  WebLogic Node Manager and Administration Server processes are run inside this container.  The Node Manager process is used as an internal implementation detail for the liveness probe which we will descibe in more detail later, for patching, and to provide monitoring and control capabilities to the Administration Console.  It is not intended to be used for other purposes, and it may be removed in some future release.
*	A `ClusterIP` type service is created for the Administration Server pod.  This service provides a stable, well-known network (DNS) name for the Administration Server.  This name is derived from the `domainUID` and the Administration Server name as described [here](#network-name-predictability), and it is known before starting up any pod.  The Administration Server `ListenAddress` is set to this well-known name.  `ClusterIP` type services are only visible inside the Kubernetes cluster.  They are used to provide the well-known names that all of the servers in a domain use to communicate with each other.  This service is labeled with `weblogic.domainUID` and `weblogic.domainName`.
*	A `NodePort` type service is optionally created for the Administration Server pod.  This service provides HTTP access to the Administration Server to clients that are outside the Kubernetes cluster.  This service is intended to be used to access the WebLogic Server Administration Console or for the T3 protocol for WLST connections.  This service is labeled with `weblogic.domainUID` and `weblogic.domainName`.
*	A pod is created for each WebLogic Server Managed Server.  These pods are named `DOMAIN_UID-wlservername` and are labeled with `weblogic.domainUID`, `weblogic.serverName`, and `weblogic.domainName`.  One container runs in each pod.  WebLogic Node Manager and Managed Server processes are run inside each of these containers.  The Node Manager process is used as an internal implementation detail for the liveness probe which we will describe in more detail later.  It is not intended to be used for other purposes, and it may be removed in some future release.
*	A `ClusterIP` type service is created for each Managed Server pod as described [here](#network-name-predictability). These services are intended to be used to access applications running on the Managed Servers.  These services are labeled with `weblogic.domainUID` and `weblogic.domainName`.
*	A `ClusterIP` type service is also created for each WebLogic cluster as described [here](#network-name-predictability).  Customers can expose these services using a load balancer or `NodePort` type service to expose these endpoints outside the Kubernetes cluster.
*	A `PodDisruptionBudget` is created for each WebLogic cluster. These pod disruption budgets are labeled with `weblogic.domainUID`, `weblogic.clusterName` and `weblogic.domainName`.
*	An Ingress may optionally be created by the customer for each WebLogic cluster.  An Ingress provides load balanced HTTP access to all Managed Servers in that WebLogic cluster.  The load balancer updates its routing table for an Ingress every time a Managed Server in the WebLogic cluster becomes “ready” or ceases to be able to service requests, such that the Ingress always points to just those Managed Servers that are able to handle user requests.

{{% notice note %}}
Kubernetes requires that the names of some resource types follow the DNS label standard as defined in [DNS Label Names](https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#dns-label-names) and [RFC 1123](https://tools.ietf.org/html/rfc1123). Therefore, the operator enforces that the names of the Kubernetes resources do not exceed Kubernetes limits (see [Meet Kubernetes resource name restrictions]({{< relref "/userguide/managing-domains/_index.md#meet-kubernetes-resource-name-restrictions" >}})).
{{% /notice %}}

The diagram below shows the components inside the containers running WebLogic Server instances:

{{< img "Inside a container" "images/inside-a-container.png" >}}

The Domain specifies a container image, defaulting to `container-registry.oracle.com/middleware/weblogic:12.2.1.4`. All containers running WebLogic Server use this same image. Depending on the use case, this image could contain the WebLogic Server product binaries or also include the domain directory.
{{% notice note %}}
During a rolling event caused by a change to the Domain's `image` field, containers will be using a mix of the updated value of the `image` field and its previous value.
{{% /notice %}}
Within the container, the following aspects are configured by the operator:

*	The `ENTRYPOINT` is configured by a script that starts up a Node Manager process, and then uses WLST to request that Node Manager start the server.  Node Manager is used to start servers so that the socket connection to the server will be available to obtain server status even when the server is unresponsive.  This is used by the liveness probe.
* The liveness probe is configured to check that a server is alive by querying the Node Manager process.  By default, the liveness probe is configured to check liveness every 45 seconds and to timeout after 5 seconds.  If a pod fails the liveness probe, Kubernetes will restart that container. For details about liveness probe customization, see [Liveness probe customization]({{< relref "/userguide/managing-domains/domain-lifecycle/liveness-readiness-probe-customization#liveness-probe-customization" >}}).
*	The readiness probe is configured to use the WebLogic Server ReadyApp framework.  The readiness probe determines if a server is ready to accept user requests.  The readiness probe is used to determine when a server should be included in a load balancer's endpoints, in the case of a rolling restart, when a restarted server is fully started, and for various other purposes. For details about readiness probe customization, see [Readiness probe customization]({{< relref "/userguide/managing-domains/domain-lifecycle/liveness-readiness-probe-customization#readiness-probe-customization" >}}).
*	A shutdown hook is configured that will execute a script that performs a graceful shutdown of the server.  This ensures that servers have an opportunity to shut down cleanly before they are killed.

### Network name predictability
The operator deploys services with predictable well-defined DNS names
for each WebLogic server and cluster in your WebLogic configuration.
The name of a WebLogic server service is `DOMAIN_UID-wlservername`
and the name of a WebLogic server cluster is `DOMAIN_UID-cluster-wlclustername`,
all in lowercase, with underscores `_` converted to hyphens `-`.

The operator also automatically overrides the `ListenAddress` fields in each
running WebLogic Server to match its service name in order 
to ensure that the servers will always be able to find each other.

For details, see [Meet Kubernetes resource name restrictions]({{< relref "/userguide/managing-domains/_index.md#meet-kubernetes-resource-name-restrictions" >}}).

### Domain state stored outside container images
The operator expects (and requires) that all state that is expected to outlive the life of a pod be stored outside of the images that are used to run the domain.  This means either in a persistent file system, or in a database.  The WebLogic configuration, that is, the domain directory and the applications directory may come from the image or a persistent volume.  However, other state, such as file-based persistent stores, and such, must be stored on a persistent volume or in a database.  All of the containers that are participating in the WebLogic domain use the same image, and take on their personality; that is, which server they execute, at startup time. Each Pod mounts storage, according to the Domain, and has access to the state information that it needs to fulfill its role in the domain.

It is worth providing some background information on why this approach was adopted, in addition to the fact that this separation is consistent with other existing operators (for other products) and the Kubernetes “cattle, not pets” philosophy when it comes to containers.

The external state approach allows the operator to treat the images as essentially immutable, read-only, binary images.  This means that the image needs to be pulled only once, and that many domains can share the same image.  This helps to minimize the amount of bandwidth and storage needed for WebLogic Server images.

This approach also eliminates the need to manage any state created in a running container, because all of the state that needs to be preserved is written into either the persistent volume or a database backend. The containers and pods are completely throwaway and can be replaced with new containers and pods, as necessary.  This makes handling failures and rolling restarts much simpler because there is no need to preserve any state inside a running container.

When users wish to apply a binary patch to WebLogic Server, it is necessary to create only a single new, patched image.  If desired, any domains that are running may be updated to this new patched image with a rolling restart, because there is no state in the containers.

It is envisaged that in some future release of the operator, it will be desirable to be able to “move” or “copy” domains in order to support scenarios like Kubernetes federation, high availability, and disaster recovery.  Separating the state from the running containers is seen as a way to greatly simplify this feature, and to minimize the amount of data that would need to be moved over the network, because the configuration is generally much smaller than the size of WebLogic Server images.

The team developing the operator felt that these considerations provided adequate justification for adopting the external state approach.

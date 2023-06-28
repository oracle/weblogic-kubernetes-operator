---
title: "About WebLogic domains"
date: 2019-02-23T16:43:45-05:00
weight: 1
description: "An overview about managing WebLogic domains and clusters in Kubernetes."
---

This document is an overview of managing WebLogic domains and clusters in Kubernetes.

{{< table_of_contents >}}

### Creating and managing WebLogic domains

Domain resources reference WebLogic domain configuration, a WebLogic install,
[images]({{< relref "/base-images/_index.md" >}}),
and anything else necessary to run the domain.
Beginning with operator 4.0, WebLogic clusters that are within a WebLogic domain configuration
may optionally be associated with a Cluster resource in addition to a Domain resource.
For more information, see [Domain and Cluster resources]({{< relref "/managing-domains/domain-resource/_index.md" >}}).

You can locate a WebLogic domain either on a persistent volume (Domain on PV), inside the container only (Model in Image), or in an image (Domain in Image).
For an explanation of each, see [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).
For examples of each, see the [WebLogic Kubernetes Operator samples]({{< relref "/samples/domains/_index.md" >}}).

{{% notice note %}}The Domain in Image [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) is deprecated in WebLogic Kubernetes Operator version 4.0. Oracle recommends that you choose either Domain on PV or Model in Image, depending on your needs.
{{% /notice %}}

If you want to create your own container images, for example, to choose a specific set of patches or to create a domain
with a specific configuration or applications deployed, then you can create the domain custom resource
manually to deploy your domain.  This process is documented in [this
sample]({{< relref "/samples/domains/manually-create-domain/_index.md" >}}).

**NOTE**: After you are familiar with the basics, it is recommended to review
[important considerations]({{< relref "/managing-domains/manage-domains#important-considerations-for-weblogic-domains-in-kubernetes" >}})
and
[resource name restrictions]({{< relref "/managing-domains/manage-domains#meet-kubernetes-resource-name-restrictions" >}}).

### Modifying domain configurations

You can modify the WebLogic domain configuration for Domain on PV, Domain in Image, and Model in Image before deploying a Domain YAML file:

When the domain is on a persistent volume, you can use WLST or WDT to change the configuration.

For Domain in Image and Domain on PV, you can use [Configuration overrides]({{< relref "/managing-domains/configoverrides/_index.md" >}}).

Configuration overrides allow changing a configuration without modifying its original `config.xml` or system resource XML files, and supports
parameterizing overrides so that you can inject values into them from Kubernetes Secrets. For example, you can inject database user names, passwords,
and URLs that are stored in a secret. However, note the scenarios for which configuration overrides [are _not_ supported]({{< relref "/managing-domains/configoverrides#unsupported-overrides" >}}).

For Model in Image, you use [Runtime Updates]({{<relref "/managing-domains/model-in-image/runtime-updates.md" >}}).

### Managing lifecycle operations

You can perform lifecycle operations on WebLogic Servers, clusters, or domains.
This includes starting, stopping, and rolling domains, clusters,
or individual servers, plus detecting failures and tuning retry behavior.
See [Domain life cycle]({{< relref "/managing-domains/domain-lifecycle/_index.md" >}}).

### Scaling clusters

The operator lets you initiate scaling of clusters in various ways:

* Using `kubectl` to edit a Cluster resource
* Using Kubernetes `scale` commands
* Using a Kubernetes Horizontal Pod Autoscaler
* Using the operator's REST APIs
* Using WLDF policies
* Using a Prometheus action

See [Domain life cycle scaling]({{< relref "/managing-domains/domain-lifecycle/scaling.md" >}}).

### About domain events

The operator generates Kubernetes events at key points during domain processing.
For more information, see [Domain events]({{< relref "/managing-domains/accessing-the-domain/domain-events.md" >}}).

### Accessing and monitoring domains

To access the domain using WLST, console, T3, or a load balancer,  or to export Prometheus-compatible metrics,
see [Access and monitor domains]({{< relref "/managing-domains/accessing-the-domain/" >}}).

### Logging

To tune log file location and rotation, see [Log Files]({{< relref "/managing-domains/accessing-the-domain/logs.md" >}}).

To export operator or domain log files, see the [Elastic Stack]({{< relref "/samples/elastic-stack/" >}}) examples.

### Meet Kubernetes resource name restrictions

Kubernetes requires that the names of some resource types follow the DNS label standard as defined in [DNS Label Names](https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#dns-label-names) and [RFC 1123](https://tools.ietf.org/html/rfc1123). This requirement restricts the characters that are allowed in the names of these resources, and also limits the length of these names to no more than 63 characters.

The following is a list of such Kubernetes resources that the operator generates when a domain resource is deployed, including how their names are constructed.

* A domain introspector job named `<domainUID>-<introspectorJobNameSuffix>`. The default suffix is `-introspector`, which can be overridden using the operator's Helm configuration `introspectorJobNameSuffix` (see [WebLogic domain management]({{< relref "/managing-operators/using-helm#weblogic-domain-management" >}})).
* A ClusterIP type service and a pod for each WebLogic Server named `<domainUID>-<serverName>`.
* A ClusterIP type service for each WebLogic cluster named `<domainUID>-cluster-<clusterName>`.
* An optional NodePort type service, also known as an external service, for the WebLogic Administration Server named `<domainUID>-<adminServerName>-<externalServiceNameSuffix>`. The default suffix is `-ext`, which can be overridden using the operator's Helm configuration `externalServiceNameSuffix` (see [WebLogic domain management]({{< relref "/managing-operators/using-helm#weblogic-domain-management" >}})).

The operator puts in place certain validation checks and conversions to prevent these resources from violating Kubernetes restrictions.
* All the names previously described can contain only the characters `A-Z`, `a-z`, `0-9`, `-`, or `_`, and must start and end with an alphanumeric character. Note that when generating pod and service names, the operator will convert configured names to lowercase and substitute a hyphen (`-`) for each underscore (`_`).
* A `domainUID` is required to be no more than 45 characters.
* WebLogic domain configuration names, such as the cluster names, Administration Server name, and Managed Server names must be kept to a legal length so that the resultant resource names do not exceed Kubernetes' limits.

When a domain resource or WebLogic domain configuration violates the limits, the domain startup will fail, and actual validation errors are reported in the domain resource's status.


### Important considerations for WebLogic domains in Kubernetes

Be aware of the following important considerations for WebLogic domains running in Kubernetes:

* _Domain Home Location:_ The WebLogic domain home location is determined by the Domain YAML file `domainHome`, if specified; otherwise, a default location is determined by the `domainHomeSourceType` setting.
  - If the Domain `domainHome` field is not specified and `domainHomeSourceType` is `Image` (the default), then the operator will assume that the domain home is a directory under `/u01/oracle/user_projects/domains/`, and report an error if no domain is found or more than one domain is found.
  - If the Domain `domainHome` field is not specified and `domainHomeSourceType` is `PersistentVolume`, then the operator will assume that the domain home is `/shared/domains/DOMAIN_UID`.
  - Finally, if the Domain `domainHome` field is not specified and the `domainHomeSourceType` is `FromModel`, then the operator will assume that the domain home is `/u01/domains/DOMAIN_UID`.

  {{% notice warning %}}
  Oracle strongly recommends storing an image containing a WebLogic domain home (`domainHomeSourceType` is `Image`)
  as private in the registry (for example, Oracle Cloud Infrastructure Registry, GitHub Container Registry, and such).
  A container image that contains a WebLogic domain has sensitive information including
  keys and credentials that are used to access external resources (for example, the data source password).
  For more information, see
  [WebLogic domain in container image protection]({{<relref "/security/domain-security/image-protection.md">}}).
  {{% /notice %}}

* _Log File Locations:_ The operator can automatically override WebLogic Server, domain, and introspector log locations.
  This occurs if the Domain `logHomeEnabled` field is explicitly set to `true`, or if `logHomeEnabled` isn't set
  and `domainHomeSourceType` is set to `PersistentVolume`.  When overriding, the log location will be the location specified by the `logHome` setting.
  For additional log file tuning information, see [Log files]({{< relref "/managing-domains/accessing-the-domain/logs.md" >}}).

* _Listen Address Overrides:_  The operator will automatically override all WebLogic domain default,
  SSL, admin, or custom channel listen addresses (using [Configuration overrides]({{< relref "/managing-domains/configoverrides/_index.md" >}})).  These will become `domainUID` followed by a
  hyphen and then the server name, all lowercase, and underscores converted to hyphens.  For example, if `domainUID=domain1` and
  the WebLogic Server name is `Admin_Server`, then its listen address becomes `domain1-admin-server`.

* _Domain, Cluster, Server, and Network-Access-Point Names:_ WebLogic domain, cluster, server, and network-access-point (channel)
  names must contain only the characters `A-Z`, `a-z`, `0-9`, `-`, or `_`, and must be kept to a reasonable length.  This ensures that they can
  be safely used to form resource names that meet Kubernetes resource and DNS1123 naming requirements. For more details,
  see [Meet Kubernetes resource name restrictions](#meet-kubernetes-resource-name-restrictions).

* _Node Ports:_ If you choose to expose any WebLogic channels outside the Kubernetes cluster using a `NodePort`, for example, the
  administration port or a T3 channel to allow WLST access, you need to ensure that you allocate each channel a
  unique port number across the entire Kubernetes cluster.  If you expose the administration port in each WebLogic domain in
  the Kubernetes cluster, then each one must have a different port number.  This is required because `NodePorts` are used to
  expose channels outside the Kubernetes cluster.
  {{% notice warning %}}
  Exposing administrative, RMI, or T3 capable channels using a Kubernetes `NodePort`
  can create an insecure configuration. In general, only HTTP protocols should be made available externally and this exposure
  is usually accomplished by setting up an external load balancer that can access internal (non-`NodePort`) services.
  For more information, see [WebLogic T3 and administrative channels]({{<relref "/security/domain-security/weblogic-channels#weblogic-t3-and-administrative-channels">}}).
  {{% /notice %}}

* _Host Path Persistent Volumes:_ If using a `hostPath` persistent volume, then it must be available on all worker nodes in the cluster and have read/write/many permissions for all container/pods in the WebLogic Server deployment.  Be aware
  that many cloud provider's volume providers may not support volumes across availability zones.  You may want to use NFS or a clustered file system to work around this limitation.

* _Security Note:_ The `USER_MEM_ARGS` environment variable defaults to `-Djava.security.egd=file:/dev/./urandom` in all WebLogic Server pods and the WebLogic introspection job. It can be explicitly set to another value in your Domain or Cluster YAML file using the `env` attribute under the `serverPod` configuration.

* _JVM Memory and Java Option Arguments:_ The following environment variables can be used to customize the JVM memory and Java options for both the WebLogic Server Managed Servers and Node Manager instances:

    * `JAVA_OPTIONS` - Java options for starting WebLogic Server
    * `USER_MEM_ARGS` - JVM memory arguments for starting WebLogic Server
    * `NODEMGR_JAVA_OPTIONS` - Java options for starting a Node Manager instance
    * `NODEMGR_MEM_ARGS` - JVM memory arguments for starting a Node Manager instance

    For more information, see [JVM memory and Java option environment variables]({{< relref "/managing-domains/domain-resource#jvm-memory-and-java-option-environment-variables" >}}).


The following features are **not** certified or supported in this release:

* Whole server migration
* Consensus leasing
* Node Manager (although it is used internally for the liveness probe and to start WebLogic Server instances)
* Multicast
* Multitenancy
* Production redeployment
* Mixed clusters (configured servers targeted to a dynamic cluster)

For up-to-date information about the features of WebLogic Server that are supported in Kubernetes environments, see My Oracle Support Doc ID 2349228.1.

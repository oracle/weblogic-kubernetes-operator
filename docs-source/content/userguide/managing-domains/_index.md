---
title: "Manage WebLogic domains"
date: 2019-02-23T16:43:45-05:00
weight: 4
description: "Important considerations for WebLogic domains in Kubernetes."
---

#### Contents

* [Important considerations for WebLogic domains in Kubernetes](#important-considerations-for-weblogic-domains-in-kubernetes)
* [Creating and managing WebLogic domains](#creating-and-managing-weblogic-domains)
* [Modifying domain configurations](#modifying-domain-configurations)
* [About the domain resource](#about-the-domain-resource)
* [Managing life cycle operations](#managing-life-cycle-operations)
* [Scaling clusters](#scaling-clusters)

#### Important considerations for WebLogic domains in Kubernetes

Be aware of the following important considerations for WebLogic domains running in Kubernetes:

* _Domain Home Location:_ The WebLogic domain home location is determined by the domain resource `domainHome` and `domainHomeInImage` fields.
  If you are using 3.0.0-rc1, these same fields are used, if specified; otherwise, a default location is determined by the `domainHomeSourceType` setting.
  The `domainHomeSourceType` field is *not* available in releases before 3.0.0-rc1.
  - If the domain resource `domainHome` field is not specified and `domainHomeSourceType` is `Image` (the default), then the operator will assume that the domain home is a directory under `/u01/oracle/user_projects/domains/`, and report an error if no domain is found or more than one domain is found.  
  - If the domain resource `domainHome` field is not specified and `domainHomeSourceType` is `PersistentVolume`, then the operator will assume that the domain home is `/shared/domains/DOMAIN_UID`.
  - Finally, if the domain resource `domainHome` field is not specified and the `domainHomeSourceType` is `FromModel`, then the operator will assume that the domain home is `/u01/domains/DOMAIN_UID`.

  {{% notice warning %}}
  Oracle strongly recommends storing an image containing a WebLogic domain home (`domainHomeSourceType` is `Image`)
  as private in the registry (for example, Oracle Cloud Infrastructure Registry, Docker Hub, and such).
  A Docker image that contains a WebLogic domain has sensitive information including
  keys and credentials that are used to access external resources (for example, the data source password).
  For more information, see
  [WebLogic domain in Docker image protection]({{<relref "/security/domain-security/image-protection#weblogic-domain-in-docker-image-protection">}}).
  {{% /notice %}}

* _Log File Locations:_ The operator can automatically override WebLogic domain and server log locations using
  configuration overrides.  This occurs if the domain resource `logHomeEnabled` field is explicitly set to `true`, or if `logHomeEnabled` isn't set
  and `domainHomeSourceType` is set to `PersistentVolume`.  When overriding, the log location will be the location specified by the `logHome` setting.

* _Listen Address Overrides:_  The operator will automatically override all WebLogic domain default,
  SSL, admin, or custom channel listen addresses (using situational configuration overrides).  These will become `domainUID` followed by a
  hyphen and then the server name, all lower case, and underscores converted to hyphens.  For example, if `domainUID=domain1` and
  the WebLogic Server name is `Admin_Server`, then its listen address becomes `domain1-admin-server`.

* _Domain, Cluster, Server, and Network-Access-Point Names:_ WebLogic domain, cluster, server, and network-access-point (channel)
  names must contain only the characters `A-Z`, `a-z`, `0-9`, `-`, or `_`.  This ensures that they can be converted to
  meet Kubernetes resource and DNS1123 naming requirements.  (When generating pod and service names, the operator will convert
  configured names to lower case and substitute a hyphen (`-`) for each underscore (`_`).)

* _Node Ports:_ If you choose to expose any WebLogic channels outside the Kubernetes cluster using a `NodePort`, for example, the
  administration port or a T3 channel to allow WLST access, you need to ensure that you allocate each channel a
  unique port number across the entire Kubernetes cluster.  If you expose the administration port in each WebLogic domain in
  the Kubernetes cluster, then each one must have a different port number.  This is required because `NodePorts` are used to
  expose channels outside the Kubernetes cluster.  
  {{% notice warning %}}
  Exposing administrative, RMI, or T3 capable channels using a Kubernetes `NodePort`
  can create an insecure configuration. In general, only HTTP protocols should be made available externally and this exposure
  is usually accomplished by setting up an external load balancer that can access internal (non-`NodePort`) services.
  For more information, see [T3 channels]({{<relref "/security/domain-security/weblogic-channels#weblogic-t3-channels">}}).
  {{% /notice %}}

* _Host Path Persistent Volumes:_ If using a `hostPath` persistent volume, then it must be available on all worker nodes in the cluster and have read/write/many permissions for all container/pods in the WebLogic Server deployment.  Be aware
  that many cloud provider's volume providers may not support volumes across availability zones.  You may want to use NFS or a clustered file system to work around this limitation.

* _Security Note:_ The `USER_MEM_ARGS` environment variable defaults to `-Djava.security.egd=file:/dev/./urandom` in all WebLogic Server pods and the WebLogic introspection job. It can be explicitly set to another value in your domain resource YAML file using the `env` attribute under the `serverPod` configuration.

* _JVM Memory and Java Option Arguments:_ The following environment variables can be used to customize the JVM memory and Java options for both the WebLogic Server Managed Servers and Node Manager instances:

    * `JAVA_OPTIONS` - Java options for starting WebLogic Server
    * `USER_MEM_ARGS` - JVM memory arguments for starting WebLogic Server
    * `NODEMGR_JAVA_OPTIONS` - Java options for starting a Node Manager instance
    * `NODEMGR_MEM_ARGS` - JVM memory arguments for starting a Node Manager instance

    For more information, see [Domain resource]({{< relref "/userguide/managing-domains/domain-resource/_index.md" >}}).

The following features are **not** certified or supported in this release:

* Whole server migration
* Consensus leasing
* Node Manager (although it is used internally for the liveness probe and to start WebLogic Server instances)
* Multicast
* Multitenancy
* Production redeployment
* Mixed clusters (configured servers targeted to a dynamic cluster)

For up-to-date information about the features of WebLogic Server that are supported in Kubernetes environments, see My Oracle Support Doc ID 2349228.1.


### Creating and managing WebLogic domains

You can locate a WebLogic domain either in a persistent volume (PV) or in a Docker image.
For examples of each, see the [WebLogic Server Kubernetes Operator samples]({{< relref "/samples/simple/domains/_index.md" >}}).

If you want to create your own Docker images, for example, to choose a specific set of patches or to create a domain
with a specific configuration or applications deployed, then you can create the domain custom resource
manually to deploy your domain.  This process is documented in [this
sample]({{< relref "/samples/simple/domains/manually-create-domain/_index.md" >}}).

### Modifying domain configurations

You can modify the WebLogic domain configuration for Domain in PV, Domain in Image, and Model in Image before deploying a domain resource:

When the domain is in a persistent volume, you can use WLST or WDT to change the configuration.

For Domain in Image and Domain in PV you can use [configuration overrides]({{< relref "/userguide/managing-domains/configoverrides/_index.md" >}}).

Configuration overrides allow changing a configuration without modifying its original `config.xml` or system resource XML files, and supports
parameterizing overrides so that you can inject values into them from Kubernetes Secrets. For example, you can inject database user names, passwords,
and URLs that are stored in a secret.

For Model in Image you use [Runtime Updates]({{<relref "/userguide/managing-domains/model-in-image/runtime-updates.md" >}}).

### About the domain resource

For more information, see [Domain resource]({{< relref "/userguide/managing-domains/domain-resource/_index.md" >}}).

### Managing lifecycle operations

You can perform lifecycle operations on WebLogic Servers, clusters, or domains.
See [Starting and stopping]({{< relref "/userguide/managing-domains/domain-lifecycle/startup.md" >}}) and [Restarting]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting.md" >}}) servers.

### Scaling clusters

The operator let's you initiate scaling of clusters in various ways:

* [Using kubectl to edit the domain resource]({{< relref "/userguide/managing-domains/domain-lifecycle/scaling#on-demand-updating-the-domain-resource-directly" >}})
* [Using the operator's REST APIs]({{< relref "/userguide/managing-domains/domain-lifecycle/scaling#calling-the-operators-rest-scale-api" >}})
* [Using WLDF policies]({{< relref "/userguide/managing-domains/domain-lifecycle/scaling#using-a-wldf-policy-rule-and-script-action-to-call-the-operators-rest-scale-api" >}})
* [Using a Prometheus action]({{< relref "/userguide/managing-domains/domain-lifecycle/scaling#using-a-prometheus-alert-action-to-call-the-operators-rest-scale-api" >}})

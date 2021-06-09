---
title: "Manage WebLogic domains"
date: 2019-02-23T16:43:45-05:00
weight: 4
description: "Important considerations for WebLogic domains in Kubernetes."
---

#### Contents

* [Important considerations for WebLogic domains in Kubernetes](#important-considerations-for-weblogic-domains-in-kubernetes)
* [Meet Kubernetes resource name restrictions](#meet-kubernetes-resource-name-restrictions)
* [Creating and managing WebLogic domains](#creating-and-managing-weblogic-domains)
* [Modifying domain configurations](#modifying-domain-configurations)
* [About the Domain resource](#about-the-domain-resource)
* [Managing lifecycle operations](#managing-lifecycle-operations)
* [Scaling clusters](#scaling-clusters)
* [About domain events](#about-domain-events)
* [Log files](#log-files)

#### Important considerations for WebLogic domains in Kubernetes

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
  [WebLogic domain in container image protection]({{<relref "/security/domain-security/image-protection#weblogic-domain-in-container-image-protection">}}).
  {{% /notice %}}

* _Log File Locations:_ The operator can automatically override WebLogic Server, domain, and introspector log locations.
  This occurs if the Domain `logHomeEnabled` field is explicitly set to `true`, or if `logHomeEnabled` isn't set
  and `domainHomeSourceType` is set to `PersistentVolume`.  When overriding, the log location will be the location specified by the `logHome` setting.
  For additional log file tuning information, see [Log files](#log-files).

* _Listen Address Overrides:_  The operator will automatically override all WebLogic domain default,
  SSL, admin, or custom channel listen addresses (using situational configuration overrides).  These will become `domainUID` followed by a
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
  For more information, see [T3 channels]({{<relref "/security/domain-security/weblogic-channels#weblogic-t3-channels">}}).
  {{% /notice %}}

* _Host Path Persistent Volumes:_ If using a `hostPath` persistent volume, then it must be available on all worker nodes in the cluster and have read/write/many permissions for all container/pods in the WebLogic Server deployment.  Be aware
  that many cloud provider's volume providers may not support volumes across availability zones.  You may want to use NFS or a clustered file system to work around this limitation.

* _Security Note:_ The `USER_MEM_ARGS` environment variable defaults to `-Djava.security.egd=file:/dev/./urandom` in all WebLogic Server pods and the WebLogic introspection job. It can be explicitly set to another value in your Domain YAML file using the `env` attribute under the `serverPod` configuration.

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

### Meet Kubernetes resource name restrictions

Kubernetes requires that the names of some resource types follow the DNS label standard as defined in [DNS Label Names](https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#dns-label-names) and [RFC 1123](https://tools.ietf.org/html/rfc1123). This requirement restricts the characters that are allowed in the names of these resources, and also limits the length of these names to no more than 63 characters.

The following is a list of such Kubernetes resources that the operator generates when a domain resource is deployed, including how their names are constructed.

* A domain introspector job named `<domainUID>-<introspectorJobNameSuffix>`. The default suffix is `-introspector`, which can be overridden using the operator's Helm configuration `introspectorJobNameSuffix` (see [WebLogic domain management]({{< relref "/userguide/managing-operators/using-the-operator/using-helm#weblogic-domain-management" >}})).
* A ClusterIP type service and a pod for each WebLogic Server named `<domainUID>-<serverName>`.
* A ClusterIP type service for each WebLogic cluster named `<domainUID>-cluster-<clusterName>`.
* An optional NodePort type service, also known as an external service, for the WebLogic Administration Server named `<domainUID>-<adminServerName>-<externalServiceNameSuffix>`. The default suffix is `-ext`, which can be overridden using the operator's Helm configuration `externalServiceNameSuffix` (see [WebLogic domain management]({{< relref "/userguide/managing-operators/using-the-operator/using-helm#weblogic-domain-management" >}})).

The operator puts in place certain validation checks and conversions to prevent these resources from violating Kubernetes restrictions.
* All the names previously described can contain only the characters `A-Z`, `a-z`, `0-9`, `-`, or `_`, and must start and end with an alphanumeric character. Note that when generating pod and service names, the operator will convert configured names to lowercase and substitute a hyphen (`-`) for each underscore (`_`).
* A `domainUID` is required to be no more than 45 characters.
* WebLogic domain configuration names, such as the cluster names, Administration Server name, and Managed Server names must be kept to a legal length so that the resultant resource names do not exceed Kubernetes' limits.

When a domain resource or WebLogic domain configuration violates the limits, the domain startup will fail, and actual validation errors are reported in the domain resource's status.

### Creating and managing WebLogic domains

You can locate a WebLogic domain either in a persistent volume (PV) or in an image.
For examples of each, see the [WebLogic Kubernetes Operator samples]({{< relref "/samples/simple/domains/_index.md" >}}).

If you want to create your own container images, for example, to choose a specific set of patches or to create a domain
with a specific configuration or applications deployed, then you can create the domain custom resource
manually to deploy your domain.  This process is documented in [this
sample]({{< relref "/samples/simple/domains/manually-create-domain/_index.md" >}}).

### Modifying domain configurations

You can modify the WebLogic domain configuration for Domain in PV, Domain in Image, and Model in Image before deploying a Domain YAML file:

When the domain is in a persistent volume, you can use WLST or WDT to change the configuration.

For Domain in Image and Domain in PV you can use [configuration overrides]({{< relref "/userguide/managing-domains/configoverrides/_index.md" >}}).

Configuration overrides allow changing a configuration without modifying its original `config.xml` or system resource XML files, and supports
parameterizing overrides so that you can inject values into them from Kubernetes Secrets. For example, you can inject database user names, passwords,
and URLs that are stored in a secret.

For Model in Image you use [Runtime Updates]({{<relref "/userguide/managing-domains/model-in-image/runtime-updates.md" >}}).

### About the Domain resource

For more information, see [Domain resource]({{< relref "/userguide/managing-domains/domain-resource/_index.md" >}}).

### Managing lifecycle operations

You can perform lifecycle operations on WebLogic Servers, clusters, or domains.
See [Starting and stopping]({{< relref "/userguide/managing-domains/domain-lifecycle/startup.md" >}}) and [Restarting]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting.md" >}}) servers.

### Scaling clusters

The operator lets you initiate scaling of clusters in various ways:

* [Using kubectl to edit the Domain resource]({{< relref "/userguide/managing-domains/domain-lifecycle/scaling#on-demand-updating-the-domain-directly" >}})
* [Using the operator's REST APIs]({{< relref "/userguide/managing-domains/domain-lifecycle/scaling#calling-the-operators-rest-scale-api" >}})
* [Using WLDF policies]({{< relref "/userguide/managing-domains/domain-lifecycle/scaling#using-a-wldf-policy-rule-and-script-action-to-call-the-operators-rest-scale-api" >}})
* [Using a Prometheus action]({{< relref "/userguide/managing-domains/domain-lifecycle/scaling#using-a-prometheus-alert-action-to-call-the-operators-rest-scale-api" >}})

### About domain events

The operator generates Kubernetes events at key points during domain processing.
For more information, see [Domain events]({{< relref "/userguide/managing-domains/domain-events.md" >}}).

### Log files

The operator can automatically override WebLogic Server, domain, and introspector `.log` and `.out` locations.
This occurs if the Domain `logHomeEnabled` field is explicitly set to `true`, or if `logHomeEnabled` isn't set
and `domainHomeSourceType` is set to `PersistentVolume`.  When overriding, the log location will be the location specified by the `logHome` setting.

If you want to fine tune the `.log` and `.out` rotation behavior for WebLogic Servers and domains, then
you can update the related `Log MBean` in your WebLogic configuration. Alternatively, for WebLogic
Servers, you can set corresponding system properties in `JAVA_OPTIONS`:

- Here are some WLST offline examples for creating and accessing commonly tuned Log MBeans:

  ```javascript
  # domain log
  cd('/')
  create(dname,'Log')
  cd('/Log/' + dname);

  # configured server log for a server named 'sname'
  cd('/Servers/' + sname)
  create(sname, 'Log')
  cd('/Servers/' + sname + '/Log/' + sname)

  # templated (dynamic) server log for a template named 'tname'
  cd('/ServerTemplates/' + tname)
  create(tname,'Log')
  cd('/ServerTemplates/' + tname + '/Log/' + tname)
  ```

- Here is sample WLST offline code for commonly tuned Log MBean attributes:

  ```javascript
  # minimum log file size before rotation in kilobytes
  set('FileMinSize', 1000)

  # maximum number of rotated files
  set('FileCount', 10)

  # set to true to rotate file every time on startup (instead of append)
  set('RotateLogOnStartup', 'true')
  ```

- Here are the defaults for commonly tuned Log MBean attributes:

  | Log MBean Attribute | Production Mode Default | Development Mode Default |
  | --------- | ----------------------- | ------------------------ |
  | FileMinSize (in kilobytes) | 5000 | 500 |
  | FileCount | 100 | 7 |
  | RotateLogOnStartup | false | true |

- For WebLogic Server `.log` and `.out` files (including both dynamic and configured servers), you can alternatively
set logging attributes using system properties that start with `weblogic.log.`
and that end with the corresponding Log MBean attribute name.

  For example, you can include `-Dweblogic.log.FileMinSize=1000 -Dweblogic.log.FileCount=10 -Dweblogic.log.RotateLogOnStartup=true` in `domain.spec.serverPod.env.name.JAVA_OPTIONS` to set the behavior for all WebLogic Servers in your domain. For information about setting `JAVA_OPTIONS`, see [Domain resource]({{< relref "/userguide/managing-domains/domain-resource/_index.md#jvm-memory-and-java-option-environment-variables" >}}).

{{% notice warning %}}
Kubernetes stores pod logs on each of its nodes, and, depending on the Kubernetes implementation, extra steps may be necessary to limit their disk space usage.
For more information, see [Kubernetes Logging Architecture](https://kubernetes.io/docs/concepts/cluster-administration/logging/).
{{% /notice %}}

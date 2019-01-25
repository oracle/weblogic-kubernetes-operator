## Create and manage WebLogic domains

In this version of the operator, a WebLogic domain can be located either in a persistent volume (PV) or in a Docker image.
There are advantages to both approaches, and there are sometimes technical limitations of various
cloud providers that may make one approach better suited to your needs.
You can also mix and match on a domain-by-domain basis.

| Domain on a persistent volume | Domain in a Docker image |
| --- | --- |
| Let's you use the same standard read-only Docker image for every server in every domain. | Requires a different image for each domain, but all servers in that domain use the same image. |
| No state is kept in Docker images making them completely throw away (cattle not pets). | Runtime state should not be kept in the images, but applications and configuration are. |
| The domain is long-lived, so you can mutate the configuration or deploy new applications using standard methods (Administration Console, WLST, etc.) | If you want to mutate the domain configuration or deploy application updates, you must create a new image. |
| Logs are automatically placed on persistent storage.  | Logs are kept in the images, and sent to the pod's log (stdout) unless you manually place them on persistent storage.  |
| Patches can be applied by simply changing the image and rolling the domain.  | To apply patches, you must create a new domain-specific image and then roll the domain.  |
| Many cloud providers do not provide persistent volumes that are shared across availability zones, so you may not be able to use a single persistent volume.  You may need to use some kind of volume replication technology or a clustered file system. | You do not have to worry about volume replication across availability zones since each pod has its own copy of the domain.  WebLogic replication will handle propagation of any online configuration changes.  |
| CI/CD pipelines may be more complicated because you would probably need to run WLST against the live domain directory to effect changes.  | CI/CD pipelines are simpler because you can create the whole domain in the image and don't have to worry about a persistent copy of the domain.  |
| There are less images to manage and store, which could provide significant storage and network savings.  |  There are more images to manage and store in this approach. |
| You may be able to use standard Oracle-provided images or at least a very small number of self-built images, for example, with patches installed. | You may need to do more work to set up processes to build and maintain your images. |

### Preparing the Kubernetes cluster to run WebLogic domains

Perform these steps to prepare your Kubernetes cluster to run a WebLogic domain:

1. Create the domain namespace(s).  One or more domains can share a namespace. A single instance of the operator can manage multiple namespaces.

   ```
   $ kubectl create namespace domain-namespace-1
   ```

   Replace `domain-namespace-1` with name you want to use.  The name must follow standard Kubernetes naming conventions, that is, lower case,
   numbers, and hyphens.

1. Create a Kubernetes secret containing the Administration Server boot credentials.  You can do this manually or using
   [the provided sample](/kubernetes/samples/scripts/create-weblogic-domain-credentials/README.md).  To create
   the secret manually, use this command:

   ```
   $ kubectl -n domain-namespace-1 \
           create secret generic domain1-weblogic-credentials \
           --from-literal=username=weblogic \
           --from-literal=password=welcome1
   ```

   Replace `domain-namespace-1` with the namespace that the domain will be in.
   Replace `domain1-weblogic-credentials` with the name of the secret.  The operator expects the secret name to be
   the `domainUID` followed by the literal string `-weblogic-credentials` and many of the samples assume this name.
   Replace the string `weblogic` in the third line with the user name for the administrative user.
   Replace the string `welcome1` in the fourth line with the password.

1. Optionally, [create a PV & persistent volume claim (PVC)](/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/README.md) which can hold the domain home, logs, and application binaries.
   Even if you put your domain in a Docker image, you may wish to put the logs on a persistent volume so that they are available after the pods terminate.
   This may be instead of, or as well as, other approaches like streaming logs into Elasticsearch.
1. [Configure load balancer(s)](/kubernetes/samples/charts/README.md) to manage access to any WebLogic clusters.

### Important considerations for WebLogic domains in Kubernetes

Please be aware of the following important considerations for WebLogic domains
running in Kubernetes.

* _Domain Home Location:_ The WebLogic domain home location is determined by the domain resource `domainHome` if set,
  and otherwise a default location is determined by the `domainHomeInImage` setting. If a domain resource `domainHome` field is not set
  and `domainHomeInImage` is `true` (the default), the operator will
  assume that the domain home is a directory under `/u01/oracle/user_projects/domains/` and report an error if no domain is found
  or more than one domain is found.  If a domain resource `domainHome` field is not set and `domainHomeInImage` is `false`, the operator will
  assume that the domain home is `/shared/domains/DOMAIN_UID`.
* _Log File Locations:_ The operator can automatically override Weblogic domain and server log locations using situational
  configuration overrides.  This occurs if the domain resource `logHomeEnabled` field is explicitly set to `true`, or if `logHomeEnabled` isn't set
  and `domainHomeInImage` is explicitly set to `false`.   When overriding, the log location will be the location specified by the `logHome` setting.
* _Listen Address Configuration:_  Channel listen addresses in a configuration either must be left completely unset (for example, not set to anything), or must be set to the exact required value, which will be in the form of the `domainUID`
  followed by a hyphen and then the server name (with all lower case, underscores converted to hyphens).  For example `domain1-admin-server`. This includes default, SSL, admin, and custom channels.
* _Listen Address Overrides:_  The operator will automatically override all WebLogic domain default,
  SSL, admin, or custom channel listen addresses (using situational configuration overrides).  These will become `domainUID` followed by a
  hyphen and then the server name, all lower case, and underscores converted to hyphens.  For example, if `domainUID=domain1` and
  the WebLogic server name is `Admin_Server`, then its listen address becomes `domain1-admin-server`.
* _Domain, Cluster, Server, and Network-Access-Point Names:_ WebLogic domain, cluster, server, and network-access-point (channel)
  names must contain only the characters `A-Z`, `a-z`, `0-9`, `-`, or `_`.  This ensures that they can be converted to
  meet Kubernetes resource and DNS1123 naming requirements.  (When generating pod and service names, the operator will convert
  configured names to lower case and substitute a hyphen (`-`) for each underscore (`_`).)
* _Node Ports:_ If you choose to expose any WebLogic channels outside the Kubernetes cluster via a `NodePort`, for example, the
  administration port or a T3 channel to allow WLST access, you need to ensure that you allocate each channel a
  unique port number across the entire Kubernetes cluster.  If you expose the administration port in each WebLogic domain in
  the Kubernetes cluster, then each one must have a different port.  This is required because `NodePorts` are used to
  expose channels outside the Kubernetes cluster.  

  **IMPORTANT:** Exposing admin, RMI, or T3 capable channels via a node port
  can create an insecure configuration; in general only HTTP protocols should be made available externally and this exposure
  is usually accomplished by setting up an external load balancer that can access internal (non-NodePort) services.
* _Host Path Persistent Volumes:_ If using a `hostPath` persistent volume, then it must be available on all worker nodes in the cluster and have read/write/many permissions for all container/pods in the WebLogic Server deployment.  Be aware
  that many cloud provider's volume providers may not support volumes across availability zones.  You may want to use NFS or a clustered file system to work around this limitation.

The following features are not certified or supported in this release:

* Whole Server Migration
* Consensus Leasing
* Node Manager (although it is used internally for the liveness probe and to start WebLogic Server instances)
* Multicast
* Multitenancy
* Production redeployment

Please consult My Oracle Support Doc ID 2349228.1 for up-to-date information about the features of WebLogic Server that are supported in Kubernetes environments.


### Creating and managing WebLogic domains

In this version of the operator, a WebLogic domain can be located either in a persistent volume (PV) or in a Docker image.
For examples of each, see the [WebLogic operator samples](../kubernetes/samples/README.md).

If you wish to create your own Docker images, for example to choose a specific set of patches, or to create a domain
with a specific configuration and/or applications deployed, then you can create the domain custom resource
manually to deploy your domain.  This process is documented in [this
sample](../kubernetes/samples/scripts/create-weblogic-domain/manually-create-domain/README.md).

### Modifying domain configurations

You can modify the WebLogic domain configuration for both the "domain in persistent volume" and the "domain in image" options before deploying a domain resource:

* When the domain is in a persistent volume, you can use WLST or WDT to change the configuration.
* For either case you can use [configuration overrides](config-overrides.md).   

Configuration overrides allow changing a configuration without modifying its original `config.xml` or system resource XML files, and also support parameterizing overrides so that you can inject values into them from Kubernetes secrets.   For example, you can inject database user names, passwords, and URLs that are stored in a secret.

### About the Domain resource

More information about the Domain resource can be found [here](domain-resource.md).

### Managing lifecycle operations including shutting down and deleting domains

In Operator 2.0, you can perform lifecycle operations on WebLogic servers, clusters, or domains.
See [Starting, stopping, and restarting servers](server-lifecycle.md) and [Restarting WebLogic servers](restart.md).

### Scaling clusters

The operator let's you initiate scaling of clusters in various ways:

* [Using kubectl to edit the domain resource](scaling.md#on-demand-updating-the-domain-resource-directly)
* [Using the operator's REST APIs](scaling.md#calling-the-operators-rest-scale-api)
* [Using WLDF policies](scaling.md#using-a-wldf-policy-rule-and-script-action-to-call-the-operators-rest-scale-api)
* [Using a Prometheus action](scaling.md#using-a-prometheus-alert-action-to-call-the-operators-rest-scale-api)

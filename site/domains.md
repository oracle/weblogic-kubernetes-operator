**TODO** write me

## Create and manage WebLogic domains

In this version of the operator, a WebLogic domain can be located either in a persistent volume (PV) or in a Docker image.
There are advantages to both approaches, and there are sometimes technical limitations of various 
cloud providers that may make one approach better suited to your needs.
You can also mix and match on a domain by domain basis.

| Domain on a persistent volume | Domain in a Docker image |
| --- | --- |
| Allows you to use the same standard read-only Docker image for every server in every domain. | Requires a different image for each domain, but all servers in that domain use the same image. |
| No state is kept in Docker images making them completely throw away (cattle not pets). | Runtime state should not be kept in the images, but applications and confguration are. |
| The domain is long-lived, so you can mutate the configuration or deploy new applications using standard methods (admin console, WLST, etc.) | If you want to mutate the domain configuration or deploy application updates, you must create a new image. |
| Logs are automatically placed on persistent storage.  | Logs are kept in the images, and sent to the Pod's log (stdout) unless you manually place them on persistent storage.  |
| Patches can be applied by simply changing the image and rolling the domain.  | To apply patches, you must create a new domain-specific image and then roll the domain.  | 
| Many cloud providers do not provide persistent volumes that are shared across availability zones, so you may not be able to use a single persistent volume.  You may need to use some kind of volume replication technology or a clustered file system. | You do not have to worry about volume replication across availability zones since each Pod has its own copy of the domain.  WebLogic replication will handle propagation of any online configuration changes.  | 
| CI/CD pipelines may be more complicated because you would probably need to run WLST against the live domain directory to effect changes.  | CI/CD pipelines are simpler because you can create the whole domain in the image and don't have to worry about a persistent copy of the domain.  | 
| There are less images to manage and store, which could provide significant storage and network savings.  |  There are more images to manage and store in this approach. |
| You may be able to use standard Oracle-provided images or at least a very small number of self-built images, e.g. with patches installed. | You may need to do more work to set up processes to build and maintain your images. |

* WebLogic binary image when domain is persisted to a PV (as in Operator v1.1)
* WebLogic domain image where the domain is persisted to a Docker image (new for Operator v2.0).  The WebLogic domain image will contain the WebLogic binaries, domain configuration, and applications.

You create the WebLogic domain inside of a Docker image or in a PV using WebLogic Scripting Tool (WLST) or WebLogic Deploy Tooling (WDT).  
* (Describe the advantages of using WDT. See samples, Domain in image WDT, Domain in image WLST, Domain in PV WDT, Domain in PV WLST.)

(What images do we need before we get started? Operator 2.0 requires you to patch your WebLogic image 12.2.1.3 with patch #.)

### Preparing the Kubernetes cluster to run WebLogic domains

Perform these steps to prepare your Kubernetes cluster to run a WebLogic domain:

1. Create the domain namespace(s).  One or more domains can share a namespace. A single instance of the operator can manage multiple namespaces.

   ``` 
   kubectl create namespace domain-namespace-1
   ```

   Replace `domain-namespace-1` with name you want to use.  The name must follow standard Kubernetes naming conventions, i.e. lower case, 
   numbers and hyphens.

1. Define RBAC roles for the domain.  **TODO** what RBAC roles?
1. Create a Kubernetes secret containing the Administration Server boot credentials.  You can do this manually or using 
   [the provided sample](/kubernetes/samples/scripts/create-weblogic-domain-credentials/README.md).  To create
   the secret manually, use this command: 
   
   ``` 
   kubectl -n domain-namespace-1 \ 
           create secret generic domain1-weblogic-credentials \
           --from-literal=username=weblogic \
           --from-literal=password=welcome1
   ```
   
   Replace `domain-namespace-1` with the namespace that the domain will be in.
   Replace `domain1-weblogic-credentials` with the name of the secret.  The operator expects the secret name to be
   the `domainUID` followed by the literal string `-weblogic-credentials` and many of the samples assume this name. 
   Replace the string `weblogic` in the third line with the username for the administrative user. 
   Replace the string `welcome1` in the fourth line with the password.
   
1. Optionally, [create a PV & persistent volume claim (PVC)](kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/README.md) which can hold the domain home, logs, and application binaries.
   Even if you put your domain in a Docker image, you may wish to put the logs on a persistent volume so that they are avilable after the Pods terminate.
   This may be instead of, or as well as, other approaches like streaming logs into Elasticsearch.
1. [Configure load balancer(s)](kubernetes/samples/charts/README.md) to manage access to any WebLogic clusters.

### Important considerations for WebLogic domains in Kubernetes

Please be aware of the following important considerations for WebLogic domains
running in Kubernetes.

* Channel Listen Addresses in a configuration must either be left completely unset (e.g. not set to anything), or must be set to the exact required value, which will be in the form of the `domainUID` 
  followed by a hyphen and then the server name (with all lower case, underscores converted to dashes).  For example `domain1-admin-server`. This includes default, SSL, admin, and custom channels.     
* If you choose to expose any WebLogic channels outside the Kubernetes cluster, e.g. the administration port or a T3 channel to 
  allow WLST access, you need to ensure that you allocate each channel a unique port number across the entire 
  Kubernetes cluster.  If you expose the administration port in each WebLogic domain in the Kubernetes cluster, then each one must 
  have a different port.  This is required because `NodePorts` are used to expose channels outside the Kubernetes cluster.
* If using a `hostPath` persistent volume, then it must be available on all worker nodes in the cluster and have read/write/many permissions for all container/pods in the WebLogic Server deployment.  Be aware 
  that many cloud provider's volume providers may no support volumes across availability zones.  You may want to use NFS or a clustered file system to work around this limitation.

The following features are not certified or supported in this release:

* Whole Server Migration,
* Consensus Leasing,
* Node Manager (although it is used internally for the liveness probe and to start WebLogic Server instances),
* Multicast,
* Multitenancy, and
* Production redeployment.

Please consult My Oracle Support Doc ID 2349228.1 for up-to-date information about the features of WebLogic Server that are supported in Kubernetes environments.


### Creating and managing WebLogic domains

To create and manage a WebLogic domain in Kubernetes we create a deployment type, the domain custom resource.   The operator introspects the custom resource and manages the domain deployment to adjust to the definitions in the custom resource. This custom resource can also be managed using the Kubernetes command-line interface `kubectl`.  
* (Point to documentation how to edit the domain inputs YAML and how to create the domain custom resource.)
* Create Ingress controllers if you are using a load balancer that supports them, such as Traefik or Voyager.

### Modifying domain configurations

You can modify the WebLogic domain configuration for both the "domain in persistent volume" and the "domain in image" options before deploying a domain resource:

* When the domain is in a persistent volume, you can use WLST or WDT to change the configuration, or
* For either case you can use [configuration overrides](config-overrides.md).   

Configuration overrides allow changing a configuration without modifying its original `config.xml` or system resource xml files, and also support parameterizing overrides so that you can inject values into them from Kubernetes secrets.   For example, you can inject database usernames, passwords, and URLs that are stored in a secret.

### Managing lifecycle operations

In Operator 2.0 you can perform lifecycle operations on WebLogic servers, clusters, or domains.
* Point to the documentation on how to manage lifecycle operations.

### Patching WebLogic and performing application updates

### Scaling clusters

The operator allows you to initiate scaling of clusters in various ways:

* [Using kubectl to edit the domain resource](scaling.md#on-demand-updating-the-domain-resource-directly)
* [Using the operator's REST APIs](scaling.md#calling-the-operators-rest-scale-api)
* [Using WLDF policies](scaling.md#using-a-wldf-policy-rule-and-script-action-to-call-the-operators-rest-scale-api)
* [Using a Prometheus action](scaling.md#using-a-prometheus-alert-action-to-call-the-operators-rest-scale-api)

###  Shutting down domains

###  Deleting domains
(Point to sample)

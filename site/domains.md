**TODO** write me

## Create and manage WebLogic domains

In this version of the operator, a WebLogic domain can be persisted either to a persistent volume (PV) or in a Docker image.
(Describe the pros and cons of both these approaches.)

* WebLogic binary image when domain is persisted to a PV (as in Operator v1.1)
* WebLogic domain image where the domain is persisted to a Docker image (new for Operator v2.0).  The WebLogic domain image will contain the WebLogic binaries, domain configuration, and applications.

You create the WebLogic domain inside of a Docker image or in a PV using WebLogic Scripting Tool (WLST) or WebLogic Deploy Tooling (WDT).  
* (Describe the advantages of using WDT. See samples, Domain in image WDT, Domain in image WLST, Domain in PV WDT, Domain in PV WLST.)

(What images do we need before we get started? Operator 2.0 requires you to patch your WebLogic image 12.2.1.3 with patch #.)

### Preparing the Kubernetes cluster to run WebLogic domains

Perform these steps to prepare your Kubernetes cluster to run a WebLogic domain:

* Create the domain namespace.  One or more domains can share a namespace.
* Define RBAC roles for the domain.
* Create a Kubernetes secret for the Administration Server boot credentials.
* Optionally, [create a PV & persistent volume claim (PVC)](kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/README.md) which can hold the domain home, logs, and application binaries.
* [Configure a load balancer](kubernetes/samples/charts/README.md) to manage the domains and ingresses.

### Important considerations for WebLogic domains in Kubernetes

Please be aware of the following important considerations for WebLogic domains
running in Kubernetes.

* Channel Listen Addresses in a configuration must either be left completely unset (e.g. not set to anything), or must be set to the exact required value of ‘DOMAIN_UID-SERVER_NAME’ (with all lower case, underscores converted to dashes).  This includes default, SSL, admin, and custom channels.     

The following features are not certified or supported in this release:

* Whole Server Migration,
* Consensus Leasing,
* Node Manager (although it is used internally for the liveness probe and to start WebLogic Server instances),
* Multicast,
* If using a hostPath persistent volume, then it must have read/write/many permissions for all container/pods in the WebLogic Server deployment,
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

###  Shutting down domains

###  Deleting domains
(Point to sample)

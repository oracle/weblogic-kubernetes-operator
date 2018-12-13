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

### Creating and managing WebLogic domains

To create and manage a WebLogic domain in Kubernetes we create a deployment type, the domain custom resource.   The operator introspects the custom resource and manages the domain deployment to adjust to the definitions in the custom resource.   This custom resource can also be managed using the Kubernetes command-line interface `kubectl`.  
* (Point to documentation how to edit the domain inputs YAML and how to create the domain custom resource.)
* Create Ingress controllers if you are using a load balancer that supports them, such as Traefik or Voyager.

### Managing lifecycle operations

In Operator 2.0 you can perform lifecycle operations on WebLogic servers, clusters, or domains.
* Point to the documentation on how to manage lifecycle operations.

### Modifying domain configurations
You can modify the WebLogic domain configuration for both the domain in PV and the domain in image:

* When the domain is in a PV, use WLST or WDT to change the configuration.
* Use configuration overrides when using the domain in image.(Check with Tom B, "The automatic and custom overrides apply to all domains - not just domain-in-image domains." Point to the documentation.)

### Patching WebLogic and performing application updates

###  Shutting down domains

###  Deleting domains
(Point to sample)

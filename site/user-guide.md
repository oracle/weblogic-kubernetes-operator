# User Guide

**TOOO** put intro here

## Table of contents

**TODO** this is not the final TOC - we will restructure/reorganize for usability....

* [Important terms](#important-terms)
* [Getting started](#getting-started)
* [Prerequisites](#prerequisites)
* [Preparing your Kubernetes environment](#preparing-your-kubernetes-environment)
  * [Set up your Kubernetes cluster](#set-up-your-kubernetes-cluster)
  * [Set up load balancers](#set-up-load-balancers)
  * [Configuring Kibana and Elasticsearch](#configuring-kibana-and-elasticsearch)
* [Create and manage the operator](#create-and-manage-the-operator)
  * [Starting the operator](#starting-the-operator)
  * [Modifying the operator](#modifying-the-operator)
  * [Shutting down the operator](#shutting-down-the-operator)
* [Creating or obtaining WebLogic Docker images](#creating-or-obtaining-weblogic-docker-images)
  * [Obtaining standard images from the Docker store](#obtaining-standard-images-from-the-docker-store)
  * [Creating a custom images with patches applied](#creating-a-custom-images-with-patches-applied)
  * [Creating a custom image with your domain inside the image](#creating-a-custom-image-with-your-domain-inside-the-image)
* [Create and manage WebLogic domains](#create-and-manage-weblogic-domains)
  * [Preparing the Kubernetes cluster to run WebLogic domains](#preparing-the-kubernetes-cluster-to-run-weblogic-domains)
  * [Creating and managing WebLogic domains](#creating-and-managing-weblogic-domains)
  * [Managing lifecycle operations](#managing-lifecycle-operations)
  * [Modifying domain configurations](#modifying-domain-configurations)
  * [Patching WebLogic and performing application updates](#patching-weblogic-and-performing-application-updates)
  * [Shutting down domains](#shutting-down-domains)
  * [Deleting domains](#deleting-domains)
* [Additional integrations](#additional-integrations)
  * [Sending WebLogic metrics to Prometheus](#sending-weblogic-metrics-to-prometheus)


## Important terms

This documentation uses several important terms which are intended to have a specific meaning.

|Term	| Definition |
| --- | --- |
| Cluster	| Because this term is ambiguous, it will be prefixed to indicate which type of cluster is meant.  A WebLogic cluster is a group of Managed Servers that together host some application or component and which are able to share load and state between them.  A Kubernetes cluster is a group of machines (“nodes”) that all host Kubernetes resources, like pods and services, and which appear to the external user as a single entity.  If the term “cluster” is not prefixed, it should be assumed to mean a Kubernetes cluster. |
| Domain	| A WebLogic domain is a group of related applications and resources along with the configuration information necessary to run them. |
| Ingress	| A Kubernetes Ingress provides access to applications and services in a Kubernetes environment to external clients.  An Ingress may also provide additional features like load balancing. |
| Namespace	| A Kubernetes namespace is a named entity that can be used to group together related objects, for example, pods and services. |
| Operator	| A Kubernetes operator is software that performs management of complex applications. |
| Pod	    | A Kubernetes pod contains one or more containers and is the object that provides the execution environment for an instance of an application component, such as a web server or database. |
| Job	    | A Kubernetes job is a type of controller that creates one or more pods that run to completion to complete a specific task. |
| Secret	| A Kubernetes secret is a named object that can store secret information like usernames, passwords, X.509 certificates, or any other arbitrary data. |
| Service	| A Kubernetes service exposes application endpoints inside a pod to other pods, or outside the Kubernetes cluster.  A service may also provide additional features like load balancing. |

## Getting started

Before using the operator, you might want to read the [design philosophy](site/design.md) to develop an understanding of the operator's design, and the [architectural overview](site/architecture.md) to understand its architecture, including how WebLogic domains are deployed in Kubernetes using the operator. Also, worth reading are the details of the [Kubernetes RBAC definitions](site/rbac.md) required by the operator.

## Prerequisites

* Kubernetes 1.10.11+, 1.11.5+, and 1.12.3+  (check with `kubectl version`).
* Flannel networking v0.9.1-amd64 (check with `docker images | grep flannel`)
* Docker 18.03.1.ce (check with `docker version`)
* Oracle WebLogic Server 12.2.1.3.0
* If you wish to use dynamic clusters and/or Configuration Overrides, patches 28186730 and 28076014 are required

## Preparing your Kubernetes environment

**TODO** write intro 

### Set up your Kubernetes cluster

If you need help setting up a Kubernetes environment, check our [cheat sheet](site/k8s_setup.md).

After creating Kubernetes clusters, you can optionally:
* Create load balancers to direct traffic to backend domains.
* Configure Kibana and Elasticsearch for your operator logs.

### Set up load balancers

Use these [scripts and Helm charts](kubernetes/samples/README.md) to install Traefik, Apache, or Voyager load balancers.

### Configuring Kibana and Elasticsearch

You can send the operator logs to Elasticsearch, to be displayed in Kibana. Use this [sample script](kubernetes/samples/scripts/elasticsearch_and_kibana.yaml) to configure Elasticsearch and Kibana deployments and services.

## Create and manage the operator

An operator is an application-specific controller that extends Kubernetes to create, configure, and manage instances of complex applications. The WebLogic Kubernetes Operator follows the standard Kubernetes Operator pattern, and simplifies the management and operation of WebLogic domains and deployments. You can find the operator image in [Docker Hub](https://hub.docker.com/r/oracle/weblogic-kubernetes-operator/).

In your Kubernetes cluster you can have one or more operators that manage one or more WebLogic domains (running in a cluster). We provide a Helm chart to create the operator. (Point to the documentation and sample that describes how to do the steps below.)


### Starting the operator

* Create the namespace and service account for the operator (See the script and doc that describe how to do these.)
* Edit the operator input YAML
* Start the operator with a Helm chart

### Modifying the operator

(images, RBAC roles, ...)

### Shutting down the operator
(See the operator sample README section that describes how to shutdown and delete all resources for the operator.)

## Creating or obtaining WebLogic Docker images

**TODO** write me

### Obtaining standard images from the Docker store 

**TODO** write me

### Creating a custom images with patches applied 

**TODO** write me

### Creating a custom image with your domain inside the image

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

## Additional integrations

**TODO** write me

### Sending WebLogic metrics to Prometheus

**TODO** write me 

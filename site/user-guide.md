# User guide

This document provides detailed user information for the Oracle WebLogic
Server Kubernetes Operator.  It provides instructions on how to install the operator in your
Kubernetes cluster and how to use it to manage WebLogic domains.  

If you are looking for information about how the operator is designed, implemented, built, and such, then
you should refer to the [Developer guide](developer.md).

## Table of contents

The information in this guide is organized in the order that you would most likely need to use it.  If you
want to set up an operator and use it to create and manage WebLogic domains, you should
follow this guide from top to bottom, and the necessary information will be
presented in the correct order.

* [Important terms](#important-terms)
* [Getting started](#getting-started)
  * [Operator Docker image](#operator-docker-image)
  * [Exposing applications outside the Kubernetes cluster](#exposing-applications-outside-the-kubernetes-cluster)
* [Prerequisites](#prerequisites)
* [Preparing your Kubernetes environment to run the operator](prepare-k8s.md)
  * [Set up your Kubernetes cluster](k8s_setup.md)
  * [Set up load balancers](../kubernetes/samples/charts/README.md)
  * [Configuring Kibana and Elasticsearch](../kubernetes/samples/scripts/elasticsearch-and-kibana/README.md)
* [Install and manage the operator](install.md)
  * [Using the Helm charts](install.md#install-helm-and-tiller)
  * [Using the operator's REST interface](rest.md)
* [Creating or obtaining WebLogic Docker images](weblogic-docker-images.md)
  * [Obtaining standard images from the Docker store](weblogic-docker-images.md#obtaining-standard-images-from-the-docker-store)
  * [Creating a custom image with patches applied](weblogic-docker-images.md#creating-a-custom-image-with-patches-applied)
  * [Creating a custom image with your domain inside the image](weblogic-docker-images.md#creating-a-custom-image-with-your-domain-inside-the-image)
* [Create and manage WebLogic domains](domains.md)
  * [Preparing the Kubernetes cluster to run WebLogic domains](domains.md#preparing-the-kubernetes-cluster-to-run-weblogic-domains)
  * [Important considerations for WebLogic domains in Kubernetes](domains.md#important-considerations-for-weblogic-domains-in-kubernetes)
  * [Creating and managing WebLogic domains](domains.md#creating-and-managing-weblogic-domains)
  * [Modifying domain configurations](domains.md#modifying-domain-configurations)
  * [Managing lifecycle operations](domains.md#managing-lifecycle-operations-including-shutting-down-and-deleting-domains)
  * [Using WLST](wlst.md)
  * [Scaling clusters](scaling.md)
  * [Information about load balancing with Ingresses](ingress.md)

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
| Secret	| A Kubernetes secret is a named object that can store secret information like user names, passwords, X.509 certificates, or any other arbitrary data. |
| Service	| A Kubernetes service exposes application endpoints inside a pod to other pods, or outside the Kubernetes cluster.  A service may also provide additional features like load balancing. |

## Getting started

Before using the operator, you might want to read the [design philosophy](design.md) to develop an understanding of the operator's design, and the [architectural overview](architecture.md) to understand its architecture, including how WebLogic domains are deployed in Kubernetes using the operator. Also, worth reading are the details of the [Kubernetes RBAC definitions](rbac.md) required by the operator.

An operator is an application-specific controller that extends Kubernetes to create, configure, and manage instances
of complex applications. The Oracle WebLogic Server Kubernetes Operator follows the standard Kubernetes operator pattern, and
simplifies the management and operation of WebLogic domains and deployments.

You can have one or more operators in your Kubernetes cluster that manage one or more WebLogic domains each.
We provide a Helm chart to manage the installation and configuration of the operator.
Detailed instructions are available [here](install.md).

### Operator Docker image

You can find the operator image in
[Docker Hub](https://hub.docker.com/r/oracle/weblogic-kubernetes-operator/).

### Exposing applications outside the Kubernetes cluster
The operator can configure services to expose WebLogic applications and features outside of the Kubernetes cluster.  Care should be taken when exposing anything externally to ensure that the appropriate security considerations are taken into account. In this regard, there is no significant difference between a WebLogic domain running in a Kubernetes cluster and a domain running in a traditional data center.  The same kinds of considerations should be taken into account, for example:

* Only expose those protocols and ports that need to be exposed.
*	Use secure protocols (HTTPS, T3S, and such).
*	Use custom channels to restrict the protocols that are exposed.
*	Is load balancing required?
*	Is certificate-based integrity needed?
*	How will users authenticate?
* Is the network channel encrypted?
* Is exposing RMI required (T3 enables RMI), or IIOP, or would HTTP alone suffice?
* Is it necessary to expose admin-capable channels?
* Is it required to expose default channels (these accept T3/RMI, and by default, accept administrative traffic)?

While it is natural to expose web applications outside the cluster, exposing administrative features like the Administration Console and a T3 channel for WLST should be given more careful consideration.  There are alternative options that should be weighed.  For example, Kubernetes provides the ability to securely access a shell running in a container in a pod in the cluster.  WLST could be executed from such an environment, meaning the T3 communications are entirely within the Kubernetes cluster and therefore more secure.

Oracle recommends careful consideration before deciding to expose any administrative or non-HTTP(S) interfaces or protocols externally.


## Prerequisites

* Kubernetes 1.10.11+, 1.11.5+, and 1.12.3+  (check with `kubectl version`).
* Flannel networking v0.9.1-amd64 (check with `docker images | grep flannel`).
* Docker 18.03.1.ce (check with `docker version`).
* Helm 2.8.2+ (check with `helm version`).
* Oracle WebLogic Server 12.2.1.3.0 with patch 29135930.
   * The existing WebLogic Docker image, `store/oracle/weblogic:12.2.1.3`,
was updated on January 17, 2019, and has all the necessary patches applied.
   * A `docker pull` is required if you pulled the image prior to that date.
* You must have the `cluster-admin` role to install the operator.

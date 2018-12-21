# User guide

This document provides detailed information targeted to users of the Oracle WebLogic
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
* [Prerequisites](#prerequisites)
* [Preparing your Kubernetes environment to run the operator](prepare-k8s.md)
  * [Set up your Kubernetes cluster](prepare-k8s.md#set-up-your-kubernetes-cluster)
  * [Set up load balancers](prepare-k8s.md#load-balancing-with-an-ingress-controller-or-a-web-server)
  * [Configuring Kibana and Elasticsearch](prepare-k8s.md#configuring-kibana-and-elasticsearch)
* [Install and manage the operator](install.md)
  * [Using the Helm charts](helm-charts.md)
* [Using the operator's REST interface](rest.md)
* [Creating or obtaining WebLogic Docker images](weblogic-docker-images.md)
  * [Obtaining standard images from the Docker store](weblogic-docker-images.md#obtaining-standard-images-from-the-docker-store)
  * [Creating a custom images with patches applied](weblogic-docker-images.md#creating-a-custom-images-with-patches-applied)
  * [Creating a custom image with your domain inside the image](weblogic-docker-images.md#creating-a-custom-image-with-your-domain-inside-the-image)
* [Create and manage WebLogic domains](domains.md)
  * [Preparing the Kubernetes cluster to run WebLogic domains](domains.md#preparing-the-kubernetes-cluster-to-run-weblogic-domains)
  * [Important considerations for WebLogic domains in Kubernetes](domains.md#important-considerations-for-weblogic-domains-in-kubernetes)
  * [Creating and managing WebLogic domains](creating-domain.md)
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
| Secret	| A Kubernetes secret is a named object that can store secret information like usernames, passwords, X.509 certificates, or any other arbitrary data. |
| Service	| A Kubernetes service exposes application endpoints inside a pod to other pods, or outside the Kubernetes cluster.  A service may also provide additional features like load balancing. |

## Getting started

Before using the operator, you might want to read the [design philosophy](design.md) to develop an understanding of the operator's design, and the [architectural overview](architecture.md) to understand its architecture, including how WebLogic domains are deployed in Kubernetes using the operator. Also, worth reading are the details of the [Kubernetes RBAC definitions](rbac.md) required by the operator.

## Prerequisites

* Kubernetes 1.10.11+, 1.11.5+, and 1.12.3+  (check with `kubectl version`).
* Flannel networking v0.9.1-amd64 (check with `docker images | grep flannel`).
* Docker 18.03.1.ce (check with `docker version`).
* Helm 2.8.2+ (check with `helm version`).
* Oracle WebLogic Server 12.2.1.3.0 with patch 28076014.
* You must have the `cluster-admin` role to install the operator.

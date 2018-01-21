# Oracle WebLogic Server Kubernetes Operator

[![wercker status](https://app.wercker.com/status/6381f32f663a4da75a8e012a407f7b22/s/master "wercker status")](https://app.wercker.com/project/byKey/6381f32f663a4da75a8e012a407f7b22) Built with [Wercker](http://www.wercker.com)

Many organizations are exploring, testing, or actively moving application workloads into a cloud environment, either in house or using an external cloud provider.  Kubernetes has emerged as a leading cloud platform and is seeing widespread adoption.  But a new computing model does not necessarily mean new applications or workloads – many of the existing application workloads running in environments designed and built over many years, before the ‘cloud era’, are still mission critical today.  As such, there is a lot of interest in moving such workloads into a cloud environment, like Kubernetes, without forcing application rewrites, retesting and additional process and cost.  There is also a desire to not just run the application in the new environment, but to run it ‘well’ – to adopt some of the idioms of the new environment and to realize some of the benefits of that new environment.

Oracle has been working with the WebLogic community to find ways to make it is easy as possible for organizations using WebLogic to run important workloads to move those workloads into the cloud.  One aspect of that effort is the creation of the Oracle WebLogic Server Kubernetes Operator.  The Technology Preview release of the Operator provides a number of features to assist with the management of WebLogic Domains in a Kubernetes environment, including:

*	A mechanism to create a WebLogic Domain on a Kubernetes Persistent Volume,
*	A mechanism to define a WebLogic Domain as a Kubernetes resource (using a Kubernetes Custom Resource Definition),
*	The ability to automatically start servers based on declarative startup parameters and desired states,
*	The ability to automatically expose the WebLogic Administration Console outside the Kubernetes Cluster (if desired),
*	The ability to automatically expose T3 Channels outside the Kubernetes Cluster (if desired),
*	The ability to automatically expose HTTP paths on a WebLogic Cluster outside the Kubernetes Cluster with load balancing, and to update the load balancer when Managed Servers in the WebLogic Cluster are started or stop,
*	The ability to scale a WebLogic Cluster by starting and stopping Managed Servers on demand, or by integrating with a REST API to initiate scaling based on WLDF, Prometheus/Grafana or other rules, and
*	The ability to publish Operator and WebLogic logs into ElasticSearch and interact with them in Kibana.

As part of Oracle’s ongoing commitment to open source in general, and to Kubernetes and the Cloud Native Computing Foundation specifically, Oracle has open sourced the Operator and is committed to enhancing it with additional features.  Oracle welcomes feedback, issues, pull requests, and feature requests from the WebLogic community.

# Important terms

In this documentation, several important terms are used and are intended to have a specific meaning.  These terms are typeset in *italics*.

## IMPORTANT TERMS USED IN THIS DOCUMENT
|Term	| Definition |
| --- | --- |
| Cluster	| Since this term is ambiguous, it will be prefixed to indicate which type of cluster is meant.  A WebLogic cluster is a group of managed servers that together host some application or component and which are able to share load and state between them.  A Kubernetes cluster is a group of machines (“nodes”) that all host Kubernetes resources like pods and services and which appear to the external user as a single entity.  If the term “cluster” is not prefixed, it should be assumed to mean a Kubernetes cluster. |
| Domain	| A WebLogic domain is a group of related applications and resources along with the configuration information necessary to run them. |
| Ingress	| A Kubernetes ingress provides access to applications and services in a Kubernetes environment to external clients.  An Ingress may also provide additional features like load balancing. |
| Job	 | write me |
| Namespace	| A Kubernetes namespace is a named entity that can be used to group together related objects, e.g. pods and services. |
| Operator	| A Kubernetes operator is a piece of software that performs management of complex applications. |
|Pod	| A Kubernetes pod contains one or more containers and is the object that provides the execution environment for an instance of an application component, e.g. a web server or database. |
| Secret	| A Kubernetes secret is a named object that can store secret information like usernames, passwords, X.509 certificates, or any other arbitrary data. |
|Service	| A Kubernetes service exposes application endpoints inside a pod to other pods, or outside the Kubernetes cluster.  A service may also provide additional features like load balancing. |


# Getting Started

Before using the operator, it is highly recommended to read the [design philosophy](site/design.md) to develop an understanding of the operator's design, and the [architectural overview](site/architecture.md) to understand its architecture, including how WebLogic domains are deployed in Kubernetes using the operator.  It is also worth reading the details of the [Kubernetes RBAC definitions](site/rbac.md) required by the operator.

# Exposing applications outside the Kubernetes cluster
The *operator* can configure *services* to expose WebLogic applications and features outside of the Kubernetes *cluster*.  Care should be taken when exposing anything externally to ensure that the appropriate security considerations are taken into account.  There is no significant difference between a WebLogic *domain* running in a Kubernetes *cluster* and a *domain* running in a traditional data center in this regard.  The same kinds of considerations should be taken into account, for example:

* Only expose those protocols and ports that need to be exposed.
*	Use secure protocols (HTTPS, T3S, etc.).
*	Use custom channels to restrict the protocols that are exposed.
*	Is load balancing required?
*	Is certificate-based integrity needed?
*	How will users authenticate?  
* Is the network channel encrypted?

While it is natural to expose web applications outside the *cluster*, exposing administrative features like the administration console and a T3 channel for WLST should be given more careful consideration.  There are alternative options that should be weighed.  For example, Kubernetes provides the ability to securely access a shell running in a container in a *pod* in the *cluster*.  WLST could be executed from such an environment, meaning the T3 communications are entirely within the Kubernetes *cluster* and therefore more secure.

Oracle recommends careful consideration before deciding to expose any administrative interfaces externally.  

# Requirements

The Oracle WebLogic Server Kubernetes Operator has the following requirements:

*	Kubernetes 1.7.5+, 1.8.0+ or 1.9.0+ (check with `kubectl version`)
*	Flannel networking v0.9.1-amd64 (check with `docker images | grep flannel`)
*	Docker 17.03.1.ce (check with `docker version`)
*	Oracle WebLogic Server 12.2.1.3.0

Note: Minikube is not supported.

# Restrictions

The following features are not certified or supported in the Technology Preview release at the time of writing:

*	Whole Server Migration
*	Consensus Leasing
*	Node Manager (although it is used internally for the liveness probe and to start WLS Servers)
*	Dynamic Clusters (the current certification only covers configured clusters, certification of Dynamic Clusters is planned at a future date)
*	Multicast
*	If using a Host Path Persistent Volume, then it must have read/write/many permissions for all container/pods in the WebLogic deployment
*	Multitenancy
*	Production Redeployment

Please consult MyOracle Support Note XXXXX for up to date information about what features of WebLogic are supported in Kubernetes environments.

# API documentation

Documentation for APIs is provided here:

* [Javadoc](https://oracle.github.io/weblogic-kubernetes-operator/apidocs/index.html) for the operator.

* [Swagger](https://oracle.github.io/weblogic-kubernetes-operator/swagger/index.html) documentation for the operator's REST interface.

# User guide

## Prefer to see it rather than read about it?

If you would rather see the developers demonstrating the *operator* rather than reading the documentation, then here are your videos:

* [Installing the operator](https://youtu.be/Ndh3GkQy9H4)
* [Creating a WebLogic domain with the operator](https://youtu.be/wp_DZsG5FbI)
* [Accessing the WebLogic administration console and deploying an application](#)
* [Verifying the load balancing for a web application](#)
* [Scaling a WebLogic cluster with the operator](#)
* [Scaling a WebLogic cluster with WLDF](https://youtu.be/Q8iZi2e9HvU)
* [Using the operator's REST APIs](#)
* [Using WLST against a domain running in Kubernetes](#)
* [Viewing the operator's logs in Kibana](#)
* [Prometheus integration](https://youtu.be/D7KWVXzzqx8) including exporting WebLogic metrics to Prometheus and creating a Prometheus alert to trigger scaling
* [Shutting down a domain](#)
* [Deleting a domain](#)
* watch this space, more to come!

Like what you see?  Read on for all the nitty-gritty details...

## Installation

Before installing the Oracle WebLogic Server Kubernetes Operator, ensure that the requirements listed above are met.

The overall process of installing and configuring the *operator* and using it to manage WebLogic *domains* consists of the following steps.  The provided scripts will perform most of these steps, but some must be performed manually:

*	Register for access to the Oracle Container Registry
* Setting up secrets to access the Oracle Container Registry
*	Customizing the operator parameters file
*	Deploying the operator to a Kubernetes cluster
*	Setting up secrets for the admin server credentials
*	Creating a persistent volume for a WebLogic domain
*	Customizing the domain parameters file
*	Creating a WebLogic domain

These steps are explained in detail [here](site/installation.md). Example files are provided in the `kubernetes` directory in this repository.

## Using the operator's REST services

The *operator* provides a REST API that can be used to obtain information about the configuration and to initiate scaling actions. Please refer to [Using the operator's REST services](site/rest.md) for details of how to use the REST APIs.

## Creating a WebLogic domain with the operator

Please refer to [Creating a WebLogic domain with the operator](site/creating-domain.md) for information about how to create a WebLogic *domain* with the *operator*.

## Manually creating a WebLogic domain

If preferred, a *domain* can be created manually, i.e. without using the scripts provided with the *operator*.  As long as the *domain* follows the guidelines, it can still be managed by the *operator*.  Please refer to [Manually creating a WebLogic domain](site/manually-creating-domain.md) for details.  A good example of when manual *domain* creation may be preferred is when a user already has a set of existing WLST scripts that are used to create *domains* and they wish to reuse those same WLST scripts in Kubernetes, perhaps with some small modifications.

## Exporting WebLogic metrics to Prometheus

The *operator* includes the ability to export WebLogic metrics to Prometheus.  This allows metrics to be displays in Grafana, and permits the creation of alerts and initiation of scaling actions from Prometheus alerts.  Please refer to [Prometheus integration](site/prometheus.md) for more information.

## Starting up the domain

The *operator* will automatically start up *domains* that it is aware of, based on the configuration in the *domain custom resource*.  Please refer to [Startup up a WebLogic domain](site/starting-domain.md) for details.

## Using WLST

When creating a *domain* there is an option to expose a T3 channel outside of the Kubernetes *cluster* to allow remote WLST access.  Please refer to [Using WLST](site/wlst.md) for more information about how to use WLST with a *domain* running in Kubernetes.

## Scaling a cluster

The *operator* provides the ability to scale up or down WebLogic *clusters*.  There are several ways to initiate scaling, including:

* Updating the *domain custom resource* directly (using `kubectl`).
* Calling the *operator's* REST `scale` API, e.g. from `curl`.
* Using a WLDF rule and script action to call the *operator's* REST `scale` API.
* Using a Prometheus alert action to call the *operator's* REST `scale` API.

Please refer to [Scaling a WebLogic cluster](site/scaling.md) for more information.

## Shutting down a domain

Please refer to [Shutting down a domain](site/shutdown-domain.md) for information about how to shut down a *domain* running in Kubernetes.

## Load balancing with the Traefik ingress controller

The initial "Technology Preview" release of the *operator* supports only the Traefik load balancer/ingress controller.  Support for other load balancers is planned in the future.
Please refer to [Load balancing with the Traefik ingress controller](site/traefik.md) for information about current capabilities.

## Exporting operator logs to ELK

The *operator* provides an option to export its log files to the ELK stack.
Please refer to [ELK integration](site/elk.md) for information about this capability.

## Removing a domain

To permanently remove a domain from a Kubernetes *cluster*, first shut down the domain using the instructions provided above in the section titled “Shutting down a domain”, then remove the *persistent volume claim* and the *persistent volume* using these commands:

```
kubectl delete pvc PVC-NAME
kubectl delete pv PV-NAME
```

Find the names of the *persistent volume claim* and the *persistent volume* in the *domain custom resource* YAML file, or if it is not available, check for the `domainUID` in the metadata on the *persistent volumes*.

To permanently delete the actual *domain* configuration, delete the physical volume using the appropriate tools.  For example, if the *persistent volume* used the `HostPath provider`, delete the corresponding directory on the Kubernetes master.

Be aware that there may be metric data from the *domain* in Prometheus if this option was used.  These data will need to be deleted separately, if desired.

## Removing the operator

To remove the operator from a Kubernetes cluster, issue the following commands:

```
kubectl delete deploy weblogic-operator –n NAMESPACE
kubectl delete service external-weblogic-operator-service –n NAMESPACE
kubectl delete service internal-weblogic-operator-service –n NAMESPACE
```

Replace `NAMESPACE` with the *namespace* that the *operator* is running in.

To remove more than one *operator*, repeat these steps for each *operator namespace*.


# Developer Guide

Developers interested in this project are encouraged to read the [Developer guide](site/developer.md) to learn how to build the project, run tests, etc.  The Developer guide also provides details about the structure of the code, coding standards, and the Asynchronous Call facility used in the code to manage calls to the Kuberentes API.

Please take a look at our [wish list](https://github.com/oracle/weblogic-kubernetes-operator/wiki/Wish-list) to get an idea of what kind of features we would like to add to the *operator*.  Maybe you will see something you would like to contribute to!

# Contributing to the operator

Oracle welcomes contributions to this project from anyone.  Contributions may be reporting an issue with the *operator*, or submitting a pull request.  Before embarking on significant development that may result in a large pull request, it is recommended to create an issue and discuss the proposed changes with the existing developers first.

If you want to submit a pull request to fix a bug or enhance an existing feature, please first open an issue and link to that issue when you submit your pull request.

If you have any questions about a possible submission, feel free to open an issue too.

## Contributing to the Oracle Kubernetes Operator for WebLogic repository

Pull requests can be made under The Oracle Contributor Agreement (OCA) which is available at [https://www.oracle.com/technetwork/community/oca-486395.html](https://www.oracle.com/technetwork/community/oca-486395.html).

For pull requests to be accepted, the bottom of the commit message must have the following line using the contributor’s name and e-mail address as it appears in the OCA Signatories list.

```
Signed-off-by: Your Name <you@example.org>
```

This can be automatically added to pull requests by committing with:

```
git commit --signoff
```

Only pull requests from committers that can be verified as having signed the OCA can be accepted.

## Pull request process

*	Fork the repository.
*	Create a branch in your fork to implement the changes. We recommend using the issue number as part of your branch name, e.g. `1234-fixes`.
*	Ensure that any documentation is updated with the changes that are required by your fix.
*	Ensure that any samples are updated if the base image has been changed.
*	Submit the pull request. Do not leave the pull request blank. Explain exactly what your changes are meant to do and provide simple steps on how to validate your changes. Ensure that you reference the issue you created as well. We will assign the pull request to 2-3 people for review before it is merged.

## Introducing a new dependency

Please be aware that pull requests that seek to introduce a new dependency will be subject to additional review.  In general, contributors should avoid dependencies with incompatible licenses, and should try to use recent versions of dependencies.  Standard security vulnerability checklists will be consulted before accepting a new dependency.  Dependencies on closed-source code, including WebLogic, will most likely be rejected.

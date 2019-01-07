# Oracle WebLogic Server Kubernetes Operator Documentation

**WARNING** This directory contains the documentation for version 1.0 of the operator, which is an old release. 

If you wish to view documentation for the current version [please click here](..).

The remainder of this document is the main README from version 1.0:

# Oracle WebLogic Server Kubernetes Operator

Many organizations are exploring, testing, or actively moving application workloads into a cloud environment, either in house or using an external cloud provider.  Kubernetes has emerged as a leading cloud platform and is seeing widespread adoption.  But a new computing model does not necessarily mean new applications or workloads; many of the existing application workloads running in environments designed and built over many years, before the ‘cloud era’, are still mission critical today.  As such, there is a lot of interest in moving such workloads into a cloud environment, like Kubernetes, without forcing application rewrites, retesting, and additional process and cost.  There is also a desire to not just run the application in the new environment, but to run it ‘well’ – to adopt some of the idioms of the new environment and to realize some of the benefits of that new environment.

Oracle has been working with the WebLogic community to find ways to make it as easy as possible for organizations using WebLogic Server to run important workloads, to move those workloads into the cloud.  One aspect of that effort is the creation of the Oracle WebLogic Server Kubernetes Operator.  This release of the Operator provides a number of features to assist with the management of WebLogic domains in a Kubernetes environment, including:

*	A mechanism to create a WebLogic domain on a Kubernetes persistent volume. This persistent volume can reside in NFS.
*	A mechanism to define a WebLogic domain as a Kubernetes resource (using a Kubernetes custom resource definition).
*	The ability to automatically start servers based on declarative startup parameters and desired states.
* The ability to manage a WebLogic configured or dynamic cluster.
*	The ability to automatically expose the WebLogic Server Administration Console outside the Kubernetes cluster (if desired).
*	The ability to automatically expose T3 channels outside the Kubernetes domain (if desired).
*	The ability to automatically expose HTTP paths on a WebLogic domain outside the Kubernetes domain with load balancing, and to update the load balancer when Managed Servers in the WebLogic domain are started or stopped.
*	The ability to scale a WebLogic domain by starting and stopping Managed Servers on demand, or by integrating with a REST API to initiate scaling based on WLDF, Prometheus/Grafana, or other rules.
*	The ability to publish Operator and WebLogic Server logs into Elasticsearch and interact with them in Kibana.



As part of Oracle’s ongoing commitment to open source in general, and to Kubernetes and the Cloud Native Computing Foundation specifically, Oracle has open sourced the Operator and is committed to enhancing it with additional features.  Oracle welcomes feedback, issues, pull requests, and feature requests from the WebLogic community.

# Important terms

In this documentation, several important terms are used and are intended to have a specific meaning.

## Important terms used in this document
|Term	| Definition |
| --- | --- |
| Cluster	| Because this term is ambiguous, it will be prefixed to indicate which type of cluster is meant.  A WebLogic cluster is a group of Managed Servers that together host some application or component and which are able to share load and state between them.  A Kubernetes cluster is a group of machines (“nodes”) that all host Kubernetes resources, like pods and services, and which appear to the external user as a single entity.  If the term “cluster” is not prefixed, it should be assumed to mean a Kubernetes cluster. |
| Domain	| A WebLogic domain is a group of related applications and resources along with the configuration information necessary to run them. |
| Ingress	| A Kubernetes Ingress provides access to applications and services in a Kubernetes environment to external clients.  An Ingress may also provide additional features like load balancing. |
| Namespace	| A Kubernetes namespace is a named entity that can be used to group together related objects, for example, pods and services. |
| Operator	| A Kubernetes operator is software that performs management of complex applications. |
|Pod	| A Kubernetes pod contains one or more containers and is the object that provides the execution environment for an instance of an application component, such as a web server or database. |
| Job	 | A Kubernetes job is a type of controller that creates one or more pods that run to completion to complete a specific task. |
| Secret	| A Kubernetes secret is a named object that can store secret information like usernames, passwords, X.509 certificates, or any other arbitrary data. |
|Service	| A Kubernetes service exposes application endpoints inside a pod to other pods, or outside the Kubernetes cluster.  A service may also provide additional features like load balancing. |

# Getting started

Before using the operator, it is highly recommended that you read the [design philosophy](design.md) to develop an understanding of the operator's design, and the [architectural overview](architecture.md) to understand its architecture, including how WebLogic domains are deployed in Kubernetes using the operator.  It is also worth reading the details of the [Kubernetes RBAC definitions](rbac.md) required by the operator.

## Exposing applications outside the Kubernetes cluster
The operator can configure services to expose WebLogic applications and features outside of the Kubernetes cluster.  Care should be taken when exposing anything externally to ensure that the appropriate security considerations are taken into account. In this regard, there is no significant difference between a WebLogic domain running in a Kubernetes cluster and a domain running in a traditional data center.  The same kinds of considerations should be taken into account, for example:

* Only expose those protocols and ports that need to be exposed.
*	Use secure protocols (HTTPS, T3S, and such).
*	Use custom channels to restrict the protocols that are exposed.
*	Is load balancing required?
*	Is certificate-based integrity needed?
*	How will users authenticate?
* Is the network channel encrypted?

While it is natural to expose web applications outside the cluster, exposing administrative features like the Administration Console and a T3 channel for WLST should be given more careful consideration.  There are alternative options that should be weighed.  For example, Kubernetes provides the ability to securely access a shell running in a container in a pod in the cluster.  WLST could be executed from such an environment, meaning the T3 communications are entirely within the Kubernetes cluster and therefore more secure.

Oracle recommends careful consideration before deciding to expose any administrative interfaces externally.

## Requirements

The Oracle WebLogic Server Kubernetes Operator has the following requirements:

*	Kubernetes 1.7.5+, 1.8.0+, 1.9.0+, 1.10.0 (check with `kubectl version`).
*	Flannel networking v0.9.1-amd64 (check with `docker images | grep flannel`)
*	Docker 17.03.1.ce (check with `docker version`)
*	Oracle WebLogic Server 12.2.1.3.0

**Note:** Minikube and the embedded Kubernetes in Docker for Mac and Docker for Windows are not "supported" platforms right now, but we have done some basic testing and everything appears to work in these environments.  They are probably suitable for "trying out" the operator, but if you run into issues, we would ask that you try to reproduce them on a supported environment before reporting them.  Also, Calico networking appears to work in the limited testing we have done so far.

## Restrictions

The following features are not certified or supported in this release:

*	Whole Server Migration
*	Consensus Leasing
*	Node Manager (although it is used internally for the liveness probe and to start WebLogic Server instances)
*	Multicast
*	If using a `hostPath` persistent volume, then it must have read/write/many permissions for all container/pods in the WebLogic Server deployment
*	Multitenancy
*	Production redeployment

Please consult My Oracle Support [Doc ID 2349228.1](https://support.oracle.com/rs?type=doc&id=2349228.1) for up-to-date information about the features of WebLogic Server that are supported in Kubernetes environments.

# User guide

## Prefer to see it rather than read about it?

If you would rather see the developers demonstrating the operator rather than reading the documentation, then here are your videos:

* [Installing the operator](https://youtu.be/B5UmY2xAJnk) includes the installation and also shows using the operator's REST API.
* [Creating a WebLogic domain with the operator](https://youtu.be/Ey7o8ldKv9Y) shows the creation of two WebLogic domains including accessing the Administration Console and looking at the various resources created in Kubernetes - services, Ingresses, pods, load balancers, and such.
* [Deploying a web application, scaling a WebLogic cluster with the operator and verifying load balancing](https://youtu.be/hx4OPhNFNDM).
* [Using WLST against a domain running in Kubernetes](https://youtu.be/eY-KXEk8rI4) shows how to create a data source for an Oracle database that is also running in Kubernetes.
* [Scaling a WebLogic cluster with WLDF](https://youtu.be/Q8iZi2e9HvU).
* Watch this space, more to come!

Like what you see?  Read on for all the nitty-gritty details...

## Installation

Before installing the Oracle WebLogic Server Kubernetes Operator, ensure that the requirements listed above are met.  If you need help setting up a Kubernetes environment, please check our [cheat sheets](k8s_setup.md).

The overall process of installing and configuring the operator, and using it to manage WebLogic domains, consists of the following steps:

*	Registering for access to the Oracle Container Registry
* Setting up secrets to access the Oracle Container Registry
*	Customizing the operator parameters file
*	Deploying the operator to a Kubernetes cluster
*	Setting up secrets for the Administration Server credentials
*	Creating a persistent volume for a WebLogic domain
*	Customizing the domain parameters file
*	Creating a WebLogic domain

The provided scripts will perform most of these steps, but some must be performed manually. All of the [installation steps are explained in detail here](installation.md). Example files are provided in the `kubernetes` directory in this repository.

[comment]: # (If you need an Oracle database in your Kubernetes cluster, e.g. because your web application needs a place to keep its data, please see [this page] site/database.md for information about how to run the Oracle database in Kubernetes.)

## Creating a WebLogic domain with the operator

For information about how to create a WebLogic domain with the operator, see [Creating a WebLogic domain with the operator](creating-domain.md).

[comment]: # ( Manually creating a WebLogic domain.  If preferred, a domain can be created manually, i.e. without using the scripts provided with the operator.  As long as the domain follows the guidelines, it can still be managed by the operator.  Please refer to [Manually creating a WebLogic domain] site/manually-creating-domain.md for details.  A good example of when manual domain creation may be preferred is when a user already has a set of existing WLST scripts that are used to create domains and they wish to reuse those same WLST scripts in Kubernetes, perhaps with some small modifications. )

## Using WLST

When creating a domain, there is an option to expose a T3 channel outside of the Kubernetes cluster to allow remote WLST access.  For more information about how to use WLST with a domain running in Kubernetes, see [Using WLST](wlst.md) .

## Starting up the domain

The operator will automatically start up domains that it is aware of, based on the configuration in the domain custom resource.  For details, see [Startup up a WebLogic domain](starting-domain.md).

## Using the operator's REST services

The operator provides a REST API that you can use to obtain information about the configuration and to initiate scaling actions. For details about how to use the REST APIs, see [Using the operator's REST services](rest.md)

## Scaling a cluster

The operator provides the ability to scale up or down WebLogic clusters.  There are several ways to initiate scaling, including:

* Updating the domain custom resource directly (using `kubectl`).
* Calling the operator's REST `scale` API, for example, from `curl`.
* Using a WLDF policy rule and script action to call the operator's REST `scale` API.
* Using a Prometheus alert action to call the operator's REST `scale` API.

For more information, see [Scaling a WebLogic cluster](scaling.md).

## Load balancing with an Ingress controller or a web server

You can choose a load balancer provider for your WebLogic domains running in a Kubernetes cluster. Please refer to [Load balancing with Voyager/HAProxy](voyager.md), [Load balancing with Traefik](traefik.md), and [Load balancing with the Apache HTTP Server](apache.md) for information about the current capabilities and setup instructions for each of the supported load balancers.


[comment]: # (Exporting operator logs to ELK.  The operator provides an option to export its log files to the ELK stack. Please refer to [ELK integration]site/elk.md for information about this capability.)

## Shutting down a domain

For information about how to shut down a domain running in Kubernetes, see [Shutting down a domain](site/shutdown-domain.md) .

## Removing a domain

To permanently remove the Kubernetes resources for a domain from a Kubernetes cluster, run the [Delete WebLogic domain resources](/kubernetes/delete-weblogic-domain-resources.sh) script. This script will delete a specific domain, or all domains, and all the Kubernetes resources associated with a set of given domains. The script will also attempt a clean shutdown of a domain’s WebLogic pods before deleting its resources.  You can run the script in a test mode to show what would be shutdown and deleted without actually performing the shutdowns and deletions.   For script help, use its `-h` option.

The script will remove only domain-related resources which are labeled with the `domainUID` label, such as resources created by the [Create WebLogic domain](/kubernetes/create-weblogic-domain.sh) script or the [integration tests](/src/integration-tests/bash/run.sh).  If you manually created resources and have not labelled them with a `domainUID`, the script will not remove them.   One way to label a resource that has already been deployed is:

```
kubectl -n <Namespace> label <ResourceType> <ResourceName> domainUID=<domainUID>
```

To manually remove the persistent volume claim and the persistent volume, use these commands:

```
kubectl delete pvc PVC-NAME -n NAMESPACE
kubectl delete pv PV-NAME
```

Find the names of the persistent volume claim (represented above as `PVC-NAME`) and the persistent volume (represented as `PV-NAME`) in the domain custom resource YAML file, or if it is not available, check for the `domainUID` in the metadata on the persistent volumes. Replace `NAMESPACE` with the namespace that the operator is running in.

To permanently delete the actual WebLogic domain configuration and domain home, delete the physical volume using the appropriate tools.  For example, if the persistent volume used the `HostPath` provider, then delete the corresponding directory on the Kubernetes master.

## Removing the operator

To remove the operator from a Kubernetes cluster, issue the following commands:

```
kubectl delete deploy weblogic-operator –n NAMESPACE
kubectl delete service external-weblogic-operator-svc –n NAMESPACE
kubectl delete service internal-weblogic-operator-svc –n NAMESPACE
```

Replace `NAMESPACE` with the namespace that the operator is running in.

To remove more than one operator, repeat these steps for each operator namespace.

# Developer guide

Developers interested in this project are encouraged to read the [Developer guide](developer.md) to learn how to build the project, run tests, and so on.  The Developer guide also provides details about the structure of the code, coding standards, and the Asynchronous Call facility used in the code to manage calls to the Kuberentes API.

Please take a look at our [wish list](https://github.com/oracle/weblogic-kubernetes-operator/wiki/Wish-list) to get an idea of the kind of features we would like to add to the operator.  Maybe you will see something you would like to contribute to!

# API documentation

Documentation for APIs is provided here:

* [Javadoc](https://oracle.github.io/weblogic-kubernetes-operator/apidocs/index.html) for the operator.

* [Swagger](https://oracle.github.io/weblogic-kubernetes-operator/swagger/index.html) documentation for the operator's REST interface.

# Recent changes

See [Recent changes](recent-changes.md) for recent changes to the operator, including any backward incompatible changes.

# Contributing to the operator

Oracle welcomes contributions to this project from anyone.  Contributions may be reporting an issue with the operator, or submitting a pull request.  Before embarking on significant development that may result in a large pull request, it is recommended that you create an issue and discuss the proposed changes with the existing developers first.

If you want to submit a pull request to fix a bug or enhance an existing feature, please first open an issue and link to that issue when you submit your pull request.

If you have any questions about a possible submission, feel free to open an issue too.

## Contributing to the Oracle WebLogic Server Kubernetes Operator repository

Pull requests can be made under The Oracle Contributor Agreement (OCA), which is available at [https://www.oracle.com/technetwork/community/oca-486395.html](https://www.oracle.com/technetwork/community/oca-486395.html).

For pull requests to be accepted, the bottom of the commit message must have the following line, using the contributor’s name and e-mail address as it appears in the OCA Signatories list.

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
*	Create a branch in your fork to implement the changes. We recommend using the issue number as part of your branch name, for example, `1234-fixes`.
*	Ensure that any documentation is updated with the changes that are required by your fix.
*	Ensure that any samples are updated if the base image has been changed.
*	Submit the pull request. Do not leave the pull request blank. Explain exactly what your changes are meant to do and provide simple steps on how to validate your changes. Ensure that you reference the issue you created as well. We will assign the pull request to 2-3 people for review before it is merged.

## Introducing a new dependency

Please be aware that pull requests that seek to introduce a new dependency will be subject to additional review.  In general, contributors should avoid dependencies with incompatible licenses, and should try to use recent versions of dependencies.  Standard security vulnerability checklists will be consulted before accepting a new dependency.  Dependencies on closed-source code, including WebLogic Server, will most likely be rejected.

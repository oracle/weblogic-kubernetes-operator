# Oracle WebLogic Server Kubernetes Operator

Built with [Wercker](http://www.wercker.com)

[![wercker status](https://app.wercker.com/status/68ce42623fce7fb2e52d304de8ea7530/m/develop "wercker status")](https://app.wercker.com/project/byKey/68ce42623fce7fb2e52d304de8ea7530)

Oracle is finding ways for organizations using WebLogic Server to run important workloads, to move those workloads into the cloud. By certifying on industry standards, such as Docker and Kubernetes, WebLogic now runs in a cloud neutral infrastructure. In addition, we've provided an open-source Oracle WebLogic Server Kubernetes Operator (the “operator”) which has several key features to assist you with deploying and managing WebLogic domains in a Kubernetes environment. You can:

*	Create WebLogic domains on a Kubernetes persistent volume. This persistent volume can reside in an NFS.
* Create a WebLogic domain in a Docker image.
* Override certain aspects of the WebLogic domain configuration.
*	Define WebLogic domains as a Kubernetes resource (using a Kubernetes custom resource definition).
*	Start servers based on declarative startup parameters and desired states.
* Manage WebLogic configured or dynamic clusters.
*	Expose the WebLogic Server Administration Console outside the Kubernetes cluster, if desired.
*	Expose T3 channels outside the Kubernetes domain, if desired.
*	Expose HTTP paths on a WebLogic domain outside the Kubernetes domain with load balancing and update the load balancer when Managed Servers in the WebLogic domain are started or stopped.
* Scale WebLogic domains by starting and stopping Managed Servers on demand, or by integrating with a REST API to initiate scaling based on WLDF, Prometheus, Grafana, or other rules.
* Publish operator and WebLogic Server logs into Elasticsearch and interact with them in Kibana.

The fastest way to experience the operator is to follow the [Quick start guide](site/quickstart.md), or you can peruse our [documentation](site), read our blogs, or try out the [samples](kubernetes/samples/README.md).


# Important terms

This documentation uses several important terms which are intended to have a specific meaning.

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

Before using the operator, you might want to read the [design philosophy](site/design.md) to develop an understanding of the operator's design, and the [architectural overview](site/architecture.md) to understand its architecture, including how WebLogic domains are deployed in Kubernetes using the operator. Also, worth reading are the details of the [Kubernetes RBAC definitions](site/rbac.md) required by the operator.

## Prerequisites

*	Kubernetes 1.10.11+, 1.11.5+, and 1.12.3+  (check with `kubectl version`).
*	Flannel networking v0.9.1-amd64 (check with `docker images | grep flannel`)
*	Docker 18.03.1.ce (check with `docker version`)
*	Oracle WebLogic Server 12.2.1.3.0

## Set up your Kubernetes cluster

If you need help setting up a Kubernetes environment, check our [cheat sheet](site/k8s_setup.md).

## Manage your Kubernetes environment

After creating Kubernetes clusters, you can optionally:
* Create load balancers to direct traffic to backend domains.
* Configure Kibana and Elasticsearch for your operator logs.

### Creating load balancers

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

# Developer guide

Developers interested in this project are encouraged to read the [Developer guide](site/developer.md) to learn how to build the project, run tests, and so on.  The Developer guide also provides details about the structure of the code, coding standards, and the Asynchronous Call facility used in the code to manage calls to the Kubernetes API.

Please take a look at our [wish list](https://github.com/oracle/weblogic-kubernetes-operator/wiki/Wish-list) to get an idea of the kind of features we would like to add to the operator.  Maybe you will see something you would like to contribute to!

## API documentation

Documentation for APIs is provided here:

* The operator provides a REST API that you can use to obtain information about the configuration and to initiate scaling actions. For details about how to use the REST APIs, see [Using the operator's REST services](site/rest.md).

* See the [Swagger](https://oracle.github.io/weblogic-kubernetes-operator/swagger/index.html) documentation for the operator's REST interface.

* [Javadoc](https://oracle.github.io/weblogic-kubernetes-operator/apidocs/index.html) for the operator.

## Need more help?

We have a public Slack channel where you can get in touch with us to ask questions about using the operator.  To join our channel, please [visit this site to get an invitation](https://weblogic-slack-inviter.herokuapp.com/).  The invitation email will include details of how to access our Slack workspace.  After you are logged in, please come to #operator and say, "hello!"

## Recent changes

See [Recent changes](site/recent-changes.md) for changes to the operator, including any backward incompatible changes.


# Contributing to the operator

Oracle welcomes contributions to this project from anyone.  Contributions may be reporting an issue with the operator or submitting a pull request.  Before embarking on significant development that may result in a large pull request, it is recommended that you create an issue and discuss the proposed changes with the existing developers first.

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

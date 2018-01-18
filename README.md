# Oracle WebLogic Server Kubernetes Operator

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

*	Kubernetes 1.7.5+, 1.8.0+ or 1.9.0+ (check with kubectl version)
*	Flannel networking v0.9.1-amd64 (check with docker images | grep flannel)
*	Docker 17.03.1.ce (check with docker version)
*	Oracle WebLogic Server 12.2.1.3.0

Note: Minikube is not supported.

#Restrictions

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

# Installation

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

These steps are explained in detail in the linked pages. Example files are provided in the `kubernetes` directory in this repository.


# Developer Guide

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

Before using the operator, it is highly recommended to read the [design philosophy](site/design.md) to develop an understanding of the operator's design, and the [architectural overview](site/architecture.md) to understand its architecture, including how WebLogic domains are deployed in Kubernetes using the operator.


# Requirements


# Developer Guide



# Installation


# User Guide


# API documentation

Documentation for APIs is provided here:

* [Javadoc](https://oracle.github.io/weblogic-kubernetes-operator/apidocs/index.html) for the operator.

* [Swagger](https://oracle.github.io/weblogic-kubernetes-operator/swagger/index.html) documentation for the operator's REST interface.

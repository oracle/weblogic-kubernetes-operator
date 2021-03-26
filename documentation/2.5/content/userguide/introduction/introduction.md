---
title: "Get started"
date: 2019-02-23T16:40:54-05:00
weight: 1
description: "Review the operator prerequisites."
---

An operator is an application-specific controller that extends Kubernetes to create, configure, and manage instances
of complex applications. The Oracle WebLogic Server Kubernetes Operator follows the standard Kubernetes operator pattern, and
simplifies the management and operation of WebLogic domains and deployments.

You can have one or more operators in your Kubernetes cluster that manage one or more WebLogic domains each.
We provide a Helm chart to manage the installation and configuration of the operator.
Detailed instructions are available [here]({{< relref "/userguide/managing-operators/installation/_index.md" >}}).


### Operator prerequisites

* Kubernetes 1.13.5+, 1.14.8+, and 1.15.7+  (check with `kubectl version`).
* Flannel networking v0.9.1-amd64 (check with `docker images | grep flannel`) *or* OpenShift SDN on OpenShift 4.3 systems.
* Docker 18.9.1 (check with `docker version`) *or* CRI-O 1.14.7 (check with `crictl version | grep RuntimeVersion`).
* Helm 2.14.3+, 3.0.3+ (check with `helm version --client --short`).
* Either Oracle WebLogic Server 12.2.1.3.0 with patch 29135930, or Oracle WebLogic Server 12.2.1.4.0.
   * The existing WebLogic Docker image, `container-registry.oracle.com/middleware/weblogic:12.2.1.3 `,
   has all the necessary patches applied.
   * Check the WLS version with `docker run container-registry.oracle.com/middleware/weblogic:12.2.1.3 sh -c` `'source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh > /dev/null 2>&1 && java weblogic.version'`.
   * Check the WLS patches with `docker run container-registry.oracle.com/middleware/weblogic:12.2.1.3 sh -c` `'$ORACLE_HOME/OPatch/opatch lspatches'`.
* You must have the `cluster-admin` role to install the operator.
* We do not currently support running WebLogic in non-Linux containers.

### Important note about Kubernetes 1.16.0+

Kubernetes 1.16 introduced changes to some Kubernetes APIs that are used by the operator.
At this time, the operator will not work on Kubernetes 1.16.0+.
When we have made the necessary code changes, we will update this page to confirm support of 1.16.

### Cloud providers

The Oracle [Global Pricing and Licensing site](https://www.oracle.com/corporate/pricing/specialty-topics.html)
provides details about licensing practices and policies.
WebLogic Server and the operator are supported on "Authorized Cloud Environments" as defined in
[this Oracle licensing policy](https://www.oracle.com/assets/cloud-licensing-070579.pdf) and
[this list of eligible products](http://www.oracle.com/us/corporate/pricing/authorized-cloud-environments-3493562.pdf).

The official document that defines the [supported configurations is here](https://www.oracle.com/middleware/technologies/ias/oracleas-supported-virtualization.html).

In accordance with these policies, the operator and WebLogic Server are supported on Oracle Cloud
Infrastructure using *Oracle Container Engine for Kubernetes*, or in a cluster running *Oracle Linux
Container Services for use with Kubernetes* on OCI Compute, and on "authorized cloud environments".

### Oracle Linux Cloud Native Environment

[Oracle Linux Cloud Native Environment](https://docs.oracle.com/en/operating-systems/olcne/) is a fully integrated suite for the development and management of cloud-native applications. Based on the Open Container Initiative (OCI) and Cloud Native Computing Foundation (CNCF) standards, Oracle Linux Cloud Native Environment delivers a simplified framework for installations, updates, upgrades, and configuration of key features for orchestrating microservices.

WebLogic Server and the WebLogic Kubernetes operator are certified and supported on Oracle Linux Cloud Native Environment.


### OpenShift

Operator 2.0.1+ is certified for use on OpenShift Container Platform 3.11.43+, with Kubernetes 1.11.5+.  OpenShift 4 certification is currently in progress.

When using the operator in OpenShift, the `anyuid` security context constraint is required to ensure that WebLogic containers run with a UNIX UID that has the correct permissions on the domain filesystem.
For more information, see [OpenShift]({{<relref "/security/openshift.md">}}) in the Security section.

### Important note about development-focused Kubernetes distributions

There are a number of development-focused distributions of Kubernetes, like kind, Minikube, Minishift and so on.
Often these run Kubernetes in a virtual machine on your development machine.  We have found that these distributions
present some extra challenges in areas like:

* Separate Docker image stores, making it necessary to save/load images to move them between Docker file systems
* Default virtual machine file sizes and resource limits that are too small to run WebLogic or hold the necessary images
* Storage providers that do not always support the features that the operator and/or WebLogic rely on
* Load balancing implementations that do not always support the features that the operator and/or WebLogic rely on

As such, we *do not* recommend using these distributions to run the operator and/or WebLogic, and we do not
provide support for WebLogic or the operator running in these distributions.

We have found that Docker for Desktop does not seem to suffer the same limitations, and we do support that as a
development/test option.

### Operator Docker image

You can find the operator image in
[Docker Hub](https://hub.docker.com/r/oracle/weblogic-kubernetes-operator/).

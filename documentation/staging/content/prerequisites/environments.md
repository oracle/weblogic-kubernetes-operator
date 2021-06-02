---
title: "Supported environments"
date: 2019-02-23T16:40:54-05:00
weight: 3
---


### OpenShift

Operator 2.0.1+ is certified for use on OpenShift Container Platform 3.11.43+, with Kubernetes 1.11.5+.  

Operator 2.5.0+ is certified for use on OpenShift Container Platform 4.3.0+ with Kubernetes 1.16.2+.

When using the operator in OpenShift, a security context constraint is required to ensure that WebLogic containers run with a UNIX UID that has the correct permissions on the domain file system.
This could be either the `anyuid` SCC or a custom one that you define for user/group `1000`. For more information, see [OpenShift]({{<relref "/security/openshift.md">}}) in the Security section.

### Important note about development-focused Kubernetes distributions

There are a number of development-focused distributions of Kubernetes, like kind, Minikube, Minishift, and so on.
Often these run Kubernetes in a virtual machine on your development machine.  We have found that these distributions
present some extra challenges in areas like:

* Separate container image caches, making it necessary to save/load images to move them between Docker file systems
* Default virtual machine file sizes and resource limits that are too small to run WebLogic or hold the necessary images
* Storage providers that do not always support the features that the operator or WebLogic rely on
* Load balancing implementations that do not always support the features that the operator or WebLogic rely on

As such, we *do not* recommend using these distributions to run the operator or WebLogic, and we do not
provide support for WebLogic or the operator running in these distributions.

We have found that Docker for Desktop does not seem to suffer the same limitations, and we do support that as a
development/test option.

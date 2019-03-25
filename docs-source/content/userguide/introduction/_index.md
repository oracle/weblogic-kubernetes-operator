---
title: "Introduction"
date: 2019-02-23T16:43:10-05:00
weight: 2
description: "Learn about the operator's design, architecture, important terms, and prerequisites."
---
{{% children style="h4" description="true" %}}

This guide provides detailed user information for the Oracle WebLogic
Server Kubernetes Operator.  It provides instructions on how to install the operator in your
Kubernetes cluster and how to use it to manage WebLogic domains.  If you are looking for information about how the operator is designed, implemented, built, and such, then
you should refer to the [Developer guide]({{< relref "/developerguide/_index.md" >}}).


### Important terms

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


### Additional reading
Before using the operator, you might want to read the [design philosophy]({{< relref "/userguide/introduction/design.md" >}}) to develop an understanding of the operator's design, and the [architectural overview]({{< relref "/userguide/introduction/architecture.md" >}}) to understand its architecture, including how WebLogic domains are deployed in Kubernetes using the operator. Also, worth reading are the details of the [Kubernetes RBAC definitions]({{< relref "/security/rbac.md" >}}) required by the operator.

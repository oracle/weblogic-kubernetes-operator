---
title: "Supported platforms"
date: 2019-02-23T16:40:54-05:00
description: "See the operator supported environments."
weight: 4
---

### Contents

- [Supported environments](#supported-environments)
- [Kubernetes, WebLogic, and operating system prerequisites](#kubernetes-weblogic-and-operating-system-prerequisites)
- [Pricing and licensing](#pricing-and-licensing)
- [Important notes about specific environments](#important-notes-about-specific-environments)
  - [Oracle Cloud Infrastructure (OCI)](#oracle-cloud-infrastructure-oci)
  - [Oracle Linux Cloud Native Environment (OLCNE)](#oracle-linux-cloud-native-environment-olcne)
  - [Oracle Private Cloud Appliance (PCA) and Oracle Private Cloud at Customer (OPCC)](#oracle-private-cloud-appliance-pca-and-oracle-private-cloud-at-customer-opcc)
  - [Microsoft Azure Kubernetes Service (AKS)](#microsoft-azure-kubernetes-service-aks)
  - [VMware Tanzu Kubernetes Grid (TKG)](#vmware-tanzu-kubernetes-grid-tkg)
  - [OpenShift](#openshift)
  - [Development-focused Kubernetes distributions](#development-focused-kubernetes-distributions)

### Supported environments

WebLogic Server and the operator are supported on
Oracle cloud environments such as
Oracle Cloud Infrastructure (OCI),
Oracle Linux Cloud Native Environment (OLCNE),
Oracle Private Cloud Appliance (PCA),
or Oracle Private Cloud at Customer (OPCC),
and on "Authorized Cloud Environments" as defined in
[this Oracle licensing policy](https://www.oracle.com/assets/cloud-licensing-070579.pdf)
for [this list of eligible products](http://www.oracle.com/us/corporate/pricing/authorized-cloud-environments-3493562.pdf).

The official document that defines the supported configurations
is [here](https://www.oracle.com/middleware/technologies/ias/oracleas-supported-virtualization.html)
(search for keyword 'Kubernetes').

Some supported environments have additional help or samples that are specific
to the operator, or are subject to limitations and restrictions: see
[Important notes about specific environments](#important-notes-about-specific-environments).

### Kubernetes, WebLogic, and operating system prerequisites

The operator is subject to Kubernetes, WebLogic, and operating system versioning prerequisites:
see [Operator prerequisites]({{< relref "/userguide/prerequisites/introduction.md" >}}).

### Pricing and licensing

The WebLogic Kubernetes Operator (the "operator") is open source and free,
licensed under the Universal Permissive license (UPL), Version 1.0.

WebLogic Server is not open source.
Licensing is required for each running WebLogic Server instance in Kubernetes,
just as with any deployment of WebLogic Server.
Licensing is free for a single developer desktop development environment.

The Oracle [Global Pricing and Licensing site](https://www.oracle.com/corporate/pricing/specialty-topics.html)
provides details about licensing practices and policies.

### Important notes about specific environments

Here are some important considerations for specific environments.

**Note:** This section does not list all supported environments.
See [Supported environments](#supported-environments)
for a list of all supported environments.

#### Oracle Cloud Infrastructure (OCI)

The operator and WebLogic Server are supported on Oracle Cloud
Infrastructure using *Oracle Container Engine for Kubernetes*, or in a cluster running *Oracle Linux
Container Services for use with Kubernetes* on OCI Compute, and on
any other OCI "Authorized Cloud Environments"
as described in [Supported environments](#supported-environments).

#### Oracle Linux Cloud Native Environment (OLCNE)

[Oracle Linux Cloud Native Environment](https://docs.oracle.com/en/operating-systems/olcne/) is a fully integrated suite for the development and management of cloud-native applications. Based on Open Container Initiative (OCI) and Cloud Native Computing Foundation (CNCF) standards, Oracle Linux Cloud Native Environment delivers a simplified framework for installations, updates, upgrades, and configuration of key features for orchestrating microservices.

WebLogic Server and the WebLogic Kubernetes Operator are certified and supported on Oracle Linux Cloud Native Environment:
- Operator v2.6.0 is certified on OLCNE 1.1 and v3.2.5 is certified on OLCNE 1.3.
- Operator v3.2.5 provides certified support of OLCNE 1.3 with Kubernetes 1.20.6 and CRI-O 1.20.2.

#### Oracle Private Cloud Appliance (PCA) and Oracle Private Cloud at Customer (OPCC)

The [Oracle Private Cloud Appliance](https://www.oracle.com/servers/technologies/private-cloud-appliance.html) (PCA)
and
[Oracle Private Cloud at Customer](https://docs.oracle.com/en/cloud/cloud-at-customer/private-cloud-at-customer/index.html) (OPCC)
fully support Oracle
Linux Cloud Native Environment (OLCNE), including Oracle Container Runtime for Docker and
Oracle Container Services for Use with Kubernetes.  They provide an ideal runtime for Oracle
WebLogic Server applications to run in Docker and Kubernetes with full, integrated system
support from Oracle. For operator certifications that are specific
to OLCNE, see [Oracle Linux Cloud Native Environment (OLCNE)](#oracle-linux-cloud-native-environment-olcne).

The [Oracle WebLogic Server on Oracle Private Cloud Appliance and Kubernetes](https://www.oracle.com/a/ocom/docs/engineered-systems/oracle-weblogic-server-on-pca.pdf)
document describes how to deploy Oracle WebLogic Server applications on Kubernetes
on PCA or OPCC, enabling you to run
these applications in cloud native infrastructure that is fully supported by Oracle, and that is
portable across cloud environments.
The document also highlights how applications deployed on
Oracle Exalogic Elastic Cloud systems can be migrated to this infrastructure without application
changes, enabling you to preserve your application investment as you adopt modern cloud
native infrastructure.

#### Microsoft Azure Kubernetes Service (AKS)

[Azure Kubernetes Service (AKS)](https://docs.microsoft.com/en-us/azure/aks/) is a hosted Kubernetes environment.  The WebLogic Kubernetes
Operator, Oracle WebLogic Sever 12c, and Oracle Fusion Middleware Infrastructure 12c are fully supported and certified on Azure Kubernetes Service (as per the documents
referenced in [Supported environments](#supported-environments)).

AKS support and limitations:

* All three domain home source types are supported (Domain in Image, Model in Image, and Domain in PV).
* For Domain in PV, we support Azure Files volumes accessed through
  a persistent volume claim; see [here](https://docs.microsoft.com/en-us/azure/aks/azure-files-volume).
* Azure Load Balancers are supported when provisioned using a Kubernetes Service of `type=LoadBalancer`.
* Oracle databases running in Oracle Cloud Infrastructure are supported for Fusion Middleware
  Infrastructure MDS data stores only when accessed through an OCI FastConnect.
* Windows Server containers are not currently supported, only Linux containers.

See also the [Azure Kubernetes Service sample]({{<relref "/samples/azure-kubernetes-service/_index.md">}}).

#### VMware Tanzu Kubernetes Grid (TKG)

Tanzu Kubernetes Grid (TKG) is a managed Kubernetes Service that lets you quickly deploy and manage Kubernetes clusters. The WebLogic Kubernetes
Operator and Oracle WebLogic Sever are fully supported and certified on VMware Tanzu Kubernetes Grid Multicloud 1.1.3 (with vSphere 6.7U3).

TKG support and limitations:

* Both Domain in Image and Model in Image domain home source types are supported. Domain in PV is not supported.
* VSphere CSI driver supports only volumes with Read-Write-Once policy. This does not allow writing stores on PV.
  * For applications requiring HA, use JMS and JTA stores in the database.
* The ingress used for certification is NGINX, with MetalLB load balancer.

See also the [Tanzu Kubernetes Grid sample]({{<relref "/samples/tanzu-kubernetes-service/_index.md">}}).

#### OpenShift

OpenShift can be a cloud platform or can be deployed on premise.

Operator 2.0.1+ is certified for use on OpenShift Container Platform 3.11.43+, with Kubernetes 1.11.5+.

Operator 2.5.0+ is certified for use on OpenShift Container Platform 4.3.0+ with Kubernetes 1.16.2+.

To accommodate OpenShift security requirements:
- For security requirements to run WebLogic in OpenShift, see the [OpenShift chapter]({{<relref "/security/openshift.md">}}) in the Security section.
- Beginning with operator version 3.3.2, specify the `kubernetesPlatorm` Helm chart property with value `OpenShift`. For more information, see [Operator Helm configuration values]({{<relref "/userguide/managing-operators/using-helm#operator-helm-configuration-values">}}).

#### Development-focused Kubernetes distributions

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

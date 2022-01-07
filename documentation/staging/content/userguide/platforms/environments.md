---
title: "Supported platforms"
date: 2019-02-23T16:40:54-05:00
description: "See the operator supported environments."
weight: 3
---

### Contents

- [Supported environments](#supported-environments)
- [Kubernetes, WebLogic Server, and operating system prerequisites](#kubernetes-weblogic-server-and-operating-system-prerequisites)
- [Pricing and licensing](#pricing-and-licensing)
  - [WebLogic Kubernetes Operator](#weblogic-kubernetes-operator)
  - [WebLogic Server](#weblogic-server)
  - [Oracle Linux and WebLogic Server Images](#oracle-linux-and-weblogic-server-images)
  - [Reference](#reference)
- [Important notes about specific environments](#important-notes-about-specific-environments)
  - [Oracle Cloud Infrastructure (OCI)](#oracle-cloud-infrastructure-oci)
  - [Oracle Linux Cloud Native Environment (OLCNE)](#oracle-linux-cloud-native-environment-olcne)
  - [Oracle Private Cloud Appliance (PCA) and Oracle Private Cloud at Customer (OPCC)](#oracle-private-cloud-appliance-pca-and-oracle-private-cloud-at-customer-opcc)
  - [Microsoft Azure](#microsoft-azure)
  - [VMware Tanzu Kubernetes Grid (TKG)](#vmware-tanzu-kubernetes-grid-tkg)
  - [OpenShift](#openshift)
  - [WebLogic Server running in Kubernetes connecting to an Oracle Database also running in Kubernetes](#weblogic-server-running-in-kubernetes-connecting-to-an-oracle-database-also-running-in-kubernetes)
  - [Development-focused Kubernetes distributions](#development-focused-kubernetes-distributions)

### Supported environments

WebLogic Server and the operator are supported on Oracle Cloud offerings, such as:
- Oracle Cloud Infrastructure (OCI)
- Oracle Container Engine for Kubernetes (OKE)
- Oracle Linux Cloud Native Environment (OLCNE)
- Oracle Private Cloud Appliance (PCA)
- Oracle Private Cloud at Customer (OPCC)

WebLogic Server and the operator are certified on offerings, such as:
- Amazon Elastic Compute Cloud (EC2)
- Microsoft Azure Platform
- Microsoft Azure Kubernetes Service (AKS)
- OpenShift Container Platform
- VMWare Tanzu
- VMware Tanzu Kubernetes Grid (TKG)

WebLogic Server and the operator are also supported on service offerings which
deploy the WebLogic Server and the operator for you. These include:
- [Oracle WebLogic Server for OKE (WLS for OKE)](https://docs.oracle.com/en/cloud/paas/weblogic-container/)
- [Oracle WebLogic Server on AKS from the Azure Marketplace (WLS on AKS Marketplace)](#oracle-weblogic-server-on-aks-from-the-azure-marketplace-wls-on-aks-marketplace)

[Development-focused Kubernetes distributions](#development-focused-kubernetes-distributions) are also supported.

For detailed virtualization and cloud licensing descriptions, see:
- [Supported Virtualization Technologies for Oracle Fusion Middleware](https://www.oracle.com/middleware/technologies/ias/oracleas-supported-virtualization.html) (search for keyword 'Kubernetes')
- [Running and Licensing Oracle Programs in Containers and Kubernetes](https://www.oracle.com/a/tech/docs/running-and-licensing-programs-in-containers-and-kubernetes.pdf)

Some supported environments have additional help or samples that are specific
to the operator, or are subject to limitations and restrictions: see
[Important notes about specific environments](#important-notes-about-specific-environments).

### Kubernetes, WebLogic Server, and operating system prerequisites

The operator is subject to Kubernetes, WebLogic Server, and operating system versioning prerequisites,
see [Operator prerequisites]({{< relref "/userguide/prerequisites/introduction.md" >}}).

### Pricing and licensing

The WebLogic Kubernetes Operator and Oracle Linux are open source and free;
WebLogic Server requires licenses unless used in a single developer desktop development environment.
See the following sections for more detailed information.

#### WebLogic Kubernetes Operator

The WebLogic Kubernetes Operator (the "operator") is open source and free,
licensed under the Universal Permissive license (UPL), Version 1.0.
For support details, see [Get help]({{< relref "userguide/introduction/get-help.md" >}}).

#### WebLogic Server

WebLogic Server is not open source.
Licensing is required for each running WebLogic Server instance in Kubernetes,
just as with any deployment of WebLogic Server.
Licensing is free for a single developer desktop development environment.
For more information, see the [Fusion Middleware Licensing Information User Manual - Application Server Products](https://docs.oracle.com/en/middleware/fusion-middleware/fmwlc/application-server-products-new-structure.html).

#### Oracle Linux and WebLogic Server images

Oracle Linux is under open source license
and is completely free to download and use.

In addition, with WebLogic Server licenses and support,
customers have access to:
- The latest WebLogic Server images
  which bundle Java SE and the latest slim Oracle Linux images.
- Oracle Linux support.
Note that WebLogic Server licenses and support do not include customer entitlements
for direct access to Oracle Linux support or Unbreakable Linux Network
(to access the standalone Oracle Linux patches).

Oracle supplies two types of WebLogic Server images, patched (CPU) and unpatched.
New WebLogic Server images are released when security fixes
are released for Oracle Linux, WebLogic Server, or Java. In addition,
patched images are rebuilt every CPU cycle, which is quarterly.
Customer support for WebLogic Server images is handled by Oracle support.

Using the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool)
customers can also build their own WebLogic Server images with
the latest Oracle Linux images, Java updates, and WebLogic Server patches.

#### Reference

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

#### Microsoft Azure

There are three different approaches for deploying the operator to Microsoft Azure:
- Microsoft Azure Platform
- Microsoft Azure Kubernetes Service (AKS)
- Oracle WebLogic Server on AKS from the Azure Marketplace (WLS on AKS Marketplace)

##### Microsoft Azure Kubernetes Service (AKS)

[Azure Kubernetes Service (AKS)](https://docs.microsoft.com/en-us/azure/aks/) is a hosted Kubernetes environment.  The WebLogic Kubernetes
Operator, Oracle WebLogic Server 12c, and Oracle Fusion Middleware Infrastructure 12c are fully supported and certified on Azure Kubernetes Service (as per the documents
referenced in [Supported environments](#supported-environments)). In this environment, it is the customer's responsibility to install the operator and supply WebLogic Server images.

AKS support and limitations:

* All three domain home source types are supported (Domain in Image, Model in Image, and Domain in PV).
* For Domain in PV, we support Azure Files volumes accessed through
  a persistent volume claim; see [here](https://docs.microsoft.com/en-us/azure/aks/azure-files-volume).
* Azure Load Balancers are supported when provisioned using a Kubernetes Service of `type=LoadBalancer`.
* Oracle databases running in Oracle Cloud Infrastructure are supported for Fusion Middleware
  Infrastructure MDS data stores only when accessed through an OCI FastConnect.
* Windows Server containers are not currently supported, only Linux containers.

See also the [Azure Kubernetes Service sample]({{<relref "/samples/azure-kubernetes-service/_index.md">}}).

##### Oracle WebLogic Server on AKS from the Azure Marketplace (WLS on AKS Marketplace)

The WebLogic Server on AKS Azure Marketplace
offer lets you embrace cloud computing by providing greater choice
and flexibility for deploying your WLS domains and applications.
The offer leverages the WebLogic Kubernetes Toolkit to automate
the provisioning of WebLogic Server and Azure resources so that you can easily move WLS workloads to AKS.
The automatically provisioned resources include an AKS cluster,
the WebLogic Kubernetes Operator, WebLogic Server images, and the Azure Container Registry (ACR).
It is possible to use an existing AKS cluster or ACR instance with the offer if desired.
The offer also supports configuring load balancing with Azure App Gateway or the Azure Load Balancer,
DNS configuration, SSL/TLS configuration, easing database connectivity,
publishing metrics to Azure Monitor as well as mounting Azure Files as Kubernetes Persistent Volumes.

For details, see [WebLogic Server on AKS Marketplace]({{<relref "/userguide/aks/_index.md">}}).

#### VMware Tanzu Kubernetes Grid (TKG)

Tanzu Kubernetes Grid (TKG) is a managed Kubernetes Service that lets you quickly deploy and manage Kubernetes clusters.
The WebLogic Kubernetes Operator and Oracle WebLogic Server are fully supported and certified on VMware Tanzu Kubernetes Grid Multicloud 1.1.3 (with vSphere 6.7U3).

TKG support and limitations:

* Both Domain in Image and Model in Image domain home source types are supported. Domain in PV is not supported.
* VSphere CSI driver supports only volumes with Read-Write-Once policy. This does not allow writing stores on PV.
  * For applications requiring HA, use JMS and JTA stores in the database.
* The ingress used for certification is NGINX, with MetalLB load balancer.

See also the [Tanzu Kubernetes Grid sample]({{<relref "/samples/tanzu-kubernetes-service/_index.md">}}).

#### OpenShift

OpenShift can be a cloud platform or can be deployed on premises.

Operator 2.0.1+ is certified for use on OpenShift Container Platform 3.11.43+, with Kubernetes 1.11.5+.

Operator 2.5.0+ is certified for use on OpenShift Container Platform 4.3.0+ with Kubernetes 1.16.2+.

To accommodate OpenShift security requirements:
- For security requirements to run WebLogic Server in OpenShift, see the [OpenShift]({{<relref "/security/openshift.md">}}) documentation.
- Beginning with operator version 3.3.2, specify the `kubernetesPlatorm` Helm chart property with value `OpenShift`. For more information, see [Operator Helm configuration values]({{<relref "/userguide/managing-operators/using-helm#operator-helm-configuration-values">}}).

#### WebLogic Server running in Kubernetes connecting to an Oracle Database also running in Kubernetes

We have certified support for WebLogic Server domains, managed by the WebLogic Kubernetes Operator (operator), connecting to an Oracle Database, managed by the Oracle Database Operator for Kubernetes (OraOperator).  For details on the supported WLS and database versions, see the following:
* [Operator prerequisites]({{< relref "/userguide/prerequisites/introduction.md" >}})
* [Oracle Database Operator for Kubernetes prerequisites](https://github.com/oracle/oracle-database-operator/blob/main/PREREQUISITES.md)

The certification includes support for both application data access and all WLS database-dependent features supported in Kubernetes. For more information, see WebLogic Server Certifications on Kubernetes in My Oracle Support [Doc ID 2349228.1](https://support.oracle.com/epmos/faces/DocumentDisplay?_afrLoop=208317433106215&id=2349228.1&_afrWindowMode=0&_adf.ctrl-state=c2nhai8p3_4).

Included in the certification is support for the following topologies:
* WebLogic Server, operator, Oracle Database, and OraOperator all running in the same Kubernetes cluster.
* WebLogic Server, operator, Oracle Database, and OraOperator all running in the same Kubernetes cluster and WebLogic Server running on an Istio mesh.
* WebLogic Server and operator running in a Kubernetes cluster and the Oracle Database and OraOperator in a different Kubernetes cluster.


#### Development-focused Kubernetes distributions

There are a number of development-focused distributions of Kubernetes, like kind, Minikube, Minishift, and so on.
Often these run Kubernetes in a virtual machine on your development machine.  We have found that these distributions
present some extra challenges in areas like:

* Separate container image caches, making it necessary to save/load images to move them between Docker file systems
* Default virtual machine file sizes and resource limits that are too small to run WebLogic Server or hold the necessary images
* Storage providers that do not always support the features that the operator or WebLogic Server rely on
* Load balancing implementations that do not always support the features that the operator or WebLogic Server rely on

As such, we *do not* recommend using these distributions to run the operator or WebLogic Server, and we do not
provide support for WebLogic Server or the operator running in these distributions.

We have found that Docker for Desktop does not seem to suffer the same limitations, and we do support that as a
development/test option.

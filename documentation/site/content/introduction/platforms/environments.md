---
title: "Supported environments"
date: 2019-02-23T16:40:54-05:00
description: "The supported environments, pricing and licensing, and support details for the operator."
weight: 6
---

{{< table_of_contents >}}

### Overview

The operator supports running on production-quality Kubernetes environments, including on-premises,
cloud offerings where Kubernetes is supplied for you, and cloud offerings where you set up Kubernetes yourself.
Please refer to the support statement [Doc ID 2790123.1](https://support.oracle.com/epmos/faces/DocContentDisplay?id=2790123.1).

These include, but are not limited to:

- Oracle Cloud certified offerings, such as:
  - Oracle Cloud Infrastructure (OCI)
  - Oracle Container Engine for Kubernetes (OKE)
  - Oracle Cloud Native Environment (OCNE)
  - Oracle Private Cloud Appliance (PCA)
  - Oracle Private Cloud at Customer (OPCC)

- Other certified offerings, such as:
  - Microsoft Azure Platform
  - Microsoft Azure Kubernetes Service (AKS)
  - OpenShift Container Platform
  - VMWare Tanzu
  - VMware Tanzu Kubernetes Grid (TKG)

- Offerings which deploy the WebLogic Server and the operator for you. These include:
  - [Oracle WebLogic Server for OKE (WLS for OKE)](https://docs.oracle.com/en/cloud/paas/weblogic-container/)
  - [Oracle WebLogic Server on AKS from the Azure Marketplace (WLS on AKS Marketplace)](#oracle-weblogic-server-on-aks-from-the-azure-marketplace-wls-on-aks-marketplace)

[Development-focused Kubernetes distributions](#development-focused-kubernetes-distributions) are also supported.

**NOTES**:

- **Important**: Some supported environments have additional help or samples that are specific
to the operator, or are subject to limitations and restrictions; see
[Important notes about specific environments](#important-notes-about-specific-environments).

- For detailed virtualization licensing, cloud licensing, and support
descriptions, see [Pricing and licensing](#pricing-and-licensing).

- The operator is subject to Kubernetes, WebLogic Server, and operating system versioning prerequisites;
see [Operator prerequisites]({{< relref "/introduction/prerequisites/introduction.md" >}}).


### Important notes about specific environments

Here are some important considerations for specific environments:

- [Oracle Cloud Infrastructure](#oracle-cloud-infrastructure)
- [Oracle Cloud Native Environment](#oracle-cloud-native-environment)
- [Oracle Private Cloud Appliance (PCA) and Oracle Private Cloud at Customer (OPCC)](#oracle-private-cloud-appliance-pca-and-oracle-private-cloud-at-customer-opcc)
- [Microsoft Azure](#microsoft-azure)
- [VMware Tanzu Kubernetes Grid (TKG)](#vmware-tanzu-kubernetes-grid-tkg)
- [OpenShift](#openshift)
- [WebLogic Server running in Kubernetes connecting to an Oracle Database also running in Kubernetes](#weblogic-server-running-in-kubernetes-connecting-to-an-oracle-database-also-running-in-kubernetes)
- [Development-focused Kubernetes distributions](#development-focused-kubernetes-distributions)

**NOTE**: This section does not list all supported environments.
See the [Overview](#overview) for a list of all supported environments.

#### Oracle Cloud Infrastructure

The operator and WebLogic Server are supported on Oracle Cloud
Infrastructure using *Oracle Container Engine for Kubernetes*, or in a cluster running *Oracle Linux
Container Services for use with Kubernetes* on Oracle Cloud Infrastructure Compute, and on
any other Oracle Cloud Infrastructure "Authorized Cloud Environments"
as described in the [Overview](#overview).

WebLogic Kubernetes Operator is certified for use on OKE with Kubernetes 1.25.0+, 1.26.2+, and 1.27.2+.

#### Oracle Cloud Native Environment

[Oracle Cloud Native Environment](https://docs.oracle.com/en/operating-systems/olcne/) is a fully integrated suite for the development and management of cloud-native applications. Based on Open Container Initiative (OCI) and Cloud Native Computing Foundation (CNCF) standards, Oracle Cloud Native Environment delivers a simplified framework for installations, updates, upgrades, and configuration of key features for orchestrating microservices.

WebLogic Server and the WebLogic Kubernetes Operator are certified and supported on Oracle Cloud Native Environment (OCNE):
* OCNE 1.9 with Kubernetes 1.29.3+
* OCNE 1.8 with Kubernetes 1.28.8+
* OCNE 1.7 with Kubernetes 1.26.6+
* OCNE 1.6 with Kubernetes 1.25.11+

#### Oracle Private Cloud Appliance (PCA) and Oracle Private Cloud at Customer (OPCC)

The [Oracle Private Cloud Appliance](https://www.oracle.com/servers/technologies/private-cloud-appliance.html) (PCA)
and
[Oracle Private Cloud at Customer](https://docs.oracle.com/en/cloud/cloud-at-customer/private-cloud-at-customer/index.html) (OPCC)
fully support Oracle Cloud Native Environment, including Oracle Container Runtime for Docker and
Oracle Container Services for Use with Kubernetes.  They provide an ideal runtime for Oracle
WebLogic Server applications to run in Docker and Kubernetes with full, integrated system
support from Oracle. For operator certifications that are specific
to Oracle Cloud Native Environment, see [Oracle Cloud Native Environment](#oracle-cloud-native-environment).

The [Oracle WebLogic Server on Oracle Private Cloud Appliance and Kubernetes](https://www.oracle.com/a/ocom/docs/engineered-systems/oracle-weblogic-server-on-pca.pdf)
document describes how to deploy Oracle WebLogic Server applications on Kubernetes
on PCA or OPCC, enabling you to run
these applications in cloud native infrastructure that is fully supported by Oracle, and that is
portable across cloud environments.
The document also highlights how applications deployed on
Oracle Exalogic Elastic Cloud systems can be migrated to this infrastructure without application
changes, enabling you to preserve your application investment as you adopt modern cloud
native infrastructure.

* WebLogic Kubernetes Operator is certified for use on PCA X9 with Kubernetes 1.24.5+ and Istio 1.14.

* WebLogic Kubernetes Operator 4.x is certified for use on PCA v3. For more information, see [Using the OKE Service](https://docs.oracle.com/en/engineered-systems/private-cloud-appliance/3.0-latest/oke/oke-overview.html) in the Oracle Private Cloud Appliance Container Engine for Kubernetes documentation.

#### Microsoft Azure

There are three different approaches for deploying the operator to Microsoft Azure:
- Microsoft Azure Platform
- Microsoft Azure Kubernetes Service (AKS)
- Oracle WebLogic Server on AKS from the Azure Marketplace (WLS on AKS Marketplace)

##### Microsoft Azure Kubernetes Service (AKS)

[Azure Kubernetes Service (AKS)](https://docs.microsoft.com/en-us/azure/aks/) is a hosted Kubernetes environment.  The WebLogic Kubernetes
Operator, Oracle WebLogic Server 12c, and Oracle Fusion Middleware Infrastructure 12c are fully supported and certified on Azure Kubernetes Service (as per the documents
referenced in the [Overview](#overview)). In this environment, it is the customer's responsibility to install the operator and supply WebLogic Server or Fusion Middleware Infrastructure images.

AKS support and limitations:

* WebLogic Kubernetes Operator is certified for use on AKS with Kubernetes 1.25.0+, 1.26.2+.
* All three [domain home source types]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) are supported (Domain in Image, Model in Image, and Domain on PV).
   * **NOTE**: The Domain in Image [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) is deprecated in WebLogic Kubernetes Operator version 4.0. Oracle recommends that you choose either Domain on PV or Model in Image, depending on your needs.
* For Domain on PV, we support Azure Files volumes accessed through
  a persistent volume claim; see [here](https://docs.microsoft.com/en-us/azure/aks/azure-files-volume).
* Azure Load Balancers are supported when provisioned using a Kubernetes Service of `type=LoadBalancer`.
* Oracle databases running in Oracle Cloud Infrastructure are supported for Fusion Middleware
  Infrastructure MDS data stores only when accessed through an Oracle Cloud Infrastructure FastConnect.
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

For details, see [WebLogic Server on AKS Marketplace]({{<relref "/managing-domains/aks/_index.md">}}).

#### VMware Tanzu Kubernetes Grid (TKG)

Tanzu Kubernetes Grid (TKG) is a managed Kubernetes Service that lets you quickly deploy and manage Kubernetes clusters.
The WebLogic Kubernetes Operator and Oracle WebLogic Server are fully supported and certified for use on VMware Tanzu Kubernetes Grid Multicloud 1.1.3 (with vSphere 6.7U3).

TKG support and limitations:

* WebLogic Kubernetes Operator is certified for use on TKG with Kubernetes 1.24.0+, 1.25.0+, 1.26.0+.
* The Model in Image domain home source type is supported; Domain on PV is not supported.
* VSphere CSI driver supports only volumes with Read-Write-Once policy. This does not allow writing stores on PV.
  * For applications requiring HA, use JMS and JTA stores in the database.
* The ingress used for certification is NGINX, with MetalLB load balancer.

Tanzu Kubernetes Grid Integrated Edition (TKGI) 1.16 with vSphere (7.0.3) CSI 2.7 support includes:
* Both domain home source types (Model in Image and Domain on PV); use vSAN for PV.
* Kubernetes 1.25.10
* Ubuntu

See also the [Tanzu Kubernetes Grid sample]({{<relref "/samples/tanzu-kubernetes-service/_index.md">}}).

#### OpenShift

OpenShift can be a cloud platform or can be deployed on premises.

- Operator v4.0.5 is certified for use on:
  - OpenShift Container Platform 4.11.30 with Kubernetes 1.24+, RedHat OpenShift Mesh 2.3.2, and Istio 1.14.5.
  - OpenShift Container Platform 4.12.2 with Kubernetes 1.25+, RedHat OpenShift Mesh 2.3.2, and Istio 1.14.5.

To accommodate OpenShift security requirements:
- For security requirements to run WebLogic Server in OpenShift, see the [OpenShift]({{<relref "/security/openshift.md">}}) documentation.
- Beginning with operator version 3.3.2, specify the `kubernetesPlatform` Helm chart property with value `OpenShift`. For more information, see [Operator Helm configuration values]({{<relref "/managing-operators/using-helm#operator-helm-configuration-values">}}).

#### WebLogic Server running in Kubernetes connecting to an Oracle Database also running in Kubernetes

We have certified support for WebLogic Server domains, managed by the WebLogic Kubernetes Operator (operator), connecting to an Oracle Database, managed by the Oracle Database Operator for Kubernetes (OraOperator).  For details on the supported WLS and database versions, see the following:
* [Operator prerequisites]({{< relref "/introduction/prerequisites/introduction.md" >}})
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

As such, we *do not* recommend using these distributions to run the operator or WebLogic Server. While we do not
provide support for WebLogic Server or the operator running in production in these distributions, we do support some (such as,
kind and Minikube) in a development or test environment.

We have found that Docker for Desktop does not seem to suffer the same limitations, and we do support that as a
development or test option.

### Pricing and licensing

The WebLogic Kubernetes Operator and Oracle Linux are open source and free;
WebLogic Server requires licenses in any environment.
All WebLogic Server licenses are suitable for deploying WebLogic to containers and Kubernetes,
including free single desktop Oracle Technology Network (OTN) developer licenses.
See the following sections for more detailed information:

- [WebLogic Kubernetes Operator](#weblogic-kubernetes-operator)
- [WebLogic Server](#weblogic-server)
- [Oracle Linux](#oracle-linux)
- [Oracle Java](#oracle-java)
- [WebLogic Server or Fusion Middleware Infrastructure Images](#weblogic-server-or-fusion-middleware-infrastructure-images)
- [Additional references](#additional-references)

#### WebLogic Kubernetes Operator

The WebLogic Kubernetes Operator (the "operator") is open source and free,
licensed under the Universal Permissive license (UPL), Version 1.0.
For support details, see [Get help]({{< relref "/introduction/get-help.md" >}}).

#### WebLogic Server

WebLogic Server is not open source:

- Licensing is required to run WebLogic Server instances in Kubernetes,
  just as with any deployment of WebLogic Server.

- Licensing is free for a single developer desktop development environment
  when using an Oracle Technology Network (OTN) developer license.

For more information, see the
[Fusion Middleware Licensing Information User Manual - Application Server Products](https://docs.oracle.com/en/middleware/fusion-middleware/fmwlc/application-server-products-new-structure.html)
and the following sections.

#### Oracle Linux

Oracle Linux is under open source license and is completely free to download and use.

Note that WebLogic Server licenses that include support
do _not_ include customer entitlements
for direct access to Oracle Linux support or Unbreakable Linux Network
(to directly access the standalone Oracle Linux patches).
The latest Oracle Linux patches are included with the latest [WebLogic Server or Fusion Middleware Infrastructure Images](#weblogic-server-or-fusion-middleware-infrastructure-images).

#### Oracle Java

Oracle support for Java is included with
WebLogic Server licenses
when Java is used for running WebLogic and Coherence servers or clients.

For more information, see the
[Fusion Middleware Licensing Information User Manual - Application Server Products](https://docs.oracle.com/en/middleware/fusion-middleware/fmwlc/application-server-products-new-structure.html).

#### WebLogic Server or Fusion Middleware Infrastructure images

Oracle provides two different types of WebLogic Server or Fusion Middleware (FMW) Infrastructure images:

- _Critical Patch Update (CPU) images:_
  Images with the latest WebLogic Server (or Fusion Middleware Infrastructure) and Coherence PSU
  and other fixes released by the
  Critical Patch Update (CPU) program.
  CPU images are intended for production use.

- _General Availability (GA) WebLogic Server (or Fusion Middleware Infrastructure) images:_
  Images which are _not_ intended for production use
  and do _not_ include WebLogic, Fusion Middleware Infrastructure, or Coherence PSUs.

All WebLogic Server and Fusion Middleware Infrastructure licenses,
including free Oracle Technology Network (OTN) developer licenses,
include access to the latest General Availability (GA) WebLogic Server
or Fusion Middleware Infrastructure images which bundle Java SE.

Customers with access to WebLogic Server support additionally have:

  - Access to Critical Patch Update (CPU) WebLogic Server images which bundle Java SE.
  - Access to WebLogic Server patches.
  - Oracle support for WebLogic Server images.
  - Oracle support for the WebLogic Kubernetes Toolkit.

(Customers with access to Fusion Middleware Infrastructure support have
similar access to its CPU images, patches, and support.)

You can use the free and open source WebLogic Image Tool
to create new or updated (patched) WebLogic or Fusion Middleware Infrastructure images.
See My Oracle Support (MOS)
[Doc ID 2790123.1](https://support.oracle.com/epmos/faces/DocContentDisplay?id=2790123.1)
for community and Oracle support policies
for the WebLogic Kubernetes Toolkit.

See [WebLogic images]({{< relref "/base-images/_index.md" >}})
for information about obtaining WebLogic Server or Fusion Middleware Infrastructure images,
developer and production licensing details,
the different types of images,
creating custom images,
and patching images.

{{% notice warning %}}
The latest Oracle Container Registry (OCR) **GA images** include
the latest security patches for Oracle Linux and Java,
and do _not_ include the latest security patches for WebLogic Server.
Oracle strongly recommends using images with the latest security patches,
such as OCR Critical Patch Updates (CPU) images or custom generated images.
See [Ensure you are using recently patched images]({{< relref "/base-images/ocr-images#ensure-you-are-using-recently-patched-images" >}}).
{{% /notice %}}

#### Additional references

- For the most up-to-date and comprehensive support information, see [Supported Virtualization Technologies for Oracle Fusion Middleware](https://www.oracle.com/middleware/technologies/ias/oracleas-supported-virtualization.html) (search for keyword 'Kubernetes')
- [Running and Licensing Oracle Programs in Containers and Kubernetes](https://www.oracle.com/a/tech/docs/running-and-licensing-programs-in-containers-and-kubernetes.pdf)

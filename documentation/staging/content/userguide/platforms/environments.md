---
title: "Supported platforms"
date: 2019-02-23T16:40:54-05:00
description: "See the operator supported environments."
weight: 4
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
  - [Development-focused Kubernetes distributions](#development-focused-kubernetes-distributions)

### Supported environments

The operator supports a wide range of on-premises and cloud Kubernetes
offerings where Kubernetes is supplied for you or you set up Kubernetes
yourself. These include, but are not limited to:

- WebLogic Server and the operator are supported on Oracle Cloud offerings, such as:
  - Oracle Cloud Infrastructure (OCI)
  - Oracle Container Engine for Kubernetes (OKE)
  - Oracle Linux Cloud Native Environment (OLCNE)
  - Oracle Private Cloud Appliance (PCA)
  - Oracle Private Cloud at Customer (OPCC)

- WebLogic Server and the operator are certified on offerings, such as:
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

The operator is subject to Kubernetes, WebLogic Server, and operating system versioning prerequisites:
see [Operator prerequisites]({{< relref "/userguide/prerequisites/introduction.md" >}}).

### Pricing and licensing

The WebLogic Kubernetes Operator and Oracle Linux are open source and free;
WebLogic Server requires licenses unless used in a single developer desktop development environment.
In detail:

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

{{% notice warning %}}
Oracle strongly recommends using dated Critical Patch Update (CPU) images from the Oracle Container Registry (OCR), 
or fully patched images that you generate yourself using the WebLogic Image Tool,
for production deployments.
General Availabity (GA) images are not licensable or suitable for production use.
{{% /notice %}}

{{% notice note %}}
All of the OCR images that are discussed in this section are built using
the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) (WIT).
Customers can use WIT to build their own WebLogic Server images
(with the latest Oracle Linux images, Java updates, and WebLogic Server patches),
apply one-off patches to existing OCR images,
or overlay their own files and applications on top of an OCR image. See TBD
{{% /notice %}}


Oracle Linux is under open source license
and is completely free to download and use.

In addition, with WebLogic Server licenses and support,
customers have access to:
- The latest WebLogic Server images which bundle Java SE and the latest slim Oracle Linux images.
- Oracle support for Linux.
- Oracle support for WebLogic Server images.

Note that WebLogic Server licenses and support do _not_ include customer entitlements
for direct access to Oracle Linux support or Unbreakable Linux Network
(to directly access the standalone Oracle Linux patches). The
latest Oracle Linux patches are included the latest WebLogic Server images.

The [Oracle Container Registry](https://container-registry.oracle.com/) (OCR)
supplies two types of WebLogic Server or Fusion Middleware Infrastructure images:

- Critical Patch Updates (CPU) images.
  - Located in OCR repositories "middleware/weblogic_cpu" and "middleware/fmw-infrastructure_cpu".
  - Updated quarterly (every CPU cycle).
  - Includes critical security fixes for Oracle Linux, Java, and Oracle WebLogic Server.
  - Suitable for production use.

- General Availability (GA) images.
  - Located in OCR repositories "middleware/weblogic" and "middleware/fmw-infrastructure".
  - Updated quarterly.
  - Includes latest updates for Oracle Linux, and Java, but _not_ for Oracle WebLogic Server.
  - GA images are subject to [Oracle Technology Network (OTN) Developer License Terms](https://www.oracle.com/downloads/licenses/standard-license.html), 
    which include, but are not limited to:
     - Must only be used for the purpose of developing, testing, prototyping, and demonstrating applications.
     - Must _not_ be used for any data processing, business, commercial, or production purposes.

Example GA images:

| Sample GA image name | Description |
|-|-|
| container-registry.oracle.com/middleware/weblogic:12.2.1.4-generic-jdk8-ol7-NNNNNNTBD | JDK 8u311, Oracle Linux 7u9, and GA Oracle WebLogic Server 12.2.1.4 generic distribution for the given date |
| 12.2.1.4-generic-jdk8-ol7 | Represents latest JDK 8, latest Oracle Linux 7, and GA Oracle WebLogic Server 12.2.1.4 generic distribution |

Example CPU images:

| Sample CPU image name | Description |
|-|-|
| container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol7-211124 | JDK 8u311, Oracle Linux 7u9, and Oracle WebLogic Server 12.2.1.4 generic distribution October 2021 CPU |
| container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol7 | Represents latest JDK 8, latest Oracle Linux 7, and GA Oracle WebLogic Server 12.2.1.4 generic distribution CPU |

You may have noticed that the image tags may include keywords like `generic`, `slim`, etc.
This reflects the type of WebLogic install in the image. There are multiple types,
and the type usually can be determined by examining the image name and tag:
- `.../weblogic...:...generic...`:
  - The WebLogic generic image is supported for development and production deployment
    of WebLogic configurations using Docker.
  - Contains the same binaries as those installed by the WebLogic generic installer.
- `.../weblogic...:...slim...`:
  - The WebLogic slim image is supported for development and production deployment
    of WebLogic configurations using Docker.
  - In order to reduce image size,
    it contains a subset of the binaries included in the WebLogic generic image:
    - The WebLogic Console, WebLogic examples, WebLogic clients, Maven plug-ins,
      and Java DB have been removed.
    - All binaries that remain included are
      the same as those in the WebLogic generic image.
  - If there are requirements to monitor the WebLogic configuration,
    they should be addressed using Prometheus and Grafana, or other alternatives.
- `.../weblogic...:...dev...`:
  - The WebLogic developer image is supported for development 
    of WebLogic applications in Docker containers.
  - In order to reduce image size, it contains a subset
    of the binaries included in the WebLogic generic image:
    - WebLogic examples and Console help files have been removed.
    - All binaries that remain included are the same as those in the WebLogic generic image.
  - This image type is primarily intended to provide a Docker image
    that is consistent with the WebLogic "quick installers" intended for development only.
    Production WebLogic domains should use the WebLogic generic or WebLogic slim images.
- `.../fmw-infrastructure...:...`:
  - The Fusion Middleware (FMW) Infrastructure image is supported for
    development and production deployment of FMW configurations using Docker.
  - It contains the same binaries as those installed by the WebLogic generic installer
    and adds Fusion Middleware Control and Java Required Files (JRF)

Notes about "undated" OCR images with name tags that do _not_ include an embedded date stamp:
- They represent a GA version. 
  _Therefore they may be used in samples and development, but are
  not recommended for production use._
- Unlike images with an embedded datastamp,
  which represent a specific version,
  undated images are periodically updated to 
  the latest available versions of their GA equivalents.
  _Therefore they change over time in the repository
  even though their name and tag remain the same._
- Examples of undated images include
  `registry.oracle.com/middleware/weblogic:TAG` images
  where `TAG` is one of `12.2.1.3`, `12.2.1.4`, `14.1.1.0-11`, or `14.1.1.0-8`.
  These are created with the generic installer, 
  Oracle Linux 7-slim, and JDK8 
  except for `14.1.1.0-11` (which has JDK11).
- The tag for an undated image may not 
  specify its WebLogic installer type, 
  in which case you can assume it is `generic`.
  Otherwise, such images may have
  a tag that includes the string `slim` or `dev`
  string, in which case they have the 
  related installer type and are _not_ `generic` images.

TBD 
- Monica:
  - A Monica will update 14.1.1.0 images, and continue to update them
  - B Provide exact names for image table above
  - F Monica plans to create a a table 
      in the landing page for OCR with all variations.
      When ready, we can add a link.
- Tom:
  - C Update GA image table above to have exact correct image names
      when A & B are available.
  - D Link to this new information from the domain images doc in key places.
  - Link to F when avaialble.

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

OpenShift can be a cloud platform or can be deployed on premise.

Operator 2.0.1+ is certified for use on OpenShift Container Platform 3.11.43+, with Kubernetes 1.11.5+.

Operator 2.5.0+ is certified for use on OpenShift Container Platform 4.3.0+ with Kubernetes 1.16.2+.

To accommodate OpenShift security requirements:
- For security requirements to run WebLogic Server in OpenShift, see the [OpenShift chapter]({{<relref "/security/openshift.md">}}) in the Security section.
- Beginning with operator version 3.3.2, specify the `kubernetesPlatorm` Helm chart property with value `OpenShift`. For more information, see [Operator Helm configuration values]({{<relref "/userguide/managing-operators/using-helm#operator-helm-configuration-values">}}).

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

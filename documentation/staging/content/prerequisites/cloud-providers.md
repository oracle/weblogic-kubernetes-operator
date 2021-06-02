---
title: "Cloud providers"
date: 2019-02-23T16:40:54-05:00
weight: 2
---



The Oracle [Global Pricing and Licensing site](https://www.oracle.com/corporate/pricing/specialty-topics.html)
provides details about licensing practices and policies.
WebLogic Server and the operator are supported on "Authorized Cloud Environments" as defined in
[this Oracle licensing policy](https://www.oracle.com/assets/cloud-licensing-070579.pdf) and
[this list of eligible products](http://www.oracle.com/us/corporate/pricing/authorized-cloud-environments-3493562.pdf).

The official document that defines the supported configurations is [here](https://www.oracle.com/middleware/technologies/ias/oracleas-supported-virtualization.html).

In accordance with these policies, the operator and WebLogic Server are supported on Oracle Cloud
Infrastructure using *Oracle Container Engine for Kubernetes*, or in a cluster running *Oracle Linux
Container Services for use with Kubernetes* on OCI Compute, and on "Authorized Cloud Environments".

### Microsoft Azure Kubernetes Service

[Azure Kubernetes Service (AKS)](https://docs.microsoft.com/en-us/azure/aks/) is a hosted Kubernetes environment.  The WebLogic Kubernetes
Operator, Oracle WebLogic Sever 12c, and Oracle Fusion Middleware Infrastructure 12c are fully supported and certified on Azure Kubernetes Service (as per the documents
referenced above).

AKS support and limitations:

* Both Domain in Image and Domain in PV domain home source types are supported.  
* For Domain in PV, we support Azure Files volumes accessed through
  a persistent volume claim; see [here](https://docs.microsoft.com/en-us/azure/aks/azure-files-volume).
* Azure Load Balancers are supported when provisioned using a Kubernetes Service of `type=LoadBalancer`.
* Oracle databases running in Oracle Cloud Infrastructure are supported for Fusion Middleware
  Infrastructure MDS data stores only when accessed through an OCI FastConnect.
* Windows Server containers are not currently supported, only Linux containers.

### VMware Tanzu Kubernetes Service

Tanzu Kubernetes Grid (TKG) is a managed Kubernetes Service that lets you quickly deploy and manage Kubernetes clusters. The WebLogic Kubernetes
Operator and Oracle WebLogic Sever are fully supported and certified on VMware Tanzu Kubernetes Grid Multicloud 1.1.3 (with vSphere 6.7U3).

TKG support and limitations:

* Both Domain in Image and Model in Image domain home source types are supported. Domain in PV is not supported.
* VSphere CSI driver supports only volumes with Read-Write-Once policy. This does not allow writing stores on PV.  
   * For applications requiring HA, use JMS and JTA stores in the database.
* The ingress used for certification is NGINX, with MetalLB load balancer.


### Oracle Linux Cloud Native Environment (OLCNE)

[Oracle Linux Cloud Native Environment](https://docs.oracle.com/en/operating-systems/olcne/) is a fully integrated suite for the development and management of cloud-native applications. Based on Open Container Initiative (OCI) and Cloud Native Computing Foundation (CNCF) standards, Oracle Linux Cloud Native Environment delivers a simplified framework for installations, updates, upgrades, and configuration of key features for orchestrating microservices.

WebLogic Server and the WebLogic Kubernetes Operator are certified and supported on Oracle Linux Cloud Native Environment. Operator 2.6.0 provides certified support of OLCNE 1.1 with Kubernetes 1.17.0.

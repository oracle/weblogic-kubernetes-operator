---
title: "Azure Kubernetes Service"
date: 2020-07-12T18:22:31-05:00
weight: 8
description: "Sample for using the operator to set up a WLS cluster on the Azure Kubernetes Service."
---


### Contents

   - [Introduction](#introduction)
     - [Azure Kubernetes Service cluster](#azure-kubernetes-service-cluster)
     - [Domain home source types](#domain-home-source-types)
   - [Domain in PV]({{< relref "/samples/simple/azure-kubernetes-service/domain-on-pv.md" >}}): Running the WebLogic cluster on AKS with domain home on PV
   - [Model in Image]({{< relref "/samples/simple/azure-kubernetes-service/model-in-image.md" >}}): Running the WebLogic cluster on AKS with domain model in image
   - [Troubleshooting]({{< relref "/samples/simple/azure-kubernetes-service/troubleshooting.md" >}})


### Introduction

This sample demonstrates how to use the [Oracle WebLogic Server Kubernetes Operator]({{< relref "/_index.md" >}}) (hereafter "the operator") to set up a WebLogic Server (WLS) cluster on the Azure Kubernetes Service (AKS). After going through the steps, your WLS domain runs on an AKS cluster.  You have several options for managing the cluster, depending on which [domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}) you choose.  With Domain in PV, you can manage your WLS domain by accessing the WebLogic Server Administration Console or WLST.  With Model in Image, you use the operator to perform WLS administrative operations.

#### Azure Kubernetes Service cluster

Azure Kubernetes Service makes it simple to deploy a managed Kubernetes cluster in Azure. AKS reduces the complexity and operational overhead of managing Kubernetes by offloading much of that responsibility to Azure. As a hosted Kubernetes service, Azure handles critical tasks like health monitoring and maintenance for you. The Kubernetes masters are managed by Azure. You only manage and maintain the agent nodes. As a managed Kubernetes service, AKS is free - you only pay for the agent nodes within your clusters, not for the masters.

To learn more, see the [What is Azure Kubernetes Service?](https://docs.microsoft.com/en-us/azure/aks/intro-kubernetes).

#### Domain home source types

This sample demonstrates running the WebLogic cluster on AKS using two domain home types. The instructions for each are self-contained and independent. This section lists the domain home source types recommended for use with AKS, along with some benefits of each. For complete details on domain home source types, see [Choose a domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

- [Model in Image]({{< relref "/samples/simple/azure-kubernetes-service/model-in-image.md" >}}): running the WebLogic cluster on AKS with domain home in image offers the following benefits:

   - Reuse image to create different domains with different `domainUID` and different configurations.
   - Mutate the domain home configuration with additional model files supplied in a `ConfigMap`.  Many such changes do not need to restart the entire domain for the change to take effect.
   - The model file syntax is far simpler and less error prone than the configuration override syntax, and, unlike configuration overrides, allows you to directly add data sources and JMS modules.

- [Domain in PV]({{< relref "/samples/simple/azure-kubernetes-service/domain-on-pv.md" >}}): running the WebLogic cluster on AKS with domain home in PV offers the following benefits:

   - Use standard Oracle-provided images with patches installed.
   - No Docker environment required. You are able to run your business quickly without building knowledge of Docker.
   - Mutate the live domain configuration with Administration Console from a browser or WLST.

### References

For references to the relevant user documentation, see:
 - [Choose a domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}) user documentation
 - [Model in Image]({{< relref "/userguide/managing-domains/model-in-image/_index.md" >}}) user documentation

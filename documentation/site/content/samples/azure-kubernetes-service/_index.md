---
title: "Azure Kubernetes Service"
date: 2020-07-12T18:22:31-05:00
weight: 8
description: "Sample for using the operator to set up a WLS cluster on the Azure Kubernetes Service."
---

{{< table_of_contents >}}

### Introduction

This sample demonstrates how to use the [WebLogic Kubernetes Operator]({{< relref "/_index.md" >}}) (hereafter "the operator") to set up a WebLogic Server (WLS) cluster on the Azure Kubernetes Service (AKS). After going through the steps, your WLS domain runs on an AKS cluster.  You have several options for managing the cluster, depending on which [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) you choose.  With Domain on PV, you can manage your WLS domain by accessing the WebLogic Server Administration Console or WLST.  With Model in Image, you use the operator to perform WLS administrative operations.

{{% notice note %}}
For an alternative approach to this sample, see the [Oracle WebLogic Server on AKS Azure Marketplace offering]({{<relref "/managing-domains/aks/_index.md">}}), which automates the provisioning of
the AKS cluster, AKS resources, Azure Container Registry (ACR), load-balancer, WebLogic Kubernetes Operator, and WebLogic Server images.
{{% /notice %}}

#### Azure Kubernetes Service cluster

{{< readfile file="/samples/azure-kubernetes-service/includes/aks-value-prop.txt" >}}


#### Domain home source types

This sample demonstrates running the WebLogic cluster on AKS using two domain home types. The instructions for each are self-contained and independent. This section lists the domain home source types recommended for use with AKS, along with some benefits of each. For complete details on domain home source types, see [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).

- [Model in Image]({{< relref "/samples/azure-kubernetes-service/model-in-image.md" >}}): running the WebLogic cluster on AKS with model in image offers the following benefits:

    - Reuse image to create different domains with different `domainUID` and different configurations.
    - Mutate the domain home configuration with additional model files supplied in a `ConfigMap`.  Many such changes do not need to restart the entire domain for the change to take effect.
    - The model file syntax is far simpler and less error prone than the configuration override syntax, and, unlike configuration overrides, allows you to directly add data sources and JMS modules.

- [Domain on PV]({{< relref "/samples/azure-kubernetes-service/domain-on-pv.md" >}}): running the WebLogic cluster on AKS with domain home on PV offers the following benefits:

    - Use standard Oracle-provided images with patches installed.
    - No Docker environment required. You are able to run your business quickly without building knowledge of Docker.
    - Mutate the live domain configuration with Administration Console from a browser or WLST.

{{% notice tip %}} Stop and Start an Azure Kubernetes Service (AKS) cluster using Azure CLI, as described [in the azure docs](https://docs.microsoft.com/en-us/azure/aks/start-stop-cluster). This allows you to optimize costs during your AKS cluster's idle time. Don't pay for running development clusters unless they are actively being used.  You can pick up objects and cluster state right where you were left off.
{{% /notice %}}

### References

For references to the relevant user documentation, see:
- [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) user documentation
- [Model in Image]({{< relref "/managing-domains/model-in-image/_index.md" >}}) user documentation

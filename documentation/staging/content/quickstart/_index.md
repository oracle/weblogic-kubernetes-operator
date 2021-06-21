+++
title = "Quick Start"
date = 2019-02-22T15:27:38-05:00
weight = 1
pre = "<b> </b>"
+++


The Quick Start guide provides a simple tutorial to help you get the operator up and running quickly. Use this Quick Start guide to create a WebLogic Server deployment in a Kubernetes cluster with the WebLogic Kubernetes Operator. Please note that this walk-through is for demonstration purposes only, not for use in production.
These instructions assume that you are already familiar with Kubernetes. If you need more detailed instructions, please
refer to the [User guide]({{< relref "/userguide/_index.md" >}}).

{{% notice note %}}
All Kubernetes distributions and managed services have small differences. In particular,
the way that persistent storage and load balancers are managed varies significantly.  
You may need to adjust the instructions in this guide to suit your particular flavor of Kubernetes.
{{% /notice %}}



For this exercise, you’ll need a Kubernetes cluster. If you need help setting one up, check out our [cheat sheet]({{< relref "/userguide/kubernetes/k8s-setup.md" >}}). This guide assumes a single node cluster.

The operator uses Helm to create and deploy the necessary resources and then run the operator in a Kubernetes cluster. For Helm installation and usage information, see [Install Helm]({{< relref "/userguide/managing-operators/_index.md#install-helm" >}}).

You should clone this repository to your local machine so that you have access to the
various sample files mentioned throughout this guide:
```shell
$ git clone --branch v3.2.3 https://github.com/oracle/weblogic-kubernetes-operator
```

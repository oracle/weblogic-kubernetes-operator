---
title: "Prerequisites"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 2
---

For this exercise, you’ll need a Kubernetes cluster. If you need help setting one up, check out our [cheat sheet]({{< relref "/userguide/overview/k8s-setup.md#cheat-sheet-for-setting-up-kubernetes" >}}). This guide assumes a single node cluster.

The operator uses Helm to create and deploy the necessary resources and then run the operator in a Kubernetes cluster. For Helm installation and usage information, see [Install Helm and Tiller]({{< relref "/userguide/managing-operators/_index.md#install-helm-and-tiller" >}}).

You should clone this repository to your local machine so that you have access to the
various sample files mentioned throughout this guide:
```bash
$ git clone https://github.com/oracle/weblogic-kubernetes-operator
```

---
title: "Ingress"
date: 2019-02-23T17:32:31-05:00
weight: 5
description: "Load balancer sample scripts."
---


The Oracle WebLogic Server Kubernetes Operator supports three load balancers: Traefik, Voyager, and Apache. We provide samples that demonstrate how to install and configure each one. The samples are located in following folders:

* [traefik](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/traefik/README.md)
* [voyager](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/voyager/README.md)
* apache-samples/[custom-sample](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/apache-samples/custom-sample/README.md)
* apache-samples/[default-sample](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/apache-samples/default-sample/README.md)
* [ingress-per-domain](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/ingress-per-domain/README.md)
* [apache-webtier](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/apache-webtier/README.md)

{{% notice note %}}
The apache-webtier script contains a Helm chart that is used in the Apache samples.
{{% /notice %}}

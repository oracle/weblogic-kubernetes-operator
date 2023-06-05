---
title: "Ingress"
date: 2019-02-23T17:32:31-05:00
weight: 5
description: "Ingress controllers and load balancer sample scripts."
---

The WebLogic Kubernetes Operator supports NGINX and Traefik. We provide samples that demonstrate how to install and configure each one.

{{% notice note %}}
For production environments, we recommend NGINX, Traefik (2.2.1 or later) ingress controllers or the load balancer provided by your cloud provider.
{{% /notice %}}


The samples are located in following folders:

* [Traefik](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/charts/traefik/README.md)
* [NGINX](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/charts/nginx/README.md)
* [Ingress-per-domain](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/charts/ingress-per-domain/README.md)

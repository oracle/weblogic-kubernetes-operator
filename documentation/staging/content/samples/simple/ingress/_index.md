---
title: "Ingress"
date: 2019-02-23T17:32:31-05:00
weight: 5
description: "Ingress controllers and load balancer sample scripts."
---


The Oracle WebLogic Server Kubernetes Operator supports NGINX, Traefik, Voyager, and Apache. We provide samples that demonstrate how to install and configure each one.

{{% notice note %}}
For production environments, we recommend NGINX, Voyager, Traefik (2.2.1 or later) ingress controllers, Apache, or the load balancer provided by your cloud provider.
{{% /notice %}}


The samples are located in following folders:

* [Traefik](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/traefik/README.md)
* [Voyager](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/voyager/README.md)
* [NGINX](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/nginx/README.md)
* Apache-samples/[custom-sample](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/apache-samples/custom-sample/README.md)
* Apache-samples/[default-sample](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/apache-samples/default-sample/README.md)
* [Ingress-per-domain](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/ingress-per-domain/README.md)
* [Apache-webtier](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/apache-webtier/README.md)

{{% notice note %}}
The Apache-webtier script contains a Helm chart that is used in the Apache samples.
{{% /notice %}}

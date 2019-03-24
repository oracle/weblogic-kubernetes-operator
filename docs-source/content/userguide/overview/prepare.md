---
title: "Prepare your environment"
date: 2019-02-23T16:43:10-05:00
weight: 1

---


#### Set up your Kubernetes cluster

If you need help setting up a Kubernetes environment, check our [cheat sheet]({{< relref "/userguide/overview/k8s-setup.md" >}}).

After creating Kubernetes clusters, you can optionally:

* Create load balancers to direct traffic to backend domains.
* Configure Kibana and Elasticsearch for your operator logs.


#### Load balance with an Ingress controller or a web server

You can choose a load balancer provider for your WebLogic domains running in a Kubernetes cluster. Please refer to the [WebLogic Operator Load Balancer Samples](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/README.md) for information about the current capabilities and setup instructions for each of the supported load balancers.


#### Configure Kibana and Elasticsearch

You can send the operator logs to Elasticsearch, to be displayed in Kibana. Use
this [sample script]({{< relref "/samples/simple/elastic-stack/_index.md" >}}) to configure Elasticsearch and Kibana deployments and services.

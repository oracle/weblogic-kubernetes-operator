---
title: "Setup checklist"
date: 2019-02-23T16:43:10-05:00
weight: 1

---



1. Fulfill the [operator prerequisite]({{< relref "/userguide/prerequisites/introduction.md" >}}) requirements.

1. Set up [Kubernetes]({{< relref "/userguide/prepare/k8s-setup.md" >}}).

1. Install [Helm]({{< relref "/userguide/managing-operators/_index.md#install-helm" >}}).

1. Run a [database]({{< relref "/userguide/prepare/database.md" >}}).

1. Load balance with an ingress controller or a web server. For information about the current capabilities and setup instructions for each of the supported load balancers, see the [WebLogic Operator Load Balancer Samples](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/README.md).

1. Configure Kibana and Elasticsearch. You can send the operator logs to Elasticsearch, to be displayed in Kibana. Use
this [sample script]({{< relref "/samples/simple/elastic-stack/_index.md" >}}) to configure Elasticsearch and Kibana deployments and services.

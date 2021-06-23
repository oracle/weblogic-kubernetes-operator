---
title: "Setup checklist"
date: 2019-02-22T15:27:38-05:00
weight: 3
description: "Follow these steps to set up your environment."
---




1. Fulfill the [operator prerequisite]({{< relref "/userguide/prerequisites/introduction.md" >}}) requirements.

1. Set up [Kubernetes]({{< relref "/userguide/kubernetes/k8s-setup.md" >}}).

1. Optional. Enable [Istio]({{< relref "/userguide/istio/istio.md" >}}).

1. Install [Helm]({{< relref "/userguide/managing-operators/_index.md#install-helm" >}}).

1. Get the operator image from the [GitHub Container Registry](https://github.com/orgs/oracle/packages/container/package/weblogic-kubernetes-operator).

1. [Install the operator]({{< relref "/userguide/managing-operators/installation.md" >}}).

1. Optional. Run a database. For example, run an [Oracle database]({{< relref "/samples/simple/database/#run-the-oracle-database-in-kubernetes" >}}) inside Kubernetes.

1. Optional. Load balance with an ingress controller or a web server. For information about the current capabilities and setup instructions for each of the supported load balancers, see the [WebLogic Operator Load Balancer Samples](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/README.md).

1. Optional. Configure Kibana and Elasticsearch. You can send the operator logs to Elasticsearch, to be displayed in Kibana. Use
this [sample script]({{< relref "/samples/simple/elastic-stack/_index.md" >}}) to configure Elasticsearch and Kibana deployments and services.

1. Optional. Create persistent file storage. For example, a Kubernetes [PersistentVolume (PV) and PersistentVolumeClaim (PVC)]({{< relref "/samples/simple/storage/_index.md" >}}).

1. Set up your domain. For information, see [Choose a domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

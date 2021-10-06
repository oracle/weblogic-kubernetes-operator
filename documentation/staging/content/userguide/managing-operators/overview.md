---
title: "Overview"
date: 2019-02-23T16:47:21-05:00
weight: 1
description: "A quick introduction to the operator runtime."
---

An operator runtime is a process that runs in a container deployed into a Kubernetes Pod and that automatically manages
[domain resources]({{<relref "/userguide/managing-domains/domain-resource.md">}}).
A domain resource references WebLogic domain configuration,
a WebLogic installation image,
Kubernetes secrets,
and anything else necessary to run a particular WebLogic domain.
You can deploy, delete, and manage domain resources while an operator is running.
For a full overview of how an operator runtime and its domain resources work together, see the
[terms]({{<relref "/userguide/introduction/terms.md">}}),
[design philosophy]({{<relref "/userguide/introduction/terms.md">}}),
and [architecture]({{<relref "/userguide/introduction/terms.md">}}) documentation.

A completely installed and running WebLogic Kubernetes Operator environment includes:

- A Kubernetes cluster.
- A Kubernetes custom resource definition (CRD) that, when installed,
  enables the Kubernetes API server and the operator to monitor and manage domain resource instances.
- One or more operator runtimes that monitor Kubernetes namespaces for domain resources.

After an operator environment is setup, you can deploy domain resources.
When an operator runtime detects a domain, it will generate and deploy the domain's pods, services, and potentially other resources.
The operator will also monitor the domain for changes, such as a request to change the number of pods in a WebLogic cluster,
will update status fields on the domain's domain resource, and will generate Kubernetes events for the domain in the domain's namespace.
If an operator is shutdown, then its domains' pods, services, and such, will remain running but changes
to a domain resource will not be detected and honored until the operator is restarted.

A Helm chart is used for [installing]({{<relref "/userguide/managing-operators/installation.md#install-the-operator-helm-chart">}}) operator runtimes and their related resources (including the CRD), and for [configuring]({{<relref "/userguide/managing-operators/using-helm.md">}}) the operator. For a detailed discussion about configuring the namespaces which an operator manages, plus preparing a namespace for operator management, see [Namespaces]({{<relref "/userguide/managing-operators/namespace-management.md">}}).

Optionally, you can monitor an operator and its log using an [Elastic Stack](https://www.elastic.co/what-is/)
(previously referred to as the ELK Stack, after Elasticsearch, Logstash, and Kibana).
For an example, see the [operator Elastic Stack sample]({{<relref "/samples/elastic-stack/operator/_index.md#elastic-stack-per-operator-configuration">}}).

For advanced users, the operator provides an optional REST server that
you can use as an alternate method for getting a list of WebLogic domains and clusters that an operator manages,
and to initiate scaling operations (instead of directly performing such operations using the Kubernetes API or the Kubernetes command line).
See the operator [REST services]({{<relref "/userguide/managing-operators/the-rest-api.md">}}).

For an example of installing the operator, setting the namespace that it monitors, deploying a domain resource to its monitored namespace, and uninstalling the operator, see the [Quick Start]({{< relref "/quickstart/_index.md" >}}).

{{% notice note %}}
There can be multiple operators in a Kubernetes cluster, and in that case, you must ensure that the namespaces managed by these operators do not overlap.
_At most, a namespace can be managed by one operator._
{{% /notice %}}

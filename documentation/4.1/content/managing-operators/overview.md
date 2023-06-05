---
title: "Overview"
date: 2021-12-05T16:47:21-05:00
weight: 1
description: "An introduction to the operator runtime."
---

An operator runtime is a process that runs in a container deployed into a Kubernetes Pod and that automatically manages
[domain resources]({{<relref "/managing-domains/domain-resource.md">}}).
A domain resource references WebLogic domain configuration,
a WebLogic installation image,
Kubernetes secrets,
and anything else necessary to run a particular WebLogic domain.
The operator requires Helm for its installation and tuning.

A single operator instance is capable of managing multiple domains
in multiple namespaces depending on how it is configured.
A Kubernetes cluster can host multiple operators, but no more than one per namespace,
and two operators cannot manage domains in the same namespace.
You can deploy, delete, and manage domain resources while an operator is running.

A completely installed and running WebLogic Kubernetes Operator environment includes:

- A Kubernetes cluster.
- A pair of Kubernetes custom resource definitions (CRD) for domain and cluster resource that, when installed,
  enables the Kubernetes API server and the operator to monitor and manage their resource instances.
- One or more operator runtimes, each deployed to a different namespace, that monitor Kubernetes namespaces for domain resources.
- A WebLogic domain resource conversion webhook that runs in a single namespacre in the Kubernetes cluster, that is shared by the operator runtimes, and that handles the automatic conversion of older versions of the domain resource.
- Each operator is associated with a local Kubernetes service account for security purposes. The service account is deployed to the same namespace as the operator.

When an operator runtime detects a domain,
it will first run a short-lived Kubernetes job (the "introspector job")
that reads and checks the domain's WebLogic configuration,
and then it will generate and deploy the domain's pods, services, and potentially other resources.
The operator will also monitor the domain for changes,
such as a request to change the number of pods in a WebLogic cluster,
will update status fields on the domain's domain resource,
and will generate Kubernetes events for the domain in the domain's namespace.
If the operator detects that a domain is deleted, then
it will shut down any running pods associated with the domain
and delete the resources that it has deployed for the domain.
If an operator is shut down,
then its domains' pods, services, and such, will remain running but changes
to a domain resource will not be detected and honored until the operator is restarted.

Optionally, you can monitor an operator and its log using an [Elastic Stack](https://www.elastic.co/elastic-stack/)
(previously referred to as the ELK Stack, after Elasticsearch, Logstash, and Kibana).
For an example, see the operator [Elastic Stack]({{<relref "/samples/elastic-stack/operator/_index.md#elastic-stack-per-operator-configuration">}}) sample.

For advanced users, the operator provides an optional REST server that
you can use as an alternative method for getting a list of WebLogic domains and clusters that an operator manages,
and to initiate scaling operations (instead of directly performing such operations using the Kubernetes API or the Kubernetes command line).
See the operator [REST services]({{<relref "/managing-operators/the-rest-api.md">}}).

References:
- For a full overview of how an operator runtime and its domain resources work together, see the
  [terms]({{<relref "/introduction/terms.md">}}),
  [design philosophy]({{<relref "/introduction/design.md">}}),
  and [architecture]({{<relref "/introduction/architecture.md">}}) documentation.
- For information about using a Helm chart to install, update, or upgrade
  the operator, its CRD, or its service account,
  see the operator
  [Prepare for installation]({{<relref "/managing-operators/preparation.md">}})
  and [Installation]({{<relref "/managing-operators/installation.md">}}) guides.
- All operator Helm chart configuration options are
  documented in the operator [Configuration Reference]({{<relref "/managing-operators/using-helm.md">}}).
- For a detailed description of configuring the namespaces which an operator manages,
  plus preparing a namespace for operator management,
  see [Namespace management]({{<relref "/managing-operators/namespace-management.md">}}).

{{% notice tip %}}
For an example of installing the operator,
setting the namespace that it monitors,
deploying a domain resource to its monitored namespace,
and uninstalling the operator,
see the [Quick Start]({{< relref "/quickstart/_index.md" >}}).
{{% /notice %}}

{{% notice note %}}
There can be multiple operators in a Kubernetes cluster,
and in that case, you must ensure that the namespaces managed by these operators do not overlap.
_At most, a namespace can be managed by one operator._
In addition, you cannot deploy more than operator to a particular namespace.
See [Common mistakes and solutions]({{<relref "/managing-operators/common-mistakes.md">}}).
{{% /notice %}}

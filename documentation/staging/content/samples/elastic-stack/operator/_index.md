---
title: "Operator"
date: 2019-10-01T14:32:31-05:00
weight: 1
description: "Sample for configuring the Elasticsearch and Kibana deployments and services for the operator's logs."
---

The operator Helm chart includes the option of installing the necessary Kubernetes resources for Elastic Stack integration.

You are responsible for configuring Kibana and Elasticsearch, then configuring the operator Helm chart to send events to Elasticsearch. In turn, the operator Helm chart configures Logstash in the operator deployment to send the operator's log contents to that Elasticsearch location.

#### Elastic Stack per-operator configuration

As part of the Elastic Stack integration, Logstash configuration occurs for each deployed operator instance.  You can use the following configuration values to configure the integration:

* Set `elkIntegrationEnabled` is `true` to enable the integration.
* Set `logStashImage` to override the default version of Logstash to be used (`logstash:6.6.0`).
* Set `elasticSearchHost` and `elasticSearchPort` to override the default location where Elasticsearch is running (`elasticsearch2.default.svc.cluster.local:9201`). This will configure Logstash to send the operator's log contents there.
* Set `createLogStashConfigMap` to `true` to use the default Logstash configuration, or set it to `false` and create a ConfigMap named `weblogic-operator-logstash-cm` in the operator's namespace with your own Logstash pipeline configuration.

For additional details, see [Elastic Stack integration]({{< relref "/userguide/managing-operators/using-helm#elastic-stack-integration" >}}) Helm commands.

#### Sample to configure Elasticsearch and Kibana

This sample configures the Elasticsearch and Kibana deployments and services.
It's useful for trying out the operator in a Kubernetes cluster that doesn't already
have them configured.

It runs the Elastic Stack on the same host and port that the operator's Helm chart defaults
to, therefore, you only need to set `elkIntegrationEnabled` to `true` in your
`values.yaml` file.

To control Elasticsearch memory parameters (Heap allocation and Enabling/Disabling swapping), open the file `elasticsearch_and_kibana.yaml`, search for `env` variables of the Elasticsearch container and change the values of the following:

* `ES_JAVA_OPTS`: value may contain, for example, `-Xms512m` `-Xmx512m` to lower the default memory usage (please be aware that this value is applicable for demonstration purposes only and it is not the one recommended by Elasticsearch).
* `bootstrap.memory_lock`: value may contain `true` (enables the usage of `mlockall`, to try to lock the process address space into RAM, preventing any Elasticsearch memory from being swapped out) or `false` (disables the usage of `mlockall`).

To install Elasticsearch and Kibana, use:
```shell
$ kubectl apply -f kubernetes/samples/scripts/elasticsearch-and-kibana/elasticsearch_and_kibana.yaml
```

To remove them, use:
```shell
$ kubectl delete -f kubernetes/samples/scripts/elasticsearch-and-kibana/elasticsearch_and_kibana.yaml
```

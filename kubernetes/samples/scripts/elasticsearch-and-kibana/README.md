# Sample to deploy Elasticsearch and Kibana


When a user installs the WebLogic operator Helm chart, the user can set
`elkIntegrationEnabled` to `true` in their `values.yaml` to tell the operator to send the
contents of the operator's logs to Elasticsearch.

Typically, a user would have already configured Elasticsearch and Kibana in the
Kubernetes cluster, and also would specify `elasticSearchHost` and `elasticSearchPort`
in their `values.yaml` file to point to where Elasticsearch is already running.

This sample configures the Elasticsearch and Kibana deployments and services.
It's useful for trying out the operator in a Kubernetes cluster that doesn't already
have them configured.

It runs Elasticstack on the same host and port that the operator's Helm chart defaults
to, therefore, the customer only needs to set `elkIntegrationEnabled` to `true` in their
`values.yaml` file.``

To install ElasticSearch and Kibana, use:
```
$  kubectl apply -f kubernetes/samples/scripts/elasticsearch-and-kibana/elasticsearch_and_kibana.yaml
```

To remove them, use:
```
$  kubectl delete -f kubernetes/samples/scripts/elasticsearch-and-kibana/elasticsearch_and_kibana.yaml
```

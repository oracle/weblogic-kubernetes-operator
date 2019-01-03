# Sample to deploy Elasticsearch and Kibana


When you install the WebLogic operator Helm chart, you can set
`elkIntegrationEnabled` to `true` in your `values.yaml` file to tell the operator to send the contents of the operator's logs to Elasticsearch.

Typically, you would have already configured Elasticsearch and Kibana in the
Kubernetes cluster, and also would have specified `elasticSearchHost` and `elasticSearchPort` in your `values.yaml` file to point to where Elasticsearch is already running.

This sample configures the Elasticsearch and Kibana deployments and services.
It's useful for trying out the operator in a Kubernetes cluster that doesn't already
have them configured.

It runs the Elastic Stack on the same host and port that the operator's Helm chart defaults
to, therefore, you only need to set `elkIntegrationEnabled` to `true` in your
`values.yaml` file.

To install Elasticsearch and Kibana, use:
```
$  kubectl apply -f kubernetes/samples/scripts/elasticsearch-and-kibana/elasticsearch_and_kibana.yaml
```

To remove them, use:
```
$  kubectl delete -f kubernetes/samples/scripts/elasticsearch-and-kibana/elasticsearch_and_kibana.yaml
```

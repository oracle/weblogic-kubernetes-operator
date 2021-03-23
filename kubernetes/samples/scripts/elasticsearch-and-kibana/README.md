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

To control Elasticsearch memory parameters (Heap allocation and Enabling/Disabling swapping) please open the file `elasticsearch_and_kibana.yaml`, search for env variables of the elasticsearch container and change the values of the following.

* ES_JAVA_OPTS: value may contain for example -Xms512m -Xmx512m to lower the default memory usage (please be aware that this value is only applicable for demo purpose and it is not the one recommended by Elasticsearch itself)
* bootstrap.memory_lock: value may contain true (enables the usage of mlockall to try to lock the process address space into RAM, preventing any Elasticsearch memory from being swapped out) or false (disables the usage of mlockall to try to lock the process address space into RAM, preventing any Elasticsearch memory from being swapped out). 

To install Elasticsearch and Kibana, use:
```shell
$ kubectl apply -f kubernetes/samples/scripts/elasticsearch-and-kibana/elasticsearch_and_kibana.yaml
```

To remove them, use:
```shell
$ kubectl delete -f kubernetes/samples/scripts/elasticsearch-and-kibana/elasticsearch_and_kibana.yaml
```

**TODO** write me

## Preparing your Kubernetes environment

**TODO** write intro 

### Set up your Kubernetes cluster

If you need help setting up a Kubernetes environment, check our [cheat sheet](site/k8s_setup.md).

After creating Kubernetes clusters, you can optionally:
* Create load balancers to direct traffic to backend domains.
* Configure Kibana and Elasticsearch for your operator logs.

### Configuring Kibana and Elasticsearch

You can send the operator logs to Elasticsearch, to be displayed in Kibana. Use this [sample script](kubernetes/samples/scripts/elasticsearch_and_kibana.yaml) to configure Elasticsearch and Kibana deployments and services.

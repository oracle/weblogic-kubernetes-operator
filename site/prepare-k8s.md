# Preparing your Kubernetes environment



## Set up your Kubernetes cluster

If you need help setting up a Kubernetes environment, check our [cheat sheet](k8s_setup.md).

After creating Kubernetes clusters, you can optionally:
* Create load balancers to direct traffic to backend domains.
* Configure Kibana and Elasticsearch for your operator logs.


### Load balancing with an Ingress controller or a web server

You can choose a load balancer provider for your WebLogic domains running in a Kubernetes cluster. Please refer to the [WebLogic Operator Load Balancer Samples](../kubernetes/samples/charts/README.md) for information about the current capabilities and setup instructions for each of the supported load balancers.


### Configuring Kibana and Elasticsearch

You can send the operator logs to Elasticsearch, to be displayed in Kibana. Use
this [sample script](/kubernetes/samples/scripts/elasticsearch-and-kibana/README.md) to configure Elasticsearch and Kibana deployments and services.

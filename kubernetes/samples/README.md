# WebLogic Operator Samples

## Sample scripts

* [Sample PV and PVC](scripts/create-weblogic-domain-pv-pvc/README.md) contains sample scripts to create a PV/PVC that can be used by a domain custom resource.
* [Sample domain home on a persistent volume](scripts/create-weblogic-domain/domain-home-on-pv/README.md) contains sample scripts to create a WebLogic domain home on an existing PV/PVC, and the domain customer resource YAML file for deploying the genrated WebLogic domain.
* [Sample ElasticSearch and Kibana configuration](scripts/elasticsearch_and_kibana.yaml) contains sample configuration for the ElasticSearch and Kibana deployments and services for the Operator's logs.
* [Sample self-signed certificate and private key](scripts/generate-external-rest-identity.sh) contains a sample script for generating a self-signed certificate and private key that can be used for the operator's external REST API.

## Sample Helm Charts

* [Sample Traefik Helm Chart](charts/traefik/README.md) for setting up a Traefik load balancer for WebLogic clusters.
* [Sample Voyager Helm Chart](charts/voyager/README.md) for setting up a Voyager load balancer for WebLogic clusters.
* [Sample Ingress Helm Chart](charts/ingress-per-domain/README.md) for setting up an Kubernetes ingress for each WebLogic cluster for Traefik or Voyager load balancer. 
* [Sample Apache  Helm Chart](charts/apache-webtier/README.md) for setting up a load balancer for WebLogic clusters using the Apache HTTP server with WebLogic Server Plugins.

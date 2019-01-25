# WebLogic operator samples

These samples provide simple demonstrations of how to accomplish common tasks.
The samples are **not** intended to be used in production deployments or to be depended upon to
create production environments.  They are provided for educational and demonstration purposes only.

While these samples may be useful and usable as is, it is intended that you would read through all of the sample code in detail, understand how the given sample works, and then customize it to suit your needs.  

## Sample scripts

* [Sample for creating a Kubernetes secret](scripts/create-weblogic-domain-credentials/README.md) that contains the Administration Server credentials. This secret can be used in creating a WebLogic domain resource.
* [Sample for creating a PV or PVC](scripts/create-weblogic-domain-pv-pvc/README.md) that can be used by a domain resource as the persistent storage for the WebLogic domain home or log files.
* [Sample for creating a WebLogic domain home on an existing PV or PVC](scripts/create-weblogic-domain/domain-home-on-pv/README.md), and the domain resource YAML file for deploying the generated WebLogic domain.
* [Sample for creating a WebLogic domain home inside a Docker image](scripts/create-weblogic-domain/domain-home-in-image/README.md), and the domain resource YAML file for deploying the generated WebLogic domain.
* [Sample for configuring the Elasticsearch and Kibana](scripts/elasticsearch-and-kibana/README.md) deployments and services for the operator's logs.
* [Sample for generating a self-signed certificate and private key](scripts/rest/README.md) that can be used for the operator's external REST API.
* [Sample for creating an OKE cluster using Terraform](scripts/terraform/README.md).

## Sample Helm charts

* [Sample Traefik Helm chart](charts/traefik/README.md) for setting up a Traefik load balancer for WebLogic clusters.
* [Sample Voyager Helm chart](charts/voyager/README.md) for setting up a Voyager load balancer for WebLogic clusters.
* [Sample Ingress Helm chart](charts/ingress-per-domain/README.md) for setting up a Kubernetes Ingress for each WebLogic cluster using a Traefik or Voyager load balancer.
* [Sample Apache  Helm chart](charts/apache-webtier/README.md) and [Apache samples using the default or custom configurations](charts/apache-samples/README.md) for setting up a load balancer for WebLogic clusters using the Apache HTTP Server with WebLogic Server Plugins.

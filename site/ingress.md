# Load balancing with an Ingress
Ingresses are one approach provided by Kubernetes to configure load balancers.
Depending on the version of Kubernetes you are using, and your cloud provider, you may need to use Ingresses.
Please refer to [the Ingress documentation](https://kubernetes.io/docs/concepts/services-networking/ingress/)
for more information about Ingresses.  

## WebLogic clusters as backends of an Ingress

In an Ingress object, a list of backends are provided for each target that will be load balanced.  Each backend is typically
[a Kubernetes service](https://kubernetes.io/docs/concepts/services-networking/service/), more specifically, a combination of a `serviceName` and a `servicePort`.

When the WebLogic operator creates a WebLogic domain, it also creates a service for each WebLogic cluster in the domain.
The operator defines the service such that its selector will match all WebLogic server pods within the WebLogic cluster
which are in the "ready" state.

The name of the service created for a WebLogic cluster follows the pattern `<domainUID>-cluster-<clusterName>`.
For example, if the `domainUID` is `domain1` and the cluster name is `cluster-1`, the corresponding service
will be named `domain1-cluster-cluster-1`.

The service name must comply with standard Kubernetes rules for naming of objects and in particular with DNS-1035:
> A DNS-1035 label must consist of lower case alphanumeric characters or '-', start with an alphabetic character, and end with an alphanumeric character (e.g. 'my-name',  or 'abc-123', regex used for validation is '[a-z]([-a-z0-9]*[a-z0-9])?').

To comply with these requirements, if the `domainUID` or the cluster name contains some upper-case characters or underscores, then
in the service name the upper-case characters will be converted to lower-case and underscores will be converted to hyphens.
For example, if the `domainUID` is `myDomain_1` and the cluster name is `myCluster_1`, the corresponding service will be named
`mydomain-1-cluster-mycluster-1`.

The service, `serviceName` and `servicePort`, of a WebLogic cluster will be used in the routing rules defined in the Ingress
object and the load balancer will route traffic to the WebLogic servers within the cluster based on the rules.

**Note**: Most common Ingress controllers, for example Traefik, Voyager, and nginx,
understand that there are zero or more actual pods behind the service, and they actually
build their backend list and route requests to those backends directly, not through the service.  This means that
requests are properly balanced across the pods, according to the load balancing algorithm
in use.  Most Ingress controllers also 
subscribe to updates on the service and adjust their internal backend sets when
additional pods become ready, or pods enter a non-ready state.

## Steps to set up an Ingress load balancer

1. Install the Ingress controller.

   After the Ingress controller is running, it monitors Ingress resources in a given namespace(s) and acts accordingly.

2. Create Ingress resource(s).

   Ingress resources contain routing rules to one or more backends. An Ingress controller is responsible to apply the rules to the underlying load balancer.
   There are two approaches to create the Ingress resource:

   a. Use the Helm chart [ingress-per-domain](../kubernetes/samples/charts/ingress-per-domain).  

   Each Ingress provider supports a number of annotations in Ingress resources. This Helm chart allows you to define the routing rules without dealing with the detailed provider-specific annotations. Currently we support two Ingress providers: Traefik and Voyager.

   b. Create the Ingress resource manually from a YAML file.  

   Manually create an Ingress YAML file and then apply it to the Kubernetes cluster.

## Guide and samples for Traefik and Voyager/HAProxy
Traefik and Voyager/HAProxy are both popular Ingress controllers.
Information about how to install and configure these to load balance WebLogic clusters is provided here:
 - [Traefik guide](../kubernetes/samples/charts/traefik/README.md)
 - [Voyager guide](../kubernetes/samples/charts/voyager/README.md)

Samples are also provided for these two Ingress controllers, showing how to manage multiple WebLogic clusters as the backends, using different routing rules, host-routing and path-routing; and TLS termination:
- [Traefik samples](../kubernetes/samples/charts/traefik/samples)
- [Voyager samples](../kubernetes/samples/charts/voyager/samples)

+++
title = "Ingress"
date = 2019-02-23T20:46:32-05:00
weight = 6
pre = "<b> </b>"
+++

Ingresses are one approach provided by Kubernetes to configure load balancers.
Depending on the version of Kubernetes you are using, and your cloud provider, you may need to use Ingresses.
For more information about Ingresses, see [the Ingress documentation](https://kubernetes.io/docs/concepts/services-networking/ingress/).  

#### WebLogic clusters as backends of an Ingress

In an Ingress object, a list of backends are provided for each target that will be load balanced.  Each backend is typically
[a Kubernetes Service](https://kubernetes.io/docs/concepts/services-networking/service/), more specifically, a combination of a `serviceName` and a `servicePort`.

When the operator creates a WebLogic domain, it also creates a service for each WebLogic cluster in the domain.
The operator defines the service such that its selector will match all WebLogic Server pods within the WebLogic cluster
which are in the "ready" state.

The name of the service created for a WebLogic cluster follows the pattern `<domainUID>-cluster-<clusterName>`.
For example, if the `domainUID` is `domain1` and the cluster name is `cluster-1`, the corresponding service
will be named `domain1-cluster-cluster-1`.

The service name must comply with standard Kubernetes rules for naming of objects and in particular with DNS-1035:
> A DNS-1035 label must consist of lower case alphanumeric characters or '-', start with an alphabetic character, and end with an alphanumeric character (e.g. `my-name`,  or `abc-123`, regex used for validation is `[a-z]([-a-z0-9]*[a-z0-9])?`).

To comply with these requirements, if the `domainUID` or the cluster name contains some upper-case characters or underscores, then
in the service name the upper-case characters will be converted to lower-case and underscores will be converted to hyphens.
For example, if the `domainUID` is `myDomain_1` and the cluster name is `myCluster_1`, the corresponding service will be named
`mydomain-1-cluster-mycluster-1`.

The service, `serviceName` and `servicePort`, of a WebLogic cluster will be used in the routing rules defined in the Ingress
object and the load balancer will route traffic to the WebLogic Servers within the cluster based on the rules.

{{% notice note %}}
Most common ingress controllers, for example Traefik, Voyager, and NGINX,
understand that there are zero or more actual pods behind the service, and they actually
build their backend list and route requests to those backends directly, not through the service.  This means that
requests are properly balanced across the pods, according to the load balancing algorithm
in use.  Most ingress controllers also
subscribe to updates on the service and adjust their internal backend sets when
additional pods become ready, or pods enter a non-ready state.
{{% /notice %}}

#### Steps to set up an ingress load balancer

1. Install the ingress controller.

    After the ingress controller is running, it monitors Ingress resources in a given namespace and acts accordingly.

1. Create Ingress resources.

    Ingress resources contain routing rules to one or more backends. An ingress controller is responsible to apply the rules to the underlying load balancer.
    There are two approaches to create the Ingress resource:

      * Use the Helm chart [ingress-per-domain](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/ingress-per-domain).  

        Each ingress provider supports a number of annotations in Ingress resources. This Helm chart allows you to define the routing rules without dealing with the detailed provider-specific annotations. Currently we support two ingress providers: Traefik and Voyager.

     * Create the Ingress resource manually from a YAML file.  

        Manually create an Ingress YAML file and then apply it to the Kubernetes cluster.

#### Guide and samples for Traefik and Voyager/HAProxy
Traefik and Voyager/HAProxy are both popular ingress controllers.
Information about how to install and configure these to load balance WebLogic clusters is provided here:

 - [Traefik guide](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/traefik/README.md)
 - [Voyager guide](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/voyager/README.md)

 {{% notice note %}}
 For production environments, we recommend NGINX, Voyager, Traefik (2.2.1 or later) ingress controllers, Apache, or the load balancer provided by your cloud provider.
 {{% /notice %}}

Samples are also provided for these two ingress controllers, showing how to manage multiple WebLogic clusters as the backends, using different routing rules, host-routing and path-routing; and TLS termination:

- [Traefik samples](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/traefik/samples)
- [Voyager samples](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/voyager/samples)

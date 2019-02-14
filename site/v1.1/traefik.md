> **WARNING** This documentation is for version 1.1 of the operator.  To view documenation for the current release, [please click here](/site).

# Load balancing with Traefik

If the `loadBalancer` option is set to `traefik` when running the `create-weblogic-domain.sh` script to create a WebLogic domain in Kubernetes, then the Traefik Ingress Controller will be installed into the cluster and an Ingress will be created for each WebLogic cluster in the domain.

More information about the Traefik Ingress controller can be found at: [https://docs.traefik.io/user-guide/kubernetes/](https://docs.traefik.io/user-guide/kubernetes/)

Traefik will expose two `NodePorts` that allow access to the Ingress itself and to the Traefik Web UI.  The ports are controlled by these settings in the domain inputs YAML file:

```
# Load balancer web port
loadBalancerWebPort: 30305

# Load balancer dashboard port
loadBalancerDashboardPort: 30315
```
The operator will automatically update the Ingress to ensure that it contains a list of only those pods that are "ready".  Here is an example of what the Ingress might look like for a WebLogic cluster called `cluster-1`, in a domain called `base_domain`, with `domainUID domain1`, that has three Managed Servers in the "ready" state:

```
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  annotations:
    kubernetes.io/ingress.class: traefik
  labels:
    weblogic.clusterName: cluster-1
    webloigc.clusterNameLC: cluster-1
    weblogic.domainName: base_domain
    weblogic.domainUID: domain1
  name: domain1-cluster-1
  namespace: default
spec:
  rules:
  - http:
      paths:
      - backend:
          serviceName: domain1-managed-server1
          servicePort: 8001
        path: /
      - backend:
          serviceName: domain1-managed-server2
          servicePort: 8001
        path: /
      - backend:
          serviceName: domain1-managed-server3
          servicePort: 8001
        path: /
```

Notice that currently the only supported type of load balancing is using the root path ("`/`").  As such, there is one instance of Traefik for each WebLogic cluster.  

# An Ingress per domain chart
This chart is for deploying an Ingress resource in front of a WebLogic domain cluster. We support two Ingress types: Traeafik and Voyager.

## Prerequisites
- Have Docker and a Kubernetes cluster running and have `kubectl` installed and configured.
- Have Helm installed.
- The corresponding Ingress controller, Traefik or Voyager, is installed in the Kubernetes cluster.
- A WebLogic domain cluster deployed by `weblogic-operator` is running in the Kubernetes cluster.

## Installing the chart

To install the chart with the release name, `my-ingress`, with the given `values.yaml`:
```
# Change directory to the cloned git weblogic-kubernetes-operator repo.
$ cd kubernetes/samples/charts

# Use helm to install the chart.  Use `--namespace` to specify the name of the WebLogic domain's namespace.
$ helm install ingress-per-domain --name my-ingress --namespace my-domain-namespace --values values.yaml
```
The Ingress resource will be created in the same namespace as the WebLogic domain cluster.

Sample `values.yaml` for the Traefik Ingress:
```
type: TRAEFIK

# WLS domain as backend to the load balancer
wlsDomain:
  domainUID: domain1
  clusterName: cluster1
  managedServerPort: 8001

# Traefik specific values
traefik:
  # hostname used by host-routing
  hostname: domain1.org
```

Sample `values.yaml` for the Voyager Ingress:
```
type: VOYAGER

# WLS domain as backend to the load balancer
wlsDomain:
  domainUID: domain1
  clusterName: cluster1  
  managedServerPort: 8001

# Voyager specific values
voyager:
  # web port
  webPort: 30305
  # stats port
  statsPort: 30315
```
## Uninstalling the chart
To uninstall and delete the `my-ingress` deployment:
```
$ helm delete --purge my-ingress
```
## Configuration
The following table lists the configurable parameters of this chart and their default values.

| Parameter | Description | Default |
| --- | --- | --- |
| `type` | Type of Ingress controller. Legal values are `TRAEFIK` or `VOYAGER`. | `TRAEFIK` |
| `wlsDomain.domainUID` | DomainUID of the WLS domain. | `domain1` |
| `wlsDomain.clusterName` | Cluster name in the WLS domain. | `cluster-1` |
| `wlsDomain.managedServerPort` | Port number of the managed servers in the WLS domain cluster. | `8001` |
| `traefik.hostname` | Hostname to route to the WLS domain cluster. | `domain1.org` |
| `voyager.webPort` | Web port to access the Voyager load balancer. | `30305` |
| `voyager.statsPort` | Port to access the Voyager/HAProxy stats page. | `30315` |

**Note:** The input values `domainUID` and `clusterName` will be used to generate the Kubernetes `serviceName` of the WLS cluster with the format `domainUID-cluster-clusterName`.

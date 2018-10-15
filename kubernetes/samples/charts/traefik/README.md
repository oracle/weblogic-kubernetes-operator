# Install and Configure Traefik
This is to:
* Provide detail steps how to install and uninstall Traefik operator with helm chart.
* Provide workable Ingress samples to route workload traffic to multiple WLS domains with Traefik.

## Install Traefik Operator with Helm Chart
Traefik helm chart is in official Charts of Helm: https://github.com/helm/charts/tree/master/stable/traefik. 
The chart is in the default repository for Helm which is located at https://kubernetes-charts.storage.googleapis.com/ and is installed by default.

To install Traefik operator to namespace `traefik` with default settings:
```
helm install --name traefik-operator --namespace traefik stable/traefik
```
Or with a given values.yaml:
```
helm install --name traefik-operator --namespace traefik --values values.yaml stable/traefik
```
With dashboard enabled, you can access the Traefik dashboard with URL `http://${HOSTNAME}:30301` with http Host `traefik.example.com`.
```
curl -H 'host: traefik.example.com' http://${HOSTNAME}:30301/
```

## Optionally Download Traefik Helm Chart
You can download Traefik helm chart and untar it to a local folder if you want.
```
$ helm fetch  stable/traefik --untar
```

## Configure Traefik as Load Balancer for WLS Domains
This chapter we'll demonstrate how to use Traefik to handle traffic to backend WLS domains.

### 1. Install some WLS Domains
Now we need to prepare some backends for Traefik to do load balancer. 

Create two WLS domains: 
- One domain with name 'domain1' under namespace 'default'.
- One domain with name 'domain2' under namespace 'test1'.
- Each domain has a webapp installed with url context 'testwebapp'.

Note: After all WLS domains are running, for now we need to stop WLS operator and remove the per-domain Ingresses created by WLS operator. Otherwise the WLS operator keeps monitor the Ingresses and restore them to the original version if they are changed.

### 2. Install Ingress
#### Install Host-routing Ingress
```
$ kubectl create -f samples/host-routing.yaml
```
Now you can send requests to different WLS domains with the unique entry point of Traefik with different hostname.
```
$ curl --silent -H 'host: domain1.org' http://${HOSTNAME}:30301/testwebapp/
$ curl --silent -H 'host: domain2.org' http://${HOSTNAME}:30301/testwebapp/
```
#### Install Path-routing Ingress
```
$ kubectl create -f samples/path-routing.yaml
```
Now you can send request to different WLS domains with the unique entry point of Traefik with different path.
```
$ curl --silent http://${HOSTNAME}:30301/testwebapp/
$ curl --silent http://${HOSTNAME}:30301/testwebapp1/
```

## Uninstall Traefik Operator
After removing all Ingress resources, uninstall Traefik operator.
```
helm delete --purge traefik-operator
```
## References
Configuration guide on Ingress for Traefik: https://docs.traefik.io/configuration/backends/kubernetes/

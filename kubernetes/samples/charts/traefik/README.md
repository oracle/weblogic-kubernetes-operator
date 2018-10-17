# Install and Configure Traefik
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
With dashboard enabled, you can access the Traefik dashboard with URL `http://${HOSTNAME}:30305` with http Host `traefik.example.com`.
```
curl -H 'host: traefik.example.com' http://${HOSTNAME}:30305/
```

## Optionally Download Traefik Helm Chart
You can download Traefik helm chart and untar it to a local folder if you want.
```
$ helm fetch  stable/traefik --untar
```

## Configure Traefik as Load Balancer for WLS Domains
In this section we'll demonstrate how to use Traefik to handle traffic to backend WLS domains.

### 1. Install WLS Domains
Now we need to prepare some backends for Traefik to do load balancing.

Create two WLS domains: 
- One domain with name 'domain1' under namespace 'default'.
- One domain with name 'domain2' under namespace 'test1'.
- Each domain has a webapp installed with url context 'testwebapp'.

### 2. Install Ingress
#### Install Host-routing Ingress
```
$ kubectl create -f samples/host-routing.yaml
```
Now you can send requests to different WLS domains with the unique entry point of Traefik with different hostname.
```
$ curl --silent -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp/
$ curl --silent -H 'host: domain2.org' http://${HOSTNAME}:30305/testwebapp/
```
#### Install Path-routing Ingress
```
$ kubectl create -f samples/path-routing.yaml
```
Now you can send request to different WLS domains with the unique entry point of Traefik with different path.
```
$ curl --silent http://${HOSTNAME}:30305/testwebapp/
$ curl --silent http://${HOSTNAME}:30305/testwebapp1/
```

## Uninstall Traefik Operator
After removing all Ingress resources, uninstall Traefik operator.
```
helm delete --purge traefik-operator
```
## Install and Uninstall Traefik Operator with setup.sh
Alternatively, you can run the helper script setup.sh under the folder `kubernetes/samples/charts/util` to install and uninstall Traefik.

To install Traefik:
```
$ ./setup.sh create traefik
```
To uninstall Traefik:
```
$ ./setup.sh delete traefik
```
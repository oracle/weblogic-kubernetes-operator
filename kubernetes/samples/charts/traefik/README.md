# Install and configure Traefik
## Install the Traefik operator with a Helm chart
The Traefik Helm chart is located in the official Helm project charts directory at https://github.com/helm/charts/tree/master/stable/traefik.
The chart is in the default repository for Helm at https://kubernetes-charts.storage.googleapis.com/ and is installed by default.

To install the Traefik operator in the `traefik` namespace with default settings:
```
helm install --name traefik-operator --namespace traefik stable/traefik
```
Or, with a given `values.yaml`:
```
helm install --name traefik-operator --namespace traefik --values values.yaml stable/traefik
```
With the dashboard enabled, you can access the Traefik dashboard with the URL `http://${HOSTNAME}:30305` with the HTTP host `traefik.example.com`.
```
curl -H 'host: traefik.example.com' http://${HOSTNAME}:30305/
```

## Optionally download the Traefik Helm chart
If you want, you can download the Traefik Helm chart and untar it into a local folder:
```
$ helm fetch  stable/traefik --untar
```

## Configure Traefik as a load balancer for WLS domains
In this section we'll demonstrate how to use Traefik to handle traffic to backend WLS domains.

### 1. Install WLS domains
Now we need to prepare some domains for Traefik load balancing.

Create two WLS domains:
- One domain with name `domain1` under namespace `default`.
- One domain with name `domain2` under namespace `test1`.
- Each domain has a web application installed with the URL context `testwebapp`.

### 2. Install Ingress
#### Install a host-routing Ingress
```
$ kubectl create -f samples/host-routing.yaml
```
Now you can send requests to different WLS domains with the unique entry point of Traefik with different hostnames.
```
$ curl --silent -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp/
$ curl --silent -H 'host: domain2.org' http://${HOSTNAME}:30305/testwebapp/
```
#### Install a path-routing Ingress
```
$ kubectl create -f samples/path-routing.yaml
```
Now you can send requests to different WLS domains with the unique entry point of Traefik with different paths.
```
$ curl --silent http://${HOSTNAME}:30305/domain1/
$ curl --silent http://${HOSTNAME}:30305/domain2/
```

## Uninstall the Traefik operator
After removing all the Ingress resources, uninstall the Traefik operator:
```
helm delete --purge traefik-operator
```
## Install and uninstall the Traefik operator with setup.sh
Alternatively, you can run the helper script `setup.sh` under the `kubernetes/samples/charts/util` folder to install and uninstall Traefik.

To install Traefik:
```
$ ./setup.sh create traefik
```
To uninstall Traefik:
```
$ ./setup.sh delete traefik
```

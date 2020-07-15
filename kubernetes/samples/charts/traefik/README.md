# Install and configure Traefik

This sample demonstrates how to install the Traefik ingress controller to provide 
load balancing for WebLogic clusters.

## Install the Traefik operator with a Helm chart
This document is based on Traefik version 2.x with helm chart located at https://github.com/containous/traefik-helm-chart.
For more information about Traefik refer to https://docs.traefik.io/

To install the Traefik operator in the `traefik` namespace with default settings:
```
$ helm repo add traefik https://containous.github.io/traefik-helm-chart
$ helm repo update
$ helm install traefik-operator traefik/traefik --namespace traefik
```
Or, with a given `values.yaml`:
```
$ helm install traefik-operator traefik/traefik --namespace traefik --values values.yaml
```
You can access the Traefik dashboard by kubectl port-forward command as follows. 
```
$ kubectl port-forward $(kubectl get pods --selector "app.kubernetes.io/name=traefik" --output=name -n traefik) 9000:9000 -n traefik
$ curl http://localhost:9000/dashboard/
```

## Update the Traefik operator
After the Traefik operator is installed and running, if you want to change some configurations of the operator, use `helm upgrade` to achieve this.
```
$ helm upgrade traefik-operator traefik/traefik --values values.yaml 
```

## Configure Traefik as a load balancer for WLS domains
In this section we'll demonstrate how to use Traefik to handle traffic to backend WLS domains.

### 1. Install WLS domains
Now we need to prepare two domains for Traefik load balancing.

Create two WLS domains:
- One domain with name `domain1` under namespace `weblogic-domain1`.
- One domain with name `domain2` under namespace `weblogic-domain2`.
- Each domain has a web application installed with the URL context `testwebapp`.

### 2. Install the Traefik IngressRoute
#### Install a host-routing IngressRoute
```
$ kubectl create -f samples/host-routing.yaml
```
Now you can send requests to different WLS domains with the unique entry point of Traefik with different hostnames.
```
$ export LB_PORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="web")].nodePort}')
$ curl -H 'host: domain1.org' http://${HOSTNAME}:${LB_PORT}/testwebapp/
$ curl -H 'host: domain2.org' http://${HOSTNAME}:${LB_PORT}/testwebapp/
```
#### Install a path-routing IngressRoute
```
$ kubectl create -f samples/path-routing.yaml
```
Now you can send requests to different WLS domains with the unique entry point of Traefik with different paths.
```
$ export LB_PORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="web")].nodePort}')
$ curl http://${HOSTNAME}:${LB_PORT}/domain1/
$ curl http://${HOSTNAME}:${LB_PORT}/domain2/
```
#### Install a TLS-enabled IngressRoute
This sample demonstrates accessing the two WLS domains using an HTTPS endpoint and the WLS domains are protected by different TLS certificates.

To make this sample work, you need to enable the TLS endpoint in the Traefik operator. If you use the `values.yaml` file in the same folder as this README, the TLS endpoint is already enabled.

First, you need to create two secrets with TLS certificates, one with the common name `domain1.org`, the other with the common name `domain2.org`. We use `openssl` to generate self-signed certificates for demonstration purposes. Note that the TLS secret needs to be in the same namespace as the WLS domain.
```
# create a TLS secret for domain1
$ openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/tls1.key -out /tmp/tls1.crt -subj "/CN=domain1.org"
$ kubectl -n weblogic-domain1 create secret tls domain1-tls-cert --key /tmp/tls1.key --cert /tmp/tls1.crt
# create a TLS secret for domain2
$ openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/tls2.key -out /tmp/tls2.crt -subj "/CN=domain2.org"
$ kubectl -n weblogic-domain2 create secret tls domain2-tls-cert --key /tmp/tls2.key --cert /tmp/tls2.crt

# deploy the TLS IngressRoute.
$ kubectl create -f samples/tls.yaml
```
Now you can access application on the WLS domain with hostname in HTTP header.
The loadbalancer secure port can be obtained dynamically form traefik-operator service on traefik namespace. 
```
LB_PORT=`kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="websecure")].nodePort}'`
$ curl -k -H 'host: domain1.org' https://${HOSTNAME}:${LB_PORT}/testwebapp/
```

## Uninstall the Traefik operator
After removing all the Ingress resources, uninstall the Traefik operator:
```
$ helm uninstall traefik-operator --namespace traefik --keep-history
```
## Install and uninstall the Traefik operator with setup.sh
Alternatively, you can run the helper script `setup.sh`, under the `kubernetes/samples/charts/util` folder, to install and uninstall Traefik.

To install Traefik:
```
$ ./setup.sh create traefik
```
To uninstall Traefik:
```
$ ./setup.sh delete traefik
```

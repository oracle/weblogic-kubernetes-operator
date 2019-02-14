# Install and configure Traefik

This sample demonstrates how to install the Traefik ingress controller to provide 
load balancing for WebLogic clusters.

## Install the Traefik operator with a Helm chart
The Traefik Helm chart is located in the official Helm project `charts` directory at https://github.com/helm/charts/tree/master/stable/traefik.
The chart is in the default repository for Helm.

To install the Traefik operator in the `traefik` namespace with default settings:
```
$ helm install --name traefik-operator --namespace traefik stable/traefik
```
Or, with a given `values.yaml`:
```
$ helm install --name traefik-operator --namespace traefik --values values.yaml stable/traefik
```
With the dashboard enabled, you can access the Traefik dashboard with the URL `http://${HOSTNAME}:30305`, with the HTTP host `traefik.example.com`.
```
$ curl -H 'host: traefik.example.com' http://${HOSTNAME}:30305/
```

## Optionally, download the Traefik Helm chart
If you want, you can download the Traefik Helm chart and untar it into a local folder:
```
$ helm fetch  stable/traefik --untar
```

## Update the Traefik operator
After the Traefik operator is installed and running, if you want to change some configurations of the operator, use `helm upgrade` to achieve this.
```
$ helm upgrade traefik-operator stable/traefik --values values.yaml 
```

## Configure Traefik as a load balancer for WLS domains
In this section we'll demonstrate how to use Traefik to handle traffic to backend WLS domains.

### 1. Install WLS domains
Now we need to prepare some domains for Traefik load balancing.

Create two WLS domains:
- One domain with name `domain1` under namespace `default`.
- One domain with name `domain2` under namespace `test1`.
- Each domain has a web application installed with the URL context `testwebapp`.

### 2. Install the Traefik Ingress
#### Install a host-routing Ingress
```
$ kubectl create -f samples/host-routing.yaml
```
Now you can send requests to different WLS domains with the unique entry point of Traefik with different hostnames.
```
$ curl -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp/
$ curl -H 'host: domain2.org' http://${HOSTNAME}:30305/testwebapp/
```
#### Install a path-routing Ingress
```
$ kubectl create -f samples/path-routing.yaml
```
Now you can send requests to different WLS domains with the unique entry point of Traefik with different paths.
```
$ curl http://${HOSTNAME}:30305/domain1/
$ curl http://${HOSTNAME}:30305/domain2/
```
#### Install a TLS-enabled Ingress
This sample demonstrates accessing the two WLS domains using an HTTPS endpoint and the WLS domains are protected by different TLS certificates.

To make this sample work, you need to enable the TLS endpoint in the Traefik operator. If you use the `values.yaml` file in the same folder as this README, the TLS endpoint is already enabled.

First, you need to create two secrets with TLS certificates, one with the common name `domain1.org`, the other with the common name `domain2.org`. We use `openssl` to generate self-signed certificates for demonstration purposes. Note that the TLS secret needs to be in the same namespace as the WLS domain.
```
# create a TLS secret for domain1
$ openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/tls1.key -out /tmp/tls1.crt -subj "/CN=domain1.org"
$ kubectl create secret tls domain1-tls-cert --key /tmp/tls1.key --cert /tmp/tls1.crt

# create a TLS secret for domain2
$ openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/tls2.key -out /tmp/tls2.crt -subj "/CN=domain2.org"
$ kubectl -n test1 create secret tls domain2-tls-cert --key /tmp/tls2.key --cert /tmp/tls2.crt
```
Then deploy the TLS Ingress.
```
$ kubectl create -f samples/tls.yaml
```
Now you can access the two WLS domains with different hostnames using the HTTPS endpoint.
```
$ curl -k -H 'host: domain1.org' https://${HOSTNAME}:30443/testwebapp/
$ curl -k -H 'host: domain2.org' https://${HOSTNAME}:30443/testwebapp/
```

## Uninstall the Traefik operator
After removing all the Ingress resources, uninstall the Traefik operator:
```
$ helm delete --purge traefik-operator
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

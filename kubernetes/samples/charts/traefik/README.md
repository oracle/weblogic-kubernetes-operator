# Install and configure Traefik

This sample demonstrates how to install the Traefik ingress controller to provide 
load balancing for WebLogic clusters.

## Install the Traefik operator with a Helm chart
This document is based on Traefik version 2.x with the Helm chart at [Traefik Helm Repository](https://github.com/containous/traefik-helm-chart).
For more information about Traefik, see the [Traefik Official Site](https://traefik.io/).

To install the Traefik operator in the `traefik` namespace with the default settings:
```shell
$ helm repo add traefik https://containous.github.io/traefik-helm-chart
$ helm repo update
$ kubectl create namespace traefik
$ helm install traefik-operator traefik/traefik --namespace traefik
```
You can also install the Traefik operator with a custom `values.yaml` file. For more detailed information, see the [Traefik GitHub Project](https://github.com/containous/traefik-helm-chart/blob/master/traefik/values.yaml).
```shell
$ helm install traefik-operator traefik/traefik --namespace traefik --values values.yaml
```

## Configure Traefik as a load balancer for WebLogic domains
This section describes how to use Traefik to handle traffic to backend WebLogic domains.

### 1. Install WebLogic domains
First, we need to prepare two domains for Traefik load balancing.

Create two WebLogic domains:
- One domain with `domain1` as the domain UID and namespace `weblogic-domain1`.
- One domain with `domain2` as the domain UID and namespace `weblogic-domain2`.
- Each domain has a web application installed with the URL context `testwebapp`.
- Each domain has a WebLogic cluster `cluster-1` where each Managed Server listens on port `8001`.

### 2. Web request routing 
The following sections describe how to route an application web request to the WebLogic domain through a Traefik frontend.

#### Host-based routing 
This sample demonstrates how to access an application on two WebLogic domains using host-based routing. Install a host-based routing Traefik [IngressRoute](https://docs.traefik.io/routing/providers/kubernetes-crd/#kind-ingressroute).
```shell
$ kubectl create -f samples/host-routing.yaml
ingressroute.traefik.containo.us/traefik-hostrouting-1 created
ingressroute.traefik.containo.us/traefik-hostrouting-2 created
```
Now you can send requests to different WebLogic domains with the unique Traefik entry point of different host names as defined in the route section of the `host-routing.yaml` file.
```shell
# Get the ingress controller web port
$ export LB_PORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="web")].nodePort}')
$ curl -H 'host: domain1.org' http://${HOSTNAME}:${LB_PORT}/testwebapp/
$ curl -H 'host: domain2.org' http://${HOSTNAME}:${LB_PORT}/testwebapp/
```

#### Path-based routing  
This sample demonstrates how to access an application on two WebLogic domains using path-based routing. Install a path-based routing Traefik [IngressRoute](https://docs.traefik.io/routing/providers/kubernetes-crd/#kind-ingressroute) and [Middleware](https://docs.traefik.io/middlewares/overview/).

```shell
$ kubectl create -f samples/path-routing.yaml
ingressroute.traefik.containo.us/traefik-pathrouting-1 created
middleware.traefik.containo.us/middleware-domain1 created
ingressroute.traefik.containo.us/traefik-pathrouting-2 created
middleware.traefik.containo.us/middleware-domain2 created
```
Now you can send requests to different WebLogic domains with the unique Traefik entry point of different paths, as defined in the route section of the `path-routing.yaml` file.
```shell
# Get the ingress controller web port
$ export LB_PORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="web")].nodePort}')
$ curl http://${HOSTNAME}:${LB_PORT}/domain1/
$ curl http://${HOSTNAME}:${LB_PORT}/domain2/
```
#### Host-based secured routing 
This sample demonstrates how to access an application on two WebLogic domains using an HTTPS endpoint. Install a TLS-enabled Traefik [IngressRoute](https://docs.traefik.io/routing/providers/kubernetes-crd/#kind-ingressroute).

First, you need to create two secrets with TLS certificates, one with the common name `domain1.org`, the other with the common name `domain2.org`. We use `openssl` to generate self-signed certificates for demonstration purposes. Note that the TLS secret needs to be in the same namespace as the WebLogic domain.
```shell
# create a TLS secret for domain1
$ openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/tls1.key -out /tmp/tls1.crt -subj "/CN=domain1.org"
$ kubectl -n weblogic-domain1 create secret tls domain1-tls-cert --key /tmp/tls1.key --cert /tmp/tls1.crt
# create a TLS secret for domain2
$ openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/tls2.key -out /tmp/tls2.crt -subj "/CN=domain2.org"
$ kubectl -n weblogic-domain2 create secret tls domain2-tls-cert --key /tmp/tls2.key --cert /tmp/tls2.crt

# Deploy a TLS IngressRoute.
$ kubectl create -f samples/tls.yaml
ingressroute.traefik.containo.us/traefik-tls-1 created
ingressroute.traefik.containo.us/traefik-tls-2 created
```
Now you can access the application on the WebLogic domain with the host name in the HTTP header.
The ingress controller secure port can be obtained dynamically from the `traefik-operator` service in the `traefik` namespace.
```shell
# Get the ingress controller secure web port
TLS_PORT=`kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="websecure")].nodePort}'`
$ curl -k -H 'host: domain1.org' https://${HOSTNAME}:${TLS_PORT}/testwebapp/
```

## SSL termination at ingress controller
This sample demonstrates how to terminate SSL traffic at the ingress controller to access the WebLogic Server Administration Console through the SSL port. 

### 1. Enable "WebLogic Plugin Enabled" on the WebLogic domain level

If you are using WDT to configure the WebLogic domain, you need to add the following resource section at the domain level to the model YAML file.
```yaml
resources:
     WebAppContainer:
         WeblogicPluginEnabled: true
```
If you are using a WLST script to configure the domain, then the following modifications are needed to the respective PY script.
```javascript
# Configure the Administration Server
cd('/Servers/AdminServer')
set('WeblogicPluginEnabled',true)
...
cd('/Clusters/%s' % cluster_name)
set('WeblogicPluginEnabled',true)
```
### 2. Update the ingress resource with customRequestHeaders value
Replace the string `weblogic-domain` with namespace of the WebLogic domain, the string `domain1` with domain UID and the string `adminserver` with name of the Administration Server in the WebLogic domain.  


**NOTE**: If you also have HTTP requests coming into an ingress, make sure that you remove any incoming `WL-Proxy-SSL` header. This protects you from a malicious user sending in a request to appear to WebLogic as secure when it isn't. Add the following `customRequestHeaders` in the Traefik ingress configuration to block `WL-Proxy` headers coming from the client. In the following example, the ingress resource will eliminate the client headers `WL-Proxy-Client-IP` and `WL-Proxy-SSL`.

```yaml
apiVersion: traefik.containo.us/v1alpha1
kind: IngressRoute
metadata:
  annotations:
    kubernetes.io/ingress.class: traefik
  name: traefik-console-tls
  namespace: weblogic-domain
spec:
  entryPoints: 
   - websecure
  routes:
  - kind: Rule
    match: PathPrefix(`/console`)
    middlewares:
    - name: tls-console-middleware
      namespace: weblogic-domain
    services:
    - kind: Service
      name: domain1-adminserver
      namespace: weblogic-domain
      port: 7001
  tls:
     secretName: domain1-tls-cert
---
apiVersion: traefik.containo.us/v1alpha1
kind: Middleware
metadata:
  name: tls-console-middleware
  namespace: weblogic-domain
spec:
  headers:
    customRequestHeaders:
      X-Custom-Request-Header: ""
      X-Forwarded-For: "" 
      WL-Proxy-Client-IP: ""
      WL-Proxy-SSL: ""
      WL-Proxy-SSL: "true"
    sslRedirect: true
```
### 3. Create ingress resource
Save the above configuration as `traefik-tls-console.yaml`.
```shell
$ kubectl create -f traefik-tls-console.yaml
```
### 4. Access the WebLogic Server Administration Console using the HTTPS port
Get the SSL port from the Kubernetes service. 
```shell
# Get the ingress controller secure web port
$ SSLPORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="websecure")].nodePort}')
```
In a web browser, type `https://${HOSTNAME}:${SSLPORT}/console` in the address bar to access the WebLogic Server Administration Console.

## Uninstall the Traefik operator
After removing all the ingress resources, uninstall the Traefik operator:
```shell
$ helm uninstall traefik-operator --namespace traefik --keep-history
```
## Install and uninstall the Traefik operator with setupLoadBalancer.sh
Alternatively, you can run the helper script `setupLoadBalancer.sh`, under the `kubernetes/samples/charts/util` folder, to install and uninstall Traefik.

To install Traefik:
```shell
$ ./setupLoadBalancer.sh create traefik [traefik-version]
```
To uninstall Traefik:
```shell
$ ./setupLoadBalancer.sh delete traefik
```

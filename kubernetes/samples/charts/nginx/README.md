# Install and configure NGINX

## A step-by-step guide to install the NGINX operator
See the official installation document at:
- https://kubernetes.github.io/ingress-nginx/deploy/#using-helm

As a *demonstration*, the following are steps to install the NGINX operator using Helm 3 on a Linux OS.

### 1. Add the ingress-nginx chart repository
```shell
$ helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
$ helm repo update
```
Verify that the chart repository has been added.

```shell
$ helm search repo ingress-nginx
NAME               CHART VERSION APP VERSION	DESCRIPTION
ingress-nginx/ingress-nginx	2.12.0       	0.34.1     	Ingress controller for Kubernetes using NGINX a...
```
> **NOTE**: After updating the Helm repository, the NGINX version listed may be newer than the one appearing here. Please check with the NGINX site for the latest supported versions.

### 2. Install the NGINX ingress controller

> **NOTE**: The NGINX version used for the install should match the version found with `helm search`.

```shell
$ kubectl create namespace nginx
$ helm install nginx-operator ingress-nginx/ingress-nginx --namespace nginx
```

Wait until the NGINX ingress controller is running.
```shell
$ kubectl get all --namespace nginx 
NAME                                                           READY   STATUS    RESTARTS   AGE
pod/nginx-operator-ingress-nginx-controller-84fbd64787-v4p4c   1/1     Running   0          11m
NAME                                                        TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)                      AGE
service/nginx-operator-ingress-nginx-controller             LoadBalancer   10.107.159.96   <pending>     80:31470/TCP,443:32465/TCP   11m
service/nginx-operator-ingress-nginx-controller-admission   ClusterIP      10.109.12.133   <none>        443/TCP                      11m
NAME                                                      READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/nginx-operator-ingress-nginx-controller   1/1     1            1           11m
NAME                                                                 DESIRED   CURRENT   READY   AGE
replicaset.apps/nginx-operator-ingress-nginx-controller-84fbd64787   1         1         1       11m
$ POD_NAME=$(kubectl get pods -n nginx -l app.kubernetes.io/name=ingress-nginx -o jsonpath='{.items[0].metadata.name}')
$ kubectl exec -it $POD_NAME -n nginx -- /nginx-ingress-controller --version
```
> **NOTE**: All the generated Kubernetes resources of the NGINX operator have names controlled by the NGINX Helm chart. In our case, we use `releaseName` of `nginx-operator`.

## Configure NGINX as a load balancer for WebLogic domains
We'll demonstrate how to use NGINX to handle traffic to backend WebLogic domains.

### 1. Install WebLogic domains
Now we need to prepare two domains for NGINX load balancing.

Create two WebLogic domains:
- One domain with `domain1` as the domain UID and namespace `weblogic-domain1`.
- One domain with `domain2` as the domain UID and namespace `weblogic-domain2`.
- Each domain has a web application installed with the URL context `testwebapp`.
- Each domain has a WebLogic cluster `cluster-1` where each Managed Server listens on port `8001`.

### 2. Web request routing
The following sections describe how to route an application web request to the WebLogic domain through a NGINX ingress controller.

#### Host-based routing 
This sample demonstrates how to access an application on two WebLogic domains using host-based routing. Install a host-based routing NGINX ingress.
```shell
$ kubectl create -f samples/host-routing.yaml
ingress.networking.k8s.io/domain1-ingress-host created
ingress.networking.k8s.io/domain2-ingress-host created
```
Now you can send requests to different WebLogic domains with the unique NGINX entry point of different host names as defined in the route section of the `host-routing.yaml` file.
```shell
# Get the ingress controller web port
$ export LB_PORT=$(kubectl -n nginx get service nginx-operator-ingress-nginx-controller -o jsonpath='{.spec.ports[?(@.name=="http")].nodePort}')
$ curl -H 'host: domain1.org' http://${HOSTNAME}:${LB_PORT}/testwebapp/
$ curl -H 'host: domain2.org' http://${HOSTNAME}:${LB_PORT}/testwebapp/
```

#### Path-based routing 
This sample demonstrates how to access an application on two WebLogic domains using path-based routing. Install a path-based routing ingress controller.
```shell
$ kubectl create -f samples/path-routing.yaml
ingress.extensions/domain1-ingress-path created
ingress.extensions/domain2-ingress-path created
```
Now you can send requests to different WebLogic domains with the unique NGINX entry point of different paths, as defined in the route section of the `path-routing.yaml` file.

```shell
# Get the ingress controller web port
$ export LB_PORT=$(kubectl -n nginx get service nginx-operator-ingress-nginx-controller -o jsonpath='{.spec.ports[?(@.name=="http")].nodePort}')
$ curl http://${HOSTNAME}:${LB_PORT}/domain1/testwebapp/
$ curl http://${HOSTNAME}:${LB_PORT}/domain2/testwebapp/
```

#### Host-based secured routing
This sample demonstrates how to access an application on two WebLogic domains using an HTTPS endpoint. Install a TLS-enabled ingress controller.

First, you need to create two secrets with TLS certificates, one with the common name `domain1.org`, the other with the common name `domain2.org`. We use `openssl` to generate self-signed certificates for demonstration purposes. Note that the TLS secret needs to be in the same namespace as the WebLogic domain.
```shell
# create a TLS secret for domain1
$ openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/tls1.key -out /tmp/tls1.crt -subj "/CN=domain1.org"
$ kubectl -n weblogic-domain1 create secret tls domain1-tls-cert --key /tmp/tls1.key --cert /tmp/tls1.crt

# create a TLS secret for domain2
$ openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/tls2.key -out /tmp/tls2.crt -subj "/CN=domain2.org"
$ kubectl -n weblogic-domain2 create secret tls domain2-tls-cert --key /tmp/tls2.key --cert /tmp/tls2.crt
# Deploy the TLS ingress controller.
$ kubectl create -f samples/tls.yaml
ingress.networking.k8s.io/domain1-ingress-tls created
ingress.networking.k8s.io/domain2-ingress-tls created
```
Now you can access the application on the WebLogic domain with the host name in the HTTP header. The ingress controller secure port can be obtained dynamically from the `nginx-operator` service in the `nginx` namespace.

```shell
# Get the ingress controller secure web port
$ export TLS_PORT=$(kubectl -n nginx get service nginx-operator-ingress-nginx-controller -o jsonpath='{.spec.ports[?(@.name=="https")].nodePort}')
$ curl -k -H 'host: domain1.org' https://${HOSTNAME}:${TLS_PORT}/testwebapp/
$ curl -k -H 'host: domain2.org' https://${HOSTNAME}:${TLS_PORT}/testwebapp/
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
If you are using a WLST script to configure the domain, then the following modifications are needed to the respective python script.
```javascript
# Configure the Administration Server
cd('/Servers/AdminServer')
set('WeblogicPluginEnabled',true)
...
cd('/Clusters/%s' % cluster_name)
set('WeblogicPluginEnabled',true)
```
### 2. Create NGINX ingress resource with custom annotation values
Save the below configuration as `nginx-tls-console.yaml` and replace the string `weblogic-domain` with the namespace of the WebLogic domain, the string `domain1` with the domain UID, and the string `adminserver` with the name of the Administration Server in the WebLogic domain.

**NOTE**:If you also have HTTP requests coming into an ingress, make sure that you remove any incoming `WL-Proxy-SSL` header. This protects you from a malicious user sending in a request to appear to WebLogic as secure when it isn't. Add the following annotations in the NGINX ingress configuration to block `WL-Proxy` headers coming from the client. In the following example, the ingress resource will eliminate the client headers `WL-Proxy-Client-IP` and `WL-Proxy-SSL`.

```yaml
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: nginx-console-tls
  namespace: weblogic-domain
  annotations:
    kubernetes.io/ingress.class: nginx
    nginx.ingress.kubernetes.io/configuration-snippet: |
      more_clear_input_headers "WL-Proxy-Client-IP" "WL-Proxy-SSL";
      more_set_input_headers "X-Forwarded-Proto: https";
      more_set_input_headers "WL-Proxy-SSL: true";
    nginx.ingress.kubernetes.io/ingress.allow-http: "false"
spec:
  tls:
  - hosts:
    secretName: domain-tls-cert
  rules:
  - host: 
    http:
      paths:
      - path: /console
        backend:
          serviceName: domain1-adminserver
          servicePort: 7001
```
### 3. Deploy the ingress resource
Deploy the ingress resource using `kubectl`.
```shell
 kubectl create -f nginx-tls-console.yaml
```
### 4. Access the WebLogic Server Administration Console using the HTTPS port
Get the SSL port from the Kubernetes service. 
```shell
# Get the ingress controller secure web port
SSLPORT=$(kubectl -n nginx get service nginx-operator-ingress-nginx-controller -o jsonpath='{.spec.ports[?(@.name=="https")].nodePort}')
```
In a web browser address bar type `https://${HOSTNAME}:${SSLPORT}/console` to access the WebLogic Server Administration Console.

## Uninstall the NGINX operator
After removing all the NGINX ingress resources, uninstall the NGINX operator.

```shell
$ helm uninstall nginx-operator --namespace nginx
```

## Install and uninstall the NGINX operator with setupLoadBalancer.sh
Alternatively, you can run the helper script `setupLoadBalancer.sh` under the `kubernetes/samples/charts/util` folder, to install and uninstall NGINX.

To install NGINX:
```shell
$ ./setupLoadBalancer.sh create nginx [nginx-version]
```
To uninstall NGINX:
```shell
$ ./setupLoadBalancer.sh delete nginx
```

# Install and configure Voyager

## A step-by-step guide to install the Voyager operator
AppsCode has provided a Helm chart and instructions to install Voyager. See the official installation document at:
- https://appscode.com/products/voyager/latest/setup/install/

Also check the Kubernetes version support matrix based on your Kubernetes installation at:
- https://github.com/appscode/voyager#supported-versions

As a *demonstration*, the following are steps to install the Voyager operator by using Helm 3 on a Linux OS.

### 1. Add the AppsCode chart repository
```shell
$ helm repo add appscode https://charts.appscode.com/stable/
$ helm repo update
```
Verify that the chart repository has been added.

```shell
$ helm search repo appscode/voyager
NAME               CHART VERSION APP VERSION	DESCRIPTION
appscode/voyager   v12.0.0       v12.0.0    	Voyager by AppsCode - Secure HAProxy In.gress Co...
```
> **NOTE**: After updating the Helm repository, the Voyager version listed may be newer that the one appearing here. Please check with the Voyager site for the latest supported versions.

### 2. Install the Voyager operator

> **NOTE**: The Voyager version used for the install should match the version found with `helm search`.

```shell
$ kubectl create ns voyager
$ helm install voyager-operator appscode/voyager --version 12.0.0 \
  --namespace voyager \
  --set cloudProvider=baremetal \
  --set apiserver.healthcheck.enabled=false \
  --set apiserver.enableValidatingWebhook=false
```

Wait until the Voyager operator and Kubernetes objects are ready.
```shell
$ kubectl -n voyager get pod
NAME                         READY   STATUS    RESTARTS   AGE
pod/voyager-operator-9bs5z   1/1     Running   0          46m
$ kubectl -n voyager get svc
NAME               TYPE      CLUSTER-IP     EXTERNAL-IP   PORT(S)
voyager-operator   ClusterIP 10.105.254.144 <none>     443/TCP,56791/TCP
$ kubectl -n voyager get deployment
NAME               READY   UP-TO-DATE   AVAILABLE   AGE
voyager-operator   1/1     1            1           9m16s
$ kubectl -n voyager get replicaset
NAME                        DESIRED   CURRENT   READY   AGE
voyager-operator-799fff9f   1         1         1       10m
$ kubectl get crd/ingresses.voyager.appscode.com -n voyager
NAME                             CREATED AT
ingresses.voyager.appscode.com   2020-11-19T23:45:34Z
```
> **NOTE**: All the generated Kubernetes resources of the Voyager operator have names controlled by the Voyager Helm chart. In our case, we use `releaseName` of `voyager-operator`.

## Update the Voyager operator
After the Voyager operator is installed and running, to change some configuration of the Voyager operator, use `helm upgrade` to achieve this.
```shell
$ helm upgrade voyager-operator appscode/voyager [flags]
```

## Configure Voyager as a load balancer for WebLogic domains
We'll demonstrate how to use Voyager to handle traffic to backend WebLogic domains.

### 1. Install WebLogic domains
Now we need to prepare some domains for Voyager load balancing.

Create two WebLogic domains:
- One domain with name `domain1` under namespace `weblogic-domain1`.
- One domain with name `domain2` under namespace `weblogic-domain2`.
Each domain has a web application installed with the URL context `testwebapp`.

### 2. Install the Voyager ingress
#### Install a host-routing ingress
```shell
$ kubectl create -f samples/host-routing.yaml
```
Now you can send requests to different WebLogic domains with the unique entry point of Voyager with different host names.
```shell
$ curl -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp/
$ curl -H 'host: domain2.org' http://${HOSTNAME}:30305/testwebapp/
```
To see the Voyager host-routing stats web page, access the URL `http://${HOSTNAME}:30315` in your web browser.

> **NOTE**: When using a web browser with a `NodePort` for the Voyager load balancer, the `Host` header is set to the host name including the `NodePort` value.
> For example, if you type into the address bar `http://app.myhost.com:30305/testwebapp` then the host name in `ingress` YAML file would be `- host: app.myhost.com:30305`

#### Install a path-routing ingress
```shell
$ kubectl create -f samples/path-routing.yaml
```
Now you can send requests to different WebLogic domains with the unique entry point of Voyager with different paths.
```shell
$ curl http://${HOSTNAME}:30307/domain1/
$ curl http://${HOSTNAME}:30307/domain2/
```
To see the Voyager path-routing stats web page, access URL `http://${HOSTNAME}:30317` in your web browser.

#### Install a TLS-enabled ingress
This sample demonstrates accessing the two WebLogic domains using an HTTPS endpoint and the WebLogic domains are protected by different TLS certificates.

First, you need to create two secrets with TLS certificates, one with the common name `domain1.org`, the other with the common name `domain2.org`. We use `openssl` to generate self-signed certificates for demonstration purposes. Note that the TLS secret needs to be in the same namespace as the WebLogic domain.
```shell
# create a TLS secret for domain1
$ openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/tls1.key -out /tmp/tls1.crt -subj "/CN=domain1.org"
$ kubectl -n weblogic-domain1 create secret tls domain1-tls-cert --key /tmp/tls1.key --cert /tmp/tls1.crt

# create a TLS secret for domain2
$ openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/tls2.key -out /tmp/tls2.crt -subj "/CN=domain2.org"
$ kubectl -n weblogic-domain1 create secret tls domain2-tls-cert --key /tmp/tls2.key --cert /tmp/tls2.crt
```
Then deploy the TLS ingress.
```shell
$ kubectl create -f samples/tls.yaml
```
Now you can access the two WebLogic domains with different host names using the HTTPS endpoint.
```shell
$ curl -k -H 'host: domain1.org' https://${HOSTNAME}:30305/testwebapp/
$ curl -k -H 'host: domain2.org' https://${HOSTNAME}:30307/testwebapp/
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
### 2. Update the frontendRules section ingress resource
Replace the string `weblogic-domain` with namespace of the WebLogic domain, the string `domain1` with domain UID and the string `adminserver` with name of the Administration Server in the WebLogic domain.

**NOTE**: If you also have HTTP requests coming into an ingress, make sure that you remove any incoming `WL-Proxy-SSL` header. This protects you from a malicious user sending in a request to appear to WebLogic as secure when it isn't. Add the following rules in the Voyager ingress configuration to block `WL-Proxy` headers coming from the client. In the following example, the ingress resource will eliminate the client headers `WL-Proxy-Client-IP` and `WL-Proxy-SSL`.
```yaml
apiVersion: voyager.appscode.com/v1beta1
kind: Ingress
metadata:
  name: voyager-console-ssl
  namespace: weblogic-domain
  annotations:
    ingress.appscode.com/type: 'NodePort'
    ingress.appscode.com/stats: 'true'
    ingress.appscode.com/affinity: 'cookie'
spec:
  tls:
  - secretName: domain1-tls-cert
    hosts: 
    - '*'
  frontendRules:
  - port: 443
    rules:
    - http-request del-header WL-Proxy-Client-IP
    - http-request del-header WL-Proxy-SSL
    - http-request set-header WL-Proxy-SSL true
  rules:
  - host: '*'
    http:
      nodePort: 30443
      paths:
      - backend:
          serviceName: domain1-adminserver
          servicePort: '7001'
```
### 3. Create ingress resource
Save the above configuration as `voyager-tls-console.yaml`.
```shell
$ kubectl create -f voyager-tls-console.yaml
```
### 4. Access the WebLogic Server Administration Console using the HTTPS port
In a web browser type `https://${HOSTNAME}:30443/console` in the address bar to access the WebLogic Server Administration Console. 

## Uninstall the Voyager operator
After removing all the Voyager ingress resources, uninstall the Voyager operator:

```shell
$ helm uninstall voyager-operator --namespace voyager
```

## Install and uninstall the Voyager operator with setupLoadBalancer.sh
Alternatively, you can run the helper script `setupLoadBalancer.sh` under the `kubernetes/samples/charts/util` folder, to install and uninstall Voyager.

To install Voyager:
```shell
$ ./ setupLoadBalancer.sh create voyager [voyager-version]
```
To uninstall Voyager:
```shell
$ ./ setupLoadBalancer.sh delete voyager
```

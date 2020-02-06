# Install and configure Voyager

## A step-by-step guide to install the Voyager operator
AppsCode has provided a Helm chart and instructions to install Voyager. See the official installation document at:
- https://appscode.com/products/voyager/latest/setup/install/

Also check the Kubernetes version support matrix based on your Kubernetes installation at:
- https://github.com/appscode/voyager#supported-versions

As a *demonstration*, the following are steps to install the Voyager operator by using Helm 2 on a Linux OS.

### 1. Add the AppsCode chart repository
```
$ helm repo add appscode https://charts.appscode.com/stable/
$ helm repo update
```
Verify that the chart repository has been added.

Using Helm 3:
```
$ helm search repo appscode/voyager
```
Using Helm 2:
```
$ helm search appscode/voyager
NAME                    CHART VERSION   APP VERSION     DESCRIPTION
appscode/voyager        v10.0.0    v10.0.0    Voyager by AppsCode - Secure HAProxy Ingress Controller f...
```
> **NOTE**: After updating the helm repository, the Voyager version listed maybe newer that the one appearing here, please check with the Voyager site for the lastest supported versions.

### 2. Install the Voyager operator

> **NOTE**: The Voyager version used for the install should match the version found with `helm search`.

Using Helm 3:
```
$ kubectl create ns voyager
$ helm install voyager-operator appscode/voyager --version 10.0.0 \
  --namespace voyager \
  --set cloudProvider=baremetal \
  --set apiserver.enableValidatingWebhook=false
```
Using Helm 2:
```
$ kubectl create ns voyager
$ helm install appscode/voyager --name voyager-operator --version 10.0.0 \
  --namespace voyager \
  --set cloudProvider=baremetal \
  --set apiserver.enableValidatingWebhook=false
```

Wait until the Voyager Operator is running.
```
$ kubectl -n voyager get all
NAME                                    READY   STATUS    RESTARTS   AGE
pod/voyager-operator-5686bc6556-9bs5z   1/1     Running   0          46m

NAME                       TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)             AGE
service/voyager-operator   ClusterIP   10.105.254.144   <none>        443/TCP,56791/TCP   46m

NAME                               READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/voyager-operator   1/1     1            1           46m

NAME                                          DESIRED   CURRENT   READY   AGE
replicaset.apps/voyager-operator-5686bc6556   1         1         1       46m
```
> **NOTE**: All the generated Kubernetes resources of the Voyager operator have names controlled by the Voyager Helm chart. In our case, we use `releaseName` of `voyager-operator`.

## Update the Voyager operator
After the Voyager operator is installed and running, to change some configuration of the Voyager operator, use `helm upgrade` to achieve this.
```
$ helm upgrade voyager-operator appscode/voyager [flags]
```

## Configure Voyager as a load balancer for WebLogic domains
We'll demonstrate how to use Voyager to handle traffic to backend WebLogic domains.

### 1. Install WebLogic domains
Now we need to prepare some domains for Voyager load balancing.

Create two WebLogic domains:
- One domain with name `domain1` under namespace `default`.
- One domain with name `domain2` under namespace `test1`.
- Each domain has a web application installed with the URL context `testwebapp`.


### 2. Install the Voyager Ingress
#### Install a host-routing Ingress
```
$ kubectl create -f samples/host-routing.yaml
```
Now you can send requests to different WebLogic domains with the unique entry point of Voyager with different host names.
```
$ curl -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp/
$ curl -H 'host: domain2.org' http://${HOSTNAME}:30305/testwebapp/
```
To see the Voyager host-routing stats web page, access the URL `http://${HOSTNAME}:30315` in your web browser.

> **NOTE**: When using a web browser with a `NodePort` for the Voyager load balancer, the `Host` header is set to the host name including the `NodePort` value.
> For example, if you type into the address bar `http://app.myhost.com:30305/testwebapp` then the host name in `Ingress` YAML file would be `- host: app.myhost.com:30305`


#### Install a path-routing Ingress
```
$ kubectl create -f samples/path-routing.yaml
```
Now you can send requests to different WebLogic domains with the unique entry point of Voyager with different paths.
```
$ curl http://${HOSTNAME}:30307/domain1/
$ curl http://${HOSTNAME}:30307/domain2/
```
To see the Voyager path-routing stats web page, access URL `http://${HOSTNAME}:30317` in your web browser.

#### Install a TLS-enabled Ingress
This sample demonstrates accessing the two WebLogic domains using an HTTPS endpoint and the WebLogic domains are protected by different TLS certificates.

First, you need to create two secrets with TLS certificates, one with the common name `domain1.org`, the other with the common name `domain2.org`. We use `openssl` to generate self-signed certificates for demonstration purposes. Note that the TLS secret needs to be in the same namespace as the WebLogic domain.
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
Now you can access the two WebLogic domains with different host names using the HTTPS endpoint.
```
$ curl -k -H 'host: domain1.org' https://${HOSTNAME}:30305/testwebapp/
$ curl -k -H 'host: domain2.org' https://${HOSTNAME}:30307/testwebapp/
```

## Uninstall the Voyager Operator
After removing all the Voyager Ingress resources, uninstall the Voyager operator:

Using Helm 3:
```
$ helm uninstall voyager-operator --namespace voyager
```
Using Helm 2:
```
$ helm delete --purge voyager-operator
```

## Install and uninstall the Voyager operator with setup.sh
Alternatively, you can run the helper script `setup.sh` when using Helm 2, under the `kubernetes/samples/charts/util` folder, to install and uninstall Voyager.

To install Voyager:
```
$ ./setup.sh create voyager
```
To uninstall Voyager:
```
$ ./setup.sh delete voyager
```

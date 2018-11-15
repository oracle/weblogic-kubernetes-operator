# Install and configure Voyager

## A step-by-step guide to install the Voyager operator
AppsCode has provided a Helm chart to install Voyager. See the official installation document at https://appscode.com/products/voyager/7.4.0/setup/install/.

As a demonstration, the following are the detailed steps to install the Voyager operator by using a Helm chart on a Linux OS.

### 1. Install Onessl
Onessl is a utility provided by AppsCode. We'll use it to get a CA certificate for the Kubernetes cluster.
```
# The assumption is that you have added ~/bin to your PATH env.
$ curl -fsSL -o onessl https://github.com/kubepack/onessl/releases/download/0.3.0/onessl-linux-amd64 \
  && chmod +x onessl \
  && mv onessl ~/bin
```

### 2. Add the AppsCode chart repository
```
$ helm repo add appscode https://charts.appscode.com/stable/
$ helm repo update
$ helm search appscode/voyager
```

### 3. Install the Voyager operator
```
# Kubernetes 1.9.x - 1.10.x
$ kubectl create ns voyager
$ helm install appscode/voyager --name voyager-operator --version 7.4.0 \
  --namespace voyager \
  --set cloudProvider=baremetal \
  --set apiserver.ca="$(onessl get kube-ca)" \
  --set apiserver.enableValidatingWebhook=true
```
## Optionally, download the Voyager Helm chart
If you want, you can download the Voyager Helm chart and untar it into a local folder:
```
$ helm fetch appscode/voyager --untar --version 7.4.0
```

## Configure Voyager as a load balancer for WLS domains
We'll demonstrate how to use Voyager to handle traffic to backend WLS domains.

### 1. Install WLS domains
Now we need to prepare some domains for Voyager load balancing.

Create two WLS domains:
- One domain with name `domain1` under namespace `default`.
- One domain with name `domain2` under namespace `test1`.
- Each domain has a web application installed with the URL context `testwebapp`.

### 2. Install the Voyager Ingress
#### Install a host-routing Ingress
```
$ kubectl create -f samples/host-routing.yaml
```
Now you can send requests to different WLS domains with the unique entry point of Voyager with different hostnames.
```
$ curl --silent -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp/
$ curl --silent -H 'host: domain2.org' http://${HOSTNAME}:30305/testwebapp/
```
To see the Voyager host-routing stats web page, access the URL `http://${HOSTNAME}:30315` in your web browser.

#### Install a path-routing Ingress
```
$ kubectl create -f samples/path-routing.yaml
```
Now you can send requests to different WLS domains with the unique entry point of Voyager with different paths.
```
$ curl http://${HOSTNAME}:30307/domain1/
$ curl http://${HOSTNAME}:30307/domain2/
```
To see the Voyager path-routing stats web page, access URL `http://${HOSTNAME}:30317` in your web browser.

#### Install a TLS-enabled Ingress
This sample demonstrates accessing the two WLS domains using an HTTPS endpoint and the WLS domains are protected by different TLS certificates.

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
$ curl -k -H 'host: domain1.org' https://${HOSTNAME}:30305/testwebapp/
$ curl -k -H 'host: domain2.org' https://${HOSTNAME}:30307/testwebapp/
```

## Uninstall the Voyager Operator
After removing all the Voyager Ingress resources, uninstall the Voyager operator:
```
helm delete --purge voyager-operator
```

## Install and uninstall the Voyager operator with setup.sh
Alternatively, you can run the helper script `setup.sh`, under the `kubernetes/samples/charts/util` folder, to install and uninstall Voyager.

To install Voyager:
```
$ ./setup.sh create voyager
```
To uninstall Voyager:
```
$ ./setup.sh delete voyager
```

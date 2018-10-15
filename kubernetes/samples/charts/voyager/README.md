# Install and Configure Voyager
This is to:
* Provide detail steps how to install and uninstall Voyager operator with helm chart.
* Provide workable Ingress samples to route workload traffic to multiple WLS domains with Voyager.

## Install Voyager Operator with Helm Chart
Appscode has provided helm chart to install Voyager. See official installation document: https://appscode.com/products/voyager/7.4.0/setup/install/.

As a demonstration, following are the detail steps to install Voyager operator using helm chart in hostlinux.

### 1. Install Onessl
Onessl is a utility provided by Appscode. We'll use it to get CA certificate for K8S cluster later.
```
# The pre-consumption is that you have added ~/bin to your PATH env.
$ curl -fsSL -o onessl https://github.com/kubepack/onessl/releases/download/0.3.0/onessl-linux-amd64 \
  && chmod +x onessl \
  && mv onessl ~/bin
```

### 2. Add Appscode Chart Repository
```
$ helm repo add appscode https://charts.appscode.com/stable/
$ helm repo update
$ helm search appscode/voyager
```

### 3. Install Voyager Operator
```
# Kubernetes 1.9.x - 1.10.x
$ kubectl create ns voyager
$ helm install appscode/voyager --name voyager-operator --version 7.4.0 \
  --namespace voyager \
  --set cloudProvider=baremetal \
  --set apiserver.ca="$(onessl get kube-ca)" \
  --set apiserver.enableValidatingWebhook=true
```
## Optionally Download Voyager Helm Chart
You can download voyager helm chart and untar it to a local folder if you want.
```
$ helm fetch appscode/voyager --untar --version 7.4.0
```

## Configure Voyager as Load Balancer for WLS Domains
This chapter we'll demonstrate how to use Voyager to handle traffic to backend WLS domains.

### 1. Install some WLS Domains
Now we need to prepare some backends for Voyager to do load balancer. 

Create two WLS domains: 
- One domain with name 'domain1' under namespace 'default'.
- One domain with name 'domain2' under namespace 'test1'.
- Each domain has a webapp installed with url context 'testwebapp'.

### 2. Install Voyager Ingress
#### Install Host-routing Ingress
```
$ kubectl create -f samples/host-routing.yaml
```
Now you can send request to different WLS domains with the unique entry point of Voyager.
```
$ curl --silent -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp/
$ curl --silent -H 'host: domain2.org' http://${HOSTNAME}:30305/testwebapp/
```
To see the Voyager host-routing stats web page, access URL `http://${HOSTNAME}:30315` in your web browser.

#### Install Path-routing Ingress
```
$ kubectl create -f samples/path-routing.yaml
```
Now you can send request to different WLS domains with the unique entry point of Voyager.
```
$ curl http://${HOSTNAME}:30307/domain1/
$ curl http://${HOSTNAME}:30307/domain2/
```
To see the Voyager path-routing stats web page, access URL `http://${HOSTNAME}:30317` in your web browser.

## Uninstall Voyager Operator
After removing all Voyager Ingress resources, uninstall Voyager operator.
```
helm delete --purge voyager-operator
```


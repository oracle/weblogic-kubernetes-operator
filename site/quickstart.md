# Quick start guide

Use this quick start guide to create a WebLogic deployment in a Kubernetes cluster with the Oracle WebLogic Kubernetes Operator. Please note that this walk-through is for demonstration purposes only, not for use in production.
These instructions assume that you are already familiar with Kubernetes.  If you need more detailed instructions, please
refer to the [User guide](user-guide.md).

> If you have an old version of the operator installed on your cluster you must remove
  it before installing this version.  You should remove the deployment (for example `kubectl delete deploy weblogic-operator -n your-namespace`) and the custom
  resource definition (for example `kubectl delete crd domain`).  If you do not remove
  the custom resource definition you may see errors like this: 
  
    `Error from server (BadRequest): error when creating "/scratch/output/uidomain/weblogic-domains/uidomain/domain.yaml": 
    the API version in the data (weblogic.oracle/v2) does not match the expected API version (weblogic.oracle/v1`

## Prerequisites
For this exercise, you’ll need a Kubernetes cluster. If you need help setting one up, check out our [cheat sheet](k8s_setup.md).

The operator uses Helm to create and deploy necessary resources and then run the operator in a Kubernetes cluster. For Helm installation and usage information, see [Using operator Helm charts](helm-charts.md).

You should clone this repository to your local machine so that you have access to the
various sample files mentioned throughout this guide:
```
$ git clone https://github.com/oracle/weblogic-kubernetes-operator
```

## 1.	Get these images and put them into your local registry.

a.  If you don't already have one, obtain a Docker Store account, log in to the Docker Store
    and accept the license agreement for the [WebLogic Server image](https://hub.docker.com/_/oracle-weblogic-server-12c).

b.  Log in to the Docker Store from your docker client:
```
$ docker login
```
c.	Pull the operator image:
```
$ docker pull oracle/weblogic-kubernetes-operator:2.0
```
d.	Pull the Traefik load balancer image:
```
$ docker pull traefik:latest
```
e.	Pull the WebLogic 12.2.1.3 install image:
```
$ docker pull store/oracle/weblogic:12.2.1.3
```
f.	Then patch the WebLogic image according to these [instructions](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-patch-wls-for-k8s),
    and copy the image to all nodes in your cluster, or put it in a Docker registry that your cluster can access.

g.  Grant the Helm service account the `cluster-admin` role:

```
$ cat <<EOF | kubectl apply -f -
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: helm-user-cluster-admin-role
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: default
  namespace: kube-system
EOF
```

## 2. Create a Traefik (Ingress-based) load balancer.

Use `helm` to install the [Traefik](../kubernetes/samples/charts/traefik/README.md) load balancer. Use the [values.yaml](../kubernetes/samples/charts/traefik/values.yaml) in the sample but set `kubernetes.namespaces` specifically.
```
$ helm install \
--name traefik-operator \
--namespace traefik \
--values <path>/values.yaml  \
--set "kubernetes.namespaces={traefik}" \
stable/traefik
```
Wait until the Traefik operator pod is running and ready.
```
$ kubectl -n traefik get pod -w
```
## 3. Install the operator.

a.  Create a namespace for the operator:
```
$ kubectl create namespace sample-weblogic-operator-ns
```
b.	Create a service account for the operator in the operator's namespace:
```
$ kubectl create serviceaccount -n sample-weblogic-operator-ns sample-weblogic-operator-sa
```

c.  Use `helm` to install and start the operator from the directory you just cloned:	 

```
$ helm install kubernetes/charts/weblogic-operator \
  --name sample-weblogic-operator \
  --namespace sample-weblogic-operator-ns \
  --set serviceAccount=sample-weblogic-operator-sa \
  --set "domainNamespaces={}" \
  --wait
```

d. Verify that the operator's pod is running, by listing the pods in the operator's namespace. You should see one for the operator.
```
$ kubectl get pods -n sample-weblogic-operator-ns
```

e.  Verify that the operator is up and running by viewing the operator pod's log:

```
$ kubectl log -n sample-weblogic-operator-ns -c weblogic-operator deployments/weblogic-operator
```

## 4. Prepare your environment for a domain.

a.  Create a namespace that can host one or more domains:

```
$ kubectl create namespace sample-domain1-ns
```
b.	Use `helm` to configure the operator to manage domains in this namespace:

```
$ helm upgrade \
  --reuse-values \
  --set "domainNamespaces={sample-domain1-ns}" \
  --wait \
  sample-weblogic-operator \
  kubernetes/charts/weblogic-operator

```
c.  Configure Traefik to manage Ingresses created in this namespace:
```
$ helm upgrade \
  --reuse-values \
  --set "kubernetes.namespaces={traefik,sample-domain1-ns}" \
  traefik-operator \
  stable/traefik
```
d. Wait until the Traefik operator pod finishes restarting.
```
$ kubectl -n traefik get pod -w
```

## 5. Create a domain in the domain namespace.

a. Create a Kubernetes secret containing the `username` and `password` for the domain using the [`create-weblogic-credentials`](../kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh) script:

```
$ cd kubernetes/samples/scripts/create-weblogic-domain-credentials
$ ./create-weblogic-credentials.sh -u weblogic -p welcome1 -n sample-domain1-ns -d sample-domain1
```

The sample will create a secret named `domainUID-weblogic-credentials` where the `domainUID` is replaced
with the value you provided.  For example, the command above would create a secret named
`sample-domain1-weblogic-credentials`.

b.	Create a new image with a domain home by running the [`create-domain`](../kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain.sh) script.
Follow the directions in the [README](../kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/README.md) file,
including:

* Copying the sample `create-domain-inputs.yaml` file and updating your copy with the `domainUID` (`sample-domain1`),
domain namespace (`sample-domain1-ns`) and the `domainHomeImageBase` (`oracle/weblogic:12213-patch-wls-for-k8s`).

* Setting `weblogicCredentialsSecretName` to the name of the secret containing the WebLogic credentials.
  By convention, the secret will be named`domainUID-weblogic-credentials` (where `domainUID` is replaced with the
  actual `domainUID` value).

For example, assuming you named your copy `my-inputs.yaml`:
```
$ cd kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image
$ ./create-domain.sh -i my-inputs.yaml -o /some/output/directory -u username -p password -e
```

You need to provide the WebLogic administration username and password in the `-u` and `-p` options
respectively, as shown in the example.  If you specify the `-e` option, the script will generate the 
Kubernetes YAML files *and* apply them to your cluster.  If you omit the `-e` option, the 
script will just generate the YAML files, but will not take any action on your cluster.

c.	Confirm that the operator started the servers for the domain:
* Use `kubectl` to show that the domain resource was created:
```
$ kubectl describe domain sample-domain1 -n sample-domain1-ns
```

After a short time, you will see the Administration Server and Managed Servers running.
```
$ kubectl get pods -n sample-domain1-ns
```

You should also see all the Kubernetes services for the domain.
```
$ kubectl get services -n sample-domain1-ns
```

d.	Create an Ingress for the domain, in the domain namespace, by using the [sample](../kubernetes/samples/charts/ingress-per-domain/README.md) Helm chart:
* Use `helm install`, specifying the `domainUID` (`sample-domain1`) and domain namespace (`sample-domain1-ns`) in the `values.yaml` file.
```
$ cd kubernetes/samples/charts
$ helm install ingress-per-domain --name domain1-ingress --values ingress-per-domain/values.yaml
```

e.	To confirm that the load balancer noticed the new Ingress and is successfully routing to the domain's server pods
    you can hit the URL for the "WebLogic Ready App" which will return a HTTP 200 status code as
    shown in the example below.  If you used the host-based routing ingress sample you will need to
    provide the hostname in the `-H` option:
```
$ curl -v -H 'host: domain1.org' http://your.server.com:30305/weblogic/ 
* About to connect() to your.server.com port 30305 (#0) 
*   Trying 10.196.1.64... 
* Connected to your.server.com (10.196.1.64) port 30305 (#0)
 > GET /weblogic/ HTTP/1.1 
> User-Agent: curl/7.29.0 
> Accept: */* 
> host: domain1.org 
>
 < HTTP/1.1 200 OK 
< Content-Length: 0 
< Date: Thu, 20 Dec 2018 14:52:22 GMT
 < Vary: Accept-Encoding 
< * Connection #0 to host your.server.com left intact 
```
**Note**: Depending on where your Kubernetes cluster is running, you may need to open firewall ports or
update security lists to allow ingress to this port.

## 6. Remove the domain.

a.	Remove the domain's Ingress by using `helm`:
```
$ helm delete --purge domain1-ingress
```
b.	Remove the domain resources by using the sample [`delete-weblogic-domain-resources`](../kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh) script.

c.	Use `kubectl` to confirm that the server pods and domain resource are gone.
```
$ kubectl get pods -n sample-domain1-ns
$ kubectl get domains -n sample-domain1-ns
```

## 7. Remove the domain namespace.
a.	Configure the Traefik load balancer to stop managing the Ingresses in the domain namespace:

```
$ helm upgrade \
  --reuse-values \
  --set "kubernetes.namespaces={traefik}" \
  traefik-operator \
  stable/traefik
```

b.	Configure the operator to stop managing the domain.

```
$ helm upgrade \
  --reuse-values \
  --set "domainNamespaces={}" \
  --wait \
  sample-weblogic-operator \
  kubernetes/charts/weblogic-operator
```
c.	Delete the domain namespace:

```
$ kubectl delete namespace sample-domain1-ns
```

## 8. Remove the operator.

a.	Remove the operator:
```
$ helm delete --purge sample-weblogic-operator
```
b.	Remove the operator's namespace:

```
$ kubectl delete namespace sample-weblogic-operator-ns
```
## 9. Remove the load balancer.
a.	Remove the Traefik load balancer:
```
$ helm delete --purge traefik-operator
```
b.	Remove the Traefik namespace:

```
$ kubectl delete namespace traefik
```

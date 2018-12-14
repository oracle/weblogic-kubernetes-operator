# Quick start guide

Use this quick start guide to create a WebLogic deployment in a Kubernetes cluster with the Oracle WebLogic Kubernetes Operator. Please note that this walk-through is for demonstration purposes only, not for use in production.

## Prerequisite
For this exercise, you’ll need a Kubernetes cluster. If you need help setting one up, check out our [cheat sheet](k8s_setup.md).

## 1.	Get these images and put them into your local registry.

a.	Pull the operator image:
```
$ docker pull oracle/weblogic-kubernetes-operator:2.0
```
b.	Pull the Traefik load balancer image:
```
$ docker pull traefik:latest
```
c.	Pull the WebLogic 12.2.1.3 install image:
```
$ docker pull store/oracle/weblogic:12.2.1.3
```
d.	Then patch the WebLogic image according to these [instructions](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-patch-wls-for-k8s).

## 2. Create a Traefik (Ingress-based) load balancer.

Use Helm to install the [Traefik](../kubernetes/samples/charts/traefik/README.md) load balancer. Use the [values.yaml](kubernetes/samples/charts/traefik/values.yaml) in the sample but set `kubernetes.namespaces` specifically.
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
c.  Use ` helm` to install and start the operator:	 

```
$ helm install \
  --name sample-weblogic-operator \
  --namespace sample-weblogic-operator-ns \
  --set serviceAccount=sample-weblogic-operator-sa \
  --set "domainNamespaces={}" \
  -- wait \
  kubernetes/charts/weblogic-operator
```
d.  Verify that the operator is up and running by viewing the operator pod's log:

```
$ kubectl log -n sample-weblogic-operator-ns -c weblogic-operator deployments/weblogic-operator
```

## 4. Prepare your environment for a domain.

a.  Create a namespace that can host one or more domains:

```
$ kubectl create namespace sample-domains-ns1
```
b.	Use `helm` to configure the operator to manage domains in this namespace:

```
$ helm upgrade \
  --reuse-values \
  --set "domainNamespaces={sample-domains-ns1}" \
  --wait \
  sample-weblogic-operator \
  kubernetes/charts/weblogic-operator

```
c.  Configure Traefik to manage Ingresses created in this namespace:
```
$ helm upgrade \
  --reuse-values \
  --set "kubernetes.namespaces={traefik,sample-domains-ns1}" \
  traefik-operator \
  stable/traefik
```
d. Wait until the Traefik operator pod finish restart.
```
$ kubectl -n traefik get pod -w
```

## 5. Create a domain in the domain namespace.

a.	Create a new image with a domain home by running the [`create-domain`](../kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain.sh) script. Follow the directions in the [README](../kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/README.md) file, including:

* Modifying the sample `inputs.yaml` file with the `domainUID` (`sample-domain1`) and domain namespace (`sample-domains-ns1`).

* Creating Kubernetes secrets `username` and `password` of the admin account using the [`create-weblogic-credentials`](../kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh) script.
```
cd ../kubernetes/samples/scripts/create-weblogic-domain-credentials
./create-weblogic-credentials.sh -u <username> -p <password> -n sample-domain1-ns -d sample-domain1
```

b.	Confirm that the operator started the servers for the domain:
```
$ kubectl get po -n sample-domain1-ns
```
* Use `kubectl` to show that the domain resource was created:
```
$ kubectl describe domain sample-domain1 -n sample-domain1-ns
```
* Verify that the operator's pod is running, by listing the pods in the operator's namespace. You should see one for the operator.
```
kubectl get pods -n sample-weblogic-operator1-ns
```

c.	Create an Ingress for the domain, in the domain namespace, by using the [sample](../kubernetes/samples/charts/ingress-per-domain/README.md) Helm chart:
* Use `helm install`, specifying the `domainUID` (`sample-domain1`) and domain namespace (`sample-domains-ns1`).
```
$ cd kubernetes/samples/charts
$ helm install ingress-per-domain --name domain1-ingress --values values.yaml
```

d.	Confirm that the load balancer noticed the new Ingress and is successfully routing to the domain's server pods:
```
$ curl http://${HOSTNAME}:30305/sample-domain1/
```


## 6. Remove the domain.

a.	Remove the domain's Ingress by using `helm`:
```
helm delete --purge domain1-ingress
```
b.	Remove the domain resource by using the sample [`delete-weblogic-domain-resources`](../kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh) script.

c.	Use `kubectl` to confirm that the server pods and domain resource are gone.

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
$ kubectl delete namespace sample-domains-ns1
```

## 8. Remove the operator.

a.	Remove the operator:
```
helm delete --purge sample-weblogic-operator
```
b.	Remove the operator's namespace:

```
$ kubectl delete namespace sample-weblogic-operator-ns
```
## 9. Remove the load balancer.
a.	Remove the Traefik load balancer:
```
helm delete --purge traefik-operator
```
b.	Remove the Traefik namespace:

```
$ kubectl delete namespace traefik
```

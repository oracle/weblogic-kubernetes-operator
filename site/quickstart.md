# Quick start guide

Use this quick start guide to create a WebLogic deployment in a Kubernetes cluster.

## 1.	Get the images and put them into your local registry.

For the Operator image:
```
$ docker pull oracle/weblogic-kubernetes-operator:2.0
```
For the Traefik image:
```
$ docker pull traefik:latest
```
## 2. Create a Traefik (Ingress-based) load balancer.

Use Helm to install the [Traefik](../kubernetes/samples/charts/traefik/README.md) load balancer.
```
$ helm install --name traefik-operator --namespace traefik stable/traefik
```
## 3. Configure Kibana and Elasticsearch.

Use the [`elasticsearch_and_kibana`](https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/kubernetes/samples/scripts/elasticsearch_and_kibana.yaml) YAML file.
```
$ kubectl apply -f kubernetes/samples/scripts/elasticsearch_and_kibana.yaml
```

## 4. Install the operator.

* Create a namespace for the operator:
```
$ kubectl create namespace weblogic-operator
```
* Create a `serviceAccount` for the operator's namespace. If not specified, it defaults to `default` (for example, the namespace's default service account).
* Invoke the script to generate the credentials for the operator and add it to the operator YAML file (you can keep all the default values).
* Create the operator using `helm install`, and passing in the namespace, service account, and location of Elasticsearch.
  * Helm is used to deploy the operator in a Kubernetes cluster.
  * Use the `helm install` command to install the operator Helm chart, passing in the `values.yaml`.
  * Edit the `values.yaml` file to update the information such as the operator's namespace and service account.
```
  $ helm install kubernetes/charts/weblogic-operator --name my-operator --namespace weblogic-operator-ns --values values.yaml --wait
```

## 5. Prepare your environment for a domain.

* Optionally, create a domain namespace if you want to persist the domain home in a PV:
```
$ kubectl create namespace domain1-ns
```
* Create the Kubernetes secrets for the Administration Server boot credentials by invoking the [`create-weblogic-credentials` script](https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/kubernetes/samples/scripts/create-weblogic-domain/create-weblogic-credentials.sh).

* Create a PV & PVC for the domain:
  * Find the `create_pv_pvc.sh script` and YAML files you'll need to edit to create the PV and PVC, in the https://github.com/oracle/weblogic-kubernetes-operator/tree/develop/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc directory.
* Edit the operator YAML file to add the domain namespace, then do a `helm upgrade`.
* Create the Docker image for the domain home in the image or use the WebLogic binary image.
  * Run WLST to create the domain in PV (remember to apply the patch).

## 6. Create a domain.

* Edit the domain YAML file (can the defaults be used?).
* Create the domain home for the domain.
  * For a domain home on a PV, first pull the WebLogic 12.2.1.3 install image into a local repository:

```  
$ docker pull store/oracle/weblogic:12.2.1.3-dev
```
 * For reference, see the [domain home on PV README](https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/README.md).
 * The `create-domain.sh` will:
   * Create a directory for the generated Kubernetes YAML files for this domain. The pathname is `/path/to/weblogic-operator-output-directory/weblogic-domains/`.
   * Create a Kubernetes job that will start up a utility WebLogic Server container and run offline WLST scripts, or WebLogic Deploy Tool (WDT) scripts, to create the domain on the shared storage.
   * Run and wait for the job to finish.
   * Create a Kubernetes domain resource YAML file, `domain-custom-resource.yaml`, in the directory that is created above. You can use this YAML file to create the Kubernetes resource using the `kubectl create -f` or `kubectl apply -f` command:

```
          ./create-domain.sh
          -i create-domain-inputs.yaml
          -o /path/to/output-directory
```

* For a domain home in image, use the sample in the Docker GitHub project.

* Optionally, create a configuration overrides template and any additional Kubernetes secrets it needs (for example, to override the domain home configuration of a database URL, username, and password).

* Create a domain resource in the domain namespace.
  * Specify the following information: domain UID, service account, secret name, the domain home details, and optionally, the configuration overrides template name.

* Configure the operator to know about the domain.
   * Edit the operator `values.yaml` file to add the namespace of the domain:

```
$ helm update kubernetes/charts/weblogic-operator --name my-operator --namespace weblogic-operator-ns --values values.yaml --wait
```

* Configure the Traefik load balancer to manage the domain as follows:
  * Create an Ingress for the domain in the domain namespace (it contains the routing rules for the domain):

```
$ cd kubernetes/samples/charts
$ helm install ingress-per-domain --name domain1-ingress --value values.yaml
```

(At this point, do they have a WebLogic Kubernetes deployment in a Kubernetes cluster? If so, we have to give them something to look at, to verify their results.)

## 7. Remove a domain.

* Remove the domain's Kubernetes resources (domain, secrets, ingress, ...).
  * To remove the domain and all the Kubernetes resources (labeled with the `domainUID`), invoke the [delete domain resources script](https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/kubernetes/samples/scripts/delete-weblogic-domain-resources.sh).

  * The operator will notice that the domain's domain resource has been removed and will then kill the pods.
* Configure the Traefik load balancer to stop managing the domain.
  * If you have configured Traefik to manage the domain's namespace (instead of the default: all namespaces), then edit the Traefik YAML file to remove the domain namespace and do a `helm update`:

```
helm update --name traefik-operator --namespace traefik (default values in yaml)
or
helm update --name traefik-operator --namespace traefik --values values.yaml stable/traefik
```

* Remove the domain home if it's on a PV.

## 8. Remove the domain namespace.

* Configure the Traefik load balancer to stop managing the domain namespace. Use `helm upgrade` to remove the domain namespace from the list of namespaces.
* Configure the operator to stop managing the domain. Use `helm upgrade` to remove the domain namespace from the list of domain namespaces.
* Remove the PV & PVC for the domain namespace.
* Remove the domain namespace:
```
$ kubectl delete namespaces domain1-ns
```

## 9. Remove the operator.

* Remove the operator:

```
helm delete --purge my-operator
```
* Remove the operator namespace:

```
$ kubectl delete namespaces weblogic-operator-ns
```

## 10. Remove other resources.

* Optionally, remove Kibana and Elasticsearch:

```
$ kubectl apply -f kubernetes/samples/scripts/elasticsearch_and_kibana.yaml
```
* Remove the Traefik load balancer:

```
helm delete --purge
```
* Remove the Traefik namespace:

```
$ kubectl delete namespaces traefik
```

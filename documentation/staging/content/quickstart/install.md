---
title: "Install the operator and ingress controller"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 4
---

#### Use Helm to install the operator and [Traefik](http://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/traefik/README.md) ingress controller.

First, set up Helm:

```shell
$ helm repo add traefik https://helm.traefik.io/traefik --force-update
```

#### Create a Traefik ingress controller.

Create a namespace for the ingress controller.

```shell
$ kubectl create namespace traefik
```

Set the unsecure and secure nodeports of the Traefik ingress controller and the `kubernetes.namespaces`.


```shell
$ helm install traefik-operator traefik/traefik \
    --namespace traefik \
    --set "ports.web.nodePort=30305" \
    --set "ports.websecure.nodePort=30443" \
    --set "kubernetes.namespaces={traefik}"
```

#### Install the operator.

1.  Create a namespace for the operator:

    ```shell
    $ kubectl create namespace sample-weblogic-operator-ns
    ```

2.	Create a service account for the operator in the operator's namespace:

    ```shell
    $ kubectl create serviceaccount -n sample-weblogic-operator-ns sample-weblogic-operator-sa
    ```

3.  Access the operator Helm chart using this format: `helm repo add <helm-chart-repo-name> <helm-chart-repo-url>`. Each version of the Helm chart defaults to using an operator image from the matching version.

    ```
    $ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update  
    ```
    Install the operator using this format: `helm install <helm-release-name> <helm-chart-repo-name>/weblogic-operator` ...

    ```shell
    $ helm install sample-weblogic-operator weblogic-operator/weblogic-operator \
      --namespace sample-weblogic-operator-ns \
      --set serviceAccount=sample-weblogic-operator-sa \
      --wait
    ```

    This Helm release deploys the operator and configures it with the default behavior to manage Domains in any Kubernetes namespace with the label, `weblogic-operator=enabled`.


4. Verify that the operator's pod is running, by listing the pods in the operator's namespace. You should see one
for the operator and one for the conversion webhook.

    ```shell
    $ kubectl get pods -n sample-weblogic-operator-ns
    ```

5.  Verify that the operator is up and running by viewing the operator pod's log:

    ```shell
    $ kubectl logs -n sample-weblogic-operator-ns -c weblogic-operator deployments/weblogic-operator
    ```

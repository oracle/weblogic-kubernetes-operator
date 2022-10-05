---
title: "Install the operator and ingress controller"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 1
---

#### Use Helm to install the operator and [Traefik](http://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/charts/traefik/README.md) ingress controller.

First, install the operator.

1. Create a namespace for the operator.

    ```shell
    $ kubectl create namespace sample-weblogic-operator-ns
    ```

1. Create a service account for the operator in the operator's namespace.

    ```shell
    $ kubectl create serviceaccount -n sample-weblogic-operator-ns sample-weblogic-operator-sa
    ```

1. Set up Helm with the location of the operator Helm chart using this format: `helm repo add <helm-chart-repo-name> <helm-chart-repo-url>`

    ```shell
    $ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update  
    ```
 1. Install the operator using this format: `helm install <helm-release-name> <helm-chart-repo-name>/weblogic-operator ...`

     ```shell
     $ helm install sample-weblogic-operator weblogic-operator/weblogic-operator \
       --namespace sample-weblogic-operator-ns \
       --set serviceAccount=sample-weblogic-operator-sa \
       --wait
     ```
     This Helm release deploys the operator with the default behavior of managing Domains in all Kubernetes namespaces with the label `weblogic-operator=enabled`.

1. Verify that the operator's pod is running by listing the pods in the operator's namespace. You should see one
   for the operator and one for the [conversion webhook]({{< relref "/managing-operators/conversion-webhook#introduction" >}}), a
   singleton Deployment in your Kubernetes cluster that automatically and transparently upgrades domain resources.

     ```shell
     $ kubectl get pods -n sample-weblogic-operator-ns
     ```

1. Verify that the operator is up and running by viewing the operator pod's log.

      ```shell
      $ kubectl logs -n sample-weblogic-operator-ns -c weblogic-operator deployments/weblogic-operator
      ```

#### Create a Traefik ingress controller.

1. Set up Helm with the location of the Traefik Helm chart using this format: `helm repo add <helm-chart-repo-name> <helm-chart-repo-url>`

   ```shell
   $ helm repo add traefik https://helm.traefik.io/traefik --force-update
   ```

1. Create a namespace for the ingress controller.

   ```shell
   $ kubectl create namespace traefik
   ```

1. Install Traefik using this format: `helm install <helm-release-name> <helm-chart-repo-name>/traefik ...`

   ```shell
   $ helm install traefik-operator traefik/traefik \
       --namespace traefik \
       --set "ports.web.nodePort=30305" \
       --set "ports.websecure.nodePort=30443" \
       --set "kubernetes.namespaces={traefik}"
   ```
    This deploys the Traefik controller with plain text node port `30305`, SSL node port `30443`, and `kubernetes.namespaces` specifically set.

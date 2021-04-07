---
title: "Install the operator and ingress controller"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 4
---

#### Use Helm to install the operator and [Traefik](http://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/traefik/README.md) ingress controller.

First, set up Helm:

```shell
$ helm repo add traefik https://containous.github.io/traefik-helm-chart/ --force-update
```

#### Create a Traefik ingress controller.

Create a namespace for the ingress controller.

```shell
$ kubectl create namespace traefik
```

Use the [values.yaml](http://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/traefik/values.yaml) file in the sample but set `kubernetes.namespaces` specifically.


```shell
$ helm install traefik-operator traefik/traefik \
    --namespace traefik \
    --values kubernetes/samples/charts/traefik/values.yaml \
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

3.  Use `helm` to install and start the operator from the directory you just cloned:	 

    ```shell
    $ helm install sample-weblogic-operator kubernetes/charts/weblogic-operator \
      --namespace sample-weblogic-operator-ns \
      --set image=ghcr.io/oracle/weblogic-kubernetes-operator:3.2.1 \
      --set serviceAccount=sample-weblogic-operator-sa \
      --set "enableClusterRoleBinding=true" \
      --set "domainNamespaceSelectionStrategy=LabelSelector" \
      --set "domainNamespaceLabelSelector=weblogic-operator\=enabled" \
      --wait
    ```

    This Helm release deploys the operator and configures it to manage Domains in any Kubernetes namespace with the label, "weblogic-operator=enabled". Because of the "enableClusterRoleBinding" option, the operator will have privilege in all Kubernetes namespaces. This simplifies adding and removing managed namespaces as you will only have to adjust labels on those namespaces. If you want to limit the operator's privilege to just the set of namespaces that it will manage, then remove this option, but this will mean that the operator only has privilege in the set of namespaces that match the selection strategy at the time the Helm release was installed or upgraded.

    **Note:** Prior to version 3.1.0, the operator's Helm chart only supported configuring the namespaces that the operator would manage using a list of namespaces. The chart now supports specifying namespaces using a label selector, regular expression, or list. Review the available [Helm configuration values]({{< relref "/userguide/managing-operators/using-the-operator/using-helm#operator-helm-configuration-values" >}}).

4. Verify that the operator's pod is running, by listing the pods in the operator's namespace. You should see one
for the operator.

    ```shell
    $ kubectl get pods -n sample-weblogic-operator-ns
    ```

5.  Verify that the operator is up and running by viewing the operator pod's log:

    ```shell
    $ kubectl logs -n sample-weblogic-operator-ns -c weblogic-operator deployments/weblogic-operator
    ```

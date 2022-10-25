---
title: "Clean up"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 4
---


#### Remove the domain and cluster.

1.	Remove the domain's ingress routes by using `kubectl`.

    ```shell
    $ kubectl delete ingressroute quickstart -n sample-domain1-ns
    $ kubectl delete ingressroute console -n sample-domain1-ns
    ```

1.	Use `kubectl` to delete the domain resource.

    ```shell
    $ kubectl delete domain sample-domain1 -n sample-domain1-ns
    ```

1.	Use `kubectl` to confirm that the WebLogic Server instance Pods and Domain are gone.

    ```shell
    $ kubectl get pods -n sample-domain1-ns
    ```
    ```shell
    $ kubectl get domains -n sample-domain1-ns
    ```

1.	Use `kubectl` to delete the cluster resource.

    ```shell
    $ kubectl delete cluster sample-domain1-cluster-1 -n sample-domain1-ns
    ```

1.	Remove the Kubernetes Secrets associated with the domain.

    ```shell
    $ kubectl -n sample-domain1-ns delete secret sample-domain1-weblogic-credentials
    $ kubectl -n sample-domain1-ns delete secret sample-domain1-runtime-encryption-secret
    ```

#### Remove the domain namespace.
1.	Configure the Traefik ingress controller to stop managing the ingresses in the domain namespace.

    ```shell
    $ helm upgrade traefik-operator traefik/traefik \
        --namespace traefik \
        --reuse-values \
        --set "kubernetes.namespaces={traefik}"
    ```

1.	Delete the domain namespace.

    ```shell
    $ kubectl delete namespace sample-domain1-ns
    ```

#### Remove the operator.

1.	Remove the operator.

    ```shell
    $ helm uninstall sample-weblogic-operator -n sample-weblogic-operator-ns
    ```

1.	Remove the operator's namespace.

    ```shell
    $ kubectl delete namespace sample-weblogic-operator-ns
    ```

#### Remove the ingress controller.

1.	Remove the Traefik ingress controller.

    ```shell
    $ helm uninstall traefik-operator -n traefik
    ```

1.	Remove the Traefik namespace.

    ```shell
    $ kubectl delete namespace traefik
    ```

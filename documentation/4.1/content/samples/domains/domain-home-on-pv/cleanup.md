---
title: "Cleanup"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 4
---


#### Remove the domain and cluster

1.	Remove the domain's ingress routes by using `kubectl`.

    ```shell
    $ kubectl delete ingressroute traefik-ingress-sample-domain1-admin-server -n sample-domain1-ns
    $ kubectl delete ingressroute traefik-ingress-sample-domain1-cluster-cluster-1 -n sample-domain1-ns
    $ kubectl delete ingressroute traefik-ingress-sample-domain2-cluster-cluster-1 -n sample-domain1-ns
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
    ```

#### Remove the operator

1.	Remove the operator.

    ```shell
    $ helm uninstall sample-weblogic-operator -n sample-weblogic-operator-ns
    ```

1.	Remove the operator's namespace.

    ```shell
    $ kubectl delete namespace sample-weblogic-operator-ns
    ```

#### Remove the ingress controller

1.	Remove the Traefik ingress controller.

    ```shell
    $ helm uninstall traefik-operator -n traefik
    ```

1.	Remove the Traefik namespace.

    ```shell
    $ kubectl delete namespace traefik
    ```

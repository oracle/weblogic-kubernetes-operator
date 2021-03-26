---
title: "Clean up"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 7
---


#### Remove the domain.

1.	Remove the domain's ingress by using `helm`:

    ```bash
    $ helm uninstall sample-domain1-ingress -n sample-domain1-ns
    ```

1.	Remove the Kubernetes resources associated with the domain by using the sample [`delete-weblogic-domain-resources`](http://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh) script:

    ```bash
    $ kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh -d sample-domain1
    ```

1.	Use `kubectl` to confirm that the WebLogic Server instance Pods and Domain are gone:

    ```bash
    $ kubectl get pods -n sample-domain1-ns
    $ kubectl get domains -n sample-domain1-ns
    ```

#### Remove the domain namespace.
1.	Configure the Traefik ingress controller to stop managing the ingresses in the domain namespace:

    ```bash
    $ helm upgrade traefik-operator traefik/traefik \
        --namespace traefik \
        --reuse-values \
        --set "kubernetes.namespaces={traefik}" 
    ```

1.	Delete the domain namespace:

    ```bash
    $ kubectl delete namespace sample-domain1-ns
    ```


#### Remove the operator.

1.	Remove the operator:

    ```bash
    $ helm uninstall sample-weblogic-operator -n sample-weblogic-operator-ns
    ```

1.	Remove the operator's namespace:

    ```bash
    $ kubectl delete namespace sample-weblogic-operator-ns
    ```

#### Remove the ingress controller.

1.	Remove the Traefik ingress controller:

    ```bash
    $ helm uninstall traefik-operator -n traefik
    ```

1.	Remove the Traefik namespace:

    ```bash
    $ kubectl delete namespace traefik
    ```

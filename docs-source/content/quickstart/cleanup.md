---
title: "Clean up"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 7
---


#### Remove the domain.

1.	Remove the domain's Ingress by using `helm`:

    For Helm 2.x:
    
    ```bash
    $ helm delete --purge sample-domain1-ingress
    ```
    
    For Helm 3.x:

    ```bash
    $ helm uninstall sample-domain1-ingress -n sample-domain1-ns
    ```

1.	Remove the domain resources by using the sample [`delete-weblogic-domain-resources`](http://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh) script:

    ```bash
    $ kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh -d sample-domain1
    ```

1.	Use `kubectl` to confirm that the server pods and domain resource are gone:

    ```bash
    $ kubectl get pods -n sample-domain1-ns
    $ kubectl get domains -n sample-domain1-ns
    ```

#### Remove the domain namespace.
1.	Configure the Traefik load balancer to stop managing the Ingresses in the domain namespace:
    
    ```bash
    $ helm upgrade traefik-operator stable/traefik \
        --namespace traefik \
        --reuse-values \
        --set "kubernetes.namespaces={traefik}" \
        --wait 
    ```

1.	Configure the operator to stop managing the domain:
    
    ```bash
    $ helm upgrade  sample-weblogic-operator \
                  kubernetes/charts/weblogic-operator \
      --namespace sample-weblogic-operator-ns \
      --reuse-values \
      --set "domainNamespaces={}" \
      --wait \
    ```
1.	Delete the domain namespace:

    ```bash
    $ kubectl delete namespace sample-domain1-ns
    ```


#### Remove the operator.

1.	Remove the operator:

    For Helm 2.x:
    
    ```bash
    $ helm delete --purge sample-weblogic-operator
    ```

    For Helm 3.x:
    
    ```bash
    $ helm uninstall sample-weblogic-operator -n sample-weblogic-operator-ns
    ```
    
1.	Remove the operator's namespace:

    ```bash
    $ kubectl delete namespace sample-weblogic-operator-ns
    ```

#### Remove the load balancer.

1.	Remove the Traefik load balancer:

    For Helm 2.x:
    
    ```bash
    $ helm delete --purge traefik-operator
    ```

    For Helm 3.x:
    
    ```bash
    $ helm uninstall traefik-operator -n traefik
    ```
    
1.	Remove the Traefik namespace:

    ```bash
    $ kubectl delete namespace traefik
    ```

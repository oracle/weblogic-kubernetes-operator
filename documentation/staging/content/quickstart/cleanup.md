---
title: "Clean up"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 8
---


#### Remove the domain.

1.	Remove the domain's ingress routes by using `kubectl`:

    ```shell
    $ kubectl delete ingressroute quickstart -n sample-domain1-ns
    $ kubectl delete ingressroute console -n sample-domain1-ns
    ```

1.	Use `kubectl` to delete the domain: 

    ```shell
    $ kubectl delete domain sample-domain1 -n sample-domain1-ns
    ```

1.	Confirm that the WebLogic Server instance Pods and Domain are gone:

    ```shell
    $ kubectl get pods -n sample-domain1-ns
    ```
    ```shell
    $ kubectl get domains -n sample-domain1-ns
    ```

1.	Remove the Kubernetes secret associated with the domain, using the following command:

    ```shell
    $ kubectl -n sample-domain1-ns delete secret sample-domain1-weblogic-credentials
    $ kubectl -n sample-domain1-ns delete secret sample-domain1-runtime-encryption-secret
    ```


#### Remove the domain namespace.
1.	Configure the Traefik ingress controller to stop managing the ingresses in the domain namespace:

    ```shell
    $ helm upgrade traefik-operator traefik/traefik \
        --namespace traefik \
        --reuse-values \
        --set "kubernetes.namespaces={traefik}"
    ```

1.	Delete the domain namespace:

    ```shell
    $ kubectl delete namespace sample-domain1-ns
    ```


#### Remove the operator.

1.	Remove the operator:

    ```shell
    $ helm uninstall sample-weblogic-operator -n sample-weblogic-operator-ns
    ```

1.	Remove the operator's namespace:

    ```shell
    $ kubectl delete namespace sample-weblogic-operator-ns
    ```

#### Remove the ingress controller.

1.	Remove the Traefik ingress controller:

    ```shell
    $ helm uninstall traefik-operator -n traefik
    ```

1.	Remove the Traefik namespace:

    ```shell
    $ kubectl delete namespace traefik
    ```

#### Delete the generated image.

1.  When no longer needed, delete the generated image by using the `docker rmi` command.
    Use the following command to delete an image tagged with `mii-aux-image:v1`:

    ```shell
    $ docker rmi mii-aux-image:v1
    ```

#### Delete the tools directory.

1.  When no longer needed, delete the directory where WebLogic Deploy Tooling and WebLogic Image Tool are installed.

    ```shell
    $ rm -rf /tmp/quickstart/tools/
    ```

#### Delete the models directory.

1.  When no longer needed, delete the directory where WDT model file, archive and variable files are copied.

    ```shell
    $ rm -rf /tmp/quickstart/models/
    ```

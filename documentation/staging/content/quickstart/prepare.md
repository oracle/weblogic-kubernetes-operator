---
title: "Prepare for a domain"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 5
---


1.  Create and label a namespace that can host one or more domains:

    ```shell
    $ kubectl create namespace sample-domain1-ns
    ```
    ```shell
    $ kubectl label ns sample-domain1-ns weblogic-operator=enabled
    ```

1.  Configure Traefik to manage ingresses created in this namespace:

    ```shell
    $ helm upgrade traefik-operator traefik/traefik \
        --namespace traefik \
        --reuse-values \
        --set "kubernetes.namespaces={traefik,sample-domain1-ns}"
    ```

1. Accept the license agreement for the WebLogic Server images.

    In a browser, navigate to https://container-registry.oracle.com/ and sign in.
    Search for `weblogic`, and select the image name in the results, click Continue, then read and accept the license agreement.

1. Create a docker-registry secret to enable pulling the example image from the registry.

   ```shell
   $ kubectl create secret docker-registry ocr-credentials \
        --docker-server=container-registry.oracle.com \
        --docker-username=YOUR_REGISTRY_USERNAME \
        --docker-password=YOUR_REGISTRY_PASSWORD \
        --docker-email=YOUR_REGISTRY_EMAIL \
        -n sample-domain1-ns
   ```
   Replace YOUR_REGISTRY_USERNAME, YOUR_REGISTRY_PASSWORD, and YOUR_REGISTRY_EMAIL with the values you use to access the registry.


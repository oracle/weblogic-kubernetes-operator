---
title: "Prepare for a domain"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 2
---


1.  Create and label a namespace that can host one or more domains.

    ```shell
    $ kubectl create namespace sample-domain1-ns
    ```
    ```shell
    $ kubectl label ns sample-domain1-ns weblogic-operator=enabled
    ```

1.  Configure Traefik to manage ingresses created in this namespace.

    ```shell
    $ helm upgrade traefik-operator traefik/traefik \
        --namespace traefik \
        --reuse-values \
        --set "kubernetes.namespaces={traefik,sample-domain1-ns}"
    ```

    {{% notice note %}} If you have reached this point while following the prerequisites in the domain samples,
    **stop here** and return to the Model in Image [sample instructions]({{< relref "/samples/domains/model-in-image/prerequisites#resume" >}}) or Domain on PV [sample instructions]({{< relref "/samples/domains/domain-home-on-pv/prerequisites#resume" >}}).
    {{% /notice %}}

1. Accept the license agreement for WebLogic Server images.

    a. In a browser, go to the [Oracle Container Registry](https://container-registry.oracle.com/) (OCR) and
    log in using the Oracle Single Sign-On (SSO) authentication service. If you do not already have SSO credentials,
    then at the top, right side of the page, click Sign In to create them.

    b. Search for `weblogic`, then select `weblogic` in the Search Results.

    c. From the drop-down menu, select your language and click Continue.

    d. Then read and accept the license agreement.

1. Create a `docker-registry` secret to enable pulling the example WebLogic Server image from the registry.

   ```shell
   $ kubectl create secret docker-registry weblogic-repo-credentials \
        --docker-server=container-registry.oracle.com \
        --docker-username=YOUR_REGISTRY_USERNAME \
        --docker-password=YOUR_REGISTRY_PASSWORD \
        --docker-email=YOUR_REGISTRY_EMAIL \
        -n sample-domain1-ns
   ```
   Replace `YOUR_REGISTRY_USERNAME`, `YOUR_REGISTRY_PASSWORD`, and `YOUR_REGISTRY_EMAIL` with the values you use to access the registry.

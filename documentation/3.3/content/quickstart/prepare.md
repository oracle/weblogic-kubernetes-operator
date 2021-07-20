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

{{% notice note %}}
If you have reached this point while following the "Model in Image" sample, please
stop here and return to the [sample instructions]({{< relref "/samples/simple/domains/model-in-image/prerequisites#resume" >}}).
{{% /notice %}}

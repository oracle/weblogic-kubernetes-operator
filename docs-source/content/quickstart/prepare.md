---
title: "Prepare for a domain"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 5
---


1.  Create a namespace that can host one or more domains:

    ```bash
    $ kubectl create namespace sample-domain1-ns
    ```

1.	Use `helm` to configure the operator to manage domains in this namespace:

    ```bash
    $ helm upgrade \
      --reuse-values \
      --set "domainNamespaces={sample-domain1-ns}" \
      --wait \
      sample-weblogic-operator \
      kubernetes/charts/weblogic-operator
    ```

1.  Configure Traefik to manage Ingresses created in this namespace:

    ```bash
    $ helm upgrade \
      --reuse-values \
      --set "kubernetes.namespaces={traefik,sample-domain1-ns}" \
      --wait \
      traefik-operator \
      stable/traefik
    ```

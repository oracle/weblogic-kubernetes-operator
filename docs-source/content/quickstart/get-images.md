---
title: "Get images"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 3
---

#### Get these images and put them into your local registry.

1. Pull the operator image:

    ```bash
    $ docker pull ghcr.io/oracle/weblogic-kubernetes-operator:3.1.4
    ```

1. Pull the Traefik ingress controller image:

    ```bash
    $ docker pull traefik:2.2.1
    ```

1. Obtain the WebLogic Server image from the [Oracle Container Registry](https://container-registry.oracle.com).

    a. First time users, follow these [directions]({{< relref "/userguide/managing-domains/domain-in-image/base-images/_index.md#obtaining-standard-images-from-the-oracle-container-registry" >}}).

    b. Find and then pull the WebLogic 12.2.1.4 install image:

     ```bash
     $ docker pull container-registry.oracle.com/middleware/weblogic:12.2.1.4
     ```

    {{% notice note %}} The WebLogic Server image, `weblogic:12.2.1.3`, has all the necessary patches applied. The WebLogic Server image, `weblogic:12.2.1.4`, does not require any additional patches.
    {{% /notice %}}


1. Copy the image to all the nodes in your cluster, or put it in a container registry that your cluster can access.

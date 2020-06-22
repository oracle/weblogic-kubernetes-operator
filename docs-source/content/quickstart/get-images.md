---
title: "Get images"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 3
---

#### Get these images and put them into your local registry.

1. If you don't already have one, obtain a Docker store account, log in to the Docker store,
and accept the license agreement for the [WebLogic Server image](https://hub.docker.com/_/oracle-weblogic-server-12c).

1. Log in to the Docker store from your Docker client:

    ```bash
    $ docker login
    ```

1. Pull the operator image:

    ```bash
    $ docker pull oracle/weblogic-kubernetes-operator:2.6.0
    ```
    
    {{% notice note %}} If you are here because you are following the Model In Image sample,
    change the image to `oracle/weblogic-kubernetes-operator:3.0.0-rc1`
    in the previous command.
    {{% /notice %}}

1. Pull the Traefik load balancer image:

    ```bash
    $ docker pull traefik:1.7.12
    ```

1. Obtain the WebLogic image from the [Oracle Container Registry](https://container-registry.oracle.com).

    a. First time users, follow these [directions]({{< relref "/userguide/managing-domains/domain-in-image/base-images/_index.md#obtaining-standard-images-from-the-oracle-container-registry" >}}).

    b. Find and then pull the WebLogic 12.2.1.4 install image:

     ```bash
     $ docker pull container-registry.oracle.com/middleware/weblogic:12.2.1.4
     ```

    {{% notice note %}} The WebLogic Docker image, `weblogic:12.2.1.3`, has all the necessary patches applied. The WebLogic Docker image, `weblogic:12.2.1.4`, does not require any additional patches.
    {{% /notice %}}


1. Copy the image to all the nodes in your cluster, or put it in a Docker registry that your cluster can access.

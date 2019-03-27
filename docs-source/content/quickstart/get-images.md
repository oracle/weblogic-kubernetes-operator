---
title: "Get images"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 3
---

#### Get these images and put them into your local registry.

1. If you don't already have one, obtain a Docker store account, log in to the Docker store
and accept the license agreement for the [WebLogic Server image](https://hub.docker.com/_/oracle-weblogic-server-12c).

1. Log in to the Docker store from your Docker client:

    ```bash
    $ docker login
    ```

1. Pull the operator image:

    ```bash
    $ docker pull oracle/weblogic-kubernetes-operator:2.1
    ```

1. Pull the Traefik load balancer image:

    ```bash
    $ docker pull traefik:1.7.6
    ```

1. Pull the WebLogic 12.2.1.3 install image:

    ```bash
    $ docker pull store/oracle/weblogic:12.2.1.3
    ```  


    {{% notice note %}} The existing WebLogic Docker image, `store/oracle/weblogic:12.2.1.3`, was updated on January 17, 2019, and has all the necessary patches applied; a `docker pull` is required if you pulled the image prior to that date.
    {{% /notice %}}


1. Copy the image to all the nodes in your cluster, or put it in a Docker registry that your cluster can access.

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
    $ docker pull oracle/weblogic-kubernetes-operator:2.2
    ```

1. Pull the Traefik load balancer image:

    ```bash
    $ docker pull traefik:1.7.6
    ```

1. Log into the [Oracle Container Registry](https://container-registry.oracle.com). First time users,
follow these [directions](https://docs.oracle.com/cd/E37670_01/E75728/html/oracle-registry-server.html).

    a. Accept the Oracle Standard Terms and Restrictions.

     ```bash
     $ docker login container-registry.oracle.com
     ```

     b. Provide the same credentials that you used to log into the web interface.

1. Find and then pull the WebLogic 12.2.1.3 install image:

    ```bash
    $ docker pull container-registry.oracle.com/middleware/weblogic:12.2.1.3
    ```  


    {{% notice note %}} The WebLogic Docker image, `weblogic:12.2.1.3`, has all the necessary patches applied.
    {{% /notice %}}


1. Copy the image to all the nodes in your cluster, or put it in a Docker registry that your cluster can access.

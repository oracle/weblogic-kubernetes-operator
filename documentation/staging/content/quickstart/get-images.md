---
title: "Get images"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 3
---

#### Get these images and put them into your local registry.

1. Pull the operator image:

    ```shell
    $ docker pull ghcr.io/oracle/weblogic-kubernetes-operator:{{< latestVersion >}}
    ```

1. Pull the Traefik ingress controller image:

    ```shell
    $ docker pull traefik:2.2.1
    ```

1. Obtain the WebLogic Server image from the [Oracle Container Registry](https://container-registry.oracle.com).

    a. First time users, follow these [directions]({{< relref "/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}}).

    b. Find and then pull the WebLogic 12.2.1.4 General Availability (GA) installation image.

   {{% notice warning %}}
   GA images are suitable for demonstration and development purposes _only_ where the environments are not available from the public Internet; they are **not acceptable for production use**. In production, you should always use CPU (patched) images from [OCR]({{< relref "/base-images/ocr-images.md" >}}) or create your images using the [WebLogic Image Tool]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}}) (WIT) with the `--recommendedPatches` option. For more guidance, see [Apply the Latest Patches and Updates](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/14.1.1.0&id=LOCKD-GUID-2DA84185-46BA-4D7A-80D2-9D577A4E8DE2) in _Securing a Production Environment for Oracle WebLogic Server_.
   {{% /notice %}}

     ```shell
     $ docker pull container-registry.oracle.com/middleware/weblogic:12.2.1.4
     ```

1. Copy the image to all the nodes in your cluster, or put it in a container registry that your cluster can access.

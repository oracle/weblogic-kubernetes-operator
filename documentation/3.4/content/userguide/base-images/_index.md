---
title: "WebLogic images"
date: 2019-02-23T16:45:55-05:00
weight: 6
pre: "<b> </b>"
description: "Obtain, create, and dynamically patch images for WebLogic Server or Fusion Middleware Infrastructure deployments."
---
The following sections guide you through obtaining, creating, and dynamically updating WebLogic images.

{{% children style="h4" description="true" %}}


#### Use or create WebLogic images depending on [domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

* Model in Image domains that use [auxiliary images]({{< relref "/userguide/managing-domains/model-in-image/auxiliary-images.md" >}}):
  * [Understand Oracle Container Registry images]({{< relref "/userguide/base-images/ocr-images#understand-oracle-container-registry-images" >}})
  * [Obtain images from the Oracle Container Registry]({{< relref "/userguide/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}})
  * [Create a custom image with patches applied]({{< relref "/userguide/base-images/custom-images#create-a-custom-image-with-patches-applied" >}})
  * [Auxiliary images]({{< relref "/userguide/managing-domains/model-in-image/auxiliary-images.md" >}})

* Model in Image domains that _do not_ use auxiliary images:
  * [Create a custom image with your model inside the image]({{< relref "/userguide/base-images/custom-images#create-a-custom-image-with-your-model-inside-the-image" >}})

* Domain in Image domains:
  * [Create a custom image with your domain inside the image]({{< relref "/userguide/base-images/custom-images#create-a-custom-image-with-your-domain-inside-the-image" >}})

* Domain in Persistent Volume (PV) domains:
  * [Understand Oracle Container Registry images]({{< relref "/userguide/base-images/ocr-images#understand-oracle-container-registry-images" >}})
  * [Obtain images from the Oracle Container Registry]({{< relref "/userguide/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}})
  * [Create a custom image with patches applied]({{< relref "/userguide/base-images/custom-images#create-a-custom-image-with-patches-applied" >}})

---
title: "Deprecated functionality"
date: 2019-02-23T08:14:59-05:00
weight: 11
draft: false
---

The following functionality has been deprecated and no longer is supported in WebLogic Kubernetes Operator.

#### Domain in Image
The Domain in Image [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) is deprecated in WebLogic Kubernetes Operator version 4.0. Oracle recommends that you choose either Domain on PV or Model in Image, depending on your needs.

#### Model in Image without auxiliary images
The Model in Image domain home source type _without_ auxiliary images (the WDT model and installation files are included in the same image with the WebLogic Server installation) is deprecated in WebLogic Kubernetes Operator version 4.0.7. Oracle recommends that you use Model in Image _with_ [auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).

#### Model in Image for JRF domains
The Model in Image domain home source type for JRF domains is deprecated in WebLogic Kubernetes Operator version 4.1.0. For JRF domains, use [Domain on PV]({{< relref "/managing-domains/domain-on-pv/_index.md" >}}) instead.

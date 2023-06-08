---
title: "Container image protection"
date: 2019-03-08T19:00:49-05:00
weight: 2
description: "WebLogic domain in image protection."
---

{{% notice warning %}}
Oracle strongly recommends storing the container images that contain a
WebLogic domain home as private in the container registry.
In addition to any local registry, public container registries include
[GitHub Container Registry](https://ghcr.io/) and the
[Oracle Cloud Infrastructure Registry](https://cloud.oracle.com/containers/registry) (OCIR).
{{% /notice %}}

The WebLogic domain home that is part of a Domain in Image  image contains sensitive
information about the domain including keys and credentials that are used to
access external resources (for example, the data source password). In addition, the image
may be used to create a running server that further exposes the WebLogic domain
outside of the Kubernetes cluster.

**NOTE**: The Domain in Image [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) is deprecated in WebLogic Kubernetes Operator version 4.0. Oracle recommends that you choose either Domain on PV or Model in Image, depending on your needs.

For information about setting up Kubernetes to access a private registry, see
[Set up Kubernetes to access domain images]({{< relref "/base-images/access-images.md" >}}).

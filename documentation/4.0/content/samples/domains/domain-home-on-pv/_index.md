---
title: "Domain on PV"
date: 2019-02-23T17:32:31-05:00
weight: 4
description: "Sample for creating a WebLogic domain home on a persistent volume (PV) for deploying the generated WebLogic domain."
---

{{< table_of_contents >}}

### Introduction

This sample demonstrates deploying a
[Domain on PV]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) domain home source type
 with [Domain creation images]({{< relref "/managing-domains/domain-on-pv/domain-creation-images.md" >}}).
The Domain on PV sample uses a WebLogic Deploy Tooling (WDT) model to specify your initial WebLogic configuration. For more information about WDT models, see [Working with model files]({{<relref "/managing-domains/working-with-wdt-models/model-files.md" >}}) in the user documentation.

#### Domain on PV domain types (WLS and JRF)

Domain on PV is supported on two types of domains: a standard WLS domain and an Oracle Fusion Middleware Infrastructure, Java Required Files (JRF) domain. This sample demonstrates both WLS and JRF domain types.

The JRF domain path through the sample includes additional steps required for JRF: deploying an infrastructure database, initializing the database using the Repository Creation Utility (RCU) tool, referencing the infrastructure database from the WebLogic configuration, setting an Oracle Platform Security Services (OPSS) wallet password, and exporting/importing an OPSS wallet file. JRF domains may be used by Oracle products that layer on top of WebLogic Server, such as SOA and OSB.

#### Ensuring your Kubernetes cluster can access images

If you run the sample from a machine that is remote to one or more of your Kubernetes cluster worker nodes, then you need to ensure that the images you create can be accessed from any node in the cluster.

For example, if you have permission to put the image in a container registry that the cluster can also access, then:
  - After you've created an image:
    - `docker tag` the image with a target image name (including the registry host name, port, repository name, and the tag, if needed).
    - `docker push` the tagged image to the target repository.
  - Before you deploy a Domain:
    - Modify the Domain YAML file's `image:` value to match the image tag for the image in the repository.
    - If the repository requires a login, then also deploy a corresponding Kubernetes `docker secret` to the same namespace that the Domain will use, and modify the Domain YAML file's `imagePullSecrets:` to reference this secret.

Alternatively, if you have access to the local image cache on each worker node in the cluster, then you can use a Docker command to save the image to a file, copy the image file to each worker node, and use a Docker command to load the image file into the node's image cache.

For more information, see the [Cannot pull image]({{<relref "/faq/cannot-pull-image">}}) FAQ.

### References

For references to the relevant user documentation, see:
 - [Domain on PV]({{< relref "/managing-domains/domain-on-pv/_index.md" >}}) user documentation
 - [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/)
 - [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/)

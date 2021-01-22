---
title: "Model in image"
date: 2019-02-23T17:32:31-05:00
weight: 4
description: "Sample for supplying a WebLogic Deploy Tooling (WDT) model that the operator expands into a full domain home during runtime."
---


### Contents

   - [Introduction](#introduction)
     - [Model in Image domain types (WLS, JRF, and Restricted JRF)](#model-in-image-domain-types-wls-jrf-and-restricted-jrf)
     - [Use cases](#use-cases)
     - [Sample directory structure](#sample-directory-structure)
     - [Ensuring your Kubernetes cluster can access images](#ensuring-your-kubernetes-cluster-can-access-images)
   - [References](#references)
   - [Prerequisites for all domain types]({{< relref "/samples/simple/domains/model-in-image/prerequisites#prerequisites-for-all-domain-types" >}})
   - [Additional prerequisites for JRF domains]({{< relref "/samples/simple/domains/model-in-image/prerequisites#additional-prerequisites-for-jrf-domains" >}})
   - [Initial]({{< relref "/samples/simple/domains/model-in-image/initial.md" >}}) use case: An initial WebLogic domain
   - [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}): Dynamically adding a data source using a model ConfigMap
   - [Update 2]({{< relref "/samples/simple/domains/model-in-image/update2.md" >}}): Deploying an additional domain
   - [Update 3]({{< relref "/samples/simple/domains/model-in-image/update3.md" >}}): Updating an application in an image
   - [Cleanup]({{< relref "/samples/simple/domains/model-in-image/cleanup.md" >}})


### Introduction


This sample demonstrates deploying a Model in Image [domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}). Unlike Domain in PV and Domain in Image, Model in Image eliminates the need to pre-create your WebLogic domain home prior to deploying your Domain YAML file. Instead, Model in Image uses a WebLogic Deploy Tooling (WDT) model to specify your WebLogic configuration.

WDT models are a convenient and simple alternative to WebLogic Scripting Tool (WLST) configuration scripts and templates. They compactly define a WebLogic domain using YAML files and support including application archives in a ZIP file. The WDT model format is described in the open source, [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling) GitHub project, and the required directory structure for a WDT archive is specifically discussed [here](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/archive.md).

For more information on Model in Image, see the [Model in Image user guide]({{< relref "/userguide/managing-domains/model-in-image/_index.md" >}}). For a comparison of Model in Image to other domain home source types, see [Choose a domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

#### Model in Image domain types (WLS, JRF, and Restricted JRF)

There are three types of domains supported by Model in Image: a standard `WLS` domain, an Oracle Fusion Middleware Infrastructure Java Required Files (`JRF`) domain, and a `RestrictedJRF` domain. This sample demonstrates the `WLS` and `JRF` types.

The `JRF` domain path through the sample includes additional steps required for JRF: deploying an infrastructure database, initializing the database using the Repository Creation Utility (RCU) tool, referencing the infrastructure database from the WebLogic configuration, setting an Oracle Platform Security Services (OPSS) wallet password, and exporting/importing an OPSS wallet file. `JRF` domains may be used by Oracle products that layer on top of WebLogic Server, such as SOA and OSB. Similarly, `RestrictedJRF` domains may be used by Oracle layered products, such as Oracle Communications products.

#### Use cases

This sample demonstrates four Model in Image use cases:

- [Initial]({{< relref "/samples/simple/domains/model-in-image/initial.md" >}}): An initial WebLogic domain with the following characteristics:

   - Image `model-in-image:WLS-v1` with:
     - A WebLogic installation
     - A WebLogic Deploy Tooling (WDT) installation
     - A WDT archive with version `v1` of an exploded Java EE web application
     - A WDT model with:
       - A WebLogic Administration Server
       - A WebLogic cluster
       - A reference to the web application
   - Kubernetes Secrets:
     - WebLogic credentials
     - Required WDT runtime password
   - A Domain with:
     - `metadata.name` and `weblogic.domainUID` label set to `sample-domain1`
     - `spec.domainHomeSourceType: FromModel`
     - `spec.image: model-in-image:WLS-v1`
     - References to the secrets

- [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}): Demonstrates updating the initial domain by dynamically adding a data source using a model ConfigMap.

   - Image `model-in-image:WLS-v1`:
     - Same image as Initial use case
   - Kubernetes Secrets:
     - Same as Initial use case, plus secrets for data source credentials and URL
   - Kubernetes ConfigMap with:
     - A WDT model for a data source targeted to the cluster
   - A Domain, same as Initial use case, plus:
     - `spec.model.configMap` referencing the ConfigMap
     - References to data source secrets

- [Update 2]({{< relref "/samples/simple/domains/model-in-image/update2.md" >}}): Demonstrates deploying a second domain (similar to the Update 1 use case domain).

  - Image `model-in-image:WLS-v1`:
    - Same image as the Initial and Update 1 use cases
  - Kubernetes Secrets and ConfigMap:
    - Similar to the Update 1 use case, except names and labels are decorated with a new domain UID
  - A Domain, similar to Update 1 use case, except:
    - Its `metadata.name` and `weblogic.domainUid` label become `sample-domain2` instead of `sample-domain1`
    - Its secret/ConfigMap references are decorated with `sample-domain2` instead of `sample-domain1`
    - Has a changed `env` variable that sets a new domain name

- [Update 3]({{< relref "/samples/simple/domains/model-in-image/update3.md" >}}): Demonstrates deploying an updated image with an updated application to the Update 1 use case domain.

  - Image `model-in-image:WLS-v2`, similar to `model-in-image:WLS-v1` image with:
    - An updated web application `v2` at the `myapp-v2` directory path instead of `myapp-v1`
    - An updated model that points to the new web application path
  - Kubernetes Secrets and ConfigMap:
    - Same as the Update 1 use case
  - A Domain:
    - Same as the Update 1 use case, except `spec.image` is `model-in-image:WLS-v2`

#### Sample directory structure

The sample contains the following files and directories:

Location | Description |
------------- | ----------- |
`domain-resources` | JRF and WLS Domain YAML files. |
`archives` | Source code location for WebLogic Deploy Tooling application ZIP archives. |
`model-images` | Staging for each model image's WDT YAML files, WDT properties, and WDT archive ZIP files. The directories in `model images` are named for their respective images. |
`model-configmaps` | Staging files for a model ConfigMap that configures a data source. |
`ingresses` | Ingress resources. |
`utils/wl-pod-wait.sh` | Utility script for watching the pods in a domain reach their expected `restartVersion`, image name, and ready state. |
`utils/patch-restart-version.sh` | Utility script for updating a running domain `spec.restartVersion` field (which causes it to 're-instrospect' and 'roll'). |
`utils/opss-wallet.sh` | Utility script for exporting or importing a JRF domain OPSS wallet file. |

#### Ensuring your Kubernetes cluster can access images

If you run the sample from a machine that is remote to one or more of your Kubernetes cluster worker nodes, then you need to ensure that the images you create can be accessed from any node in the cluster.

For example, if you have permission to put the image in a container registry that the cluster can also access, then:
  - After you've created an image:
    - `docker tag` the image with a target image name (including the registry host name, port, repository name, and the tag, if needed).
    - `docker push` the tagged image to the target repository.
  - Before you deploy a Domain:
    - Modify the Domain YAML file's `image:` value to match the image tag for the image in the repository.
    - If the repository requires a login, then also deploy a corresponding Kubernetes `docker secret` to the same namespace that the Domain will use, and modify the Domain YAML file's `imagePullSecrets:` to reference this secret.

Alternatively, if you have access to the local image cache on each worker node in the cluster, then you can use a Docker command to save the image to a file, copy the image file to each worker node, and use a `docker` command to load the image file into the node's image cache.

For more information, see the [Cannot pull image FAQ]({{<relref "/faq/cannot-pull-image">}}).

### References

For references to the relevant user documentation, see:
 - [Model in Image]({{< relref "/userguide/managing-domains/model-in-image/_index.md" >}}) user documentation
 - [Oracle WebLogic Server Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling)
 - [Oracle WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool)

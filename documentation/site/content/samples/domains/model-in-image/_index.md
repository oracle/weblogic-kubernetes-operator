---
title: "Model in image"
date: 2019-02-23T17:32:31-05:00
weight: 4
description: "Sample for supplying a WebLogic Deploy Tooling (WDT) model that the operator expands into a full domain home during runtime."
---

{{< table_of_contents >}}

### Introduction

This sample demonstrates deploying a [Model in Image]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) domain home source type
with [Auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).
Model in Image eliminates the need to pre-create
your WebLogic domain home prior to deploying your Domain YAML file.
Instead, Model in Image uses a
WebLogic Deploy Tooling (WDT) model to specify your WebLogic configuration.

WDT models are a convenient and simple alternative to WebLogic Scripting Tool (WLST) configuration scripts. They compactly define a WebLogic domain using model files, variable properties files, and application archive files. The WDT model format is described in the open source, [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) GitHub project, and the required directory structure for a WDT archive is specifically discussed [here](https://oracle.github.io/weblogic-deploy-tooling/concepts/archive/).

Furthermore, the Model in Image auxiliary image option lets you supply your WDT models files, WDT variable files, and WDT archives files
in a small separate image separate from your WebLogic image.

For more information, see the [Model in Image]({{< relref "/managing-domains/model-in-image/_index.md" >}}) user guide. For a comparison of Model in Image to other domain home source types, see [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).

#### Use cases

This sample demonstrates five Model in Image use cases:

- [Initial]({{< relref "/samples/domains/model-in-image/initial.md" >}}): An initial WebLogic domain with the following characteristics:

   - Auxiliary image `wdt-domain-image:WLS-v1` with:
     - A directory where the WebLogic Deploy Tooling software is installed (also known as WDT Home).
     - A WDT archive with version `v1` of an exploded Java EE web application
     - A WDT model with:
       - A WebLogic Administration Server
       - A WebLogic cluster
       - A reference to the web application
   - A WebLogic image with a WebLogic and Java installation.
   - Kubernetes Secrets:
     - WebLogic credentials
     - Required WDT runtime password
   - A Domain with:
     - `metadata.name` and `weblogic.domainUID` label set to `sample-domain1`
     - `spec.domainHomeSourceType: FromModel`
     - `spec.image` set to a WebLogic image with a WebLogic and Java installation.
     - References to the secrets

- [Update 1]({{< relref "/samples/domains/model-in-image/update1.md" >}}): Demonstrates updating the initial domain by dynamically adding a data source using a model ConfigMap and then restarting (rolling) the domain to propagate the change. Updates:

   - Kubernetes Secrets:
     - Same as Initial use case, plus secrets for data source credentials and URL
   - Kubernetes ConfigMap with:
     - A WDT model for a data source targeted to the cluster
   - Domain, same as Initial use case, plus:
     - `spec.model.configMap` referencing the ConfigMap
     - References to data source secrets

- [Update 2]({{< relref "/samples/domains/model-in-image/update2.md" >}}): Demonstrates deploying a second domain (similar to the Update 1 use case domain). Updates:

  - Kubernetes Secrets and ConfigMap:
    - Similar to the Update 1 use case, except names and labels are decorated with a new domain UID
  - A Domain, similar to Update 1 use case, except:
    - Its `metadata.name` and `weblogic.domainUid` label become `sample-domain2` instead of `sample-domain1`
    - Its secret/ConfigMap references are decorated with `sample-domain2` instead of `sample-domain1`
    - Has a changed `env` variable that sets a new domain name

- [Update 3]({{< relref "/samples/domains/model-in-image/update3.md" >}}): Demonstrates deploying an updated auxiliary image with an updated application to the Update 1 use case domain and then restarting (rolling) its domain to propagate the change. Updates:

  - Auxiliary image `wdt-domain-image:WLS-v2`, similar to `wdt-domain-image:WLS-v1` image with:
    - An updated web application `v2` at the `myapp-v2` directory path instead of `myapp-v1`
    - An updated model that points to the new web application path
  - Domain:
    - Same as the Update 1 use case, except `spec.image` is `wdt-domain-image:WLS-v2`

- [Update 4]({{< relref "/samples/domains/model-in-image/update4.md" >}}): Demonstrates dynamically updating the running Update 1 or Update 3 WebLogic domain configuration without requiring a domain restart (roll). Updates:

   - Kubernetes ConfigMap with:
     - A WDT model for Work Manager minimum and maximum threads constraints, plus the same data source as the Update 1 use case
   - Kubernetes Secrets:
     - Same as the Update 1 and Update 3 use case, except:
     - An updated data source secret with a new password and an increased maximum pool capacity
   - A Domain, same as Update 1 or Update 3 use case, plus:
     - `spec.configuration.model.onlineUpdate` set to `enabled: true`

#### Sample directory structure

The sample contains the following files and directories:

Location | Description |
------------- | ----------- |
`kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources` | Domain YAML files. |
`kubernetes/samples/scripts/create-weblogic-domain/wdt-artifacts/archives` | Source code location for WebLogic Deploy Tooling application ZIP archives. |
`kubernetes/samples/scripts/create-weblogic-domain/wdt-artifacts/wdt-model-files` | Staging for each model image's WDT YAML files, WDT properties, and WDT archive ZIP files. The directories in `model images` are named for their respective images. |
`kubernetes/samples/scripts/create-weblogic-domain/model-in-image/model-configmaps/datasource` | Staging files for a model ConfigMap that configures a data source. |
`kubernetes/samples/scripts/create-weblogic-domain/model-in-image/model-configmaps/workmanager` | Staging files for a model ConfigMap that configures the Work Manager threads constraints. |
`kubernetes/samples/scripts/create-weblogic-domain/ingresses` | Ingress resources. |
`kubernetes/samples/scripts/create-weblogic-domain/model-in-image/utils/patch-introspect-version.sh` | Utility script for updating a running domain `spec.introspectVersion` field (which causes it to 're-instrospect' and 'roll' only if non-dynamic attributes are updated). |
`kubernetes/samples/scripts/create-weblogic-domain/model-in-image/utils/patch-restart-version.sh` | Utility script for updating a running domain `spec.restartVersion` field (which causes it to 're-instrospect' and 'roll'). |
`kubernetes/samples/scripts/create-weblogic-domain/model-in-image/utils/patch-enable-online-update.sh` | Utility script for updating a running domain `spec.configuration.model.onlineUpdate` field to `enabled: true` (which enables the online update feature). |

In addition, this sample makes use of the `waitForDomain.sh` sample lifecycle script
that is located in the operator source `kubernetes/samples/scripts/domain-lifecycle` directory.
This is a utility script that optionally waits for the pods in a domain
to reach their expected `restartVersion`, `introspectVersion`, `Completed`, `image`, and `ready` state.

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

For more information, see the [Cannot pull image]({{<relref "/faq/cannot-pull-image">}}) FAQ.

### References

For references to the relevant user documentation, see:
 - [Model in Image]({{< relref "/managing-domains/model-in-image/_index.md" >}}) user documentation
 - [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/)
 - [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/)

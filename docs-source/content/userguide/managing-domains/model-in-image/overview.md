+++
title = "Overview"
date = 2020-03-11T16:45:16-05:00
weight = 10
pre = "<b> </b>"
description = "Introduction to Model in Image, description of its runtime behavior, and references."
+++

#### Content

 - [Introduction](#introduction)
 - [WebLogic Deploy Tool models](#weblogic-deploy-tool-models)
 - [Runtime behavior](#runtime-behavior)
 - [Runtime updates](#runtime-updates)
 - [Continuous integration and delivery (CI/CD)](#continuous-integration-and-delivery-cicd)
 - [References](#references)

#### Introduction

Model in Image is an alternative to the operator's Domain in Image and Domain in PV domain types. See [Choose a domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}) for a comparison of operator domain types.

Unlike Domain in PV and Domain in Image, Model in Image eliminates the need to pre-create your WebLogic domain home prior to deploying your domain resource.

It enables:

 - Defining a WebLogic domain home configuration using WebLogic Deploy Tool (WDT) model files and application archives.
 - Embedding model files and archives in a custom Docker image, and using the WebLogic Image Tool (WIT) to generate this image.
 - Supplying additional model files using a Kubernetes ConfigMap.
 - Supplying Kubernetes Secrets that resolve macro references within the models. For example, a secret can be used to supply a database credential.
 - Updating WDT model files at runtime. For example, you can add a data source to a running domain. Note that all such updates currently cause the domain to 'roll' in order to take effect.

This feature is supported for standard WLS domains, Restricted JRF domains, and JRF domains.

For JRF domains, Model in Image provides additional support for initializing the infrastructure database for a domain when a domain is started for the first time, supplying an database password, and obtaining an database wallet for re-use in subsequent restarts of the same domain. See [Requirements for JRF domain types]({{< relref "/userguide/managing-domains/model-in-image/usage/_index.md#requirements-for-jrf-domain-types" >}}).

#### WebLogic Deploy Tool models

WDT models are a convenient and simple alternative to WebLogic Scripting Tool (WLST) configuration scripts and templates. They compactly define a WebLogic domain using YAML files and support including application archives in a ZIP file. For a discussion of the model format and its integration with Model in Image, see [WebLogic Server image]({{< relref "/userguide/managing-domains/model-in-image/usage/_index.md#weblogic-server-image" >}}) and [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}). The WDT model format is fully described in the open source, [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling) GitHub project.

#### Runtime behavior

When you deploy a Model in Image domain resource:

  - The operator will run a Kubernetes Job called the 'introspector job' that:
    - Merges your WDT artifacts.
    - Runs WDT tooling to generate a domain home.
    - Packages the domain home and passes it to the operator.

  - After the introspector job completes:
    - The operator creates a ConfigMap named `DOMAIN_UID-weblogic-domain-introspect-cm` and puts the packaged domain home in it.
    - The operator subsequently boots your domain's WebLogic Server pods.
    - The pods will obtain their domain home from the ConfigMap.

#### Runtime updates

Model updates can be applied at runtime by changing the image, secrets, or WDT model ConfigMap after initial deployment. If the image name changes, or the domain resource `restartVersion` changes, then this will cause the introspector job to rerun and generate a new domain home, and subsequently the changed domain home will be propagated to the domain's WebLogic Server pods using a rolling upgrade (each pod restarting one at a time). See [Runtime updates]({{< relref "/userguide/managing-domains/model-in-image/runtime-updates.md" >}}).

#### Continuous integration and delivery (CI/CD)

To understand how Model in Image works with CI/CD, see [CI/CD considerations]({{< relref "/userguide/cicd/_index.md" >}}).

#### References

 - [Model in Image sample]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}})
 - [WebLogic Deploy Tool (WDT)](https://github.com/oracle/weblogic-deploy-tooling)
 - [WebLogic Image Tool (WIT)](https://github.com/oracle/weblogic-image-tool)
 - Domain resource [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/docs/domains/Domain.md), [documentation]({{< relref "/userguide/managing-domains/domain-resource.md" >}})
 - HTTP load balancers: Ingress [documentation]({{< relref "/userguide/managing-domains/ingress/_index.md" >}}), [sample]({{< relref "/samples/simple/ingress/_index.md" >}})
 - [CI/CD considerations]({{< relref "/userguide/cicd/_index.md" >}})

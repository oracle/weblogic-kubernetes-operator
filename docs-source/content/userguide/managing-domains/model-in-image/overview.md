+++
title = "Overview"
date = 2020-03-11T16:45:16-05:00
weight = 10
pre = "<b> </b>"
description = "Introduction to Model in Image, description of its runtime behavior, and references."
+++

### Content

 - [Introduction](#introduction)
 - [Runtime behavior overview](#runtime-behavior-overview)
 - [Runtime updates overview](#runtime-updates-overview)
 - [References](#references)

### Introduction

Model in Image is an alternative to the operator's Domain in Image and Domain on PV domain types. See [Choose a Model]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}) for a comparison of Operator domain types.

Unlike Domain in PV and Domain in Image, Model in Image eliminates the need to pre-create your WebLogic domain home prior to deploying your domain resource. It enables:

 - Defining a WebLogic domain home configuration using a Weblogic Deploy Tool (WDT) model files and application archives.
 - Embedding model files and archives in a custom Docker image, and leveraging the WebLogic Image Tool (WIT) to generate this image.
 - Supplying additional model files via a Kubernetes config map.
 - Supplying Kubernetes secrets that resolve macro references within the models. For example, a secret can be used to supply a database credential.
 - Updating WDT model files at runtime. For example, you can add a datasource to a runing domain. Note that all such updates currently cause the domain to 'roll' in order to take effect (a limitation that's planned to be removed in a future release).
 - Deploying standard WLS domains, RestrictedJRF domains, or JRF domains.

WDT models are a convenient and simple alternative to WebLogic WLST configuration scripts and templates. They compactly define a WebLogic domain using yaml files, plus support including application archives in a zip file. The WDT model format is described in the open source [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling) GitHub project.

For JRF domains, Model in Image provides additional support for (a) initializing the RCU database for a domain when a domain is started for first time, (b) supplying an RCU password, and finally (c) obtaining an RCU wallet for re-use in subsequent restarts of the same domain. See [(8) Prerequisites for JRF domain types.]({{< relref "/userguide/managing-domains/model-in-image/usage.md#8-prerequisites-for-jrf-domain-types" >}}) and [Reusing an RCU database]({{< relref "/userguide/managing-domains/model-in-image/reusing-rcu.md" >}}).


### Runtime behavior overview

When you deploy a Model in Image domain resource, the operator will run a Kubernetes job called the 'introspector job' that:

  - Merges your WDT artifacts.
  - Runs WDT tooling to generate a domain home.
  - Zips up the domain home.
  - Puts the zip in an output Kubernetes configmap named DOMAIN_UID-weblogic-domain-introspect-cm

After the introspector job completes, the operator subsequently boots your domain's WebLogic pods, and the pods will obtain their domain home from the introspector's output configmap.

### Runtime updates overview

Model updates can be applied at runtime by changing the image, secrets, or WDT model configmap after initial deployment. If the image name changes, or the domain resource `restartVersion` changes, then this will cause the introspector to rerun and generate a new domain home, and subsequently the changed domain home will be propagated to the domain's WebLogic pods via a rolling upgrade (each pod restarting one at a time). See [Runtime updates]({{< relref "/userguide/managing-domains/model-in-image/runtime-updates.md" >}}).

### References

 - [Model in Image Sample]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}})
 - [WebLogic Deploy Tool (WDT)](https://github.com/oracle/weblogic-deploy-tooling)
 - [WebLogic Image Tool (WIT)](https://github.com/oracle/weblogic-image-tool)
 - Domain Resource [Schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/docs/domains/Domain.md), [Doc]({{< relref "/userguide/managing-domains/domain-resource.md" >}})
 - HTTP Load Balancers: [Ingress Doc]({{< relref "/userguide/managing-domains/ingress/_index.md" >}}), [Ingress Sample]({{< relref "/samples/simple/ingress/_index.md" >}})

---
title: "Tools"
date: 2019-04-11T13:50:15-04:00
draft: false
weight: 6
description: "Tools that are available to build CI/CD pipelines."
---

#### WebLogic Deploy Tooling (WDT)

You can use several of the [WDT tools](https://github.com/oracle/weblogic-deploy-tooling)
in a CI/CD pipeline. For example, the
`createDomain` tool creates a new domain based on a simple model, and
`updateDomain` (and `deployApps`) uses the same model concept to update
an existing domain (preserving the same domain encryption key). The `deployApps`
tool is very similar to the `updateDomain` tool, but limits what can be updated
to application-related configuration attributes such as data sources and
application archives.  The model used by these tools is a sparse set of
attributes needed to create or update the domain. A model can be as sparse
as providing only the WebLogic Server administrative password, although not very
interesting.  A good way to get a jumpstart on a model is to use the
`discoverDomain` tool in WDT which builds a model based on an existing domain.

{{% notice note %}}
A Model in Image domain takes advantage of WDT by letting
you specify an operator domain directly with a model instead of requiring
that you supply a domain home.
{{% /notice %}}

Other than the tools themselves, there are three components to the WDT tools:  

- *The Domain Model* - Metadata model describing the desired domain.  
  The metadata domain model can be written in YAML or JSON and is documented [here](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/model.md).
- *The Archive ZIP* - Binaries to supplement the model.  
  All binaries needed to supplement the model must be specified in an archive
  file, which is just a ZIP file with a specific directory structure. Optionally,
  the model can be stored inside the ZIP file, if desired. Any binaries not
  already on the target system must be in the ZIP file so that the tooling
  can extract them in the target domain.
- *The Properties File* - A standard Java properties file.  
  A property file used to provide values to placeholders in the model.

#### WDT Create Domain Samples

- (Docker) A sample for creating a domain in a container image with WDT can be found
  [here](https://github.com/oracle/weblogic-deploy-tooling/tree/master/samples/docker-domain).
- (Kubernetes) A similar sample of creating a domain in an image with WDT
  can be found in the WebLogic Server Kubernetes Operator project for creating a
  [domain-in-image with WDT](https://oracle.github.io/weblogic-kubernetes-operator/samples/simple/domains/domain-home-in-image/).
- (Kubernetes) A [Model in Image sample]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}})
  for supplying an image that contains a WDT model only,
  instead of a domain home. In this case, the operator generates the domain
  home for you at runtime.

#### WebLogic Scripting Tool (WLST)

You can use WLST scripts to create and update domain homes in a CI/CD pipeline
for Domain in Image and Domain in PV domains.
We recommend that you use offline WLST for this purpose.  There may be some
scenarios where it is necessary to use WLST online, but we recommend that
you do that as an exception only, and when absolutely necessary.

If you do not already have WLST scripts, we recommend that you consider
using WebLogic Deploy Tooling (WDT) instead.  It provides a more declarative
approach to domain creation, whereas WLST is more of an imperative scripting
language.  WDT provides advantages like being able to use the same model with
different versions of WebLogic, whereas you may need to update WLST scripts
manually when migrating to a new version of WebLogic for example.

#### WebLogic pack and unpack tools

WebLogic Server provides tools called "pack" and "unpack" that can be used to
"clone" a domain home.  These tools do not preserve the domain encryption key.
You can use these tools to make copies of Domain in PV and Domain in Image
domain homes in scenarios when you do not need the same domain encryption key. See [Creating Templates and Domains Using the Pack and Unpack Commands](https://docs.oracle.com/en/middleware/fusion-middleware/12.2.1.3/wldpu/index.html).

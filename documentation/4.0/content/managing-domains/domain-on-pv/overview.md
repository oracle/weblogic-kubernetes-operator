+++
title = "Overview"
date = 2023-04-26T16:45:16-05:00
weight = 25
pre = "<b> </b>"
description = "Creating domain on PV."
+++

{{< table_of_contents >}}

### Overview

Domain on persistent volume is one of the operator's types. See [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) for a comparison of operator domain types.

Domain on persistent volume require the domain home exists on a persistent volume,  the domain home can be created either manually 
or automated by specifying the section `domain.spec.configuration.initializeDomainOnPV` in the domain resource YAML file.
The initial domain topology and resources are described using [Weblogic Deploy Tooling (WDT)](#weblogic-deploy-tooling-models).

**The `initializeDomainOnPV` section provides a one time only domain home initialization,
the Operator will create the domain when the domain resource is first deployed, once the domain is created,
this section will be ignored, subsequent domain lifecycle updates should be controlled by
WebLogic console, WLST or other mechanisms.**  See [High level use case](#high-level-use-case).

The `initializeDomainOnPv` provides the following functions:

- Create the `PersistentVolume` and/or `PersistenVolumeClaim` if needed.
- Create `JRF RCU schema` if needed.
- Create the domain home based on provided WDT models on the persistent volume. 

### High level use case

The typical use case for using domain home on persistent volume is for application lifecycle that required persisting changes to the permanent file system.

For example, you use frameworks like `Meta data service (MDS)`, `Oracle Application Development Framework (ADF)`, `Oracle Service Bus (OSB)`. 
These frameworks require a running domain and the normal lifecycle operations are persisted to the file systems. Typically,
after the initial domain is created, you use tools like `Fusion Middleware Controls`, product specific `WLST` functions, 
`WebLogic Console`, `Service Bus Console`, `JDeveloper` for normal lifecycle operations, the changes are managed by
these tools, and the data and operation cannot be described using `WDT` models.

### WebLogic Deploy Tooling models

WDT models are a convenient and simple alternative to WebLogic Scripting Tool (WLST)
configuration scripts and templates.
They compactly define a WebLogic domain using YAML files and support including
application archives in a ZIP file. For a description of the model format
and its integration,
see [Usage]({{< relref "/managing-domains/domain-on-pv/usage.md" >}})
and [Model files]({{< relref "/managing-domains/working-with-wdt-models/model-files.md" >}}).
The WDT model format is fully described in the open source,
[WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) GitHub project.

### Runtime behavior

When you deploy a Domain on persistent volume domain resource YAML file:

- The operator will run a Kubernetes Job called the 'introspector job' that:
    - Merges your WDT artifacts.
    - Runs WDT tooling to generate a domain home.

- After the introspector job completes:
    - The operator creates a ConfigMap named `DOMAIN_UID-weblogic-domain-introspect-cm`
      (possibly with some additional maps distinguished by serial names).
    - The operator subsequently boots your domain's WebLogic Server pods.

### Runtime updates

Any runtime updates to the WebLogic domain configuration is controlled by the user using tools such as `Fusion Middleware Controls`, product specific `WLST` functions,
`WebLogic Console`, `Service Bus Console`, or `JDeveloper`.  After the initial domain is created, subsequent updates to the 
source of the `WDT` artifacts or referenced Kubernetes secrets will be ignored.  

Some changes may require triggering an introspector job.  For example, after you made the change to the WeLogic domain credential, you need to:

- update the credentials specified in `domain.spec.webLogicCredentialsSecret` of the domain resource YAML. 
- update the `domai.spec.introspectVersion` of the domain resource YAML.


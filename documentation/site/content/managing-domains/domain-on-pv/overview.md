+++
title = "Overview"
date = 2023-04-26T16:45:16-05:00
weight = 1
pre = "<b> </b>"
description = "Learn how to create a domain on a persistent volume."
+++

{{< table_of_contents >}}

### Overview

Domain on persistent volume (Domain on PV) is an operator [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}),
which requires that the domain home exists on a persistent volume. The domain home can be created either manually
or automatically by specifying the section, `domain.spec.configuration.initializeDomainOnPV`, in the domain resource YAML file.
The initial domain topology and resources are described using [WebLogic Deploy Tooling (WDT) models](#weblogic-deploy-tooling-models).

**NOTE**: The `initializeDomainOnPV` section provides a **one time only** domain home initialization.
The operator creates the domain when the domain resource is first deployed. After the domain is created,
this section is ignored. Subsequent domain lifecycle updates must be controlled by
the WebLogic Server Administration Console, the WebLogic Remote Console, WebLogic Scripting Tool (WLST), or other mechanisms.  See the [High-level use case](#high-level-use-case).

The `initializeDomainOnPv` section:

- Creates the PersistentVolume (PV) and/or PersistenVolumeClaim (PVC), if needed.
- Creates the RCU schema, if needed.
- Creates the WebLogic domain home on the persistent volume based on the provided WDT models.

### High-level use case

The typical Domain on PV use case is for an application life cycle that requires persisting changes to the permanent file system.

For example, you might use frameworks like Metadata Services (MDS), Oracle Application Development Framework (ADF), or Oracle Web Services Manager (OWSM).
These frameworks require a running domain and the lifecycle operations are persisted to the file system. Typically,
after the initial domain is created, you use tools like Fusion Middleware Control, product-specific WLST functions,
the WebLogic Server Administration Console, the WebLogic Remote Console, or JDeveloper for lifecycle operations. The changes are managed by
these tools; the data and operations _cannot_ be described using WDT models.

### WebLogic Deploy Tooling models

WDT models are a convenient and simple alternative to WLST
configuration scripts.
They compactly define a WebLogic domain using model files, variable properties files, and application archive files.
For more information about the model format
and its integration,
see [Usage]({{< relref "/managing-domains/domain-on-pv/usage.md" >}})
and [Working with WDT Model files]({{< relref "/managing-domains/domain-on-pv/model-files.md" >}}).
The WDT model format is fully described in the open source,
[WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) GitHub project.

### Runtime behavior

When you deploy a Domain on PV domain resource YAML file:

- The operator will run a Kubernetes Job, called an introspector job, that:
    - Merges your WDT model files.
    - Runs the WDT Create Domain Tool to create a domain home.

- After the introspector job completes:
    - The operator creates one or more ConfigMaps following the pattern `DOMAIN_UID-weblogic-domain-introspect-cm***`.
    - The operator subsequently boots your domain's WebLogic Server pods.

### Runtime updates

You control runtime updates to the WebLogic domain configuration using tools, such as Fusion Middleware Control, product-specific WLST functions,
the WebLogic Server Administration Console, the WebLogic Remote Console, or JDeveloper.  After the initial domain is created, subsequent updates to the
source of the WDT model files or any referenced macros _will be ignored_.  

Some changes may require triggering an introspector job.  For example:

- After you change the WebLogic domain credential in the WebLogic Server Administration Console, in the domain resource YAML file, you must:

  - Update the credentials in `domain.spec.webLogicCredentialsSecret`.
  - Update the value of `domain.spec.introspectVersion`.

- If you change any WebLogic domain topology, such as using the WebLogic Server Administration Console to add clusters or servers, you must:

  - Update the value of `domain.spec.introspectVersion` in the domain resource YAML file.
  - Optionally, if you want to fine tune their life cycle or replica counts, then update the domain resource YAML file to add the new clusters or servers.

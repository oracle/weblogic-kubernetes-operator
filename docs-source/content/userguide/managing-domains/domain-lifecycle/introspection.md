---
title: "Rerunning introspection"
date: 2020-06-04T08:14:51-05:00
draft: true
weight: 5
description: "This document describes rerunning the introspector job in the Oracle WebLogic Server in Kubernetes environment."
---


This document describes _how and when_ to rerun the introspector job in the Oracle WebLogic Server in Kubernetes environment.

#### Overview

In order to manage a domain, the operator needs to know its definition in WebLogic Server. It does this by running an _introspector job_
on the domain definition when the domain is first deployed in the operator. If the configuration is modified, the operator must rerun
the introspector job. The exact behavior will depend on the model chosen, and the detected change. Often the running
domain can be updated without restarting any servers. Currently, this is not supported for 
the Model in Image [domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

#### Common re-introspection scenarios

This document describes what actions allow avoiding the restart of servers in a number of common re-introspection scenarios:

* Modifying the WebLogic configuration
* Changing the custom domain configuration overrides (also called situational configuration) for Domain in PV and Domain in Image domains

### Use cases

#### Modifying the WebLogic Server configuration

Changes to the Oracle WebLogic Server configuration may not require a full restart, depending on the domain home location 
and the type of configuration change.

* **Domain in PV:**
For a domain home on PV, it is often possible to avoid a full restart when making a dynamic change.
    * Configuration changes that will not restart any servers:
      * Adding a cluster, server or dynamic server
      * Changing a dynamic property such as a server's connection timeout property
    * Configuration changes that may shut down or restart some servers:  
      * Removing a cluster, server, dynamic server, or network access point
      * Changing a cluster, server, dynamic server, or network access point name
      * Enabling or disabling the listen port, SSL port, or admin port
      * Changing any port numbers
      * Changing a network access point's public address

#### Changing the custom domain configuration overrides

Changes to configuration overrides may be adopted without restarting the domain. These changes are made by:
  * Changing the domain resource's `configuration.overridesConfigMap` to point to a different configuration map
  * Changing the domain resource's `configuration.secrets` to point to a different list of secrets
  * Changing the contents of the configuration map referenced by `configuration.overridesConfigMap`
  * Changing the contents to any of the secrets referenced by `configuration.secrets`
  
The running servers will, however, only see the actual changes when restarted.  
<!-- Note: this will change when the "Implement distribution strategy for config override changes" feature is merged. --> 

### Forcing the operator to rerun the introspector job

The operator will run the introspector job when the domain specification field `introspectVersion` is changed. To
edit this field in the domain resource directly using the `kubectl` command-line tool:

```
kubectl edit domain <domain name> -n <domain namespace>
```

The `edit` command opens a text editor which lets you edit the domain resource in place.

{{% notice note %}}
Typically, it's better to edit the domain resource directly; otherwise, if you scaled the domain, and you edit only the original `domain.yaml` file and reapply it, you could go back to your old replicas count.
{{% /notice %}}

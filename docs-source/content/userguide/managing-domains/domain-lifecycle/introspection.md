---
title: "Rerunning introspection"
date: 2020-06-04T08:14:51-05:00
draft: true
weight: 5
description: "This document describes rerunning the introspector job in the Oracle WebLogic Server in Kubernetes environment."
---


This document describes _how and when_ to rerun the introspector job in the Oracle WebLogic Server in Kubernetes environment.

#### Overview

In order to manage a domain, the operator needs to examine the domain configuration in WebLogic Server. It does this by running an _introspector job_
on the domain definition when the domain is first deployed in the operator. If the configuration is modified,
the introspector job must be run again. The exact behavior will depend on the model chosen, and the detected change. Often the running
domain can be updated without restarting any servers. Currently, this is not supported for 
the Model in Image [domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

#### Common re-introspection scenarios

This document describes what actions allow avoiding the restart of servers in a number of common re-introspection scenarios:

* Modifying the WebLogic configuration for Domain in PV domains.
* Changing the custom domain configuration overrides (also called situational configuration) for Domain in PV and Domain in Image domains

### Use cases

#### Supported dynamic configuration changes

Changes to the Oracle WebLogic Server configuration may not require a full restart, depending on the domain home location 
and the type of configuration change.

* **Domain in PV:**
For a domain home on PV, it is often possible to avoid a full restart when making a dynamic change.
    * Dynamic configuration changes that do not require a full restart:
      * Adding a cluster, server, or dynamic server
      * Changing a dynamic property such as a server's connection timeout property
    * Dynamic configuration changes that require shutting down or restarting servers.:  
      * Removing a cluster, server, dynamic server, or network access point
      * Changing a cluster, server, dynamic server, or network access point name
      * Enabling or disabling the listen port, SSL port, or admin port
      * Changing any port numbers
      * Changing a network access point's public address

#### Dynamically changing custom domain configuration overrides

Dynamic changes to configuration overrides may be adopted without restarting the domain. These changes are made by:
  * Changing the domain resource's `configuration.overridesConfigMap` to point to a different configuration map or 
  deploying a changed configuration map with the same name as the original configuration map
  * Changing a domain resource's `configuration.secrets` to point to a different list of secrets
  * Changing the contents of the configuration map referenced by `configuration.overridesConfigMap`
  * Changing the contents to any of the secrets referenced by `configuration.secrets`
  
Currently running WebLogic Server pods will, however, only see the actual changes when restarted.  
<!-- Note: this will change when the "Implement distribution strategy for config override changes" feature is merged. --> 

### Forcing the operator to rerun the introspector job

The operator will run the introspector job when the domain specification field `introspectVersion` is changed.  Here are some common ways to do this:

 - You can alter `introspectVersion` interactively using `kubectl edit -n MY_NAMESPACE domain MY_DOMAINUID`.

 - If you have your domain's resource file, then you can alter this file and call `kubectl apply -f` on the file.

 - You can use the Kubernetes `get` and `patch` commands. Here's a sample automation script that takes a namespace as the first parameter (default `sample-domain1-ns`) and that takes a domainUID as the second parameter (default `sample-domain1`):

   ```
   #!/bin/bash
   NAMESPACE=${1:-sample-domain1-ns}
   DOMAINUID=${2:-sample-domain1}
   currentIV=$(kubectl -n ${NAMESPACE} get domain ${DOMAINUID} -o=jsonpath='{.spec.introspectVersion}')
   if [ $? = 0 ]; then
     # we enter here only if the previous command succeeded

     nextIV=$((currentIV + 1))

     echo "@@ Info: Introspecting domain '${DOMAINUID}' in namespace '${NAMESPACE}' by changing introspectVersion='${currentIV}' to introspectVersion='${nextIV}'."

     kubectl -n ${NAMESPACE} patch domain ${DOMAINUID} --type='json' \
       -p='[{"op": "replace", "path": "/spec/introspectVersion", "value": "'${nextIV}'" }]'
   fi
   ```
 

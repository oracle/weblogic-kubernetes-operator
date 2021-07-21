---
title: "Domain lifecycle operations"
date: 2019-02-23T17:32:31-05:00
weight: 8
description: "Start and stop Managed Servers, clusters, and domains."
---

#### Domain lifecycle sample scripts

Beginning in version 3.1.0, the operator provides sample scripts to start up or shut down a specific Managed Server or cluster in a deployed domain, or the entire deployed domain. Beginning in version 3.2.0, additional scripts are provided for scaling a WebLogic cluster, displaying the WebLogic cluster status, initiating rolling restart of a domain or a WebLogic cluster, and initiating explicit introspection of a domain.

**Note**: Prior to running these scripts, you must have previously created and deployed the domain.

The scripts are located in the `kubernetes/samples/scripts/domain-lifecycle` directory. They are helpful when scripting the life cycle of a WebLogic Server domain. For more information, see the [README](https://github.com/oracle/weblogic-kubernetes-operator/tree/main/kubernetes/samples/scripts/domain-lifecycle/README.md).

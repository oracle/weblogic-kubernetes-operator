---
title: "Lifecycle scripts"
date: 2021-12-05T17:04:41-05:00
draft: false
weight: 8
description: "A collection of useful domain lifecycle sample scripts."
---

Beginning in version 3.1.0,
the operator provides sample scripts to start up
or shut down a specific Managed Server or cluster in a deployed domain,
or the entire deployed domain.

Versions 3.2 and 3.3 have subsequently added sample scripts for
restarting a server,
scaling a cluster,
rolling a domain or a cluster,
monitoring a cluster,
and reinitiating introspection.

The scripts are located in the `kubernetes/samples/scripts/domain-lifecycle` directory.
They are helpful when scripting the life cycle of a WebLogic Server domain.

For more information,
see the [README](https://github.com/oracle/weblogic-kubernetes-operator/tree/{{< latestMinorVersion >}}/kubernetes/samples/scripts/domain-lifecycle/README.md).

**NOTE**: Prior to running these scripts, you must have previously created and deployed the domain.

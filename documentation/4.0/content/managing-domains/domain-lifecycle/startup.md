---
title: "Startup and shutdown"
date: 2019-02-23T17:04:41-05:00
draft: false
weight: 1
description: "There are fields on the Domain that specify which WebLogic Server instances should be running,
started, or restarted. To start, stop, or restart servers, modify these fields on the Domain."
---

This document describes approaches for stopping, starting, rolling, and restarting WebLogic Server instances in a Kubernetes environment.

{{< table_of_contents >}}

### Introduction

There are fields on the Domain and the Cluster that specify which servers should be running,
which servers should be restarted, and the desired initial state. To start, stop, or restart servers, modify these fields on the Domain or the Cluster
(for example, by using `kubectl` or the Kubernetes REST API).  The operator will detect the changes and apply them.

### Starting and stopping servers

The `serverStartPolicy` and `replicas` fields of the Domain and the Cluster control which servers should be running, where a Cluster `replicas` field defaults to the corresponding Domain value.
The operator monitors these fields and creates or deletes the corresponding WebLogic Server instance Pods.

{{% notice note %}} Do not use the WebLogic Server Administration Console to start or stop servers.
{{% /notice %}}

#### `serverStartPolicy` rules

You can specify the `serverStartPolicy` property at the `domain.spec` Domain level,
the `cluster.spec` Cluster level,
the `domain.spec.managedServers` Managed Server level,
or the `domain.spec.adminServer` Administration Server level.
Each level supports a different set of values.

#### Available `serverStartPolicy` values
| Level | Default Value | Supported Values                 |
| --- |---------------|----------------------------------|
| Domain | `IfNeeded`    | `IfNeeded`, `AdminOnly`, `Never` |
| Cluster | `IfNeeded`   | `IfNeeded`, `Never`              |
| Server | `IfNeeded`   | `IfNeeded`, `Always`, `Never`    |

#### Administration Server start and stop rules
| Domain | Admin Server | Started / Stopped |
| --- | --- | --- |
| `Never` | any value | Stopped |
| `AdminOnly`, `IfNeeded` | `Never` | Stopped |
| `AdminOnly`, `IfNeeded` | `IfNeeded`, `Always` | Started |

#### Standalone Managed Server start and stop rules
| Domain | Standalone Server | Started / Stopped |
| --- | --- | --- |
| `AdminOnly`, `Never` | any value | Stopped |
| `IfNeeded` | `Never` | Stopped |
| `IfNeeded` | `IfNeeded`, `Always` | Started |

#### Clustered Managed Server start and stop rules
| Domain | Cluster | Clustered Server | Started / Stopped |
| --- | --- | --- | --- |
| `AdminOnly`, `Never` | any value | any value | Stopped |
| `IfNeeded` | `Never` | any value | Stopped |
| `IfNeeded` | `IfNeeded` | `Never` | Stopped |
| `IfNeeded` | `IfNeeded` | `Always` | Started |
| `IfNeeded` | `IfNeeded` | `IfNeeded` | Started if needed to get to the cluster's `replicas` count |

{{% notice note %}}
Servers configured as `Always` count toward the cluster's `replicas` count.
{{% /notice %}}

{{% notice note %}}
If more servers are configured as `Always` than the cluster's `replicas` count, they will all be started and the `replicas` count will be exceeded.
{{% /notice %}}

### Common starting and stopping scenarios

#### Normal running state
Normally, the Administration Server, all of the standalone Managed Servers, and enough Managed Servers members in each cluster to satisfy its `replicas` count, should be started.
In this case, the Domain does not need to specify `serverStartPolicy`, or list any `clusters` or `servers`, but it does need to specify a `replicas` count.

For example:
```
  kind: Domain
  metadata:
    name: domain1
  spec:
    image: ...
    replicas: 3
```

The `domain.spec.replicas` field is the default for all clusters. Individual clusters
may customize their replicas using `cluster.spec.replicas`.

#### Shut down all the servers
Sometimes you need to completely shut down the domain (for example, take it out of service).
```
  kind: Domain
  metadata:
    name: domain1
  spec:
    serverStartPolicy: Never
    ...
```

#### Only start the Administration Server
Sometimes you want to start the Administration Server only, that is, take the Managed Servers out of service but leave the Administration Server running so that you can administer the domain.
```
  kind: Domain
  metadata:
    name: domain1
  spec:
    serverStartPolicy: AdminOnly
    ...
```

#### Shut down a cluster
To shut down a cluster (for example, take it out of service), add it to the Domain and set its `serverStartPolicy` to `Never`.
```
  kind: Cluster
  metadata:
    name: domain1-cluster1
  spec:
    clusterName: "cluster1"
    serverStartPolicy: Never
    ...
```

A Cluster resource must be referenced from the `domain.spec.clusters` and must have a `.spec.clusterName`
that matches the corresponding cluster in the domain's WebLogic configuration.

#### Shut down a specific standalone server
To shut down a specific standalone server, add it to the Domain and set its `serverStartPolicy` to `Never`.
```
  kind: Domain
  metadata:
    name: domain1
  spec:
    managedServers:
    - serverName: "server1"
      serverStartPolicy: Never
    ...
```
{{% notice note %}}
The Administration Server can be shut down by setting the `serverStartPolicy` of the `adminServer` to `Never`.
Care should be taken when shutting down the Administration Server. If a Managed Server cannot connect
to the Administration Server during startup, it will try to start up in
[*Managed Server Independence (MSI)* mode](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/start/failures.html#GUID-CA4696B6-B462-4FD8-92A9-F27DEA8A2E87)
but this could fail due to reasons such as no accessible
[*Authentication Provider*](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/secmg/atn_intro.html#GUID-E56E30B4-5C18-4A21-A683-AC166792A9DE)
from the Managed Server pod.
{{% /notice %}}


#### Force a specific clustered Managed Server to start
Normally, all of the Managed Servers members in a cluster are identical and it doesn't matter which ones are running as long as the operator starts enough of them to get to the cluster's `replicas` count.
However, sometimes some of the Managed Servers are different (for example, support some extra services that the other servers in the cluster use) and need to always be started.

This is done by adding the server to the Domain and setting its `serverStartPolicy` to `Always`.
```
  kind: Domain
  metadata:
    name: domain1
  spec:
    managedServers:
    - serverName: "cluster1_server1"
      serverStartPolicy: Always
    ...
```

{{% notice note %}}
The server will count toward the cluster's `replicas` count.  Also, if you configure more than the `replicas` servers count to `Always`, they will all be started, even though the `replicas` count will be exceeded.
{{%/ notice %}}

### Shutdown options

The Domain and Cluster YAML files include the field `serverPod` that is available under
`domain.spec`, `domain.adminServer`, each entry of `domain.spec.managedServers`,
and `cluster.spec`.
The `serverPod` field controls many details of how Pods are generated for WebLogic Server instances.

The `shutdown` field of `serverPod` controls how managed servers will be shut down and has the following four properties:
`shutdownType`, `timeoutSeconds`, `ignoreSessions` and `waitForAllSessions`. The operator runtime monitors these properties but will not restart any server pods solely to adjust the shutdown options.
Instead, server pods created or restarted because of another property change will be configured to shutdown, at the appropriate
time, using the shutdown options set when the WebLogic Server instance Pod is created.

| Field| Default Value | Supported Values | Description |
| --- | --- | --- | --- |
| `shutdownType` | `Graceful` | `Graceful` or `Forced` | Specifies how the operator will shut down server instances. |
| `timeoutSeconds` | 30 | Whole number in seconds where 0 means no timeout. | For graceful shutdown only, number of seconds to wait before aborting in-flight work and shutting down the server. |
| `ignoreSessions` | `false` | `true` or `false` | Boolean indicating if active sessions should be ignored; only applicable if shutdown is graceful. |
| `waitForAllSessions` | `false` | `true` or `false` | For graceful shutdown only, set to `true` to wait for all HTTP sessions during in-flight work handling; `false` to wait for non-persisted HTTP sessions only during in-flight work handling. |

{{% notice note %}}
The `waitForAllSessions` property does not apply when the `ignoreSessions` property is `true`. When the
`ignoreSessions` property is `false` then `waitForAllSessions` property is taken into account during
the WebLogic graceful shutdown process. When the`waitForAllSessions` is `true`, the graceful shutdown
process will wait for all HTTP sessions to complete or be invalidated before proceeding. When `waitForAllSessions`
is `false`, the graceful shutdown process will wait only for non-persisted HTTP sessions to complete
or be invalidated before proceeding.
{{% /notice %}}

#### Shutdown environment variables

The operator configures shutdown behavior with the use of the following environment variables. Users may
instead simply configure these environment variables directly.  When a user-configured environment variable is present,
the operator will not override the environment variable based on the shutdown configuration.

| Environment Variables | Default Value | Supported Values |
| --- | --- | --- |
| `SHUTDOWN_TYPE` | `Graceful` | `Graceful` or `Forced` |
| `SHUTDOWN_TIMEOUT` | 30 | Whole number in seconds where 0 means no timeout |
| `SHUTDOWN_IGNORE_SESSIONS` | `false` | Boolean indicating if active sessions should be ignored; only applicable if shutdown is graceful |
| `SHUTDOWN_WAIT_FOR_ALL_SESSIONS` | `false` | `true` to wait for all HTTP sessions during in-flight work handling; `false` to wait for non-persisted HTTP sessions only ; only applicable if shutdown is graceful |


#### `shutdown` rules

You can specify the `serverPod` field, including the `shutdown` field, at the domain, cluster, and server levels. If
`shutdown` is specified at multiple levels, such as for a cluster and for a member server that is part of that cluster,
then the shutdown configuration for a specific server is the combination of all of the relevant values with each field
having the value from the `shutdown` field at the most specific scope.  

For instance, given the following Domain YAML and Cluster YAML files:
```
  kind: Domain
  metadata:
    name: domain1
  spec:
    serverPod:
      shutdown:
        shutdownType: Graceful
        timeoutSeconds: 45
    clusters:
    - name: "domain1-cluster1"
    managedServers:
    - serverName: "cluster1_server1"
      serverPod:
        shutdown:
          timeoutSeconds: 60
          ignoreSessions: false
    ...
```

```
  kind: Cluster
  metadata:
    name: domain1-cluster1
    labels:
      weblogic.domainUID: sample-domain1
  spec:
    clusterName: cluster1
    replicas: 2
    serverPod:
      shutdown:
        ignoreSessions: true
```

Graceful shutdown is used for all servers in the domain because this is specified at the domain level and is not overridden at
any cluster or server level.  The "cluster1" cluster defaults to ignoring sessions; however, the "cluster1_server1" server
instance will not ignore sessions and will have a longer timeout.

### Restarting servers

The operator automatically recreates (restarts) WebLogic Server instance Pods when fields on the Domain that affect Pod generation change (such as `image`, `volumes`, and `env`).
The `restartVersion` field on the Domain lets you force the operator to restart a set of WebLogic Server instance Pods.

The operator does rolling restarts of clustered servers so that service is maintained.

#### Fields that cause servers to be restarted

The operator will restart servers when any of the follow fields on the Domain that affect the WebLogic Server instance Pod generation are changed:

* `auxiliaryImages`
* `auxiliaryImageVolumes`
* `containerSecurityContext`
* `domainHome`
* `domainHomeSourceType`
* `env`
* `image`
* `imagePullPolicy`
* `imagePullSecrets`
* `includeServerOutInPodLog`
* `logHomeEnabled`
* `logHome`
* `livenessProbe`
* `nodeSelector`
* `podSecurityContext`
* `readinessProbe`
* `resources`
* `restartVersion`
* `volumes`
* `volumeMounts`

For Model in Image, a change to the `introspectVersion` field, which causes the operator to initiate a new [introspection]({{< relref "/managing-domains/domain-lifecycle/introspection.md" >}}), will result in the restarting of servers if the introspection results in the generation of a modified WebLogic domain home. See the documentation on Model in Image [runtime updates]({{< relref "/managing-domains/model-in-image/runtime-updates.md" >}}) for a description of changes to the model or associated resources, such as Secrets, that will cause the generation of a modified WebLogic domain home.

{{% notice note %}}
If the only change detected is the addition or modification of a customer-specified label or annotation,
the operator will *patch* the Pod rather than restarting it. Removing a label or annotation from
the Domain will cause neither a restart nor a patch. It is possible to force a restart to remove
such a label or annotation by modifying the `restartVersion`.
{{% /notice %}}


### Rolling restarts

Clustered servers that need to be restarted are gradually restarted (for example, "rolling restarted") so that the cluster is not taken out of service and in-flight work can be migrated to other servers in the cluster.

The `maxUnavailable` field on the Cluster determines how many of the cluster's servers may be taken out of service at a time when doing a rolling restart.
It can be specified at the cluster level and defaults to 1 (that is, by default, clustered servers are restarted one at a time).

When using in-memory session replication, Oracle WebLogic Server employs a primary-secondary session replication model to provide high availability of application session state (that is, HTTP and EJB sessions).
The primary server creates a primary session state on the server to which the client first connects, and a secondary replica on another WebLogic Server instance in the cluster.
Specifying a `maxUnavailable` property value of `1` protects against inadvertent session state loss which could occur if both the primary and secondary
servers are shut down at the same time during the rolling restart process.

{{% notice note %}}
If you are supplying updated models or secrets for a running Model in Image domain, and you want the configuration updates to take effect using a rolling restart, consult [Modifying WebLogic Configuration]({{< relref "/managing-domains/domain-lifecycle/restarting/_index.md#modifying-the-weblogic-domain-configuration" >}}) and [Runtime updates]({{< relref "/managing-domains/model-in-image/runtime-updates.md" >}}) before consulting this document.
{{% /notice %}}

### Draining a node and PodDisruptionBudget

A Kubernetes cluster administrator can [drain a Node](https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/) for repair, upgrade, or scaling down the Kubernetes cluster.

Beginning in version 3.2, the operator takes advantage of the [PodDisruptionBudget](https://kubernetes.io/docs/concepts/workloads/pods/disruptions/#pod-disruption-budgets) feature offered by Kubernetes for high availability during a Node drain operation. The operator creates a PodDisruptionBudget (PDB) for each WebLogic cluster in the Domain namespace to limit the number of WebLogic Server pods simultaneously evicted when draining a node. The maximum number of WebLogic cluster's server pods evicted simultaneously is determined by the `maxUnavailable` field on the Cluster resource. The `.spec.minAvailable` field of the PDB for a cluster is calculated from the difference of the current `replicas` count and `maxUnavailable` value configured for the cluster. For example, if you have a WebLogic cluster with three replicas and a `maxUnavailable` of `1`, the `.spec.minAvailable` for PDB is set to `2`. In this case, Kubernetes ensures that at least two pods for the WebLogic cluster's Managed Servers are available at any given time, and it only evicts a pod when all three pods are ready. For details about safely draining a node and the PodDisruptionBudget concept, see [Safely Drain a Node](https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/) and [PodDisruptionBudget](https://kubernetes.io/docs/concepts/workloads/pods/disruptions/).

### Common restarting scenarios

#### Using `restartVersion` to force the operator to restart servers

The `restartVersion` property lets you force the operator to restart servers.

Each time you want to restart some servers, you need to set `restartVersion` to a different value. The specific value does not matter so most customers use whole number values.

The operator will detect the new value and restart the affected servers (using the same mechanisms as when other fields that affect the WebLogic Server instance Pod generation are changed, including doing rolling restarts of clustered servers).

The `restartVersion` property can be specified at the domain, cluster, and server levels.  A server will be restarted if any of these three values change.

{{% notice note %}}
The servers will also be restarted if `restartVersion` is removed from the Domain (for example, if you had previously specified a value to cause a restart, then you remove that value after the previous restart has completed).
{{% /notice %}}


#### Restart all the servers in the domain

Set `restartVersion` at the domain level to a new value.

```
  kind: Domain
  metadata:
    name: domain1
  spec:
    restartVersion: "5"
    ...
```

#### Restart all the servers in the cluster

Set `restartVersion` at the cluster level to a new value.

```
  kind: Cluster
  metadata:
    name: domain1-cluster1
  spec:
    clusterName : "cluster1"
    restartVersion: "5"
    maxUnavailable: 2
    ...
```

A Cluster resource must be referenced from the `domain.spec.clusters` and must have a `.spec.clusterName`
that matches the corresponding cluster in the domain's WebLogic configuration.

#### Restart the Administration Server

Set `restartVersion` at the `adminServer` level to a new value.

```
  kind: Domain
  metadata:
    name: domain1
  spec:
    adminServer:
      restartVersion: "5"
    ...
```

#### Restart a standalone or clustered Managed Server

Set `restartVersion` at the `managedServer` level to a new value.

```
  kind: Domain
  metadata:
    name: domain1
  spec:
    managedServers:
    - serverName: "standalone_server1"
      restartVersion: "1"
    - serverName: "cluster1_server1"
      restartVersion: "2"
    ...
```
#### Full domain restarts

To do a full domain restart, first shut down all servers (Administration Server and Managed Servers), taking the domain out of service,
then restart them.  Unlike rolling restarts, the operator cannot detect and initiate a full domain restart; you must always manually initiate it.

To manually initiate a full domain restart:

1. Change the domain-level `serverStartPolicy` on the Domain to `Never`.
```
  kind: Domain
  metadata:
    name: domain1
  spec:
    serverStartPolicy: Never
    ...
```

2. Wait for the operator to stop ALL the servers for that domain.

3. To restart the domain, set the domain level `serverStartPolicy` back to `IfNeeded`. Alternatively, you do not
have to specify the `serverStartPolicy` as the default value is `IfNeeded`.

```
  kind: Domain
  metadata:
    name: domain1
  spec:
    serverStartPolicy: IfNeeded
    ...
```

4. The operator will restart all the servers in the domain.

### Domain lifecycle sample scripts

See the [Lifecycle sample scripts]({{< relref "/managing-domains/domain-lifecycle/scripts.md" >}})
for scripts that help with initiating domain lifecycle operations.

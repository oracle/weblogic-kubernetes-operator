# Starting, Stopping and Restarting Servers

This document describes how the user can start, stop and restart the domain's servers.

## Overview

There are properties on the domain resource that specify which servers should be running
and which servers should be restarted.

To start, stop or restart servers, the should user modify these properties on the domain resource
(e.g. using kubectl or the Kubernetes REST api).  The operator will notice the changes
and apply them.

## Starting and Stopping Servers

The "serverStartPolicy" property on domain resource controls which servers should be running.
The operator runtime monitors this property and creates or deletes the corresponding server pods.

### "serverStartPolicy" Rules

"serverStartPolicy" can be specified at the domain, cluster and server levels. Each level supports a different set of values:

#### Available serverStartPolicy Values
| Level | Default Value | Supported Values |
| --- | --- | --- |
| Domain | IF_NEEDED | IF_NEEDED, ADMIN_ONLY, NEVER |
| Cluster | IF_NEEDED | IF_NEEDED, NEVER |
| Server | IF_NEEDED | IF_NEEDED, ALWAYS, NEVER |

#### Admin Server Start/Stop Rules
| Domain | Admin Server | Started / Stopped |
| --- | --- | --- |
| NEVER | any value | Stopped |
| ADMIN_ONLY, IF_NEEDED | NEVER | Stopped |
| ADMIN_ONLY, IF_NEEDED | IF_NEEDED, ALWAYS | Started |

#### Standalone Managed Server Start/Stop Rules
| Domain | Standalone Server | Started / Stopped |
| --- | --- | --- |
| ADMIN_ONLY, NEVER | any value | Stopped |
| IF_NEEDED | NEVER | Stopped |
| IF_NEEDED | IF_NEEDED, ALWAYS | Started |

#### Clustered Managed Server Start/Stop Rules
| Domain | Cluster | Clustered Server | Started / Stopped |
| --- | --- | --- | --- |
| ADMIN_ONLY, any value | any value | any value | Stopped |
| IF_NEEDED | NEVER | any value | Stopped |
| IF_NEEDED | IF_NEEDED | NEVER | Stopped |
| IF_NEEDED | IF_NEEDED | ALWAYS | Started |
| IF_NEEDED | IF_NEEDED | IF_NEEDED | Started if needed to get to the cluster's "replicas" count |

Note: servers configured as ALWAYS count towards the cluster's "replicas" count.

Note: if more servers are configured as ALWAYS than the cluster's "replicas" count, they will all be started and the "replicas" count will be ignored.

### Common Scenarios

#### Normal Running State
Normally, the admin server, all of the stand alone managed servers, and enough managed servers in each cluster to satisfy its "replicas" count should be started.
In this case, the domain resource does not need to specify "serverStartPolicy", or list any "clusters" or "servers", but it does need to specify a "replicas" count.

For example:
```
   domain:
     spec:
       image: ...
       replicas: 10
```

#### Shut Down All the Servers
Sometimes the user needs to completely shut down the domain (i.e. take it out of service).
```
   domain:
     spec:
       serverStartPolicy: "NEVER"
       ...
```

#### Only Start the Admin Server
Sometimes the user wants to only start the admin server, that is, take the domain out of service but leave the admin server running so that the user can administer the domain.
```
   domain:
     spec:
       serverStartPolicy: "ADMIN_ONLY"
       ...
```

#### Shut Down A Cluster
To shut down a cluster (i.e. take it out of service), add it to the domain resource and set its "serverStartPolicy" to "NEVER".
```
   domain:
     spec:
       clusters:
       - clusterName: "cluster1"
         serverStartPolicy: "NEVER"
       ...
```

#### Shut Down a Specific Stand Alone Server
To shut down a specific stand alone server, add it to the domain resource and set its "serverStartPolicy" to "NEVER"
```
   domain:
     spec:
       managedServers:
       - serverName: "server1"
         serverStartPolicy: "NEVER"
       ...
```

#### Force a Specific Clustered Managed Server To Start
Normally, all of the managed servers in a cluster are identical and it doesn't matter which ones are running as long as the operator starts enough to get to the cluster's "replicas" count.
However, sometimes some of the managed servers are different (e.g support some extra services that the other servers in the cluster use) and need to always to started.

This is done by adding the server to the domain resource and settings its "serverStartPolicy" to "ALWAYS".
```
   domain:
     spec:
       managedServers:
       - serverName: "cluster1_server1"
         serverStartPolicy: "ALWAYS"
       ...
```

Note: the server will count towards the cluster's "replicas" count.  Also, if the user configures more than "replicas" servers to "ALWAYS", they will all be started, even though "replicas" will be exceeded.

## Restarting Servers

The operator runtime automatically recreates (restarts) server pods when properties on the domain resource that affect server pods change (such as "image", "volumes" and "env").
The "restartVersion" property on the domain resource lets the user force the operator to restart a set of server pods.

The operator runtime does rolling restarts of clustered servers so that service is maintained.

### Properties that Cause Servers To Be Restarted
The operator will restart servers when any of the follow properties on the domain resource that affect the server are changed:
* annotations
* containerSecurityContext
* domainHome
* domainHomeInImage
* env
* image
* imagePullPolicy
* imagePullSecrets
* includeServerOutInPodLog
* labels
* logHomeEnabled
* logHome
* livenessProbe
* nodeSelector
* podSecurityContext
* readinessProbe
* resources
* restartVersion
* serverStartState
* volumes
* volumeMounts

TBD - do we need to include this table?  Also, Russ is still working on implementing this feature for 2.0.

### Rolling Restarts

Clustered servers that need to be restarted are gradually restarted (i.e. 'rolling restarted') so that the cluster is not taken out of service and in-flight work can be migrated to other servers in the cluster.

The "maxUnavailable" property on the domain resource determines how many of the cluster's servers may be taken out of service at a time when doing a rolling restart.
It can be specified at the domain and cluster levels and defaults to 1 (that is, by default, clustered servers are restarted one at a time).

### Using restartVersion to Force the Operator to Restart Servers

The "restartVersion" property lets users force the operator to restart servers.

It's basically a user-specified string that gets added to new server pods (as a label) so that the operator can tell which servers need to be restarted.
If the value is different, then the server pod is old and needs to be restarted.  If the value matches, then the server pod has already been restarted.

Each time the user wants to restart some servers, the user needs to set "restartVersion" to a different string (the particular value doesn't matter).

The operator will notice the new value and restart the affected servers (using the same mechanisms as when other properties that affect the server pods are changed, including doing rolling restarts of clustered servers).

"restartVersion" can be specified at the domain, cluster and server levels.  A server will be restarted if any of these three values change.

Note: the servers will also be restarted if restartVersion is removed from the domain resource (for example, if the user had previously specified a value to cause a restart then the user removes that value after the previous restart has completed).

### Common Scenarios

#### Restart All the Servers in the Domain

Set "restartVersion" at the domain level to a new value.

```
   domain:
     spec:
       restartVersion: "domainV1"
       ...
```

#### Restart All the Servers in the Cluster

Set "restartVersion" at the cluster level to a new value.

```
   domain:
     spec:
       clusters:
       - clusterName : "cluster1"
         restartVersion: "cluster1V1"
         maxUnavailable: 2
       ...
```

#### Restart the Admin Server

Set "restartVersion" at the adminServer level to a new value.

```
   domain:
     spec:
       adminServer:
         restartVersion: "adminV1"
       ...
```

#### Restart a Standalone or Clustered Manged Server

Set "restartVersion" at the managedServer level to a new value.

```
   domain:
     spec:
       managedServers:
       - serverName: "standalone_server1"
         restartVersion: "v1"
       - serverName: "cluster1_server1"
         restartVersion: "v1"
       ...
```

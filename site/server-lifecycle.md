# Starting, Stopping and Restarting Servers

This document describes how the user can start, stop and restart the domain's servers.

## Overview

There are properties on the domain resource that specify which servers should be running
and which servers should be restarted.

To start, stop or restart servers, the should user modify these properties on the domain resource
(e.g. using kubectl or the Kubernetes REST api).  The operator will notice the changes
and apply them.

### Starting and Stopping Servers

The "serverStartPolicy" property on domain resource controls which servers should be running.
The operator runtime monitors this property and creates or deletes the corresponding server pods.

### Restarting Servers

The operator runtime automatically recreates (restarts) server pods when properties on the domain resource that affect server pods change (such as "image", "volumes" and "env").
The "restartVersion" property on the domain resource lets the user force the operator to restart a set of server pods.

The operator runtime does rolling restarts of clustered servers so that service is maintained.
The "maxUnavailable" property determines how many of the cluster's servers may be taken out of service at a time when doing a rolling restart.
By default, the servers are restarted one at a time.

## Starting and Stopping Servers

The "serverStartPolicy" property determine which servers should be running.

### "serverStartPolicy" Rules

"serverStartPolicy" can be specified at the domain, cluster and server levels. Each level supports a different set of values:

#### Available serverStartPolicy Values
| Level | Default Value | Supported Values |
| --- | --- | --- |
| Domain | IF_NEEDED | IF_NEEDED, ADMIN_ONLY, NEVER |
| Cluster | IF_NEEDED | IF_NEEDED, NEVER |
| Server | IF_NEEDED | IF_NEEDED, ALWAYS, NEVER |

#### Admin Server Rules
| Domain | Admin Server | Started / Stopped |
| --- | --- | --- |
| NEVER | IF_NEEDED, ALWAYS, NEVER | Stopped |
| ADMIN_ONLY, IF_NEEDED | NEVER | Stopped |
| ADMIN_ONLY, IF_NEEDED | IF_NEEDED, ALWAYS | Started |

#### Standalone Managed Server Rules
| Domain | Standalone Server | Started / Stopped |
| --- | --- | --- |
| ADMIN_ONLY, NEVER | IF_NEEDED, ALWAYS, NEVER | Stopped |
| IF_NEEDED | NEVER | Stopped |
| IF_NEEDED | IF_NEEDED, ALWAYS | Started |

#### Clustered Managed Server Rules
| Domain | Cluster | Clustered Server | Started / Stopped |
| --- | --- | --- | --- |
| ADMIN_ONLY, NEVER | IF_NEEDED, NEVER | IF_NEEDED, ALWAYS, NEVER | Stopped |
| IF_NEEDED | NEVER | IF_NEEDED, ALWAYS, NEVER | Stopped |
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

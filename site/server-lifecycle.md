# Starting, Stopping and Restarting Servers

This document describes how the user can start, stop and restart the domain's servers.

## Overview

### Starting and Stopping Servers

The "serverStartPolicy" property on domain resource controls which servers should be running.
The operator runtime monitors this property and creates or deletes the corresponding server pods.

### Restarting Servers

The operator runtime automatically recreates (restarts) server pods when properties on the domain resource that affect server pods change (such as "image", "volumes" and "env").
The "restartVersion" property on the domain resource lets the user force the operator to restart a set of server pods.

The operator runtime does rolling restarts of clustered servers so that that service is maintained.
The "maxUnavailable" property determines how many of the cluster's servers may be taken out of service at a time when doing a rolling restart.
By default, the servers are restarted one at a time.

## Starting and Stopping Servers

### serverStartPolicy rules

The "serverStartPolicy" property determine which servers should be running.

It can be specified at the domain, cluster and server levels. Each level supports a different set of values:
* domain:  "NEVER", "IF_NEEDED", "ADMIN_ONLY", defaults to "IF_NEEDED"
* cluster: "NEVER", "IF_NEEDED", defaults to "IF_NEEDED"
* server:  "NEVER", "IF_NEEDED", "ALWAYS", defaults to "IF_NEEDED"

The admin server will be started if:
* the domain is "ADMIN_ONLY" or "IF_NEEDED" (i.e. not "NEVER")
* AND
* the adminServer is "ALWAYS" or "IF_NEEDED" (i.e. not "NEVER")

A stand alone managed server will be started if:
* the domain is "IF_NEEDED" (i.e. not "NEVER" or "ADMIN_ONLY")
* AND
* the server is "ALWAYS" or "IF_NEEDED" (i.e. not "NEVER")

A clustered managed server will be started if:
* the domain is "IF_NEEDED" (i.e. not "NEVER" or "ADMIN_ONLY")
* AND
* the cluster is "IF_NEEDED" (I.e. not "NEVER")
* AND
** the server is "ALWAYS", or if the server is "IF_NEEDED" and the operator needs to start it to get to the cluster's "replicas" count

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
The simplest was to do this is to set the domain level "serverStartPolicy" to "NEVER":
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
To shut down a cluster, add it to the domain resource and set its "serverStartPolicy" to "NEVER"
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
Normally, all of the managed servers in a cluster are identical and it doesn't matter which ones are run as long as the operator starts enough to get to the cluster's "replicas" count.
However, sometimes some of the managed servers might be different (e.g support some extra services that the other servers in the cluster use) and need to always to started.

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


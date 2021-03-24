### Domain life cycle sample scripts

The operator provides sample scripts to start up or shut down a specific Managed Server or cluster in a deployed domain, or the entire deployed domain.

**Note**: Prior to running these scripts, you must have previously created and deployed the domain. These scripts make use of [jq](https://stedolan.github.io/jq/) for processing JSON. You must have `jq 1.5 or higher` installed in order to run these scripts. See the installation options on the [jq downlod](https://stedolan.github.io/jq/download/) page.

These scripts can be helpful when scripting the life cycle of a WebLogic Server domain. For information on how to start, stop, restart, and scale WebLogic Server instances in your domain, see [Domain Life Cycle](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle).

#### Scripts to start and stop a WebLogic Server
The `startServer.sh` script starts a WebLogic Server in a domain. For clustered Managed Servers, either it increases the `spec.clusters[<cluster-name>].replicas` value for the Managed Server's cluster by `1` or updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource or both as necessary. For the Administration Server, it updates the value of the `spec.adminServer.serverStartPolicy` attribute of the domain resource. For non-clustered Managed Servers, it updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource. The script provides an option to keep the `spec.clusters[<cluster-name>].replicas` value constant for clustered servers. See the script `usage` information by using the `-h` option.

Use the following command to start the server either by increasing the replica count or by updating the server start policy:
```shell
$ startServer.sh -d domain1 -n weblogic-domain-1 -s managed-server1
[INFO] Updating replica count for cluster 'cluster-1' to 1.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully updated replica count for cluster 'cluster-1' to 1.
```

Use the following command to start the server without increasing the replica count:
```shell
$ startServer.sh -d domain1 -n weblogic-domain-1 -s managed-server2 -k
[INFO] Patching start policy for 'managed-server2' to 'ALWAYS'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched server 'managed-server2' with 'ALWAYS' start policy.
```

The `stopServer.sh` script shuts down a running WebLogic Server in a domain. For clustered Managed Servers, either it decreases the `spec.clusters[<cluster-name>].replicas` value for the Managed Server's cluster by `1` or updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource or both as necessary. For the Administration Server, it updates the value of the `spec.adminServer.serverStartPolicy` attribute of the domain resource. For non-clustered Managed Servers, it updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource. The script provides an option to keep the `spec.clusters[<cluster-name>].replicas` value constant for clustered servers. See the script `usage` information by using the `-h` option.

Use the following command to stop the server either by decreasing the replica count or by updating the server start policy:
```shell
$ stopServer.sh -d domain1 -n weblogic-domain-1 -s managed-server1
[INFO] Updating replica count for cluster cluster-1 to 0.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully updated replica count for cluster 'cluster-1' to 0.
```

Use the following command to stop the server without decreasing the replica count:
```shell
$ stopServer.sh -d domain1 -n weblogic-domain-1 -s managed-server2 -k
[INFO] Unsetting the current start policy 'ALWAYS' for 'managed-server2'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully unset policy 'ALWAYS'.
```

### Scripts to start and stop a cluster

The `startCluster.sh` script starts a cluster by patching the `spec.clusters[<cluster-name>].serverStartPolicy` attribute of the domain resource to `IF_NEEDED`. The operator will start the WebLogic Server instance Pods that are part of the cluster after the `serverStartPolicy` attribute is updated to `IF_NEEDED`. See the script `usage` information by using the `-h` option.
```shell
$ startCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1
[INFO]Patching start policy of cluster 'cluster-1' from 'NEVER' to 'IF_NEEDED'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched cluster 'cluster-1' with 'IF_NEEDED' start policy!.
```
The `stopCluster.sh` script shuts down a cluster by patching the `spec.clusters[<cluster-name>].serverStartPolicy` attribute of the domain resource to `NEVER`. The operator will shut down the WebLogic Server instance Pods that are part of the cluster after the `serverStartPolicy` attribute is updated to `NEVER`. See the script `usage` information by using the `-h` option.
```shell
$ stopCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1
[INFO] Patching start policy of cluster 'cluster-1' from 'IF_NEEDED' to 'NEVER'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched cluster 'cluster-1' with 'NEVER' start policy!
```
### Scripts to start and stop a domain
The `startDomain.sh` script starts a deployed domain by patching the `spec.serverStartPolicy` attribute of the domain resource to `IF_NEEDED`. The operator will start the WebLogic Server instance Pods that are part of the domain after the `spec.serverStartPolicy` attribute of the domain resource is updated to `IF_NEEDED`. See the script `usage` information by using the `-h` option.
```shell
$ startDomain.sh -d domain1 -n weblogic-domain-1
[INFO] Patching domain 'domain1' from serverStartPolicy='NEVER' to 'IF_NEEDED'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched domain 'domain1' in namespace 'weblogic-domain-1' with 'IF_NEEDED' start policy!
```

The `stopDomain.sh` script shuts down a domain by patching the `spec.serverStartPolicy` attribute of the domain resource to `NEVER`. The operator will shut down the WebLogic Server instance Pods that are part of the domain after the `spec.serverStartPolicy` attribute is updated to `NEVER`. See the script `usage` information by using the `-h` option.
```shell
$ stopDomain.sh -d domain1 -n weblogic-domain-1
[INFO] Patching domain 'domain1' in namespace 'weblogic-domain-1' from serverStartPolicy='IF_NEEDED' to 'NEVER'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched domain 'domain1' in namespace 'weblogic-domain-1' with 'NEVER' start policy!
```

### Script to scale a WebLogic cluster

The `scaleCluster.sh` script scales a WebLogic cluster by patching the `spec.clusters[<cluster-name>].replicas` attribute of the domain resource to the specified value. The operator will perform the scaling operation for the WebLogic cluster based on the specified value of the `replicas` attribute after its value is updated. See the script `usage` information by using the `-h` option.
```shell
$ scaleCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1 -r 3
[2021-02-26T19:04:14.335 UTC][INFO] Patching replicas for cluster 'cluster-1' to '3'.
domain.weblogic.oracle/domain1 patched
[2021-02-26T19:04:14.466 UTC][INFO] Successfully patched replicas for cluster 'cluster-1'!
```

### Script to view the status of a WebLogic cluster

The `clusterStatus.sh` script can be used to view the status of a WebLogic cluster in the WebLogic domain managed by the operator. The WebLogic Cluster Status contains information about the minimum, maximum, goal, current, and ready replica count for a WebLogic cluster. This script displays a table containing the status for WebLogic clusters in one or more domains across one or more namespaces. See the script `usage` information by using the `-h` option.

Use the following command to view the status of all WebLogic clusters in all domains across all namespaces.
```shell
$ clusterStatus.sh

WebLogic Cluster Status -n "" -d "" -c "":

namespace          domain            cluster    min  max  goal  current  ready
---------          ------            -------    ---  ---  ----  -------  -----
ns-kvmt            mii-domain1       cluster-1  1    5    5     5        5
weblogic-domain-1  domain1           cluster-1  0    4    2     2        2
weblogic-domain-1  domain1           cluster-2  0    4    0     0        0
```

Use the following command to view the status of all WebLogic clusters in 'domain1' in 'weblogic-domain-1' namespace.
```shell
$ clusterStatus.sh -d domain1 -n weblogic-domain-1

WebLogic Cluster Status -n "weblogic-domain-1" -d "domain1" -c "":

namespace          domain   cluster    min  max  goal  current  ready
---------          ------   -------    ---  ---  ----  -------  -----
weblogic-domain-1  domain1  cluster-1  0    4    2     2        2
weblogic-domain-1  domain1  cluster-2  0    4    0     0        0
```

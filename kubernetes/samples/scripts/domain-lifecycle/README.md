### Domain life cycle sample scripts

The operator provides sample scripts to start up or shut down a specific Managed Server or cluster in a deployed domain, or the entire deployed domain.

**Note**: Prior to running these scripts, you must have previously created and deployed the domain. These scripts make use of [jq](https://stedolan.github.io/jq/) for processing JSON. You must have `jq 1.5 or higher` installed in order to run these scripts. See the installation options on the [jq downlod](https://stedolan.github.io/jq/download/) page.

These scripts can be helpful when scripting the life cycle of a WebLogic Server domain. For information on how to start, stop, restart, and scale WebLogic Server instances in your domain, see [Domain Life Cycle](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle).

#### Scripts to start and stop a WebLogic Server
The `startServer.sh` script starts a WebLogic Server in a domain. For clustered Managed Servers, either it increases the `spec.clusters[<cluster-name>].replicas` value for the Managed Server's cluster by `1` or updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource or both as necessary. For the Administration Server, it updates the value of the `spec.adminServer.serverStartPolicy` attribute of the domain resource. For non-clustered Managed Servers, it updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource. The script provides an option to keep the `spec.clusters[<cluster-name>].replicas` value constant for clustered servers. See the script `usage` information by using the `-h` option.

Use the following command to start the server either by increasing the replica count or by updating the server start policy:
```
$ startServer.sh -d domain1 -n weblogic-domain-1 -s managed-server1
[INFO] Updating replica count for cluster 'cluster-1' to 1.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully updated replica count for cluster 'cluster-1' to 1.
```

Use the following command to start the server without increasing the replica count:
```
$ startServer.sh -d domain1 -n weblogic-domain-1 -s managed-server2 -k
[INFO] Patching start policy for 'managed-server2' to 'ALWAYS'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched server 'managed-server2' with 'ALWAYS' start policy.
```

The `stopServer.sh` script shuts down a running WebLogic Server in a domain. For clustered Managed Servers, either it decreases the `spec.clusters[<cluster-name>].replicas` value for the Managed Server's cluster by `1` or updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource or both as necessary. For the Administration Server, it updates the value of the `spec.adminServer.serverStartPolicy` attribute of the domain resource. For non-clustered Managed Servers, it updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource. The script provides an option to keep the `spec.clusters[<cluster-name>].replicas` value constant for clustered servers. See the script `usage` information by using the `-h` option.

Use the following command to stop the server either by decreasing the replica count or by updating the server start policy:
```
$ stopServer.sh -d domain1 -n weblogic-domain-1 -s managed-server1
[INFO] Updating replica count for cluster cluster-1 to 0.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully updated replica count for cluster 'cluster-1' to 0.
```

Use the following command to stop the server without decreasing the replica count:
```
$ stopServer.sh -d domain1 -n weblogic-domain-1 -s managed-server2 -k
[INFO] Unsetting the current start policy 'ALWAYS' for 'managed-server2'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully unset policy 'ALWAYS'.
```

### Scripts to start and stop a cluster

The `startCluster.sh` script starts a cluster by patching the `spec.clusters[<cluster-name>].serverStartPolicy` attribute of the domain resource to `IF_NEEDED`. The operator will start the WebLogic Server instance Pods that are part of the cluster after the `serverStartPolicy` attribute is updated to `IF_NEEDED`. See the script `usage` information by using the `-h` option.
```
$ startCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1
[INFO]Patching start policy of cluster 'cluster-1' from 'NEVER' to 'IF_NEEDED'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched cluster 'cluster-1' with 'IF_NEEDED' start policy!.
```
The `stopCluster.sh` script shuts down a cluster by patching the `spec.clusters[<cluster-name>].serverStartPolicy` attribute of the domain resource to `NEVER`. The operator will shut down the WebLogic Server instance Pods that are part of the cluster after the `serverStartPolicy` attribute is updated to `NEVER`. See the script `usage` information by using the `-h` option.
```
$ stopCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1
[INFO] Patching start policy of cluster 'cluster-1' from 'IF_NEEDED' to 'NEVER'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched cluster 'cluster-1' with 'NEVER' start policy!
```
### Scripts to start and stop a domain
The `startDomain.sh` script starts a deployed domain by patching the `spec.serverStartPolicy` attribute of the domain resource to `IF_NEEDED`. The operator will start the WebLogic Server instance Pods that are part of the domain after the `spec.serverStartPolicy` attribute of the domain resource is updated to `IF_NEEDED`. See the script `usage` information by using the `-h` option.
```
$ startDomain.sh -d domain1 -n weblogic-domain-1
[INFO] Patching domain 'domain1' from serverStartPolicy='NEVER' to 'IF_NEEDED'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched domain 'domain1' in namespace 'weblogic-domain-1' with 'IF_NEEDED' start policy!
```

The `stopDomain.sh` script shuts down a domain by patching the `spec.serverStartPolicy` attribute of the domain resource to `NEVER`. The operator will shut down the WebLogic Server instance Pods that are part of the domain after the `spec.serverStartPolicy` attribute is updated to `NEVER`. See the script `usage` information by using the `-h` option.
```
$ stopDomain.sh -d domain1 -n weblogic-domain-1
[INFO] Patching domain 'domain1' in namespace 'weblogic-domain-1' from serverStartPolicy='IF_NEEDED' to 'NEVER'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched domain 'domain1' in namespace 'weblogic-domain-1' with 'NEVER' start policy!
```

### Script to scale a WebLogic cluster

The `scaleCluster.sh` script scales a WebLogic cluster by patching the `spec.clusters[<cluster-name>].replicas` attribute of the domain resource to the specified value. The operator will perform the scaling operation for the WebLogic cluster based on the specified value of the `replicas` attribute after its value is updated. See the script `usage` information by using the `-h` option.
```
$ scaleCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1 -r 3
[2021-02-26T19:04:14.335000Z][INFO] Patching replicas for cluster 'cluster-1' to '3'.
domain.weblogic.oracle/domain1 patched
[2021-02-26T19:04:14.466000Z][INFO] Successfully patched replicas for cluster 'cluster-1'!
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
```
$ clusterStatus.sh -d domain1 -n weblogic-domain-1

WebLogic Cluster Status -n "weblogic-domain-1" -d "domain1" -c "":

namespace          domain   cluster    min  max  goal  current  ready
---------          ------   -------    ---  ---  ----  -------  -----
weblogic-domain-1  domain1  cluster-1  0    4    2     2        2
weblogic-domain-1  domain1  cluster-2  0    4    0     0        0
```

### Scripts to initiate a rolling restart of a WebLogic domain or cluster

The `rollDomain.sh` script can be used to initiate a rolling restart of the WebLogic Server Pods in a domain managed by the operator. Similarly, the `rollCluster.sh` script can be used to initiate a rolling restart of the WebLogic Server Pods belonging to a WebLogic cluster in a domain managed by the operator.

The `rollDomain.sh` script updates the value of the `spec.restartVersion` attribute of the domain resource.  Then, the operator will do a rolling restart of the Server Pods in the WebLogic domain after the value of the `spec.restartVersion` is updated. You can provide the new value for `spec.restartVersion` as a parameter to the script or the script will automatically generate a new value to trigger the rolling restart. See the script `usage` information by using the `-h` option.

```
$ rollDomain.sh -d domain1 -n weblogic-domain-1
[2021-03-24T04:01:19.733000Z][INFO] Patching restartVersion for domain 'domain1' to '1'.
domain.weblogic.oracle/domain1 patched
[2021-03-24T04:01:19.850000Z][INFO] Successfully patched restartVersion for domain 'domain1'!
```

Use the following command to roll the Server Pods in a WebLogic domain with a specific `restartVersion`:
```
$ rollDomain.sh -r v1 -d domain1 -n weblogic-domain-1
[2021-03-24T13:43:47.586000Z][INFO] Patching restartVersion for domain 'domain1' to 'v1'.
domain.weblogic.oracle/domain1 patched
[2021-03-24T13:43:47.708000Z][INFO] Successfully patched restartVersion for domain 'domain1'!
```

The `rollCluster.sh` script updates the value of the `spec.clusters[<cluster-name>].restartVersion` attribute of the domain resource. Then, the operator will do a rolling restart of the WebLogic cluster Server Pods after the value of the `spec.clusters[<cluster-name>].restartVersion` is updated. You can provide the new value of the `restartVersion` as a parameter to the script or the script will automatically generate a new value to trigger the rolling restart. See the script `usage` information by using the `-h` option.

```
$ rollCluster.sh -c cluster-1 -d domain1 -n weblogic-domain-1
[2021-03-24T04:03:27.521000Z][INFO] Patching restartVersion for cluster 'cluster-1' to '2'.
domain.weblogic.oracle/domain1 patched
[2021-03-24T04:03:27.669000Z][INFO] Successfully patched restartVersion for cluster 'cluster-1'!
```

Use the following command to roll the WebLogic Cluster Servers with a specific `restartVersion`:
```
$ rollCluster.sh -r v2 -c cluster-1 -d domain1 -n weblogic-domain-1
[2021-03-24T13:46:16.833000Z][INFO] Patching restartVersion for cluster 'cluster-1' to 'v2'.
domain.weblogic.oracle/domain1 patched
[2021-03-24T13:46:16.975000Z][INFO] Successfully patched restartVersion for cluster 'cluster-1'!
```

### Scripts to restart a WebLogic Server in a domain
The `restartServer.sh` script can be used to restart a WebLogic Server in a domain. This script restarts the Server by deleting the Server Pod for the WebLogic Server instance.
```
$ restartServer.sh -s managed-server1 -d domain1 -n weblogic-domain-1
[2021-03-24T22:20:22.498000Z][INFO] Initiating restart of 'managed-server1' by deleting server pod 'domain1-managed-server1'.
[2021-03-24T22:20:37.614000Z][INFO] Server restart succeeded !
```

### Scripts to explicitly initiate introspection of a WebLogic domain

The `introspectDomain.sh` script can be used to rerun a WebLogic domain's introspect job by explicitly initiating the introspection. This script updates the value of the `spec.introspectVersion` attribute of the domain resource. The resulting behavior depends on your domain home source type and other factors, see [Initiating introspection](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/introspection/#initiating-introspection) for details. You can provide the new value of the `introspectVersion` as a parameter to the script or the script will automatically generate a new value to trigger the introspection. See the script `usage` information by using the `-h` option.

Use the following command to rerun a domain's introspect job with the `introspectVersion` value generated by the script.
```
$ introspectDomain.sh -d domain1 -n weblogic-domain-1
[2021-03-24T21:37:55.989000Z][INFO] Patching introspectVersion for domain 'domain1' to '1'.
domain.weblogic.oracle/domain1 patched
[2021-03-24T21:37:56.110000Z][INFO] Successfully patched introspectVersion for domain 'domain1'!
```

Use the following command to rerun a domain's introspect job with a specific `introspectVersion` value.
```
$ introspectDomain.sh -i v1 -d domain1 -n weblogic-domain-1
[2021-03-24T21:38:34.369000Z][INFO] Patching introspectVersion for domain 'domain1' to 'v1'.
domain.weblogic.oracle/domain1 patched
[2021-03-24T21:38:34.488000Z][INFO] Successfully patched introspectVersion for domain 'domain1'!
```

### Watching the Pods after executing life cycle scripts

After executing the lifecycle scripts described above for a domain or a cluster or a Server, you can manually run the `kubectl -n MYNS get pods --watch=true --show-labels` command to watch the effect of running the scripts and monitor the status and labels of various Pods. You will need to do 'Ctrl-C' to stop watching the Pods and exit.

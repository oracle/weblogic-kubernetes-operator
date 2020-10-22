### Domain lifecycle sample scripts
The WebLogic Server Kubernetes Operator project provides a set of sample scripts to shut down or start a specific managed-server, cluster or the entire domain. These can be helpful when scripting the lifecycle of a WebLogic Server Domain.

#### Scripts to start and stop a managed server
The `startServer.sh` script starts a managed server by patching it's server start policy to `ALWAYS`. It also increases the replica count value for the managed server's cluster by `1`. The script provides an option to keep the replica count value constant. Please see script `usage` information using `-h` option.
```
$ startServer.sh -d domain1 -n weblogic-domain-1 -s managed-server1
[INFO] Patching start policy of server 'managed-server1' from 'NEVER' to 'ALWAYS' and incrementing replica count for cluster 'cluster-1'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched server 'managed-server1' with 'ALWAYS' start policy!

       The replica count for cluster 'cluster-1' updated to 1.
```

The `stopServer.sh` script shuts down a running managed server by patching it's server start policy to `NEVER`. It also decreases the replica count for the managed server's cluster by `1`. The script provides an option to keep the replica count value constant. Please see script `usage` information using `-h` option.
```
$ stopServer.sh -d domain1 -n weblogic-domain-1 -s managed-server1
[INFO] Patching start policy of server 'managed-server1' from 'ALWAYS' to 'NEVER' and decrementing replica count for cluster 'cluster-1'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched server 'managed-server1' with 'NEVER' start policy!

       The replica count for cluster 'cluster-1' updated to 0.
```

#### Script behavior when starting or stopping a clustered managed server
Below table describes the behavior of server start and stop scripts and how it updates server start policy and the replica count value.
| Script Name | Keep Replica Count Constant? | Previous Clustered Server State | New Clustered Server Start Policy | New Clustered Server State | Cluster's Replica Count Value |
| --- | --- | --- | --- | --- | --- |
| `startServer.sh`| No (default) | Stopped | `ALWAYS` | Started | `Incremented by 1` |
| `startServer.sh`| Yes | Stopped | `ALWAYS` | Started | Unchanged |
| `startServer.sh`| Any | Started | Unchanged | Started | Unchanged |
| `stopServer.sh`| No (default) | Started | `NEVER` | Stopped | `Decremented by 1` |
| `stopServer.sh`| Yes | Started | `NEVER` | Stopped | Unchanged |
| `stopServer.sh`| Any | Stopped | Unchanged | Stopped | Unchanged |

### Scripts to start and stop a cluster
The `startCluster.sh` script starts a cluster by patching it's server start policy to `IF_NEEDED`. Once the cluster's server start policy is updated to `IF_NEEDED`, the operator will start the WebLogic Server instance Pods that are part of the cluster if they are not already running. Please see script `usage` information using `-h` option.
```
$ startCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1
[INFO]Patching start policy of cluster 'cluster-1' from 'NEVER' to 'IF_NEEDED'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched cluster 'cluster-1' with 'IF_NEEDED' start policy!.
```
The `stopCluster.sh` script shuts down a cluster by patching it's server start policy to `NEVER`. Once the cluster's server start policy is updated to `NEVER`, the operator will shut down the WebLogic Server instance Pods that are part of the cluster if they are in running state. Please see script `usage` information using `-h` option.
```
$ stopCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1
[INFO] Patching start policy of cluster 'cluster-1' from 'IF_NEEDED' to 'NEVER'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched cluster 'cluster-1' with 'NEVER' start policy!
```
### Scripts to start and stop a Domain
The `startDomain.sh` script starts a deployed Domain by patching it's server start policy to `IF_NEEDED`. Once the Domain's server start policy is updated to `IF_NEEDED`, the operator will start the WebLogic Server instance Pods that are part of the Domain if they are not already running. Please see script `usage` information using `-h` option.
```
$ startDomain.sh -d domain1 -n weblogic-domain-1
[INFO] Patching domain 'domain1' from serverStartPolicy='NEVER' to 'IF_NEEDED'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched domain 'domain1' in namespace 'weblogic-domain-1' with 'IF_NEEDED' start policy!
```

The `stopDomain.sh` script shuts down a Domain by patching it's server start policy to `NEVER`. Once the Domain's server start policy is updated to `NEVER`, the operator will shut down the WebLogic Server instance Pods that are part of the Domain if they are in running state. Please see script `usage` information using `-h` option.
```
$ stopDomain.sh -d domain1 -n weblogic-domain-1
[INFO] Patching domain 'domain1' in namespace 'weblogic-domain-1' from serverStartPolicy='IF_NEEDED' to 'NEVER'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched domain 'domain1' in namespace 'weblogic-domain-1' with 'NEVER' start policy!

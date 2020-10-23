### Domain lifecycle sample scripts
The WebLogic Server Kubernetes Operator project provides a set of sample scripts to shut-down or start-up a specific managed-server or cluster in a deployed Domain or the entire deployed Domain. Please note that Domain must have been created and deployed before these scripts can start or stop managed servers, clusters or the Domain. These can be helpful when scripting the lifecycle of a WebLogic Server Domain. Please see [Domain Life Cycle](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle) to learn how to start, stop, restart, and scale the WebLogic Server instances in your Domain.

#### Scripts to start and stop a managed server
The `startServer.sh` script starts a managed server by patching 'spec.managedServers.<server-name>.serverStartPolicy' attribute of the domain resource to `ALWAYS`. For clustered servers, it also increases the 'spec.clusters.<cluster-name>.replicas' value for the managed server's cluster by `1`. The script provides an option to keep the 'spec.clusters.<cluster-name>.replicas' value constant for clustered servers. Please see script `usage` information using `-h` option.
```
$ startServer.sh -d domain1 -n weblogic-domain-1 -s managed-server1
[INFO] Patching start policy of server 'managed-server1' from 'NEVER' to 'ALWAYS' and incrementing replica count for cluster 'cluster-1'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched server 'managed-server1' with 'ALWAYS' start policy!

       The replica count for cluster 'cluster-1' updated to 1.
```

The `stopServer.sh` script shuts down a running managed server by patching 'spec.managedServers.<server-name>.serverStartPolicy' attribute of the domain resource to `NEVER`. For clustered servers, it also decreases the 'spec.clusters.<cluster-name>.replicas' for the managed server's cluster by `1`. The script provides an option to keep the 'spec.clusters.<cluster-name>.replicas' value constant for clustered servers. Please see script `usage` information using `-h` option.
```
$ stopServer.sh -d domain1 -n weblogic-domain-1 -s managed-server1
[INFO] Patching start policy of server 'managed-server1' from 'ALWAYS' to 'NEVER' and decrementing replica count for cluster 'cluster-1'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched server 'managed-server1' with 'NEVER' start policy!

       The replica count for cluster 'cluster-1' updated to 0.
```

#### Script behavior when starting or stopping a clustered managed server
Below table shows the new values of 'spec.managedServers.<server-name>.serverStartPolicy' and 'spec.clusters.<cluster-name>.replicas' as updated by the server start and stop scripts.
| Script Name | Keep replicas constant? | Previous Clustered Server State | New serverStartPolicy value | New Clustered Server State | Cluster's replicas value |
| --- | --- | --- | --- | --- | --- |
| `startServer.sh`| No (default) | Stopped | `ALWAYS` | Started | `Incremented by 1` |
| `startServer.sh`| Yes | Stopped | `ALWAYS` | Started | Unchanged |
| `startServer.sh`| Any | Started | Unchanged | Started | Unchanged |
| `stopServer.sh`| No (default) | Started | `NEVER` | Stopped | `Decremented by 1` |
| `stopServer.sh`| Yes | Started | `NEVER` | Stopped | Unchanged |
| `stopServer.sh`| Any | Stopped | Unchanged | Stopped | Unchanged |

### Scripts to start and stop a cluster
The `startCluster.sh` script starts a cluster by patching 'spec.clusters.<cluster-name>.serverStartPolicy' attribute of the domain resource to `IF_NEEDED`. The operator will start the WebLogic Server instance Pods that are part of the cluster once the 'serverStartPolicy' attrribute is updated to `IF_NEEDED`. Please see script `usage` information using `-h` option.
```
$ startCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1
[INFO]Patching start policy of cluster 'cluster-1' from 'NEVER' to 'IF_NEEDED'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched cluster 'cluster-1' with 'IF_NEEDED' start policy!.
```
The `stopCluster.sh` script shuts down a cluster by patching 'spec.clusters.<cluster-name>.serverStartPolicy' attribute of the domain resource to `NEVER`. The operator will shut down the WebLogic Server instance Pods that are part of the cluster once the 'serverStartPolicy' attribute is updated to `NEVER`. Please see script `usage` information using `-h` option.
```
$ stopCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1
[INFO] Patching start policy of cluster 'cluster-1' from 'IF_NEEDED' to 'NEVER'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched cluster 'cluster-1' with 'NEVER' start policy!
```
### Scripts to start and stop a Domain
The `startDomain.sh` script starts a deployed Domain by patching 'spec.serverStartPolicy' attribute of the domain resource to `IF_NEEDED`. The operator will start the WebLogic Server instance Pods that are part of the Domain once 'spec.serverStartPolicy' attribute of the domain resource is updated to `IF_NEEDED`. Please see script `usage` information using `-h` option.
```
$ startDomain.sh -d domain1 -n weblogic-domain-1
[INFO] Patching domain 'domain1' from serverStartPolicy='NEVER' to 'IF_NEEDED'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched domain 'domain1' in namespace 'weblogic-domain-1' with 'IF_NEEDED' start policy!
```

The `stopDomain.sh` script shuts down a Domain by patching 'spec.serverStartPolicy' attribute of the domain resource to `NEVER`. The operator will shut down the WebLogic Server instance Pods that are part of the Domain once the 'spec.serverStartPolicy' attribute is updated to `NEVER`. Please see script `usage` information using `-h` option.
```
$ stopDomain.sh -d domain1 -n weblogic-domain-1
[INFO] Patching domain 'domain1' in namespace 'weblogic-domain-1' from serverStartPolicy='IF_NEEDED' to 'NEVER'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched domain 'domain1' in namespace 'weblogic-domain-1' with 'NEVER' start policy!

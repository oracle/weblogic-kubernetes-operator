### Domain lifecycle sample scripts

The operator provides sample scripts to start up or shut down a specific Managed Server or cluster in a deployed domain, or the entire deployed domain.

**Note**: Prior to running these scripts, you must have previously created and deployed the domain.

These scripts can be helpful when scripting the life cycle of a WebLogic Server domain. For information on how to start, stop, restart, and scale WebLogic Server instances in your domain, see [Domain Life Cycle](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle).

#### Scripts to start and stop a Managed Server
The `startServer.sh` script starts a Managed Server either by increasing the `spec.clusters[<cluster-name>].replicas` value for the Managed Server's cluster by `1` or by updating the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource or both as necessary. The script provides an option to keep the `spec.clusters[<cluster-name>].replicas` value constant for clustered servers. See the script `usage` information by using the `-h` option.

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

The `stopServer.sh` script shuts down a running Managed Server either by decreasing the `spec.clusters[<cluster-name>].replicas` value for the Managed Server's cluster by `1` or by patching the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource or both as necessary. The script provides an option to keep the `spec.clusters[<cluster-name>].replicas` value constant for clustered servers. See the script `usage` information by using the `-h` option.

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

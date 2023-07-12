### Sample Lifecycle Management Scripts

The operator provides sample lifecycle management scripts
to start up or shut down a specific Managed Server or cluster in a deployed domain, or the entire deployed domain.
In addition, it provides sample scripts to force a new introspection of a domain, scale a cluster, or monitor a domain.

These scripts can be helpful when scripting the life cycle of a WebLogic Server domain.
For information on how to start, stop, restart, and scale WebLogic Server instances in your domain, see
[Domain Life Cycle](https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-lifecycle).

- [Prerequisites](#prerequisites)
- [Cycle a domain](#cycle-a-domain)
  - [`startDomain.sh`](#startdomainsh)
  - [`stopDomain.sh`](#stopdomainsh)
  - [`rollDomain.sh`](#rolldomainsh)
  - [`introspectDomain.sh`](#introspectdomainsh)
- [Cycle a cluster](#cycle-a-cluster)
  - [`startCluster.sh`](#startclustersh)
  - [`stopCluster.sh`](#stopclustersh)
  - [`rollCluster.sh`](#rollclustersh)
  - [`scaleCluster.sh`](#scaleclustersh)
- [Cycle a server](#cycle-a-server)
  - [`startServer.sh`](#startserversh)
  - [`stopServer.sh`](#stopserversh)
  - [`restartServer.sh`](#restartserversh)
- [Monitor domains, clusters, and servers](#monitor-domains-clusters-and-servers)
  - [`kubectl --watch`](#kubectl---watch)
  - [`clusterStatus.sh`](#clusterstatussh)
  - [`waitForDomain.sh`](#waitfordomainsh)
- [Examine, change permissions or delete PV contents](#examine-change-or-delete-pv-contents)
  - [`pv-pvc-helper.sh`](#pv-pvc-helpersh)  
- [OPSS Wallet utility](#opss-wallet-utility)
  - [`opss-wallet.sh`](#opss-walletsh)  

### Prerequisites

- Prior to running these scripts, you must have previously created and deployed the domain.

- Additionally, the cluster cycle scripts require a cluster resource,
  the server cycle scripts require a cluster resource when the server is part of a cluster,
  and the `waitForDomain.sh` script requires cluster resources when the domain contains clusters.

- Some scripts make use of [jq](https://stedolan.github.io/jq/) for processing JSON.
  You must have `jq 1.5` or later installed in order to run these scripts.
  See the installation options on the [jq download](https://stedolan.github.io/jq/download/) page.

### Cycle a domain

Use the following scripts to cycle a WebLogic Server domain.

#### `startDomain.sh`

The `startDomain.sh` script starts a deployed domain by patching the `spec.serverStartPolicy` attribute of the domain resource to `IfNeeded`. The operator will start the WebLogic Server instance Pods that are part of the domain after the `spec.serverStartPolicy` attribute of the domain resource is updated to `IfNeeded`. See the script `usage` information by using the `-h` option.
```
$ startDomain.sh -d domain1 -n weblogic-domain-1
[INFO] Patching domain 'domain1' from serverStartPolicy='Never' to 'IfNeeded'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched domain 'domain1' in namespace 'weblogic-domain-1' with 'IfNeeded' start policy!
```

#### `stopDomain.sh`

The `stopDomain.sh` script shuts down a domain by patching the `spec.serverStartPolicy` attribute of the domain resource to `Never`. The operator will shut down the WebLogic Server instance Pods that are part of the domain after the `spec.serverStartPolicy` attribute is updated to `Never`. See the script `usage` information by using the `-h` option.
```
$ stopDomain.sh -d domain1 -n weblogic-domain-1
[INFO] Patching domain 'domain1' in namespace 'weblogic-domain-1' from serverStartPolicy='IfNeeded' to 'Never'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched domain 'domain1' in namespace 'weblogic-domain-1' with 'Never' start policy!
```

#### `rollDomain.sh`

Use the `rollDomain.sh` script to initiate a rolling restart of the WebLogic Server instance Pods in a domain managed by the operator.

The `rollDomain.sh` script updates the value of the `spec.restartVersion` attribute of the domain resource.  Then, the operator will do a rolling restart of the server Pods in the WebLogic domain after the value of the `spec.restartVersion` is updated. You can provide the new value for `spec.restartVersion` as a parameter to the script or the script will automatically generate a new value to trigger the rolling restart. See the script `usage` information by using the `-h` option.

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

#### `introspectDomain.sh`

Use the `introspectDomain.sh` script to rerun a WebLogic domain's instrospection job by explicitly initiating the introspection. This script updates the value of the `spec.introspectVersion` attribute of the domain resource. The resulting behavior depends on your domain home source type and other factors, see [Initiating introspection](https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-lifecycle/introspection/#initiating-introspection) for details. You can provide the new value of the `introspectVersion` as a parameter to the script or the script will automatically generate a new value to trigger the introspection. See the script `usage` information by using the `-h` option.

Use the following command to rerun a domain's instrospection job with the `introspectVersion` value generated by the script.
```
$ introspectDomain.sh -d domain1 -n weblogic-domain-1
[2021-03-24T21:37:55.989000Z][INFO] Patching introspectVersion for domain 'domain1' to '1'.
domain.weblogic.oracle/domain1 patched
[2021-03-24T21:37:56.110000Z][INFO] Successfully patched introspectVersion for domain 'domain1'!
```

Use the following command to rerun a domain's instrospection job with a specific `introspectVersion` value.
```
$ introspectDomain.sh -i v1 -d domain1 -n weblogic-domain-1
[2021-03-24T21:38:34.369000Z][INFO] Patching introspectVersion for domain 'domain1' to 'v1'.
domain.weblogic.oracle/domain1 patched
[2021-03-24T21:38:34.488000Z][INFO] Successfully patched introspectVersion for domain 'domain1'!
```


### Cycle a cluster

Use the following scripts to cycle specific WebLogic Server clusters within a domain.

#### `startCluster.sh`

The `startCluster.sh` script starts a cluster by patching the `spec.serverStartPolicy` attribute of the cluster resource to `IfNeeded`. The operator will start the WebLogic Server instance Pods that are part of the cluster after the `serverStartPolicy` attribute is updated to `IfNeeded`. See the script `usage` information by using the `-h` option.
```
$ startCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1
[INFO]Patching start policy of cluster 'cluster-1' from 'Never' to 'IfNeeded'.
cluster.weblogic.oracle/cluster-1 patched
[INFO] Successfully patched cluster 'cluster-1' with 'IfNeeded' start policy!.
```

#### `stopCluster.sh`

The `stopCluster.sh` script shuts down a cluster by patching the `spec.serverStartPolicy` attribute of the cluster resource to `Never`. The operator will shut down the WebLogic Server instance Pods that are part of the cluster after the `serverStartPolicy` attribute is updated to `Never`. See the script `usage` information by using the `-h` option.
```
$ stopCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1
[INFO] Patching start policy of cluster 'cluster-1' from 'IfNeeded' to 'Never'.
cluster.weblogic.oracle/cluster-1 patched
[INFO] Successfully patched cluster 'cluster-1' with 'Never' start policy!
```

#### `rollCluster.sh`

Use the `rollCluster.sh` script to initiate a rolling restart of the WebLogic Server Pods belonging to a WebLogic cluster in a domain managed by the operator.

The `rollCluster.sh` script updates the value of the `spec.restartVersion` attribute of the cluster resource. Then, the operator will do a rolling restart of the WebLogic cluster server instances after the value of the `spec.restartVersion` is updated. You can provide the new value of the `restartVersion` as a parameter to the script or the script will automatically generate a new value to trigger the rolling restart. See the script `usage` information by using the `-h` option.

```
$ rollCluster.sh -c cluster-1 -d domain1 -n weblogic-domain-1
[2021-03-24T04:03:27.521000Z][INFO] Patching restartVersion for cluster 'cluster-1' to '2'.
cluster.weblogic.oracle/cluster-1 patched
[2021-03-24T04:03:27.669000Z][INFO] Successfully patched restartVersion for cluster 'cluster-1'!
```

Use the following command to roll the WebLogic cluster servers with a specific `restartVersion`:
```
$ rollCluster.sh -r v2 -c cluster-1 -d domain1 -n weblogic-domain-1
[2021-03-24T13:46:16.833000Z][INFO] Patching restartVersion for cluster 'cluster-1' to 'v2'.
cluster.weblogic.oracle/cluster-1 patched
[2021-03-24T13:46:16.975000Z][INFO] Successfully patched restartVersion for cluster 'cluster-1'!
```

#### `scaleCluster.sh`

The `scaleCluster.sh` script scales a WebLogic cluster by patching the `spec.replicas` attribute of the cluster resource to the specified value. The operator will perform the scaling operation for the WebLogic cluster based on the specified value of the `replicas` attribute after its value is updated. See the script `usage` information by using the `-h` option.
```
$ scaleCluster.sh -d domain1 -n weblogic-domain-1 -c cluster-1 -r 3
[2021-02-26T19:04:14.335000Z][INFO] Patching replicas for cluster 'cluster-1' to '3'.
cluster.weblogic.oracle/cluster-1 patched
[2021-02-26T19:04:14.466000Z][INFO] Successfully patched replicas for cluster 'cluster-1'!
```

### Cycle a server

Use the following scripts to cycle specific WebLogic Server pods within a domain.

#### `startServer.sh`

The `startServer.sh` script starts a WebLogic Server instance in a domain. For clustered Managed Servers, either it increases the `spec.replicas` value for the Managed Server's cluster resource by `1` or updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource or both as necessary. For the Administration Server, it updates the value of the `spec.adminServer.serverStartPolicy` attribute of the domain resource. For non-clustered Managed Servers, it updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource. The script provides an option to keep the `spec.replicas` value of the cluster resource constant for clustered servers. See the script `usage` information by using the `-h` option.

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
[INFO] Patching start policy for 'managed-server2' to 'Always'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully patched server 'managed-server2' with 'Always' start policy.
```

#### `stopServer.sh`

The `stopServer.sh` script shuts down a running WebLogic Server instance in a domain. For clustered Managed Servers, either it decreases the `spec.replicas` value for the Managed Server's cluster resource by `1` or updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource or both as necessary. For the Administration Server, it updates the value of the `spec.adminServer.serverStartPolicy` attribute of the domain resource. For non-clustered Managed Servers, it updates the `spec.managedServers[<server-name>].serverStartPolicy` attribute of the domain resource. The script provides an option to keep the `spec.replicas` value of the cluster resource constant for clustered servers. See the script `usage` information by using the `-h` option.

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
[INFO] Unsetting the current start policy 'Always' for 'managed-server2'.
domain.weblogic.oracle/domain1 patched
[INFO] Successfully unset policy 'Always'.
```

#### `restartServer.sh`

Use the `restartServer.sh` script to restart a WebLogic Server in a domain. This script restarts the server by deleting the Server Pod for the WebLogic Server instance.
```
$ restartServer.sh -s managed-server1 -d domain1 -n weblogic-domain-1
[2021-03-24T22:20:22.498000Z][INFO] Initiating restart of 'managed-server1' by deleting server pod 'domain1-managed-server1'.
[2021-03-24T22:20:37.614000Z][INFO] Server restart succeeded !
```

### Monitor domains, clusters, and servers

Use these approaches to monitor a domain, cluster, or server as it cycles.

#### `kubectl --watch`

After executing the lifecycle scripts described previously for a domain or a cluster or a Server, you can manually run the `kubectl -n MYNS get pods --watch=true --show-labels` command to watch the effect of running the scripts and monitor the status and labels of various Pods. You will need to use `Ctrl-C` to stop watching the Pods and exit.

#### `clusterStatus.sh`

Use the `clusterStatus.sh` script to view the status of a WebLogic cluster in the WebLogic domain managed by the operator. The `WebLogic Cluster Status` contains information about the minimum, maximum, goal, current, and ready replica count for a WebLogic cluster. This script displays a table containing the status for WebLogic clusters in one or more domains across one or more namespaces. See the script `usage` information by using the `-h` option.

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

Use the following command to view the status of all WebLogic clusters in `domain1` in the `weblogic-domain-1` namespace.
```
$ clusterStatus.sh -d domain1 -n weblogic-domain-1

WebLogic Cluster Status -n "weblogic-domain-1" -d "domain1" -c "":

namespace          domain   cluster    min  max  goal  current  ready
---------          ------   -------    ---  ---  ----  -------  -----
weblogic-domain-1  domain1  cluster-1  0    4    2     2        2
weblogic-domain-1  domain1  cluster-2  0    4    0     0        0
```

#### `waitForDomain.sh`

Use the `waitForDomain.sh` script to wait for a domain to shut down or for a domain to fully start (reach its `Completed` condition).
This script additionally reports information about the ready, condition, image, restart, and introspect state of the domain's pods.
By default, this script exits with an error when the `Failure` condition is detected.

Use the following command for script usage:

```
$ waitForDomain.sh -?
```

Use the following command to wait for a domain to reach its `Completed` condition:

```
$ waitForDomain.sh -n my-namespace -d my-domain -p Completed
```

Use the following command to wait for a domain to fully shut down:

```
$ waitForDomain.sh -n my-namespace -d my-domain -p 0
```

### Examine, change, or delete PV contents

#### `pv-pvc-helper.sh`

Use this helper script for examining, changing permissions, or deleting the contents of the persistent volume (such as domain files or logs) for a WebLogic Domain on PV or Model in Image domain.
The script launches a Kubernetes pod named 'pvhelper' using the provided persistent volume claim name and the mount path.
You can run the 'kubectl exec' command to get a shell to the running pod container and run commands to examine or clean up the contents of shared directories on the persistent volume.
Use the 'kubectl delete pod pvhelper -n <namespace>' command to delete the Pod after it's no longer needed.

Use the following command for script usage:

```
$ pv-pvc-helper.sh -h
```

The following is an example command to launch the helper pod with the PVC name `sample-domain1-weblogic-sample-pvc` and mount path `/shared`. 
Specifying the `-r` argument allows the script to run as the `root` user.

```
$ pv-pvc-helper.sh -n sample-domain1-ns -c sample-domain1-weblogic-sample-pvc -m /shared -r
```

After the Pod is created, use the following command to get a shell to the running pod container.

```
$ kubectl -n sample-domain1-ns exec -it pvhelper -- /bin/sh
```

After you get a shell to the running pod container, you can recursively delete the contents of the domain home and applications
directories using the `rm -rf /shared/domains/sample-domain1` and `rm -rf /shared/applications/sample-domain1` commands. Because these
commands will delete files on the persistent storage, we recommend that you understand and execute these commands carefully.

Use the following command to delete the Pod after it's no longer needed.

```
$ kubectl delete pod pvhelper -n <namespace>
```

### OPSS Wallet utility

#### `opss-wallet.sh`

The OPSS wallet utility is a helper script for JRF-type domains that can save an OPSS key
wallet from a running domain's introspector ConfigMap to a file and
restore an OPSS key wallet file to a Kubernetes Secret for use by a
domain that you're about to run.

Use the following command for script usage:

```
$ opss-wallet.sh -?
```

For example, run the following command to save an OPSS key wallet from a running domain to the file './ewallet.p12':

```
$ opss-wallet.sh -s
```

Run the following command to restore the OPSS key wallet from the file './ewallet.p12' to the secret
'sample-domain1-opss-walletfile-secret' for use by a domain you're about to run:

```
$ opss-wallet.sh -r
```

# Sample scripts to shutdown and start a managed server/cluster/domain
The WebLogic Kubernetes Operator project offers a set of scripts to shut-down or start-up a particular managed-server, cluster or the entire Domain. These scripts can be helpful when scripting and automating the lifecycle of a WebLogic Domain. Below sections describe the usage for each script.


## Scripts for shutting down and starting a managed server
The `stopServer.sh` and `startServer.sh` scripts can be used to shut down or start-up a particular managed server in a WebLogic Server Domain. These scripts accept below four input parameters.
1) Name of the managed server to shutdown or start
2) Domain unique-id (for managed server's Domain)
3) Namespace (for managed server's Domain)
4) `-k` parameter to keep the replica count for the cluster constant.

The `stopServer.sh` script shuts down a managed server by patching it's server start policy to `NEVER`. It also decreases the replica count for the managed server's cluster by `1`. If you want to keep the replica count value constant, you can do that by specifying the `-k` option. When `-k` option is specified, the script will NOT change the cluster's replica count value. Use `-h` option to see script `usage` information.

The `startServer.sh` script starts up a managed server by patching it's server start policy to `ALWAYS`. It also increases the replica count value for the managed server's cluster by `1`. If you want to keep the replica count value constant, you can do that by specifying the `-k` option. When -k option is specified, the script will NOT change the cluster's replica count value. The operator will start the WebLogic Server instance Pod once it's server start policy is updated to ALWAYS (if Pod is not already running). Use `-h` option to see script usage information.

## Scripts for shutting down and starting a cluster
The `stopCluster.sh` and `startCluster.sh` scripts can be used to shut down or start-up a particular cluster in a WebLogic Server Domain. These scripts accept below three input parameters.
1) Name of the cluster to start or shutdown
2) Domain unique-id
3) Namespace

The `stopCluster.sh` script shuts down a cluster by patching it's server start policy to `NEVER`. Once the cluster's server start policy is updated to `NEVER`, the operator will shut down the WebLogic Server instance Pods that are part of the cluster if they are in running state. Use `-h` option to see script `usage` information.

The `startCluster.sh` script starts a cluster by patching it's server start policy to `IF_NEEDED`. Once the cluster's server start policy is updated to `IF_NEEDED`, the operator will start the WebLogic Server instance Pods that are part of the cluster if they are not already running based on replica count value. Use `-h` option to see script `usage` information.

## Starting and Stopping Domains
The `stopDomain.sh` and `startDomain.sh` scripts can be used to shut down or start-up a particular WebLogic Server Domain. These scripts accept below two input parameters.
1) Domain unique-id
2) Domain namespace

The `stopDomain.sh` script shuts down a Domain by patching it's server start policy to `NEVER`. Once the Domain's server start policy is updated to `NEVER`, the operator will shut down the WebLogic Server instance Pods that are part of the Domain if they are in running state. Use `-h` option to see script `usage` information.

The `startDomain.sh` script starts a Domain by patching it's server start policy to `IF_NEEDED`. Once the Domain's server start policy is updated to `IF_NEEDED`, the operator will start the WebLogic Server instance Pods that are part of the Domain if they are not already running. Use `-h` option to see script `usage` information.

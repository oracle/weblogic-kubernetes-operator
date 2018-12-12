> **WARNING** This documentation is for version 1.0 of the operator.  To view documenation for the current release, [please click here](/site).

# Shutting down a domain

To shut down a domain, issue the following command:

```
kubectl delete domain DOMAINUID -n NAMESPACE
```

Replace `DOMAINUID` with the UID of the target domain and `NAMESPACE` with the namespace it is running in.

This command will remove the domain custom resource for the target domain.  The operator will be notified that the custom resource has been removed, and it will initiate the following actions:

*	Remove any Ingress associated with the domain.
*	Initiate a graceful shutdown of each server in the domain, Managed Servers first and then the Administration Server last.
*	Remove any services associated with the domain.

The operator will not delete any of the content on the persistent volume.  This command simply shuts down the domain; it does not remove it.

If the load balancer option was selected, there may also be one or more load balancer deployments in the namespace that can be removed.  For example, if there was one cluster in the domain, a command similar to this would be used to remove the load balancer for that cluster:

```
kubectl delete deployment domain1-cluster-1-traefik -n domain1
```

If there is only one domain in the namespace, then just deleting the namespace might be a faster option:

```
kubectl delete namespace NAMESPACE
```

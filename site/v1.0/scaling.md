> **WARNING** This documentation is for version 1.0 of the operator.  To view documenation for the current release, [please click here](/site).

# Scaling a WebLogic cluster

WebLogic Server supports two types of clustering configurations, configured and dynamic clustering. Configured clusters are created by manually configuring each individual Managed Server instance. In dynamic clusters, the Managed Server configurations are generated from a single, shared template.  With dynamic clusters, when additional server capacity is needed, new server instances can be added to the cluster without having to manually configure them individually. Also, unlike configured clusters, scaling up of dynamic clusters is not restricted to the set of servers defined in the cluster but can be increased based on runtime demands. For more information on how to create, configure, and use dynamic clusters in WebLogic Server, see [Dynamic Clusters](https://docs.oracle.com/middleware/1221/wls/CLUST/dynamic_clusters.htm#CLUST678).

The operator provides the ability to scale WebLogic clusters by simply editing the replicas setting, as you would do with most other Kubernetes resources that support scaling.

## Initiating a scaling operation using the REST API

Scaling up or scaling down a WebLogic cluster provides increased reliability of customer applications as well as optimization of resource usage. In Kubernetes cloud environments, scaling WebLogic clusters involves scaling the corresponding pods in which WebLogic Managed Server instances are running.  Because the operator manages the life cycle of a WebLogic domain, the operator exposes a REST API that allows an authorized actor to request scaling of a WebLogic cluster.


The following URL format is used for describing the resources for scaling (scale up and scale down) a WebLogic cluster:

```
http(s)://${OPERATOR_ENDPOINT}/operator/<version>/domains/<domainUID>/clusters/<clusterName>/scale
```

For example:

```
http(s)://${OPERATOR_ENDPOINT}/operator/v1/domains/domain1/clusters/cluster-1/scale
```

In this URL format:

*	`OPERATOR_ENDPOINT` is the host and port of the operator service.
*	`<version>` denotes the version of the REST resource.
*	`<domainUID>` is the unique identifier of the WebLogic domain.
*	`<clusterName>` is the name of the WebLogic cluster to be scaled.

The `/scale` REST endpoint accepts an HTTP POST request and the request body supports the JSON `"application/json"` media type.  The request body will be a simple name-value item named `configuredManagedServerCount`; for example:

```
{
    "configuredManagedServerCount": 3
}
```

The `configuredManagedServerCount` value designates the number of WebLogic Server instances to scale to.  Note that the scale resource is implemented using the JAX-RS framework, and so a successful scaling request will return an HTTP response code of `204 (“No Content”)` because the resource method’s return type is void and does not return a message body.

## What does the operator do in response to a scaling request?

When the operator receives a scaling request, it will:

*	Perform an authentication and authorization check to verify that the specified user is allowed to perform the specified operation on the specified resource.
*	Validate that the specified domain, identified by `domainUID`, exists.
*	Validate that the WebLogic cluster, identified by `clusterName`, exists.
*	Verify that the specified WebLogic cluster has a sufficient number of configured servers to satisfy the scaling request.
*	Initiate scaling by setting the `replicas` property within the corresponding domain custom resource, which can be done in either:
  *	A `clusterStartup` entry, if defined within its cluster list
  *	At the domain level, if not defined in a `clusterStartup` entry and the `startupControl` property is set to `AUTO`

In response to a change to either `replicas` property, in the domain custom resource, the operator will increase or decrease the number of pods (Managed Servers) to match the desired replica count.

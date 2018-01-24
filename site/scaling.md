# Scaling a WebLogic cluster

Explain how the scaling works – replicas, etc., the REST endpoint.

## Initiating a scaling operation using the REST API

Scaling up or scaling down a WebLogic cluster provides increased reliability of customer applications as well as optimization of resource usage. In Kubernetes cloud environments, scaling WebLogic clusters involves scaling the corresponding pods in which WebLogic Managed Server instances are running.  Because the operator manages the life cycle of a WebLogic domain, the operator exposes a REST API that allows an authorized actor to request scaling of a WebLogic cluster.

**Note:** In the Technology Preview release, only WebLogic Server configured clusters are supported by the operator, and the operator will scale up only to the number of Managed Servers that are already defined.  Support for WebLogic Server dynamic clusters, and for scaling configured clusters to more servers than are defined, is planned for a future release.

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

The `/scale` REST endpoint accepts an HTTP POST request and the request body supports the JSON `"application/json"` media type.  The request body will be a simple name-value item named `managedServerCount`; for example:

```
{
    "managedServerCount": 3
}
```

The `managedServerCount` value designates the number of WebLogic Server instances to scale to.  Note that the scale resource is implemented using the JAX-RS framework, and so a successful scaling request will return an HTTP response code of `204 (“No Content”)` because the resource method’s return type is void and does not return a message body.

## What does the operator do in response to a scaling request?

When the operator receives a scaling request, it will:

*	Perform an authentication and authorization check to verify that the specified user is allowed to perform the specified operation on the specified resource.
*	Validate that the specified domain, identified by `domainUID`, exists.
*	Validate that the WebLogic cluster, identified by `clusterName`, exists.
*	Verify that the specified WebLogic cluster has a sufficient number of configured servers to satisfy the scaling request.
*	Initiate scaling by setting the `replicas` property within the corresponding domain custom resource either in:
  *	a `clusterStartup` entry, if defined within its cluster list.
  *	at the domain level, if not defined in a `clusterStartup` entry and the `startupControl` property is set to `AUTO`.

In response to a change to either `replicas` property, in the domain custom resource, the operator will increase or decrease the number of pods (Managed Servers) to match the desired replica count.

## Initiating a scaling operation from WLDF

Note that there is a video demonstration of scaling with WLDF available [here](https://youtu.be/Q8iZi2e9HvU).

Write me

## Initiating a scaling operation from Prometheus

Write me

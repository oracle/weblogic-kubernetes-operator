Explain how the scaling works – replicas, etc., the REST endpoint.
Initiating a scaling operation using the REST API
Scaling up (or down) of a WebLogic cluster provides increased reliability of customer applications as well as optimization of resource usage.  Scaling of WebLogic clusters, in Kubernetes cloud environments, involves scaling the corresponding pods in which WebLogic managed server instances are running.  Since the operator manages the lifecycle of a WebLogic Server domain, the operator has exposed an endpoint REST API that allows an authorized actor to request scaling of a WebLogic cluster.
NOTE: In the Technology Preview release, only WebLogic configured clusters are supported by the operator, and the operator will only scale up to the number of managed servers that are already defined.  Support for WebLogic dynamic clusters, and for scaling configured clusters to more servers than are defined is planned for a future release.
The following URL format is used for describing the resources for scaling (scale up and scale down) of a WebLogic Cluster:
http(s)://${OPERATOR_ENDPOINT}/operator/<version>/domains/<domainUID>/clusters/<clusterName>/scale
For example:
http(s)://${OPERATOR_ENDPOINT}/operator/v1/domains/domain1/clusters/cluster-1/scale
In this URL format:
»	OPERATOR_ENDPOINT is the host/port of the operator service.
»	<version> denotes the version of the REST resource.
»	<domainUID> is the unique identifier of the WebLogic domain.
»	<clusterName> is the name of the WebLogic cluster to be scaled.
The /scale REST endpoint accepts an HTTP POST request and the request body supports the JSON ("application/json") media type.  The request body will be a simple name/value item named managedServerCount for example:
{
    "managedServerCount": 3
}
The managedServerCount value designates the number of WebLogic Server instances to scale to.  Note that the scale resource is implemented using the JAX-RS framework, and so a successful scaling request will return an HTTP response code of 204 (“No Content”) since the resource method’s return type is void and does not return a message body.
What does the Operator do in response to a scaling request?
When the operator receives a scaling request, it will:
»	Perform an authentication and authorization check that the specified user is allowed to perform the specified operation on the specified resource.
»	Validate that the specified domain, identified by domainUID, exists.
»	Validate that the WebLogic cluster, identified by clusterName, exists.
»	Verify that the specified WebLogic cluster has enough configured servers to satisfy the scaling request.
»	Initiate scaling by setting the replicas property within the corresponding domain custom resource either in:
»	a clusterStartup entry if defined within its cluster list.
»	at the domain level, if not defined in a clusterStartup entry and the startupControl property is set to AUTO.
In response to a change to either replicas property, in the domain custom resource, the operator will increase/decrease the number of pods (managed servers) to match the desired replica count.
Initiating a scaling operation from WLDF
Write me
Initiating a scaling operation from Prometheus
Write me

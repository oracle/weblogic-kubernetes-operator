---
title: "Scaling"
date: 2019-02-23T17:04:45-05:00
draft: false
weight: 3
description: "The operator provides several ways to initiate scaling of WebLogic clusters."
---

WebLogic Server supports two types of clustering configurations, configured and dynamic. Configured clusters are created by manually configuring each individual Managed Server instance. In dynamic clusters, the Managed Server configurations are generated from a single, shared template.  With dynamic clusters, when additional server capacity is needed, new server instances can be added to the cluster without having to manually configure them individually. Also, unlike configured clusters, scaling up of dynamic clusters is not restricted to the set of servers defined in the cluster but can be increased based on runtime demands. For more information on how to create, configure, and use dynamic clusters in WebLogic Server, see [Dynamic Clusters](https://docs.oracle.com/en/middleware/standalone/weblogic-server/14.1.1.0/clust/dynamic_clusters.html#GUID-DA7F7FAD-49AA-4F3D-8A05-0D9921B96971).

The following blogs provide more in-depth information on support for scaling WebLogic clusters in Kubernetes:

* [Automatic Scaling of WebLogic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/automatic-scaling-of-weblogic-clusters-on-kubernetes-v2)
* [WebLogic Dynamic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/weblogic-dynamic-clusters-on-kubernetes)

The operator provides several ways to initiate scaling of WebLogic clusters, including:

* [On-demand, updating the domain resource directly (using `kubectl`)](#on-demand-updating-the-domain-resource-directly).
* [Calling the operator's REST scale API, for example, from `curl`](#calling-the-operators-rest-scale-api).
* [Using a WLDF policy rule and script action to call the operator's REST scale API](#using-a-wldf-policy-rule-and-script-action-to-call-the-operators-rest-scale-api).
* [Using a Prometheus alert action to call the operator's REST scale API](#using-a-prometheus-alert-action-to-call-the-operators-rest-scale-api).

#### On-demand, updating the domain resource directly
The easiest way to scale a WebLogic cluster in Kubernetes is to simply edit the `replicas` property within a domain resource.  This can be done by using the `kubectl` command-line interface for running commands against Kubernetes clusters.  More specifically, you can modify the domain resource directly by using the `kubectl edit` command.  For example:
```
$ kubectl edit domain domain1 -n [namespace]
```
Here we are editing a domain resource named `domain1`.  The `kubectl edit` command will open the domain resource definition in an editor and allow you to modify the `replicas` value directly. Once committed, the operator will be notified of the change and will immediately attempt to scale the corresponding dynamic cluster by reconciling the number of running pods/Managed Server instances with the `replicas` value specification.
```
spec:
  ...
  clusters:
    - clusterName: cluster-1
      replicas: 1
  ...
```

Alternatively, you can specify a default `replicas` value for all the clusters.  If you do this, then you don't need to list the cluster in the domain resource (unless you want to customize another property of the cluster).
```
spec:
  ...
  replicas: 1
  ...
```

#### Calling the operator's REST scale API

Scaling up or scaling down a WebLogic cluster provides increased reliability of customer applications as well as optimization of resource usage. In Kubernetes cloud environments, scaling WebLogic clusters involves scaling the corresponding pods in which WebLogic Server Managed Server instances are running.  Because the operator manages the life cycle of a WebLogic domain, the operator exposes a REST API that allows an authorized actor to request scaling of a WebLogic cluster.


The following URL format is used for describing the resources for scaling (up and down) a WebLogic cluster:

```
http(s)://${OPERATOR_ENDPOINT}/operator/<version>/domains/<domainUID>/clusters/<clusterName>/scale
```

For example:

```
http(s)://${OPERATOR_ENDPOINT}/operator/v1/domains/domain1/clusters/cluster-1/scale
```

In this URL format:

*	`OPERATOR_ENDPOINT` is the host and port of the operator REST endpoint (internal or external).
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

When you POST to the `/scale` REST endpoint, you must send the following headers:

* `X-Requested-By` request value.  The value is an arbitrary name such as `MyClient`.  
* `Authorization: Bearer` request value. The value of the `Bearer` token is the WebLogic domain service account token.

For example, when using `curl`:

```
curl -v -k -H X-Requested-By:MyClient -H Content-Type:application/json -H Accept:application/json -H "Authorization:Bearer ..." -d '{ "managedServerCount": 3 }' https://.../scaling
```

If you omit the header, you'll get a `400 (bad request)` response without any details explaining why the request was bad.  If you omit the Bearer Authentication header, then you'll get a `401 (Unauthorized)` response.

##### Operator REST endpoints
The WebLogic Server Kubernetes Operator can expose both an internal and external REST HTTPS endpoint.
The internal REST endpoint is only accessible from within the Kubernetes cluster. The external REST endpoint
is accessible from outside the Kubernetes cluster.
The internal REST endpoint is enabled by default and thus always available, whereas the external REST endpoint
is disabled by default and only exposed if explicitly configured.
Detailed instructions for configuring the external REST endpoint are available [here]({{< relref "/userguide/managing-operators/_index.md#optional-configure-the-operators-external-rest-https-interface" >}}).

{{% notice note %}}
Regardless of which endpoint is being invoked, the URL format for scaling is the same.
{{% /notice %}}

##### What does the operator do in response to a scaling request?

When the operator receives a scaling request, it will:

*	Perform an authentication and authorization check to verify that the specified user is allowed to perform the specified operation on the specified resource.
*	Validate that the specified domain, identified by `domainUID`, exists.
*	Validate that the WebLogic cluster, identified by `clusterName`, exists.
*	Verify that the specified WebLogic cluster has a sufficient number of configured servers to satisfy the scaling request.
*	Initiate scaling by setting the `replicas` property within the corresponding domain resource, which can be done in either:
      *	A `cluster` entry, if defined within its cluster list.
      *	At the domain level, if not defined in a `cluster` entry.

In response to a change to either `replicas` property, in the domain resource, the operator will increase or decrease the number of pods (Managed Servers) to match the desired replica count.

#### Using a WLDF policy rule and script action to call the operator's REST scale API
The WebLogic Diagnostics Framework (WLDF) is a suite of services and APIs that collect and surface metrics that provide visibility into server and application performance.
To support automatic scaling of WebLogic clusters in Kubernetes, WLDF provides the Policies and Actions component, which lets you write policy expressions for automatically executing scaling
operations on a cluster. These policies monitor one or more types of WebLogic Server metrics, such as memory, idle threads, and CPU load.  When the configured threshold
in a policy is met, the policy is triggered, and the corresponding scaling action is executed.  The WebLogic Server Kubernetes Operator project provides a shell script, [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/src/scripts/scaling/scalingAction.sh),
for use as a Script Action, which illustrates how to issue a request to the operator’s REST endpoint.

##### Configure automatic scaling of WebLogic clusters in Kubernetes with WLDF
The following steps are provided as a guideline on how to configure a WLDF Policy and Script Action component for issuing scaling requests to the operator's REST endpoint:

1. Copy the [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/src/scripts/scaling/scalingAction.sh) script to a directory (such as `$DOMAIN_HOME/bin/scripts`) so that it's accessible within the Administration Server pod.

1. Configure a WLDF policy and action as part of a diagnostic module targeted to the Administration Server. For information about configuring the WLDF Policies and Actions component,
see [Configuring Policies and Actions](https://docs.oracle.com/en/middleware/standalone/weblogic-server/14.1.1.0/wldfc/config_watch_notif.html#GUID-D3FA8301-AAF2-41CE-A6A5-AB4005849913) in _Configuring and Using the Diagnostics Framework for Oracle WebLogic Server_.

     a. Configure a WLDF policy with a rule expression for monitoring WebLogic Server metrics, such as memory, idle threads, and CPU load for example.

     b. Configure a WLDF script action and associate the [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/src/scripts/scaling/scalingAction.sh) script.

Important notes about the configuration properties for the Script Action:

The `scalingAction.sh` script requires access to the SSL certificate of the operator’s endpoint and this is provided through the environment variable `INTERNAL_OPERATOR_CERT`.  
The operator’s SSL certificate can be found in the `internalOperatorCert` entry of the operator’s ConfigMap `weblogic-operator-cm`:

For example:
```
#> kubectl describe configmap weblogic-operator-cm -n weblogic-operator
...
Data
====
internalOperatorCert:
----
LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUR3akNDQXFxZ0F3SUJBZ0lFRzhYT1N6QU...
...
```

The scalingAction.sh script accepts a number of customizable parameters:

* `action` - scaleUp or scaleDown (Required)

* `domain_uid` - WebLogic domain unique identifier (Required)

* `cluster_name` - WebLogic cluster name (Required)

* `kubernetes_master` - Kubernetes master URL, default=https://kubernetes  

{{% notice note %}}
Set this to `https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}` when invoking `scalingAction.sh` from the Administration Server pod.
{{% /notice %}}

* `access_token` - Service Account Bearer token for authentication and authorization for access to REST Resources

* `wls_domain_namespace` - Kubernetes namespace in which the WebLogic domain is defined, default=`default`

* `operator_service_name` - WebLogic Server Kubernetes Operator Service name of the REST endpoint, default=`internal-weblogic-operator-service`

* `operator_service_account` - Kubernetes Service Account name for the operator, default=`weblogic-operator`

* `operator_namespace` – Namespace in which the operator is deployed, default=`weblogic-operator`

* `scaling_size` – Incremental number of WebLogic Server instances by which to scale up or down, default=`1`

You can use any of the following tools to configure policies for diagnostic system modules:

* WebLogic Server Administration Console
* WLST
* REST
* JMX application    

A more in-depth description and example on using WLDF's Policies and Actions component for initiating scaling requests through the operator's REST endpoint can be found in the blogs:

* [Automatic Scaling of WebLogic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/automatic-scaling-of-weblogic-clusters-on-kubernetes-v2)
* [WebLogic Dynamic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/weblogic-dynamic-clusters-on-kubernetes)



##### Create cluster role bindings to allow a namespace user to query WLS Kubernetes cluster information
The script `scalingAction.sh`, specified in the WLDF script action above, needs the appropriate RBAC permissions granted for the service account user (in the namespace in which the WebLogic domain is deployed) in order to query the Kubernetes API server for both configuration and runtime information of the domain resource.
The following is an example YAML file for creating the appropriate Kubernetes cluster role bindings:

{{% notice note %}}
In the example cluster role binding definition below, the WebLogic domain is deployed to a namespace `weblogic-domain`.  Replace the namespace value with the name of the namespace in which the WebLogic domain is deployed in your Kubernetes environment.
{{% /notice %}}

```
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: weblogic-domain-cluster-role
rules:
- apiGroups: ["weblogic.oracle"]
  resources: ["domains"]
  verbs: ["get", "list", "update"]
- apiGroups: ["apiextensions.k8s.io"]
  resources: ["customresourcedefinitions"]
  verbs: ["get", "list"]
---
#
# creating role-bindings for cluster role
#
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: domain-cluster-rolebinding
subjects:
- kind: ServiceAccount
  name: default
  namespace: weblogic-domain
  apiGroup: ""
roleRef:
  kind: ClusterRole
  name: weblogic-domain-cluster-role
  apiGroup: "rbac.authorization.k8s.io"
---
#
# creating role-bindings
#
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: weblogic-domain-operator-rolebinding
  namespace: weblogic-operator
subjects:
- kind: ServiceAccount
  name: default
  namespace: weblogic-domain
  apiGroup: ""
roleRef:
  kind: ClusterRole
  name: cluster-admin
  apiGroup: "rbac.authorization.k8s.io"
---

```

#### Using a Prometheus alert action to call the operator's REST scale API
In addition to using the WebLogic Diagnostic Framework for automatic scaling of a dynamic cluster,
you can use a third-party monitoring application like Prometheus.  Please read the following blog for
details about [Using Prometheus to Automatically Scale WebLogic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/using-prometheus-to-automatically-scale-weblogic-clusters-on-kubernetes-v5).

#### Helpful Tips
##### Debugging scalingAction.sh
The [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/src/scripts/scaling/scalingAction.sh) script was designed to be executed within the
Administration Server pod because the associated diagnostic module is targed to the Administration Server.

The easiest way to verify and debug the `scalingAction.sh` script is to open a shell on the running Administration Server pod and execute the script on the command line.

The following example illustrates how to open a bash shell on a running Administration Server pod named `domain1-admin-server` and execute the `scriptAction.sh` script.  It assumes that:

* The domain home is in `/u01/oracle/user-projects/domains/domain1` (that is, the domain home is inside a Docker image).
* The Dockerfile copied [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/src/scripts/scaling/scalingAction.sh) to `/u01/oracle/user-projects/domains/domain1/bin/scripts/scalingAction.sh`.

```
> kubectl exec -it domain1-admin-server /bin/bash
# bash> cd /u01/oracle/user-projects/domains/domain1/bin/scripts
# bash> ./scalingAction.sh
```

A log, `scalingAction.log`, will be generated in the same directory in which the script was executed and can be examined for errors.

##### Example on accessing the external REST endpoint
The easiest way to test scaling using the external REST endpoint is to use a command-line tool like `curl`. Using `curl` to issue
an HTTPS scale request requires these mandatory header properties:

* Bearer Authorization token
* SSL certificate for the operator's external REST endpoint
* `X-Requested-By` header value

The following shell script is an example of how to issue a scaling request, with the necessary HTTP request header values, using `curl`.
This example assumes the operator and domain resource are configured with the following properties in Kubernetes:

* Operator properties:
  * externalRestEnabled: `true`
  * externalRestHttpsPort: `31001`
  * operator's namespace: `weblogic-operator`
  * operator's hostname is the same as the host shell script is executed on.
* Domain resource properties:  
  * WebLogic cluster name: `DockerCluster`
  * Domain UID: `domain1`

```
#!/bin/sh

# Setup properties  
ophost=`uname -n`
opport=31001 #externalRestHttpsPort
cluster=cluster-1
size=3 #New cluster size
ns=weblogic-operator # Operator NameSpace
sa=weblogic-operator # Operator ServiceAccount
domainuid=domain1

# Retrieve service account name for given namespace   
sec=`kubectl get serviceaccount ${sa} -n ${ns} -o jsonpath='{.secrets[0].name}'`
#echo "Secret [${sec}]"

# Retrieve base64 encoded secret for the given service account   
enc_token=`kubectl get secret ${sec} -n ${ns} -o jsonpath='{.data.token}'`
#echo "enc_token [${enc_token}]"

# Decode the base64 encoded token  
token=`echo ${enc_token} | base64 --decode`
#echo "token [${token}]"

# clean up any temporary files
rm -rf operator.rest.response.body operator.rest.stderr operator.cert.pem

# Retrieve SSL certificate from the Operator's external REST endpoint
`openssl s_client -showcerts -connect ${ophost}:${opport} </dev/null 2>/dev/null | openssl x509 -outform PEM > operator.cert.pem`

echo "Rest EndPoint url https://${ophost}:${opport}/operator/v1/domains/${domainuid}/clusters/${cluster}/scale"

# Issue 'curl' request to external REST endpoint  
curl --noproxy '*' -v --cacert operator.cert.pem \
-H "Authorization: Bearer ${token}" \
-H Accept:application/json \
-H "Content-Type:application/json" \
-H "X-Requested-By:WLDF" \
-d "{\"managedServerCount\": $size}" \
-X POST  https://${ophost}:${opport}/operator/v1/domains/${domainuid}/clusters/${cluster}/scale \
-o operator.rest.response.body \
--stderr operator.rest.stderr

```

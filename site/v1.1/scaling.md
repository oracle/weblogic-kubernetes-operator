> **WARNING** This documentation is for version 1.1 of the operator.  To view documenation for the current release, [please click here](/site).

# Scaling a WebLogic cluster

WebLogic Server supports two types of clustering configurations, configured and dynamic. Configured clusters are created by manually configuring each individual Managed Server instance. In dynamic clusters, the Managed Server configurations are generated from a single, shared template.  With dynamic clusters, when additional server capacity is needed, new server instances can be added to the cluster without having to manually configure them individually. Also, unlike configured clusters, scaling up of dynamic clusters is not restricted to the set of servers defined in the cluster but can be increased based on runtime demands. For more information on how to create, configure, and use dynamic clusters in WebLogic Server, see [Dynamic Clusters](https://docs.oracle.com/middleware/1221/wls/CLUST/dynamic_clusters.htm#CLUST678).

The following blogs provide more in-depth information on support for scaling WebLogic clusters in Kubernetes:
* [Automatic Scaling of WebLogic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/automatic-scaling-of-weblogic-clusters-on-kubernetes-v2)
* [WebLogic Dynamic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/weblogic-dynamic-clusters-on-kubernetes)

The operator provides several ways to initiate scaling of WebLogic clusters, including:

* On-demand, updating the domain custom resource directly (using `kubectl`).
* Calling the operator's REST scale API, for example, from `curl`.
* Using a WLDF policy rule and script action to call the operator's REST scale API.
* Using a Prometheus alert action to call the operator's REST scale API.

## On-demand, updating the domain custom resource directly
The easiest way to scale a WebLogic cluster in Kubernetes is to simply edit the `replicas` property within a domain custom resource.  This can be done by using the `kubectl` command-line interface for running commands against Kubernetes clusters.  More specifically, you can modify the domain custom resource directly by using the `kubectl edit` command.  For example:
```
kubectl edit domain domain1 -n [namespace]
```
Here we are editing a domain custom resource named 'domain1'.  The `kubectl edit` command will open the domain custom resource definition in an editor and allow you to modify the `replicas` value directly. Once committed, the operator will be notified of the change and will immediately attempt to scale the corresponding dynamic cluster by reconciling the number of running pods/Managed Server instances with the `replicas` value specification.
```
spec:
  adminSecret:
    name: domain1-weblogic-credentials
  asName: admin-server
  asPort: 7001
  clusterStartup:
  - clusterName: cluster-1
    desiredState: RUNNING
    env:
    - name: JAVA_OPTIONS
      value: -Dweblogic.StdoutDebugEnabled=false
    - name: USER_MEM_ARGS
      value: '-Xms64m -Xmx256m '
    replicas: 1
...
```

## Calling the operator's REST scale API

Scaling up or scaling down a WebLogic cluster provides increased reliability of customer applications as well as optimization of resource usage. In Kubernetes cloud environments, scaling WebLogic clusters involves scaling the corresponding pods in which WebLogic Managed Server instances are running.  Because the operator manages the life cycle of a WebLogic domain, the operator exposes a REST API that allows an authorized actor to request scaling of a WebLogic cluster.


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
* `X-Requested-By` request value.  The value is an arbitrary name such as 'MyClient'.  
* `Authorization: Bearer` request value. The value of the 'Bearer' token is the WebLogic domain service account token.

For example, when using `curl`:

```
curl -v -k -H X-Requested-By:MyClient -H Content-Type:application/json -H Accept:application/json -H "Authorization:Bearer ..." -d { "managedServerCount": 3 } https:/.../scaling
```

If you omit the header, you'll get a `400 (bad request)` response without any details explaining why the request was bad.  If you omit the Bearer Authentication header, then you'll get a `401 (Unauthorized)` response.

### Operator REST endpoints
The WebLogic Kubernetes Operator can expose both an internal and external REST HTTPS endpoint.
The internal REST endpoint is only accessible from within the Kubernetes cluster. The external REST endpoint
is accessible from outside the Kubernetes cluster.
The internal REST endpoint is enabled by default and thus always available, whereas the external REST endpoint
is disabled by default and only exposed if explicitly configured.  The following values, defined in the `create-weblogic-operator-inputs.yaml`,
are used to enable and configure the external REST endpoint:

```
# Options for externally exposing the operator REST HTTPS interface
# (i.e. outside of the Kubernetes cluster). Valid values are:
#
# "NONE"
#    The REST interface is not exposed outside the Kubernetes cluster.
#
# "SELF_SIGNED_CERT"
#    The REST interface is exposed outside of the Kubernetes cluster on the
#    port specified by the 'externalRestHttpsPort' property.
#    A self-signed certificate and private key are generated for the REST interface.
#    The certificate's subject alternative names are specified by the 'externalSans'
#    property.
#
# "CUSTOM_CERT"
#    The REST interface is exposed outside of the Kubernetes cluster on the
#    port specified by the 'externalRestHttpsPort' property.
#    The customer supplied certificate and private key are used for the REST
#    interface.  They are specified by the 'externalOperatorCert' and
#    'eternalOperatorKey' properties.
externalRestOption: NONE
  
# The node port that should be allocated for the external operator REST https interface.
# This parameter is required if 'externalRestOption' is not 'NONE'.
# Otherwise, it is ignored.
externalRestHttpsPort: 31001
  
# The subject alternative names to put into the generated self-signed certificate
# for the external WebLogic Operator REST https interface, for example:
#   DNS:myhost,DNS:localhost,IP:127.0.0.1
# This parameter is required if 'externalRestOption' is 'SELF_SIGNED_CERT'.
# Otherwise, it is ignored.
externalSans:
  
# The customer supplied certificate to use for the external operator REST
# https interface.  The value must be a string containing a base64 encoded PEM certificate.
# This parameter is required if 'externalRestOption' is 'CUSTOM_CERT'.
# Otherwise, it is ignored.
externalOperatorCert:
  
# The customer supplied private key to use for the external operator REST
# https interface.  The value must be a string containing a base64 encoded PEM key.
# This parameter is required if 'externalRestOption' is 'CUSTOM_CERT'.
# Otherwise, it is ignored.
externalOperatorKey:
```  

**NOTE**: Regardless of which endpoint is being invoked, the URL format for scaling is the same.

### What does the operator do in response to a scaling request?

When the operator receives a scaling request, it will:

*	Perform an authentication and authorization check to verify that the specified user is allowed to perform the specified operation on the specified resource.
*	Validate that the specified domain, identified by `domainUID`, exists.
*	Validate that the WebLogic cluster, identified by `clusterName`, exists.
*	Verify that the specified WebLogic cluster has a sufficient number of configured servers to satisfy the scaling request.
*	Initiate scaling by setting the `replicas` property within the corresponding domain custom resource, which can be done in either:
  *	A `clusterStartup` entry, if defined within its cluster list.
  *	At the domain level, if not defined in a `clusterStartup` entry and the `startupControl` property is set to `AUTO`.

In response to a change to either `replicas` property, in the domain custom resource, the operator will increase or decrease the number of pods (Managed Servers) to match the desired replica count.

## Using a WLDF policy rule and script action to call the operator's REST scale API
The WebLogic Diagnostics Framework (WLDF) is a suite of services and APIs that collect and surface metrics that provide visibility into server and application performance.
To support automatic scaling of WebLogic clusters in Kubernetes, WLDF provides the Policies and Actions component, which lets you write policy expressions for automatically executing scaling
operations on a cluster. These policies monitor one or more types of WebLogic Server metrics, such as memory, idle threads, and CPU load.  When the configured threshold
in a policy is met, the policy is triggered, and the corresponding scaling action is executed.  The WebLogic Kubernetes Operator project provides a shell script, [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/src/scripts/scaling/scalingAction.sh),
for use as a Script Action, which illustrates how to issue a request to the operator’s REST endpoint.

### Configure automatic scaling of WebLogic clusters in Kubernetes with WLDF
The following steps are provided as a guideline on how to configure a WLDF Policy and Script Action component for issuing scaling requests to the operator's REST endpoint:
1. Copy the [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/src/scripts/scaling/scalingAction.sh) script to a directory (such as `$DOMAIN_HOME/bin/scripts`) so that it's accessible within the Administration Server pod.
2. Configure a WLDF policy and action as part of a diagnostic module targeted to the Administration Server. For information about configuring the WLDF Policies and Actions component,
see [Configuring Policies and Actions](https://docs.oracle.com/middleware/1221/wls/WLDFC/config_watch_notif.htm#WLDFC188) in _Configuring and Using the Diagnostics Framework for Oracle WebLogic Server_.

    a. Configure a WLDF policy with a rule expression for monitoring WebLogic Server metrics, such as memory, idle threads, and CPU load for example.

    b. Configure a WLDF script action and associate the [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/src/scripts/scaling/scalingAction.sh) script.

    Important notes about the configuration properties for the Script Action:

    `Working Directory` and `Path to Script` configuration entries specify the volume mount path (`/shared`) to access the WebLogic domain home.
    The `scalingAction.sh` script requires access to the SSL certificate of the operator’s endpoint and this is provided through the environment variable `INTERNAL_OPERATOR_CERT`.  
    The operator’s SSL certificate can be found in the `internalOperatorCert` entry of the operator’s ConfigMap `weblogic-operator-cm`:

    For example:
    ```
    #> kubectl describe configmap weblogic-operator-cm -n weblogic-operator

    Name:         `weblogic-operator-cm`      
    Namespace:    `weblogic-operator`
    Labels:       `weblogic.operatorName=weblogic-operator`
    Annotations:  `kubectl.kubernetes.io/last-applied-configuration={"apiVersion":"v1","data":{"externalOperatorCert":"","internalOperatorCert":"LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0t...`
    Data
    ====
    internalOperatorCert:
    ----
    LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUR3akNDQXFxZ0F3SUJBZ0lFRzhYT1N6QU...
    ```

    The scalingAction.sh script accepts a number of customizable parameters:

    * action - scaleUp or scaleDown (Required)

    * domain_uid - WebLogic domain unique identifier (Required)

    * cluster_name - WebLogic cluster name (Required)

    * kubernetes_master - Kubernetes master URL, default=https://kubernetes

    * access_token - Service Account Bearer token for authentication and authorization for access to REST Resources

    * wls_domain_namespace - Kubernetes namespace in which the WebLogic domain is defined, default=default

    * operator_service_name - WebLogic Operator Service name of the REST endpoint, default=internal-weblogic-operator-service

    * operator_service_account - Kubernetes Service Account name for the WebLogic Operator, default=weblogic-operator

    * operator_namespace – Namespace in which the WebLogic Operator is deployed, default=weblogic-operator

    * scaling_size – Incremental number of WebLogic Server instances by which to scale up or down, default=1

    You can use any of the following tools to configure policies for diagnostic system modules:
    * WebLogic Server Administration Console
    * WLST
    * REST
    * JMX application    

A more in-depth description and example on using WLDF's Policies and Actions component for initiating scaling requests through the operator's REST endpoint can be found in the blogs:
* [Automatic Scaling of WebLogic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/automatic-scaling-of-weblogic-clusters-on-kubernetes-v2)
* [WebLogic Dynamic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/weblogic-dynamic-clusters-on-kubernetes)



### Create cluster role bindings to allow a namespace user to query WLS Kubernetes cluster information
The script `scalingAction.sh`, specified in the WLDF script action above, needs the appropriate RBAC permissions granted for the service account user (in the namespace in which the WebLogic domain is deployed) in order to query the Kubernetes API server for both configuration and runtime information of the domain custom resource.
The following is an example YAML file for creating the appropriate Kubernetes cluster role bindings:

**NOTE**: In the example cluster role binding definition below, the WebLogic domain is deployed to a namespace `weblogic-domain`.  Replace the namespace value with the name of the namespace in which the WebLogic domain is deployed in your Kubernetes environment.

```
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: weblogic-domain-cluster-role
rules:
- apiGroups: ["weblogic.oracle"]
  resources: ["domains"]
  verbs: ["get", "list", "update"]
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

## Using a Prometheus alert action to call the operator's REST scale API
In addition to using the WebLogic Diagnostic Framework for automatic scaling of a dynamic cluster,
you can use a third party monitoring application like Prometheus.  Please read the following blog for
details about [Using Prometheus to Automatically Scale WebLogic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/using-prometheus-to-automatically-scale-weblogic-clusters-on-kubernetes-v5).

## Helpful Tips
### Debugging scriptAction.sh
The [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/src/scripts/scaling/scalingAction.sh) script was designed to be executed within the
Administration Server pod because the associated diagnostic module is targed to the Administration Server.  The easiest way to verify and debug the `scriptAction.sh` script is
to open a shell on the running Administration Server pod and execute the script on the command line.  The following example illustrates how to open a bash shell on a running
Administration Server pod named `domain1-admin-server` and execute the `scriptAction.sh` script:

```
> kubectl exec -it domain1-admin-server /bin/bash
# bash> cd /shared/domain/base_domain/bin/scripts
# bash> ./scriptAction.sh
```

A log, `scriptAction.log`, will be generated in the same directory as the script was executed in and can be examined for errors.

### Example on accessing the external REST endpoint
The easiest way to test scaling using the external REST endpoint is to use a command-line tool like `curl`. Using `curl` to issue
an HTTPS scale request requires these mandatory header properties:
* Bearer Authorization token
* SSL certificate for the operator's external REST endpoint
* `X-Requested-By` header value

The following shell script is an example of how to issue a scaling request, with the necessary HTTP request header values, using `curl`.
This example assumes the operator and domain custom resource are configured with the following properties in Kubernetes:
* Operator properties:
  * externalRestOption: `SELF_SIGNED_CERT`
  * externalRestHttpsPort: `31001`
  * operator's namespace: `weblogic-operator`
  * operator's hostname is the same as the host shell script is executed on.
* Domain custom resource properties:  
  * WebLogic cluster name: `DockerCluster`
  * Domain UID: `domain1`

```
#!/bin/sh

# Setup properties  
ophost=`uname -n`
opport=31001 #externalRestHttpsPort
cluster=DockerCluster
size=3 #New cluster size
domdir=${PWD}
ns=weblogic-operator # Operator NameSpace
domainuid=domain1
  
# Retrieve service account name for given namespace   
sec=`kubectl get serviceaccount ${ns} -n ${ns} -o jsonpath='{.secrets[0].name}'`
#echo "Secret [${sec}]"
  
# Retrieve base64 encoded secret for the given service account   
enc_token=`kubectl get secret ${sec} -n ${ns} -o jsonpath='{.data.token}'`
#echo "enc_token [${enc_token}]"
  
# Decode the base64 encoded token  
token=`echo ${enc_token} | base64 --decode`
#echo "token [${token}]"
  
# Retrieve SSL certificate for the external REST endpoint from the generated yaml file for the Operator  
operator_cert_data=`kubectl get cm -n ${ns} weblogic-operator-cm -o jsonpath='{.data.externalOperatorCert}'`
#echo "operator_cert_data [${operator_cert_data}]"
  
# clean up any temporary files
rm -rf operator.rest.response.body operator.rest.stderr operator.cert.pem
  
# Decode and store the encoded SSL certificate into a pem file  
echo ${operator_cert_data} | base64 --decode > operator.cert.pem
  
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

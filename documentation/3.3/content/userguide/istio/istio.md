---
title: "Istio support"
date: 2019-08-15T13:30:04-04:00
weight: 11
description: "Lets you run the operator, and WebLogic domains managed by the operator, with Istio sidecar injection enabled. You can use Istio gateways and virtual services to access applications deployed in these domains."
---

#### Contents

- [Overview](#overview)
- [Limitations](#limitations)
- [Determining the Istio version](#determining-the-istio-version)
- [Setting up an operator with Istio support](#setting-up-an-operator-with-istio-support)
- [Creating a domain with Istio support](#creating-a-domain-with-istio-support)
  - [Setting up the domain namespace](#setting-up-the-domain-namespace)
  - [Configuring the domain resource](#configuring-the-domain-resource)
  - [Applying a Domain YAML file](#applying-a-domain-yaml-file)
  - [Exposing applications in Istio-enabled domains](#exposing-applications-in-istio-enabled-domains)
- [Traffic management](#traffic-management)
- [Distributed tracing](#distributed-tracing)
- [Automatically added network channels](#automatically-added-network-channels)
  - [Added network channels for Istio versions prior to v1.10](#added-network-channels-for-istio-versions-prior-to-v110)
  - [Added network channel for Istio versions v1.10 and later](#added-network-channel-for-istio-versions-v110-and-later)
  - [Added network channel for WebLogic EJB and servlet session state replication traffic](#added-network-channel-for-weblogic-ejb-and-servlet-session-state-replication-traffic)

#### Overview

{{% notice note %}}
These instructions assume that you are using a Kubernetes cluster with
[Istio](https://istio.io/latest/docs/setup/install/) installed and configured already.  The operator will not install
Istio for you.
{{% /notice %}}

Istio support lets you run the operator, and WebLogic domains managed by
the operator, when Istio sidecar injection is enabled. You can use
Istio gateways and virtual services to access applications deployed in these domains.
If your applications have suitable tracing code in them, then you will also be able to
use distributed tracing, such as Jaeger, to trace requests across domains and to
other components and services that have tracing enabled.

To learn more about Istio,
see [What is Istio](https://istio.io/latest/docs/concepts/what-is-istio/).  

#### Limitations

The current support for Istio has these limitations:

* The operator supports Istio versions 1.7 and later,
  and has been tested with single and multicluster
  Istio installations from 1.7.3 up to 1.11.x.

* You cannot set up a NodePort using `domain.spec.adminServer.adminService.channels`
  with a `channelName` of `default`, `default-secure`, and `default-admin`.
  Any attempt will result in an error when deploying a domain
  in combination with Istio.

* If the `istio-ingressgateway` service in your environment
  does not have an `EXTERNAL-IP` defined and
  you would like to externally run WLST commands,
  then see
  [Use WLST]({{< relref "/userguide/managing-domains/accessing-the-domain/wlst.md" >}}).  

#### Determining the Istio version

To see the Istio build version that is installed, use the `istioctl version` command.  For example:

```shell script
$ istioctl version
client version: 1.10.4
control plane version: 1.10.4
data plane version: 1.10.4 (1 proxies)
```

#### Setting up an operator with Istio support

Istio support requires labeling the operator namespace and
your domain namespaces to enable Istio automatic
sidecar injection, plus modifying your domain resource
configuration. This section describes
the steps for the operator namespace; the steps for the domain are described in later sections.

Before installing the operator,
create the namespace in which you want to run the operator and label it.

```shell
$ kubectl create namespace weblogic-operator
```
```shell
$ kubectl label namespace weblogic-operator istio-injection=enabled
```

After the namespace is labeled, you can [install the operator]({{< relref "/userguide/managing-operators/installation.md" >}}).  
When the operator pod starts, you will notice that Istio automatically injects an `initContainer` called `istio-init`
and the Envoy container `istio-proxy`.

You can validate this using the following commands:

```shell
$ kubectl --namespace weblogic-operator get pods
```
```shell
$ kubectl --namespace weblogic-operator get pod weblogic-operator-xxx-xxx -o yaml
```

In the second command, change `weblogic-operator-xxx-xxx` to the name of your pod.

#### Creating a domain with Istio support

Setting up Istio support for a domain requires labeling
its namespace and defining `domain.spec.configuration.istio` attributes.

##### Setting up the domain namespace

To allow your domains to run with Istio automatic sidecar injection enabled,
create the namespace in which you want to run the domain
and label it for automatic injection before deploying your domain.

```shell
$ kubectl create namespace domain1
```
```shell
$ kubectl label namespace domain1 istio-injection=enabled
```

##### Configuring the domain resource

To enable Istio support for a domain, you need to add a
`domain.spec.configuration.istio` section to your domain custom resource YAML file,
as shown in the following example:  

```yaml
apiVersion: "weblogic.oracle/v8"
kind: Domain
metadata:
  name: domain2
  namespace: domain1
  labels:
    weblogic.domainUID: domain2
spec:
  ... other content ...
  configuration:
    istio:
      enabled: true
      readinessPort: 8888
      replicationChannelPort: 4564
      localhostBindingsEnabled: false
```

See the following description of each `spec.configuration.istio` attribute:

* `enabled`: To enable Istio support, you must include the `istio` section
  and set `enabled: true` as shown.
* `readinessPort`: This attribute is optional
   and defaults to `8888` if not provided; it is used for a readiness health check.
* `replicationChannelPort`: This attribute is optional and defaults to `4564` if not provided;
  the operator will create a `T3` protocol WebLogic network access point on each WebLogic
  Server that is part of a cluster with this port to handle EJB and servlet session state
  replication traffic between servers.

  This setting is ignored for clusters
  where the WebLogic cluster configuration already defines a `replication-channel` attribute.
* `localhostBindingsEnabled`:
   This setting was added in operator version 3.3.3;
   it defaults to the Helm chart configuration value `istioLocalhostBindingsEnabled`,
   which in turn defaults to `true`. When `true`, the operator creates a WebLogic
   network access point with a `localhost` binding for each existing channel and protocol.
   Use `true` for Istio versions prior to 1.10 and set to `false` for versions 1.10 and later.

   |Istio version|localhostBindingsEnabled|Notes|
   |----|----|----|
   |Pre-1.10|`true`|Supported. Note that `true` is the default.|
   |Pre-1.10|`false`|Not supported.|
   |1.10 and later|`true`|Not supported.|
   |1.10 and later|`false`|Supported.|

If the `localhostBindingsEnabled` is set incorrectly for the Istio version running in a domain,
then the `weblogic-server` container in the managed server pods will
fail to reach a `ready` state due to readiness probe failures.
For example, if the `localhostBindingsEnabled` is set to
`true` when running Istio versions 1.10 and later,
then a `kubectl get pods` will have output like this:

```text
$ kubectl -n sample-domain1-ns get pods
```
```text
NAME                             READY   STATUS    RESTARTS   AGE
sample-domain1-admin-server      1/2     Running   0          2m
```

Using the `kubectl describe pod` command will show a readiness probe event failure:

```text
$ kubectl describe pod sample-domain1-admin-server -n sample-domain1-ns
```
```text
Events:
     Type     Reason       Age                  From      Message
     ----     ------       ----                 ----      -------

    Warning  Unhealthy    60s (x10 over 105s)  kubelet   Readiness probe failed: HTTP probe failed with statuscode: 500
```

Also, viewing the logging output of the `istio-proxy` container in a managed server pod
will show that the
readiness probe was unable to successfully establish
a connection to the endpoint of the managed server:

```text
$ kubectl logs sample-domain1-admin-server -n sample-domain1-ns -c istio-proxy
```
```text
2021-10-22T20:35:01.354031Z	error	Request to probe app failed: Get "http://192.168.0.93:8888/weblogic/ready": dial tcp 127.0.0.6:0->192.168.0.93:8888: connect: connection refused, original URL path = /app-health/weblogic-server/readyz
app URL path = /weblogic/ready
```

##### Applying a Domain YAML file

After a Domain YAML file is modified, apply it by:

```shell
$ kubectl apply -f domain.yaml
```

After all the servers are up, you will see output like this:

```shell
$ kubectl -n sample-domain1-ns get pods
```
```
NAME                             READY   STATUS    RESTARTS   AGE
sample-domain1-admin-server      2/2     Running   0          154m
sample-domain1-managed-server1   2/2     Running   0          153m
sample-domain1-managed-server2   2/2     Running   0          153m
```

If you use `istioctl proxy-status`, you will see the mesh status:

```text
$ istioctl proxy-status
```
```text
NAME                                                               CDS        LDS        EDS        RDS          PILOT                            VERSION
istio-ingressgateway-5c7d8d7b5d-tjgtd.istio-system                 SYNCED     SYNCED     SYNCED     NOT SENT     istio-pilot-6cfcdb75dd-87lqm     1.5.4
sample-domain1-admin-server.sample-domain1-ns                      SYNCED     SYNCED     SYNCED     SYNCED       istio-pilot-6cfcdb75dd-87lqm     1.5.4
sample-domain1-managed-server1.sample-domain1-ns                   SYNCED     SYNCED     SYNCED     SYNCED       istio-pilot-6cfcdb75dd-87lqm     1.5.4
sample-domain1-managed-server2.sample-domain1-ns                   SYNCED     SYNCED     SYNCED     SYNCED       istio-pilot-6cfcdb75dd-87lqm     1.5.4
weblogic-operator-7d86fffbdd-5dxzt.sample-weblogic-operator-ns     SYNCED     SYNCED     SYNCED     SYNCED       istio-pilot-6cfcdb75dd-87lqm     1.5.4
```

##### Exposing applications in Istio-enabled domains

When a domain is running with Istio support, you should use the Istio ingress
gateway to provide external access to applications, instead of using an ingress
controller like Traefik.  Using the Istio ingress gateway, you can also view the
traffic in Kiali and use distributed tracing from the entry point to
the cluster.

To configure external access to your domain, you need to create an Istio `Gateway` and
`VirtualService`, as shown in the following example:

```yaml
---
apiVersion: networking.istio.io/v1alpha3
kind: Gateway
metadata:
  name: domain1-gateway
  namespace: domain1
spec:
  selector:
    istio: ingressgateway
  servers:
    - hosts:
        - '*'
      port:
        name: http
        number: 80
        protocol: HTTP
---
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: domain1-virtualservice
  namespace: domain1
spec:
  gateways:
    - domain1-gateway
  hosts:
    - '*'
  http:
    - match:
        - uri:
            prefix: /console
        - port: 7001
      route:
        - destination:
            host: sample-domain1-admin-server.sample-domain1-ns.svc.cluster.local
            port:
              number: 7001
    - match:
        - uri:
            prefix: /testwebapp
        - port: 8001
      route:
        - destination:
            host: sample-domain1-cluster-cluster-1.domain1.svc.cluster.local
            port:
              number: 8001
```

This example creates a gateway that will accept requests with any host name
using HTTP on port 80, and a virtual service that will route all of
those requests to the cluster service for `cluster-1` in `domain1` in
the namespace `domain1`.  **Note**: In a production environment, `hosts` should be limited to the proper DNS name.

After the gateway and virtual service has been set up, you can access it through your ingress host and port.
Refer to [Determining the ingress IP and ports](https://istio.io/latest/docs/setup/getting-started/#determining-the-ingress-ip-and-ports).


For more information about providing ingress using Istio, see the [Istio documentation](https://istio.io/docs/tasks/traffic-management/ingress/).

#### Traffic management

Istio provides traffic management capabilities, including the ability to
visualize traffic in Kiali.  You do not need to change your applications to use
this feature.  The Istio proxy (Envoy) sidecar that is injected into your pods
provides it. The following image shows an example with traffic
flowing: in from the Istio gateway on the left, to a domain called `domain1`.

In this example, you can see how the traffic flows to the cluster services and
then to the individual Managed Servers.

{{< img "Traffic visualization with Kiali" "images/kiali.png" >}}

To learn more, see [Istio traffic management](https://istio.io/docs/concepts/traffic-management/).

#### Distributed tracing

Istio provides distributed tracing capabilities, including the ability to view
traces in Jaeger.  In order to use distributed tracing though, first you will need to
instrument your WebLogic application, for example, using the
[Jaeger Java client](https://github.com/jaegertracing/jaeger-client-java).
The following image shows an example of a distributed trace
that shows a transaction following the same path through the system
as shown in the previous image.

{{< img "Distributed tracing with Jaeger" "images/jaeger.png" >}}

To learn more, see [distrubting tracing in Istio](https://istio.io/docs/tasks/telemetry/distributed-tracing/).

#### Automatically added network channels

The operator will automatically
add network channels to each WebLogic Server
when Istio is enabled for a domain.

##### Added network channels for Istio versions prior to v1.10

When deploying a domain that is configured to support Istio versions prior to 1.10,
the operator automatically adds the following network channels
(also known as network access points) to your
WebLogic configuration so that Istio is able to route traffic:

For non-SSL traffic:

|Name|Port|Protocol|Exposed as a container port|
|----|----|--------|-----|
|`http-probe`|From configuration Istio `readinessPort` |`http`| No |
|`tcp-default`|Server listening port|`t3`| Yes |
|`http-default`|Server listening port|`http`| Yes |
|`tcp-snmp`|Server listening port|`snmp`| Yes |
|`tcp-cbt`|server listening port|`CLUSTER-BROADCAST`| No |
|`tcp-iiop`|Server listening port|`http`| No |

For SSL traffic, if SSL is enabled on the server:

|Name|Port|Protocol|Exposed as a container port|
|----|----|--------|-----|
|`tls-default`|Server SSL listening port|`t3s`| Yes |
|`https-secure`|Server SSL listening port|`https`| Yes |
|`tls-iiops`|Server SSL listening port|`iiops`| No |
|`tls-ldaps`|Server SSL listening port|`ldaps`| No |
|`tls-cbts`|Server listening port|`CLUSTER-BROADCAST-SECURE`| No |

If the WebLogic administration port is enabled on the Administration Server:

|Name|Port|Protocol|Exposed in the container port|
|----|----|--------|-----|
|`https-admin`|WebLogic administration port|`https`| Yes |

##### Added network channel for Istio versions v1.10 and later

_Background_:

Beginning with Istio version 1.10, Istio's networking behavior
was simplified. It changed
so that the Istio network proxy that runs in each Istio sidecar
(the Envoy proxy) no longer redirects
network traffic to the current pod's localhost interface,
but instead directly forwards it to the network interface associated
with the pod's IP address. This means that the operator
does not need to create additional localhost network
channels on each WebLogic pod except to enable
the readiness probe.

To learn more about changes to Istio networking beginning with Istio 1.10,
see [Upcoming networking changes in Istio 1.10](https://istio.io/latest/blog/2021/upcoming-networking-changes/).

_Channel behavior_:

When deploying a domain that is configured to support Istio versions 1.10 and later,
the operator automatically adds an HTTP protocol network channel
(also known as network access points) to your
WebLogic configuration for each server so that the pod's
readiness probe is bound to the server pod's network interface:

|Channel Name|Port|Listen address|Protocol|Exposed as a container port|
|----|----|----|--------|-----|
|`http-probe-ext`|From configuration Istio `readinessPort` | Server Pod's IP address | `http`| No |

##### Added network channel for WebLogic EJB and servlet session state replication traffic

To support WebLogic EJB and servlet session state replication traffic in an Istio service mesh,
operator versions 3.3.3 and later will automatically create a channel (network access point)
using the `domain.spec.configuration.istio.replicationChannelPort`
in the domain resource:

|Name|Port|Protocol|Exposed as a container port|
|----|----|--------|-----|
|`istiorepl`|From configuration Istio `replicationChannelPort` |`t3`| No |

The operator will also set up the `replication-channel` attribute
in each WebLogic cluster configuration:

```text
<cluster>
   <name>cluster-1</name>
   <replication-channel>istiorepl</replication-channel>
<cluster>
```
{{% notice note %}}
The operator will not create a replication channel or
alter a cluster's `replication-channel` configuration
if such a channel is already configured for a WebLogic cluster.
(This is unnecessary when the channel already exists.)
{{% /notice %}}

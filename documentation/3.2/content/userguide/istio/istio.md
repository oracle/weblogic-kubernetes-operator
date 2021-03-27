---
title: "Istio support"
date: 2019-08-15T13:30:04-04:00
weight: 6
---

#### Overview

WebLogic Server Kubernetes Operator version 2.6 and later, includes support for Istio 1.4.2 and later.
This support lets you run the operator, and WebLogic domains managed by
the operator, with Istio sidecar injection enabled.  You can use
Istio gateways and virtual services to access applications deployed in these domains.
If your applications have suitable tracing code in them, then you will also be able to
use distributed tracing, such as Jaeger, to trace requests across domains and to
other components and services that have tracing enabled.

#### Limitations

The current support for Istio has these limitations:

* It is tested with Istio 1.4.2 and later (up to 1.7.x); it is tested with both single and
  multicluster installations of Istio.

  **NOTE**: The WebLogic Server Kubernetes Operator creates Kubernetes headless Services for the domain; Istio 1.6.x does not work with headless Services. See [Headless service broken in 1.6.0](https://github.com/istio/istio/issues/24082). Instead, use Istio version 1.7 and higher. 

* You cannot expose any of the default channels; any attempt will result in an error when deploying the domain.  
* If the `istio-ingressgateway` service in your environment does not have an `EXTERNAL-IP` defined,
in order to use WLST commands, define a network access point (NAP) in your WebLogic domain and expose it as a `NodePort` in your Domain YAML file
and access it through the `NodePort` instead of accessing the channel through the Istio mesh network.

To learn more about service mesh, see [Istio](https://istio.io/latest/docs/concepts/what-is-istio/).  

#### Using the operator with Istio support

{{% notice note %}}
These instructions assume that you are using a Kubernetes cluster with
[Istio](https://istio.io/latest/docs/setup/install/) installed and configured already.  The operator will not install
Istio for you.
{{% /notice %}}

You can deploy the operator into a namespace which has Istio automatic sidecar
injection enabled.  Before installing the operator, create the namespace in which you want to run the domain and label it.

```shell
$ kubectl create namespace weblogic-operator
```
```shell
$ kubectl label namespace weblogic-operator istio-injection=enabled
```

After the namespace is labeled, you can [install the operator]({{< relref "/userguide/managing-operators/installation/_index.md" >}}).  
When the operator pod starts, you will notice that Istio automatically injects an `initContainer` called `istio-init`
and the envoy container `istio-proxy`.

You can validate this using the following commands:

```shell
$ kubectl --namespace weblogic-operator get pods
```
```shell
$ kubectl --namespace weblogic-operator get pod weblogic-operator-xxx-xxx -o yaml
```

In the second command, change `weblogic-operator-xxx-xxx` to the name of your pod.

#### Creating a domain with Istio support

You can configure your domains to run with Istio automatic sidecar injection enabled.
Before creating your domain, create the namespace in which you want to run the operator
and label it for automatic injection.

```shell
$ kubectl create namespace domain1
```
```shell
$ kubectl label namespace domain1 istio-injection=enabled
```

To enable Istio support for a domain, you need to add the
`configuration` section to your domain custom resource YAML file, as shown in the
following example:  

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
```

To enable Istio support, you must include the `istio` section
and set `enabled: true` as shown.  The `readinessPort` is optional
and defaults to `8888` if not provided; it is used for a readiness health check.

##### How Istio-enabled domains differ from regular domains

Istio enforces a number of requirements on Pods.  When you enable Istio support in the Domain YAML file, the
introspector job automatically creates configuration overrides with the necessary channels for the domain to satisfy Istio's requirements, including:

When deploying a domain with Istio sidecar injection enabled, the operator automatically adds the following network
channels using configuration overrides.

https://istio.io/latest/docs/ops/configuration/traffic-management/protocol-selection/

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


Additionally, when Istio support is enabled for a domain, the operator
ensures that the Istio sidecar is not injected into the introspector job's pods.


### Apply the Domain YAML file

After the Domain YAML file is modified, apply it by:

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

```shell
istioctl proxy-status
```
```
NAME                                                               CDS        LDS        EDS        RDS          PILOT                            VERSION
istio-ingressgateway-5c7d8d7b5d-tjgtd.istio-system                 SYNCED     SYNCED     SYNCED     NOT SENT     istio-pilot-6cfcdb75dd-87lqm     1.5.4
sample-domain1-admin-server.sample-domain1-ns                      SYNCED     SYNCED     SYNCED     SYNCED       istio-pilot-6cfcdb75dd-87lqm     1.5.4
sample-domain1-managed-server1.sample-domain1-ns                   SYNCED     SYNCED     SYNCED     SYNCED       istio-pilot-6cfcdb75dd-87lqm     1.5.4
sample-domain1-managed-server2.sample-domain1-ns                   SYNCED     SYNCED     SYNCED     SYNCED       istio-pilot-6cfcdb75dd-87lqm     1.5.4
weblogic-operator-7d86fffbdd-5dxzt.sample-weblogic-operator-ns     SYNCED     SYNCED     SYNCED     SYNCED       istio-pilot-6cfcdb75dd-87lqm     1.5.4
```

#### Exposing applications in Istio-enabled domains

When a domain is running with Istio support, you should use the Istio ingress
gateway to provide external access to applications, instead of using an ingress
controller like Traefik.  Using the Istio ingress gateway, you can also view the
traffic in Kiali and use distributed tracing from the entry point to
the cluster.

To configure external access to your domain, you need to create an Istio `Gateway` and
`VirtualService`, as shown in the example below:

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
this feature.  The Istio proxy (envoy) sidecar that is injected into your pods
provides it. The image below shows an example with traffic
flowing: In from the Istio gateway on the left, to a domain called `domain1`.

In this example, you can see how the traffic flows to the cluster services and
then to the individual Managed Servers.

{{< img "Traffic visualization with Kiali" "images/kiali.png" >}}

To learn more, see [Istio traffic management](https://istio.io/docs/concepts/traffic-management/).

#### Distributed tracing

Istio provides distributed tracing capabilities, including the ability to view
traces in Jaeger.  In order to use distributed tracing though, first you will need to
instrument your WebLogic application, for example, using the
[Jaeger Java client](https://github.com/jaegertracing/jaeger-client-java).
The image below shows an example of a distributed trace
that shows a transaction following the same path through the system
as shown in the image above.

{{< img "Distributed tracing with Jaeger" "images/jaeger.png" >}}

To learn more, see [distrubting tracing in Istio](https://istio.io/docs/tasks/telemetry/distributed-tracing/).

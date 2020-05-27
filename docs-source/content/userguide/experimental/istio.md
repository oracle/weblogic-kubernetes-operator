---
title: "Istio support"
date: 2019-08-15T13:30:04-04:00
weight: 1
---

#### Overview

WebLogic Server Kubernetes Operator version 2.3 and later includes support for Istio 1.2.2 and later.
This support allows you to run the operator itself, and WebLogic domains managed by
the operator, with Istio sidecar injection enabled.  It will allow you to use
Istio gateways and virtual services to access applications deployed in these domains.
If your applications have suitable tracing code in them, then you will also be able to
use distributed tracing, such as Jaeger, to trace requests across domains and to
other components and services that have tracing enabled.

#### Limitations

The current support for Istio has these limitations:

* It is tested with Istio 1.2.2 and later (up to 1.5), however it is tested with both single and
  multicluster installations of Istio.
* Support is provided only for domains with a single dynamic cluster.
  Multiple clusters and configured clusters are not currently supported ???

#### Using the operator with Istio support

{{% notice note %}}
These instructions assume that you are using a Kubernetes cluster with
[Istio](https://istio.io) installed and configured already.  The operator will not install
Istio for you.
{{% /notice %}}

You can deploy the operator into a namespace which has Istio automatic sidecar
injection enabled.  Before installing the operator, create the namespace you
wish to run the operator in, and label it for automatic injection.

```
$ kubectl create namespace weblogic-operator
$ kubectl label namespace weblogic-operator istio-injection=enabled
```

After the namespace is labeled, you can install the operator using the normal
method.  When the operator pod starts, you will notice that Istio automatically
injects an `initContainer` called `istio-init` and the envoy container `istio-proxy`.

You can check this using the following commands:

```
$ kubectl --namespace weblogic-operator get pods
$ kubectl --namespace weblogic-operator get pod weblogic-operator-xxx-xxx -o yaml
```

In the second command, change `weblogic-operator-xxx-xxx` to the name of your pod.

#### Creating a domain with Istio support

You can configure your domains to run with Istio automatic sidecar injection enabled.
Before creating your domain, create the namespace you wish to run the domain in,
and label it for automatic injection.

```
$ kubectl create namespace domain1
$ kubectl label namespace domain1 istio-injection=enabled
```

To enable the support for a domain, you need to add the
`configuration` section to your domain custom resource YAML file as shown in the
example below.  

```
apiVersion: "weblogic.oracle/v7"
kind: Domain
metadata:
  name: domain2
  namespace: domain1
  labels:
    weblogic.resourceVersion: domain-v2
    weblogic.domainUID: domain2
spec:
  ... other content ...
  configuration:
    istio:
      enabled: true
      readinessPort: 8888
      envoyPort: 31111
```

To enable the Istio support, you must include the `istio` section
and you must set `enabled: true` as shown.  The `readniessPort` is optional
and defaults to `8888` if not provided.  The `envoyPort` is optional and defaults to `31111` if not provided.

##### How Istio-enabled domains differ from regular domains

Istio enforces a number of requirements on pods.  When you enable Istio support in the domain resource, the
introspect job will automatcially create configuration overrrides with the necessary channels for the domain to satisy Istio's requirements:

* On the Administration Server:
    * A network channel called `istio-probe` with listen address `127.0.0.1:8888` (or
      the port you specified in the `readinessPort` setting).
    * A network channel called `istio-t3` with listen address `127.0.0.1` and the port
      you specified as the admin port.
    * A channel called `istio-ldap` with listen address `127.0.0.1` and the port
      you specified as the admin port, with only the LDAP protocol enabled.
    * The introspect job will not create any configuration network channel for external access for you.  You can create a channel called `istio-T3Channel` with listen address `127.0.0.1` and the port you specified as the T3 port in your regular WebLogic domain configuration.
* In the server template that is used to create Managed Servers in clusters:
    * A channel called `istio-probe` with listen address `127.0.0.1:8888` (or
      the port you specified in the `readinessPort` setting) and the public address
      set to the Kubernetes Service for the Managed Server.
    * A channel called `istio-t3` with listen address `127.0.0.1` and the port
      you specified as the admin port and the public address
      set to the Kubernetes Service for the Managed Server.
    * A channel called `istio-cluster` with listen address `127.0.0.1` and the port
      you specified as the admin port, with only the CLUSTER_BROADCAST protocol enabled,
      and the public address set to the Kubernetes Service for the Managed Server.
    * A channel called `istio-http` with listen address `127.0.0.1:31111` and the
      public address set to the Kubernetes Service for the Managed Server. Note that `31111`
      is the Istio proxy (envoy) port.  You can set it to according to your environment of omit it to use the default (31111)


Additionally, when the Istio support is enabled for a domain, the operator will
ensure that the Istio sidecar is not injected into the introspector job's pods.

#### Exposing applications in Istio-enabled domains

When a domain is running with the Istio support, you should use the Istio
gateway to provide external access to applications, instead of using an ingress
controller like Traefik.  Using the Istio gateway will enable you to view the
traffic in Kiali and to use distributed tracing all the way from the entry point to
the cluster, for example, the Istio gateway.

To configure external access to your domain, you need to create an Istio `gateway` and
`virtualservice` as shown in the example below:

```
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
            prefix: /
        - port: 8001
      route:
        - destination:
            host: domain1-cluster-cluster-1.domain1.svc.cluster.local
            port:
              number: 8001
```

This example creates a gateway that will accept requests with any host name
using HTTP on port 80, and a virtual service that will route all of
those requests to the cluster service for `cluster-1` in `domain1` in
the namespace `domain1`.

For more information about providing ingress using Istio, refer to the [Istio documentation](https://istio.io/docs/tasks/traffic-management/ingress/).

#### Traffic management

Istio provides traffic management capabilities, including the ability to
visualize traffic in Kiali.  You do not need to change your applications to use
this feature.  The Istio proxy (envoy) sidecar that is injected into your pods
provides this visibility. The Istio support does enable
traffic management.  The image below shows an example with traffic
flowing:

* In from the Istio gateway on the left.
* To a domain called `bobbys-front-end`.
* To a non-WebLogic application, in this case a Helidon microservice
  called `bobbys-helidon-stock-application`.
* To a second domain called `bobs-bookstore`.

In this example you can see how the traffic flows to the cluster services and
then to the individual Managed Servers.

![Traffic visualization with Kiali](/weblogic-kubernetes-operator/images/kiali.png)

You can learn more about [Istio traffic management](https://istio.io/docs/concepts/traffic-management/)
in their documentation.

#### Distributed tracing

Istio provides distributed tracing capabilities, including the ability to view
traces in Jaeger.  In order to use distributed tracing though, you will need to
instrument your WebLogic application first, for example, using the
[Jaeger Java client](https://github.com/jaegertracing/jaeger-client-java).
The image below shows an example of a distributed trace
that shows a transaction following the same path through the system
as shown in the image above.

![Distributed tracing with Jaeger](/weblogic-kubernetes-operator/images/jaeger.png)

You can learn more about [distrubting tracing in Istio](https://istio.io/docs/tasks/telemetry/distributed-tracing/)
in their documentation.

---
title: "Istio support"
date: 2019-08-15T13:30:04-04:00
weight: 1
---

### Overview

WebLogic Kubernetes Operator version 2.3 includes experimental support for Istio 1.2.2.
This support allows you to run the operator itself, and WebLogic domains managed by
the operator with Istio sidecar injection enabled.  It will allow you to use
Istio gateways and virtual services to access applications deployed in these domains.
If your applications have suitable tracing code in them, you will also be able to
use distributed tracing, such as Jaeger, to trace requests across domains and to
other components and services that have tracing enabled.

### Limitations

The current experimental support for Istio has the current limitations:

* It is tested only with Istio 1.2.2, however it is tested with both single and 
  multicluster installations of Istio.
* Support is provided only for domains that are stored in persistent 
  volumes and created with the provided sample using the WLST option. 
  We intend to support domain in image and WDT options as well, but that is not currently
  available.
* Support is provided only for domains with a single dynamic cluster.
  Multiple clusters and configured clusters are not currently supported.

### Using the operator with experimental Istio support

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

### Creating a domain with experimental Istio support

You can configure your domains to run with Istio automatic sidecar injection enabled.
Before creating your domain, create the namespace you wish to run the domain in,
and label it for automatic injection.

```
$ kubectl create namespace domain1
$ kubectl label namespace domain1 istio-injection=enabled
```

Currently, the experimental Istio support is provided only for domains stored on
persistent volumes.  To enable the support for a domain, you need to add the 
`experimental` section to your domain custom resource YAML file as shown in the
example below.  

This *must* be done in the inputs file before running the `create-domain.sh` script 
in the [sample directory]({{< relref "/samples/simple/domains/domain-home-on-pv" >}})
because the `create-domain.sh` script makes the necessary adjustments to the domain
to make it work in an Istio environment.

```
apiVersion: "weblogic.oracle/v6"
kind: Domain
metadata:
  name: domain2
  namespace: domain1
  labels:
    weblogic.resourceVersion: domain-v2
    weblogic.domainUID: domain2
spec:
  ... other content ...
  experimental:
    istio:
      enabled: true
      readinessPort: 8888
```

To enable the experimental Istio support, you must include the `istio` section
and you must set `enabled: true` as shown.  The `readniessPort` is optional
and defaults to `8888` if not provided.  

#### How Istio-enabled domains differ from regular domains

Istio enforces a number of requirements on pods.  When you enable Istio support, the
domain on persistent volume sample scripts will make the following adjustments
to your domain in order to satisy Istio's requirements:

* On the Admin server: 
    * Create a channel called `istio-probe` with listen address `127.0.0.1:8888` (or 
      the port you specified in the `readinessPort` setting).
    * Create a channel called `istio-t3` with listen address `127.0.0.1` and the port
      you specified as the admin port.
    * Create a channel called `istio-ldap` with listen address `127.0.0.1` and the port
      you specified as the admin port, with only the LDAP protocol enabled.
    * Create a channel called `istio-T3Channel` with listen
      address `127.0.0.1` and the port you specified as the T3 port.
* In the server template that is used to create managed servers in clusters:
    * Create a channel called `istio-probe` with listen address `127.0.0.1:8888` (or 
      the port you specified in the `readinessPort` setting) and the public address
      set to the Kubernetes service for the managed server.
    * Create a channel called `istio-t3` with listen address `127.0.0.1` and the port
      you specified as the admin port and the public address
      set to the Kubernetes service for the managed server.
    * Create a channel called `istio-cluster` with listen address `127.0.0.1` and the port
      you specified as the admin port, with only the CLUSTER_BROADCAST protocol enabled,
      and the public address set to the Kubernetes service for the managed server.
    * Create a channel called `istio-http` with listen address `127.0.0.1:31111` and the 
      public address set to the Kubernetes service for the managed server. Note that `31111`
      is the Istio proxy (envoy) port.
* The create domain job will be configured to disable injection of the Istio sidecar.

Additionally, the operator will adjust its behavior as follows when the experimental
Istio support is enabled for a domain:

* The operator will ensure that the Istio sidecar is not injected into the introspector
  job's pods.

### Exposing applications in Istio-enabled domains

When a domain is running with the experimental Istio support, you should use the Istio
gateway to provide external access to applications, instead of using an Ingress 
controller like Traefik.  Using the Istio gateway will enable you to view the 
traffic in Kiali and to use distributed tracing all the way from the entry point to 
the cluster, i.e. the Istio gateway.

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

Please refer to the [Istio documentation](https://istio.io/docs/tasks/traffic-management/ingress/)
for more information about providing ingress using Istio.

### Traffic management

Istio provides traffic management capabilities, including the ability to 
visualize traffic in Kiali.  You do not need to change your applications to use
this feature.  The Istio proxy (envoy) sidecar that is injected into your pods
provides this visibility. The experimental Istio support does enable
traffic management.  The image below shows an example with traffic
flowing:

* In from the Istio gateway on the left.
* To a domain called "bobbys-front-end".
* To a non-WebLogic application, in this case a Helidon microservice
  called "bobbys-helidon-stock-application".
* To a second domain called "bobs-bookstore".

In this example you can see how the traffic flows to the cluster services and 
then to the individual managed servers.

{{< img "Traffic visualization with Kiali" "images/kiali.png" >}}

You can learn more about [Istio traffic management](https://istio.io/docs/concepts/traffic-management/)
in their documentation.

### Distributed tracing

Istio provides distributed tracing capabilities, including the ability to view
traces in Jaeger.  In order to use distributed tracing though, you will need to 
instrument your WebLogic application first, for example, using the 
[Jaeger Java client](https://github.com/jaegertracing/jaeger-client-java).
The image below shows an example of a distributed trace
that shows a transaction following the same path through the system
as shown in the image above.

{{< img "Distributed tracing with Jaeger" "images/jaeger.png" >}}

You can learn more about [distrubting tracing in Istio](https://istio.io/docs/tasks/telemetry/distributed-tracing/)
in their documentation.


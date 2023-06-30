---
title: "Istio support"
date: 2019-08-15T13:30:04-04:00
weight: 6
description: "Run the operator and WebLogic domains managed by the operator when Istio sidecar injection is enabled."
---

{{< table_of_contents >}}

### Overview

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

WebLogic Kubernetes Operator assumes that you are familiar with Istio. If you are new to
Istio, we strongly recommend reading the documentation and working through the `Bookinfo`
sample application to familiarize yourself with the mesh and verify that it is working properly in your environment, before proceeding to work with the operator.

To learn more about Istio,
see [What is Istio](https://istio.io/latest/docs/concepts/what-is-istio/).  

### Limitations

The current support for Istio has these limitations:

* The operator supports Istio versions 1.10 and later,
  and has been tested with single and multicluster
  Istio installations from 1.10 up to 1.13.2.

* When using Istio in Red Hat OpenShift, operator version 4.0 and later is compatible only with Red Hat OpenShift version 4.11.x with the latest Service Mesh 2.2.3 and later.  

* You cannot set up a NodePort using `domain.spec.adminServer.adminService.channels`
  with a `channelName` of `default`, `default-secure`, and `default-admin`.
  Any attempt will result in an error when deploying a domain
  in combination with Istio.

* If the `istio-ingressgateway` service in your environment
  does not have an `EXTERNAL-IP` defined and
  you would like to externally run WLST commands,
  then see
  [Use WLST]({{< relref "/managing-domains/accessing-the-domain/wlst.md" >}}).  

### Determining the Istio version

To see the Istio build version that is installed, use the `istioctl version` command.  For example:

```shell script
$ istioctl version
client version: 1.11.1
control plane version: 1.11.1
data plane version: 1.11.1 (1 proxies)
```

### Setting up an operator with Istio support

Istio support requires labeling the operator namespace and
your domain namespaces to enable Istio automatic
sidecar injection. In this section, we describe
the steps for the operator namespace; we will describe
the steps for the domain in later sections.

Before installing the operator,
create the namespace in which you want to run the operator and label it.

```shell
$ kubectl create namespace weblogic-operator
```
Label the namespace as follows:

```shell
$ kubectl label namespace weblogic-operator istio-injection=enabled
```

After the namespace is labeled, you can [install the operator]({{< relref "/managing-operators/installation.md" >}}).

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

### Creating a domain with Istio support

Setting up Istio support for a domain requires only enabling Istio automatic sidecar injection.

To allow your domains to run with Istio automatic sidecar injection enabled,
create the namespace in which you want to run the domain
and label it for automatic injection before deploying your domain.

```shell
$ kubectl create namespace sample-domain1
```

```shell
$ kubectl label namespace sample-domain1 istio-injection=enabled
```

##### Configuring the domain resource

Beginning with WebLogic Kubernetes Operator release 4.0, you no longer have to provide the `domain.spec.configuration.istio` section to
enable Istio support for a domain.  The `domain.spec.configuration.istio` is no longer a valid field in the schema.

#### Applying a Domain YAML file

Apply a Domain YAML file by:

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
weblogic-operator-7d86fffbdd-5dxzt.weblogic-operator-ns     SYNCED     SYNCED     SYNCED     SYNCED       istio-pilot-6cfcdb75dd-87lqm     1.5.4
```

#### Exposing applications in Istio-enabled domains

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
  namespace: sample-domain1
spec:
  selector:
    istio: ingressgateway
  servers:
    - hosts:
        - 'yourdomain.dns.com'
      port:
        name: http
        number: 80
        protocol: HTTP
---
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: domain1-virtualservice
  namespace: sample-domain1
spec:
  gateways:
    - domain1-gateway
  hosts:
    - 'yourdomain.dns.com'
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
the namespace `domain1`.  **NOTE**: In a production environment, `hosts` should be limited to the proper DNS name.

After the gateway and virtual service has been set up, you can access it through your ingress host and port.
Refer to [Determining the ingress IP and ports](https://istio.io/latest/docs/setup/getting-started/#determining-the-ingress-ip-and-ports).


For more information about providing ingress using Istio, see the [Istio documentation](https://istio.io/docs/tasks/traffic-management/ingress/).

### Traffic management

Istio provides traffic management capabilities, including the ability to
visualize traffic in Kiali.  You do not need to change your applications to use
this feature.  The Istio proxy (Envoy) sidecar that is injected into your pods
provides it. The following image shows an example with traffic
flowing: in from the Istio gateway on the left, to a domain called `domain1`.

In this example, you can see how the traffic flows to the cluster services and
then to the individual Managed Servers.

{{< img "Traffic visualization with Kiali" "images/kiali.png" >}}

To learn more, see [Istio traffic management](https://istio.io/docs/concepts/traffic-management/).

### Distributed tracing

Istio provides distributed tracing capabilities, including the ability to view
traces in Jaeger.  To use distributed tracing though, first you will need to
instrument your WebLogic application, for example, using the
[Jaeger Java client](https://github.com/jaegertracing/jaeger-client-java).
The following image shows an example of a distributed trace
that shows a transaction following the same path through the system
as shown in the previous image.

{{< img "Distributed tracing with Jaeger" "images/jaeger.png" >}}

To learn more, see [distrubting tracing in Istio](https://istio.io/docs/tasks/telemetry/distributed-tracing/).

### Automatically added network channels

The operator will automatically
add network channels to each WebLogic Server
when Istio is enabled for a domain.


#### Network channel for Istio versions v1.10 and later

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
(also known as Network Access Points) to your
WebLogic configuration for each server so that the pod's
readiness probe is bound to the server pod's network interface:

|Channel Name|Port|Listen address|Protocol|Exposed as a container port|
|----|----|----|--------|-----|
|`http-probe-ext`|From configuration Istio `readinessPort` | Server Pod's IP address | `http`| No |

#### Network channel for WebLogic EJB and servlet session state replication traffic

WebLogic Kubernetes Operator versions 4.0 and later support WebLogic EJB and servlet session state replication traffic in
an Istio service mesh; it uses the default channel of the domain for replication.

### Security

Istio provides rich sets of security features that you can use to secure the Istio service mesh environments.  For details, see Istio [Security](https://istio.io/latest/docs/concepts/security/).   The following are some sample scenarios.

#### Mutual TLS

By default, all traffic between the Istio sidecar proxies use mutual TLS within the mesh. However, service within the mesh can still be accessed by other pods outside the mesh.  For example, you have `domain-1`  deployed with sidecar injection, therefore within the mesh, and another domain, `domain-2`, deployed without sidecar injection, therefore outside of the mesh. Services within `domain-2` can still access the services within `domain-1`, however the traffic will be `Plain` unencrypted traffic.   This is because by default, Istio configures the traffic using the `PERMISSIVE` mode, which means it can accept both `Plain` and `mutual TLS` traffic.  You can restrict this behavior by allowing only `mutual TLS` traffic by locking down the entire mesh or by namespace within the mesh.   

For locking down the entire mesh, you can:

```text
kubectl apply -n istio-system -f - <<EOF
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: "default"
spec:
  mtls:
    mode: STRICT
EOF
```

For namespace only, you can:

```text
kubectl apply -n <your namespace> -f - <<EOF
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: "default"
spec:
  mtls:
    mode: STRICT
EOF
```

See Istio [Mutual TLS Migration](https://istio.io/latest/docs/tasks/security/authentication/mtls-migration).

#### Authorization policy

Istio provides policy-based authorization using `AuthorizationPolicy`.  You can set up policies to deny or allow access to services deployed in the mesh.  For example, if you want to limit access to a particular service in the domain from another namespace only with a service account.

Create a service account for the client namespace.

```text
kubectl -n domain2-ns create serviceaccount privaccess
```

Set up the service account in the client deployment pod.  For example, if it is another `WebLogic Domain` in the `Operator`, specify the `ServiceAccountName` in the `domain.spec.serverPod`.


```text
spec:
  serverPod:
     serviceAccountName:  privaccess
```

Create an `AuthorizationPolicy` for the target service.

```
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: privaccess
  namespace: sample-domain1-ns
spec:
  action: ALLOW
  rules:
  - from:
    - source:
        principals: ["cluster.local/ns/domain2-ns/sa/privaccess"]
    to:
    - operation:
        methods: ["GET"]
        paths: ["/domain1-priv-service"]
```


See Istio [Authorization Policy](https://istio.io/latest/docs/reference/config/security/authorization-policy/).

#### Destination rule

Istio allows you to define traffic management polices applied to the service after the routing has occurred. You can use it to control load balancing, connection pool size from the sidecar, and outlier detection settings to detect and evict unhealthy hosts from the load balancing pool. You can also set up a service-level mutual TLS requirement instead of entire mesh or namespace-based.

For example, to configure service-level mutual TLS:

```text
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: sample-domain1-service
spec:
  host: sample-domain1-cluster-cluster-1.sample-domain1-ns.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
```

For example, to configure a sticky session for a service using hashing-based hash key `user_cookie`:

```text
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: sample-domain1-service
spec:
  host: sample-domain1-cluster-cluster-1.sample-domain1-ns.svc.cluster.local
  trafficPolicy:
    loadBalancer:
      consistentHash:
        httpCookie:
          name: user_cookie
          ttl: 0s
```


See Istio [Destination Rule](https://istio.io/latest/docs/reference/config/networking/destination-rule/).

#### Ingress gateway

Ingress gateway provides similar functions to `Kubernetes Ingress` but with more advanced functionality.

I. For example, to configure an Ingress gateway for SSL termination at the gateway:

1. Create a TLS certificate and secret.

```text
$ openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/tls1.key -out /tmp/tls1.crt -subj "/CN=secure-domain.org"
$ kubectl -n istio-system create secret tls domain1-tls-cert --key /tmp/tls1.key --cert /tmp/tls1.crt
```

2. Create the Ingress gateway.

```text
apiVersion: networking.istio.io/v1alpha3
kind: Gateway
metadata:
  name: sample-domain1-gateway
  namespace: sample-domain1-ns
spec:
  selector:
    istio: ingressgateway
  servers:
    - port:
        number: 443
        name: https
        protocol: HTTPS
      tls:
        mode: SIMPLE
        credentialName: domain1-tls-cert
      hosts:
      - 'secure-domain.org'
    - port:
        number: 80
        name: http
        protocol: HTTP
      hosts:
      - 'regular-domain.org'
```

If you are accessing the WebLogic Console through a secure gateway with SSL termination at the gateway level, enable
 `WeblogicPluginEnabled` in the WebLogic domain and add the appropriate request headers.  For example,

If you are using WDT, add the `resources` section in the model YAML file.

```text
      resources:
         WebAppContainer:
            WeblogicPluginEnabled: true
```

If you are using WLST, set the `WeblogicPluginEnabled` for each server and cluster

```text
   set('WeblogicPluginEnabled',true)
```

Set the request headers in the virtual service:  (Use `kubectl explain virtualservice.spec.http.route.headers` for help)

```text
   headers:
     request:
       remove: ['WL-Proxy-Client-IP',  'WL-Proxy-SSL']
       set:
         X-Forwarded-Proto: https
         WL-Proxy-SSL: 'true'
```  

II. For example, to configure an Ingress gateway for SSL passthrough:


```text
apiVersion: networking.istio.io/v1alpha3
kind: Gateway
metadata:
  name: sample-domain1-gateway
  namespace: sample-domain1-ns
spec:
  selector:
    istio: ingressgateway
  servers:
    - port:
        number: 443
        name: https
        protocol: HTTPS
      tls:
        mode: PASSTHROUGH
      hosts:
      - 'secure-domain.org'
    - port:
        number: 80
        name: http
        protocol: HTTP
      hosts:
      - 'regular-domain.org'
```

The virtual service will then configure to match the `tls` rule.

```text
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: sample-domain1-virtualservice
  namespace: sample-domain1-ns
spec:
  gateways:
    - sample-domain1-gateway
  hosts:
    - secure-domain.org
  tls:
  - match:
    - port: 443
      sniHosts:
      - secure-domain.org
    route:
    - destination:
        host: sample-domain1-admin-server
        port:
          number: 9002    
```

See Istio [Ingress](https://istio.io/latest/docs/tasks/traffic-management/ingress).

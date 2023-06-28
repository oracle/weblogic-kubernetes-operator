---
title: "Use the Administration Console"
date: 2019-02-23T17:39:15-05:00
draft: false
weight: 1
description: "Use the WebLogic Server Administration Console with domains running in Kubernetes."
---

{{< table_of_contents >}}

### Introduction
You can access the WebLogic Server Administration Console external to the Kubernetes cluster using the following approaches:

   * [Use a load balancer](#use-a-load-balancer)

   * Use an [Administration Server `NodePort`](#use-an-administration-server-nodeport)

   * [Use a `kubectl port-forward` connection](#use-a-kubectl-port-forward-connection)

**NOTES**:
 * For production use cases, Oracle recommends using a load balancer with ingress path routing rules and an SSL port to access the WebLogic Server Administration Console.

 * To verify that your load balancer, NodePort, or `kubectl port-forward` setup is working as expected, see [Test]({{< relref "#test" >}}).

 * Do not use the WebLogic Server Administration Console to start or stop servers, or for scaling clusters. See [Starting and stopping servers]({{< relref "/managing-domains/domain-lifecycle/startup#starting-and-stopping-servers" >}}) and [Scaling]({{< relref "/managing-domains/domain-lifecycle/scaling.md" >}}).
 * If your domain home type is either [Domain in Image]({{< relref "/samples/domains/domain-home-in-image/_index.md" >}}) or [Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}}), then do not use the Administration Console to make changes to the WebLogic domain configuration because these changes are ephemeral and will be lost when servers restart. See [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).

{{% notice warning %}}
Externally exposing administrative, RMI, or T3 capable WebLogic channels
using a Kubernetes `NodePort`, load balancer,
port forwarding, or a similar method can create an insecure configuration.
For more information, see [External network access security]({{<relref "/security/domain-security/weblogic-channels.md">}}).
{{% /notice %}}

### Use a load balancer
To access the WebLogic Server Administration Console through a load balancer, first set up an [Ingress]({{< relref "/managing-domains/accessing-the-domain/ingress/_index.md">}}). This, in combination with SSL, is the best practice approach for production use cases.

{{% notice note %}} The following `path-routing` ingress instructions do not apply when you need to concurrently access multiple domains in the same Kubernetes cluster through the same external load balancer port. For the multiple domain use case, see the [Host-based routing](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/traefik/README.md#host-based-routing) sample and make sure that the host names are resolvable by your DNS server (for example, `domain1.org` and `domain2.org` in the sample).
{{% /notice %}}

#### Configure ingress path routing rules for a non-SSL port
The following example sets up an ingress path routing rule to access a WebLogic Server Administration Console through a non-SSL port.

1. Set up a `path-routing` YAML file for a Traefik load balancer:

   ```yaml
   apiVersion: traefik.containo.us/v1alpha1
   kind: IngressRoute
   metadata:
     annotations:
       kubernetes.io/ingress.class: traefik
     name: traefik-pathrouting-1
     namespace: weblogic-domain
   spec:
     routes:
     - kind: Rule
       match: PathPrefix(`/console`)
       services:
       - kind: Service
         name: domain1-adminserver
         namespace: weblogic-domain
         port: 7001
   ```

2. To access the WebLogic Server Administration Console, open the following URL from your browser:

   ```
   http://${HOSTNAME}:${LB_PORT}/console
   ```
   Where:

     * `${HOSTNAME}` is where the ingress load balancer is running.

     * To determine the `${LB_PORT}` when using a Traefik load balancer:

        `$ export LB_PORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="web")].nodePort}')`

If you have an [FMW Infrastructure]({{< relref "/managing-fmw-domains.md" >}}) domain, then you can add an ingress path routing rule for the PathPrefix `/em` and access Fusion Middleware Control (Enterprise Manager) using the following URL:

```
http://${HOSTNAME}:${LB_PORT}/em
```
#### Configure ingress path routing rules for an SSL port and enable `WebLogic Plugin Enabled`
The following example sets up load balancer routing for access to the WebLogic Server Administration Console through an SSL port.

1. Enable the `WebLogic Plugin Enabled` setting in the WebLogic configuration:

   The WebLogic configuration setting `WebLogic Plugin Enabled`, when set to `true`, informs WebLogic Server about the presence of a load balancer proxy. Failure to have this setting enabled causes unexpected results in cases where the client IP address is required or when SSL terminates at the load balancer.

   When using WDT to configure a WebLogic domain, use the resource section at the domain level in a model YAML file:
   ```yaml
   resources:
        WebAppContainer:
            WeblogicPluginEnabled: true
   ```
   When using a `WLST` script to configure a WebLogic domain, use these commands:
   ```javascript
   # Configure the Administration Server
   cd('/Servers/AdminServer')
   set('WeblogicPluginEnabled',true)
   ...
   cd('/Clusters/%s' % cluster_name)
   set('WeblogicPluginEnabled',true)
   ```
2. Configure an ingress path routing rule and update the ingress resource with a `customRequestHeaders` value:

   For example, see the following `path-routing` YAML file for a Traefik load balancer. In the case of SSL termination, Traefik must pass a custom header `WL-Proxy-SSL:true` to the WebLogic Server endpoints.

   ```yaml
   apiVersion: traefik.containo.us/v1alpha1
   kind: IngressRoute
   metadata:
     annotations:
       kubernetes.io/ingress.class: traefik
     name: traefik-console-tls
     namespace: weblogic-domain
   spec:
     entryPoints:
      - websecure
     routes:
     - kind: Rule
       match: PathPrefix(`/console`)
       middlewares:
       - name: tls-console-middleware
         namespace: weblogic-domain
       services:
       - kind: Service
         name: domain1-adminserver
         namespace: weblogic-domain
         port: 7002
   ---
   apiVersion: traefik.containo.us/v1alpha1
   kind: Middleware
   metadata:
     name: tls-console-middleware
     namespace: weblogic-domain
   spec:
     headers:
       customRequestHeaders:
         WL-Proxy-SSL: "true"
       sslRedirect: true
   ```
3. Access the WebLogic Server Administration Console using the HTTPS port:

   Get the SSL port from the Kubernetes service:
   ```shell
   # Get the ingress controller secure web port
   $ SSLPORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="websecure")].nodePort}')
   ```
   From your browser, use the following URL to access the WebLogic Server Administration Console:
   ```
   https://${HOSTNAME}:${SSLPORT}/console
   ```

   If you have an [FMW Infrastructure]({{< relref "/managing-fmw-domains.md" >}}) domain, then you can add an ingress path routing rule for the PathPrefix `/em` and access Fusion Middleware Control (Enterprise Manager) using the following URL:

   ```
   https://${HOSTNAME}:${SSLPORT}/em
   ```

### Use an Administration Server `NodePort`

Use the following steps to configure a `NodePort` to access the WebLogic Server Administration Console:

1. Update the WebLogic Administration Server configuration to add a Network Access Point (custom channel) with the HTTP protocol, and expose this channel on a NodePort service using the `domain.spec.adminServer.adminService.channels` attribute.

   For an example of setting up the `NodePort` on an Administration Server, see [Use a `NodePort`]({{< relref "/managing-domains/accessing-the-domain/wlst#use-a-nodeport" >}}). For information about the `NodePort` Service on an Administration Server, see the [Domain resource](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md) document.

2. From your browser, use the following URL to access the WebLogic Server Administration Console:

   ```
   http://hostname:adminserver-NodePort/console
   ```
   The `adminserver-NodePort` is the port number of the Administration Server outside the Kubernetes cluster.

   If you have an [FMW Infrastructure]({{< relref "/managing-fmw-domains.md" >}}) domain, then you can also access Fusion Middleware Control (Enterprise Manager) using the following URL:

   ```
   http://hostname:adminserver-NodePort/em
   ```

### Use a `kubectl port-forward` connection
A Kubernetes port forward command is convenient for development use cases but is _not recommended_ for production use cases. It creates a local process external to a Kubernetes cluster that accepts external traffic on a dedicated local port and forwards this traffic to a specific pod and port in the Kubernetes cluster. If you have multiple domains, then each domain will require its own dedicated port forward command and a separate local port.

1. Forward a local port (that is external to
   Kubernetes) to the administration port of the
   Administration Server Pod according to these
   [instructions]({{< relref "/managing-domains/accessing-the-domain/port-forward.md" >}}).

   **NOTE**: If you plan to access the WebLogic Server Administration Console from a browser
   on a different machine than the port forwarding command,
   then the port forwarding command needs to specify an `--address` parameter
   with an externally accessible IP address for the machine that is running the command.

1. In the browser, use the following URL:

   ```
   http://${HOSTNAME}:${LOCAL_PORT}/console
   ```
   Where:

     * `${HOSTNAME}` is the DNS address or the IP address of the machine
       where the `kubectl port-forward` command is running. This is
       customizable using the `--address` parameter
       and is `localhost` or `127.0.0.1`, by default.

     * `${LOCAL_PORT}` is the local port specified on the `kubectl port-forward` command line.

   If you have an [FMW Infrastructure]({{< relref "/managing-fmw-domains.md" >}}) domain, then you can also access Fusion Middleware Control (Enterprise Manager) using the following URL:

   ```
   http://${HOSTNAME}:${LOCAL_PORT}/em
   ```

### Test

To verify that your WebLogic Server Administration Server URL is correct, and to verify that that your load balancer,
`NodePort`, or `kubectl port-forward` are working as expected, run the following curl command at the same location as your browser:


```
$ curl http://${HOSTNAME}:${LB_PORT}/console > /dev/null && echo "Connection succeeded."
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100   271  100   271    0     0  90333      0 --:--:-- --:--:-- --:--:-- 90333
Connection succeeded.
```

If successful, then you will see the `Connection succeeded` message in the output from the command.

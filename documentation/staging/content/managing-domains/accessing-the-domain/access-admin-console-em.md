---
title: "Access the Administration Console"
date: 2019-02-23T17:39:15-05:00
draft: false
weight: 1
description: "Access the WebLogic Admin Console to manage domains running in Kubernetes."
---

You can use the WebLogic Server Administration Console to monitor and manage a WebLogic domain running in Kubernetes.

{{% notice note %}} Do not use the WebLogic Server Administration Console to start or stop servers, or for scaling clusters. See [Starting and stopping servers]({{< relref "/managing-domains/domain-lifecycle/startup#starting-and-stopping-servers" >}}) and [Scaling]({{< relref "/managing-domains/domain-lifecycle/scaling.md" >}}). In addition, if your domain home type is either [Domain in Image]({{< relref "/samples/domains/domain-home-in-image/_index.md" >}}) or [Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}}), then do not use the Administration Console to make changes to the WebLogic domain configuration as these changes are ephemeral and will be lost when servers restart. See [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).
{{% /notice %}}

To setup WebLogic Server Administration Console access to a domain running in Kubernetes, you can:
   * Deploy a load balancer with [ingress path routing rules for non-SSL port](#configure-ingress-path-routing-rules) and [SSL port](#access-the-weblogic-server-administration-console-through-the-ssl-port).

   * Use an [Administration Server `NodePort`](#use-an-administration-server-nodeport).

   * [Use a `kubectl port-forward` connection](#use-a-kubectl-port-forward-connection).

{{% notice warning %}}
Externally exposing administrative, RMI, or T3 capable WebLogic channels
using a Kubernetes `NodePort`, load balancer,
port forwarding, or a similar method can create an insecure configuration.
For more information, see [External network access security]({{<relref "/security/domain-security/weblogic-channels.md">}}).
{{% /notice %}}


#### Configure ingress path routing rules

1. Configure an ingress path routing rule. For information about ingresses, see the [Ingress]({{< relref "/managing-domains/accessing-the-domain/ingress/_index.md">}}) documentation.

   For an example, see the following `path-routing` YAML file for a Traefik load balancer. If you have multiple domains managed by a single ingress controller, then see the [Host-based routing](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/traefik/README.md#host-based-routing) example or consider using the [Remote Console]({{< relref "/managing-domains/accessing-the-domain/admin-console/_index.md">}}).
 
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

2. Open the following URL from your browser to access the WebLogic Server Administration Console:

   ```
   http://${HOSTNAME}:${LB_PORT}/console
   ```
   Where:
   
     * `${HOSTNAME}` is where the ingress load balancer is running.
   
     * To determine the `${LB_PORT}` when using a Traefik load balancer:

        `$ export LB_PORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="web")].nodePort}')`

   If you have an [FMW Infrastructure]({{< relref "/managing-fmw-domains.md" >}}) type domain, then you can add an ingress path routing rule for PathPrefix `/em` and access the Fusion Middleware Control (Enterprise Manager) Console using the following URL:

   ```
   http://${HOSTNAME}:${LB_PORT}/em
   ```
#### Access the WebLogic Server Administration Console through the SSL port 
1. Enable `WebLogic Plugin Enabled` on the WebLogic domain level

   If you are using WDT to configure the WebLogic domain, you need to add the following resource section at the domain level to the model YAML file.
   ```yaml
   resources:
        WebAppContainer:
            WeblogicPluginEnabled: true
   ```
   If you are using a WLST script to configure the domain, then the following modifications are needed to the respective WLST python script.
   ```javascript
   # Configure the Administration Server
   cd('/Servers/AdminServer')
   set('WeblogicPluginEnabled',true)
   ...
   cd('/Clusters/%s' % cluster_name)
   set('WeblogicPluginEnabled',true)
   ```
2. Configure an ingress path routing rule and update the ingress resource with customRequestHeaders value

   For an example, see the following `path-routing` YAML file for a Traefik load balancer. In case of SSL termination, Traefik must pass a custom header `WL-Proxy-SSL:true` to the WebLogic Server endpoints. Following example creates the Traefik Middleware custom resource with the custom request header `WL-Proxy-SSL:true`. If you have multiple domains managed by a single ingress controller, then see the [Host-based secured routing](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/charts/traefik/README.md#host-based-secured-routing) example or consider using the [Remote Console]({{< relref "/managing-domains/accessing-the-domain/admin-console/_index.md">}}).

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
         port: 7001
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
3. Create ingress resource

   Save the above configuration as `traefik-tls-console.yaml`.
   ```shell
   $ kubectl create -f traefik-tls-console.yaml
   ```

4. Access the WebLogic Server Administration Console using the HTTPS port

   Get the SSL port from the Kubernetes service. 
   ```shell
   # Get the ingress controller secure web port
   $ SSLPORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="websecure")].nodePort}')
   ```
   Use the following URL from your browser to access the WebLogic Server Administration Console:
   ```
   https://${HOSTNAME}:${SSLPORT}/console
   ```

   If you have an [FMW Infrastructure]({{< relref "/managing-fmw-domains.md" >}}) type domain, then you can add an ingress path routing rule for PathPrefix `/em` and access the Fusion Middleware Control (Enterprise Manager) Console using the following URL:

   ```
   https://${HOSTNAME}:${SSLPORT}/em
   ```

#### Use an Administration Server `NodePort`

1. Configure the Administration Server to expose an externally accessible NodePort in the Domain resource.

   For an example of setting up the `NodePort` on an Administration Server, see [Use a `NodePort`]({{< relref "/managing-domains/accessing-the-domain/wlst#use-a-nodeport" >}}). For information about the `NodePort` Service on an Administration Server, see the [Domain resource](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md) document.

2. Use the following URL from your browser to access the WebLogic Server Administration Console:

   ```
   http://hostname:adminserver-NodePort/console
   ```
   The `adminserver-NodePort` is the port number of the Administration Server outside the Kubernetes cluster.

   If you have an [FMW Infrastructure]({{< relref "/managing-fmw-domains.md" >}}) type domain, then you can also access the Fusion Middleware Control (Enterprise Manager) Console using the following URL:
   
   ```
   http://hostname:adminserver-NodePort/em
   ```

#### Use a `kubectl port-forward` connection

1. Forward a local port (that is external to
   Kubernetes) to the administration port of the
   Administration Server Pod according to these
   [instructions]({{< relref "/managing-domains/accessing-the-domain/port-forward.md" >}}).

   **NOTE:** If you plan to access the WebLogic Server Administration Console from a browser
   on a different machine than the port forwarding command,
   then the port forwarding command needs to specify a `--address` parameter
   with the IP address of the machine that is hosting the command.

1. Use the below URL using either the local hostname or IP address
   from the `port-forward` command in the first step, plus the local port from
   this same command to access the WebLogic Server Administration Console. For example:

   ```
   http://${HOSTNAME}:${LOCAL_PORT}/console
   ```
   Where:

     * `${HOSTNAME}` is the hostname or the defined IP address of the machine
       where the `kubectl port-forward` command is running. This is
       customizable on the `port-forward` command and is `localhost`
       or `127.0.0.1`, by default.

     * `${LOCAL_PORT}` is the local port where the `kubectl port-forward` command is running.
       This is specified on the `port-forward` command.

   If you have an [FMW Infrastructure]({{< relref "/managing-fmw-domains.md" >}}) type domain, then you can also access the Fusion Middleware Control (Enterprise Manager) Console using the following URL:

   ```
   http://${HOSTNAME}:${LOCAL_PORT}/em
   ```

### Test 
To verify that your load balancer, `NodePort`, or `kubectl port-forward` setup is working as expected, 
see [Test]({{< relref "/managing-domains/accessing-the-domain/admin-console#test" >}}).


---
title: "Use the Remote Console"
date: 2019-02-23T17:39:15-05:00
draft: false
weight: 1.5
description: "Use the WebLogic Remote Console with domains running in Kubernetes."
---

{{< table_of_contents >}}

### Introduction
The WebLogic Remote Console is a lightweight, open source console that does not need to be collocated with a WebLogic Server domain.
It is an _alternative_ to the WebLogic Server Administration Console.
You can install and run the Remote Console anywhere. For an introduction, read the blog, ["The NEW WebLogic Remote Console"](https://blogs.oracle.com/weblogicserver/new-weblogic-server-remote-console).
For detailed documentation, see the [WebLogic Remote Console](https://oracle.github.io/weblogic-remote-console/).

A major benefit of using the Remote Console is that it runs in your browser or as a desktop application, and can be used to connect to different WebLogic Server instances.
You can use the Remote Console with WebLogic Server _slim_ installers, available on the [OTN](https://www.oracle.com/middleware/technologies/weblogic-server-installers-downloads.html)
or [OSDC](https://edelivery.oracle.com/osdc/faces/Home.jspx).
Slim installers reduce the size of WebLogic Server downloads, installations, container images, and Kubernetes pods.
For example, a WebLogic Server 12.2.1.4 slim installer download is approximately 180 MB smaller.

The Remote Console is deployed as a standalone application, which can connect to multiple WebLogic Server Administration Servers using REST APIs.
You connect to the Remote Console and, when prompted, supply the WebLogic Server login credentials
along with the URL of the WebLogic Server Administration Server's administration port to which you want to connect.

**NOTES**:  
  * An Administration Server administration port typically is the same as its default port unless either an SSL port or an administration port is configured and enabled.
  * If your domain home type is either [Domain in Image]({{< relref "/samples/domains/domain-home-in-image/_index.md" >}}) or [Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}}), then do not use the WebLogic Remote Console to make changes to the WebLogic domain configuration because these changes are ephemeral and will be lost when servers restart. See [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).


{{% notice warning %}}
Externally exposing administrative, RMI, or T3 capable WebLogic channels
using a Kubernetes `NodePort`, load balancer,
port forwarding, or a similar method can create an insecure configuration.
For more information, see [External network access security]({{<relref "/security/domain-security/weblogic-channels.md">}}).
{{% /notice %}}


### Setup

To set up access to WebLogic Server domains running in Kubernetes using the Remote Console:

1. Install, configure, and start the Remote Console according to these [instructions](https://oracle.github.io/weblogic-remote-console/setup/).

   **NOTE**: These instructions assume that you are installing and running the Remote Console externally to your Kubernetes cluster.

1. For [additional functionality](https://oracle.github.io/weblogic-remote-console/setup/console/#ext), incorporate and deploy the WebLogic Remote Console extension in your 12.2.1.4 14.1.1, and 14.1.2 domains. **NOTE**: As a best practice, make sure that you are using the same versions of the WebLogic Remote Console and the WebLogic Remote Console Extension, otherwise you might lose functionality.

    a. From [https://github.com/oracle/weblogic-remote-console/releases](https://github.com/oracle/weblogic-remote-console/releases), download the Remote Console extension WAR file, [console-rest-ext-[version].war](https://github.com/oracle/weblogic-remote-console/releases/download/v2.4.10/console-rest-ext-9.0.war).

    b. Using the WebLogic Deploy Tooling (WDT) Archive Helper Tool, modify the WDT application archive to include the Remote Console Extension downloaded in the previous step. For example:

    ```
    /Directory to WDT/weblogic-deploy/bin/archiveHelper.sh add weblogicRemoteConsoleExtension -archive_file=/Directory to WDT application archive/archive.zip -source=/Directory to Remote Console Extension/console-rest-ext[version].war
    ```

    For more information, see the [Archive Helper Tool](https://oracle.github.io/weblogic-deploy-tooling/userguide/tools/archive_helper/) documentation.

    c. With the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/), create an auxiliary image and include the archive modified in the previous step.

    d. Provision or update the domain using the new auxiliary image.

1. When you first launch the Remote Console, it will prompt you with a login dialog for a WebLogic Server Administration Server URL. To give the Remote Console access to an Administration Server running in Kubernetes, you can:
   * Use an [Administration Server `NodePort`](#use-an-administration-server-nodeport).

   * Deploy a load balancer with [ingress path routing rules](#configure-ingress-path-routing-rules).

   * [Use a `kubectl port-forward` connection](#use-a-kubectl-port-forward-connection).

   **NOTE**: If you want the Remote Console to use SSL to connect to the WebLogic Server Administration Server,
     then see [Connect to a WebLogic domain using SSL/TLS](https://oracle.github.io/weblogic-remote-console/userguide/advanced-settings/#ssl).


#### Use an Administration Server `NodePort`

For the Remote Console to connect to the Kubernetes WebLogic Server Administration Server’s `NodePort`, use the following URL after you have launched the Remote Console
 and it prompts for the location of your WebLogic Server Administration Server:

```
http://hostname:adminserver-NodePort/
```

The `adminserver-NodePort` is the port number of the Administration Server outside the Kubernetes cluster.
For information about the `NodePort` Service on an Administration Server, see the [Domain resource](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md) document.
For an example of setting up the `NodePort` on an Administration Server,
see [Use a `NodePort` for WLST]({{< relref "/managing-domains/accessing-the-domain/wlst#use-a-nodeport" >}}).

#### Configure ingress path routing rules

1. Configure an ingress path routing rule. For information about ingresses, see the [Ingress]({{< relref "/managing-domains/accessing-the-domain/ingress/_index.md" >}}) documentation.

   For an example, see the following `path-routing` YAML file for a Traefik load balancer:

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
       match: PathPrefix(`/`)
       services:
       - kind: Service
         name: domain1-adminserver
         namespace: weblogic-domain
         port: 7001
   ```


1. After you have connected to the Remote Console with your browser,
   it will prompt for the location of your WebLogic Server Administration
   Server.
   For the Remote Console to connect to the Kubernetes WebLogic Server Administration Server, supply a URL that resolves to the load balancer host and ingress that you supplied in the previous step. For example:

   ```
   http://${HOSTNAME}:${LB_PORT}/
   ```
   Where:

     * `${HOSTNAME}` is where the ingress load balancer is running.

     * To determine the `${LB_PORT}` when using a Traefik load balancer:

        `$ export LB_PORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="web")].nodePort}')`

#### Use a `kubectl port-forward` connection

1. Forward a local port (that is external to
   Kubernetes) to the administration port of the
   Administration Server Pod according to these
   [instructions]({{< relref "/managing-domains/accessing-the-domain/port-forward.md" >}}).

   **NOTE**: If you plan to run the Remote Console
   on a different machine than the port forwarding command,
   then the port forwarding command needs to specify a `--address` parameter
   with the IP address of the machine that is hosting the command.

1. After you have connected to the Remote Console with your browser,
   it will prompt you for the location of your WebLogic Server Administration
   Server.
   Supply a URL using the local hostname or IP address
   from the `port-forward` command in the first step, plus the local port from
   this same command. For example:

   ```
   http://${LOCAL_HOSTNAME}:${LOCAL_PORT}/
   ```
   Where:

     * `${LOCAL_HOSTNAME}` is the hostname or the defined IP address of the machine
       where the `kubectl port-forward` command is running. This is
       customizable on the `port-forward` command and is `localhost`
       or `127.0.0.1`, by default.

     * `${LOCAL_PORT}` is the local port where the `kubectl port-forward` command is running.
       This is specified on the `port-forward` command.

### Test

To verify that your WebLogic Server Administration Server URL is correct, and to verify that that your load balancer,
`NodePort`, or `kubectl port-forward` are working as expected, run the following curl commands at the same location as your browser:


```
$ curl --user username:password \
     http://${HOSTNAME}:${LB_PORT}/management/weblogic/latest/domainRuntime?fields=name\&links=none ; echo

$ curl --user username:password \
     http://${HOSTNAME}:${LB_PORT}/management/weblogic/latest/serverRuntime?fields=name\&links=none ; echo
```

These commands access the REST interface of the WebLogic Server Administration Server in a way that is similar to the Remote Console's use of REST.
If successful, then the output from the two commands will be `{"name": "your-weblogic-domain-name"}` and `{"name": "your-weblogic-admin-server-name"}`, respectively.

If you want to see the full content of the domainRuntime and serverRuntime beans, then rerun the commands
but remove `?fields=name\&links=none`, which is appended at the end of each URL.

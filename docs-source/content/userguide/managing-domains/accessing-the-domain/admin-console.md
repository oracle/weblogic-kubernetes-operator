---
title: "Use the Remote Console"
date: 2019-02-23T17:39:15-05:00
draft: false
weight: 2
description: "Use the Oracle WebLogic Server Remote Console to manage a domain running in Kubernetes."
---

The Oracle WebLogic Server Remote Console is a lightweight, open source console that does not need to be collocated with a WebLogic Server domain.
You can install and run the Remote Console anywhere. For an introduction, read the blog, ["The NEW WebLogic Server Remote Console"](https://blogs.oracle.com/weblogicserver/new-weblogic-server-remote-console).
For detailed documentation, see the [Oracle WebLogic Server Remote Console](https://github.com/oracle/weblogic-remote-console) GitHub project.

A major benefit of using the Remote Console is that it runs in your browser and can be used to connect to different WebLogic Server instances.
You can use the Remote Console with WebLogic Server _slim_ installers, available on the [OTN](https://www.oracle.com/middleware/technologies/weblogic-server-installers-downloads.html)
or [OSDC](https://edelivery.oracle.com/osdc/faces/Home.jspx;jsessionid=LchBX6sgzwv5MwSaamMxrIIk-etWJLb0IyCet9mcnqAYnINXvWzi!-1201085350).
Slim installers reduce the size of WebLogic Server downloads, installations, container images, and Kubernetes pods.
For example, a WebLogic Server 12.2.1.4 slim installer download is approximately 180 MB smaller.


The Remote Console is deployed as a standalone Java program, which can connect to multiple WebLogic Server Administration Servers using REST APIs.
You connect to the Remote Console using a web browser and, when prompted, supply the WebLogic Server login credentials
along with the URL of the WebLogic Server Administration Server's administration port to which you want to connect.

**Note**:  An Administration Server administration port typically is the same as its default port unless either an SSL port or an administration port is configured and enabled.

### Setup

To set up access to WebLogic Server domains running in Kubernetes using the Remote Console:

1. Install, configure, and start the Remote Console according to these [instructions](https://github.com/oracle/weblogic-remote-console/blob/master/site/install_config.md).

1. To give the Remote Console access to an Administration Server running in Kubernetes, you can:

   * Use an [Administration Server `NodePort`](#use-an-administration-server-nodeport).

   * Deploy a load balancer with [ingress path routing rules](#configure-ingress-path-routing-rules).


#### Use an Administration Server `NodePort`

For the Remote Console to connect to the Kubernetes WebLogic Server Administration Server’s `NodePort`, use the URL:

```
http://hostname:adminserver-NodePort/
```

The `adminserver-NodePort` is the port number of the Administration Server outside the Kubernetes cluster.
For information about the `NodePort` Service on an Administration Server, see the [Domain resource](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/docs/domains/Domain.md) document.

{{% notice warning %}}
Exposing administrative, RMI, or T3 capable channels using a Kubernetes `NodePort`
can create an insecure configuration. In general, only HTTP protocols should be made available externally and this exposure
is usually accomplished by setting up an external load balancer that can access internal (non-`NodePort`) services.
For more information, see [T3 channels]({{<relref "/security/domain-security/weblogic-channels#weblogic-t3-channels">}}).
{{% /notice %}}

#### Configure ingress path routing rules

1. Configure an ingress path routing rule. For more information about ingresses, see the [Ingress]({{< relref "/userguide/managing-domains/ingress/_index.md" >}}) documentation.

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

1. For the Remote Console to connect to the Kubernetes WebLogic Server Administration Server, use the URL:

   ```
   http://${HOSTNAME}:${LB_PORT}/
   ```
   Where:

     * `${HOSTNAME}` is where you start up the WebLogic domain.

     * To determine the `${LB_PORT}` when using a Traefik load balancer:

        `$ export LB_PORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="web")].nodePort}')`

### Test

To verify the connection and that the correct `hostname:port` is being used, use the following curl command to access the REST interface of WebLogic Server Administration Server:

```
curl --user username:password http://${HOSTNAME}:${LB_PORT}/management/weblogic/latest/
```

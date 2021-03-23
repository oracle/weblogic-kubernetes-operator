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

A major benefit of using the Remote Console is that you don't need to install or run the WebLogic Server Administration Console on WebLogic Server instances.
You can use the Remote Console with WebLogic Server **slim** installers, available on the [OTN](https://www.oracle.com/middleware/technologies/weblogic-server-installers-downloads.html)
or [OSDC](https://edelivery.oracle.com/osdc/faces/Home.jspx;jsessionid=LchBX6sgzwv5MwSaamMxrIIk-etWJLb0IyCet9mcnqAYnINXvWzi!-1201085350).
Slim installers reduce the size of WebLogic Server downloads, installations, container images, and Kubernetes pods.
For example, a WebLogic Server 12.2.1.4 slim installer download is approximately 180 MB smaller.

## Use the Remote Console

To access WebLogic Server domains running in Kubernetes:

1. Install, configure, and start the Remote Console according to these [instructions](https://github.com/oracle/weblogic-remote-console/blob/master/site/install_config.md).

1. For the Remote Console to access the Administration Server running in Kubernetes, you can:

   * Use [curl](#use-curl).
   * Use the [Administration Server `NodePort`](#use-the-administration-server-nodeport).
   * Configure [ingress path routing rules](#configure-ingress-path-routing-rules).


### Use curl

Access the REST interface of the WebLogic Server Administration Server to verify the connection and that the correct `hostname:port` is being used:

```
$ curl --user username:password http://${HOSTNAME}:${LB_PORT}/console/login/LoginForm.jsp
```

* `${HOSTNAME}` is where you start up the WebLogic domain.

* To determine `${LB_PORT}` when using a Traefik load balancer:

   `$ export LB_PORT=$(kubectl -n traefik get service traefik-operator -o jsonpath='{.spec.ports[?(@.name=="web")].nodePort}')`


### Use the Administration Server `NodePort`

For the Remote Console to connect to the Kubernetes WebLogic Server Administration Server’s `NodePort`:

```
http://hostname:adminserver-NodePort/
```

### Configure ingress path routing rules

1. Configure an ingress path routing rule. For example, see the following `path-routing` YAML file for a Traefik load balancer:

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
       match: PathPrefix(`/domain1`)
       services:
       - kind: Service
         name: domain1-cluster-dockercluster
         namespace: weblogic-domain
         port: 8001
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

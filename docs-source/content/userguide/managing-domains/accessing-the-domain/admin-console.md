---
title: "Use the Remote Console"
date: 2019-02-23T17:39:15-05:00
draft: false
weight: 2
description: "Use the Oracle WebLogic Server Remote Console to manage a domain running in Kubernetes."
---
Use the Remote Console to access WebLogic domains running in Kubernetes.

The Oracle WebLogic Server Remote Console is a lightweight, open source console that does not need to be collocated with a WebLogic Server domain.
You can install and run the Remote Console anywhere. For an introduction, read the blog, "The NEW WebLogic Server Remote Console."
For detailed documentation, see the [Oracle WebLogic Server Remote Console](https://github.com/oracle/weblogic-remote-console) GitHub project.

A major benefit of using the Remote Console is that you don't need to install or run the WebLogic Server Administration Console on WebLogic Server instances.
You can use the Remote Console with WebLogic Server slim installers, available on the Oracle Technology Network [(OTN)](https://www.oracle.com/middleware/technologies/weblogic-server-installers-downloads.html)
or Oracle Software Delivery Cloud [(OSDC)](https://edelivery.oracle.com/osdc/faces/Home.jspx;jsessionid=LchBX6sgzwv5MwSaamMxrIIk-etWJLb0IyCet9mcnqAYnINXvWzi!-1201085350).
Slim installers reduce the size of WebLogic Server downloads, installations, Docker images, and Kubernetes pods.
For example, a WebLogic Server 12.2.1.4 slim installer download is approximately 180 MB.


For the Remote Console to access the Administration Server running in Kubernetes, you can:

* Use curl to access the REST interface of WebLogic Server Administration Server to verify the connection and that the correct `hostname:port` is being used.
* Configure Ingress path routing rules.
* Use the Administration Server `NodePort`.

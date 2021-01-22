---
title: "CI/CD considerations"
date: 2019-04-11T13:01:55-04:00
weight: 5
description: "Learn about managing domain images with continuous integration and continuous delivery (CI/CD)."
draft: false
---

### Overview

In this section, we will discuss the recommended techniques for managing the evolution
and mutation of container images to run WebLogic Server in Kubernetes.  There are several
approaches and techniques available, and the choice of which to use depends very
much on your particular requirements.  We will start with a review of the "problem
space," and then talk about the considerations that would lead us to choose various
approaches.  We will provide details about several approaches to implementing
CI/CD and links to examples.

### Review of the problem space

Kubernetes makes a fundamental assumption that images are immutable,
that they contain no state, and that updating them is as simple as throwing
away a pod/container and replacing it with a new one that uses a newer version
of the image.  These assumptions work very well for microservices
applications, but for more traditional workloads, we need to do some extra
thinking and some extra work to get the behavior we want.

CI/CD is an area where the standard assumptions aren't always suitable.  In the
microservices architecture, you typically minimize dependencies and build
images from scratch with all of the dependencies in them.  You also typically
keep all of the configuration outside of the image, for example, in Kubernetes config
maps or secrets, and all of the state outside of the image too.  This makes
it very easy to update running pods with a new image.

Let's consider how a WebLogic image is different.  There will, of course, be a
base layer with the operating system; let's assume it is
[Oracle Linux "slim"](https://hub.docker.com/_/oraclelinux/).  Then you need
a JDK and this is very commonly in another layer.  Many people will use
the officially supported JDK images from the Docker Store, like the
[Server JRE image](https://hub.docker.com/_/oracle-serverjre-8), for example.  On
top of this, you need the WebLogic Server binaries (the "Oracle Home").  On top
of that, you may wish to have some patches or updates installed.  And then
you need your domain, that is the configuration.

There is also other information associated with a domain that needs to live
somewhere, for example leasing tables, message and transaction stores, and so
on.  We recommend that these be kept in a database to take advantage of built-in
database server HA, and the fact that disaster recovery of sites across all
but the shortest distances, almost always requires using a single database
server to consolidate and replicate data (DataGuard).

There are three common approaches on how to structure these components:
 * The first, "domain on a persistent volume" or Domain in PV,
   places the JDK and WebLogic binaries
   in the image, but the domain home is kept on a separate persistent storage
   outside of the image.
 * The second, Domain in Image,
   puts the JDK, WebLogic Server binaries,
   and the domain home all in the image.
 * The third approach, Model in Image, puts the JDK, WebLogic Server binaries, and a domain model
   in the image, and generates the domain home at runtime
   from the domain model.

All of these approaches are perfectly
valid (and fully supported) and they have various advantages and disadvantages.
We have listed the [relative advantages of these approaches here]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

One of the key differences between these approaches is how many images
you have, and therefore, how you build and maintain them - your image CI/CD
process.  Let's take a short detour and talk about image layering.

{{% children style="h4" description="true" %}}

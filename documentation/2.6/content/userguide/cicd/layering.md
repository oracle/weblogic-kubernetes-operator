---
title: "Docker image layering"
date: 2019-04-11T13:15:32-04:00
weight: 1
draft: false
description: "Learn about Docker image layering and why it is important."
---


Docker images are composed of layers, as shown in the diagram below.  If you download
the standard `weblogic:12.2.1.4` image from the [Oracle Container Registry](https://container-registry.oracle.com),
then you can see these layers using the command  `docker inspect container-registry.oracle.com/middleware/weblogic:12.2.1.4`
(the domain layer will not be there).  You are not required to use layers, but
efficient use of layers is considered a best practice.

![Docker image layers](/weblogic-kubernetes-operator/images/layers.png)

#### Why is it important to maintain the layering of images?

Layering is an important technique in Docker images.  Layers are important because they
are shared between images.  Let's consider an example.  In the diagram below, we have
two domains that we have built using layers.  The second domain has some additional
patches that we needed on top of those provided in the standard WebLogic image.  Those
are installed in their own layer, and then the second domain is created in another
layer on top of that.

Let's assume we have a three-node Kubernetes cluster and we are running both domains
in this cluster.  Sooner or later, we will end up with servers in each domain running
on each node, so eventually all of the image layers are going to be needed on all of
the nodes.  Using the approach shown below (that is, standard Docker layering techniques)
we are going to need to store all six of these layers on each node.  If you add up the
sizes, then you will see that it comes out to about 1.5GB per node.

![Docker images with layers](/weblogic-kubernetes-operator/images/more-layers.png)

Now, let's consider the alternative, where we do not use layers, but instead,
build images for each domain and put everything in one big layer (this is often
called "squashing" the layers).  In this case, we have the same content, but if
you add up the size of the images, you get 2.9GB per node. That’s almost twice the size!

![Docker images without layers](/weblogic-kubernetes-operator/images/no-layers.png)

With only two domains, you start to see the problem.  In the layered approach, each
new domain is adding only a relatively very small increment.  In the non-layered
approach, each new domain is essentially adding the entire stack over again.  Imagine
if we had ten domains, now the calculation looks like this:

|                            | With Layers       | Without Layers    |
| -------------------------- | ----------------- | ----------------- |
| Shared Layers              | 1.4GB             | 0GB               |
| Dedicated/different layers | 10 x 10MB = 100MB | 10 x 1.5GB = 15GB |
| Total per node             | 1.5GB             | 15GB              |


You can see how the amount of storage for images really starts to add up, and it
is not just a question of storage.  When Kubernetes creates a container from an
image, the size of the image has an impact on how long it takes to create and
start the container.

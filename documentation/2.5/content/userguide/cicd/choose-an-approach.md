---
title: "Choose an approach"
date: 2019-04-11T13:36:57-04:00
draft: false
weight: 3
description: "How to choose an approach."
---

Let's review what we have discussed and talk about when we might want to use
various approaches.  We can start by asking ourselves questions like these:


- *Can you make the desired change with a configuration override?*  
  The WebLogic Kubernetes Operator allows you to inject a number of [configuration
  overrides]({{< relref "/userguide/managing-domains/configoverrides/_index.md" >}})
  into your pods before starting any servers in the domain.  This allows you to use 
  the same image for multiple
  different configurations.  A good example would be changing the settings for a data
  source, for example. You may wish to have a larger connection pool in your production
  environment than you do in your development/test environments.  You probably also
  want to have different credentials.  You may want to change the service name, and
  so on.  All of these kinds of updates can be made with configuration overrides.
  These are placed in a Kubernetes config map, that is, they are outside of the image, so
  they do not require rebuilding the Docker image.  If all of your changes fit into
  this category, it is probably much better to just use configuration overrides
  instead of building a new image.  
- *Are you only changing the WebLogic configuration, for example, deploying or updating an
  application, changing a resource configuration in a way that is not supported by
  configuration overrides, and such?*  
  If your changes fit into this category, and you have used the "domain-in-image"
  approach and the Docker layering model, then you only need to update the top layer
  of your image.  This is relatively easy compared to making changes in lower layers.
  You could create a new layer with the changes, or you could rebuild/replace the
  existing top layer with a new one.  Which approach you choose depends mainly on
  whether you need to maintain the same domain encryption keys or not.
- *Do you need to be able to do a rolling restart?*
  If you need to do a rolling restart, for example to maintain the availability of
  your applications, then you need to make sure the new domain layer has the same
  domain encryption keys.  You cannot perform a rolling restart of a domain if the
  new members have a different encryption key.
- *Do you need to mutate something in a lower layer, for example, patch WebLogic, the JDK, or Linux?*  
  If you need to make an update in a lower layer, then you will need to rebuild that
  layer and all of the layers above it.  This means that you will need to rebuild the
  domain layer.  You will need to determine if you need to keep the same domain encryption keys.

The diagram below summarizes these concerns in a decision tree for the “domain in image” case:

{{< img "Decision model for the \"domain in image\" approach" "images/flowchart.png" >}}

If you are using the "domain on persistent storage" approach, many of these concerns become
moot because you have an effective separation between your domain and the Docker image.
There is still the possibility that an update in the Docker image could affect your domain;
for example, if you updated the JDK, you may need to update some of your domain scripts
to reflect the new JDK path.  

However, in this scenario, your environment is much closer to what you are probably used
to in a traditional (non-Kubernetes) environment, and you will probably find that all of
the practices you used from that pre-Kubernetes environment are directly applicable here
too, with just some small modifications.  For example, applying a WebLogic patch would
now involve building a new Docker image.

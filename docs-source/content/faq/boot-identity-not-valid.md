---
title: "Boot Identity Not Valid"
date: 2020-03-02T08:08:19-04:00
draft: false
weight: 1
---

> One or more WebLogic Server instances in my domain will not start and I see errors in the server log like this:
>
> `<Feb 6, 2020 12:05:35,550 AM GMT> <Critical> <Security> <BEA-090402> <Authentication denied: Boot identity not valid. The user name or password or both from the boot identity file (boot.properties) is not valid. The boot identity may have been changed since the boot identity file was created. Please edit and update the boot identity file with the proper values of username and password. The first time the updatedboot identity file is used to start the server, these new values are encrypted.>`

When you see these kinds of errors, it means that either the user name and password provided in the `weblogicCredentialsSecret` are invalid
or that the WebLogic domain directory's security configuration files have changed in an incompatible way between when the Operator scanned
the domain directory, which occurs during the "introspection" phase, and when the server instance attempted to start.

First, always check that the user name and password credentials stored in the Kubernetes secret referenced by `weblogicCredentialsSecret` contain the expected values for an account with administrative privilege for the WebLogic domain.

To understand the "incompatible domain security configuration" type of failure, it's important to review the contents of the
[WebLogic domain directory](https://docs.oracle.com/middleware/12213/wls/DOMCF/config_files.htm#DOMCF140). Each WebLogic
domain directory contains a "security" subdirectory that contains a file called "SerializedSystemIni.dat".  This file contains
security data to bootstrap the WebLogic domain, including a domain-specific encryption key.

During introspection, the operator generates a Kubernetes job that runs a pod in the domain's Kubernetes namespace and with the
same Kubernetes service account that will be used later to run the Administration Server. This pod has access to the Kubernetes
secret referenced by `weblogicCredentialsSecret` and encrypts these values with the domain-specific encryption key so that the
secured value can be injected as the "boot.properties" contents when starting server instances.

When the domain directory is changed such that the domain-specific encryption key is different, the "boot.properties" entries
generated during introspection will now be invalid.

This can happen in a variety of ways, depending on the [model selected](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/choosing-a-model/):

#### Domain in a Docker image and rolling to an image containing new or unrelated domain directory

The domain is using the "domain in a Docker image" model and the error occurs while rolling to a new Docker image that contains an entirely new or unrelated domain directory.

The problem is that WebLogic cannot support server instances being part of the same WebLogic domain if the server instances do 
not all share the same domain-specific encryption key and other bootstrapping security details. Additionally, operator introspection
currently only happens when starting servers following a total shutdown. Therefore, the "boot.properites" generated from
introspecting the image containing the original domain directory will be invalid when used with a container started with
the updated Docker image containing the new or unrelated domain directory.

The solution is to either follow the recommended [CI/CD guidelines](https://oracle.github.io/weblogic-kubernetes-operator/userguide/cicd/) so that the original and new Docker images contain domain directories
with consistent domain-specific encryption keys and bootstrapping security details or to [perform a total shutdown](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers) of the domain so
that introspection reoccurs as servers are restarted.

#### Domain in a Docker image after a full domain shutdown and restart

The domain is using the "domain in a Docker image" model and the error occurs while starting servers after a full domain shutdown. 

For this type of failure, it is even more probable that the user name and password specified in the Kubernertes secret referenced by
`weblogicCredentialsSecret` are wrong. However, there is another common cause. If your development model generates new Docker images
with new and unrelated domain directories and then tags those images with the same tag, then different Kubernetes worker nodes
may have different images under the same tag in their local Docker repositories.

The simplest solution is to set `imagePullPolicy` to `Always`; however, the better solution would be to design your development
pipeline to generate new Docker image tags on every build and to never reuse an existing tag.

#### Domain on a persistent volume and completely replacing the domain directory

The domain is using the "domain on a persistent volume" model and the error occurs while starting servers when the domain directory
change was made while other servers were still running.

If completely replacing the domain directory then you must [stop all running servers](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers).

Since all servers will already be stopped, there is no requirement that the new contents of the domain directory be related to
the previous contents of the domain directory.  When starting servers again, the operator will perform its introspection
of the domain directory. However, you may want to preserve the domain directory security configuration including the domain-specific
encryption key and, in that case, you should follow a similar pattern as is described in the [CI/CD guidelines](https://oracle.github.io/weblogic-kubernetes-operator/userguide/cicd/) for the domain
in a Docker image model to preserve the original security-related domain directory files.


---
title: "Domain secret mismatch"
date: 2020-03-02T08:08:19-04:00
draft: false
weight: 21
---

> One or more WebLogic Server instances in my domain will not start and the domain resource `status` or the pod log reports errors like this:
>
> ***Domain secret mismatch. The domain secret in `DOMAIN_HOME/security/SerializedSystemIni.dat` where DOMAIN_HOME=`$DOMAIN_HOME` does not match the domain secret found by the introspector job. WebLogic requires that all WebLogic Servers in the same domain share the same domain secret.***

When you see these kinds of errors, it means that the WebLogic domain directory's security configuration files have changed in an incompatible way between when the operator scanned
the domain directory, which occurs during the "introspection" phase, and when the server instance attempted to start.

To understand the "incompatible domain security configuration" type of failure, it's important to review the contents of the
[WebLogic domain directory](https://docs.oracle.com/en/middleware/standalone/weblogic-server/14.1.1.0/domcf/config_files.html#GUID-C8312BFA-340F-4B97-A12D-229DC2ADB1B3). Each WebLogic
domain directory contains a `security` subdirectory that contains a file called `SerializedSystemIni.dat`.  This file contains
security data to bootstrap the WebLogic domain, including a domain-specific encryption key.

During introspection, the operator generates a Kubernetes Job that runs a pod in the domain's Kubernetes Namespace and with the
same Kubernetes ServiceAccount that will be used later to run the Administration Server. This pod has access to the Kubernetes
secret referenced by `weblogicCredentialsSecret` and encrypts these values with the domain-specific encryption key so that the
secured value can be injected in to the `boot.properties` files when starting server instances.

When the domain directory is changed such that the domain-specific encryption key is different, the `boot.properties` entries
generated during introspection will now be invalid.

This can happen in a variety of ways, depending on the [domain home source type](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/choosing-a-model/).

#### Domain in Image

##### Rolling to an image containing new or unrelated domain directory

The error occurs while rolling pods have containers based on a new Docker image that contains an entirely new or unrelated domain directory.

The problem is that WebLogic cannot support server instances being part of the same WebLogic domain if the server instances do
not all share the same domain-specific encryption key. Additionally, operator introspection
currently happens only when starting servers following a total shutdown. Therefore, the `boot.properites` files generated from
introspecting the image containing the original domain directory will be invalid when used with a container started with
the updated Docker image containing the new or unrelated domain directory.

The solution is to follow either the recommended [CI/CD guidelines](https://oracle.github.io/weblogic-kubernetes-operator/userguide/cicd/) so that the original and new Docker images contain domain directories
with consistent domain-specific encryption keys and bootstrapping security details, or to [perform a total shutdown](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers) of the domain so
that introspection reoccurs as servers are restarted.

##### Full domain shutdown and restart

The error occurs while starting servers after a full domain shutdown.

If your development model generates new Docker images
with new and unrelated domain directories and then tags those images with the same tag, then different Kubernetes worker nodes
may have different images under the same tag in their individual, local Docker repositories.

The simplest solution is to set `imagePullPolicy` to `Always`; however, the better solution would be to design your development
pipeline to generate new Docker image tags on every build and to never reuse an existing tag.

#### Domain in PV

##### Completely replacing the domain directory

The error occurs while starting servers when the domain directory change was made while other servers were still running.

If completely replacing the domain directory, then you must [stop all running servers](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers).

Because all servers will already be stopped, there is no requirement that the new contents of the domain directory be related to
the previous contents of the domain directory.  When starting servers again, the operator will perform its introspection
of the domain directory. However, you may want to preserve the domain directory security configuration including the domain-specific
encryption key and, in that case, you should follow a similar pattern as is described in the [CI/CD guidelines](https://oracle.github.io/weblogic-kubernetes-operator/userguide/cicd/) for the domain
in a Docker image model to preserve the original security-related domain directory files.

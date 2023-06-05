---
title: "OpenShift"
date: 2019-10-04T08:08:08-05:00
weight: 7
description: "OpenShift information for the operator."
---

#### Set the Helm chart property `kubernetesPlatform` to `OpenShift`

Beginning with operator version 3.3.2,
set the operator `kubernetesPlatform` Helm chart property to `OpenShift`.
This property accommodates OpenShift security requirements. Specifically, the operator's deployment and any pods created
by the operator for WebLogic Server instances will not contain `runAsUser: 1000` in the configuration of the `securityContext`. This is to
accommodate OpenShift's default `restricted` security context constraint.
For more information, see [Operator Helm configuration values]({{<relref "/managing-operators/using-helm#operator-helm-configuration-values">}}).

#### Use a dedicated namespace

When the user that installs an individual instance of the operator
does _not_ have the required privileges to create resources at the Kubernetes cluster level,
they can use a `Dedicated` namespace selection strategy for the operator instance to limit
it to managing domain resources in its local namespace only
(see [Operator namespace management]({{< relref "/managing-operators/namespace-management#choose-a-domain-namespace-selection-strategy" >}})),
and they may need to manually install the Domain Custom Resource (CRD)
(see [Prepare for installation]({{< relref "/managing-operators/preparation#how-to-manually-install-the-domain-resource-custom-resource-definition-crd" >}})).

#### With WIT, set the `target` parameter to `OpenShift`

When using the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) (WIT),
`create`, `rebase`, or `update` command, to create a
[Domain in Image]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) domain home,
[Model in Image]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) image,
or [Model in Image]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) auxiliary image,
you can specify the `--target` parameter for the target Kubernetes environment.
Its value can be either `Default` or `OpenShift`.
The `OpenShift` option changes the domain directory files such that the group permissions
for those files will be the same as the user permissions (group writable, in most cases).
If you do not supply the OS group and user setting with `--chown`,
then the `Default` setting for this option is changed from `oracle:oracle` to `oracle:root`
to be in line with the expectations of an OpenShift environment.

#### Security requirements to run WebLogic in OpenShift

WebLogic Kubernetes Operator images starting with version 3.1 and
WebLogic Server or Fusion Middleware Infrastructure images obtained from Oracle Container Registry after August 2020
have an `oracle` user with UID 1000 with the default group set to `root`.

Here is an excerpt from a standard WebLogic [Dockerfile](https://github.com/oracle/docker-images/blob/master/OracleWebLogic/dockerfiles/12.2.1.4/Dockerfile.generic#L89)
that demonstrates how the file system group ownership is configured in the standard WebLogic Server images:

```dockerfile
# Setup filesystem and oracle user
# Adjust file permissions, go to /u01 as user 'oracle' to proceed with WLS installation
# ------------------------------------------------------------
RUN mkdir -p /u01 && \
    chmod 775 /u01 && \
    useradd -b /u01 -d /u01/oracle -m -s /bin/bash oracle && \
    chown oracle:root /u01

COPY --from=builder --chown=oracle:root /u01 /u01
```

OpenShift, by default, enforces the `restricted` security context constraint which
allocates a high, random UID in the `root` group for each container.  The standard
images mentioned previously are designed to work with the `restricted` security context constraint.

However, if you build your own image, have an older version of an image, or obtain an
image from another source, it may not have the necessary permissions.  You may need to
configure similar file system permissions to allow your image to work in OpenShift.
Specifically, you need to make sure the following directories have `root` as their
group, and that the group read, write and execute permissions are set (enabled):

* For the operator, `/operator` and `/logs`.
* For WebLogic Server and Fusion Middleware Infrastructure images, `/u01` (or the ultimate parent directory of your
  Oracle Home and domain if you put them in different locations).


{{% notice note %}}
For additional information about OpenShift requirements and the operator,
see [OpenShift]({{<relref  "/introduction/platforms/environments#openshift">}}).
{{% /notice %}}

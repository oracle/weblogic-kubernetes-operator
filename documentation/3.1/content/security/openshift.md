---
title: "OpenShift"
date: 2019-10-04T08:08:08-05:00
weight: 7
description: "OpenShift information for the operator"
---

#### Security requirements to run WebLogic in OpenShift

WebLogic Server Kubernetes Operator images starting with version 3.1 and
WebLogic Server images obtained from Oracle Container Registry after August 2020
have an `oracle` user with UID 1000 with the default group set to `root`.

Here is an excerpt from a standard WebLogic [Dockerfile](https://github.com/oracle/docker-images/blob/master/OracleWebLogic/dockerfiles/12.2.1.4/Dockerfile.generic#L89)
that demonstrates how the file system group ownership is configured in the standard WebLogic Server images:

```bash
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
images mentioned above are designed to work with the `restricted` security context constraint.

However, if you build your own image, have an older version of an image, or obtain an
image from another source, it may not have the necessary permissions.  You may need to
configure similar file system permissions to allow your image to work in OpenShift.
Specifically, you need to make sure the following directories have `root` as their
group, and that the group read, write and execute permissions are set (enabled):

* For the operator, `/operator` and `/logs`.
* For WebLogic Server images, `/u01` (or the ultimate parent directory of your
  Oracle Home and domain if you put them in different locations).

If your OpenShift environment has a different default security context constraint,
you may need to configure OpenShift to allow use of UID 1000 by creating
a security context constraint.  Oracle recommends that you define
a custom security context constraint that has just the permissions that are required
and apply that to WebLogic pods.  Oracle does not recommend using the built-in `anyuid`
Security Context Constraint, because it provides more permissions
than are needed, and is therefore less secure.

#### Create a custom Security Context Constraint

To create a custom security context constraint, create a YAML file with the following
content.  This example assumes that your OpenShift project is called `weblogic` and
that the service account you will use to run the operator and domains
is called `weblogic-operator`.  You should change these
in the `groups` and `users` sections to match your environment.

```yaml
kind: SecurityContextConstraints
apiVersion: v1
metadata:
  name: uid1000
allowHostDirVolumePlugin: false
allowHostIPC: false
allowHostNetwork: false
allowHostPID: false
allowHostPorts: false
allowPrivilegeEscalation: true
allowPrivilegedContainer: false
fsGroup:
  type: MustRunAs
groups:
- system:serviceaccounts:weblogic
readOnlyRootFilesystem: false
requiredDropCapabilities:
- KILL
- MKNOD
- SETUID
- SETGID
runAsUser:
  type: MustRunAs
  uid: 1000
seLinuxContext:
  type: MustRunAs
supplementalGroups:
  type: RunAsAny
users:
- system:serviceaccount:weblogic:weblogic-operator
volumes:
- configMap
- downwardAPI
- emptyDir
- persistentVolumeClaim
- projected
- secret
```

Assuming you called that file `uid1000.yaml`, you can create the security context constraint
using the following command:

```bash
$ oc create -f uid1000.yaml
```

After you have created the security context constraint, you can install the WebLogic Server Kubernetes Operator.
Make sure you use the same service account to which you granted permission in the security
context constraint (`weblogic-operator` in the preceding example).  The operator will then run
with UID 1000, and any WebLogic domain it creates will also run with UID 1000.

{{% notice note %}}
For additional information about OpenShift requirements and the operator,
see the [OpenShift]({{<relref  "/userguide/introduction/introduction#openshift">}}) section in the User Guide.
{{% /notice %}}

#### Using a dedicated namespace

When the user that installs an individual instance of the operator does not have the required privileges to create resources at the Kubernetes cluster level, a dedicated namespace can be used for the operator instance and all the WebLogic domains that it manages. For more details about the `dedicated` setting, please refer to [Operator Helm configuration values]({{< relref "/userguide/managing-operators/using-the-operator/using-helm#operator-helm-configuration-values" >}}).

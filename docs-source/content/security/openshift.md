---
title: "OpenShift"
date: 2019-10-04T08:08:08-05:00
weight: 7
description: "OpenShift information for the operator"
---

#### Security requirements to run WebLogic in OpenShift

WebLogic Server Docker images obtained from Oracle Container Registry have an `oracle` user with
UID 1000 and it is in a group, which is also called `oracle` and has GID 1000.
The WebLogic Server Kubernetes Operator Docker images use the same UID and GID.

OpenShift, by default, allocates a high, random UID for each container and this
UID may not have the necessary permissions to run the programs in the container.

Since August 2020, the standard WebLogic Server images that are published in
Oracle Container Registry set the file system group ownership to
allow the high UIDs allocated by OpenShift to have the necessary permissions.

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

However, if you build your own image, or obtain an image from another source, it
may not have the necessary permissions.  You may need to configure similar file
system permissions to allow your image to work in OpenShift.

Alternatively, you may choose to configure OpenShift to allow use of UID 1000.  This
can be done using a security context constraint.  Oracle recommends that you define
a custom security context constraint that has just the permissions that are required
and apply that to WebLogic pods.  If you prefer, you can use the built-in `anyuid`
Security Context Constraint, but you should be aware that it provides more permissions
than are needed, and is therefore a less secure option.

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

#### Use the `anyuid` Security Context Constraint

The built-in `anyuid` security context constraint
will also ensure proper access to the file system within the
Docker image, however this security context constraint grants more permissions
than are needed. This means that the administrator must:

1. Ensure the `anyuid` security content is granted
2. Ensure that WebLogic containers are annotated with `openshift.io/scc: anyuid`

For example, to update the OpenShift policy, use:

```bash
$ oc adm policy add-scc-to-user anyuid -z default
```

To annotate the WebLogic containers, update the WebLogic `Domain` resource
to include `annotations` for the `serverPod`. For example:

``` yaml
kind: Domain
metadata:
  name: domain1
spec:
  domainUID: domain1
  serverPod:
    env:
      - name: var1
        value: value1
    annotations:
      openshift.io/scc: anyuid
```

{{% notice note %}}
For additional information about OpenShift requirements and the operator,
see the [OpenShift]({{<relref  "/userguide/introduction/introduction#openshift">}}) section in the User Guide.
{{% /notice %}}

#### Using a dedicated namespace

When the user that installs an individual instance of the operator does not have the required privileges to create resources at the Kubernetes cluster level, a dedicated namespace can be used for the operator instance and all the WebLogic domains that it manages. For more details about the `dedicated` setting, please refer to [Operator Helm configuration values]({{< relref "/userguide/managing-operators/using-the-operator/using-helm#operator-helm-configuration-values" >}}).

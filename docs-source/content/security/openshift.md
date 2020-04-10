---
title: "OpenShift"
date: 2019-10-04T08:08:08-05:00
weight: 7
description: "OpenShift information for the operator"
---

#### OpenShift `anyuid` security context

The Docker images that Oracle publishes default to the container user
as `oracle`, which is UID `1000` and GID `1000`. When running the
Oracle images or layered images that retain the default user as
`oracle` with OpenShift, the `anyuid` security context constraint
is required to ensure proper access to the file system within the
Docker image. This means that the administrator must:

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

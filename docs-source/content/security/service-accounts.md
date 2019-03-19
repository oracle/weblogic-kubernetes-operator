---
title: "Service accounts"
date: 2019-02-23T17:36:12-05:00
weight: 4
description: "Kubernetes service accounts for the WebLogic operator"
---


#### WebLogic operator service account

When the WebLogic operator is installed, the Helm chart property, `serviceAccount`, can
be specified where the value contains the name of the Kubernetes `ServiceAccount`
in the namespace in which the WebLogic operator will be installed.
For more information about the Helm chart, see the
[operator Helm configuration values]({{<relref "/userguide/managing-operators/using-the-operator/using-helm/_index.md#operator-helm-configuration-values">}}).

The WebLogic operator will use this `ServiceAccount` when calling the Kubernetes API server
and the appropriate access controls will be created for this `ServiceAccount` by
the operator's Helm chart.

{{% notice info %}}
For more information about access controls, see [RBAC]({{<relref "/security/rbac.md">}}) under **Security**.
{{% /notice %}}

In order to display the `ServiceAccount` used by the WebLogic operator,
where the operator was installed using the Helm release name `weblogic-operator`,
look for the `serviceAccount` value using the Helm command:
```bash
$ helm get values --all weblogic-operator
```
#### Additional reading
* [Helm service account]({{<relref "userguide/managing-operators/_index.md#install-helm-and-tiller">}})
* [Operator Helm chart service account configuration]({{<relref "/userguide/managing-operators/using-the-operator/using-helm/_index.md#serviceaccount">}})

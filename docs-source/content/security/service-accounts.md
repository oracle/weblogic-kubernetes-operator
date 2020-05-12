---
title: "Service accounts"
date: 2019-02-23T17:36:12-05:00
weight: 4
description: "Kubernetes ServiceAccounts for the operator"
---


#### WebLogic Server Kubernetes Operator ServiceAccounts

When the operator is installed, the Helm chart property, `serviceAccount`, can
be specified where the value contains the name of the Kubernetes `ServiceAccount`
in the namespace in which the operator will be installed.
For more information about the Helm chart, see the
[Operator Helm configuration values]({{<relref "/userguide/managing-operators/using-the-operator/using-helm/_index.md#operator-helm-configuration-values">}}).

The operator will use this `ServiceAccount` when calling the Kubernetes API server
and the appropriate access controls will be created for this `ServiceAccount` by
the operator's Helm chart.

{{% notice info %}}
For more information about access controls, see [RBAC]({{<relref "/security/rbac.md">}}).
{{% /notice %}}

{{% notice note %}}
If the operator's service account cannot have the privileges to access the cluster-level resources, such as `CustomResourceDefinitions`, `Namespaces` and `PersistentVolumes`, consider using a `dedicated` namespace for each operator and the domains that the operator manages. See the `dedicated` setting in [Operator Helm configuration values]({{< relref "/userguide/managing-operators/using-the-operator/using-helm#operator-helm-configuration-values" >}}).
{{% /notice %}}

In order to display the `ServiceAccount` used by the operator,
where the operator was installed using the Helm release name `weblogic-operator`,
look for the `serviceAccount` value using the Helm command:
```bash
$ helm get values --all weblogic-operator
```
#### Additional reading
* [Helm service account]({{<relref "userguide/managing-operators/_index.md#install-helm-and-tiller">}})
* [Operator Helm chart service account configuration]({{<relref "/userguide/managing-operators/using-the-operator/using-helm/_index.md#serviceaccount">}})

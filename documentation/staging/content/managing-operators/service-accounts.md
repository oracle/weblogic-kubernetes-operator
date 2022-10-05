---
title: "Service accounts"
date: 2019-02-23T17:36:12-05:00
weight: 7
description: "Kubernetes ServiceAccounts for the operator."
---

### WebLogic Kubernetes Operator ServiceAccounts

When the operator is installed, the Helm chart property, `serviceAccount`, can
be specified where the value contains the name of the Kubernetes `ServiceAccount`
in the namespace in which the operator will be installed.

The operator will use this service account when calling the Kubernetes API server
and the appropriate access controls will be created for this `ServiceAccount` by
the operator's Helm chart.

To display the service account used by the operator,
where the operator was installed using the Helm release name `weblogic-operator`,
look for the `serviceAccount` value using the Helm command:

```shell
$ helm get values --all weblogic-operator
```

{{% notice note %}}
If the operator's service account cannot have the privileges to access the cluster-level resources,
such as `CustomResourceDefinitions`, `Namespaces`, and `PersistentVolumes`,
then consider using the same dedicated namespace for each operator
and the domains that each operator manages.
See the `Dedicated` option for the
[domainNamespaceSelectionStrategy]({{< relref "/managing-operators/using-helm#domainnamespaceselectionstrategy" >}})
setting.
{{% /notice %}}

### Additional reading

* See [Prepare an operator namespace and service account]({{<relref "/managing-operators/preparation#prepare-an-operator-namespace-and-service-account">}}).
* See the operator Helm chart [serviceAccount]({{<relref "/managing-operators/using-helm#serviceaccount">}})
  setting.
* For more information about access controls, see [RBAC]({{<relref "/managing-operators/rbac.md">}}).

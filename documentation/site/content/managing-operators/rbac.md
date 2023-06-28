---
title: "RBAC"
date: 2019-02-23T17:15:36-05:00
weight: 8
description: "Operator role-based authorization."
---

{{< table_of_contents >}}

### Overview

This document describes the Kubernetes Role-Based Access Control (RBAC)
roles that an operator installation Helm chart automatically creates for you.

The general design goal of the operator installation is to
automatically provide the operator with the minimum amount of
permissions that the operator requires and to favor built-in roles over custom roles
where it makes sense to use the Kubernetes built-in roles.

The operator installation Helm chart automatically creates
RBAC ClusterRoles, ClusterRoleBindings, Roles, and RoleBindings
for the `ServiceAccount` that is used by the operator.
A running operator assumes that these roles are created in the
Kubernetes cluster and will automatically attempt to verify that
they are correct when it starts.

Note that the operator installation Helm chart
creates ClusterRoles and ClusterRoleBindings
when the [enableClusterRoleBinding]({{<relref "/managing-operators/using-helm#enableclusterrolebinding">}}) Helm chart configuration setting
is set to `true` (the default), and the chart creates Roles and RoleBindings
when the setting is set to `false`.

**References**

For more information about:
- Installing the operator, see
  [Prepare for installation]({{< relref "/managing-operators/preparation.md" >}})
  and [Installation]({{< relref "/managing-operators/installation.md" >}}).
- The `enableClusterRoleBinding` operator Helm chart setting, see
  [Choose a security strategy]({{<relref "/managing-operators/preparation#choose-a-security-strategy">}}).
- The Kubernetes `ServiceAccount` used by the operator, see
  [Service accounts]({{<relref "/managing-operators/service-accounts.md">}}).
- Kubernetes Roles, see the Kubernetes
  [RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/) documentation.

### Operator RBAC definitions

To display the Kubernetes Roles and related Bindings used by
the operator, where the operator was installed using the
Helm release name `weblogic-operator`, look for the Kubernetes objects, `Role`, `RoleBinding`,
`ClusterRole`, and `ClusterRoleBinding`, when using the Helm `status` command:

```shell
$ helm status weblogic-operator
```

Assuming the operator was installed into the namespace `weblogic-operator-ns`
with a target namespaces of `domain1-ns`, the following
commands can be used to display a _subset_ of the Kubernetes Roles and
related RoleBindings:

```shell
$ kubectl describe clusterrole \
  weblogic-operator-ns-weblogic-operator-clusterrole-general
```
```shell
$ kubectl describe clusterrolebinding \
  weblogic-operator-ns-weblogic-operator-clusterrolebinding-general
```
```shell
$ kubectl -n weblogic-operator-ns \
  describe role weblogic-operator-role
```
```shell
$ kubectl -n domain1-ns \
  describe rolebinding weblogic-operator-rolebinding-namespace
```

#### Kubernetes Role and RoleBinding naming conventions

The following naming pattern is used for the `Role` and `RoleBinding` objects:

- weblogic-operator-`<type>`-`<optional-role-name>`

*Using:*

1. `<type>` as the kind of Kubernetes object:
     * _role_
     * _rolebinding_
2. `<optional-role-name>` as an optional name given to the Role or RoleBinding
     * For example: `namespace`

A complete name for an operator created Kubernetes `RoleBinding` would be:

> `weblogic-operator-rolebinding-namespace`

#### Kubernetes ClusterRole and ClusterRoleBinding naming conventions

The following naming pattern is used for the `ClusterRole` and `ClusterRoleBinding` objects:

- `<operator-ns>`-weblogic-operator-`<type>`-`<role-name>`

*Using:*

1. `<operator-ns>` as the namespace in which the operator is installed
     * For example: `weblogic-operator-ns`
2. `<type>` as the kind of Kubernetes object:
     * _clusterrole_
     * _clusterrolebinding_
3. `<role-name>` as the name given to the Role or RoleBinding
     * For example: `general`

A complete name for an operator created Kubernetes `ClusterRoleBinding` would be:

> `weblogic-operator-ns-weblogic-operator-clusterrolebinding-general`

### RoleBindings

Assuming that the operator was installed into the Kubernetes Namespace `weblogic-operator-ns`,
and a target namespace for the operator is `domain1-ns`, the following `RoleBinding` entries are mapped
to a `Role` or `ClusterRole` granting permission to the operator.

| RoleBinding | Mapped to Role | Resource Access | Notes |
| --- | --- | --- | --- |
| `weblogic-operator-rolebinding` | `weblogic-operator-role` | **Edit**: secrets, configmaps, events | The RoleBinding is created in the namespace `weblogic-operator-ns` [^1] |
| `weblogic-operator-rolebinding-namespace` | Operator Cluster Role `namespace` | **Read**: secrets, pods/log, pods/exec | The RoleBinding is created in the namespace `domain1-ns` [^2] |
| | | **Edit**: configmaps, events, pods, services, jobs.batch, poddisruptionbudgets.policy | |
| | | **Create**: pods/exec | |

### ClusterRoleBindings

Assuming that the operator was installed into the Kubernetes Namespace `weblogic-operator-ns`,
the following `ClusterRoleBinding` entries are mapped to a `ClusterRole` granting permission to the operator.

**NOTE**: The operator names in following table represent the `<role-name>` from the [cluster names](#kubernetes-clusterrole-and-clusterrolebinding-naming-conventions) section.

| ClusterRoleBinding | Mapped to Cluster Role | Resource Access | Notes |
| --- | --- | --- | --- |
| Operator `general` | Operator `general` | **Read**: namespaces | [^3] |
| | | **Edit**: customresourcedefinitions | |
| | | **Update**: domains (weblogic.oracle), domains/status | |
| | | **Create**: tokenreviews, selfsubjectrulesreviews | |
| Operator `nonresource` | Operator `nonresource` | **Get**: /version/* | [^1] |


[^1]: The binding is assigned to the operator `ServiceAccount`.
[^2]: The binding is assigned to the operator `ServiceAccount`
      in each namespace that the operator is configured to manage.
      See [Namespace management]({{< relref "/managing-operators/namespace-management.md" >}})
[^3]: The binding is assigned to the operator `ServiceAccount`.
      In addition, the Kubernetes RBAC resources that the operator installation actually
      sets up will be adjusted based on whether the operator is in dedicated mode.
      By default, the operator does not run in dedicated mode and those security resources
      are created as `ClusterRole` and `ClusterRoleBindings`.
      If the operator is running in dedicated mode,
      then those resources will be created as `Roles` and `RoleBindings` in the namespace of the operator.
      See the `Dedicated` option for the
      [domainNamespaceSelectionStrategy]({{< relref "/managing-operators/using-helm#domainnamespaceselectionstrategy" >}})
      setting.

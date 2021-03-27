---
title: "RBAC"
date: 2019-02-23T17:15:36-05:00
weight: 5
description: "Operator role-based authorization"
---

#### Contents
* [Overview](#overview)
* [Operator RBAC definitions](#operator-rbac-definitions)
  - [Role and RoleBinding naming conventions](#kubernetes-role-and-rolebinding-naming-conventions)
  - [ClusterRole and ClusterRoleBinding naming conventions](#kubernetes-clusterrole-and-clusterrolebinding-naming-conventions)
* [RoleBindings](#rolebindings)
* [ClusterRoleBindings](#clusterrolebindings)

#### Overview

The operator assumes that certain Kubernetes Roles are created in the
Kubernetes cluster.  The operator Helm chart creates the required ClusterRoles,
ClusterRoleBindings, Roles, and RoleBindings for the `ServiceAccount` that
is used by the operator. The operator will also attempt to verify that
the RBAC settings are correct when the operator starts running.

{{% notice info %}}
For more information about the Kubernetes `ServiceAccount` used by the operator, see
[Service Accounts]({{<relref "/security/service-accounts#weblogic-server-kubernetes-operator-service-accounts">}}).
{{% /notice %}}

The general design goal is to provide the operator with the minimum amount of
permissions that the operator requires and to favor built-in roles over custom roles
where it make sense to use the Kubernetes built-in roles.

{{% notice info %}}
For more information about Kubernetes Roles, see the
[Kubernetes RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/) documentation.
{{% /notice %}}

#### Operator RBAC definitions

To display the Kubernetes Roles and related Bindings used by
the operator where the operator was installed using the
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
  describe rolebinding weblogic-operator-rolebinding-namespace \
```

##### Kubernetes Role and RoleBinding naming conventions

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

##### Kubernetes ClusterRole and ClusterRoleBinding naming conventions

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

#### RoleBindings

Assuming that the operator was installed into the Kubernetes Namespace `weblogic-operator-ns`,
and a target namespace for the operator is `domain1-ns`, the following `RoleBinding` entries are mapped
to a `Role` or `ClusterRole` granting permission to the operator.

| RoleBinding | Mapped to Role | Resource Access | Notes |
| --- | --- | --- | --- |
| `weblogic-operator-rolebinding` | `weblogic-operator-role` | **Edit**: secrets, configmaps, events | The RoleBinding is created in the namespace `weblogic-operator-ns` [^1] |
| `weblogic-operator-rolebinding-namespace` | Operator Cluster Role `namespace` | **Read**: secrets, pods/log, pods/exec | The RoleBinding is created in the namespace `domain1-ns` [^2] |
| | | **Edit**: configmaps, events, pods, services, jobs.batch, poddisruptionbudgets.policy | |
| | | **Create**: pods/exec | |

#### ClusterRoleBindings

Assuming that the operator was installed into the Kubernetes Namespace `weblogic-operator-ns`,
the following `ClusterRoleBinding` entries are mapped to a `ClusterRole` granting permission to the operator.

**Note**: The operator names in table below represent the `<role-name>` from the [cluster names](#kubernetes-cluster-role-and-cluster-role-binding-naming-convention) section.

| ClusterRoleBinding | Mapped to Cluster Role | Resource Access | Notes |
| --- | --- | --- | --- |
| Operator `general` | Operator `general` | **Read**: namespaces | [^3] |
| | | **Edit**: customresourcedefinitions | |
| | | **Update**: domains (weblogic.oracle), domains/status | |
| | | **Create**: tokenreviews, selfsubjectrulesreviews | |
| Operator `nonresource` | Operator `nonresource` | **Get**: /version/* | [^1] |
| Operator `discovery` | Kubernetes `system:discovery` | **See**: [Kubernetes Discovery Roles](https://kubernetes.io/docs/reference/access-authn-authz/rbac/#discovery-roles) | [^3] |
| Operator `auth-delegator` | Kubernetes `system:auth-delegator` | **See**: [Kubernetes Component Roles](https://kubernetes.io/docs/reference/access-authn-authz/rbac/#other-component-roles) | [^3] |


[^1]: The binding is assigned to the operator `ServiceAccount`.
[^2]: The binding is assigned to the operator `ServiceAccount`
      in each namespace that the operator is configured to manage. See [Managing domain namespaces]({{< relref "/faq/namespace-management.md" >}})
[^3]: The binding is assigned to the operator `ServiceAccount`. In addition, the Kubernetes RBAC resources that the operator installation actually set up will be adjusted based on the value of the `dedicated` setting. By default,  the `dedicated` value is set to `false`, those security resources are created as `ClusterRole` and `ClusterRoleBindings`. If the `dedicated` value is set to `true`,  those resources will be created as `Roles` and `RoleBindings` in the namespace of the operator.

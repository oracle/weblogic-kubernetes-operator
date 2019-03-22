---
title: "RBAC"
date: 2019-02-23T17:15:36-05:00
weight: 5
description: "Role based authorization for the WebLogic operator"
---

#### Contents
* [Overview](#overview)
* [Operator RBAC definitions](#operator-rbac-definitions)
  - [Role and role binding naming convention](#kubernetes-role-and-role-binding-naming-convention)
  - [Cluster role and cluster role binding naming convention](#kubernetes-cluster-role-and-cluster-role-binding-naming-convention)
* [Role bindings](#role-bindings)
* [Cluster role bindings](#cluster-role-bindings)

#### Overview

The operator assumes that certain Kubernetes roles are created in the
Kubernetes cluster.  The operator Helm chart creates the required cluster roles,
cluster role bindings, roles and role bindings for the `ServiceAccount` that
is used by the operator. The operator will also attempt to verify that
the RBAC settings are correct when the operator starts running.

{{% notice info %}}
For more information about the Kubernetes `ServiceAccount` used by the operator, see
[Service Accounts]({{<relref "/security/service-accounts.md#weblogic-operator-service-account">}})
under **Security**.
{{% /notice %}}

The general design goal is to provide the operator with the minimum amount of
permissions that the operator requires and to favor built-in roles over custom roles
where it make sense to use the Kubernetes built-in roles.

{{% notice info %}}
For more information about Kubernetes roles, see the
[Kubernetes RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/) documentation.
{{% /notice %}}

#### Operator RBAC definitions

To display the Kubernetes roles and related bindings used by
the WebLogic operator where the operator was installed using the
Helm release name `weblogic-operator`, look for the Kubernetes objects:

- `Role`
- `RoleBinding`
- `ClusterRole`
- `ClusterRoleBinding`

when using the Helm `status` command:

```bash
$ helm status weblogic-operator
```

Assuming the operator was installed into the namespace `weblogic-operator-ns`
with a target namespaces of `domain1-ns`, the following
commands can be used to display a _subset_ of the Kubernetes roles and
related role bindings:

```bash
$ kubectl describe clusterrole \
  weblogic-operator-ns-weblogic-operator-clusterrole-general

$ kubectl describe clusterrolebinding \
  weblogic-operator-ns-weblogic-operator-clusterrolebinding-general

$ kubectl -n weblogic-operator-ns \
  describe role weblogic-operator-role

$ kubectl -n domain1-ns \
  describe rolebinding weblogic-operator-rolebinding-namespace \
```

##### Kubernetes role and role binding naming convention

The following naming pattern is used for the `Role` and `RoleBinding` objects:

- weblogic-operator-`<type>`-`<optional-role-name>`

*Using:*

1. `<type>` as the kind of Kubernetes object:
  * _role_
  * _rolebinding_
2. `<optional-role-name>` as an optional name given to the role or role binding
  * For example: `namespace`

A complete name for an operator created Kubernetes `RoleBinding` would be:

> `weblogic-operator-rolebinding-namespace`

##### Kubernetes cluster role and cluster role binding naming convention

The following naming pattern is used for the `ClusterRole` and `ClusterRoleBinding` objects:

- `<operator-ns>`-weblogic-operator-`<type>`-`<role-name>`

*Using:*

1. `<operator-ns>` as the namespace the operator is installed in
  * For example: `weblogic-operator-ns`
2. `<type>` as the kind of Kubernetes object:
  * _clusterrole_
  * _clusterrolebinding_
3. `<role-name>` as the name given to the role or role binding
  * For example: `general`

A complete name for an operator created Kubernetes `ClusterRoleBinding` would be:

> `weblogic-operator-ns-weblogic-operator-clusterrolebinding-general`

#### Role bindings

Assuming that the WebLogic operator was installed into the Kubernetes namespace `weblogic-operator-ns`,
and a target namespace for the operator is `domain1-ns`, the following `RoleBinding` entries are mapped
to a `Role` or `ClusterRole` granting permission to the operator.

| Role Binding | Mapped to Role | Resource Access | Notes |
| --- | --- | --- | --- |
| `weblogic-operator-rolebinding` | `weblogic-operator-role` | **Edit**: secrets, configmaps, events | The role binding is created in the namespace `weblogic-operator-ns` [^1] |
| `weblogic-operator-rolebinding-namespace` | Operator Cluster Role `namespace` | **Read**: secrets, pod/log, storageclasses | The role binding is created in the namespace `domain1-ns` [^2] |
| | | **Edit**: configmaps, events, pods, podtemplates, services, persistentvolumeclaims, newtworkpolicies, podsecuritypolicies, podpresets, jobs.batch, cronjobs.batch | |
| | | **Create**: pods/exec | |

#### Cluster role bindings

Assuming that the WebLogic operator was installed into the Kubernetes namespace `weblogic-operator-ns`,
the following `ClusterRoleBinding` entries are mapped to a `ClusterRole` granting permission to the operator.

**Note**: The Operator names in table below represent the `<role-name>` from [cluster names](#kubernetes-cluster-role-and-cluster-role-binding-naming-convention) section.

| Cluster Role Binding | Mapped to Cluster Role | Resource Access | Notes |
| --- | --- | --- | --- |
| Operator `general` | Operator `general` | **Read**: namespaces | [^1] |
| | | **Edit**: customresourcedefinitions, ingresses, persistentvolumes | |
| | | **Update**: domains (weblogic.oracle), domains/status | |
| | | **Create**: tokenreviews, subjectaccessreviews, localsubjectaccessreviews, selfsubjectaccessreviews, selfsubjectrulesreviews | |
| Operator `nonresource` | Operator `nonresource` | **Get**: /version/* | [^1] |
| Operator `discovery` | Kubernetes `system:discovery` | **See**: [Kubernetes Discovery Roles](https://kubernetes.io/docs/reference/access-authn-authz/rbac/#discovery-roles) | [^1] |
| Operator `auth-delegator` | Kubernetes `system:auth-delegator` | **See**: [Kubernetes Component Roles](https://kubernetes.io/docs/reference/access-authn-authz/rbac/#other-component-roles) | [^1] |


[^1]: The binding is assigned to the operator `ServiceAccount`.
[^2]: The binding is assigned to the operator `ServiceAccount`
      in each namespace listed with the `domainNamespaces` setting.
      The `domainNamespaces` setting contains the list of namespaces
      that the operator is configured to manage.

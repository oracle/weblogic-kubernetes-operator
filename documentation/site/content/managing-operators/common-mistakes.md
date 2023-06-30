---
title: "Common mistakes and solutions"
date: 2019-02-23T17:08:43-05:00
weight: 11
description: "Help for common installing and upgrading mistakes."
---

{{< table_of_contents >}}

### Namespace related

Common namespace-related mistakes.

#### Changes in default Helm chart values from WebLogic Kubernetes Operator version 3.4 to 4.0

The default for the `domainNamespaceSelectionStrategy` Helm chart value, which specifies how the operator will select the namespaces that it will manage, was changed between version 3.4 and 4.0.
In version 3.4, the default was `List` and in version 4.0, the default is `LabelSelector`. This means that the operator used to default to managing the set of namespaces listed in the `domainNamespaces` Helm chart value and
the updated default is that the operator searches for namespaces that have the label specified in the `domainNamespaceLabelSelector` Helm chart value, which defaults to `weblogic-operator=enabled`.

When upgrading the operator from 3.4 to 4.0, you can use the `--reuse-values` option, such as `helm upgrade --reuse-values`, to install the upgraded chart with the values that were used during the original installation, including default values.
If you do not use `--reuse-values`, then the upgraded chart will be installed with the updated default values, which could result in an unanticipated change in the set of namespaces that the operator is managing. Alternatively, you can also set
the value of `domainNamespaceSelectionStragegy` (or any other value) using a values file or the `--set` option.

#### Deleting and recreating a namespace that an operator manages without informing the operator

If you create a new domain in a namespace that is deleted and recreated, the domain does not start up until you notify the operator.
For more details about the problem and solutions, see [Namespace management]({{<relref "/managing-operators/namespace-management#recreate-a-previously-deleted-kubernetes-namespace-with-a-running-operator">}}).

#### Forgetting to configure the operator to monitor a namespace

If it appears that an operator is not managing a domain resource, for example:
- A domain YAML file is deployed and no introspector or WebLogic Server pods start.
- The operator log contains no mention of the domain.
- No events are generated for the domain in the domain's namespace.
- The domain resource's `domain.status` fields do not contain updated information about the status of the domain.
Then check to make sure that the Domain's namespace has been set up to be monitored by an operator.

For more information, see [Namespace management]({{<relref "/managing-operators/namespace-management#ensuring-the-operator-has-permission-to-manage-a-namespace">}}).

#### Installing the operator a second time into the same namespace

A new `FAILED` Helm release is created.
```shell
$ helm install --no-hooks --name op2 --namespace myuser-op-ns --values custom-values.yaml weblogic-operator/weblogic-operator
```
```
Error: release op2 failed: secrets "weblogic-operator-secrets" already exists
```

Both the previous and new release own the resources created by the previous operator.

* You can't modify it to change the namespace (because `helm upgrade` does not let you change the namespace).
* You can't fix it by deleting this release because it removes your previous operator's resources.
* You can't fix it by rolling back this release because it is not in the `DEPLOYED` state.
* You can't fix it by deleting the previous release because it removes the operator's resources too.
* All you can do is delete both operator releases and reinstall the original operator.

See https://github.com/helm/helm/issues/2349.

#### Installing an operator and having it manage a domain namespace that another operator is already managing

A new `FAILED` Helm release is created.
```shell
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values custom-values.yaml weblogic-operator/weblogic-operator
```
```
Error: release op2 failed: rolebindings.rbac.authorization.k8s.io "weblogic-operator-rolebinding-namespace" already exists
```

To recover:

- `helm delete --purge` the failed release.
  - **NOTE**: This deletes the role binding in the domain namespace that was created by the first operator release, to give the operator access to the domain namespace.
- `helm upgrade <old op release> weblogic-operator/weblogic-operator --values <old op custom-values.yaml>`
  - This recreates the role binding.
  - There might be intermittent failures in the operator for the period of time when the role binding was deleted.

#### Upgrading an operator and having it manage a domain namespace that another operator is already managing

The `helm upgrade` succeeds, and silently adopts the resources the first operator's Helm chart created in the domain namespace (for example, `rolebinding`), and, if you also instructed it to stop managing another domain namespace, then it abandons the role binding it created in that namespace.

For example, if you delete this release, then the first operator will end up without the role binding it needs. The problem is that you don't get a warning, so you don't know that there's a problem to fix.

* This can be fixed by just upgrading the Helm release.
* This may also be fixed by rolling back the Helm release.

#### Installing an operator and having it manage a domain namespace that doesn't exist

A new `FAILED` Helm release is created.
```shell
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values o.yaml weblogic-operator/weblogic-operator
```
```
Error: release op2 failed: namespaces "myuser-d2-ns" not found
```

To recover:

- `helm delete --purge` the failed release.
- Create the domain namespace.
- `helm install` again.

#### Upgrading an operator and having it manage a domain namespace that doesn't exist

The `helm upgrade` fails and moves the release to the `FAILED` state.
```shell
$ helm upgrade myuser-op weblogic-operator/weblogic-operator --values o.yaml --no-hooks
```
```
Error: UPGRADE FAILED: failed to create resource: namespaces "myuser-d2-ns" not found
```
To recover:

- `helm rollback`.
- Create the domain namespace.
- `helm upgrade` again.

### REST port conflict

REST port conflict-related mistakes.

#### Installing an operator and assigning it the same external REST port number as another operator

A new `FAILED` Helm release is created.
```shell
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values o.yaml weblogic-operator/weblogic-operator
```
```
Error: release op2 failed: Service "external-weblogic-operator-svc" is invalid: spec.ports[0].nodePort: Invalid value: 31023: provided port is already allocated
```

To recover:

- $ `helm delete --purge` the failed release.
- Change the port number and `helm install` the second operator again.

#### Upgrading an operator and assigning it the same external REST port number as another operator

The `helm upgrade` fails and moves the release to the `FAILED` state.
```shell
$ helm upgrade --no-hooks --values o23.yaml op2 weblogic-operator/weblogic-operator --wait
```
```
Error: UPGRADE FAILED: Service "external-weblogic-operator-svc" is invalid: spec.ports[0].nodePort: Invalid value: 31023: provided port is already allocated
```

* You can fix this by upgrading the Helm release (to fix the port number).
* You can also fix this by rolling back the Helm release.

### Missing service account

Missing service account-related mistakes.

#### Installing an operator and assigning it a service account that doesn't exist

The following `helm install` command fails because it tries to install an operator release with a non-existing service account `op2-sa`.
```shell
$ helm install op2 weblogic-operator/weblogic-operator --namespace myuser-op2-ns --set serviceAccount=op2-sa --wait --no-hooks
```

The output contains the following error message.
```
ServiceAccount op2-sa not found in namespace myuser-op2-ns
```
To recover:

- Create the service account.
- `helm install` again.

#### Upgrading an operator and assigning it a service account that doesn't exist

The `helm upgrade` with a non-existing service account fails with the same error message as mentioned in the previous section, and the existing operator deployment stays unchanged.

To recover:

- Create the service account.
- `helm upgrade` again.

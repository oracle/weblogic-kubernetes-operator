---
title: "Operator RBAC"
date: 2026-05-20T00:00:00-05:00
weight: 6
description: "Manually manage operator RBAC for selected namespaces."
---

{{< table_of_contents >}}

### Overview

This document describes a manual RBAC workflow for limiting a WebLogic Kubernetes
Operator installation to selected namespaces instead of granting the operator
service account broad domain management access across the cluster.

In this workflow:

- A cluster administrator installs the WebLogic Domain CRDs and the conversion webhook.
- A namespace administrator or installer installs the operator in `Dedicated` mode.
- An administrator manually creates, updates, and deletes the runtime RBAC that allows
  the operator service account to manage selected domain namespaces.
- The operator namespace selection policy can later be changed to `List`, `LabelSelector`,
  or `RegExp`, and RBAC for matching namespaces is managed separately.

Set these variables for the examples:

```shell
export OPERATOR_RELEASE=user-weblogic-operator
export OPERATOR_NS=sample-domain1-ns
export OPERATOR_SA=default
export DOMAIN_NS=sample-domain1-ns
export INSTALLER_USER=user
```

For OpenShift, also set:

```shell
export PLATFORM_ARGS='--set kubernetesPlatform=OpenShift'
```

For other Kubernetes clusters, use:

```shell
export PLATFORM_ARGS=''
```

### Administrator: Install the CRDs and webhook

A cluster administrator installs the CRDs and the conversion webhook once for the cluster.
The validating webhook service must be available before users create WebLogic
Domain resources. If the webhook configuration points to a service that does not
exist, Domain creation fails during admission.

```shell
helm install sample-weblogic-operator weblogic-operator/weblogic-operator \
  --namespace sample-weblogic-operator-ns \
  --create-namespace \
  ${PLATFORM_ARGS} \
  --set webhookOnly=true \
  --wait
```

Verify that the CRDs are installed:

```shell
kubectl get crd domains.weblogic.oracle clusters.weblogic.oracle
```

Verify the webhook deployment:

```shell
kubectl get deployment weblogic-operator-webhook -n sample-weblogic-operator-ns
kubectl rollout status deployment weblogic-operator-webhook -n sample-weblogic-operator-ns
kubectl get service weblogic-operator-webhook-svc -n sample-weblogic-operator-ns
```

If the same Helm release is used for both the operator and the webhook, do not
leave the release installed with `operatorOnly=true` when the CRD validating
webhook configuration points to that release namespace. Either install the
webhook separately with `webhookOnly=true`, as shown previously, or include the
webhook in the operator release:

```shell
helm upgrade ${OPERATOR_RELEASE} weblogic-operator/weblogic-operator \
  --namespace ${OPERATOR_NS} \
  --reuse-values \
  --set operatorOnly=false \
  --wait
```

### Administrator: Grant installer permissions

The namespace installer needs enough permission in the operator namespace to install
the operator Helm chart in `Dedicated` mode.

```shell
kubectl create namespace ${OPERATOR_NS}
```

```shell
kubectl apply -n ${OPERATOR_NS} -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: weblogic-operator-installer-role
rules:
- apiGroups: ["apps"]
  resources: ["deployments"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
- apiGroups: [""]
  resources: ["configmaps", "secrets", "services"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
- apiGroups: ["rbac.authorization.k8s.io"]
  resources: ["roles", "rolebindings"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
EOF
```

```shell
kubectl apply -n ${OPERATOR_NS} -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: weblogic-operator-installer-rolebinding
subjects:
- kind: User
  name: ${INSTALLER_USER}
  apiGroup: rbac.authorization.k8s.io
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: weblogic-operator-installer-role
EOF
```

Verify installer permissions:

```shell
kubectl auth can-i create deployments.apps \
  --as=${INSTALLER_USER} \
  -n ${OPERATOR_NS}

kubectl auth can-i create roles.rbac.authorization.k8s.io \
  --as=${INSTALLER_USER} \
  -n ${OPERATOR_NS}

kubectl auth can-i create rolebindings.rbac.authorization.k8s.io \
  --as=${INSTALLER_USER} \
  -n ${OPERATOR_NS}
```

### Installer: Install the operator in dedicated mode

Install the operator in `Dedicated` mode with cluster role binding disabled.

```shell
helm install ${OPERATOR_RELEASE} weblogic-operator/weblogic-operator \
  --namespace ${OPERATOR_NS} \
  ${PLATFORM_ARGS} \
  --set enableClusterRoleBinding=false \
  --set domainNamespaceSelectionStrategy=Dedicated \
  --set operatorOnly=true \
  --wait
```

Verify the operator deployment:

```shell
kubectl get deployment weblogic-operator -n ${OPERATOR_NS}
kubectl rollout status deployment weblogic-operator -n ${OPERATOR_NS}
```

### Administrator: Grant cluster-scoped runtime permissions

When the operator uses `List`, `LabelSelector`, or `RegExp`, it needs limited
cluster-scoped permissions for namespace discovery, CRD checks, webhook
configuration, token reviews, self subject rule reviews, persistent volumes, and
Kubernetes version checks. This role does not grant cluster-wide permission to
manage WebLogic domains.

```shell
kubectl apply -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: ${OPERATOR_NS}-weblogic-operator-cluster-common
rules:
- apiGroups: [""]
  resources: ["namespaces"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["apiextensions.k8s.io"]
  resources: ["customresourcedefinitions"]
  verbs: ["get", "list", "watch", "create", "update", "patch"]
- apiGroups: [""]
  resources: ["persistentvolumes"]
  verbs: ["get", "list", "create"]
- apiGroups: ["authentication.k8s.io"]
  resources: ["tokenreviews"]
  verbs: ["create"]
- apiGroups: ["authorization.k8s.io"]
  resources: ["selfsubjectrulesreviews"]
  verbs: ["create"]
- apiGroups: ["admissionregistration.k8s.io"]
  resources: ["validatingwebhookconfigurations"]
  verbs: ["get", "create", "update", "patch", "delete"]
- nonResourceURLs: ["/version/*"]
  verbs: ["get"]
EOF
```

```shell
kubectl apply -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: ${OPERATOR_NS}-weblogic-operator-cluster-common
subjects:
- kind: ServiceAccount
  name: ${OPERATOR_SA}
  namespace: ${OPERATOR_NS}
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: ${OPERATOR_NS}-weblogic-operator-cluster-common
EOF
```

Verify cluster-scoped runtime permissions:

```shell
kubectl auth can-i list namespaces \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA}

kubectl auth can-i watch namespaces \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA}

kubectl auth can-i list customresourcedefinitions.apiextensions.k8s.io \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA}

kubectl auth can-i create selfsubjectrulesreviews.authorization.k8s.io \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA}
```

### Administrator: Create runtime RBAC for a domain namespace

Create the domain namespace if it does not already exist:

```shell
kubectl create namespace ${DOMAIN_NS}
```

Create the namespace-local runtime role:

```shell
kubectl apply -n ${DOMAIN_NS} -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: weblogic-operator-role-namespace
  labels:
    weblogic.operatorName: "${OPERATOR_NS}"
rules:
- apiGroups: [""]
  resources: ["services", "configmaps", "pods"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources: ["persistentvolumeclaims"]
  verbs: ["get", "list", "create"]
- apiGroups: [""]
  resources: ["pods/log"]
  verbs: ["get", "list"]
- apiGroups: [""]
  resources: ["pods/exec"]
  verbs: ["get", "create"]
- apiGroups: ["events.k8s.io"]
  resources: ["events"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["batch"]
  resources: ["jobs"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["policy"]
  resources: ["poddisruptionbudgets"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["weblogic.oracle"]
  resources: ["domains", "clusters"]
  verbs: ["get", "create", "list", "watch", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["weblogic.oracle"]
  resources: ["domains/status", "clusters/status"]
  verbs: ["get", "list", "watch", "update", "patch"]
- apiGroups: ["weblogic.oracle"]
  resources: ["clusters/scale"]
  verbs: ["update", "patch"]
EOF
```

Bind the role to the operator service account:

```shell
kubectl apply -n ${DOMAIN_NS} -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: weblogic-operator-rolebinding-namespace
  labels:
    weblogic.operatorName: "${OPERATOR_NS}"
subjects:
- kind: ServiceAccount
  name: ${OPERATOR_SA}
  namespace: ${OPERATOR_NS}
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: weblogic-operator-role-namespace
EOF
```

Verify runtime permissions in the domain namespace:

```shell
kubectl auth can-i list pods \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA} \
  -n ${DOMAIN_NS}

kubectl auth can-i create jobs.batch \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA} \
  -n ${DOMAIN_NS}

kubectl auth can-i update domains.weblogic.oracle \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA} \
  -n ${DOMAIN_NS}

kubectl auth can-i patch clusters/status.weblogic.oracle \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA} \
  -n ${DOMAIN_NS}
```

### Administrator: Change namespace selection

Update the operator configuration ConfigMap after RBAC has been prepared for the
namespaces that the operator will manage.

For `List`:

```shell
kubectl patch configmap weblogic-operator-cm -n ${OPERATOR_NS} --type merge -p \
  '{"data":{"domainNamespaceSelectionStrategy":"List","domainNamespaces":"sample-domain1-ns,sample-domain2-ns"}}'
```

For `LabelSelector`:

```shell
kubectl label namespace ${DOMAIN_NS} weblogic-operator=enabled --overwrite

kubectl patch configmap weblogic-operator-cm -n ${OPERATOR_NS} --type merge -p \
  '{"data":{"domainNamespaceSelectionStrategy":"LabelSelector","domainNamespaceLabelSelector":"weblogic-operator=enabled"}}'
```

For `RegExp`:

```shell
kubectl patch configmap weblogic-operator-cm -n ${OPERATOR_NS} --type merge -p \
  '{"data":{"domainNamespaceSelectionStrategy":"RegExp","domainNamespaceRegExp":"^sample-domain[0-9]+-ns$"}}'
```

The operator periodically rereads the ConfigMap. To force immediate pickup of
the change:

```shell
kubectl rollout restart deployment weblogic-operator -n ${OPERATOR_NS}
kubectl rollout status deployment weblogic-operator -n ${OPERATOR_NS}
```

### Administrator: Add namespace RBAC

To add another managed namespace:

```shell
export DOMAIN_NS=sample-domain2-ns
kubectl create namespace ${DOMAIN_NS}
```

Create `Role/weblogic-operator-role-namespace` and
`RoleBinding/weblogic-operator-rolebinding-namespace` in the new namespace using
the YAML from [Create runtime RBAC for a domain namespace](#administrator-create-runtime-rbac-for-a-domain-namespace).

Then update the selection policy so the operator selects the new namespace.

For `List`:

```shell
kubectl patch configmap weblogic-operator-cm -n ${OPERATOR_NS} --type merge -p \
  '{"data":{"domainNamespaceSelectionStrategy":"List","domainNamespaces":"sample-domain1-ns,sample-domain2-ns"}}'
```

For `LabelSelector`:

```shell
kubectl label namespace ${DOMAIN_NS} weblogic-operator=enabled --overwrite
```

Verify access:

```shell
kubectl auth can-i list pods \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA} \
  -n ${DOMAIN_NS}
```

### Administrator: Update namespace RBAC

To update runtime permissions, edit the namespace-local role:

```shell
kubectl edit role weblogic-operator-role-namespace -n ${DOMAIN_NS}
```

You can also reapply the `Role` manifest from
[Create runtime RBAC for a domain namespace](#administrator-create-runtime-rbac-for-a-domain-namespace)
after making the required changes.

Verify the updated permission. For example:

```shell
kubectl auth can-i delete jobs.batch \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA} \
  -n ${DOMAIN_NS}
```

### Administrator: Remove namespace RBAC

Before removing RBAC, update the namespace selection policy so the operator no
longer selects the namespace.

For `List`:

```shell
kubectl patch configmap weblogic-operator-cm -n ${OPERATOR_NS} --type merge -p \
  '{"data":{"domainNamespaceSelectionStrategy":"List","domainNamespaces":"sample-domain1-ns"}}'
```

For `LabelSelector`:

```shell
kubectl label namespace ${DOMAIN_NS} weblogic-operator-
```

Then remove the namespace-local RBAC:

```shell
kubectl delete rolebinding weblogic-operator-rolebinding-namespace -n ${DOMAIN_NS}
kubectl delete role weblogic-operator-role-namespace -n ${DOMAIN_NS}
```

Verify that the operator service account no longer has runtime access:

```shell
kubectl auth can-i list pods \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA} \
  -n ${DOMAIN_NS}

kubectl auth can-i update domains.weblogic.oracle \
  --as=system:serviceaccount:${OPERATOR_NS}:${OPERATOR_SA} \
  -n ${DOMAIN_NS}
```

If the namespace still matches the operator namespace selection policy after
the namespace-local RBAC is removed, a later Domain deployment is expected to
fail. For example, admission may fail because the validating webhook uses the
operator service account and the service account can no longer list WebLogic
Domain resources in that namespace:

```text
domains.weblogic.oracle is forbidden:
User "system:serviceaccount:<operator-namespace>:<operator-service-account>"
cannot list resource "domains" in API group "weblogic.oracle"
in the namespace "<domain-namespace>"
```

### Administrator: Review RBAC

List installer RBAC:

```shell
kubectl get role weblogic-operator-installer-role -n ${OPERATOR_NS}
kubectl get rolebinding weblogic-operator-installer-rolebinding -n ${OPERATOR_NS}
```

List operator runtime RBAC in a managed namespace:

```shell
kubectl get role weblogic-operator-role-namespace -n ${DOMAIN_NS}
kubectl get rolebinding weblogic-operator-rolebinding-namespace -n ${DOMAIN_NS}
```

List cluster-scoped runtime RBAC:

```shell
kubectl get clusterrole ${OPERATOR_NS}-weblogic-operator-cluster-common
kubectl get clusterrolebinding ${OPERATOR_NS}-weblogic-operator-cluster-common
```

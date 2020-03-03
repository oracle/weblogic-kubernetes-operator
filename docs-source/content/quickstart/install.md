---
title: "Install the operator and load balancer"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 4
---

#### Grant the Helm service account the `cluster-admin` role.

```bash
$ cat <<EOF | kubectl apply -f -
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: helm-user-cluster-admin-role
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: default
  namespace: kube-system
EOF
```

#### Use Helm to install the operator and [Traefik](http://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/traefik/README.md) load balancer.

First, set up Helm.

For Helm version 2.x:

```bash
$ helm init

```

For Helm version 3.x:

```bash
$ helm repo add stable https://kubernetes-charts.storage.googleapis.com/
```

#### Create a Traefik (Ingress-based) load balancer.

Create a namespace for the load balancer.

```bash
$ kubectl create namespace traefik
```

Use the [values.yaml](http://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/charts/traefik/values.yaml) in the sample but set `kubernetes.namespaces` specifically.

For Helm 2.x:

```bash
$ helm install stable/traefik \
  --name traefik-operator \
  --namespace traefik \
  --values kubernetes/samples/charts/traefik/values.yaml \
  --set "kubernetes.namespaces={traefik}" \
  --wait
```

For Helm 3.x:

```bash
$ helm install traefik-operator stable/traefik \
    --namespace traefik \
    --values kubernetes/samples/charts/traefik/values.yaml \
    --set "kubernetes.namespaces={traefik}" \
    --wait
```

#### Install the operator.

1.  Create a namespace for the operator:

    ```bash
    $ kubectl create namespace sample-weblogic-operator-ns
    ```

2.	Create a service account for the operator in the operator's namespace:

    ```bash
    $ kubectl create serviceaccount -n sample-weblogic-operator-ns sample-weblogic-operator-sa
    ```

3.  Use `helm` to install and start the operator from the directory you just cloned:	 

    For Helm 2.x:

    ```bash
    $ helm install kubernetes/charts/weblogic-operator \
      --name sample-weblogic-operator \
      --namespace sample-weblogic-operator-ns \
      --set image=oracle/weblogic-kubernetes-operator:2.5.0 \
      --set serviceAccount=sample-weblogic-operator-sa \
      --set "domainNamespaces={}" \
      --wait
    ```

    For Helm 3.x:

    ```bash
    $ helm install sample-weblogic-operator kubernetes/charts/weblogic-operator \
      --namespace sample-weblogic-operator-ns \
      --set image=oracle/weblogic-kubernetes-operator:2.5.0 \
      --set serviceAccount=sample-weblogic-operator-sa \
      --set "domainNamespaces={}" \
      --wait
    ```

4. Verify that the operator's pod is running, by listing the pods in the operator's namespace. You should see one
for the operator.

    ```bash
    $ kubectl get pods -n sample-weblogic-operator-ns
    ```

5.  Verify that the operator is up and running by viewing the operator pod's log:

    ```bash
    $ kubectl logs -n sample-weblogic-operator-ns -c weblogic-operator deployments/weblogic-operator
    ```

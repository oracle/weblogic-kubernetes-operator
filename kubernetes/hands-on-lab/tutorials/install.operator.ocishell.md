# Oracle WebLogic Operator Tutorial #

### Install and configure Operator  ###

An operator is an application-specific controller that extends Kubernetes to create, configure, and manage instances of complex applications. The Oracle WebLogic Server Kubernetes Operator (the "operator") simplifies the management and operation of WebLogic domains and deployments.

#### Clone WebLogic Operator repository to Cloud Shell instance ####
First clone the WebLogic Operator git repository to OCI Cloud Shell.
```bash
$ git clone https://github.com/oracle/weblogic-kubernetes-operator.git  -b v2.5.0
```
The output has to be similar to the following:
```bash
Cloning into 'weblogic-kubernetes-operator'...
remote: Enumerating objects: 1568, done.
remote: Counting objects: 100% (1568/1568), done.
remote: Compressing objects: 100% (641/641), done.
remote: Total 123827 (delta 748), reused 1284 (delta 567), pack-reused 122259
Receiving objects: 100% (123827/123827), 86.55 MiB | 28.25 MiB/s, done.
Resolving deltas: 100% (74546/74546), done.
Note: checking out 'b9d4b934ebd90df9726dc4051ece82491bd726c0'.

You are in 'detached HEAD' state. You can look around, make experimental
changes and commit them, and you can discard any commits you make in this
state without impacting any branches by performing another checkout.

If you want to create a new branch to retain commits you create, you may
do so (now or later) by using -b with the checkout command again. Example:

  git checkout -b new_branch_name

Checking out files: 100% (8396/8396), done.
```
#### Prepare the environment ####
In order to use Helm to install and manage the operator, you need to ensure that the service account has the `cluster-admin` role.  The default would be `default` in namespace `kube-system`.  You can give that service
account the necessary permissions with this command:

```bash
cat << EOF | kubectl apply -f -
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
The output has to be the following:
```bash
clusterrolebinding.rbac.authorization.k8s.io/helm-user-cluster-admin-role created
```
Kubernetes distinguishes between the concept of a user account and a service account for a number of reasons. The main reason is that user accounts are for humans while service accounts are for processes, which run in pods. WebLogic Operator also requires service accounts.  If service account not specified, it defaults to default (for example, the namespace's default service account). If you want to use a different service account, then you must create the operator's namespace and the service account before installing the operator Helm chart.

Thus create operator's namespace in advance:
```bash
kubectl create namespace sample-weblogic-operator-ns
```
Create the service account:
```bash
kubectl create serviceaccount -n sample-weblogic-operator-ns sample-weblogic-operator-sa
```
Finally add stable repository to helm which will be neccessary later for 3rd party services.
```bash
helm repo add stable https://kubernetes-charts.storage.googleapis.com/
```
#### Install WebLogic Operator using Helm ####
Make sure before execute operator `helm` install you are in the WebLogic Operator's local Git repository folder.
```bash
cd ~/weblogic-kubernetes-operator/
```
Use the `helm install` command to install the operator Helm chart. As part of this, you must specify a "release" name for their operator.

You can override default configuration values in the operator Helm chart by doing one of the following:

- Creating a [custom YAML](https://github.com/oracle/weblogic-kubernetes-operator/blob/v2.5.0/kubernetes/charts/weblogic-operator/values.yaml) file containing the values to be overridden, and specifying the `--value` option on the Helm command line.
- Overriding individual values directly on the Helm command line, using the `--set` option.

Using the last option simply define overriding values using the `--set` option.

Note the values:

- **name**: name of the resource
- **namespace**: where the operator deployed
- **image**: the prebuilt WebLogic Operator 2.5.0 image. Available on public Docker hub.
- **serviceAccount**: service account required to run operator
- **domainNamespaces**: namespaces where WebLogic domains deployed in order to control. Note WebLogic domain is not yet deployed so this value will be updated when namespaces created for WebLogic deployment.

Execute the following `helm install`:
```bash
helm install sample-weblogic-operator \
  kubernetes/charts/weblogic-operator \
  --namespace sample-weblogic-operator-ns \
  --set image=oracle/weblogic-kubernetes-operator:2.5.0 \
  --set serviceAccount=sample-weblogic-operator-sa \
  --set "domainNamespaces={}"
```
The output has to be similar:
```bash
NAME: sample-weblogic-operator
LAST DEPLOYED: Fri Mar  6 18:24:29 2020
NAMESPACE: sample-weblogic-operator-ns
STATUS: deployed
REVISION: 1
TEST SUITE: None
```
Check the operator pod:
```bash
$ kubectl get po -n sample-weblogic-operator-ns
NAME                                READY   STATUS    RESTARTS   AGE
weblogic-operator-86ff6fccc-gh79p   1/1     Running   0          69s
```
Check the operator helm chart:
```bash
$ helm list -n sample-weblogic-operator-ns
NAME                            NAMESPACE                       REVISION        UPDATED                                 STATUS          CHART                   APP VERSION
sample-weblogic-operator        sample-weblogic-operator-ns     1               2020-03-06 18:24:29.312983566 +0000 UTC deployed        weblogic-operator-2.5.0
```

The WebLogic Operator has been installed. You can continue with next tutorial module.

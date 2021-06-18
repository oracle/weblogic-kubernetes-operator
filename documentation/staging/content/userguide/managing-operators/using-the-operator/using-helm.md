---
title: "Use Helm"
date: 2019-02-23T17:08:43-05:00
weight: 1
description: "Useful Helm operations."
---

#### Contents

* [Useful Helm operations](#useful-helm-operations)
* [Operator Helm configuration values](#operator-helm-configuration-values)
  * [Overall operator information](#overall-operator-information)
  * [Creating the operator pod](#creating-the-operator-pod)
  * [WebLogic domain management](#weblogic-domain-management)
  * [Elastic Stack integration](#elastic-stack-integration)
  * [REST interface configuration](#rest-interface-configuration)
  * [Debugging options](#debugging-options)
* [Common mistakes and solutions](#common-mistakes-and-solutions)

Note that the operator Helm chart is available from the GitHub chart repository. For more details, see [Alternatively, install the operator Helm chart from the GitHub chart repository]({{< relref "/userguide/managing-operators/installation/_index.md#alternatively-install-the-operator-helm-chart-from-the-github-chart-repository" >}}).

#### Useful Helm operations

Show the available operator configuration values and their defaults:
```shell
$ helm inspect values kubernetes/charts/weblogic-operator
```

Show the custom values you configured for the operator Helm release:
```shell
$ helm get values weblogic-operator
```

Show all of the values your operator Helm release is using:
```shell
$ helm get values --all weblogic-operator
```

List the Helm releases for a specified namespace or all namespaces:
```shell
$ helm list --namespace <namespace>
```
```shell
$ helm list --all-namespaces
```

Get the status of the operator Helm release:
```shell
$ helm status weblogic-operator --namespace <namespace>
```

Show the history of the operator Helm release:
```shell
$ helm history weblogic-operator --namespace <namespace>
```

Roll back to a previous version of this operator Helm release, in this case, the first version:
```shell
$ helm rollback weblogic-operator 1 --namespace <namespace>
```

Change one or more values in the operator Helm release. In this example, the `--reuse-values` flag indicates that previous overrides of other values should be retained:
```shell
$ helm upgrade \
  --reuse-values \
  --set "domainNamespaces={sample-domains-ns1}" \
  --set "javaLoggingLevel=FINE" \
  --wait \
  weblogic-operator \
  kubernetes/charts/weblogic-operator
```

### Operator Helm configuration values

This section describes the details of the operator Helm chart's available configuration values.

#### Overall operator information

##### `serviceAccount`
Specifies the name of the service account in the operator's namespace that the operator will use to make requests to the Kubernetes API server. You are responsible for creating the service account.
The `helm install` or `helm upgrade` command with a non-existing service account results in a Helm chart validation error.

Defaults to `default`.

Example:
```yaml
serviceAccount: "weblogic-operator"
```

##### `javaLoggingLevel`
Specifies the level of Java logging that should be enabled in the operator. Valid values are:  `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, and `FINEST`.

Defaults to `INFO`.

Example:
```yaml
javaLoggingLevel:  "FINE"
```

#### Creating the operator pod

##### `image`
Specifies the container image containing the operator code.

Defaults to `ghcr.io/oracle/weblogic-kubernetes-operator:3.2.4`.

Example:
```yaml
image:  "ghcr.io/oracle/weblogic-kubernetes-operator:some-tag"
```

##### `imagePullPolicy`
Specifies the image pull policy for the operator container image.

Defaults to `IfNotPresent`.

Example:
```yaml
image:  "Always"
```

##### `imagePullSecrets`
Contains an optional list of Kubernetes Secrets, in the operator's namespace, that are needed to access the registry containing the operator image. You are responsible for creating the secret. If no secrets are required, then omit this property.

Example:
```yaml
imagePullSecrets:
- name: "my-image-pull-secret"
```

##### `annotations`
Specifies a set of key-value annotations that will be added to each pod running the operator. If no customer defined annotations are required, then omit this property.

Example:
```yaml
annotations:
  stage: production
```

You may also specify annotations [using the `--set` parameter to the Helm install command](https://helm.sh/docs/intro/using_helm/#customizing-the-chart-before-installing), as follows:

```
--set annotations.stage=production
```

##### `labels`
Specifies a set of key-value labels that will be added to each pod running the operator. The Helm chart will automatically add any required labels, so the customer is not required to define those here. If no customer defined labels are required, then omit this property.

Example:
```yaml
labels:
  sidecar.istio.io/inject: "false"
```

You may also specify labels [using the `--set` parameter to the Helm install command](https://helm.sh/docs/intro/using_helm/#customizing-the-chart-before-installing), as follows:

```
--set labels."sidecar\.istio\.io/inject"=false
```

##### `nodeSelector`
Allows you to run the operator Pod on a Node whose labels match the specified `nodeSelector` labels. You can use this optional feature if you want the operator Pod to run on a Node with particular labels. For more details, see [Assign Pods to Nodes](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#nodeselector) in the Kubernetes documentation for more details. This is not required if the operator Pod can run on any Node.

Example:
```yaml
nodeSelector:
  disktype: ssd
```

##### `affinity`
Allows you to constrain the operator Pod to be scheduled on a Node with certain labels; it is conceptually similar to `nodeSelector`. `affinity` provides advanced capabilities to limit Pod placement on specific Nodes. For more details, see  [Assign Pods to Nodes](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#node-affinity) in the Kubernetes documentation for more details. This is optional and not required if the operator Pod can run on any Node or when using `nodeSelector`.

Example:
```yaml
affinity:
  nodeAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
      nodeSelectorTerms:
      - matchExpressions:
        - key: nodeType
          operator: In
          values:
          - dev
          - test
    preferredDuringSchedulingIgnoredDuringExecution:
    - weight: 1
      preference:
        matchExpressions:
        - key: another-node-label-key
          operator: In
          values:
          - another-node-label-value
```

##### `enableClusterRoleBinding`
Specifies whether the roles necessary for the operator to manage domains
will be granted using a ClusterRoleBinding rather than using RoleBindings in each managed namespace.

Defaults to `false`.

This option greatly simplifies managing namespaces when the selection is done using label selectors or
regular expressions as the operator will already have privilege in any namespace.

Customers who deploy the operator in Kubernetes clusters that run unrelated workloads will likely
not want to use this option.

If `enableClusterRoleBinding` is `false` and you select namespaces that the operator will
manage using label selectors or a regular expression, then the Helm release will only include
RoleBindings in each namespace that match at the time the Helm release is created. If you later
create namespaces that the operator should manage, the new namespaces will not yet have the necessary
RoleBinding.

You can correct this by upgrading the Helm release and reusing values:
```shell
$ helm upgrade \
  --reuse-values \
  weblogic-operator \
  kubernetes/charts/weblogic-operator
```

#### WebLogic domain management

##### `domainNamespaceSelectionStrategy`
Specifies how the operator will select the set of namespaces that it will manage. 
Legal values are: `List`, `LabelSelector`, `RegExp`, and `Dedicated`.

Defaults to `List`.

If set to `List`, then the operator will manage the set of namespaces listed by the `domainNamespaces` value.
If set to `LabelSelector`, then the operator will manage the set of namespaces discovered by a list
of namespaces using the value specified by `domainNamespaceLabelSelector` as a label selector.
If set to `RegExp`, then the operator will manage the set of namespaces discovered by a list
of namespaces using the value specified by `domainNamespaceRegExp` as a regular expression matched
against the namespace names.
Finally, if set to `Dedicated`, then operator will manage WebLogic Domains only in the same namespace
which the operator itself is deployed, which is the namespace of the Helm release.

##### `domainNamespaces`
Specifies a list of namespaces that the operator manages. The names must be lowercase. You are responsible for creating these namespaces.
The operator will only manage Domains found in these namespaces.
This value is required if `domainNamespaceSelectionStrategy` is `List` and ignored otherwise.

Example 1: In the configuration below, the operator will manage the `default` Kubernetes Namespace:
```yaml
domainNamespaces:
- "default"
```

Example 2: In the configuration below, the operator will manage `namespace1` and `namespace2`:
```yaml
domainNamespaces: [ "namespace1", "namespace2" ]
```

{{% notice note %}}
These examples show two valid YAML syntax options for arrays.
{{% /notice %}}

{{% notice note %}}
You must include the `default` namespace in the list if you want the operator to monitor both the `default` namespace and some other namespaces.
{{% /notice %}}

{{% notice note %}}
This value is ignored if `dedicated` is set to `true`. Then, the operator will manage only domains in its own namespace.
{{% /notice %}}

For more details about managing `domainNamespaces`, see [Managing domain namespaces]({{< relref "/faq/namespace-management.md" >}}).

##### `domainNamespaceLabelSelector`
Specifies a [label selector](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/#label-selectors) that will be used when searching for namespaces that the operator will manage.
The operator will only manage Domains found in namespaces matching this selector.
This value is required if `domainNamespaceSelectionStrategy` is `LabelSelector` and ignored otherwise.

If `enableClusterRoleBinding` is `false`, the Helm chart will create RoleBindings in each namespace that matches the selector.
These RoleBindings give the operator's service account the necessary privileges in the namespace. The Helm chart will only create
these RoleBindings in namespaces that match the label selector at the time the chart is installed. If you later create namespaces
that match the selector or label existing namespaces that make them now match the selector, then the operator will not have
privilege in these namespaces until you upgrade the Helm release.

Example 1: In the configuration below, the operator will manage namespaces that have the label "weblogic-operator"
regardless of the value of that label:
```yaml
domainNamespaceLabelSelector: weblogic-operator
```

Example 2: In the configuration below, the operator will manage all namespaces that have the label "environment",
but where the value of that label is not "production" or "systemtest":
```yaml
domainNamespaceLabelSelector: environment notin (production,systemtest)
```

{{% notice note %}}
To specify the above sample on the Helm command line, escape spaces and commas as follows:
```
--set "domainNamespaceLabelSelector=environment\\ notin\\ (production\\,systemtest)"
```
{{% /notice %}}

##### `domainNamespaceRegExp`
Specifies a regular expression that will be used when searching for namespaces that the operator will manage.
The operator will only manage Domains found in namespaces matching this regular expression.
This value is required if `domainNamespaceSelectionStrategy` is `RegExp` and ignored otherwise.

If `enableClusterRoleBinding` is `false`, the Helm chart will create RoleBindings in each namespace that matches the regular expression.
These RoleBindings give the operator's service account the necessary privileges in the namespace. The Helm chart will only create
these RoleBindings in namespaces that match the regular expression at the time the chart is installed. If you later create namespaces
that match the selector or label existing namespaces that make them now match the selector, the operator will not have
privilege in these namespaces until you upgrade the Helm release.

{{% notice note %}}
The regular expression functionality included with Helm is restricted to linear time constructs and,
in particular, does not support lookarounds. The operator, written in Java, supports these
complicated expressions. If you need to use a complex regular expression, then either set
`enableClusterRoleBinding` to `true` or create the necessary RoleBindings outside of Helm.
{{% /notice %}}

##### `dedicated` ***(Deprecated)***
Specifies if this operator will manage WebLogic domains only in the same namespace in which the operator itself is deployed. If set to `true`, then the `domainNamespaces` value is ignored.

This field is deprecated. Use `domainNamespaceSelectionStrategy: Dedicated` instead.

Defaults to `false`.

Example:
```yaml
dedicated: false
```

In the `dedicated` mode, the operator does not require permissions to access the cluster-scoped Kubernetes resources, such as `CustomResourceDefinitions`, `PersistentVolumes`, and `Namespaces`. In those situations, the operator may skip some of its operations, such as verifying the WebLogic domain `CustomResoruceDefinition` `domains.weblogic.oracle` (and creating it when it is absent), watching namespace events, and cleaning up `PersistentVolumes` as part of deleting a domain.

{{% notice note %}}
It is the responsibility of the administrator to make sure that the required `CustomResourceDefinition (CRD)` `domains.weblogic.oracle` is deployed in the Kubernetes cluster before the operator is installed. The creation of the `CRD` requires the Kubernetes `cluster-admin` privileges. A YAML file for creating the `CRD` can be found at [domain-crd.yaml](http://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/crd/domain-crd.yaml).
{{% /notice %}}

##### `domainPresenceFailureRetryMaxCount` and `domainPresenceFailureRetrySeconds`
Specify the number of introspector job retries for a Domain and the interval in seconds between these retries.

Defaults to 5 retries and 10 seconds between each retry.

Example:
```yaml
domainPresenceFailureRetryMaxCount: 10
domainPresenceFailureRetrySeconds: 30
```

##### `introspectorJobNameSuffix` and `externalServiceNameSuffix`
Specify the suffixes that the operator uses to form the name of the Kubernetes job for the domain introspector, and the name of the external service for the WebLogic Administration Server, if the external service is enabled.

Defaults to `-introspector` and `-ext` respectively. The values cannot be more than 25 and 10 characters respectively. 

{{% notice note %}}
Prior to the operator 3.1.0 release, the suffixes are hard-coded to `-introspect-domain-job` and `-external`. The defaults are shortened in newer releases to support longer names in the domain resource and WebLogic domain configurations, such as the `domainUID`, and WebLogic cluster and server names.
{{% /notice %}}

{{% notice note %}}
In order to work with Kubernetes limits to resource names, the resultant names for the domain introspector job and the external service should not be more than 63 characters. For more details, see [Meet Kubernetes resource name restrictions]({{< relref "/userguide/managing-domains/_index.md#meet-kubernetes-resource-name-restrictions" >}}).
{{% /notice %}}

##### `clusterSizePaddingValidationEnabled`
Specifies if the operator needs to reserve additional padding when validating the server service names to account for longer Managed Server names as a result of expanding a cluster's size in WebLogic domain configurations. 

Defaults to `true`.

If `clusterSizePaddingValidationEnabed` is set to true, two additional characters will be reserved if the configured cluster's size is between one and nine, and one additional character will be reserved if the configured cluster's size is between 10 and 99. No additional character is reserved if the configured cluster's size is greater than 99.

#### Elastic Stack integration

##### `elkIntegrationEnabled`
Specifies whether or not Elastic Stack integration is enabled.

Defaults to `false`.

Example:
```yaml
elkIntegrationEnabled:  true
```

##### `logStashImage`
Specifies the container image containing Logstash.  This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `logstash:6.6.0`.

Example:
```yaml
logStashImage:  "logstash:6.2"
```

##### `elasticSearchHost`
Specifies the hostname where Elasticsearch is running. This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `elasticsearch.default.svc.cluster.local`.

Example:
```yaml
elasticSearchHost: "elasticsearch2.default.svc.cluster.local"
```

##### `elasticSearchPort`
Specifies the port number where Elasticsearch is running. This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `9200`.

Example:
```yaml
elasticSearchPort: 9201
```

#### REST interface configuration

##### `externalRestEnabled`
Determines whether the operator's REST interface will be exposed outside the Kubernetes cluster.

Defaults to `false`.

If set to `true`, you must provide the `externalRestIdentitySecret` property that contains the name of the Kubernetes Secret which contains the SSL certificate and private key for the operator's external REST interface.

Example:
```yaml
externalRestEnabled: true
```

##### `externalRestHttpsPort`
Specifies the node port that should be allocated for the external operator REST HTTPS interface.

Only used when `externalRestEnabled` is `true`, otherwise ignored.

Defaults to `31001`.

Example:
```yaml
externalRestHttpsPort: 32009
```

##### `externalRestIdentitySecret`
Specifies the user supplied secret that contains the SSL/TLS certificate and private key for the external operator REST HTTPS interface. The value must be the name of the Kubernetes `tls` secret previously created in the namespace where the operator is deployed. This parameter is required if `externalRestEnabled` is `true`, otherwise, it is ignored. In order to create the Kubernetes `tls` secret you can use the following command:

```shell
$ kubectl create secret tls <secret-name> \
  -n <operator-namespace> \
  --cert=<path-to-certificate> \
  --key=<path-to-private-key>
```

There is no default value.

The Helm installation will produce an error, similar to the following, if `externalRestIdentitySecret` is not specified (left blank) and `externalRestEnabled` is `true`:
```
Error: render error in "weblogic-operator/templates/main.yaml": template: weblogic-operator/templates/main.yaml:9:3: executing "weblogic-operator/templates/main.yaml"
    at <include "operator.va...>: error calling include: template: weblogic-operator/templates/_validate-inputs.tpl:42:14: executing "operator.validateInputs"
    at <include "utils.endVa...>: error calling include: template: weblogic-operator/templates/_utils.tpl:22:6: executing "utils.endValidation"
    at <fail $scope.validati...>: error calling fail:
 string externalRestIdentitySecret must be specified
```

Example:
```yaml
externalRestIdentitySecret: weblogic-operator-external-rest-identity
```

##### `externalOperatorCert` ***(Deprecated)***
{{% notice info %}}
Use **`externalRestIdentitySecret`** instead
{{% /notice %}}

Specifies the user supplied certificate to use for the external operator REST HTTPS interface. The value must be a string containing a Base64 encoded PEM certificate. This parameter is required if `externalRestEnabled` is `true`, otherwise, it is ignored.

There is no default value.

The Helm installation will produce an error, similar to the following, if `externalOperatorCert` is not specified (left blank) and `externalRestEnabled` is `true`:
```
Error: render error in "weblogic-operator/templates/main.yaml": template: weblogic-operator/templates/main.yaml:4:3: executing "weblogic-operator/templates/main.yaml"
  at <include "operator.va...>: error calling include: template: weblogic-operator/templates/_validate-inputs.tpl:53:4: executing "operator.validateInputs"
  at <include "operator.re...>: error calling include: template: weblogic-operator/templates/_utils.tpl:137:6: executing "operator.reportValidationErrors"
  at <fail .validationErro...>: error calling fail: The string property externalOperatorCert must be specified.
```

Example:
```yaml
externalOperatorCert: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUQwakNDQXJxZ0F3S ...
```

##### `externalOperatorKey` ***(Deprecated)***
{{% notice info %}}
Use **`externalRestIdentitySecret`** instead
{{% /notice %}}

Specifies user supplied private key to use for the external operator REST HTTPS interface. The value must be a string containing a Base64 encoded PEM key. This parameter is required if `externalRestEnabled` is `true`, otherwise, it is ignored.

There is no default value.

The Helm installation will produce an error, similar to the following, if `externalOperatorKey` is not specified (left blank) and `externalRestEnabled` is `true`:
```
Error: render error in "weblogic-operator/templates/main.yaml": template: weblogic-operator/templates/main.yaml:4:3: executing "weblogic-operator/templates/main.yaml"
  at <include "operator.va...>: error calling include: template: weblogic-operator/templates/_validate-inputs.tpl:53:4: executing "operator.validateInputs"
  at <include "operator.re...>: error calling include: template: weblogic-operator/templates/_utils.tpl:137:6: executing "operator.reportValidationErrors"
  at <fail .validationErro...>: error calling fail: The string property externalOperatorKey must be specified.
```

Example:
```yaml
externalOperatorKey: QmFnIEF0dHJpYnV0ZXMKICAgIGZyaWVuZGx5TmFtZTogd2VibG9naWMtb3B ...
```
##### `tokenReviewAuthentication`
If set to `true`, `tokenReviewAuthentication` specifies whether the the operator's REST API should use:
   * Kubernetes token review API for authenticating users and
   * Kubernetes subject access review API for authorizing a user's operation (`get`, `list`,
      `patch`, and such) on a resource.
   * Update the Domain resource using the operator's privileges.
 
 If set to `false`, the operator's REST API will use the caller's bearer token for any update
 to the Domain resource so that it is done using the caller's privileges.
 
 Defaults to `false`.
 
 Example:
 ```yaml
 tokenReviewAuthentication: true
 ```
#### Debugging options

##### `remoteDebugNodePortEnabled`
Specifies whether or not the operator will start a Java remote debug server on the provided port and suspend execution until a remote debugger has attached.

Defaults to `false`.

Example:
```yaml
remoteDebugNodePortEnabled:  true
```

##### `internalDebugHttpPort`

Specifies the port number inside the Kubernetes cluster for the operator's Java remote debug server.

This parameter is required if `remoteDebugNodePortEnabled` is `true`. Otherwise, it is ignored.

Defaults to `30999`.

Example:
```yaml
internalDebugHttpPort:  30888
```

##### `externalDebugHttpPort`
Specifies the node port that should be allocated for the Kubernetes cluster for the operator's Java remote debug server.

This parameter is required if `remoteDebugNodePortEnabled` is `true`. Otherwise, it is ignored.

Defaults to `30999`.

Example:
```yaml
externalDebugHttpPort:  30777
```

### Common mistakes and solutions

#### Installing the operator a second time into the same namespace

A new `FAILED` Helm release is created.
```shell
$ helm install --no-hooks --name op2 --namespace myuser-op-ns --values custom-values.yaml kubernetes/charts/weblogic-operator
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
See https://github.com/helm/helm/issues/2349

#### Installing an operator and having it manage a domain namespace that another operator is already managing

A new `FAILED` Helm release is created.
```shell
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values custom-values.yaml kubernetes/charts/weblogic-operator
```
```
Error: release op2 failed: rolebindings.rbac.authorization.k8s.io "weblogic-operator-rolebinding-namespace" already exists
```

To recover:

- `helm delete --purge` the failed release.
  - **NOTE**: This deletes the role binding in the domain namespace that was created by the first operator release, to give the operator access to the domain namespace.
- `helm upgrade <old op release> kubernetes/charts/weblogic-operator --values <old op custom-values.yaml>`
  - This recreates the role binding.
  - There might be intermittent failures in the operator for the period of time when the role binding was deleted.

#### Upgrading an operator and having it manage a domain namespace that another operator is already managing

The `helm upgrade` succeeds, and silently adopts the resources the first operator's Helm chart created in the domain namespace (for example, `rolebinding`), and, if you also instructed it to stop managing another domain namespace, it abandons the role binding it created in that namespace.

For example, if you delete this release, then the first operator will end up without the role binding it needs. The problem is that you don't get a warning, so you don't know that there's a problem to fix.

* This can be fixed by just upgrading the Helm release.
* This may also be fixed by rolling back the Helm release.

#### Installing an operator and assigning it the same external REST port number as another operator

A new `FAILED` Helm release is created.
```shell
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values o.yaml kubernetes/charts/weblogic-operator
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
$ helm upgrade --no-hooks --values o23.yaml op2 kubernetes/charts/weblogic-operator --wait
```
```
Error: UPGRADE FAILED: Service "external-weblogic-operator-svc" is invalid: spec.ports[0].nodePort: Invalid value: 31023: provided port is already allocated
```

* You can fix this by upgrading the Helm release (to fix the port number).
* You can also fix this by rolling back the Helm release.

#### Installing an operator and assigning it a service account that doesn't exist

The following `helm install` command fails because it tries to install an operator release with a non-existing service account `op2-sa`.
```shell
$ helm install op2 kubernetes/charts/weblogic-operator --namespace myuser-op2-ns --set serviceAccount=op2-sa --wait --no-hooks
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

#### Installing an operator and having it manage a domain namespace that doesn't exist

A new `FAILED` Helm release is created.
```shell
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values o.yaml kubernetes/charts/weblogic-operator
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
$ helm upgrade myuser-op kubernetes/charts/weblogic-operator --values o.yaml --no-hooks
```
```
Error: UPGRADE FAILED: failed to create resource: namespaces "myuser-d2-ns" not found
```
To recover:

- `helm rollback`
- Create the domain namespace.
- `helm upgrade` again.

#### Deleting and recreating a namespace that an operator manages without informing the operator

If you create a new domain in a namespace that is deleted and recreated, the domain does not start up until you notify the operator.
For more details about the problem and solutions, see [Managing domain namespaces]({{<relref "/faq/namespace-management.md">}}).

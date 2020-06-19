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

Note that the operator Helm chart is available from the GitHub chart repository, see [Alternatively, install the operator Helm chart from the GitHub chart repository]({{< relref "/userguide/managing-operators/installation/_index.md#alternatively-install-the-operator-helm-chart-from-the-github-chart-repository" >}}).

#### Useful Helm operations

Show the available operator configuration parameters and their default values:
```
$ helm inspect values kubernetes/charts/weblogic-operator
```

Show the custom values you configured for the operator Helm release:
```
$ helm get values weblogic-operator
```

Show all of the values your operator Helm release is using:
```
$ helm get values --all weblogic-operator
```

List the Helm releases for a specified namespace or all namespaces:
```
$ helm list --namespace <namespace>
$ helm list --all-namespaces
```

Get the status of the operator Helm release:
```
$ helm status weblogic-operator
```

Show the history of the operator Helm release:
```
$ helm history weblogic-operator
```

Roll back to a previous version of this operator Helm release, in this case, the first version:
```
$ helm rollback weblogic-operator 1
```

Change one or more values in the operator Helm release. In this example, the `--reuse-values` flag indicates that previous overrides of other values should be retained:
```
$ helm upgrade \
  --reuse-values \
  --set "domainNamespaces={sample-domains-ns1}" \
  --set "javaLoggingLevel=FINE" \
  --wait \
  weblogic-operator \
  kubernetes/charts/weblogic-operator
```

Enable operator debugging on port 30999. Again, we use `--reuse-values` to change one value without affecting the others:
```
$ helm upgrade \
  --reuse-values \
  --set "remoteDebugNodePortEnabled=true" \
  --wait \
  weblogic-operator \
  kubernetes/charts/weblogic-operator
```

### Operator Helm configuration values

This section describes the details of the operator Helm chart's available configuration values.

#### Overall operator information

##### `serviceAccount`

Specifies the name of the service account in the operator's namespace that the operator will use to make requests to the Kubernetes API server. You are responsible for creating the service account.

Defaults to `default`.

Example:
```
serviceAccount: "weblogic-operator"
```

##### `dedicated`

Specifies if this operator will manage WebLogic domains only in the same namespace in which the operator itself is deployed.  If set to `true`, then the `domainNamespaces` value is ignored.

Defaults to `false`.

Example:
```
dedicated: false
```

In the `dedicated` mode, the operator does not require permissions to access the cluster-scoped Kubernetes resources, such as `CustomResourceDefinitions`, `PersistentVolumes`, and `Namespaces`. In those situations, the operator may skip some of its operations, such as verifying the WebLogic domain `CustomResoruceDefinition` `domains.weblogic.oracle` (and creating it when it is absent), watching namespace events, and cleaning up `PersistentVolumes` as part of deleting a domain.

{{% notice note %}}
It is the responsibility of the administrator to make sure that the required `CustomResourceDefinition (CRD)` `domains.weblogic.oracle` is deployed in the Kubernetes cluster before the operator is installed. The creation of the `CRD` requires the Kubernetes `cluster-admin` privileges. A YAML file for creating the `CRD` can be found at [domain-crd.yaml](http://github.com/oracle/weblogic-kubernetes-operator/blob/develop/kubernetes/crd/domain-crd.yaml).
{{% /notice %}}

##### `javaLoggingLevel`

Specifies the level of Java logging that should be enabled in the operator. Valid values are:  `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, and `FINEST`.

Defaults to `INFO`.

Example:
```
javaLoggingLevel:  "FINE"
```

#### Creating the operator pod

##### `image`

Specifies the Docker image containing the operator code.

Defaults to `weblogic-kubernetes-operator:3.0.0`.

Example:
```
image:  "weblogic-kubernetes-operator:LATEST"
```

##### `imagePullPolicy`
Specifies the image pull policy for the operator Docker image.

Defaults to `IfNotPresent`.

Example:
```
image:  "Always"
```

##### `imagePullSecrets`
Contains an optional list of Kubernetes Secrets, in the operator's namespace, that are needed to access the registry containing the operator Docker image. You are responsible for creating the secret. If no secrets are required, then omit this property.

Example:
```
imagePullSecrets:
- name: "my-image-pull-secret"
```

#### WebLogic domain management

##### `domainNamespaces`

Specifies a list of WebLogic domain namespaces which the operator manages. The names must be lower case. You are responsible for creating these namespaces.

This property is required.

Example 1: In the configuration below, the operator will monitor the `default` Kubernetes Namespace:
```
domainNamespaces:
- "default"
```

Example 2: In the configuration below, the Helm installation will manage `namespace1` and `namespace2`:
```
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

For more information about managing `domainNamespaces`, see [Managing domain namespaces]({{< relref "/faq/namespace-management.md" >}}).

##### `domainPresenceFailureRetryMaxCount` and `domainPresenceFailureRetrySeconds`

Specify the number of introspector job retries for a domain resource and the interval in seconds between these retries.

Defaults to 5 retries and 10 seconds between each retry.

Example:
```
domainPresenceFailureRetryMaxCount: 10
domainPresenceFailureRetrySeconds: 30
```

#### Elastic Stack integration

##### `elkIntegrationEnabled`

Specifies whether or not Elastic Stack integration is enabled.

Defaults to `false`.

Example:
```
elkIntegrationEnabled:  true
```

##### `logStashImage`

Specifies the Docker image containing Logstash.  This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `logstash:6.6.0`.

Example:
```
logStashImage:  "logstash:6.2"
```

##### `elasticSearchHost`
Specifies the hostname where Elasticsearch is running. This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `elasticsearch.default.svc.cluster.local`.

Example:
```
elasticSearchHost: "elasticsearch2.default.svc.cluster.local"
```

##### `elasticSearchPort`

Specifies the port number where Elasticsearch is running. This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `9200`.

Example:
```
elasticSearchPort: 9201
```

#### REST interface configuration

##### `externalRestEnabled`
Determines whether the operator's REST interface will be exposed outside the Kubernetes cluster.

Defaults to `false`.

If set to `true`, you must provide the `externalRestIdentitySecret` property that contains the name of the Kubernetes Secret which contains the SSL certificate and private key for the operator's external REST interface.

Example:
```
externalRestEnabled: true
```

##### `externalRestHttpsPort`
Specifies the node port that should be allocated for the external operator REST HTTPS interface.

Only used when `externalRestEnabled` is `true`, otherwise ignored.

Defaults to `31001`.

Example:
```
externalRestHttpsPort: 32009
```

##### `externalRestIdentitySecret`

Specifies the user supplied secret that contains the SSL/TLS certificate and private key for the external operator REST HTTPS interface. The value must be the name of the Kubernetes `tls` secret previously created in the namespace where the operator is deployed. This parameter is required if `externalRestEnabled` is `true`, otherwise, it is ignored. In order to create the Kubernetes `tls` secret you can use the following command:

```
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
```
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
```
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
```
externalOperatorKey: QmFnIEF0dHJpYnV0ZXMKICAgIGZyaWVuZGx5TmFtZTogd2VibG9naWMtb3B ...
```

#### Debugging options

##### `remoteDebugNodePortEnabled`

Specifies whether or not the operator will start a Java remote debug server on the provided port and suspend execution until a remote debugger has attached.

Defaults to `false`.

Example:
```
remoteDebugNodePortEnabled:  true
```

##### `internalDebugHttpPort`

Specifies the port number inside the Kubernetes cluster for the operator's Java remote debug server.

This parameter is required if `remoteDebugNodePortEnabled` is `true`. Otherwise, it is ignored.

Defaults to `30999`.

Example:
```
internalDebugHttpPort:  30888
```

##### `externalDebugHttpPort`

Specifies the node port that should be allocated for the Kubernetes cluster for the operator's Java remote debug server.

This parameter is required if `remoteDebugNodePortEnabled` is `true`. Otherwise, it is ignored.

Defaults to `30999`.

Example:
```
externalDebugHttpPort:  30777
```

### Common mistakes and solutions

#### Installing the operator a second time into the same namespace

A new `FAILED` Helm release is created.
```
$ helm install --no-hooks --name op2 --namespace myuser-op-ns --values custom-values.yaml kubernetes/charts/weblogic-operator
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
```
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values custom-values.yaml kubernetes/charts/weblogic-operator
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
```
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values o.yaml kubernetes/charts/weblogic-operator
Error: release op2 failed: Service "external-weblogic-operator-svc" is invalid: spec.ports[0].nodePort: Invalid value: 31023: provided port is already allocated
```

To recover:

- $ `helm delete --purge` the failed release.
- Change the port number and `helm install` the second operator again.

#### Upgrading an operator and assigning it the same external REST port number as another operator

The `helm upgrade` fails and moves the release to the `FAILED` state.
```
$ helm upgrade --no-hooks --values o23.yaml op2 kubernetes/charts/weblogic-operator --wait
Error: UPGRADE FAILED: Service "external-weblogic-operator-svc" is invalid: spec.ports[0].nodePort: Invalid value: 31023: provided port is already allocated
```

* You can fix this by upgrading the Helm release (to fix the port number).
* You can also fix this by rolling back the Helm release.

#### Installing an operator and assigning it a service account that doesn't exist

The `helm install` eventually times out and creates a failed release.
```
$ helm install kubernetes/charts/weblogic-operator --name op2 --namespace myuser-op2-ns --values o24.yaml --wait --no-hooks
Error: release op2 failed: timed out waiting for the condition

kubectl logs -n kube-system tiller-deploy-f9b8476d-mht6v
...
[kube] 2018/12/06 21:16:54 Deployment is not ready: myuser-op2-ns/weblogic-operator
...
```

To recover:

- `helm delete --purge` the failed release.
- Create the service account.
- `helm install` again.

#### Upgrading an operator and assigning it a service account that doesn't exist

The `helm upgrade` succeeds and changes the service account on the existing operator deployment, but the existing deployment's pod doesn't get modified, so it keeps running. If the pod is deleted, the deployment creates another one using the OLD service account. However, there's an error in the deployment's status section saying that the service account doesn't exist.
```
lastTransitionTime: 2018-12-06T23:19:26Z
lastUpdateTime: 2018-12-06T23:19:26Z
message: 'pods "weblogic-operator-88bbb5896-" is forbidden: error looking up
service account myuser-op2-ns/no-such-sa2: serviceaccount "no-such-sa2" not found'
reason: FailedCreate
status: "True"
type: ReplicaFailure
```

To recover:

- Create the service account.
- `helm rollback`
- `helm upgrade` again.

#### Installing an operator and having it manage a domain namespace that doesn't exist

A new `FAILED` Helm release is created.
```
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values o.yaml kubernetes/charts/weblogic-operator
Error: release op2 failed: namespaces "myuser-d2-ns" not found
```

To recover:

- `helm delete --purge` the failed release.
- Create the domain namespace.
- `helm install` again.

#### Upgrading an operator and having it manage a domain namespace that doesn't exist

The `helm upgrade` fails and moves the release to the `FAILED` state.
```
$ helm upgrade myuser-op kubernetes/charts/weblogic-operator --values o.yaml --no-hooks
Error: UPGRADE FAILED: failed to create resource: namespaces "myuser-d2-ns" not found
```
To recover:

- `helm rollback`
- Create the domain namespace.
- `helm upgrade` again.

#### Deleting and recreating a namespace that an operator manages without informing the operator

If you create a new domain in a namespace that is deleted and recreated, the domain does not start up until you notify the operator.
For more information about the problem and solutions, see [Managing domain namespaces]({{<relref "/faq/namespace-management.md">}}).

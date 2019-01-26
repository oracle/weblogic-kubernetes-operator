# Create and manage the operator

An operator is an application-specific controller that extends Kubernetes to create, configure, and manage instances of complex applications. The Oracle WebLogic Server Kubernetes Operator (the "operator") simplifies the management and operation of WebLogic domains and deployments.

## Overview

Helm is used to create and deploy necessary operator resources and to run the operator in a Kubernetes cluster. Helm is a framework that helps you manage Kubernetes applications, and helm charts help you define and install Helm applications into a Kubernetes cluster. The operator's Helm chart is located in the `kubernetes/charts/weblogic-operator` directory.

> If you have an older version of the operator installed on your cluster, then you must remove it before installing this version. This includes the 2.0-rc1 version; it must be completely removed. You should remove the deployment (for example, `kubectl delete deploy weblogic-operator -n your-namespace`) and the custom
  resource definition (for example, `kubectl delete crd domain`).  If you do not remove
  the custom resource definition, then you might see errors like this:

    `Error from server (BadRequest): error when creating "/scratch/output/uidomain/weblogic-domains/uidomain/domain.yaml":
    the API version in the data (weblogic.oracle/v2) does not match the expected API version (weblogic.oracle/v1`

> **NOTE**: You should be able to upgrade from version 2.0-rc2 to 2.0 because there are no backward incompatible changes between these two releases.

## Install Helm and Tiller

Helm has two parts: a client (Helm) and a server (Tiller). Tiller runs inside of your Kubernetes cluster, and manages releases (installations) of your charts.  See https://github.com/kubernetes/helm/blob/master/docs/install.md for detailed instructions on installing Helm and Tiller.

In order to use Helm to install and manage the operator, you need to ensure that the service account that Tiller uses
has the `cluster-admin` role.  The default would be `default` in namespace `kube-system`.  You can give that service
account the necessary permissions with this command:

```
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

**Note** Oracle strongly recommends that you create a new service account to be used exclusively by Tiller and grant
`cluster-admin` to that service account, rather than using the `default` one.

## Operator's Helm chart configuration

The operator Helm chart is pre-configured with default values for the configuration of the operator.

You can override these values by doing one of the following:
- Creating a custom YAML file with only the values to be overridden, and specifying the `--value` option on the Helm command line.
- Overriding individual values directly on the Helm command line, using the `--set` option.

You can find out the configuration values that the Helm chart supports, as well as the default values, using this command:
```
$ helm inspect values kubernetes/charts/weblogic-operator
```

The available configuration values are explained by category in [Operator Helm configuration values](#operator-helm-configuration-values).

Helm commands are explained in more detail in [Useful Helm operations](#useful-helm-operations).

## Optional: Configure the operator's external REST HTTPS interface

The operator can expose an external REST HTTPS interface which can be accessed from outside the Kubernetes cluster. As with the operator's internal REST interface, the external REST interface requires an SSL certificate and private key that the operator will use as the identity of the external REST interface (see below).

To enable the external REST interface, configure these values in a custom configuration file, or on the Helm command line:

* Set `externalRestEnabled` to `true`.
* Set `externalOperatorCert` to the certificate's Base64 encoded PEM.
* Set `externalOperatorKey` to the keys Base64 encoded PEM.
* Optionally, set `externalRestHttpsPort` to the external port number for the operator REST interface (defaults to `31001`).

More detailed information about configuration values can be found in [Operator Helm configuration values](#operator-helm-configuration-values).

### SSL certificate and private key for the REST interface

For testing purposes, the WebLogic Kubernetes Operator project provides a sample script that generates a self-signed certificate and private key for the operator REST interface and outputs them in YAML format. These values can be added to your custom YAML configuration file, for use when the operator's Helm chart is installed.

___This script should not be used in a production environment (because self-signed certificates are not typically considered safe).___

The script takes the subject alternative names that should be added to the certificate, for example, the list of hostnames that clients can use to access the external REST interface. In this example, the output is directly appended to your custom YAML configuration:
```
$ kubernetes/samples/scripts/rest/generate-external-rest-identity.sh "DNS:${HOSTNAME},DNS:localhost,IP:127.0.0.1" >> custom-values.yaml
```

## Optional: Elastic Stack (Elasticsearch, Logstash, and Kibana) integration

The operator Helm chart includes the option of installing the necessary Kubernetes resources for Elastic Stack integration.

You are responsible for configuring Kibana and Elasticsearch, then configuring the operator Helm chart to send events to Elasticsearch. In turn, the operator Helm chart configures Logstash in the operator deployment to send the operator's log contents to that Elasticsearch location.

### Elastic Stack per-operator configuration

As part of the Elastic Stack integration, Logstash configuration occurs for each deployed operator instance.  You can use the following configuration values to configure the integration:

* Set `elkIntegrationEnabled` is `true` to enable the integration.
* Set `logStashImage` to override the default version of Logstash to be used (`logstash:6.2`).
* Set `elasticSearchHost` and `elasticSearchPort` to override the default location where Elasticsearch is running (`elasticsearch2.default.svc.cluster.local:9201`). This will configure Logstash to send the operator's log contents there.

More detailed information about configuration values can be found in [Operator Helm configuration values](#operator-helm-configuration-values).

## Install the Helm chart

Use the `helm install` command to install the operator Helm chart. As part of this, you must specify a "release" name for their operator.

You can override default configuration values in the operator Helm chart by doing one of the following:
- Creating a custom YAML file containing the values to be overridden, and specifying the `--value` option on the Helm command line.
- Overriding individual values directly on the Helm command line, using the `--set` option.

You supply the `–namespace` argument from the `helm install` command line to specify the namespace in which the operator should be installed.  If not specified, it defaults to `default`.  If the namespace does not already exist, Helm will automatically create it (and create a default service account in the new namespace), but will not remove it when the release is deleted.  If the namespace already exists, Helm will re-use it.  These are standard Helm behaviors.

Similarly, you may override the default `serviceAccount` configuration value to specify which service account in the operator's namespace the operator should use.  If not specified, it defaults to `default` (for example, the namespace's default service account).  If you want to use a different service account, then you must create the operator's namespace and the service account before installing the operator Helm chart.

For example:
```
$ helm install kubernetes/charts/weblogic-operator \
  --name weblogic-operator --namespace weblogic-operator-namespace \
  --values custom-values.yaml --wait
```
or:
```
$ helm install kubernetes/charts/weblogic-operator \
  --name weblogic-operator --namespace weblogic-operator-namespace \
  --set "javaLoggingLevel=FINE" --wait
```

This creates a Helm release, named `weblogic-operator` in the `weblogic-operator-namespace` namespace, and configures a deployment and supporting resources for the operator.

If `weblogic-operator-namespace` exists, it will be used.  If it does not exist, then Helm will create it.

You can verify the operator installation by examining the output from the `helm install` command.

## Removing the operator

The `helm delete` command is used to remove an operator release and its associated resources from the Kubernetes cluster.  The release name used with the `helm delete` command is the same release name used with the `helm install` command (see [Install the Helm chart](#install-the-helm-chart)).  For example:
```
$ helm delete --purge weblogic-operator
```

**NOTE**: If the operator's namespace did not exist before the Helm chart was installed, then Helm will create it, however, `helm delete` will not remove it.

After removing the operator deployment, you should also remove the domain custom resource definition:
```
$ kubectl delete customresourcedefinition domains.weblogic.oracle
```
Note that the domain custom resource definition is shared if there are multiple operators in the same cluster.

## Useful Helm operations

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

List the Helm releases that have been installed in this Kubernetes cluster:
```
$ helm list
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

## Operator Helm configuration values

This section describes the details of the operator Helm chart's available configuration values.

### Overall operator information

#### `serviceAccount`

Specifies the name of the service account in the operator's namespace that the operator will use to make requests to the Kubernetes API server. You are responsible for creating the service account.

Defaults to `default`.

Example:
```
serviceAccount: "weblogic-operator"
```

#### `javaLoggingLevel`

Specifies the level of Java logging that should be enabled in the operator. Valid values are:  `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, and `FINEST`.

Defaults to `INFO`.

Example:
```
javaLoggingLevel:  "FINE"
```

### Creating the operator pod

#### `image`

Specifies the Docker image containing the operator code.

Defaults to `weblogic-kubernetes-operator:2.0`.

Example:
```
image:  "weblogic-kubernetes-operator:LATEST"
```

#### `imagePullPolicy`
Specifies the image pull policy for the operator Docker image.

Defaults to `IfNotPresent`.

Example:
```
image:  "Always"
```

#### `imagePullSecrets`
Contains an optional list of Kubernetes secrets, in the operator's namepace, that are needed to access the registry containing the operator Docker image. You are responsible for creating the secret. If no secrets are required, then omit this property.

Example:
```
imagePullSecrets:
- name: "my-image-pull-secret"
```

### WebLogic domain management

#### `domainNamespaces`

Specifies a list of WebLogic domain namespaces which the operator manages. The names must be lower case. You are responsible for creating these namespaces.

This property is required.

Example 1: In the configuration below, the operator will monitor the `default` Kubernetes namespace:
```
domainNamespaces:
- "default"
```

Example 2: In the configuration below, the Helm installation will manage `namespace1` and `namespace2`:
```
domainNamespaces: [ "namespace1", "namespace2" ]
```

**NOTE**: You must include the `default` namespace in the list if you want the operator to monitor both the `default` namespace and some other namespaces.

**NOTE**: These examples show two valid YAML syntax options for arrays.

### Elastic Stack integration

#### `elkIntegrationEnabled`

Specifies whether or not Elastic Stack integration is enabled.

Defaults to `false`.

Example:
```
elkIntegrationEnabled:  true
```

#### `logStashImage`

Specifies the Docker image containing Logstash.  This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `logstash:5`.

Example:
```
logStashImage:  "logstash:6.2"
```

#### `elasticSearchHost`
Specifies the hostname where Elasticsearch is running. This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `elasticsearch.default.svc.cluster.local`.

Example:
```
elasticSearchHost: "elasticsearch2.default.svc.cluster.local"
```

#### `elasticSearchPort`

Specifies the port number where Elasticsearch is running. This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `9200`.

Example:
```
elasticSearchPort: 9201
```

### REST interface configuration

#### `externalRestEnabled`
Determines whether the operator's REST interface will be exposed outside the Kubernetes cluster.

Defaults to `false`.

If set to `true`, you must provide the SSL certificate and private key for the operator's external REST interface by specifying the `externalOperatorCert` and `externalOperatorKey` properties.

Example:
```
externalRestEnabled: true
```

#### externalRestHttpsPort
Specifies the node port that should be allocated for the external operator REST HTTPS interface.

Only used when `externalRestEnabled` is `true`, otherwise ignored.

Defaults to `31001`.

Example:
```
externalRestHttpsPort: 32009
```

#### externalOperatorCert

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

#### externalOperatorKey

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

### Debugging options

#### remoteDebugNodePortEnabled

Specifies whether or not the operator will start a Java remote debug server on the provided port and suspend execution until a remote debugger has attached.

Defaults to `false`.

Example:
```
remoteDebugNodePortEnabled:  true
```

#### internalDebugHttpPort

Specifies the port number inside the Kubernetes cluster for the operator's Java remote debug server.

This parameter is required if `remoteDebugNodePortEnabled` is `true`. Otherwise, it is ignored.

Defaults to `30999`.

Example:
```
internalDebugHttpPort:  30888
```

#### externalDebugHttpPort

Specifies the node port that should be allocated for the Kubernetes cluster for the operator's Java remote debug server.

This parameter is required if `remoteDebugNodePortEnabled` is `true`. Otherwise, it is ignored.

Defaults to `30999`.

Example:
```
externalDebugHttpPort:  30777
```

## Common mistakes and solutions

### Installing the operator a second time into the same namespace

A new `FAILED` Helm release is created.
```
$ helm install --no-hooks --name op2 --namespace myuser-op-ns --values custom-values.yaml kubernetes/charts/weblogic-operator
Error: release op2 failed: secrets "weblogic-operator-secrets" already exists
```

Both the previous and new release own the resources created by the previous operator.

You can't modify it to change the namespace (because `helm upgrade` doesn't let you change the namespace).

You can't fix it by deleting this release because it removes your previous operator's resources.

You can't fix it by rolling back this release because it is not in the `DEPLOYED` state.

You can't fix it by deleting the previous release because it removes the operator's resources too.

All you can do is delete both operator releases and reinstall the original operator.
See https://github.com/helm/helm/issues/2349

### Installing an operator and telling it to manage a domain namespace that another operator is already managing

A new `FAILED` Helm release is created.
```
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values custom-values.yaml kubernetes/charts/weblogic-operator
Error: release op2 failed: rolebindings.rbac.authorization.k8s.io "weblogic-operator-rolebinding-namespace" already exists
```

To recover:

- `helm delete --purge` the failed release.
  - **NOTE**: This deletes the role binding in the domain namespace that was created by the first operator release to give the operator access to the domain namespace.
- `helm upgrade <old op release> kubernetes/charts/weblogic-operator --values <old op custom-values.yaml>`
  - This recreates the role binding.
  - There might be intermittent failures in the operator for the period of time when the role binding was deleted.

### Upgrading an operator and telling it to manage a domain namespace that another operator is already managing

The `helm upgrade` succeeds, and silently adopts the resources the first operator's Helm chart created in the domain namespace (for example, `rolebinding`), and, if you also told it to stop managing another domain namespace, it abandons the role binding it created in that namespace.

For example, if you delete this release, then the first operator will get messed up because the role binding it needs is gone. The big problem is that you don't get a warning, so you don't know that there's a problem to fix.

This can be fixed by just upgrading the Helm release.

This may also be fixed by rolling back the Helm release.

### Installing an operator and telling it to use the same external REST port number as another operator

A new `FAILED` Helm release is created.
```
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values o.yaml kubernetes/charts/weblogic-operator
Error: release op2 failed: Service "external-weblogic-operator-svc" is invalid: spec.ports[0].nodePort: Invalid value: 31023: provided port is already allocated
```

To recover:
- $ `helm delete --purge` the failed release.
- Change the port number and `helm install` the second operator again.

### Upgrading an operator and telling it to use the same external REST port number as another operator

The `helm upgrade` fails and moves the release to the `FAILED` state.
```
$ helm upgrade --no-hooks --values o23.yaml op2 kubernetes/charts/weblogic-operator --wait
Error: UPGRADE FAILED: Service "external-weblogic-operator-svc" is invalid: spec.ports[0].nodePort: Invalid value: 31023: provided port is already allocated
```

You can fix this by upgrading the Helm release (to fix the port number).
You can also fix this by rolling back the Helm release.

### Installing an operator and telling it to use a service account that doesn't exist

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

### Upgrading an operator and telling it to use a service account that doesn't exist

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

### Installing an operator and telling it to manage a domain namespace that doesn't exist

A new `FAILED` Helm release is created.
```
$ helm install --no-hooks --name op2 --namespace myuser-op2-ns --values o.yaml kubernetes/charts/weblogic-operator
Error: release op2 failed: namespaces "myuser-d2-ns" not found
```

To recover:
- `helm delete --purge` the failed release.
- Create the domain namespace.
- `helm install` again.

### Upgrading an operator and telling it to manage a domain namespace that doesn't exist

The `helm upgrade` fails and moves the release to the `FAILED` state.
```
$ helm upgrade myuser-op kubernetes/charts/weblogic-operator --values o.yaml --no-hooks
Error: UPGRADE FAILED: failed to create resource: namespaces "myuser-d2-ns" not found
```
To recover:
- `helm rollback`
- Create the domain namespace.
- `helm upgrade` again.

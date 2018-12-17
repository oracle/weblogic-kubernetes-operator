# Using operator Helm charts

## Overview

The WebLogic Kubernetes Operator uses Helm to create and deploy any necessary resources and then run the operator in a Kubernetes cluster. Helm helps you manage Kubernetes applications. Helm charts help you define and install applications into the Kubernetes cluster. The operator's Helm chart is located in the "kubernetes/charts/weblogic-operator" directory.

## Install Helm and Tiller

Helm has two parts: a client (helm) and a server (tiller). Tiller runs inside of your Kubernetes cluster, and manages releases (installations) of your charts.  See https://github.com/kubernetes/helm/blob/master/docs/install.md for detailed instructions on installing helm and tiller.

## Operator's Helm chart configuration

The operator Helm chart is pre-configured with default values for the configuration of the operator.

The user can override these values by doing one of the following:
- create a custom YAML file with the only values to be overridden, and specify the `--value` option on the Helm command line
- override individual values directly on the helm command line, using the `--set` option 

The user can find out the configuration values that the Helm chart supports, as well as the default values, using this command:
```
helm inspect values kubernetes/charts/weblogic-operator
```

The available configuration values are explained by category in [Operator Helm configuration values](#operator-helm-configuration-values) below.

Helm commands are explained in more detail in [Useful Helm operations](#useful-helm-operations) below.

## Optional: Configure the operator's external REST HTTPS interface

The operator can expose an external REST HTTPS interface which can be accessed from outside the Kubernetes cluster. As with the Operator's internal REST interface, the external REST interface requires an SSL certificate and private key that the operator will use as the identity of the external REST interface (see below).

To enable the external REST interface, configure these values in a custom configuration file, or on the Helm command line:

* set `externalRestEnabled` to true
* set `externalOperatorCert` to the certificate's Base64 encoded PEM
* set `externalOperatorKey` to the keys Base64 encoded PEM
* optionally, set `externalRestHttpsPort` to the external port number for the operator REST interface (defaults to `31001`)

More detailed information about configuration values can be found in [Operator Helm configuration values](#operator-helm-configuration-values)

### SSL certificate and private key for the REST interface

For testing purposes, the WebLogic Kubernetes Operator project provides a sample script that generates a self-signed certificate and private key for the operator REST interface and outputs them out in YAML format. These values can be added to the user's custom YAML configuration file, for use when the operator's Helm chart is installed.
   
___This script should not be used in a production environment (since self-signed certificates are normally not considered safe).___
   
The script takes the subject alternative names that should be added to the certificate - i.e. the list of hostnames that clients can use to access the external REST interface. In this example, the output is directly appended to the user's custom YAML configuration:
```
kubernetes/generate-external-weblogic-operator-certificate.sh "DNS:${HOSTNAME},DNS:localhost,IP:127.0.0.1" >> custom-values.yaml
```

## Optional: ELK (Elasticsearch, Logstash and Kibana) integration

The operator Helm chart includes the option of installing the necessary Kubernetes resources for ELK integration.

The user is responsible for configuring Kibana and Elasticsearch, then configuring the operator Helm chart to send events to Elasticsearch. In turn, the operator Helm chart configures Logstash in the operator deployment to send the operator's log contents to that Elasticsearch location.

### ELK per-operator configuration

As part of the ELK integration, Logstash configuration occurs for each deployed operator instance.  The following configuration values can be used to configure the integration:

* set `elkIntegrationEnabled` is `true` to enable the integration
* set `logStashImage` to override the default version of logstash to be used (`logstash:6.2`)
* set `elasticSearchHost` and `elasticSearchPort` to override the default location where Elasticsearch is running (`elasticsearch2.default.svc.cluster.local:9201`). This will configure Logstash to send the operator's log contents there.

More detailed information about configuration values can be found in [Operator Helm configuration values](#operator-helm-configuration-values)

## Install the Helm chart

The `helm install` command is used to install the operator Helm chart. As part of this, the user specifies a "release" name for their operator.

The user can override default configuration values in the operator Helm chart by doing one of the following:
- create a custom YAML file containing the values to be overridden, and specify the `--value` option on the Helm command line
- override individual values directly on the helm command line, using the `--set` option 

The user supplies the `–namespace` argument from the `helm install` command line to specify the namespace in which the operator should be installed.  If not specified, it defaults to `default`.  If the namespace does not already exist, Helm will automatically create it (and create a default service account in the new namespace), but will not remove it when the release is deleted.  If the namespace already exists, Helm will re-use it.  These are standard Helm behaviors.

Similarly, the user may override the default `serviceAccount` configuration value to specify which service account in the operator's namespace the operator should use.  If not specified, it defaults to `default` (i.e. the namespace's default service account).  If the user wants to use a different service account, then the user must create the operator's namespace and the service account before installing the operator Helm chart.

For example:
```
helm install kubernetes/charts/weblogic-operator \
  --name weblogic-operator --namespace weblogic-operator-namespace \
  --values custom-values.yaml --wait
```
or:
```
helm install kubernetes/charts/weblogic-operator \
  --name weblogic-operator --namespace weblogic-operator-namespace \
  --set "javaLoggingLevel:FINE" --wait
```

This creates a Helm release, named `weblogic-operator` in the `weblogic-operator-namespace` namespace, and configures a deployment and supporting resources for the operator.

If `weblogic-operator-namespace` exists, it will be used.  If it does not exist, then Helm will create it.

You can verify the operator installation by examining the output from the `helm install` command.

## Removing the Operator

The `helm delete` command is used to remove an operator release and its associated resources from the Kubernetes cluster.  The release name used with the `helm delete` command is the same release name used with the `helm install` command (see [Install the Helm chart](#install-the-helm-chart)).  For example:
```
helm delete --purge weblogic-operator
```

Note: if the operator's namespace did not exist before the Helm chart was installed, then Helm will create it, however, `helm delete` will not remove it.

## Useful Helm operations

Show the available operator configuration parameters and their default values:
```
helm inspect values kubernetes/charts/weblogic-operator
```

Show the custom values you configured for the operator Helm release:
```
helm get values weblogic-operator
```

Show all of the values your operator Helm release is using:
```
helm get values --all weblogic-operator
```

List the Helm releases that have been installed in this Kubernetes cluster:
```
helm list
```

Get the status of the operator Helm release.
```
helm status weblogic-operator
```

Show the history of the operator Helm release:
```
helm history weblogic-operator
```

Roll back to a previous version of this operator Helm release, in this case the first version.
```
helm rollback weblogic-operator 1
```

Change one or more values in the operator Helm release. In this example, the `--reuse-values` flag indicates that previous overrides of other values should be retained:
```
helm upgrade \
  --reuse-values \
  --set "domainNamespaces={sample-domains-ns1}" \
  --set "javaLoggingLevel:FINE" \
  --wait \
  weblogic-operator \
  kubernetes/charts/weblogic-operator
```

## Operator Helm configuration values

This section describes the details of the operator Helm chart's available configuration values.

### Overall Operator information

#### serviceAccount

Specifies the name of the service account in the operator's namespace that the operator will use to make requests to the Kubernetes API server. The user is responsible for creating the service account.

Defaults to `default`.

Example:
```
serviceAccount: "weblogic-operator"
```

#### javaLoggingLevel 

Specifies the level of Java logging that should be enabled in the operator. Valid values are:  "SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER", and "FINEST".

Defaults to `INFO`

Example:
```
javaLoggingLevel:  "FINE"
```

### Creating the operator pod

#### image 

Specifies the Docker image containing the operator code.

Defaults to `weblogic-kubernetes-operator:2.0`.

Example:
```
image:  "weblogic-kubernetes-operator:LATEST"
```

#### imagePullPolicy
Specifies the image pull policy for the operator Docker image.

Defaults to `IfNotPresent`

Example:
```
image:  "Always"
```

#### imagePullSecrets
Contains an optional list of Kubernetes secrets, in the operator's namepace, that are needed to access the registry containing the operator Docker image. The user is responsible for creating the secret. If no secrets are required, then omit this property.

Example:
```
imagePullSecrets:
- name: "my-image-pull-secret"
```

### WebLogic Domain management

#### domainNamespaces

Specifies a list of WebLogic Domain namespaces which the operator manages. The names must be lower case. The user is responsible for creating these namespace.

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

NOTE: one must include the `default` namespace in the list if you want the operator to monitor both the `default` namespace and some other namespaces.

NOTE: these examples show two valid YAML syntax options for arrays.

### ELK integration

#### elkIntegrationEnabled

Specifies whether or not ELK integration is enabled.

Defaults to false

Example:
```
elkIntegrationEnabled:  true
```

#### logStashImage 

Specifies the Docker image containing Logstash.  This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `logstash:5`

Example:
```
logStashImage:  "logstash:6.2"
```

#### elasticSearchHost 
Specifies the hostname where Elasticsearch is running. This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `elasticsearch.default.svc.cluster.local`

Example:
```
elasticSearchHost: "elasticsearch2.default.svc.cluster.local"
```

#### elasticSearchPort 

Specifies the port number where Elasticsearch is running. This parameter is ignored if `elkIntegrationEnabled` is false.

Defaults to `9200`.

Example:
```
elasticSearchPort: 9201
```

### REST interface configuration

#### externalRestEnabled
Determines whether the operator's REST interface will be exposed outside the Kubernetes cluster.

Defaults to `false`.

If set to true, the user must provide the SSL certificate and private key for the operator's external REST interface by specifying the `externalOperatorCert` and `externalOperatorKey` properties.

Example:
```
externalRestEnabled: true
```

#### externalRestHttpsPort
Specifies node port that should be allocated for the external operator REST HTTPS interface.

Only used when `externalRestEnabled` is `true`, otherwise ignored.

Defaults to `31001`.

Example:
```
externalRestHttpsPort: 32009
```

#### externalOperatorCert

Specifies the user supplied certificate to use for the external operator REST HTTPS interface. The value must be a string containing a Base64 encoded PEM certificate. This parameter is required if `externalRestEnabled` is true, otherwise, it is ignored.

There is no default value.

The helm installation will produce an error, similar to the following, if `externalOperatorCert` is not specified (left blank) and `externalRestEnabled` is true:
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

The helm installation will produce an error, similar to the following, if `externalOperatorKey` is not specified (left blank) and `externalRestEnabled` is true:
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

Defaults to `false`

Example:
```
remoteDebugNodePortEnabled:  true
```

#### internalDebugHttpPort

Specifies the port number inside the Kubernetes cluster for the operator's Java remote debug server.

This parameter is required if `remoteDebugNodePortEnabled` is `true`. Otherwise, it is ignored.

Defaults to `30999`

Example:
```
internalDebugHttpPort:  30888
```

#### externalDebugHttpPort

Specifies the node port that should be allocated for the Kubernetes cluster for the operator's Java remote debug server.

This parameter is required if `remoteDebugNodePortEnabled` is `true`. Otherwise, it is ignored.

Defaults to `30999`

Example:
```
externalDebugHttpPort:  30777
```

## Common mistakes and solutions

### Install the operator a second time into the same namespace

A new 'FAILED' helm release is created
```
helm install --no-hooks --name op2 --namespace myuser-op-ns --values custom-values.yaml kubernetes/charts/weblogic-operator
Error: release op2 failed: secrets "weblogic-operator-secrets" already exists
```

Both the previous and new release own the resources created by the previous operator

You can't modify it to change the namespace (since helm upgrade doesn't let you change the namespace)

You can't fix it by deleting this release since it removes your previous operator's resources

You can't fix it by rolling back this release since it is not in the 'DEPLOYED' state

You can't fix it by deleting the previous release since it removes the operator's resources too

All you can do is delete both operator releases and reinstall the original operator. 
See https://github.com/helm/helm/issues/2349

### Install an operator and tell it to manage a domain namespace that another operator is already managing

A new 'FAILED' helm release is created.
```
helm install --no-hooks --name op2 --namespace myuser-op2-ns --values custom-values.yaml kubernetes/charts/weblogic-operator
Error: release op2 failed: rolebindings.rbac.authorization.k8s.io "weblogic-operator-rolebinding-namespace" already exists
```

To recover:
- `helm delete --purge` the failed release
  - note: this deletes the role binding in the domain namespace that was created by the first operator release to give the operator access to the domain namespace
- `helm upgrade <old op release> kubernetes/charts/weblogic-operator --values <old op custom-values.yaml>`
  - this recreates the role binding
  - there might be intermittent failures in the operator for the period of time when the role binding was deleted

### Upgrade an operator and tell it to manage a domain namespace that another operator is already managing

The helm upgrade succeeds, and silently adopts the resources the first operator's Helm chart created in the domain namespace (i.e. rolebinding), and, if you also told it to stop managing another domain namespace, it abandons the role binding it created in that namespace

i.e. if you delete this release, then the first operator will get messed up because the role binding it needs is gone

The big problem is that the user doesn't get a warning, so doesn't know that there's a problem to fix

This can be fixed by just upgrading the helm release

This may also be fixed by rolling back the helm release.

### Install an operator and tell it to use the same external REST port number as another operator

A new 'FAILED' helm release is created.
```
helm install --no-hooks --name op2 --namespace myuser-op2-ns --values o.yaml kubernetes/charts/weblogic-operator
Error: release op2 failed: Service "external-weblogic-operator-svc" is invalid: spec.ports[0].nodePort: Invalid value: 31023: provided port is already allocated
```

To recover:
- `helm delete --purge` the failed release
- change the port number and `helm install` the second operator again

### Upgrade an operator and tell it to use the same external REST port number as another operator

The helm upgrade fails and moves the release to the FAILED state.
```
helm upgrade --no-hooks --values o23.yaml op2 kubernetes/charts/weblogic-operator --wait
Error: UPGRADE FAILED: Service "external-weblogic-operator-svc" is invalid: spec.ports[0].nodePort: Invalid value: 31023: provided port is already allocated
```

This can be fixed by upgrading the helm release (to fix the port number).
This can also be fixed by rolling back the helm release.

### Install an operator and tell it to use a service account that doesn't exist

The helm install eventually times out an creates a failed release.
```
helm install kubernetes/charts/weblogic-operator --name op2 --namespace myuser-op2-ns --values o24.yaml --wait --no-hooks
Error: release op2 failed: timed out waiting for the condition

kubectl log -n kube-system tiller-deploy-f9b8476d-mht6v
...
[kube] 2018/12/06 21:16:54 Deployment is not ready: myuser-op2-ns/weblogic-operator
...
```

To recover:
- `helm delete --purge` the failed release
- create the service account
- `helm install` again

### Upgrade an operator and tell it to use a service account that doesn't exist

The helm upgrade succeeds and changes the service account on the existing operator deployment, but the existing deployment's pod doesn't get modified, so it keeps running. If the pod is deleted, the deployment creates another one using the OLD service account. However, there's an error in the deployment's status section saying that the service account doesn't exist
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
- create the service account
- `helm rollback`
- `helm upgrade` again

### Install an operator and tell it to manage a domain namespace that doesn't exist

A new 'FAILED' helm release is created
```
helm install --no-hooks --name op2 --namespace myuser-op2-ns --values o.yaml kubernetes/charts/weblogic-operator
Error: release op2 failed: namespaces "myuser-d2-ns" not found
```

To recover
- `helm delete --purge` the failed release
- create the domain namespace
- `helm install` again

### Upgrade an operator and tell it to manage a domain namespace that doesn't exist

The helm upgrade fails and moves the release to the FAILED state.
```
helm upgrade myuser-op kubernetes/charts/weblogic-operator --values o.yaml --no-hooks
Error: UPGRADE FAILED: failed to create resource: namespaces "myuser-d2-ns" not found
```
To recover:
- `helm rollback`
- create the domain namespace
- `helm upgrade` again

---
title: "Configuration reference"
date: 2019-02-23T17:08:43-05:00
weight: 30
description: "An operator runtime is installed and configured using Helm. Here are useful Helm operations and operator configuration values."
---

### Contents

- [Introduction](#introduction)
- [Useful Helm operations](#useful-helm-operations)
- [Operator Helm configuration values](#operator-helm-configuration-values)
  - [Overall operator information](#overall-operator-information)
    - [`serviceAccount`](#serviceaccount)
    - [`kubernetesPlatform`](#kubernetesplatform)
    - [`enableClusterRoleBinding`](#enableclusterrolebinding)
  - [Creating the operator pod](#creating-the-operator-pod)
    - [`image`](#image)
    - [`imagePullPolicy`](#imagepullpolicy)
    - [`imagePullSecrets`](#imagepullsecrets)
    - [`annotations`](#annotations)
    - [`labels`](#labels)
    - [`nodeSelector`](#nodeselector)
    - [`affinity`](#affinity)
  - [WebLogic domain management](#weblogic-domain-management)
    - [`domainNamespaceSelectionStrategy`](#domainnamespaceselectionstrategy)
    - [`domainNamespaces`](#domainnamespaces)
    - [`domainNamespaceLabelSelector`](#domainnamespacelabelselector)
    - [`domainNamespaceRegExp`](#domainnamespaceregexp)
    - [`dedicated` ***(Deprecated)***](#dedicated-deprecated)
    - [`domainPresenceFailureRetryMaxCount` and `domainPresenceFailureRetrySeconds`](#domainpresencefailureretrymaxcount-and-domainpresencefailureretryseconds)
    - [`introspectorJobNameSuffix` and `externalServiceNameSuffix`](#introspectorjobnamesuffix-and-externalservicenamesuffix)
    - [`clusterSizePaddingValidationEnabled`](#clustersizepaddingvalidationenabled)
    - [`istioLocalhostBindingsEnabled`](#istiolocalhostbindingsenabled)
  - [Elastic Stack integration](#elastic-stack-integration)
    - [`elkIntegrationEnabled`](#elkintegrationenabled)
    - [`logStashImage`](#logstashimage)
    - [`elasticSearchHost`](#elasticsearchhost)
    - [`elasticSearchPort`](#elasticsearchport)
    - [`createLogStashConfigMap`](#createlogstashconfigmap)
  - [REST interface configuration](#rest-interface-configuration)
    - [`externalRestEnabled`](#externalrestenabled)
    - [`externalRestHttpsPort`](#externalresthttpsport)
    - [`externalRestIdentitySecret`](#externalrestidentitysecret)
    - [`externalOperatorCert` ***(Deprecated)***](#externaloperatorcert-deprecated)
    - [`externalOperatorKey` ***(Deprecated)***](#externaloperatorkey-deprecated)
    - [`tokenReviewAuthentication`](#tokenreviewauthentication)
  - [Debugging options](#debugging-options)
    - [`javaLoggingLevel`](#javalogginglevel)
    - [`remoteDebugNodePortEnabled`](#remotedebugnodeportenabled)
    - [`internalDebugHttpPort`](#internaldebughttpport)
    - [`externalDebugHttpPort`](#externaldebughttpport)

### Introduction

The operator requires Helm for its installation and tuning,
and this document is a reference guide for useful Helm commands and operator configuration values.

This document assumes that the operator has been installed using an operator Helm chart.
An operator Helm chart can be obtained from the GitHub chart repository or can be found in the operator source.
For information about operator Helm chart access, installation, and upgrade,
see [Prepare for installation]({{< relref "/userguide/managing-operators/preparation.md" >}})
and [Installation and upgrade]({{< relref "/userguide/managing-operators/installation.md" >}}).

### Useful Helm operations

- You can find out the configuration values that the operator Helm chart supports,
  as well as the default values, using the `helm inspect` command.
  - Here's an example of using `helm inspect`
    with the local file based operator Helm chart
    assuming the operator source code is in
    `/tmp/weblogic-kubernetes-operator`.
    ```text
    $ cd /tmp/weblogic-kubernetes-operator
    ```
    ```text
    $ helm inspect values kubernetes/charts/weblogic-operator
    ```
  - Here's an example of using `helm inspect`
    with a GitHub chart repository based operator Helm chart
    where the repository is named `weblogic-operator`:
    ```text
    $ helm inspect values weblogic-operator/weblogic-operator
    ```
  Alternatively, you can view most of the configuration values
  and their defaults in the operator source in the
  `./kubernetes/charts/weblogic-operator/values.yaml` file.

- An installed operator is maintained by a Helm release. List the Helm releases for a specified namespace or all namespaces:
  ```shell
  $ helm list --namespace <namespace>
  ```

  ```shell
  $ helm list --all-namespaces
  ```

- Get the status of the operator Helm release named `weblogic-operator`:
  ```shell
  $ helm status weblogic-operator --namespace <namespace>
  ```

- Show the history of the operator Helm release named `weblogic-operator`:
  ```shell
  $ helm history weblogic-operator --namespace <namespace>
  ```

- Roll back to a previous version of the operator Helm release named `weblogic-operator`, in this case, the first version:
  ```shell
  $ helm rollback weblogic-operator 1 --namespace <namespace>
  ```

- Show the custom values you configured for a operator Helm release named `weblogic-operator`:
  ```shell
  $ helm get values weblogic-operator
  ```

- Show all of the values your operator Helm release named `weblogic-operator` is using:
  ```shell
  $ helm get values --all weblogic-operator
  ```

- Change one or more values in the operator Helm release named `weblogic-operator` using `helm upgrade`, for example:
  ```shell
  $ helm upgrade \
    --reuse-values \
    --set "domainNamespaces={sample-domains-ns1}" \
    --set "javaLoggingLevel=FINE" \
    --wait \
    weblogic-operator \
    kubernetes/charts/weblogic-operator
  ```
  **Notes**:
  - In this example, the `--reuse-values` flag indicates that previous overrides of other values should be retained,
    `kubernetes/charts/weblogic-operator` refers to the operator Helm chart supplied in the operator source,
    and it is assumed you are running the command from the top directory within the operator source:
  - Before changing the `javaLoggingLevel` setting,
    consult the [Operator logging level]({{< relref "/userguide/managing-operators/troubleshooting#operator-logging-level" >}}) advice.


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

##### `kubernetesPlatform`
Specify the Kubernetes platform on which the operator is running. This setting has no default, the only valid value is OpenShift, and the setting should be left unset for other platforms. When set to `OpenShift`, the operator:
- Sets the domain home file permissions in each WebLogic Server pod to work correctly in OpenShift for [Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}}), and [Domain home in Image]({{< relref "/samples/domains/domain-home-in-image/_index.md" >}}) domains. Specifically, it sets file group permissions so that they match file user permissions.
- Sets the `weblogic.SecureMode.WarnOnInsecureFileSystem` Java system property to `false` on the command line of each WebLogic Server. This flag suppresses insecure file system warnings reported in the WebLogic Server console when the WebLogic Server is in production mode. These warnings result from setting the file permissions necessary to work with restricted security context constraints on OpenShift.

For more information about the security requirements for running WebLogic in OpenShift, see the [OpenShift]({{<relref "/security/openshift.md">}}) documentation.

Example:
```yaml
kubernetesPlatform: OpenShift
```

##### `enableClusterRoleBinding`
Specifies whether the roles necessary for the operator to manage domains
will be granted using a ClusterRoleBinding rather than using RoleBindings in each managed namespace.

Defaults to `false`.

This option greatly simplifies managing namespaces when the selection is done using label selectors or
regular expressions as the operator will already have privilege in any namespace.

Customers who deploy the operator in Kubernetes clusters that run unrelated workloads will likely
not want to use this option.

**Note:** If your operator Helm `enableClusterRoleBinding` configuration value is `false`, then
then a running operator will _not_ have privilege to manage a newly added namespace
that matches its namespace selection criteria until you upgrade
the operator's Helm release.
See [Ensuring the operator has permission to manage a namespace]({{< relref "/userguide/managing-operators/namespace-management#ensuring-the-operator-has-permission-to-manage-a-namespace" >}}).

#### Creating the operator pod

##### `image`
Specifies the container image containing the operator code.

Defaults to `ghcr.io/oracle/weblogic-kubernetes-operator:{{< latestVersion >}}`
or similar (based on the default in your Helm chart, see `helm inspect`
in [Useful Helm operations](#useful-helm-operations)).

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

Examples:
- Using YAML:
  ```yaml
  imagePullSecrets:
  - name: "my-image-pull-secret1"
  - name: "my-image-pull-secret2"
  ```
- Using the Helm command line:
  ```
  --set "imagePullSecrets[0].name=my-image-pull-secret1" \
  --set "imagePullSecrets[1].name=my-image-pull-secret2"
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

#### WebLogic domain management

The settings in this section determine the namespaces that an operator
monitors for domain resources. For usage,
also see [Namespace management]({{< relref "/userguide/managing-operators/namespace-management.md" >}}).

##### `domainNamespaceSelectionStrategy`

Specifies how the operator will select the set of namespaces that it will manage.
Legal values are: `List`, `LabelSelector`, `RegExp`, and `Dedicated`:

- If set to `List`, then the operator will manage the set of namespaces listed by the `domainNamespaces` value.
- If set to `LabelSelector`, then the operator will manage the set of namespaces discovered by a list
  of namespaces using the value specified by `domainNamespaceLabelSelector` as a label selector.
- If set to `RegExp`, then the operator will manage the set of namespaces discovered by a list
  of namespaces using the value specified by `domainNamespaceRegExp` as a regular expression matched
  against the namespace names.
- Finally, if set to `Dedicated`, then operator will manage WebLogic domains only in the same namespace
  which the operator itself is deployed, which is the namespace of the Helm release.

**Notes:**
- Defaults to `List`.
- If the deprecated `dedicated` setting is set to `true`,
  then this value is ignored and
  the operator will manage only domains in its own namespace.

For more information,
see [Choose a domain namespace section strategy]({{<relref "/userguide/managing-operators/namespace-management#choose-a-domain-namespace-selection-strategy">}})
in the operator _Namespace management_ guide.

##### `domainNamespaces`

Specifies a list of namespaces that the operator manages. The names must be lowercase. You are responsible for creating these namespaces.
The operator will only manage domains found in these namespaces.
This value is required if `domainNamespaceSelectionStrategy` is `List` and ignored otherwise.

Examples:
- Example 1: In the configuration below, the operator will manage the `default` and `ns1` Kubernetes Namespaces:
  ```yaml
  domainNamespaces:
  - "default"
  - "ns1"
  ```
- Example 2: In the configuration below, the operator will manage `namespace1` and `namespace2`:
  ```yaml
  domainNamespaces: [ "namespace1", "namespace2" ]
  ```
  Note that this is a valid but different YAML syntax for specifying arrays
  in comparison to the previous example.
- Example 3: To specify on the Helm command line:
  ```
  --set "domainNamespaces={namespace1,namespace2}"
  ```

**Notes:**
- Defaults to the `default` namespace.
- You must include the `default` namespace in the list if you want the operator to monitor both the `default` namespace and some other namespaces.
- If you change `domainNamespaces` using a `helm upgrade`,
  then the new list completely replaces the original list
  (they are not merged).
- If the deprecated `dedicated` setting is set to `true`,
  then this value is ignored and
  the operator will manage only domains in its own namespace.

##### `domainNamespaceLabelSelector`
Specifies a [label selector](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/#label-selectors) that will be used when searching for namespaces that the operator will manage.
The operator will only manage domains found in namespaces matching this selector.
This value is required if `domainNamespaceSelectionStrategy` is `LabelSelector` and ignored otherwise.

Examples:
- Example 1: In the configuration below, the operator will manage namespaces that have the label "weblogic-operator"
  regardless of the value of that label:
  ```yaml
  domainNamespaceLabelSelector: weblogic-operator
  ```
- Example 2: In the configuration below, the operator will manage all namespaces that have the label "environment",
  but where the value of that label is not "production" or "systemtest":
  ```yaml
  domainNamespaceLabelSelector: environment notin (production,systemtest)
  ```

**Notes**:
- To specify the previous sample on the Helm command line, escape the equal sign, spaces and commas as follows:
  ```
  --set "domainNamespaceLabelSelector\=environment\\ notin\\ (production\\,systemtest)"
  ```
- If your operator Helm `enableClusterRoleBinding` configuration value is `false`, then
  then a running operator will _not_ have privilege to manage a newly added namespace
  that matches its label selector until you upgrade
  the operator's Helm release.
  See [Ensuring the operator has permission to manage a namespace]({{< relref "/userguide/managing-operators/namespace-management#ensuring-the-operator-has-permission-to-manage-a-namespace" >}}).
- If the deprecated `dedicated` setting is set to `true`,
  then this value is ignored and
  the operator will manage only domains in its own namespace.

##### `domainNamespaceRegExp`
Specifies a regular expression that will be used when searching for namespaces that the operator will manage.
The operator will only manage domains found in namespaces matching this regular expression.
This value is required if `domainNamespaceSelectionStrategy` is `RegExp` and ignored otherwise.

**Notes:**

- The regular expression functionality included with Helm is restricted to linear time constructs and,
  in particular, does not support lookarounds. The operator, written in Java, supports these
  complicated expressions. If you need to use a complex regular expression, then either set
  `enableClusterRoleBinding` to `true` or create the necessary RoleBindings outside of Helm.
- If your operator Helm `enableClusterRoleBinding` configuration value is `false`, then
  then a running operator will _not_ have privilege to manage a newly added namespace
  that matches its regular expression until you upgrade
  the operator's Helm release.
  See [Ensuring the operator has permission to manage a namespace]({{< relref "/userguide/managing-operators/namespace-management#ensuring-the-operator-has-permission-to-manage-a-namespace" >}}).
- If the deprecated `dedicated` setting is set to `true`,
  then this value is ignored and
  the operator will manage only domains in its own namespace.

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
Specify the number of introspector job retries for a domain and the interval in seconds between these retries.

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

##### `istioLocalhostBindingsEnabled`

Default for the domain resource `domain.spec.configuration.istio.localhostBindingsEnabled` setting.

For more information, see [_Configuring the domain resource_ in the Istio user guide]({{< relref "/userguide/istio/istio.md#configuring-the-domain-resource" >}}).

#### Elastic Stack integration

The following settings are related to integrating the Elastic Stack with the operator pod.

For example usage, see the [Operator Elastic Stack (Elasticsearch, Logstash, and Kibana) integration sample]({{<relref "/samples/elastic-stack/operator/_index.md#elastic-stack-per-operator-configuration">}}).

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
logStashImage:  "docker.elastic.co/logstash/logstash:6.2.0"
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

##### `createLogStashConfigMap`
Specifies whether a ConfigMap named `weblogic-operator-logstash-cm` should be created during `helm install`. 
The ConfigMap contains the Logstash pipeline configuration for the Logstash container running in the operator pod.
If set to `true`, a ConfigMap will be created during `helm install` using the `logstash.conf` file in the `kubernetes/samples/charts/weblogic-operator` directory.
Set `createLogStashConfigMap` to `false` if the ConfigMap already exists in the operator's namespace with the Logstash configuration provided by the customer.
This parameter is ignored if `elkIntegrationEnabled` is false. 

Defaults to `true`.

Example:
```yaml
createLogStashConfigMap:  false
```

#### REST interface configuration

The REST interface configuration options are advanced settings for configuring the operator's external REST interface.

For usage information, see the operator [REST Services]({{<relref "/userguide/managing-operators/the-rest-api.md">}}).

##### `externalRestEnabled`
Determines whether the operator's REST interface will be exposed outside the Kubernetes cluster using a node port.

See also `externalRestHttpsPort` for customizing the port number.

Defaults to `false`.

If set to `true`, you must provide the `externalRestIdentitySecret` property that contains the name of the Kubernetes Secret which contains the SSL certificate and private key for the operator's external REST interface.

Example:
```yaml
externalRestEnabled: true
```

**Note**: A node port is a security risk because the port may be publicly exposed to the internet in some environments. If you need external access to the REST port, then consider alternatives such as providing access through your load balancer, or using Kubernetes port forwarding.

##### `externalRestHttpsPort`
Specifies the node port that should be allocated for the external operator REST HTTPS interface.

Only used when `externalRestEnabled` is `true`, otherwise ignored.

Defaults to `31001`.

Example:
```yaml
externalRestHttpsPort: 32009
```

**Note**: A node port is a security risk because the port may be publicly exposed to the internet in some environments. If you need external access to the REST port, then consider alternatives such as providing access through your load balancer, or using Kubernetes port forwarding.

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
   * Update the domain resource using the operator's privileges.

 If set to `false`, the operator's REST API will use the caller's bearer token for any update
 to the domain resource so that it is done using the caller's privileges.

 Defaults to `false`.

 Example:
 ```yaml
 tokenReviewAuthentication: true
 ```
#### Debugging options

##### `javaLoggingLevel`

Specifies the level of Java logging that should be enabled in the operator. Valid values are:  `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, and `FINEST`.

Defaults to `INFO`.

Example:
```yaml
javaLoggingLevel:  "FINE"
```

**Note**: Please consult [Operator logging level]({{< relref "/userguide/managing-operators/troubleshooting#operator-logging-level" >}}) before changing this setting.

##### `remoteDebugNodePortEnabled`
Specifies whether or not the operator will start a Java remote debug server on the provided port and suspend execution until a remote debugger has attached.

Defaults to `false`.

Example:
```yaml
remoteDebugNodePortEnabled:  true
```

##### `internalDebugHttpPort`

Specifies the port number inside the Kubernetes cluster for the operator's Java remote debug server.

This parameter is required if `remoteDebugNodePortEnabled` is `true` (default `false`). Otherwise, it is ignored.

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

**Note**: A node port is a security risk because the port may be publicly exposed to the internet in some environments. If you need external access to the debug port, then consider using Kubernetes port forwarding instead.

---
title: "Manage operators"
date: 2019-02-23T16:43:38-05:00
weight: 3
description: "Helm is used to create and deploy necessary operator resources and to run the operator in a Kubernetes cluster. Use the operator's Helm chart to install and manage the operator."
---


### Overview

Helm is a framework that helps you manage Kubernetes applications, and Helm charts help you define and install Helm applications into a Kubernetes cluster. The operator's Helm chart is located in the `kubernetes/charts/weblogic-operator` directory.

**Important note for users of operator releases before 2.0**
{{% expand "Click here to expand" %}}

{{% notice warning %}}
If you have an older version of the operator installed on your cluster, for example, a 1.x version or one of the 2.0 release
candidates, then you must remove it before installing this version. This includes the 2.0-rc1 version; it must be completely removed. You should remove the deployment (for example, `kubectl delete deploy weblogic-operator -n your-namespace`) and the custom
resource definition (for example, `kubectl delete crd domain`).  If you do not remove
the custom resource definition, then you might see errors like this:
```
Error from server (BadRequest): error when creating "/scratch/output/uidomain/weblogic-domains/uidomain/domain.yaml":
the API version in the data (weblogic.oracle/v2) does not match the expected API version (weblogic.oracle/v1
```
{{% /notice %}}      

{{% /expand %}}

#### Install Helm and Tiller

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

{{% notice note %}}
Oracle strongly recommends that you create a new service account to be used exclusively by Tiller and grant
`cluster-admin` to that service account, rather than using the `default` one.
{{% /notice %}}

### Operator's Helm Chart Configuration

The operator Helm chart is pre-configured with default values for the configuration of the operator.

You can override these values by doing one of the following:

- Creating a custom YAML file with only the values to be overridden, and specifying the `--value` option on the Helm command line.
- Overriding individual values directly on the Helm command line, using the `--set` option.

You can find out the configuration values that the Helm chart supports, as well as the default values, using this command:
```
$ helm inspect values kubernetes/charts/weblogic-operator
```

The available configuration values are explained by category in
[Operator Helm configuration values]({{<relref "/userguide/managing-operators/using-the-operator/using-helm.md#operator-helm-configuration-values">}}).

Helm commands are explained in more detail in
[Useful Helm operations]({{<relref "/userguide/managing-operators/using-the-operator/using-helm.md#useful-helm-operations">}}).

#### Optional: Configure the operator's external REST HTTPS interface

The operator can expose an external REST HTTPS interface which can be accessed from outside the Kubernetes cluster. As with the operator's internal REST interface, the external REST interface requires an SSL/TLS certificate and private key that the operator will use as the identity of the external REST interface (see below).

To enable the external REST interface, configure these values in a custom configuration file, or on the Helm command line:

* Set `externalRestEnabled` to `true`.
* Set `externalRestIdentitySecret` to the name of the kubernetes `tls secret` that contains the certificate(s) and private key.
* Optionally, set `externalRestHttpsPort` to the external port number for the operator REST interface (defaults to `31001`).

For more detailed information, see the [REST interface configuration]({{<relref "/userguide/managing-operators/using-the-operator/using-helm.md#rest-interface-configuration">}}) values.

##### Sample SSL certificate and private key for the REST interface

For testing purposes, the WebLogic Kubernetes Operator project provides a sample script
that generates a self-signed certificate and private key for the operator external REST interface.
The generated certificate and key is stored in a Kubernetes `tls secret` and the sample
script outputs the corresponding configuration values in YAML format. These values can be added to your custom YAML configuration file, for use when the operator's Helm chart is installed.

{{% notice warning %}}
The sample script should ***not*** be used in a production environment because
typically a self-signed certificate for external communucation is not considered safe.
A certficate signed by a commercial certificate authority is more widely accepted and
should contain valid host names, expiration dates and key constraints.
{{% /notice %}}

For more detailed information about the sample script and how to run it, see
the [REST APIs]({{<relref "/samples/simple/rest/_index.md#sample-to-create-certificate-and-key">}}) in the ***Samples*** section.

#### Optional: Elastic Stack (Elasticsearch, Logstash, and Kibana) integration

The operator Helm chart includes the option of installing the necessary Kubernetes resources for Elastic Stack integration.

You are responsible for configuring Kibana and Elasticsearch, then configuring the operator Helm chart to send events to Elasticsearch. In turn, the operator Helm chart configures Logstash in the operator deployment to send the operator's log contents to that Elasticsearch location.

##### Elastic Stack per-operator configuration

As part of the Elastic Stack integration, Logstash configuration occurs for each deployed operator instance.  You can use the following configuration values to configure the integration:

* Set `elkIntegrationEnabled` is `true` to enable the integration.
* Set `logStashImage` to override the default version of Logstash to be used (`logstash:6.2`).
* Set `elasticSearchHost` and `elasticSearchPort` to override the default location where Elasticsearch is running (`elasticsearch2.default.svc.cluster.local:9201`). This will configure Logstash to send the operator's log contents there.

For more detailed information, see the [Operator Helm configuration values]({{<relref "/userguide/managing-operators/using-the-operator/using-helm.md#operator-helm-configuration-values">}}).

---
title: "Secrets"
date: 2019-02-23T17:36:33-05:00
weight: 6
description: "Kubernetes Secrets for the operator."
---

{{< table_of_contents >}}

### Domain credentials secret

The credentials for the WebLogic domain are kept in a Kubernetes `Secret` where the name of
the secret is specified using `webLogicCredentialsSecret` in the WebLogic `Domain` resource.
Also, the domain credentials secret must be created in the namespace where the `Domain` will be running.

{{% notice note %}}
For an example of a WebLogic Domain YAML file using `webLogicCredentialsSecret`,
see [Container Image Protection]({{<relref "/security/domain-security/image-protection.md">}}).
{{% /notice %}}

The samples supplied with the operator use a naming convention that follows
the pattern `<domainUID>-weblogic-credentials`, where `<domainUID>` is
the unique identifier of the domain, for example, `domain1-weblogic-credentials`.

If the WebLogic domain will be started in `domain1-ns` and the `<domainUID>` is `domain1`,
an example of creating a Kubernetes `generic secret` is as follows:

```shell
$ kubectl -n domain1-ns create secret generic domain1-weblogic-credentials \
  --from-file=username --from-file=password
```
```shell
$ kubectl -n domain1-ns label secret domain1-weblogic-credentials \
  weblogic.domainUID=domain1 weblogic.domainName=domain1
```

{{% notice tip %}}
Oracle recommends that you not include unencrypted passwords on command lines.
Passwords and other sensitive data can be prompted for or looked up by shell scripts or
tooling. For more information about creating Kubernetes Secrets, see the Kubernetes
[Secrets](https://kubernetes.io/docs/concepts/configuration/secret/#creating-your-own-secrets)
documentation.
{{% /notice %}}

The operator's introspector job will expect the secret key names to be:

- `username`
- `password`

For example, here is the result when describing the Kubernetes `Secret`:
```shell
$ kubectl -n domain1-ns describe secret domain1-weblogic-credentials
```
```
Name:         domain1-weblogic-credentials
Namespace:    domain1-ns
Labels:       weblogic.domainName=domain1
              weblogic.domainUID=domain1
Annotations:  <none>

Type:  Opaque

Data
====
password:  8 bytes
username:  8 bytes
```

### Domain image pull secret

The WebLogic domain that the operator manages can have images that are protected
in the registry. The `imagePullSecrets` setting on the `Domain` can be used to specify the
Kubernetes `Secret` that holds the registry credentials.

{{% notice info %}}
For more information, see [Container Image Protection]({{<relref "/security/domain-security/image-protection.md">}}).
{{% /notice %}}

### Domain configuration override or runtime update secrets

The operator supports embedding macros within configuration override templates
and Model in Image model files that reference Kubernetes Secrets.
These Kubernetes Secrets can be created with any name in the
namespace where a domain will be running. The Kubernetes Secret names are
specified using `configuration.secrets` in the WebLogic `Domain` resource.

{{% notice info %}}
For more information, see
[Configuration overrides]({{<relref "/managing-domains/configoverrides/_index.md#how-do-you-specify-overrides">}})
and
[Runtime updates]({{<relref "/managing-domains/model-in-image/runtime-updates.md">}}).
{{% /notice %}}

### Operator image pull secret

The Helm chart for installing the operator has an `imagePullSecrets` option to specify the
image pull secret used for the operator's image when using a private registry;
alternatively, the image pull secret can be specified on the operator's service account.

{{% notice info %}}
For more information, see
[Customizing operator image name, pull secret, and private registry]({{<relref "/managing-operators/preparation#customizing-operator-image-name-pull-secret-and-private-registry">}}).
{{% /notice %}}

### Operator external REST interface secret

The operator can expose an external REST HTTPS interface which can be
accessed from outside the Kubernetes cluster. A Kubernetes `tls secret`
is used to hold the certificates and private key.

{{% notice info %}}
For more information, see [REST Services]({{<relref "/managing-operators/the-rest-api.md">}}).
{{% /notice %}}

### Operator internal REST interface secret

The operator exposes an internal REST HTTPS interface with a self-signed certificate.
The certificate is kept in a Kubernetes `ConfigMap` with the name `weblogic-operator-cm` using the key `internalOperatorCert`.
The private key is kept in a Kubernetes `Secret` with the name `weblogic-operator-secrets` using the key `internalOperatorKey`.
These Kubernetes objects are managed by the operator's Helm chart and are part of the
namespace where the operator is installed.

For example, to see all the operator's ConfigMaps and secrets when installed into
the Kubernetes Namespace `weblogic-operator-ns`, use:
```shell
$ kubectl -n weblogic-operator-ns get cm,secret
```

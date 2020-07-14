---
title: "Secrets"
date: 2019-02-23T17:36:33-05:00
weight: 6
description: "Kubernetes Secrets for the operator"
---

#### Contents
* [Domain credentials secret](#domain-credentials-secret)
* [Domain image pull secret](#domain-image-pull-secret)
* [Operator image pull secret](#operator-image-pull-secret)
* [Operator configuration override secrets](#operator-configuration-override-secrets)
* [Operator external REST interface secret](#operator-external-rest-interface-secret)
* [Operator internal REST interface secret](#operator-internal-rest-interface-secret)

#### Domain credentials secret

The credentials for the WebLogic domain are kept in a Kubernetes `Secret` where the name of
the secret is specified using `webLogicCredentialsSecret` in the WebLogic `Domain` resource.
Also, the domain credentials secret must be created in the namespace where the `Domain` will be running.

{{% notice note %}}
For an example of a WebLogic Domain YAML file using `webLogicCredentialsSecret`,
see [Docker Image Protection]({{<relref "/security/domain-security/image-protection#1-use-imagepullsecrets-with-the-domain-resource">}}).
{{% /notice %}}

The samples supplied with the operator use a naming convention that follows
the pattern `<domainUID>-weblogic-credentials`, where `<domainUID>` is
the unique identifier of the domain, for example, `domain1-weblogic-credentials`.

If the WebLogic domain will be started in `domain1-ns` and the `<domainUID>` is `domain1`,
an example of creating a Kubernetes `generic secret` is as follows:

```bash
$ kubectl -n domain1-ns create secret generic domain1-weblogic-credentials \
  --from-file=username --from-file=password

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
```bash
$ kubectl -n domain1-ns describe secret domain1-weblogic-credentials
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

#### Domain image pull secret

The WebLogic domain that the operator manages can have images that are protected
in the registry. The `imagePullSecrets` setting on the `Domain` can be used to specify the
Kubernetes `Secret` that holds the registry credentials.

{{% notice info %}}
For more information, see [Docker Image Protection]({{<relref "/security/domain-security/image-protection#weblogic-domain-in-docker-image-protection">}}).
{{% /notice %}}

#### Operator image pull secret

The Helm chart for installing the operator has an option to specify the
image pull secret used for the operator's image when using a private registry.
The Kubernetes `Secret` of type `docker-registry` should be created in the namespace
where the operator is deployed.

Here is an example of using the `helm install` command to set the image name and image pull secret:

```bash
$ helm install my-weblogic-operator kubernetes/charts/weblogic-operator \
  --set "image=my.io/my-operator-image:1.0" \
  --set "imagePullSecrets[0].name=my-operator-image-pull-secret" \
  --namespace weblogic-operator-ns \
  --wait
```

{{% notice info %}}
For more information, see
[Install the operator Helm chart]({{<relref "/userguide/managing-operators/installation/_index.md#install-the-operator-helm-chart">}}).
{{% /notice %}}

#### Operator configuration override secrets

The operator supports embedding macros within configuration override templates
that reference Kubernetes Secrets. These Kubernetes Secrets can be created with any name in the
namespace where the `Domain` will be running. The Kubernetes Secret names are
specified using `configuration.secrets` in the WebLogic `Domain` resource.

{{% notice info %}}
For more information, see
[Configuration overrides]({{<relref "/userguide/managing-domains/configoverrides/_index.md#how-do-you-specify-overrides">}}).
{{% /notice %}}

#### Operator external REST interface secret

The operator can expose an external REST HTTPS interface which can be
accessed from outside the Kubernetes cluster. A Kubernetes `tls secret`
is used to hold the certificates and private key.

{{% notice info %}}
For more information, see [Certificates]({{<relref "/security/certificates#additional-reading">}}).
{{% /notice %}}

#### Operator internal REST interface secret

The operator exposes an internal REST HTTPS interface with a self-signed certificate.
The certificate is kept in a Kubernetes `ConfigMap` with the name `weblogic-operator-cm` using the key `internalOperatorCert`.
The private key is kept in a Kubernetes `Secret` with the name `weblogic-operator-secrets` using the key `internalOperatorKey`.
These Kubernetes objects are managed by the operator's Helm chart and are part of the
namespace where the operator is installed.

For example, to see all the operator's ConfigMaps and secrets when installed into
the Kubernetes Namespace `weblogic-operator-ns`, use:
```bash
$ kubectl -n weblogic-operator-ns get cm,secret
```

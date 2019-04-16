---
title: "Secrets"
date: 2019-02-23T17:36:33-05:00
weight: 6
description: "Kubernetes secrets for the WebLogic operator"
---

#### Contents
* [WebLogic domain credentials secret](#weblogic-domain-credentials-secret)
* [WebLogic domain image pull secret](#weblogic-domain-image-pull-secret)
* [WebLogic operator image pull secret](#weblogic-operator-image-pull-secret)
* [WebLogic operator configuration override secrets](#weblogic-operator-configuration-override-secrets)
* [WebLogic operator external REST interface secret](#weblogic-operator-external-rest-interface-secret)
* [WebLogic operator internal REST interface secret](#weblogic-operator-internal-rest-interface-secret)

#### WebLogic domain credentials secret

The credentials for the WebLogic domain are kept in a Kubernetes `Secret` where the name of
the secret is specified using `webLogicCredentialsSecret` in the WebLogic `Domain` resource.
Also, the domain credentials secret must be created in the namespace where the `Domain` will be running.

{{% notice note %}}
For an example of a WebLogic domain resource using `webLogicCredentialsSecret`,
see [Docker Image Protection]({{<relref "/security/domain-security/image-protection.md#1-use-imagepullsecrets-with-the-domain-resource">}}).
{{% /notice %}}

The samples supplied with the WebLogic operator use a naming convention that follows
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
Passwords and other sensitive data can be prompted for or looked up by shell scripts and/or
tooling. For more information about creating Kubernetes secrets, see the Kubernetes
[Secrets](https://kubernetes.io/docs/concepts/configuration/secret/#creating-your-own-secrets)
documentation.
{{% /notice %}}

The WebLogic operator's introspector job will expect the secret key names to be:

- `username`
- `password`

For example, here is what results when describing the Kubernetes `Secret`:
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

#### WebLogic domain image pull secret

The WebLogic domain that the operator manages can have images that are protected
in the registry. The `imagePullSecrets` setting on the `Domain` can be used to specify the
Kubernetes `Secret` that holds the registry credentials.

{{% notice info %}}
For more information, see [Docker Image Protection]({{<relref "/security/domain-security/image-protection.md#weblogic-domain-in-docker-image-protection">}})
under **Domain security**.
{{% /notice %}}

#### WebLogic operator image pull secret

The Helm chart for installing the operator has an option to specify the
image pull secret used for the operator's image when using a private registry.
The Kubernetes `Secret` of type `docker-registry` should be created in the namespace
where the operator is deployed.

Here is an example of using the `helm install` command to set the image name and image pull secret:
```bash
$ helm install kubernetes/charts/weblogic-operator \
  --set "image=my.io/my-operator-image:1.0" \
  --set "imagePullSecrets[0].name=my-operator-image-pull-secret" \
  --name my-weblogic-operator --namespace weblogic-operator-ns \
  --wait
```

{{% notice info %}}
For more information, see
[Install the operator Helm chart]({{<relref "/userguide/managing-operators/installation/_index.md#install-the-operator-helm-chart">}})
under **User Guide**.
{{% /notice %}}

#### WebLogic operator configuration override secrets

The WebLogic operator supports embedding macros within configuration override templates
that reference Kubernetes secrets. These Kubernetes secrets can be created with any name in the
namespace where the `Domain` will be running. The Kubernetes secret names are
specified using `configOverrideSecrets` in the WebLogic `Domain` resource.

{{% notice info %}}
For more information, see
[Configuration overrides]({{<relref "/userguide/managing-domains/configoverrides/_index.md#how-do-you-specify-overrides">}})
under **User Guide**.
{{% /notice %}}

#### WebLogic operator external REST interface secret

The operator can expose an external REST HTTPS interface which can be
accessed from outside the Kubernetes cluster. A Kubernetes `tls secret`
is used to hold the certificate(s) and private key.

{{% notice info %}}
For more information, see [Certificates]({{<relref "/security/certificates.md#reference">}})
under **Securty**.
{{% /notice %}}

#### WebLogic operator internal REST interface secret

The operator exposes an internal REST HTTPS interface with a self-signed certificate.
The certificate is kept in a Kubernetes `ConfigMap` with the name `weblogic-operator-cm` using the key `internalOperatorCert`.
The private key is kept in a Kubernetes `Secret` with the name `weblogic-operator-secrets` using the key `internalOperatorKey`.
These Kubernetes objects are managed by the operator's Helm chart and are part of the
namespace where the WebLogic operator is installed.

For example, to see all the operator's config maps and secrets when installed into
the Kubernetes namespace `weblogic-operator-ns`, use:
```bash
$ kubectl -n weblogic-operator-ns get cm,secret
```

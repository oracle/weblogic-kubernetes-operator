---
title: "Secrets"
date: 2019-02-23T17:36:33-05:00
weight: 6
description: "Kubernetes secrets for the WebLogic operator"
---

#### Contents
* [WebLogic domain credentials secret](#weblogic-domain-credentials-secret)
* [WebLogic domain image pull secret](#weblogic-domain-image-pull-secret)
* [WebLogic operator external REST interface secret](#weblogic-operator-external-rest-interface-secret)
* [WebLogic operator internal REST interface secret](#weblogic-operator-internal-rest-interface-secret)

#### WebLogic domain credentials secret

The credential for the WebLogic domain is kept in a Kubernetes `Secret` that
follows the pattern `<domainUID>-weblogic-credentials`, where `<domainUID>` is
the unique identifier of the domain, for example, `domain1-weblogic-credentials`.
The `Secret` is created in the namespace where the `Domain` will be running.

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
in the registry. The `imagePullSecrets` setting can be used to specify the
Kubernetes `Secret` that holds the registry credentials.

{{% notice info %}}
For more information, see [Docker Image Protection]({{<relref "/security/domain-security/image-protection.md#weblogic-domain-in-docker-image-protection">}})
under **Domain Security**.
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
The certificate is kept in a Kubernetes `ConfigMap` with the name `weblogic-operator-cm ` using the key `internalOperatorCert`.
The private key is kept in a Kubernetes `Secret` with the name `weblogic-operator-secrets` using the key `internalOperatorKey`.
These Kubernetes objects are managed by the operator's Helm chart and are part of the
namespace where the WebLogic operator is installed.

For example, to see all the operator's config maps and secrets when installed into
the Kubernetes namespace `weblogic-operator-ns`, use:
```bash
$ kubectl -n weblogic-operator-ns get cm,secret
```

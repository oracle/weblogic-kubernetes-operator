---
title: "Encryption"
date: 2019-02-23T17:36:29-05:00
weight: 3
description: "WebLogic domain encryption and the operator"
---
#### Contents

* [Introspector encryption](#introspector-encryption)
* [Encryption of Kubernetes secrets](#encryption-of-kubernetes-secrets)
* [Additional reading](#additional-reading)

#### Introspector encryption

The operator has an introspection job that handles WebLogic domain encryption.
The introspection job also addresses the use of Kubernetes secrets with configuration overrides.
For additional information on the configuration handling, see
[Configuration overrides]({{<relref "/userguide/managing-domains/configoverrides/_index.md">}}).

The introspection job also creates a `boot.properties` file that is made available
to the pods in the WebLogic domain. The credential used for the
WebLogic domain is kept in a Kubernetes `Secret` which follows the naming pattern
`<domainUID>-weblogic-credentials`, where `<domainUID>` is
the unique identifier of the domain, for example, `mydomain-weblogic-credentials`.

{{% notice info %}}
For more information about the WebLogic credentials secret, see [Secrets]({{<relref "/security/secrets.md">}}).
{{% /notice %}}

#### Encryption of Kubernetes secrets

{{% notice tip %}}
To better protect your credentials and private keys, the Kubernetes cluster should be set up with encryption.
Please see the Kubernetes documentation about
[encryption at rest for secret data](https://kubernetes.io/docs/tasks/administer-cluster/encrypt-data/)
and [using a KMS provider for data encryption](https://kubernetes.io/docs/tasks/administer-cluster/kms-provider/).
{{% /notice %}}

#### Additional reading
* [Encryption of values for WebLogic configuration overrides]({{<relref "/userguide/managing-domains/configoverrides/_index.md#override-template-macros">}})

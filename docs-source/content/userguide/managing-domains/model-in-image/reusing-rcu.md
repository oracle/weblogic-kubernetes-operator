+++
title = "Reusing an RCU database"
date = 2020-03-11T16:45:16-05:00
weight = 70
pre = "<b> </b>"
description = "Reusing an RCU database between deployments of a Model in Image JRF domain."
+++

#### Reusing an RCU database between JRF domain deployments

{{% notice info %}} This section only applies for a JRF domain type. Skip it if your domain type is `WLS` or `Restricted JRF`.
{{% /notice %}}

When you deploy a JRF domain for the first time, the domain will add itself to its RCU database tables, and also create a 'wallet' file in the domain's home directory that enables access to the domain's data in the RCU database. This wallet is encrypted using an OPSS key password that you supply to the domain using a secret.

If it is important to reuse or share the same database and data between deployments of your domain, then it is also important locate and preserve its OPSS wallet password and wallet file. An OPSS wallet password and wallet file allows a JRF deployment to access a FMW infrastructure database that has already been initialized and used before.

When a domain is first deployed, the operator will copy its OPSS wallet file from the domain home and store it in the domain's introspector domain configmap. For a domain that has been created using Model in Image, here is how to export this wallet file for reuse:

    ```
    kubectl -n MY_DOMAIN_NAMESPACE \
      get configmap MY_DOMAIN_UID-weblogic-domain-introspect-cm \
      -o jsonpath='{.data.ewallet\.p12}' \
      > ewallet.p12
    ```

Alternatively, you can use the `kubernetes/samples/scripts/create-weblogic-domain/model-in-image/utils/opss_wallet.sh -s` command to export the wallet file (pass `-?` to see this script's command-line arguments and defaults).

To reuse the wallet:
  - Create a secret with a key named `walletPassword` that contains the same OPSS password that you specified in the original domain and make sure that your domain resource `configuration.opss.walletPasswordSecret` attribute names this secret.
  - Create a secret with a key named `walletFile` that contains the OPSS wallet file that you exported above and make sure that your domain resource `configuration.opss.walletFileSecret` attribute names this secret.

Here's sample code for deploying the OPSS wallet password and wallet file secrets that assumes the wallet is in local file `ewallet.p12` and that the secret passphrase is `welcome1`:

    ```
    kubectl -n MY_DOMAIN_NAMESPACE \
      create secret generic MY_DOMAIN_UID-my-opss-wallet-password-secret \
      --from-literal=walletPassword=welcome1
    kubectl -n MY_DOMAIN_NAMESPACE \
      label secret MY_DOMAIN_UID-my-opss-wallet-password-secret \
      weblogic.domainUID=sample-domain1

    kubectl -n MY_DOMAIN_NAMESPACE \
      create secret generic MY_DOMAIN_UID-my-opss-wallet-file-secret \
      --from-file=walletFile=ewallet.p12
    kubectl -n sample-domain1-ns \
      label secret MY_DOMAIN_UID-my-opss-wallet-file-secret \
      weblogic.domainUID=sample-domain1
    ```

Alternatively, you can use the `kubernetes/samples/scripts/create-weblogic-domain/model-in-image/utils/opss_wallet.sh -r` command to deploy a local wallet file as a secret (pass `-?` to see this script's command-line arguments and defaults).

See also, [Prerequisites for JRF domain types]({{< relref "/userguide/managing-domains/model-in-image/usage/_index.md#7-prerequisites-for-jrf-domain-types" >}}).

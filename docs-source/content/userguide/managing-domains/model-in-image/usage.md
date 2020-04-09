+++
title = "Usage"
date = 2020-03-11T16:45:16-05:00
weight = 20
pre = "<b> </b>"
description = "Steps for creating and deploying Model in Image images and their associated domain resources."
+++


#### Contents

   - [WebLogic Server Kubernetes Operator](#1-weblogic-server-kubernetes-operator)
   - [WebLogic Server image](#2-weblogic-server-image)
   - [Optional WDT model config map](#3-optional-wdt-model-config-map)
   - [Required runtime encryption secret](#4-required-runtime-encryption-secret)
   - [Secrets for model macros](#5-secrets-for-model-macros)
   - [Domain resource attributes](#6-domain-resource-attributes)
   - [Prerequisites for JRF domain types](#7-prerequisites-for-jrf-domain-types)

#### Requirements

Here's what's needed to create and deploy a typical Model in Image domain. These items do not need to be created in order.

#### 1. WebLogic Server Kubernetes Operator

Deploy the operator and ensure that it is monitoring the desired namespace for your Model in Image domain. See [Manage operators]({{< relref "/userguide/managing-operators/_index.md" >}}) and [Quick Start]({{< relref "/quickstart/_index.md" >}}).

#### 2. WebLogic Server image

Model in Image requires creating a 'final' deployable image that has WebLogic Server and WDT installed, plus your model and application files.

You can start with a WebLogic Server 12.2.1.3 or later pre-built base image obtained from [Docker Hub](https://github.com/oracle/docker-images/tree/master/OracleWebLogic) or similar, manually build your own base image as per [Preparing a Base Image]({{< relref "/userguide/managing-domains/domain-in-image/base-images/_index.md" >}}), or build a base image using the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool). Note that any 12.2.1.3 image must also include patch 29135930 (the pre-built images already contain this patch). For an example of the first approach for both WLS and JRF domains, see the [Model in Image]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}}) sample.

After you have a base image, Model in Image requires layering the following directory structure for its (optional) WDT models artifacts and (required) WDT binaries:

| Directory                | Contents                           | Extension   |
| ------------------------ | ---------------------------------- | ----------- |
| `/u01/wdt/models`         | Optional domain model YAML files   | `.yaml`       |
| `/u01/wdt/models`         | Optional model variable files      | `.properties` |
| `/u01/wdt/models`         | Optional application archives      | `.zip`        |
| `/u01/wdt/weblogic-deploy`| Unzipped WebLogic deploy install   |             |

There are two methods for layering Model in Image artifacts on top of a base image:

  - **Manual Image Creation**: Use Docker commands to layer the WDT artifacts from the above table on top of your base image into a new image.

  - **WebLogic Image Tool**: Use the convenient [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool). The WebLogic Image Tool (WIT) has built-in options for embedding WDT model files, WDT binaries, WebLogic Server binaries, and WebLogic Server patches in an image. The [Model in Image]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}}) sample uses the WIT approach. For an example, see the sample's `build_image_model.sh` file in the operator source's `kubernetes/samples/scripts/create-weblogic-domain/model-in-image` directory.

For more information about model file syntax, see [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}).

#### 3. Optional WDT model config map

You can create a WDT model config map that defines additional model `.yaml` and `.properties` files beyond what you've already supplied in your image, and then reference this config map using your domain resource's `configuration.model.configMap` attribute. This is optional if the supplied image already fully defines your model.

WDT model config map files will be merged with the WDT files defined in your image at runtime before your domain home is created. The config map files can add to, remove from, or alter the model configuration that you supplied within your image.

For example, place additional `.yaml` and `.properties` files in a directory called `/home/acmeuser/wdtoverride` and run the following commands:

  ```
  kubectl -n MY-DOMAIN-NAMESPACE \
    create configmap MY-DOMAINUID-my-wdt-config-map \
    --from-file /home/acmeuser/wdtoverride
  kubectl -n MY-DOMAIN-NAMESPACE \
    label  configmap MY-DOMAINUID-my-wdt-config-map \
    weblogic.domainUID=MY-DOMAINUID
  ```

See [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}) for a discussion of model file syntax and loading order, and see [Runtime updates]({{< relref "/userguide/managing-domains/model-in-image/runtime-updates.md" >}}) for a discussion of using WDT model config maps to update the model configuration of a running domain.


#### 4. Required runtime encryption secret

Model in Image requires a runtime encryption secret with a secure `password` key. This secret is used by the operator to encrypt model and domain home artifacts before it adds them to a runtime config map or log. You can safely change the `password` that you set, at any time after you've fully shut down a domain, but it must remain the same for the life of a running domain. The runtime encryption secret that you create can be named anything, but note that it is a best practice to name and label secrets with their domain UID to help ensure that cleanup scripts can find and delete them.

**NOTE**: Because the runtime encryption password does not need to be shared and needs to exist only for the life of a domain, you may want to use a password generator.

Example:

  ```
  kubectl -n MY-DOMAIN-NAMESPACE \
    create secret generic MY-DOMAINUID-runtime-encrypt-secret \
    --from-literal=password=welcome1
  kubectl -n MY-DOMAIN-NAMESPACE \
    label secret MY-DOMAINUID-runtime-encrypt-secret \
    weblogic.domainUID=MY-DOMAINUID
  ```

Corresponding domain resource snippet:

  ```
  configuration:
    model:
      runtimeEncryptionSecret: MY-DOMAINUID-runtime-encrypt-secret
  ```

#### 5. Secrets for model macros

Create additional secrets as needed by macros in your model files. For example, these can store database URLs and credentials that are accessed using `@@SECRET` macros in your model that reference the secrets.  For a discussion of model macros, see [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}).

#### 6. Domain resource attributes

The following domain resource attributes are specific to Model in Image domains.

| Domain Resource Attribute                    |  Notes |
| -------------------------                    |  ------------------ |
| `domainHomeSourceType`                       |  Required. Set to `FromModel`. |
| `domainHome`                                 |  Must reference an empty or non-existent directory within your image. Do not include the mount path of any persistent volume. Note that Model in Image recreates the domain home for a WebLogic pod every time the pod restarts.|
| `configuration.model.configMap`             | Optional. Set if you have stored additional models in a config map as per [Optional WDT model config map](#3-optional-wdt-model-config-map). |
| `configuration.secrets`                      | Optional. Set this array if your image or config map models contain macros that reference custom Kubernetes secrets. For example, if your macros depend on secrets `my-secret` and `my-other-secret`, then set to `[my-secret, my-other-secret]`.|
| `configuration.model.runtimeEncryptionSecret`| Required. All Model in Image domains must specify a runtime encryption secret. See [Required runtime encryption secret](#4-required-runtime-encryption-secret). |
| `configuration.model.domainType`             | Set the type of domain. Valid values are `WLS`, `JRF`, and `RestrictedJRF` where `WLS` is the default. See [WDT Domain Types](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/type_def.md).|

**Notes**:

 - There are additional attributes that are common to all domain home source types, such as the `image` field. See the Domain Resource [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/docs/domains/Domain.md) and [documentation]({{< relref "/userguide/managing-domains/domain-resource.md" >}}) for a full list of domain resource fields.

 - There are also additional fields that are specific to JRF domain types. For more information, see [Prerequisites for JRF domain types](#7-prerequisites-for-jrf-domain-types).

 - Sample domain resource: For an example of a fully specified sample domain resource, see the the operator source's `kubernetes/samples/scripts/create-weblogic-domain/model-in-image/k8s-domain.yaml.template` file for the [Model in Image]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}}) sample. Note that the `@@` entries in this template are not processed by the operator; they need to replaced with actual values before the resource can be applied.

#### 7. Prerequisites for JRF domain types

{{% notice info %}} This section applies only for a `JRF` domain type. Skip it if your domain type is `WLS` or `RestrictedJRF`.
{{% /notice %}}

A JRF domain requires an infrastructure database called an RCU database, initializing this database, and configuring your domain to access this database. All of these steps must occur before you first deploy your domain. When you first deploy your domain, the introspector job will initialize it's RCU schema tables in the database - a process that can take several minutes.

Furthermore, if you want to safely ensure that a restarted JRF domain can access updates to the infrastructure database that the domain made at an earlier time, the original domain's wallet file must be safely saved as soon as practical, and the restarted domain must be supplied a wallet file that was obtained from a previous run of the domain, as discussed in [Reusing an RCU database]({{< relref "/userguide/managing-domains/model-in-image/reusing-rcu.md" >}}).

__Here are the required settings for Model in Image JRF domains:__

- Set `configuration.model.domainType` to `JRF`.

- Set `configuration.opss.walletPasswordSecret` to reference a secret that defines a `walletPassword` key. This is used to encrypt the domain's OPSS wallet file. This is a required field for JRF domains.

- Set `configuration.opss.walletFileSecret` to reference a secret that contains your domain's OPSS wallet file in its `walletFile` key. This assumes you have an OPSS wallet file from a previous start of the same domain. It enables a restarted or migrated domain to access its RCU database information. For more information, see [Reusing an RCU database between domain deployments]({{< relref "/userguide/managing-domains/model-in-image/reusing-rcu.md" >}}). This is an optional field for JRF domains, but must always be set if you want a restarted or migrated domain to access its RCU database information.

- Set the `configuration.introspectorJobActiveDeadlineSeconds` introspection job timeout to at least 300 seconds. This is in an optional field but is needed because domain home creation takes a considerable amount of time the first time a JRF domain is created (due to initializing the domain's RCU database tables), and because Model in Image creates your domain home for you using the introspection job.

- Define an `RCUDbInfo` stanza in your model. Access to an RCU database requires defining a `RCUDbInfo` stanza in your model's `domainInfo` stanza with the necessary information for accessing the domain's schema within the database. Usually this information should be supplied using a secret that you deploy and reference in your domain resource's `configuration.secrets` field. Here's an example `RCUDbInfo` stanza:

  ```
  domainInfo:
      RCUDbInfo:
          rcu_prefix:          '@@SECRET:sample-domain1-rcu-access/rcu_prefix@@'
          rcu_schema_password: '@@SECRET:sample-domain1-rcu-access/rcu_schema_password@@'
          rcu_db_conn_string:  '@@SECRET:sample-domain1-rcu-access/rcu_db_conn_string@@'

  ```

__Important instructions when changing an RCU schema password:__

  {{% notice warning %}}
  Carefully follow these instructions in order to prevent irrecoverably locking up your RCU database schema account when changing your RCU schema password.
  {{% /notice %}}

- Shutdown all domains that access the RCU database schema. For example, set their `serverStartPolicy` to `NEVER`.

- Update the RCU schema password in the database.

- Update the Kubernetes secret that contains your `RCUDbInfo.rcu_schema_password` for each domain.

- Restart the domains. For example, change their `serverStartPolicy` from `NEVER` to `IF_NEEDED`.

- Save your wallet files again, as changing your RCU schema password generates a different wallet. See [Reusing an RCU database between domain deployments]({{< relref "/userguide/managing-domains/model-in-image/reusing-rcu.md" >}}).

__References:__

For an example of using JRF in combination with Model in Image, see the [Model in Image]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}}) sample.

See [Reusing an RCU database between domain deployments]({{< relref "/userguide/managing-domains/model-in-image/reusing-rcu.md" >}}).

See also, [Specifying RCU connection information in the model](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/rcuinfo.md) in the WDT documentation.

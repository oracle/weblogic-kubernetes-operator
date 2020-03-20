+++
title = "Model files"
date = 2020-03-11T16:45:16-05:00
weight = 30
pre = "<b> </b>"
description = "Model file requirements, macros, and loading order."
+++

#### Contents

 - [Introduction](#introduction)
 - [Sample model file](#sample-model-file)
 - [Important notes about Model in Image model files](#important-notes-about-model-in-image-model-files)
 - [Model file naming and loading order](#model-file-naming-and-loading-order)
 - [Model file macros](#model-file-macros)
   - [Using secrets in model files](#using-secrets-in-model-files)
   - [Using environment variables in model files](#using-environment-variables-in-model-files)
   - [Combining secrets and environment variable in model files](#combining-secrets-and-environment-variable-in-model-files)

#### Introduction

This document describes basic Model in Image model file syntax, naming, and macros. For additional information, see the [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling) documentation.

{{% notice tip %}} The WDT 'discover tool' is particularly useful for generating model files from an existing domain home.
{{% /notice %}}

#### Sample model file

Here's an example of a model `.yaml` file that defines a WebLogic Administration Server and dynamic cluster.

```
domainInfo:
  AdminUserName: '@@FILE:/weblogic-operator/secrets/username@@'
  AdminPassword: '@@FILE:/weblogic-operator/secrets/password@@'
  ServerStartMode: 'prod'

topology:
  Name: '@@ENV:DOMAIN_UID@@'
  AdminServerName: "admin-server"
  Cluster:
    "cluster-1":
      DynamicServers:
        ServerTemplate:  "cluster-1-template"
        ServerNamePrefix: "managed-server"
        DynamicClusterSize: 5
        MaxDynamicClusterSize: 5
        CalculatedListenPorts: false
  Server:
    "admin-server":
      ListenPort: 7001
  ServerTemplate:
    "cluster-1-template":
      Cluster: "cluster-1"
      ListenPort: 8001
```

Some notes about the sample model file:
 - It includes a WebLogic credentials stanza that is required by Model in Image.
 - It derives its domain name from the pre-defined environment variable `DOMAIN_UID` but note that this is not required.
 - For a description of model file macro references to secrets and environment variables, see [Model file macros](#model-file-macros).

#### Important notes about Model in Image model files

- You can use model macros to reference arbitrary secrets from model files. This is recommended for handling mutable values such as database user names, passwords, and URLs. See [Using secrets in model files](#using-secrets-in-model-files).

- You can use model macros to reference arbitrary environment variables from model files. This is useful for handling plain text mutable values that you can define using an `env` stanza in your domain resource, and is also useful for accessing the built in `DOMAIN_UID` environment variable. See [Using environment variables in model files](#using-environment-variables-in-model-files).

- A model __must__ contain a `domainInfo` stanza that references your WebLogic administrative credentials. You can use the `@@FILE` macro to reference your domain resource's WebLogic credentials secret for this purpose. For example:

    ```
    domainInfo:
      AdminUserName: '@@FILE:/weblogic-operator/secrets/username@@'
      AdminPassword: '@@FILE:/weblogic-operator/secrets/password@@'
    ```

- For most models, it's useful to minimize or eliminate the usage of model variable files (also known as property files) and use secrets or environment variables instead.

- For most models, passwords should not be directly included in property or model files - these files instead should use macros that reference secrets. But, if a property or model file must contain secure data, there is a WDT encryption option you can use. See [Optional WDT encryption secret]({{< relref "/userguide/managing-domains/model-in-image/usage.md#4-optional-wdt-encryption-secret" >}}).

- You can control the order that WDT uses to load your model files, see [Model file naming and loading order](#model-file-naming-and-loading-order).

#### Model file naming and loading order

Refer to this section if you need to control the order in which your model files are loaded.

During domain home creation, model and property files are first loaded from the `/u01/model_home/models` directory within the image and are then loaded from the optional WDT config map described in [Optional WDT model config map]({{< relref "/userguide/managing-domains/model-in-image/usage.md#3-optional-wdt-model-config-map" >}}).

The loading order within each of these locations is first determined using the convention `filename.##.yaml` and `filename.##.properties`, where `##` is a numeric number that specifies the desired order, and then is determined alphabetically as a tie-breaker. File names that don't include `.##.` sort _before_ other files as if they implicitly have the lowest possible `.##.`.

If an image file and config map file both have the same name, then both files are loaded.

For example, if you have these files in the image directory `/u01/model_home/models`:

```
jdbc.20.yaml
main-model.10.yaml
my-model.10.yaml
y.yaml  
```

And you have these files in the config map:

```
jdbc-dev-urlprops.10.yaml
z.yaml
```

Then the combined model files list is passed to the `WebLogic Deploy Tool` as:

```y.yaml,main-model.10.yaml,my-model.10.yaml,jdbc.20.yaml,z.yaml,jdbc-dev-urlprops.10.yaml```

Property files (ending in `.properties`) use the same sorting algorithm, but they are appended together into a single file prior to passing them to the `WebLogic Deploy Tool`.

#### Model file macros

##### Using secrets in model files

You can use WDT model `@@FILE` macros to reference the WebLogic administrator `username` and `password` keys that are stored in a Kubernetes secret and to optionally reference additional secrets.  These secrets must be deployed to the same namespace as your domain resource, and must be referenced in your domain resource using the `weblogicCredentialsSecret` and `configuration.secrets` fields. Here is the macro pattern for accessing these secrets:

  |Domain Resource Attribute|Corresponding WDT Model `@@FILE` Macro|
  |---------------------|-------------|
  |`webLogicCredentialsSecret`|`@@FILE:/weblogic-operator/secrets/username@@` and `@@FILE:/weblogic-operator/secrets/password@@`|
  |`configuration.secrets`|`@@FILE:/weblogic-operator/config-overrides-secrets/SECRET_NAME/SECRET_KEY@@`|

For example, you can reference the WebLogic credential user name using `@@FILE:/weblogic-operator/secrets/username@@`, and you can reference a custom secret `mysecret` with key `mykey` using `@@FILE:/weblogic-operator/config-overrides-secrets/mysecret/mykey@@`.

Here's a sample snippet from a domain resource that sets a `webLogicCredentialsSecret` and two custom secrets `my-custom-secret1` and `my-custom-secret2`.

  ```
  ...
  spec:
    webLogicCredentialsSecret:
      name: my-weblogic-credentials-secret
    configuration:
      secrets: [ my-custom-secret1,my-custom-secret2 ]
  ...
  ```

##### Using environment variables in model files

You can reference operator environment variables in model files. This includes any that you define yourself in your domain resource, or the built-in `DOMAIN_UID` environment variable.  For example, the `@@ENV:DOMAIN_UID@@` macro resolves to the current domain's domain UID.

TBD test this once WDT releases this feature - feature dev is complete but unreleased as of 3/19

##### Combining secrets and environment variable in model files

You can embed an environment variable macro in a secret macro. This is useful for referencing secrets that you've named based on your domain's `domainUID`. For example, if your `domainUID` is `domain1`, then the macro `@@FILE:/weblogic-operator/config-overrides-secrets/@@ENV:DOMAIN_UID@@-super-double-secret/mykey@@` resolves to the value stored on `mykey` for secret `domain1-super-double-secret`.

TBD test this once WDT releases the ENV feature - feature dev is complete but unreleased as of 3/19

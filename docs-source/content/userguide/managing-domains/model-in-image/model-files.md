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
   - [Combining secrets and environment variables in model files](#combining-secrets-and-environment-variables-in-model-files)

#### Introduction

This document describes basic Model in Image model file syntax, naming, and macros. For additional information, see the [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling) documentation.

{{% notice tip %}} The WDT [Discover Domain Tool](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/discover.md) is particularly useful for generating model files from an existing domain home.
{{% /notice %}}

#### Sample model file

Here's an example of a model YAML file that defines a WebLogic Server Administration Server and dynamic cluster.

```
domainInfo:
  AdminUserName: '@@SECRET:__weblogic-credentials__:username@@'
  AdminPassword: '@@SECRET:__weblogic-credentials__:password@@'
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

This sample model file:
 - Includes a WebLogic credentials stanza that is required by Model in Image.
 - Derives its domain name from the predefined environment variable `DOMAIN_UID`, but note that this is not required.

For a description of model file macro references to secrets and environment variables, see [Model file macros](#model-file-macros).

#### Important notes about Model in Image model files

  - Using model file macros

    - You can use model macros to reference arbitrary secrets from model files. This is recommended for handling mutable values such as database user names, passwords, and URLs. See [Using secrets in model files](#using-secrets-in-model-files).

      - All password fields in a model should use a secret macro. Passwords should not be directly included in property or model files because the files may appear in logs or debugging.

      - Model files encrypted with the WDT [Encrypt Model Tool](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/encrypt.md) are not supported. Use secrets instead.

    - You can use model macros to reference arbitrary environment variables from model files. This is useful for handling plain text mutable values that you can define using an `env` stanza in your domain resource, and is also useful for accessing the built in `DOMAIN_UID` environment variable. See [Using environment variables in model files](#using-environment-variables-in-model-files).

    - For most models, it's useful to minimize or eliminate the usage of model variable files (also known as property files) and use secrets or environment variables instead.

- A model __must__ contain a `domainInfo` stanza that references your WebLogic administrative credentials. You can use the `@@SECRET` macro with the reserved secret name `__weblogic-credentials__` to reference your domain resource's WebLogic credentials secret for this purpose. For example:

    ```
    domainInfo:
      AdminUserName: '@@SECRET:__weblogic-credentials__:username@@'
      AdminPassword: '@@SECRET:__weblogic-credentials__:password@@'
    ```

- A JRF domain type model __must__ contain a `domainInfo.RCUDbInfo` stanza; see [Requirements for JRF domain types]({{< relref "/userguide/managing-domains/model-in-image/usage/_index.md#requirements-for-jrf-domain-types" >}}).

- You can control the order that WDT uses to load your model files, see [Model file naming and loading order](#model-file-naming-and-loading-order).

#### Model file naming and loading order

Refer to this section if you need to control the order in which your model files are loaded.  The order is important when two or more model files refer to the same configuration, because the last model that's loaded has the highest precedence.

During domain home creation, model, and property files are first loaded from the `/u01/model_home/models` directory within the image and are then loaded from the optional WDT ConfigMap, described in [Optional WDT model ConfigMap]({{< relref "/userguide/managing-domains/model-in-image/usage/_index.md#optional-wdt-model-configmap" >}}).

The loading order within each of these locations is first determined using the convention `filename.##.yaml` and `filename.##.properties`, where `##` are digits that specify the desired order when sorted numerically. Additional details:

 * Embedding a `.##.` in a file name is optional.
   * When present, it must be placed just before the `properties` or `yaml` extension in order for it to take precedence over alphabetical precedence.
   * The precedence of file names that include more than one `.##.` is undefined.
   * The number can be any integer greater than or equal to zero.
 * File names that don't include `.##.` sort _before_ other files as if they implicitly have the lowest possible `.##.`  
 * If two files share the same number, the loading order is determined alphabetically as a tie-breaker.

If an image file and ConfigMap file both have the same name, then both files are loaded.

For example, if you have these files in the image directory `/u01/model_home/models`:

```
jdbc.20.yaml
main-model.10.yaml
my-model.10.yaml
y.yaml  
```

And you have these files in the ConfigMap:

```
jdbc-dev-urlprops.10.yaml
z.yaml
```

Then the combined model files list is passed to the WebLogic Deploy Tool as:

```y.yaml,main-model.10.yaml,my-model.10.yaml,jdbc.20.yaml,z.yaml,jdbc-dev-urlprops.10.yaml```

Property files (ending in `.properties`) use the same sorting algorithm, but they are appended together into a single file prior to passing them to the WebLogic Deploy Tool.

#### Model file macros

##### Using secrets in model files

You can use WDT model `@@SECRET` macros to reference the WebLogic administrator `username` and `password` keys that are stored in a Kubernetes Secret and to optionally reference additional secrets. Here is the macro pattern for accessing these secrets:


  |Domain Resource Attribute|Corresponding WDT Model `@@SECRET` Macro|
  |---------------------|-------------|
  |`webLogicCredentialsSecret`|`@@SECRET:__weblogic-credentials__:username@@` and `@@SECRET:__weblogic-credentials__:password@@`|
  |`configuration.secrets`|`@@SECRET:mysecret:mykey@@`|

For example, you can reference the WebLogic credential user name using `@@SECRET:__weblogic-credentials__:username@@`, and you can reference a custom secret `mysecret` with key `mykey` using `@@SECRET:mysecret:mykey@@`.

Any secrets that are referenced by an `@@SECRET` macro must be deployed to the same namespace as your domain resource, and must be referenced in your domain resource using the `weblogicCredentialsSecret` and `configuration.secrets` fields.

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

You can reference operator environment variables in model files. This includes any that you define yourself in your domain resource, or the built-in `DOMAIN_UID` environment variable.

For example, the `@@ENV:DOMAIN_UID@@` macro resolves to the current domain's domain UID.

##### Combining secrets and environment variables in model files

You can embed an environment variable macro in a secret macro. This is useful for referencing secrets that you've named based on your domain's `domainUID`.

For example, if your `domainUID` is `domain1`, then the macro `@@SECRET:@@ENV:DOMAIN_UID@@-super-double-secret:mykey@@` resolves to the value stored in `mykey` for secret `domain1-super-double-secret`.

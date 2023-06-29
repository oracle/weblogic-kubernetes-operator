+++
title = "Working with WDT model files"
date = 2020-03-11T16:45:16-05:00
weight = 4
pre = "<b> </b>"
description = "Learn about model file requirements, macros, and loading order."
+++

{{< table_of_contents >}}


This document describes working with WebLogic Deploy Tooling (WDT) model files in the operator.
For additional information, see the [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) documentation.

{{% notice tip %}} The WDT [Discover Domain Tool](https://oracle.github.io/weblogic-deploy-tooling/userguide/tools/discover/) is particularly useful for generating WDT model files from an existing domain home.
{{% /notice %}}

### Sample WDT model file with macros

Here's an example of a WDT model YAML file describing a domain.  For detailed information, see [Metadata model](https://oracle.github.io/weblogic-deploy-tooling/concepts/model/).

```yaml
domainInfo:
  AdminUserName: '@@SECRET:__weblogic-credentials__:username@@'
  AdminPassword: '@@SECRET:__weblogic-credentials__:password@@'
  RCUDbInfo:
      rcu_prefix: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_prefix@@'
      rcu_schema_password: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_schema_password@@'
      rcu_db_conn_string: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_db_conn_string@@'  
topology:
  Name: '@@ENV:DOMAIN_UID@@'
  AdminServerName: "admin-server"
  Cluster:
    "cluster-1":
      ...
resources:
  ...
appDeployments:
  Application:
    myear:
      SourcePath: wlsdeploy/applications/sample_app.ear
      ModuleType: ear
      Target: 'cluster-1'
```

This sample model file has four sections:

| Section    | Purpose                                                    |
|------------|------------------------------------------------------------|
| `domainInfo` | Describes the domain level information.                     |
| `topology`   | Describes the topology of the domain.                        |
| `resources`  | Describes the J2EE resources used in the domain.             |
| `appDeployments`  | Describes the applications and libraries used in the domain. |

Notice this value pattern: `@@...@@`.  These are macros that will be resolved at runtime by WDT in the operator environment.
For a description of model file macro references to secrets and environment variables, see [Model file macros](#model-file-macros).

### Important notes about WDT model files

  - Using model file macros:

    - You can use model macros to reference arbitrary secrets from model files. This is recommended for handling mutable values such as database user names, passwords, and URLs. See [Using secrets in model files](#using-secrets-in-model-files).

      - All password fields in a model should use a secret macro. Passwords should not be directly included in property or model files because the files may appear in logs or debugging.

      - Model files encrypted with the WDT [Encrypt Model Tool](https://oracle.github.io/weblogic-deploy-tooling/userguide/tools/encrypt/) are not supported. Use secrets instead.

    - You can use model macros to reference arbitrary environment variables from model files. This is useful for handling plain text mutable values that you can define using an `env` stanza in your Domain YAML file, and is also useful for accessing the built in `DOMAIN_UID` environment variable. See [Using environment variables in model files](#using-environment-variables-in-model-files).

    - For most models, it's useful to minimize or eliminate the usage of model variable files (also known as property files) and use secrets or environment variables instead.

- You can control the order that WDT uses to load your model files, see [WDT models location and loading order](#wdt-models-source-location-and-loading-order).

### WDT models source location and loading order

Refer to this section if you need to control the order in which your model files are loaded.  The order is important when two or more model files refer to the same configuration, because the last model that's loaded has the highest precedence.

During domain home creation, model and property files are first loaded from the models image and then from the optional
WDT ConfigMap.

| Domain deployment model | Models image source specification                                           | Optional WDT ConfigMap specification                                      |
|-------------------------|------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| Model in Image          | `domain.spec.image` or `domain.spec.configuration.model.auxiliaryImages`   | `domain.spec.configuration.model.configMap`                                 |
| Domain on PV            | `domain.spec.configuration.initializeDomainOnPV.domain.domainCreationImages`  | `domain.spec.configuration.initializeDomainOnPV.domain.domainCreationConfigMap`|

The loading order within each of these locations is first determined using the convention `filename.##.yaml` and `filename.##.properties`, where `##` are digits that specify the desired order when sorted numerically. Additional details:

 * Embedding a `.##.` in a file name is optional.
   * When present, it must be placed just before the `properties` or `yaml` extension in order for it to take precedence over alphabetical precedence.
   * The precedence of file names that include more than one `.##.` is undefined.
   * The number can be any integer greater than or equal to zero.
 * File names that don't include `.##.` sort _before_ other files as if they implicitly have the lowest possible `.##.`  
 * If two files share the same number, the loading order is determined alphabetically as a tie-breaker.

After all the models are sorted, the operator will create a comma-separated list and this is passed to the WDT `create domain` command:

```
  /u01/wdt/models/model1.yaml,/u01/wdt/models/model2.yaml,/weblogic-operator/wdt-config-map/modela.yaml,/weblogic-operator/wdt-config-maap/mdoelb.yaml
```

Internally, the WDT `create` command will first merge all the model files into a single model file and resolve all the macros before processing the model
to create the domain.  The final merged model must be valid, both syntactically and semantically.  If
the deployment model is Model in Image, then the merged model is saved internally.


**NOTE**: If the WDT model files in the image source are supplied by combining multiple images ,
then the files in this directory are populated according to their
[Merge order]({{< relref "/managing-domains/model-in-image/auxiliary-images#multiple-images" >}})
before the loading order is determined.

For example, if you have these files in the model home directory:

```
jdbc.20.yaml
main-model.10.yaml
my-model.10.yaml
y.yaml  
```

And, you have these files in the ConfigMap:

```
jdbc-dev-urlprops.10.yaml
z.yaml
```

Then the combined model files list is passed to WebLogic Deploy Tooling as:

```
y.yaml,main-model.10.yaml,my-model.10.yaml,jdbc.20.yaml,z.yaml,jdbc-dev-urlprops.10.yaml
```

Property files (ending in `.properties`) use the same sorting algorithm, but they are appended together into a single file prior to passing them to the WebLogic Deploy Tooling.

### Model file macros

WDT models can have macros that reference secrets or environment variables.

#### Using secrets in model files

You can use WDT model `@@SECRET` macros to reference the WebLogic administrator `username` and `password` keys that are stored in a Kubernetes Secret and to optionally reference additional secrets. Here is the macro pattern for accessing these secrets:


  |Domain Resource Attribute|Corresponding WDT Model `@@SECRET` Macro|
  |---------------------|-------------|
  |`webLogicCredentialsSecret`|`@@SECRET:__weblogic-credentials__:username@@` and `@@SECRET:__weblogic-credentials__:password@@`|
  |`configuration.secrets`|`@@SECRET:mysecret:mykey@@`|

For example, you can reference the WebLogic credential user name using `@@SECRET:__weblogic-credentials__:username@@`, and you can reference a custom secret `mysecret` with key `mykey` using `@@SECRET:mysecret:mykey@@`.

Any secrets that are referenced by an `@@SECRET` macro must be deployed to the same namespace as your Domain, and must be referenced in your Domain YAML file using the `weblogicCredentialsSecret` and `configuration.secrets` fields.

Here's a sample snippet from a Domain YAML file that sets a `webLogicCredentialsSecret` and two custom secrets `my-custom-secret1` and `my-custom-secret2`.

  ```yaml
  spec:
    webLogicCredentialsSecret:
      name: my-weblogic-credentials-secret
    configuration:
      secrets: [ my-custom-secret1,my-custom-secret2 ]
  ...
  ```

#### Using environment variables in model files

You can reference operator environment variables in model files. This includes any that you define yourself in your
Domain YAML file using `domain.spec.serverPod.env` or `domain.spec.adminServer.serverPod.env`, or the built-in `DOMAIN_UID` environment variable.

For example, the `@@ENV:DOMAIN_UID@@` macro resolves to the current domain's domain UID.

#### Combining secrets and environment variables in model files

You can embed an environment variable macro in a secret macro. This is useful for referencing secrets that you've named based on your domain's `domainUID`.

For example, if your `domainUID` is `domain1`, then the macro `@@SECRET:@@ENV:DOMAIN_UID@@-super-double-secret:mykey@@` resolves to the value stored in `mykey` for secret `domain1-super-double-secret`.

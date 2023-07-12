+++
title = "Usage"
date = 2020-03-11T16:45:16-05:00
weight = 20
pre = "<b> </b>"
description = "Steps for creating and deploying Model in Image images and their associated Domain YAML files."
+++

This document describes what's needed to create and deploy a typical Model in Image domain.

{{< table_of_contents >}}

### WebLogic Kubernetes Operator

Deploy the operator and ensure that it is monitoring the desired namespace for your Model in Image domain. See [Manage operators]({{< relref "/managing-operators/_index.md" >}}) and [Quick Start]({{< relref "/quickstart/_index.md" >}}).

### WebLogic Server image

Model in Image requires an image with a WebLogic Server installation.

- You can start with WebLogic Server 12.2.1.4 or later, an Oracle Container Registry pre-built base image, such as `container-registry.oracle.com/middleware/weblogic:12.2.1.4`.
  {{% notice note %}}
  The images in `container-registry.oracle.com/middleware/weblogic` are unpatched images. You should always either use patched images from `container-registry.oracle.com/middleware/weblogic_cpu` or build your own patched images (see [Create a custom image with patches applied]({{< relref "/base-images/custom-images#create-a-custom-image-with-patches-applied" >}})).
  {{% /notice %}}

  {{% notice warning %}}
  The example base images are GA images and are suitable for demonstration and development purposes _only_ where the environments are not available from the public Internet; they are **not acceptable for production use**. In production, you should always use CPU (patched) images from [OCR]({{< relref "/base-images/ocr-images.md" >}}) or create your images using the [WebLogic Image Tool]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}}) (WIT) with the `--recommendedPatches` option. For more guidance, see [Apply the Latest Patches and Updates](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/14.1.1.0&id=LOCKD-GUID-2DA84185-46BA-4D7A-80D2-9D577A4E8DE2) in _Securing a Production Environment for Oracle WebLogic Server_.
  {{% /notice %}}



  For an example of this approach, see the [Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}}) sample. For detailed instructions on how to log in to the Oracle Container Registry and accept the license agreement for an image (required to allow pulling an Oracle Container Registry image), see this [document]({{< relref "/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}}).

- Or, you can manually build your own base image, as described in [Create a custom image with patches applied]({{< relref "/base-images/custom-images#create-a-custom-image-with-patches-applied" >}}). This is useful if you want your base images to include additional patches.

  **NOTE**: As of June, 2023, Oracle WebLogic Server 12.2.1.3 is no longer supported. The last Critical Patch Updates (CPU) images for WebLogic Server 12.2.1.3 were published in April, 2023.

### Directory structure

Model in Image requires the following directory structure in its pods for
its (optional) WDT model files and (required) WDT Home :

| Domain resource attribute  | Contents                              | Default directory                                                                                                                                                                                                                                                                                                         |
| -------------------------- | ------------------------------------- |---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `domain.spec.configuration.model.modelHome` | Zero or more model `.yaml`, `.properties`, and/or archive `.zip` files. | Optional. Location of the WDT model home, which can include model YAML files, `.properties` files, and application `.zip` archives. Defaults to `/u01/wdt/models` if no [Auxiliary Images]({{<relref "/managing-domains/model-in-image/auxiliary-images.md" >}}) are configured, and to `/aux/models` otherwise. |
| `domain.spec.configuration.model.wdtInstallHome` | Unzipped WDT installation (required).  | Optional. Location of the WDT Home. Defaults to `/u01/wdt/weblogic-deploy` if no [Auxiliary Images]({{<relref "/managing-domains/model-in-image/auxiliary-images" >}}) are configured, and to `/aux/weblogic-deploy` otherwise.                                                                          |

{{% notice note %}}
If you set `modelHome` and `wdtInstallHome` to a non-default value,
then the operator will ignore WDT model files and WDT Home
that are copied from [Auxiliary Images]({{<relref "/managing-domains/model-in-image/auxiliary-images" >}}).
{{% /notice %}}

### Supplying initial WDT model files and WDT Home

Model in Image minimally requires an image with a WebLogic installation
(see [WebLogic Server image](#weblogic-server-image)), plus access
to:
* A WDT installation in `domain.spec.configuration.model.wdtInstallHome`.
* One or more WDT model `.yaml` files that configure your domain in
  the `domain.spec.configuration.model.modelHome` directory
  or in the [optional WDT model ConfigMap](#optional-wdt-model-configmap).
* Zero or more WDT model `.properties` files in the
  `domain.spec.configuration.model.modelHome` directory
  or in the [optional WDT model ConfigMap](#optional-wdt-model-configmap).
* Zero or more WDT model application `.zip` archives
  in the `domain.spec.configuration.model.modelHome` directory. Archives
  must be supplied in the model home because application archives
  are not supported in the [optional WDT model ConfigMap](#optional-wdt-model-configmap).

There are multiple methods for supplying Model in Image WDT models files, WDT variables files, and WDT archive files (collectively known as WDT model files):

  - Use auxiliary images:
    Use [auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}})
    to create one or more small images that contain the desired files.

    This is the recommended best approach. It automatically copies files
    from each of the small images into the `/aux/models` and `/aux/weblogic-deploy` directories
    in each pod's file system so that the introspection job can find them.

  - Include in the main image:
    You can include the WDT model files in your domain resource `domain.spec.image`
    in its `domain.spec.configuration.model.modelHome`
    and `domain.spec.configuration.model.wdtInstallHome` directories as
    a layer on top of your base image
    (where the base image includes your WebLogic installation).

    **NOTE**: Model in Image without auxiliary images (the WDT model and installation files are included in the same image with the WebLogic Server installation) is deprecated in WebLogic Kubernetes Operator version 4.0.7. Oracle recommends that you use Model in Image _with_ auxiliary images. See [Auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).

    Use either of the following methods.

    - _Manual image creation_ uses Docker commands to layer the WDT models files, described in the previous table,
      on top of your base image into a new image.
    - The _WebLogic Image Tool_ (WIT) has built-in options for layering WDT model files,
      WDT installation, WebLogic Server installation, and WebLogic Server patches in an image.
      See [Create a custom image with your model inside the image]({{< relref "/base-images/custom-images#create-a-custom-image-with-your-model-inside-the-image" >}}).

  - Use a Persistent Volume Claim (PVC):
    This method is for advanced use cases only. Supply WDT model YAML, variables, or archive files
    in a [Persistent Volume Claim]({{< relref "/managing-domains/persistent-storage/volumes.md" >}})
    and modify `configuration.model.modelHome` and `configuration.model.wdtInstallHome` to
    the corresponding directory within the PVC's mount location.

  - Use a WDT model ConfigMap:
    Use the [Optional WDT model ConfigMap](#optional-wdt-model-configmap) for
    WDT model YAML and `.properties` files. This can be combined with
    any of the previously mentioned methods and is most often used to facilitate runtime
    updates to models supplied by one of these methods.

For more information about model file syntax,
see [Working with WDT model files]({{< relref "/managing-domains/model-in-image/model-files.md" >}}).

### Optional WDT model ConfigMap

You can create a WDT model ConfigMap that defines additional model `.yaml` and `.properties` files beyond what you've already supplied in your image, and then reference this ConfigMap using your Domain YAML file's `configuration.model.configMap` attribute. This is optional if the supplied image already fully defines your model.

WDT model ConfigMap files will be merged with the WDT files defined in your image at runtime before your domain home is created. The ConfigMap files can add to, remove from, or alter the model configuration that you supplied within your image.

For example, place additional `.yaml` and `.properties` files in a directory called `/home/acmeuser/wdtoverride` and run the following commands:

  ```shell
  $ kubectl -n MY-DOMAIN-NAMESPACE \
    create configmap MY-DOMAINUID-my-wdt-config-map \
    --from-file /home/acmeuser/wdtoverride
  ```
  ```shell
  $ kubectl -n MY-DOMAIN-NAMESPACE \
    label  configmap MY-DOMAINUID-my-wdt-config-map \
    weblogic.domainUID=MY-DOMAINUID
  ```

See [Working with WDT model files]({{< relref "/managing-domains/model-in-image/model-files.md" >}}) for a description of model file syntax and loading order, and see [Runtime updates]({{< relref "/managing-domains/model-in-image/runtime-updates.md" >}}) for a description of using WDT model ConfigMaps to update the model configuration of a running domain.


### Required runtime encryption secret

Model in Image requires a runtime encryption secret with a secure `password` key. This secret is used by the operator to encrypt model and domain home files before it adds them to a runtime ConfigMap or log. You can safely change the `password`, at any time after you've fully shut down a domain, but it must remain the same for the life of a running domain. The runtime encryption secret that you create can be named anything, but note that it is a best practice to name and label secrets with their domain UID to help ensure that cleanup scripts can find and delete them.

**NOTE**: Because the runtime encryption password does not need to be shared and needs to exist only for the life of a domain, you may want to use a password generator.

Example:

  ```shell
  $ kubectl -n MY-DOMAIN-NAMESPACE \
    create secret generic MY-DOMAINUID-runtime-encrypt-secret \
    --from-literal=password=MY-RUNTIME-PASSWORD
  ```
  ```shell
  $ kubectl -n MY-DOMAIN-NAMESPACE \
    label secret MY-DOMAINUID-runtime-encrypt-secret \
    weblogic.domainUID=MY-DOMAINUID
  ```

Corresponding Domain YAML file snippet:

  ```yaml
  configuration:
    model:
      runtimeEncryptionSecret: MY-DOMAINUID-runtime-encrypt-secret
  ```

### Secrets for model macros

Create additional secrets as needed by macros in your model files. For example, these can store database URLs and credentials that are accessed using `@@SECRET` macros in your model that reference the secrets.  For a description of model macros, see [Model files]({{< relref "/managing-domains/model-in-image/model-files.md" >}}).

### Domain fields

The following Domain fields are specific to Model in Image domains.

| Domain Resource Attribute                    | Notes                                                                                                                                                                                                                                                                                                                  |
| -------------------------                    |------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `domainHomeSourceType`                       | Required. Set to `FromModel`.                                                                                                                                                                                                                                                                                          |
| `domainHome`                                 | Must reference an empty or non-existent directory within your image. Do not include the mount path of any persistent volume. Note that Model in Image recreates the domain home for a WebLogic Server pod every time the pod restarts.                                                                                 |
| `configuration.model.configMap`              | Optional. Set if you have stored additional models in a ConfigMap as per [Optional WDT model ConfigMap](#optional-wdt-model-configmap).                                                                                                                                                                                |
| `configuration.secrets`                      | Optional. Set this array if your image or ConfigMap models contain macros that reference custom Kubernetes Secrets. For example, if your macros depend on secrets `my-secret` and `my-other-secret`, then set to `[my-secret, my-other-secret]`.                                                                       |
| `configuration.model.runtimeEncryptionSecret`| Required. All Model in Image domains must specify a runtime encryption secret. See [Required runtime encryption secret](#required-runtime-encryption-secret).                                                                                                                                                          |
| `configuration.model.domainType`             | Set the type of domain. `WLS` is the default. See [WDT Domain Types](https://oracle.github.io/weblogic-deploy-tooling/userguide/tools-config/domain_def/).                                                                                                                                                             |
| `configuration.model.runtimeEncryptionSecret`| Required. All Model in Image domains must specify a runtime encryption secret. See [Required runtime encryption secret](#required-runtime-encryption-secret).                                                                                                                                                          |
| `configuration.model.modelHome`              | Optional. Location of the WDT model home, which can include model YAML files, `.properties` files, and application `.zip` archives. Defaults to `/u01/wdt/models` if no [Auxiliary Images]({{<relref "/managing-domains/model-in-image/auxiliary-images" >}}) are configured, and to `/aux/models` otherwise. |
| `configuration.model.wdtInstallHome`         | Optional. Location of the WDT Home . Defaults to `/aux/weblogic-deploy` when [Auxiliary Images]({{<relref "/managing-domains/model-in-image/auxiliary-images" >}}) are configured, otherwise to `/u01/wdt/weblogic-deploy`.                                                                       |

{{% notice note %}}
If you set `modelHome` and `wdtInstallHome` to a non-default value,
then the operator will ignore WDT model files and WDT Home
that are copied from [Auxiliary Images]({{<relref "/managing-domains/model-in-image/auxiliary-images" >}}).
{{% /notice %}}

**NOTES**:

 - There are additional attributes that are common to all domain home source types, such as the `image` or `clusters` field. See the Domain Resource [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md) and [documentation]({{< relref "/managing-domains/domain-resource.md" >}}) for a full list of Domain fields.

 - For fully specified Model in Image Domain YAML file examples,
   see the [`kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources`](https://github.com/oracle/weblogic-kubernetes-operator/tree/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources) GitHub directory for the [Model in Image sample]({{< relref "/samples/domains/model-in-image/_index.md" >}}).

### Always use external state

Regardless of the domain home source type, we recommend that you always keep state outside the image. This includes cluster database leasing tables, JMS and transaction stores, EJB timers, and so on. This ensures that data will not be lost when a container is destroyed.

We recommend that state be kept in a database to take advantage of built-in database server high availability features, and the fact that disaster recovery of sites across all but the shortest distances, almost always requires using a single database server to consolidate and replicate data (DataGuard).

For more information see:
- [Tuning JDBC Stores](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/perfm/storetune.html#GUID-F868624F-2898-4330-AF96-FC0EAB3CFE0A) in _Tuning Performance of Oracle WebLogic Server_.
- [Using a JDBC Store](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/fusion-middleware/weblogic-server/12.2.1.4/perfm&id=STORE-GUID-328FD4C1-91C4-4901-B1C4-97B9D93B2ECE) in _Administering the WebLogic Persistent Store_.
- [High Availability Best Practices](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/jmsad/best_practice.html#GUID-FB97F9A2-E6FA-4C51-B74E-A2A5DDB43B8C) in _Administering JMS Resources for Oracle WebLogic Server_.
- [Leasing](https://docs.oracle.com/pls/topic/lookup?ctx=en/middleware/fusion-middleware/weblogic-server/12.2.1.4/jmsad&id=CLUST-GUID-8F7348E2-7C45-4A6B-A72C-D1FB51A8E83F) in _Administering Clusters for Oracle WebLogic Server_.

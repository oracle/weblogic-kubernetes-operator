+++
title = "Domain WDT artifacts images"
date = 2019-02-23T16:45:16-05:00
weight = 25
pre = "<b> </b>"
description = "WDT artifacts images are used to supply the WDT artifacts for Model in image and Domain on PV."
+++

{{< table_of_contents >}}

### Introduction

Domain WDT artifacts images are the recommended best approach for supplying,
application archive files, and WebLogic Deploy Tooling installation files for operator managed domains.

You will:
- Use or patch a WebLogic installation image.
- Share one WebLogic installation image with multiple different model
  configurations that are supplied in specific images.
- Distribute or update model files, application archives, and the
  WebLogic Deploy Tooling executable using specific images
  that do not contain a WebLogic installation.


### References

- For Model in image, run the `kubectl explain domain.spec.configuration.model.auxiliaryImages` command.

  - See the `model.auxiliaryImages` section
    in the domain resource
    [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md).

- For Domain on PV, run the ` kubectl explain domain.spec.configuration.initializeDomainOnPV.domain.domainCreationImages` command.

  - See the `initializeDomainOnPV.domain.domainCreationImages` section
    in the domain resource
    [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md).

### Configuration

Beginning with operator version 4.10, you can configure one or more WDT artifacts images in a domain resource.

Each array entry must define an `image` which is the name of an auxiliary image.
Optionally, you can set the `imagePullPolicy`,
which defaults to `Always` if the `image` ends in `:latest` and `IfNotPresent`,
otherwise.
If image pull secrets are required for pulling auxiliary images, then the secrets must be referenced using `domain.spec.imagePullSecrets`.

Also, optionally, you can configure the [source locations](#source-locations) of the WebLogic Deploy Tooling model
and installation files in the auxiliary image using the `sourceModelHome` and `sourceWDTInstallHome` fields, described in the following
[section](#source-locations).  

- For details about each field, see the [References](#references)

- For a basic configuration example, see [Configuration example 1](#example-1-basic-configuration).

#### Source locations

Use the optional attributes `... sourceModelHome` and
`... .sourceWdtInstallHome` to specify non-default locations of
WebLogic Deploy Tooling model and installation files in your image(s).
Allowed values for `sourceModelHome` and `sourceWdtInstallHome`:
- Unset - Defaults to `/auxiliary/models` and `/auxiliary/weblogic-deploy`, respectively.
- Set to a path - Must point to an existing location containing WDT model and installation files, respectively.
- `None` - Indicates that the image has no WDT models or installation files, respectively.

If you set the `sourceModelHome` or `sourceWDTInstallHome` to `None` or,
the source attributes are left unset and there are no files at the default locations,
then the operator will ignore the source directories. Otherwise,
note that if you set a source directory attribute to a specific value
and there are no files in the specified directory in the auxiliary image,
then the domain deployment will fail.

The files in `sourceModelHome` and `sourceWDTInstallHome` directories will be made available in `/aux/models`
and `/aux/weblogic-deploy` directories of the WebLogic Server container in all pods, respectively.

For example source locations, see [Configuration example 2](#example-2-source-locations).

#### Multiple auxiliary images

If specifying multiple images with model files in their respective `sourceModelHome`
directories, then model files are merged.
The operator will merge the model files from multiple images in the same order in which images appear under `model.auxiliaryImages` or `domain.domainCreationImages`.
Files from later images in the merge overwrite same-named files from earlier images.

When specifying multiple images, ensure that only one of the images supplies a WDT installation location using
`sourceWDTInstallHome`.
{{% notice warning %}}
If you provide more than one WDT installation home among multiple images,
then the domain deployment will fail.
Set `sourceWDTInstallHome` to `None`, or make sure there are no files in `/auxiliary/weblogic-deploy`,
for all but one of your specified auxililary images.
{{% /notice %}}

For an example of configuring multiple auxiliary images for Model in image, see [Configuration example 3](#example-3-multiple-images).

#### Model and WDT installation homes

If you are using auxiliary images in Model in image, typically, it should not be necessary to set `domain.spec.configuration.models.modelHome` and
`domain.spec.configuration.models.wdtInstallHome`. The model and WDT install files you supply in the auxiliary image
(see [source locations](#source-locations)) are always placed in the `/aux/models` and `/aux/weblogic-deploy` directories,
respectively, in all WebLogic Server pods. When auxiliary image(s) are configured, the operator automatically changes
the default for `modelHome` and `wdtInstallHome` to match.

{{% notice warning %}}
If you set `modelHome` and `wdtInstallHome` to a non-default value,
then the domain will ignore the WDT model and installation files in its auxiliary image(s).
{{% /notice %}}

### Configuration examples

The following configuration examples illustrate each of the previously described sections.

#### Example 1: Basic configuration

This example specifies the required image parameter for the auxiliary image(s); all other fields are at default values.

For Model in image:
```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: wdt-model-images:v1
```

For Domain on PV:
```
spec:
  configuration:
    initializeDomainOnPV:
      domainCreationImages:
      - image: wdt-model-images:v1
```

#### Example 2: Source locations

This example is same as Example 1 except that it specifies the source locations for the WebLogic Deploy Tooling model and installation files.

For Model in image:
```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: wdt-model-images:v1
        sourceModelHome: /foo/models
        sourceWDTInstallHome: /bar/weblogic-deploy
```

For Domain on PV:
```
spec:
  configuration:
  configuration:
    initializeDomainOnPV:
      domainCreationImages:
      - image: wdt-model-images:v1
        sourceModelHome: /foo/models
        sourceWDTInstallHome: /bar/weblogic-deploy
```

#### Example 3: Multiple images

This example is the same as Example 1, except it configures multiple auxiliary images and sets the `sourceWDTInstallHome`
for the second image to `None`.
In this case, the source location of the WebLogic Deploy Tooling installation from the second image `new-model-in-image:v1` will be ignored.

For Model in image:
```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: wdt-model-images:v1
      - image: wdt-model-images2:v1
        sourceWDTInstallHome: None
```

For Domain on PV:
```
spec:
  configuration:
    initializeDomainOnPV:
      domainCreationImages:
      - image: wdt-model-images:v1
      - image: wdt-model-images2:v1
        sourceWDTInstallHome: None
```


### Sample   (TODO: do we still ned this from this till the end?)

The [Model in Image Sample]({{< relref "/samples/domains/model-in-image/initial.md" >}})
demonstrates deploying a Model in Image domain that uses
auxiliary images to supply the domain's WDT model files,
application archive ZIP files, and WDT installation in a small, separate
container image.


### Automated upgrade of the `weblogic.oracle/v8` schema auxiliary images configuration

{{% notice note %}}
The automated upgrade described in this section converts `weblogic.oracle/v8` schema auxiliary image configuration into low-level Kubernetes schema, for example, init containers and volumes.
Instead of relying on the generated low-level schema, Oracle recommends using a simplified `weblogic.oracle/v9` schema configuration for auxiliary images, as documented in the [Configuration](#configuration) section.
{{% /notice %}}

In operator version 4.0, we have enhanced auxiliary images to improve ease of use; also, its configuration has changed from operator 3.x releases.

Operator 4.0 provides a seamless upgrade of Domains with `weblogic.oracle/v8` schema auxiliary images configuration. When you create a Domain with auxiliary images using `weblogic.oracle/v8` schema in a namespace managed by the 4.0 operator, the [WebLogic Domain resource conversion webhook]({{< relref "/managing-operators/conversion-webhook.md" >}}) performs an automated upgrade of the domain resource to the `weblogic.oracle/v9` schema. The conversion webhook runtime converts the `weblogic.oracle/v8` auxiliary image configuration to the equivalent configuration using init containers, volume and volume mounts under the `serverPod` spec in `weblogic.oracle/v9`. Similarly, when [upgrading the operator]({{< relref "/managing-operators/installation#upgrade-the-operator" >}}), Domains with `weblogic.oracle/v8` schema auxiliary images are seamlessly upgraded.

The following is a sample `weblogic.oracle/v8` schema auxiliary image configuration in operator 3.x and
the equivalent `weblogic.oracle/v9` schema configuration generated by the conversion webhook in operator 4.0.

#### Sample `weblogic.oracle/v8` schema auxiliary image configuration

```
spec:
  auxiliaryImageVolumes:
  - name: auxiliaryImageVolume1
    mountPath: "/auxiliary"

  serverPod:
    auxiliaryImages:
    - image: "model-in-image:WLS-AI-v1"
      imagePullPolicy: IfNotPresent
      volume: auxiliaryImageVolume1
```

#### Compatibility `weblogic.oracle/v9` schema auxiliary image configuration generated by conversion webhook in operator 4.0

```
  serverPod:
    initContainers:
    - command:
      - /weblogic-operator/scripts/auxImage.sh
      env:
      - name: AUXILIARY_IMAGE_PATH
        value: /auxiliary
      - name: AUXILIARY_IMAGE_TARGET_PATH
        value: /tmpAuxiliaryImage
      - name: AUXILIARY_IMAGE_COMMAND
        value: cp -R $AUXILIARY_IMAGE_PATH/* $AUXILIARY_IMAGE_TARGET_PATH
      - name: AUXILIARY_IMAGE_CONTAINER_IMAGE
        value: model-in-image:WLS-AI-v1
      - name: AUXILIARY_IMAGE_CONTAINER_NAME
        value: compat-operator-aux-container1
      image: model-in-image:WLS-AI-v1
      imagePullPolicy: IfNotPresent
      name: compat-operator-aux-container1
      volumeMounts:
      - mountPath: /tmpAuxiliaryImage
        name: compat-ai-vol-auxiliaryimagevolume1
      - mountPath: /weblogic-operator/scripts
        name: weblogic-scripts-cm-volume
    volumeMounts:
    - mountPath: /auxiliary
      name: compat-ai-vol-auxiliaryimagevolume1
    volumes:
    - emptyDir: {}
      name: compat-ai-vol-auxiliaryimagevolume1
```

{{% notice note %}}
The conversion webhook runtime creates init containers with names prefixed with `compat-` when converting the auxiliary image configuration of the `weblogic.oracle/v8` schema. The operator generates only init containers with names starting with either `compat-` or `wls-shared-` in the introspector job pod. To alter the generated init container's name, the new name must start with either `compat-` or `wls-shared-`.
{{% /notice %}}

### Domain upgrade tool to manually upgrade the `weblogic.oracle/v8` schema domain resource

To manually upgrade the domain resource from the `weblogic.oracle/v8` schema to the `weblogic.oracle/v9` schema, see [Upgrade the `weblogic.oracle/v8` schema domain resource manually]({{< relref "/managing-domains/upgrade-domain-resource#upgrade-the-weblogicoraclev8-schema-domain-resource-manually" >}}).


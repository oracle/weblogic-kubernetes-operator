+++
title = "Domain WDT artifacts images"
date = 2019-02-23T16:45:16-05:00
weight = 25
pre = "<b> </b>"
description = "WDT artifacts images are used to supply the WDT artifacts for Model in image and Domain on PV."
+++

{{< table_of_contents >}}

### Introduction

Domain WDT artifacts images are the recommended best approach for supplying model files, variable files,
application archive files, and WebLogic Deploy Tooling (WDT) installation files when deploying a domain using
Model in image or Domain on PV model.  You will distribute model files, application archives, and the
WebLogic Deploy Tooling executable using these images.  Operator will use the WDT tool and artifacts to 
manage the domain according the the domain source type `Model in image` or `Domain on PV` (operator version 4.10 or newer).  

**Note:**  These image(s) are managed differently in each deployment model.  For `Model in image` it is used throughout the domain life-cycle,
for `Domain on PV`, they are  _only_ used for creating the domain, and will not be used to update the domain.

### Configuration

You can configure one or more WDT artifacts images in a domain resource.

Optionally, you can set the `imagePullPolicy`,
which defaults to `Always` if the `image` ends in `:latest` and `IfNotPresent`,
otherwise.
If image pull secrets are required for pulling the images, then the secrets must be referenced using `domain.spec.imagePullSecrets`.

Also, optionally, you can configure the [source locations](#source-locations) of the WebLogic Deploy Tooling model
and installation files in the image using the `sourceModelHome` and `sourceWDTInstallHome` fields, described in the following
[section](#source-locations).  

- For details about each field, see the [References](#references)

- For a basic configuration example, see [Configuration example 1](#example-1-basic-configuration).

### References

- For Model in image, run the `kubectl explain domain.spec.configuration.model.auxiliaryImages` command or.

  - See the `model.auxiliaryImages` section
    in the domain resource
    [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md).

- For Domain on PV (operator version 4.10 or newer), run the ` kubectl explain domain.spec.configuration.initializeDomainOnPV.domain.domainCreationImages` command or

  - See the `initializeDomainOnPV.domain.domainCreationImages` section
    in the domain resource
    [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md).


#### Source locations

Use the optional attributes `sourceModelHome` and
`sourceWdtInstallHome` to specify non-default locations of
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

#### Multiple  images

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

For an example of configuring multiple images, see [Configuration example 3](#example-3-multiple-images).

#### Model and WDT installation homes

For Model in image:

- It should not be necessary to set `domain.spec.configuration.models.modelHome` and
`domain.spec.configuration.models.wdtInstallHome`. The model and WDT install files you supply in the image
(see [source locations](#source-locations)) are always placed in the `/aux/models` and `/aux/weblogic-deploy` directories,
respectively, in all WebLogic Server pods. The operator automatically changes the default for `modelHome` and `wdtInstallHome` to match.

- {{% notice warning %}}
If you set `modelHome` and `wdtInstallHome` to a non-default value,
then the domain will ignore the WDT model and installation files in its auxiliary image(s).
{{% /notice %}}

### Configuration examples

The following configuration examples illustrate each of the previously described sections.

#### Example 1: Basic configuration

This example specifies the image location; all other fields are at default values.

For Model in image:
```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: wdt-model-image:v1
```

For Domain on PV:
```
spec:
  configuration:
    initializeDomainOnPV:
      domainCreationImages:
      - image: wdt-model-image:v1
```

#### Example 2: Source locations

This example is same as Example 1 except that it specifies the source locations for the WebLogic Deploy Tooling model and installation files.

For Model in image:
```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: wdt-model-image:v1
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
      - image: wdt-model-image:v1
        sourceModelHome: /foo/models
        sourceWDTInstallHome: /bar/weblogic-deploy
```

#### Example 3: Multiple images

This example is the same as Example 1, except it configures multiple images and sets the `sourceWDTInstallHome`
for the second image to `None`.
In this case, the source location of the WebLogic Deploy Tooling installation from the second image `wdt-model-image2:v1` will be ignored.

For Model in image:
```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: wdt-model-image:v1
      - image: wdt-model-image2:v1
        sourceWDTInstallHome: None
```

For Domain on PV:
```
spec:
  configuration:
    initializeDomainOnPV:
      domainCreationImages:
      - image: wdt-model-image:v1
      - image: wdt-model-image2:v1
        sourceWDTInstallHome: None
```



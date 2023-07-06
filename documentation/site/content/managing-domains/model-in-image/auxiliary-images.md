+++
title = "Auxiliary images"
date = 2019-02-23T16:45:16-05:00
weight = 25
pre = "<b> </b>"
description = "Auxiliary images are an alternative approach for supplying a domain's model files or other types of files."
+++

{{< table_of_contents >}}

### Introduction

Auxiliary images are the recommended best approach for including Model in Image model files,
application archive files, and the WebLogic Deploy Tooling installation, in your pods.
This feature eliminates the need to provide these files in the image specified
in `domain.spec.image`.

Instead:

- The domain resource's `domain.spec.image` directly references a base image
  that needs to include only a WebLogic installation and a Java installation.
- The domain resource's auxiliary image related fields reference one or
  more smaller images that contain the desired Model in Image files.

The advantages of auxiliary images for Model In Image domains are:

- Use or patch a WebLogic installation image without needing to include a WDT installation,
  application archive, or model artifacts within the image.
- Share one WebLogic installation image with multiple different model
  configurations that are supplied in specific images.
- Distribute or update model files, application archives, and the
  WebLogic Deploy Tooling executable using specific images
  that do not contain a WebLogic installation.

Auxiliary images internally
use a Kubernetes `emptyDir` volume and Kubernetes `init` containers to share files
from additional images.

### References

- Run the `kubectl explain domain.spec.configuration.model.auxiliaryImages` command.

- See the `model.auxiliaryImages` section
  in the domain resource
  [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md).

### Configuration

Beginning with operator version 4.0, you can configure one or more auxiliary images in a domain resource
`configuration.model.auxiliaryImages` array.
Each array entry must define an `image` which is the name of an auxiliary image.
Optionally, you can set the `imagePullPolicy`,
which defaults to `Always` if the `image` ends in `:latest` and `IfNotPresent`,
otherwise.
If image pull secrets are required for pulling auxiliary images, then the secrets must be referenced using `domain.spec.imagePullSecrets`.

Also, optionally, you can configure the [source locations](#source-locations) of the WebLogic Deploy Tooling model
and the directory where the WebLogic Deploy Tooling software is installed (known as the WDT Home) in the auxiliary image using the `sourceModelHome` and `sourceWDTInstallHome` fields, described in the following
[section](#source-locations).  

- For details about each field, see the
[schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md#auxiliary-image).

- For a basic configuration example, see [Configuration example 1](#example-1-basic-configuration).

#### Source locations

Use the optional attributes `configuration.model.auxiliaryImages[].sourceModelHome` and
`configuration.model.auxiliaryImages[].sourceWdtInstallHome` to specify non-default locations of
WebLogic Deploy Tooling model and WDT Home in your auxiliary image(s).
Allowed values for `sourceModelHome` and `sourceWdtInstallHome`:
- Unset - Defaults to `/auxiliary/models` and `/auxiliary/weblogic-deploy`, respectively.
- Set to a path - Must point to an existing location containing WDT model files and WDT Home, respectively.
- `None` - Indicates that the image has no WDT models or WDT Home, respectively.

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

If specifying multiple auxiliary images with model files in their respective `configuration.model.auxiliaryImages[].sourceModelHome`
directories, then model files are merged.
The operator will merge the model files from multiple auxiliary images in the same order in which images appear under `model.auxiliaryImages`.
Files from later images in the merge overwrite same-named files from earlier images.

When specifying multiple auxiliary images, ensure that only one of the images supplies a WDT Home using
`configuration.model.auxiliaryImages[].sourceWDTInstallHome`.
{{% notice warning %}}
If you provide more than one WDT Home among multiple auxiliary images,
then the domain deployment will fail.
Set `sourceWDTInstallHome` to `None`, or make sure there are no files in `/auxiliary/weblogic-deploy`,
for all but one of your specified auxililary images.
{{% /notice %}}

For an example of configuring multiple auxiliary images, see [Configuration example 3](#example-3-multiple-images).

#### Model and WDT installation homes

If you are using auxiliary images, typically, it should not be necessary to set `domain.spec.configuration.models.modelHome` and
`domain.spec.configuration.models.wdtInstallHome`. The model and WDT installation you supply in the auxiliary image
(see [source locations](#source-locations)) are always placed in the `/aux/models` and `/aux/weblogic-deploy` directories,
respectively, in all WebLogic Server pods. When auxiliary image(s) are configured, the operator automatically changes
the default for `modelHome` and `wdtInstallHome` to match.

{{% notice warning %}}
If you set `modelHome` and `wdtInstallHome` to a non-default value,
then the domain will ignore the WDT model files and WDT Home in its auxiliary image(s).
{{% /notice %}}

### Configuration examples

The following configuration examples illustrate each of the previously described sections.

#### Example 1: Basic configuration

This example specifies the required image parameter for the auxiliary image(s); all other fields are at default values.

```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: model-in-image:v1
```

#### Example 2: Source locations

This example is same as Example 1 except that it specifies the source locations for the WebLogic Deploy Tooling model and WDT Home.
```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: model-in-image:v1
        sourceModelHome: /foo/models
        sourceWDTInstallHome: /bar/weblogic-deploy
```

#### Example 3: Multiple images

This example is the same as Example 1, except it configures multiple auxiliary images and sets the `sourceWDTInstallHome`
for the second image to `None`.
In this case, the source location of the WebLogic Deploy Tooling installation from the second image `new-model-in-image:v1` will be ignored.

```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: model-in-image:v1
      - image: new-model-in-image:v1
        sourceWDTInstallHome: None
```

### Sample

The [Model in Image Sample]({{< relref "/samples/domains/model-in-image/initial.md" >}})
demonstrates deploying a Model in Image domain that uses
auxiliary images to supply the domain's WDT model files,
application archive ZIP files, and WDT installation in a small, separate
container image.

### Using Docker to create an auxiliary image

The [Model in Image Sample initial use case]({{< relref "/samples/domains/model-in-image/initial.md" >}})
describes using the WebLogic Image Tool as a convenient way to create the auxiliary image,
which is the recommended best approach. Alternatively, you can "manually" build the image.
For example, the following steps modify the Model in Image sample's initial use case
to use Docker to build its auxiliary image:

1. Download the Model in Image sample source and WebLogic Deploy Tooling by following
   the corresponding steps in the
   [Model in Image Sample prerequisites]({{< relref "/samples/domains/model-in-image/prerequisites.md" >}}).

1. Create a `/tmp/mystaging/models` directory as a staging directory and copy the model YAML file, properties, and archive into it:
   ```shell
   $ mkdir -p /tmp/mystaging/models
   $ cp /tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1/model.10.yaml ./models
   $ cp /tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1/model.10.properties ./models
   $ cp /tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1/archive.zip ./models
   ```
   If the `archive.zip` file is missing, then repeat the step to create this file
   in the Model in Image sample
   [initial use case]({{< relref "/samples/domains/model-in-image/initial.md#staging-a-zip-file-of-the-archive" >}})
   while using `/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1` as the target directory.

1. Install WDT in the staging directory and remove its `weblogic-deploy/bin/*.cmd` files,
   which are not used in UNIX environments:
   ```shell
   $ cd /tmp/mystaging
   $ unzip /tmp/mii-sample/model-images/weblogic-deploy.zip -d .
   $ rm ./weblogic-deploy/bin/*.cmd
   ```
   If the `weblogic-deploy.zip` file is missing, then repeat the step to download the latest WebLogic Deploy Tooling (WDT)
   in the Model in Image sample [prerequisites]({{< relref "/samples/domains/model-in-image/prerequisites.md" >}}).

1. Run the `docker build` command using `/tmp/mii-sample/ai-docker-file/Dockerfile`.

   ```shell
   $ cd /tmp/mystaging
   $ docker build -f /tmp/mii-sample/ai-docker-file/Dockerfile \
     --build-arg AUXILIARY_IMAGE_PATH=/auxiliary \
     --tag model-in-image:WLS-v1 .
   ```

   See `./Dockerfile` for an explanation of each build argument.

   {{%expand "Click here to view the Dockerfile." %}}
   ```
   # Copyright (c) 2021, 2022, Oracle and/or its affiliates.
   # Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

   # This is a sample Dockerfile for supplying Model in Image model files
   # and a WDT installation in a small separate auxiliary image
   # image. This is an alternative to supplying the files directly
   # in the domain resource `domain.spec.image` image.

   # AUXILIARY_IMAGE_PATH arg:
   #   Parent location for Model in Image model and WDT Home.
   #   The default is '/auxiliary', which matches the parent directory in the default values for
   #   'domain.spec.configuration.model.auxiliaryImages.sourceModelHome' and
   #   'domain.spec.configuration.model.auxiliaryImages.sourceWDTInstallHome', respectively.
   #

   FROM busybox
   ARG AUXILIARY_IMAGE_PATH=/auxiliary
   ARG USER=oracle
   ARG USERID=1000
   ARG GROUP=root
   ENV AUXILIARY_IMAGE_PATH=${AUXILIARY_IMAGE_PATH}
   RUN adduser -D -u ${USERID} -G $GROUP $USER
   # ARG expansion in COPY command's --chown is available in docker version 19.03.1+.
   # For older docker versions, change the Dockerfile to use separate COPY and 'RUN chown' commands.
   COPY --chown=$USER:$GROUP ./ ${AUXILIARY_IMAGE_PATH}/
   USER $USER
   ```
   {{% /expand %}}

1. If you have successfully created the image, then it should now be in your local machine's Docker repository. For example:

   ```
   $ docker images model-in-image:WLS-v1
   REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
   model-in-image      WLS-v1           eac9030a1f41        1 minute ago        4.04MB
   ```

1. After the image is created, it should have the WDT executables in
   `/auxiliary/weblogic-deploy`, and WDT model, property, and archive
   files in `/auxiliary/models`. You can run `ls` in the Docker
   image to verify this:

   ```shell
   $ docker run -it --rm model-in-image:WLS-v1 ls -l /auxiliary
     total 8
     drwxr-xr-x    1 oracle   root          4096 Jun  1 21:53 models
     drwxr-xr-x    1 oracle   root          4096 May 26 22:29 weblogic-deploy

   $ docker run -it --rm model-in-image:WLS-v1 ls -l /auxiliary/models
     total 16
     -rw-rw-r--    1 oracle   root          5112 Jun  1 21:52 archive.zip
     -rw-rw-r--    1 oracle   root           173 Jun  1 21:59 model.10.properties
     -rw-rw-r--    1 oracle   root          1515 Jun  1 21:59 model.10.yaml

   $ docker run -it --rm model-in-image:WLS-v1 ls -l /auxiliary/weblogic-deploy
     total 28
     -rw-r-----    1 oracle   root          4673 Oct 22  2019 LICENSE.txt
     -rw-r-----    1 oracle   root            30 May 25 11:40 VERSION.txt
     drwxr-x---    1 oracle   root          4096 May 26 22:29 bin
     drwxr-x---    1 oracle   root          4096 May 25 11:40 etc
     drwxr-x---    1 oracle   root          4096 May 25 11:40 lib
     drwxr-x---    1 oracle   root          4096 Jan 22  2019 samples
   ```

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
    - image: "model-in-image:WLS-v1"
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
        value: model-in-image:WLS-v1
      - name: AUXILIARY_IMAGE_CONTAINER_NAME
        value: compat-operator-aux-container1
      image: model-in-image:WLS-v1
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

---
title: "Create custom images"
date: 2019-02-23T16:45:55-05:00
weight: 2
description: "Create custom images and apply patches."
---

### Contents

- [Use the WebLogic Image Tool to create custom images](#use-the-weblogic-image-tool-to-create-custom-images)
- [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)
  - [Create a custom base image](#create-a-custom-base-image)
  - [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image)
  - [Create a custom image with your model inside the image](#create-a-custom-image-with-your-model-inside-the-image)
- [Apply patched images to a running domain](#apply-patched-images-to-a-running-domain)
  - [Domain in PV](#domain-in-pv)
  - [Model in Image with auxiliary images](#model-in-image-with-auxiliary-images)
  - [Model in Image without auxiliary images](#model-in-image-without-auxiliary-images)
  - [Domain in Image](#domain-in-image)

### Use the WebLogic Image Tool to create custom images

To create custom images for your domain resource, you can use the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/).
Download and install the WebLogic Image Tool (WIT) following the WIT [Setup](https://oracle.github.io/weblogic-image-tool/userguide/setup/) instructions.
Also, refer to the WIT [Quick Start](https://oracle.github.io/weblogic-image-tool/quickstart/quickstart/) Guide.

The WebLogic Image Tool `create`, `update`, or `rebase` commands supply
three different ways to generate a custom WebLogic Server installation image
from a base OS image (optionally, with WebLogic patches).

In addition, the WIT `createAuxImage` command
supports creating auxiliary images which
do _not_ contain a WebLogic Server installation,
and instead, solely contain the [WebLogic Deploy Tool](https://oracle.github.io/weblogic-deploy-tooling/) binary, model, or archive files;
this option is designed for the Model in Image [domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

Finally, you can use the WIT `inspect` command to [inspect images]({{< relref "/userguide/base-images/_index.md#inspect-images" >}}).

  In detail:

  - WIT [`create`](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-image/) command:

    - Creates a new WebLogic image from a base OS image.
    - Can be used for all [domain home source types]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}) (Domain in Image, Model in Image, and Domain in PV).
    - Optionally, includes a WebLogic Deploy Tooling (WDT) [installation](https://oracle.github.io/weblogic-deploy-tooling/userguide/install/) and model files in the image
      (for Model in Image domains).
      See also, [Create a custom image with your model inside the image](#create-a-custom-image-with-your-model-inside-the-image).
    - Optionally, generates a domain home in the image using WLST or WDT
      (for Domain in Image domains).
      See also, [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image).
    - _Important_:
      - The `create` command is _not_ suitable for updating an existing domain home
        in existing Domain in Image images
        when the update is intended for a running domain. Use `rebase` instead
        or shut down the running domain entirely before applying the new image.
        For more information, see [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image).

  - WIT [`rebase`](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/) command:

    - The `rebase` command is used for Domain in Image
      domains.
      - Creates a new WebLogic image and copies an existing WebLogic domain home
        from an older image to the new image.
      - The created image is suitable for deploying to an already running
        domain with an older version of the domain home.

    - The new image can be created in one of two ways:
      - As a layer on an existing
        WebLogic image in the repository
        that doesn't already have a domain home, such as an updated CPU image from OCR.
      - Or, as a new WebLogic image from a base OS image.

  - WIT [`update`](https://oracle.github.io/weblogic-image-tool/userguide/tools/update-image/) command:

    - Creates a new WebLogic image layered on
      an existing WebLogic image (specified in the WIT `--fromImage` parameter).
      - Note that if you specify the `--pull` parameter for WIT,
        and the `--fromImage` parameter refers to an image in a repository,
        and the repository image is newer than the locally cached version of the image,
        then the command will download the repository image
        to the local Docker cache and use it
        instead of using the outdated local image.
    - Optionally, generates a domain home in the new image using WDT or WLST
      (for Domain in Image domains).
    - Optionally, includes a WDT installation and model files in the image
      (for Model in Image domains).
    - _Important_:
      - **Note:** Patching an Oracle Home using the WIT `update`
        command results in a larger WebLogic Server image
        due to the immutable layering in container images.
        Consider using `rebase` or `create` to reduce the size impact of applying patches.
      - The WIT `update` command is not suitable for updating an existing domain home in
        an existing Domain in Image image
        when the update is intended for a running domain.
        Use the WIT `rebase` command instead
        or shut down the running domain entirely before applying the new image.
        For more information, see [Apply patched images to a running domain](#apply-patched-images-to-a-running-domain).
      - Optionally, includes a WDT installation and model files in the image (for Model in Image domains).

  - WIT [`createAuxImage`](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-aux-image/) command:

     - Supports creating auxiliary images for Model in Image domains only.
     - The auxiliary images
       solely contain WebLogic Deploy Tooling files for the Model in Image use case
       and are used in addition to the
       domain resource image that contains your WebLogic and Java installations.
     - For more information, see [Auxiliary images]({{< relref "/userguide/managing-domains/model-in-image/auxiliary-images.md" >}}).

  - WIT [`inspect`](https://oracle.github.io/weblogic-image-tool/userguide/tools/inspect-image/) command:

     - Inspects images created with the WebLogic Image Tool.
     - See [Inspect images]({{< relref "/userguide/base-images/_index.md#inspect-images" >}}).

### Create a custom image with patches applied

All domain home source types require a base image which contains JDK and WebLogic Server binaries.
This base image is usually [obtained directly]({{< relref "/userguide/base-images/_index.md#obtain-images-from-the-oracle-container-registry" >}}) from the Oracle Container Registry,
but, as needed, you can also [create your own custom base image](#create-a-custom-base-image).

If you are using the Domain in Image domain home source type,
then you will additionally need to use the base image
to [create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image).

Or, if you are using the Model in Image domain home source type _without_ auxiliary images,
then you will additionally need to use the base image
to [create a custom image with your model inside the image](#create-a-custom-image-with-your-model-inside-the-image).

#### Create a custom base image

{{% notice tip %}}
This section describes using the WebLogic Image Tool (WIT) `create` command
to build a custom base WebLogic Server image.
This is sometimes necessary to build an image with a specific patch, and such,
but most use cases can instead, obtain pre-built
patched images directly from the Oracle Container Registry.
See
[Obtain images from the Oracle Container Registry]({{< relref "/userguide/base-images/_index.md#obtain-images-from-the-oracle-container-registry" >}}).
{{% /notice %}}

Here's an example of using the WIT `create` command to create a base WebLogic Server image
from a base Oracle Linux image, a WebLogic installer download, and a JRE installer download:

1. First, [install](https://oracle.github.io/weblogic-image-tool/userguide/setup/) the WebLogic Image Tool.

1. Download your desired JRE installer from the Oracle Technology Network [Java downloads](https://www.oracle.com/java/technologies/downloads/) page or from the [Oracle Software Delivery Cloud](https://edelivery.oracle.com/osdc/faces/Home.jspx) (OSDC).

1. Download your desired WebLogic Server installer from the Oracle Technology Network [WebLogic Server installers](https://www.oracle.com/middleware/technologies/weblogic-server-installers-downloads.html) page or from the [Oracle Software Delivery Cloud](https://edelivery.oracle.com/osdc/faces/Home.jspx) (OSDC).

   **Note:** The WebLogic Server installers will not be fully patched.
   In a subsequent step, you'll use
   the WIT `--patches` or `--recommendedPatches` options
   to apply one-off and recommended Oracle patches.

1. Add the installers to your WIT cache using the
   [`cache`](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/) command.
   For example, assuming you downloaded the installers to the `/home/acmeuser/wls-installers` directory:

   ```shell
   $ /tmp/imagetool/bin/imagetool.sh cache addInstaller \
     --type=jdk \
     --version=8u291 \
     --path=/home/acmeuser/wls-installers/jre-8u291-linux-x64.tar.gz
   ```
   ```shell
   $ /tmp/imagetool/bin/imagetool.sh cache addInstaller \
     --type=wls \
     --version=12.2.1.3.0 \
     --path=/home/acmeuser/wls-installers/fmw_12.2.1.3.0_wls_Disk1_1of1.zip
   ```
   ```shell
   $ /tmp/imagetool/bin/imagetool.sh cache addInstaller \
     --type=wls \
     --version=12.2.1.4.0 \
     --path=/home/acmeuser/wls-installers/fmw_12.2.1.4.0_wls_Disk1_1of1.zip
   ```

   For details, see the WIT [Quick Start](https://oracle.github.io/weblogic-image-tool/quickstart/quickstart/) guide.

1. Use the [`create`](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-image/) command
   to build the image using a default Oracle Linux image as its base, and download and apply the patches.

   For example, use the following command to create a WebLogic Server image
   named `latest_weblogic:12.2.1.4` with:
   - The WebLogic Server 12.2.1.4.0 generic installer
   - JDK 8u291
   - The latest version of the Oracle Linux 7 slim container image
   - The latest quarterly Patch Set Update (PSU), which include security fixes, or with one-off patches
   ```shell
   $ /tmp/imagetool/bin/imagetool.sh create \
     --tag latest_weblogic:12.2.1.4 \
     --pull \
     --jdkVersion=8u291 \
     --type=wls \
     --version=12.2.1.4.0 \
     --recommendedPatches \
     --user myusername@mycompany.com \
     --passwordEnv=MYPWD
   ```

   As another example, if you want to create a WebLogic Server image
   named `minimal_weblogic:12.2.1.3` with:
   - The WebLogic slim installer instead of the generic installer
   - JDK 8u291
   - The latest version of the Oracle Linux 7 slim container image
   - The minimal patches required for the operator to run a 12.2.1.3 image
     (patches 29135930 and 27117282)
     instead of the latest recommended patches
   ```shell
   $ /tmp/imagetool/bin/imagetool.sh create \
     --tag minimal_weblogic:12.2.1.3 \
     --pull \
     --jdkVersion=8u291 \
     --type=wlsslim \
     --version=12.2.1.3.0 \
     --patches=29135930_12.2.1.3.0,27117282_12.2.1.3.0 \
     --user myusername@mycompany.com \
     --passwordEnv=MYPWD
   ```

   **Notes:**
   - To enable WIT to download patches,
        you must supply your My Oracle Support (Oracle Single Sign-On) credentials
     using the `--user` and `--passwordEnv` parameters (or one of the other password CLA options).
     This example assumes that you have set the `MYPWD`
     shell environment variable so that it contains your password.
   - The `--type` parameter designates the Oracle product installation, such as WebLogic Server,
     Fusion Middleware (FMW) Infrastructure, and such,
     to include in the generated image. For example,
     the `wls` type corresponds to the WebLogic Server (WLS) generic installation,
     the `wlsslim` type to the WLS slim installation,
     and the `wlsdev` type to the WLS developer installation.
     For a description of each installation type, see
     [WebLogic distribution installer type]({{< relref "/userguide/base-images/_index.md#weblogic-distribution-installer-type" >}}).
   - The `--recommendedPatches` parameter finds and applies the latest PatchSet Update (PSU)
     and recommended patches for each of the products included in the installer. For example, for WebLogic Server, the recommended patches for Coherence and TopLink are included.
   - These sample commands use a default base image, which is an Oracle Linux OS image,
     and downloads (pulls) this image only if it is not already cached locally.
     You can use `docker images` to view your image cache.
   - The `--pull` parameter for WIT is passed to the container build engine which forces a check to the remote repository,
     if applicable, prior to the build execution of the new image to update any image used during the build (updates dependencies).
   - For details about each parameter, see the WebLogic Image Tool [User Guide](https://oracle.github.io/weblogic-image-tool/userguide/tools/).

1. After the tool creates the image, verify that the image is in your local repository:

    ```shell
    $ docker images
    ```

   You can also [inspect](#inspect-images) the contents of the image.

#### Create a custom image with your domain inside the image

{{% notice warning %}}
Oracle strongly recommends storing Domain in Image images in a private registry.
A container image that contains a WebLogic domain home has sensitive information
including credentials that are used to access external resources
(for example, a data source password),
and decryption keys
(for example, the `DOMAIN_HOME/security/SerializedSystemIni.dat` domain secret file).
For more information,
see [Container image protection]({{<relref "/security/domain-security/image-protection.md">}}).
{{% /notice %}}


{{% notice warning %}}
The sample scripts in this section reference base image
`container-registry.oracle.com/middleware/weblogic:12.2.1.4`.
This is an Oracle Container Registry (OCR) GA image
which includes the latest security patches for Oracle Linux and Java,
_and does not include the latest security patches for WebLogic Server_.
GA images are intended for single desktop demonstration and development purposes.
For all other uses, Oracle strongly recommends using images with the latest security patches,
such as OCR Critical Patch Updates (CPU) images or custom generated images.
See [Ensure you are using recently patched images]({{< relref "/userguide/base-images/_index.md#ensure-you-are-using-recently-patched-images" >}}).
{{% /notice %}}



This section provides guidance for creating a new Domain in Image image.
This type of image cannot be used in pods that must join the pods in
an already running domain. If you need to create a Domain in Image
image that is meant for updating an already running domain, then see
[Apply patched images to a running domain](#apply-patched-images-to-a-running-domain).


For Domain in Image domains,
you must create an image with the WebLogic domain inside the image.
[Samples]({{< relref "/samples/domains/domain-home-in-image/_index.md" >}})
are provided that demonstrate how to create the image using either
WLST to define the domain
or [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) models to define the domain.
In these samples, you will see a reference to a "base" or `--fromImage` image.
You should use an image with the recommended patches installed as this base image,
where this image could be an OCR image or a custom image.
See
[Obtain images from the Oracle Container Registry]({{< relref "/userguide/base-images/_index.md#obtain-images-from-the-oracle-container-registry" >}})
and
[Create a custom image with patches applied](#create-a-custom-image-with-patches-applied).

The samples perform multiple steps for you
using a single provided script
and are not intended for production use.
To help understand the individual steps,
use the following step-by-step guidance
for using WLST or WDT
to create the domain home in Domain in Image.

- Domain in Image using WDT:

  Here we explore a step-by-step approach for Domain in Image
  using WebLogic Deploy Tool models to create the domain home.
  These steps stage files to `/tmp/dii-wdt-stage`,
  assume the operator source is in `/tmp/weblogic-kubernetes-operator`,
  assume you have installed WIT in `/tmp/imagetool`,
  and generate a Domain in Image image named `my-dii-wdt:v1`,

  {{%expand "Click here to view the script." %}}

  ```
  #!/bin/bash

  set -eux

  # Define paths for:
  # - the operator source (assumed to already be downloaded)
  # - the WIT installation (assumed to already be installed)
  # - a staging directory for the image build

  srcDir=/tmp/weblogic-kubernetes-operator
  imageToolBin=/tmp/imagetool/bin
  stageDir=/tmp/dii-wdt-stage

  # Define location of the domain home within the image

  domainHome=/u01/oracle/user_projects/domains/dii-wdt

  # Define base image and final image

  fromImage=container-registry.oracle.com/middleware/weblogic:12.2.1.4
  finalImage=my-dii-wdt:v1

  # Init staging directory

  [ -e "$stageDir" ] && echo "Error: stage dir '$stagedir' already exists." && exit 1
  mkdir -p $stageDir

  # Copy sample model file to the stage directory

  cp $srcDir/kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/wdt/wdt_model_dynamic.yaml $stageDir

  # Create a set of properties that are referenced by the model file

  cat << EOF > $stageDir/domain.properties
  DOMAIN_NAME=dii-wdt
  SSL_ENABLED=false
  ADMIN_PORT=7001
  ADMIN_SERVER_SSL_PORT=7002
  ADMIN_NAME=admin-server
  ADMIN_HOST=wlsadmin
  ADMIN_USER_NAME=weblogic1
  ADMIN_USER_PASS=mypassword1
  MANAGED_SERVER_PORT=8001
  MANAGED_SERVER_SSL_PORT=8002
  MANAGED_SERVER_NAME_BASE=managed-server
  CONFIGURED_MANAGED_SERVER_COUNT=5
  CLUSTER_NAME=cluster-1
  DEBUG_PORT=8453
  DB_PORT=1527
  DEBUG_FLAG=true
  PRODUCTION_MODE_ENABLED=true
  CLUSTER_TYPE=DYNAMIC
  JAVA_OPTIONS=-Dweblogic.StdoutDebugEnabled=false
  T3_CHANNEL_PORT=30012
  T3_PUBLIC_ADDRESS=
  EOF

  # Download WDT and add a reference in the WIT cache

  curl -m 120 \
    -fL https://github.com/oracle/weblogic-deploy-tooling/releases/latest/download/weblogic-deploy.zip \
    -o $stageDir/weblogic-deploy.zip

  $imageToolBin/imagetool.sh \
    cache deleteEntry --key wdt_latest

  $imageToolBin/imagetool.sh \
    cache addInstaller \
    --type wdt \
    --version latest \
    --path $stageDir/weblogic-deploy.zip

  # Create the image
  # (this will run the latest version of the WIT tool during image creation
  #  to create the domain home from the provided model files)

  $imageToolBin/imagetool.sh update \
    --fromImage "$fromImage" \
    --tag "$finalImage" \
    --wdtModel "$stageDir/wdt_model_dynamic.yaml" \
    --wdtVariables "$stageDir/domain.properties" \
    --wdtOperation CREATE \
    --wdtVersion LATEST \
    --wdtDomainHome "$domainHome" \
    --chown=oracle:root

  ```

  {{% /expand %}}

- Domain in Image using WLST:

  Here is a step-by-step approach for Domain in Image
  images using WLST. These steps stage files to `dii-wlst-stage`,
  put the domain home inside the image at `/u01/oracle/user_projects/domains/dii-wlst`,
  assume the operator source is in `/tmp/weblogic-kubernetes-operator`,
  assume you have installed WIT in `/tmp/imagetool`,
  and name the final image `my-dii-wlst:v1`.

  {{%expand "Click here to view the script." %}}

  ```
  #!/bin/bash

  set -eux

  # Define paths for:
  # - the operator source (assumed to already be downloaded)
  # - the WIT installation (assumed to already be installed)
  # - a staging directory for the image build

  srcDir=/tmp/weblogic-kubernetes-operator
  imageToolBin=/tmp/imagetool/bin
  stageDir=/tmp/dii-wlst-stage

  # Define location of the domain home within the image

  domainHome=/u01/oracle/user_projects/domains/dii-wlst

  # Define base image and final image

  fromImage=container-registry.oracle.com/middleware/weblogic:12.2.1.4
  finalImage=my-dii-wlst:v1

  # Copy sample WLST, Docker commands, and setup scripts
  # to the staging directory and modify to point to the domain home

  [ -e "$stageDir" ] && echo "Error: stage dir '$stagedir' already exists." && exit 1
  mkdir -p $stageDir

  sampleDir=$srcDir/kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image
  cp $sampleDir/wlst/additional-build-commands-template $stageDir/additional-build-commands
  sed -i -e "s:%DOMAIN_HOME%:$domainHome:g" $stageDir/additional-build-commands
  cp $sampleDir/wlst/createWLSDomain.sh $stageDir
  cp $sampleDir/wlst/create-wls-domain.py $stageDir

  # Create a set of properties to pass to the create-wls-domain.py WLST script

  cat << EOF > $stageDir/domain.properties
  DOMAIN_NAME=dii-wlst
  DOMAIN_HOME=$domainHome
  SSL_ENABLED=false
  ADMIN_PORT=7001
  ADMIN_SERVER_SSL_PORT=7002
  ADMIN_NAME=admin-server
  ADMIN_HOST=wlsadmin
  ADMIN_USER_NAME=weblogic1
  ADMIN_USER_PASS=mypassword1
  MANAGED_SERVER_PORT=8001
  MANAGED_SERVER_SSL_PORT=8002
  MANAGED_SERVER_NAME_BASE=managed-server
  CONFIGURED_MANAGED_SERVER_COUNT=5
  CLUSTER_NAME=cluster-1
  DEBUG_PORT=8453
  DB_PORT=1527
  PRODUCTION_MODE_ENABLED=true
  CLUSTER_TYPE=DYNAMIC
  JAVA_OPTIONS=-Dweblogic.StdoutDebugEnabled=false
  T3_CHANNEL_PORT=30012
  T3_PUBLIC_ADDRESS=
  EOF

  # Create the image
  # Notes:
  # - This will run the provided WLST during image creation to create the domain home.
  # - The wdt parameters are required, but ignored.
  $imageToolBin/imagetool.sh update \
    --fromImage "$fromImage" \
    --tag "$finalImage"      \
    --wdtOperation CREATE    \
    --wdtVersion LATEST      \
    --wdtDomainHome "$domainHome"  \
    --additionalBuildCommands $stageDir/additional-build-commands \
    --additionalBuildFiles "$stageDir/createWLSDomain.sh,$stageDir/create-wls-domain.py,$stageDir/domain.properties" \
    --chown=oracle:root
  ```

  {{% /expand %}}

**Notes**:

- The sample script and its `domain.properties` file include a sample WebLogic administration password.
  These files must be protected and the sample password must be changed.
- The sample scripts, sample properties,
  the files provided in `--additionalBuildCommands` and `--additionalBuildFiles` parameters for the WLST approach,
  or the sample WDT model files provided in the WDT approach,
  are _not_ intended for production use.
  These files can all change substantially in new versions of the operator
  and must all be copied, preserved, and customized to suite your particular use case.

#### Create a custom image with your model inside the image

In the [Model in Image]({{< relref "/userguide/managing-domains/model-in-image/_index.md" >}})
documentation, you will see a reference to a "base" or `--fromImage` image.
This image can be an OCR image or a custom image.
See
[Obtain images from the Oracle Container Registry]({{< relref "/userguide/base-images/_index.md#obtain-images-from-the-oracle-container-registry" >}})
or
[Create a custom image with patches applied](#create-a-custom-image-with-patches-applied).

### Apply patched images to a running domain

When updating the WebLogic binaries of a running domain in Kubernetes with a patched container image,
the operator applies the update in a zero downtime fashion.
The procedure for the operator to update the running domain differs depending on the
[domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).
See the following corresponding directions:

- [Domain in PV](#domain-in-pv)
- [Model in Image with auxiliary images](#model-in-image-with-auxiliary-images)
- [Model in Image without auxiliary images](#model-in-image-without-auxiliary-images)
- [Domain in Image](#domain-in-image)

For a broader description of managing the evolution and mutation
of container images to run WebLogic Server in Kubernetes,
see [CI/CD]({{< relref "/userguide/cicd/_index.md" >}}).

#### Domain in PV

{{% notice warning %}}
Oracle strongly recommends strictly limiting access to Domain in PV domain home files.
A WebLogic domain home has sensitive information
including credentials that are used to access external resources
(for example, a data source password),
and decryption keys
(for example, the `DOMAIN_HOME/security/SerializedSystemIni.dat` domain secret file).
{{% /notice %}}

For Domain in PV domains,
the container image contains only the JDK and WebLogic Server binaries,
and its domain home is located in a Persistent Volume (PV)
where the domain home is generated by the user.

For this domain home source type, you can create your own patched images using the steps
in [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)
or you can obtain patched images from the Oracle Container Registry,
see [Obtain images from the Oracle Container Registry]({{< relref "/userguide/base-images/_index.md#obtain-images-from-the-oracle-container-registry" >}}).

To apply the patched image,
edit the Domain Resource image reference with the new image name/tag
(for example, `oracle/weblogic:12.2.1.4-patched`).
The operator will then automatically perform a
[rolling restart]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.
For more information on server restarts,
see [Restarting]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting.md" >}}).

#### Model in Image with auxiliary images

For Model in Image domains when using auxiliary images:

- The container image contains only the JDK and WebLogic Server binaries.
- The [WebLogic Deployment Tooling](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) installation,
  WDT model files, and application archive files,
  are located in separate auxiliary images.
- The domain home is generated by the operator during runtime.

To create and apply patched WebLogic Server images to a running domain of this type,
first follow the steps in
[Obtain images from the Oracle Container Registry]({{< relref "/userguide/base-images/_index.md#obtain-images-from-the-oracle-container-registry" >}}) or
[Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)
to obtain or create the image,
and then edit the Domain Resource `image` field with the new image name (for example, `oracle/weblogic:12.2.1.4-patched`).

To apply patched auxiliary images to a running domain of this type,
follow the same steps that you used to create your original auxiliary image
and alter your domain resource to reference the new image
(see [Auxiliary images]({{< relref "/userguide/managing-domains/model-in-image/auxiliary-images.md" >}})).
The operator will then perform a [rolling restart]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.

#### Model in Image without auxiliary images

For Model in Image domains _without_ using auxiliary images:

- The container image contains the JDK, WebLogic Server binaries,
  a [WebLogic Deployment Tooling](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) installation,
  WDT model files, and an application archive file.
- The domain home is generated by the operator during runtime.

If you need to update the image for a running Model in Image domain,
then simply follow the same steps that were used to create the original
image as described in [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied),
and edit the domain resource's `domain.spec.image` attribute
with the new image's name/tag (`mydomain:v2`).
The operator will then perform a [rolling restart]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.

#### Domain in Image

If you need to update the image for a running Domain in Image domain,
then use the WIT [`rebase`](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/)
command to update the Oracle Home
for an existing domain image using the patched Oracle Home from a patched container image.
For Domain in Image domains:

- The container image contains the JDK, WebLogic Server binaries, and domain home.

- The domain home is generated during image creation using either WLST or WDT,
  usually with the assistance of the WebLogic Image Tool (WIT).

The `rebase` command does the following:

- Minimizes the image size. The alternative `update` command does _not_ remove old WebLogic installations
  in the image but instead, layers new WebLogic installations on top of the original installation, thereby
  greatly increasing the image size; we strongly recommend against using the `update` command in this situation.

- Creates a new WebLogic image by copying an existing WebLogic domain home
  from an existing image to a new image.
  It finds the domain home location within the original image
  using the image's internal `DOMAIN_HOME` environment variable.

- Maintains the same security configuration
  as the original image because the domain home is copied
  (for example, the `DOMAIN_HOME/security/SerializedSystemIni.dat` file).
  This ensures that pods that are based on the new image
  are capable of joining an already running
  domain with pods on an older version of the image with same security configuration.

Using `rebase`, the new image can be created in one of two ways:

- As a new WebLogic image from a base OS image (similar to the `create` command; recommended).

  To activate:
  - Set `--tag` to the name of the final new image.
  - Set `--sourceImage` to the WebLogic image that contains the WebLogic configuration.
  - Set additional fields (such as the WebLogic and JDK locations),
    similar to those used by `create`.
    See [Create a custom base image](#create-a-custom-base-image).
  - Do _not_ set `--targetImage`.  (When
    you don't specify a `--targetImage`, `rebase` will use
    the same options and defaults as `create`.)

- Or, as a new layer on an existing
  WebLogic image in the repository
  that doesn't already have a domain home (similar to the `update` command; not recommended).
  - Usage:
    - Set `--tag` to the name of the final new image.
    - Set `--sourceImage` to the WebLogic image that contains the WebLogic configuration.
    - Set `--targetImage` to the image that you will you use as a base for the new layer.
  - Example:
    First, generate the new image:
    ```shell
    $ /tmp/imagetool/bin/imagetool.sh rebase \
      --tag mydomain:v2 \
      --sourceImage mydomain:v1 \
      --targetImage oracle/weblogic:12.2.1.4-patched
    ```
   - Second, edit the domain resource `domain.spec.image`
    attribute with the new image's name `mydomain:2`.
    - Then, the operator automatically performs a
    [rolling upgrade]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}})
    on the domain.

In summary, the `rebase` command preserves the original domain home's security configuration
files in a Domain in Image image so that, when they are both deployed to the same running domain,
your updated images and original images can interoperate without a
[domain secret mismatch]({{< relref "/faq/domain-secret-mismatch.md" >}}).

**Notes:**

  - You cannot use the `rebase` command alone to update the domain home configuration.
    If you need to update the domain home configuration,
    then use the `rebase` command first, followed by the `update` command.

  - An Oracle Home and the JDK must be installed in the same directories on each image.

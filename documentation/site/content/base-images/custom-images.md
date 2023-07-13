---
title: "Create custom images"
date: 2019-02-23T16:45:55-05:00
weight: 2
description: "Create custom WebLogic images using the WebLogic Image Tool (WIT)."
---

{{< table_of_contents >}}


### Use the WebLogic Image Tool to create custom images

You can use the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) (WIT) to build your own WebLogic Server or
Fusion Middleware Infrastructure images (with the latest Oracle Linux images, Java updates, and WebLogic Server patches),
apply one-off patches to existing OCR images, or overlay your own files and applications on top of an OCR image.  

Download and install the WebLogic Image Tool (WIT) following the
WIT [Setup](https://oracle.github.io/weblogic-image-tool/userguide/setup/) instructions.
Also, refer to the WIT [Quick Start](https://oracle.github.io/weblogic-image-tool/quickstart/quickstart/) Guide. The
samples in this document assume that you have installed WIT in `/tmp/imagetool`; you can choose to install it in any location.

The WebLogic Image Tool `create`, `update`, or `rebase` commands supply
three different ways to generate a custom WebLogic Server installation image
from a base OS image (optionally, with WebLogic patches). In addition, the WIT `createAuxImage` command
supports creating auxiliary images which
do _not_ contain a WebLogic Server installation,
and instead, solely contain the [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) installation and model files;
this option is designed for the Model in Image [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).

Finally, you can use the WIT `inspect` command to [inspect images]({{< relref "/base-images/ocr-images#inspect-images" >}}).

  In detail:

  - WIT [`create`](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-image/) command:

    - Creates a new WebLogic image from a base OS image.
    - Can be used for all [domain home source types]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) (Domain in Image, Model in Image, and Domain on PV).
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
      - Or, as a new WebLogic image from a base OS image. **NOTE**:  Oracle strongly recommends rebasing your
        images with the latest security patches by applying the [`--recommendedPatches`](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/) option.
      - For more information, see [Apply patched images to a running domain]({{< relref "/base-images/patch-images#domain-in-image" >}}).

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
      - **NOTE**: Patching an Oracle Home using the WIT `update`
        command results in a larger WebLogic Server image
        due to the immutable layering in container images. For small updates, such as a one-off patch,
        the increase in the image size may be negligible. However, for larger updates, the increase in size will be _significant_.
        Consider using `rebase` or `create` to reduce the size impact of applying patches.
      - The WIT `update` command is not suitable for updating an existing domain home in
        an existing Domain in Image image
        when the update is intended for a running domain.
        Use the WIT `rebase` command instead
        or shut down the running domain entirely before applying the new image.
        For more information, see [Apply patched images to a running domain]({{< relref "/base-images/patch-images#apply-patched-images-to-a-running-domain" >}}).
      - Optionally, includes a WDT installation and model files in the image (for Model in Image domains).

  - WIT [`createAuxImage`](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-aux-image/) command:

     - Supports creating auxiliary images for Model in Image domains only.
     - The auxiliary images
       solely contain WebLogic Deploy Tooling files for the Model in Image use case
       and are used in addition to the
       domain resource image that contains your WebLogic and Java installations.
     - For more information, see [Auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).

  - WIT [`inspect`](https://oracle.github.io/weblogic-image-tool/userguide/tools/inspect-image/) command:

     - Inspects images created with the WebLogic Image Tool.
     - See [Inspect images]({{< relref "/base-images/ocr-images#inspect-images" >}}).

### Create a custom image with patches applied

All domain home source types require a base image which contains JDK and WebLogic Server binaries.
This base image is usually [obtained directly]({{< relref "/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}}) from the Oracle Container Registry,
but, as needed, you can also [create your own custom base image](#create-a-custom-base-image).

If you are using the Domain in Image domain home source type,
then you will additionally need to use the base image
to [create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image).

Or, if you are using the Model in Image domain home source type _without_ auxiliary images,
then you will additionally need to use the base image
to [create a custom image with your model inside the image](#create-a-custom-image-with-your-model-inside-the-image).

#### Create a custom base image

{{% notice tip %}}
This section describes using the WebLogic Image Tool (WIT) [`create`](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-image/) command
to build a custom base WebLogic Server image.
This is sometimes necessary to build an image with a specific patch, and such,
but most use cases can instead, obtain pre-built
patched images directly from the Oracle Container Registry.
See
[Obtain images from the Oracle Container Registry]({{< relref "/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}}).
{{% /notice %}}

Here's an example of using the WIT [create](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-image/)
command to create a base WebLogic Server image
from a base Oracle Linux image, a WebLogic installer download, and a JRE installer download:

1. First, [install](https://oracle.github.io/weblogic-image-tool/userguide/setup/) the WebLogic Image Tool. This
sample assumes that you have installed WIT in `/tmp/imagetool`; you can choose to install it in any location.

1. Download your desired JRE installer from the Oracle Technology Network [Java downloads](https://www.oracle.com/java/technologies/downloads/) page or from the [Oracle Software Delivery Cloud](https://edelivery.oracle.com/osdc/faces/Home.jspx) (OSDC).

1. Download your desired WebLogic Server installer from the Oracle Technology Network [WebLogic Server installers](https://www.oracle.com/middleware/technologies/weblogic-server-installers-downloads.html) page or from the [Oracle Software Delivery Cloud](https://edelivery.oracle.com/osdc/faces/Home.jspx) (OSDC).

   **NOTE**: The WebLogic Server installers will not be fully patched.
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

{{% notice note %}} As of June, 2023, Oracle WebLogic Server 12.2.1.3 is no longer supported. The last Critical Patch Updates (CPU) images for WebLogic Server 12.2.1.3 were published in April, 2023. As of December, 2022, Fusion Middleware 12.2.1.3 is no longer supported.  The last CPU images for FMW Infrastructure 12.2.1.3 were published in October, 2022.
{{% /notice %}}

   **NOTES**:
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
     [WebLogic distribution installer type]({{< relref "/base-images/ocr-images#weblogic-distribution-installer-type" >}}).
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

   You can also [inspect]({{< relref "/base-images/ocr-images#inspect-images" >}}) the contents of the image.

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
This is an OCR General Availability (GA) image
which **does not include** the latest security patches for WebLogic Server.
GA images are intended for single desktop demonstration and development purposes _only_.
For all other purposes, Oracle strongly recommends using only images with the latest set of recommended patches applied,
such as OCR Critical Patch Updates (CPU) images or custom generated images.
See [Ensure you are using recently patched images]({{< relref "/base-images/ocr-images#ensure-you-are-using-recently-patched-images" >}}).
{{% /notice %}}



This section provides guidance for creating a new Domain in Image image.
This type of image cannot be used in pods that must join the pods in
an already running domain. If you need to create a Domain in Image
image that is meant for updating an already running domain, then see
[Apply patched images to a running domain]({{< relref "/base-images/patch-images#apply-patched-images-to-a-running-domain" >}}).

**NOTE**: The Domain in Image [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) is
deprecated in WebLogic Kubernetes Operator version 4.0. Oracle recommends that you choose either Domain on PV or Model in Image, depending on your needs.

For Domain in Image domains,
you must create an image with the WebLogic domain inside the image.
[Samples]({{< relref "/samples/domains/domain-home-in-image/_index.md" >}})
are provided that demonstrate how to create the image using either
WLST to define the domain
or [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) models to define the domain.
In these samples, you will see a reference to a "base" or `--fromImage` image.
You should use an image with the recommended security patches installed as this base image,
where this image could be an OCR image or a custom image.
See
[Obtain images from the Oracle Container Registry]({{< relref "/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}})
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
  using WebLogic Deploy Tooling models to create the domain home.
  These steps stage files to `/tmp/dii-wdt-stage`,
  assume the operator source is in `/tmp/weblogic-kubernetes-operator`,
  assume you have installed WIT in `/tmp/imagetool`,
  and generate a Domain in Image image named `my-dii-wdt:v1`.

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

**NOTES**:

- The sample script and its `domain.properties` file include a sample WebLogic administration password.
  These files must be protected and the sample password must be changed.
- The sample scripts, sample properties,
  the files provided in `--additionalBuildCommands` and `--additionalBuildFiles` parameters for the WLST approach,
  or the sample WDT model files provided in the WDT approach,
  are _not_ intended for production use.
  These files can all change substantially in new versions of the operator
  and must all be copied, preserved, and customized to suite your particular use case.

#### Create a custom image with your model inside the image

**NOTE**: Model in Image without auxiliary images (the WDT model and installation files are included in the same image with the WebLogic Server installation) is deprecated in WebLogic Kubernetes Operator version 4.0.7. Oracle recommends that you use Model in Image _with_ auxiliary images. See [Auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).

{{% notice warning %}}
The example in this section references a base image,
`container-registry.oracle.com/middleware/weblogic:12.2.1.4`.
This is an OCR General Availability (GA) image
which **does not include** the latest security patches for WebLogic Server.
GA images are intended for single desktop demonstration and development purposes _only_.
For all other purposes, Oracle strongly recommends using only images with the latest set of recommended patches applied,
such as OCR Critical Patch Updates (CPU) images or custom generated images.
See [Ensure you are using recently patched images]({{< relref "/base-images/ocr-images#ensure-you-are-using-recently-patched-images" >}}).
{{% /notice %}}

Example steps for creating a custom WebLogic image with a Model in Image file layer
(using files from the Model in Image sample):

1. To gain an overall understanding of Model in Image domains,
   read the [Model in Image User Guide]({{< relref "/managing-domains/model-in-image/_index.md" >}})
   and the [Model in Image Sample]({{< relref "/samples/domains/model-in-image/_index.md" >}}).
   Note that the sample uses the recommended best approach,
   auxiliary images, instead of the alternative approach, which is used in this example.

1. Follow the prerequisite steps in the
   [Model in Image Sample]({{< relref "/samples/domains/model-in-image/prerequisites.md" >}})
   that describe how to:

   - Download the operator source and its Model in Image sample
     (including copying the sample to the suggested location, `/tmp/sample`).
   - Download the latest [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling/releases) (WDT)
     and [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool/releases) (WIT) installer ZIP files
     to your `/tmp/sample/wdt-artifacts` directory.
     Both WDT and WIT are required to create your Model in Image container images.
   - Install (unzip) the WebLogic Image Tool and configure its cache to reference your WebLogic Deploy Tooling download.

1. Locate or create a base WebLogic image.

   See [Obtain images from the Oracle Container Registry]({{< relref "/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}})
   or [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied).

   In the following step, you will use the `container-registry.oracle.com/middleware/weblogic:12.2.1.4` GA image.

1. Build the final image using WIT while specifying the base image, target image tag, WDT installation location,
   and WDT model file locations. For example:

   First, create a model ZIP file application archive and place it in the same directory
   where the sample model YAML file and model properties files are already staged:

   ```shell
   $ rm -f /tmp/sample/wdt-artifacts/wdt-model-files/WLS-LEGACY-v1/archive.zip
   $ cd /tmp/sample/wdt-artifacts/archives/archive-v1
   $ zip -r /tmp/sample/wdt-artifacts/wdt-model-files/WLS-LEGACY-v1/archive.zip wlsdeploy
   ```

   (The `rm -f` command is included in case there's an
   old version of the archive ZIP file from a
   previous run of this sample.)

   Second, run the following WIT command:

   ```shell
   $ cd /tmp/sample/wdt-artifacts/wdt-model-files/WLS-LEGACY-v1
   ```
   ```shell
   $ ./imagetool/bin/imagetool.sh update \
     --tag wdt-domain-image:WLS-LEGACY-v1 \
     --fromImage container-registry.oracle.com/middleware/weblogic:12.2.1.4 \
     --wdtModel      ./model.10.yaml \
     --wdtVariables  ./model.10.properties \
     --wdtArchive    ./archive.zip \
     --wdtModelOnly \
     --wdtDomainType WLS \
     --chown oracle:root
   ```
   **NOTE** that JRF support in Model in Image domains is deprecated in operator version 4.1.0; For JRF domains, use the [Domain on PV]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) domain home source type instead.

1. For an example Domain YAML file that sets up Model in Image to reference the image,
   see `/tmp/sample/domain-resources/WLS-LEGACY/mii-initial-d1-WLS-LEGACY-v1.yaml`

   **NOTES**:

   - The default values for `domain.spec.configuration.model.wdtInstallHome` and `.modelHome`
     reference the location of the WDT installation and model files that WIT copied into the image.

   - The domain type specified in `domain.spec.configuration.model.domainType`
     must correspond with the `--wdtDomainType` specified on the WIT command line when creating the image.

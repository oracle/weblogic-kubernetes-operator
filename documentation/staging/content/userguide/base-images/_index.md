---
title: "WebLogic Server images"
date: 2019-02-23T16:45:55-05:00
weight: 6
description: "Create or obtain WebLogic Server images."
---

#### Contents

- [Overview](#overview)
- [Understanding Oracle Container Registry WebLogic Server images](#understanding-oracle-container-registry-weblogic-server-images)
- [Obtain WebLogic Server images from the Oracle Container Registry](#obtain-weblogic-server-images-from-the-oracle-container-registry)
- [Set up Kubernetes to access a container registry](#set-up-kubernetes-to-access-a-container-registry)
- [Ensuring you are using recently patched images](#ensuring-you-are-using-recently-patched-images)
- [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)
- [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image)
- [Create a custom image with your model inside the image](#create-a-custom-image-with-your-model-inside-the-image)
- [Apply patched images to a running domain](#apply-patched-images-to-a-running-domain)
  - [Domain in PV](#domain-in-pv)
  - [Model in Image with auxiliary images](#model-in-image-with-auxiliary-images)
  - [Model in Image without auxiliary images](#model-in-image-without-auxiliary-images)
  - [Domain in Image](#domain-in-image)

#### Overview

You will need WebLogic Server or Fussion Middleware Infrastructure
images to run your WebLogic domains in Kubernetes,
where the image location is specified in the domain resource's `domain.spec.image` attribute.
Oracle recommends obtaining such images
from the Oracle Container Registry (OCR)
or creating custom images using the WebLogic Image Tool.

There are three main options available for obtaining or creating such images,
where the option depends on your
[domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}):

* Use an image which only contains the WebLogic Server binaries.
  * This approach can be used for:
    * Domain in PV type domains.
    * Model in Image domains that leverage
      [auxiliary images]({{< relref "/userguide/managing-domains/model-in-image/auxiliary-images.md" >}}).
  * See:
    * [Understanding Oracle Container Registry WebLogic Server images](#understanding-oracle-container-registry-weblogic-server-images)
    * [Obtain WebLogic Server images from the Oracle Container Registry](#obtain-weblogic-server-images-from-the-oracle-container-registry)
    * [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)

* Create an image which contains both the WebLogic Server binaries
  and a WebLogic domain home directory.
  * This approach can be used for Domain in Image type domains.
  * See [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image).

* Create an image which contains WebLogic Server binaries
  and WebLogic Deploy Tooling files.
  * This approach can be used for Model in Image type domains where
    the WebLogic Deploy Tooling install and potentially also model files
    are supplied in the image.
  * See [Create a custom image with your model inside the image](#create-a-custom-image-with-your-model-inside-the-image).

#### Understanding Oracle Container Registry WebLogic Server images

TBD Tom update to reconcile same wording as in environments

{{% notice warning %}}
Oracle strongly recommends using images with up to date patches.
See [Ensuring you are using recently patched images](#ensuring-you-are-using-recently-patched-images).
{{% /notice %}}

TBD Monica:
- Is it confusing to say "WebLogic Server images" when this same applies to infra images?
- Derek thinks this is probably OK, but recommends asking Monica.
- Monica to Tom:
  - Derek is right, but it is sometimes better to say "WebLogic Server and Fusion Middleware Infrastructure images"
    because they have separate sets of images and installers.
  - Please add an introductory discussion that covers the following:
    - WebLogic Server images have a pre-installed Oracle Home
      with Oracle WebLogic Server and Coherence
    - Fusion Middleware Infrastructure images
      have a pre-installed Oracle Home with Oracle WebLogic Server with Java Required Files (JRF) and Coherence
      It contains the same binaries as those installed by the WebLogic generic installer
      and adds Fusion Middleware Control and Java Required Files (JRF)

The [Oracle Container Registry](https://container-registry.oracle.com/) (OCR)
contains images for licensed commercial Oracle software products
that you may use in your enterprise.
OCR supplies two types of WebLogic Server or Fusion Middleware Infrastructure images:

- General Availability (GA) images.
  - Located in OCR repositories "middleware/weblogic" and "middleware/fmw-infrastructure".
  - Updated quarterly.
  - Includes latest updates for Oracle Linux, and Java, but _not_ for Oracle WebLogic Server.
  - GA images are free to use and are subject to
    [Oracle Technology Network (OTN) Developer License Terms](https://www.oracle.com/downloads/licenses/standard-license.html),
    which include, but are not limited to:
    - Must only be used for the purpose of developing, testing, prototyping, and demonstrating applications.
    - Must _not_ be used for any data processing, business, commercial, or production purposes.
  - Examples:
    - `container-registry.oracle.com/middleware/weblogic:12.2.1.4-YYMMDD`
      - Includes JDK 8, Oracle Linux 7, and the GA Oracle WebLogic Server 12.2.1.4 generic distribution for the given date.
    - `container-registry.oracle.com/middleware/weblogic:12.2.1.4`
      - Includes latest JDK 8, latest Oracle Linux 7, and the GA Oracle WebLogic Server 12.2.1.4 generic distribution.

- Critical Patch Updates (CPU) images.
  - Located in OCR repositories "middleware/weblogic_cpu" and "middleware/fmw-infrastructure_cpu".
  - Updated quarterly (every CPU cycle).
  - Includes critical security fixes for Oracle Linux, Java, and Oracle WebLogic Server and Coherence.
  - Suitable for production use.
  - Examples:
    - `container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol7-211124`
      - Includes JDK 8u311, Oracle Linux 7u9, and the Oracle WebLogic Server 12.2.1.4 generic distribution October 2021 CPU.
    - `container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol7`
      - Includes latest JDK 8, latest Oracle Linux 7, and GA Oracle WebLogic Server 12.2.1.4 generic distribution CPU.

You may have noticed that the image tags may include keywords like `generic`, `slim`, etc.
This reflects the type of WebLogic distribution installed in the image's Oracle Home.
There are multiple types,
and the type usually can be determined by examining the image name and tag:

- `.../weblogic...:...generic...` or `.../weblogic...:...`:
  - The WebLogic generic image is supported for development and production deployment
    of WebLogic configurations using Docker.
  - Contains the same binaries as those installed by the WebLogic generic installer.
  - Note that if an image name does not have a string that specifies its WebLogic installer type
    (does _not_ embed the keyword `slim`, `dev`, or `generic`),
    then you can assume it is a WebLogic generic image.

- `.../weblogic...:...slim...`:
  - The WebLogic slim image is supported for development and production deployment
    of WebLogic configurations using Docker.
  - In order to reduce image size,
    it contains a subset of the binaries included in the WebLogic generic image:
    - The WebLogic Administration Console, WebLogic examples, WebLogic clients, Maven plug-ins,
      and Java DB have been removed. (Note that you can use the
      open source WebLogic Remote Console (TBDLink)
      as an alternative for the WebLogic Administration Console).
    - All binaries that remain included are
      the same as those in the WebLogic generic image.
  - If there are requirements to monitor the WebLogic configuration,
    they should be addressed using Prometheus and Grafana, or other alternatives.

- `.../weblogic...:...dev...`:
  - The WebLogic developer image is supported for development
    of WebLogic applications in Docker containers.
  - In order to reduce image size, it contains a subset
    of the binaries included in the WebLogic generic image:
    - WebLogic examples and Console help files have been removed
      (the WebLogic Administration Console is still included).
    - All binaries that remain included are the same as those in the WebLogic generic image.
  - This image type is primarily intended to provide a Docker image
    that is consistent with the WebLogic "quick installers" intended for development only.
    Production WebLogic domains should use the WebLogic generic or WebLogic slim images.

- `.../fmw-infrastructure...:...`:
  - The Fusion Middleware (FMW) Infrastructure image is supported for
    development and production deployment of FMW configurations using Docker.
  - It contains the same binaries as those installed by the WebLogic generic installer
    and adds Fusion Middleware Control and Java Required Files (JRF)

You may also have noticed there are "dated" and "undated" OCR images
depending on whether the name tags include an embedded date stamp
of the form `YYMMDD`.
Unlike dated images with an embedded date stamp,
which represent a specific version that was released on a specific date,
undated images are periodically updated to
the latest available versions of their GA or CPU equivalents.
_Therefore they change over time in the repository
even though their name and tag remain the same._

{{% notice note %}}
All of the OCR images that are described in this section are built using
the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) (WIT).
Customers can use WIT to build their own WebLogic Server images
(with the latest Oracle Linux images, Java updates, and WebLogic Server patches),
apply one-off patches to existing OCR images,
or overlay their own files and applications on top of an OCR image.
See [Contents](#contents) for information about using this tool
to create custom WebLogic Server images for the WebLogic Kubernetes Operator.
{{% /notice %}}

#### Obtain WebLogic Server images from the Oracle Container Registry

TBD Tom - use warning wording that Monica and I agreed on in "environments":

{{% notice warning %}}
Oracle strongly recommends using images with up to date patches.
See [Ensuring you are using recently patched images](#ensuring-you-are-using-recently-patched-images).
{{% /notice %}}

The Oracle Container Registry (OCR) contains images for licensed commercial Oracle software products
that you may use in your enterprise.
To access the Oracle Registry Server, you must have an Oracle Single Sign-On (SSO) account.
OCR provides a web interface that allows an administrator to authenticate
and then to select the images for the software that your organization wishes to use.
Oracle Standard Terms and Restrictions terms must be agreed to using the web interface.
After the Oracle Standard Terms and Restrictions have been accepted,
you can pull images of the software from OCR using the standard `docker pull` command.

For example, to use docker to pull an image from OCR:

1. Accept the Oracle Standard Terms and Restrictions for the Oracle software images that you intend to deploy:

   - In a web browser, navigate to
     [https://container-registry.oracle.com](https://container-registry.oracle.com)
     and log in using the Oracle Single Sign-On (SSO) authentication service.
     If you do not already have SSO credentials,
     then at the top of the page, click the Sign In link to create them.

   - Use the web interface to accept the Oracle Standard Terms and Restrictions
     for the Oracle software images that you intend to deploy.
     For example, click the "Middleware" button, click the "weblogic_cpu" link,
     and follow the prompts to sign in with your SSO and accept the terms.

     Your acceptance of these terms is stored in a database that links the software images
     to your Oracle Single Sign-On login credentials.

   **Note**:
   This step is only needed once for each image name (not the tag level).
   For example, if you accept the terms for the "weblogic_cpu"
   link in the "middleware" repository, then
   the acceptance applies to all versions of WebLogic CPU images.

1. Log docker in to the Oracle Container Registry:

   ```shell
   $ docker login container-registry.oracle.com
   ```

1. Use docker to pull the desired image:

   ```shell
   $ docker pull container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol8
   ```

1. Use docker to display an inventory of your local image cache:

   ```shell
   $ docker images
   ```

1. If desired, you can:

   * Check the WLS version with:
     ```text
     $ docker run \
       container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol8 \
       sh -c 'source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh > /dev/null 2>&1 && java weblogic.version'
     ```

   * Check the WLS patches with:
     ```text
     $ docker run \
       container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol8 \
       sh -c '$ORACLE_HOME/OPatch/opatch lspatches'
     ```

   * If you have images that were generated using the WebLogic Image Tool, including OCR images,
     and you have installed the tool, then you can obtain useful version and patch information
     using the inspect command (TBDLink). For example:
     ```
     $ imagetool inspect \
       --image=container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol8 \
       --patches
     ```
     TBD Tom test above command
    
{{% notice note %}}
The operator requires WebLogic Server 12.2.1.3.0 or later.
Note that WebLogic Server 12.2.1.3 images must contain patch 29135930,
which is included in OCR 12.2.1.3 GA and CPU images.
{{% /notice %}}

#### Set up Kubernetes to access a container registry

If Kubernetes needs to directly obtain a WebLogic Server image for a domain resource
from a container image registry or repository that requires authentication,
such as the Oracle Container Registry (OCR), then:

- A Kubernetes "docker-registry" secret containing the registry credentials must be created
  in the same namespace as domain resources with a `domain.spec.image` attribute that reference the image.
  For example, to create a secret with OCR credentials, issue the following command:
  ```shell
  $ kubectl create secret docker-registry SECRET_NAME \
    -n NAMESPACE_WHERE_YOU_DEPLOY_DOMAINS \
    --docker-server=container-registry.oracle.com \
    --docker-username=YOUR_USERNAME \
    --docker-password=YOUR_PASSWORD \
    --docker-email=YOUR_EMAIL
  ```

- The name of the secret must be added to these domain resources using
  the `domain.spec.imagePullSecrets` field. For example:
  ```text
  ...
  spec:
  ...
    imagePullSecrets:
    - name: SECRET_NAME
  ...
  ```

- If you are using the Oracle Container Registry, then
  you must use the web interface to accept the Oracle Standard Terms and Restrictions
  for the Oracle software images that you intend to deploy.
  You only need to do this once for a particular image.
  See [Obtain WebLogic Server images from the Oracle Container Registry](#obtain-weblogic-server-images-from-the-oracle-container-registry).

Alternatively, it may be preferable to manually pull an image in advance
on each Kubernetes worker node in you Kubernetes cluster,
see [Obtain WebLogic Server images from the Oracle Container Registry](#obtain-weblogic-server-images-from-the-oracle-container-registry).
If you choose this approach, then a Kubernetes secret is not required
and your domain resource `domain.spec.imagePullPolicy` must be set to `Never` or `IfNotPresent`.

#### Ensuring you are using recently patched images

Please review the following guidance
to ensure that you are using recently patched images:

- For production deployments,
  Oracle requires using
  fully patched custom images that you generate yourself,
  or Critical Patch Update (CPU) images from the
  [Oracle Container Registry](https://container-registry.oracle.com/) (OCR).
  Such images contain `_cpu` in their image name,
  for example `container-registry.oracle.com/middleware/weblogic_cpu:TAG`.

- General Availability (GA) images are not licensable or suitable for production use.
  The latest GA images include the latest security patches for Oracle Linux and Java,
  and do not include the latest patches for WebLogic Server.

- Locally cached OCR images that do not have a date stamp
  embedded in their tag:
  - May have a corresponding newer version in the registry.
  - If so, then such images will remain out of date until
    one of the following occurs:
    - The images are explicitly pulled again on every host machine with such a cached image.
    - The images are explicitly deleted from every host machine with such a cached image.
    - The images are implicitly pulled again due to `spec.image`
      referencing a repository with an updated image,
      and having a domain resource `spec.imagePullPolicy`
      of `Always` when a pod starts.

See the [Overview](#overview) for information about OCR and custom images.

See
[supported environments]({{< relref "/userguide/platforms/environments.md" >}})
for information about licensed access to WebLogic patches and CPU images.

#### Create a custom image with patches applied

TBD Create in title --> Generate

You can use the
[WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) (WIT)
`create`, `update`, or `rebase` commands to
generate a custom WebLogic Server image
with patches applied from a base OS image:

- WIT `create` command:

  - Creates a new WebLogic image from a base OS image.

  - Optionally generates a domain home in the image using WLST or WDT
    (for the Domain in Image domain home source type).
    See also [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image).

    **Important:**
    This command is not suitable for updating an existing domain home
    in existing Domain in Image images
    when the update is intended for a running domain. Use `rebase` instead, 
    or shutdown the running domain entirely before applying the new image.
    See TBD for background.

  - Optionally includes a WDT install and model files in the image
    (for the Model in Image domain home source type).
    See also [Create a custom image with your model inside the image](#create-a-custom-image-with-your-model-inside-the-image).

- WIT `rebase` command:

  - The `rebase` command is used for the Domain in Image
    and Model in Image domain home source types:

    - For Domain in Image, it:
      - Creates a new WebLogic image and copies an existing WebLogic domain home
        from an older image to the new image.
      - The created image is suitable for deploying to an already running
        domain with an older version of the domain home.
      - Optionally updates the domain home using WLST or WDT.
    - For Model in Image, it:
      - Creates a new WebLogic image and copies existing WDT install and models
        from an existing image to the new image.
      - Optionally updates the WDT install and models.

  - The new image can be created in one of two ways:
    - As a layer on an existing
      WebLogic image in the repository
      that doesn't already have a domain home (confirm with Derek)
      (similar to `update`).
    - Or as a new WebLogic image from a base OS image (similar to `create`)
      where. This option is usually preferred because
      it results in the smallest WebLogic Server image size. TBD?

- WIT `update` command:
  - Creates a new WebLogic image layered on an existing WebLogic image
    (which is turn layered on a base OS image).
  - Optionally generates a domain home in the image using WLST or WDT
    (for the Domain in Image domain home source type).

    **Important:** 
    This command is not suitable for updating the domain home in 
    an existing Domain in Image image
    when the update is intended for a running domain.
    Use `rebase` instead, 
    or shutdown the running domain entirely before applying the new image.
    See TBD for background.
  - Optionally includes a WDT install and model files in the image (for the Model in Image domain home soure type).
  - **Note:** Patching using the `update` command results
    in a larger WebLogic Server image size than `rebase` or `create`.
  - TBD what does `--pull` mean in this context? The base image is already designated in `--fromImage`. Answer from Derek: the "fromImage" will be pulled if SHA is out of date versus local cache image... `--pull` is a no-op if there's no repo in image name (no forward slash `/`).

Here's an example of using the WIT `create` command to create 
a base WebLogic Server image:

1. To download the Image Tool,
   follow the [Setup](https://oracle.github.io/weblogic-image-tool/quickstart/setup/) instructions
   and refer to the [Quick Start](https://oracle.github.io/weblogic-image-tool/quickstart/quickstart/) Guide.
   TBD double check this

1. Download your desired JRE installer from the 
   [Oracle Technology Network Java download page](https://www.oracle.com/java/technologies/downloads/)
   or from the
   [Oracle Software Delivery Cloud (OSDC)](https://edelivery.oracle.com/osdc/faces/Home.jspx).

1. Download your desired WebLogic Server installer from the
   [Oracle Technology Network WebLogic Server download page](https://www.oracle.com/middleware/technologies/weblogic-server-installers-downloads.html)
   or from the
   [Oracle Software Delivery Cloud (OSDC)](https://edelivery.oracle.com/osdc/faces/Home.jspx).

   Note that the WebLogic Server installers may not be fully patched.
   You will use the "--patches" or "--recommendedPatches" Image Tool options in a later step to add patches.

1. Add the installers to your WIT cache using the
   [WIT `cache` command](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/).
   For example:

   ```shell
   $ imagetool cache addInstaller \
     --type=jdk \
     --version=8u291 \
     --path=/home/acmeuser/wls-installers/jre-8u291-linux-x64.tar.gz
   ```
   ```shell
   $ imagetool cache addInstaller \
     --type=wls \
     --version=12.2.1.3.0 \
     --path=/home/acmeuser/wls-installers/fmw_12.2.1.3.0_wls_Disk1_1of1.zip
   ```
   ```shell
   $ imagetool cache addInstaller \
     --type=wls \
     --version=12.2.1.4.0 \
     --path=/home/acmeuser/wls-installers/fmw_12.2.1.4.0_wls_Disk1_1of1.zip
   ```

   For details, see the WIT
   [Quick Start](https://oracle.github.io/weblogic-image-tool/quickstart/quickstart/) guide.

1. Use the WIT [Create Tool](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-image/)
   to build the image using a default Oracle Linux image as its base,
   download the patches,
   and apply the patches.

   - Create a WebLogic Server image named `minimal_weblogic:12.2.1.3` with
     - the WebLogic Server 12.2.1.3.0 generic installer
     - JDK 8u291
     - the latest version of the Oracle Linux 7 slim container image
     - the minimal patches required for the operator to run a 12.2.1.3 image
       (patches 29135930 and 27117282).

     ```shell
     $ imagetool create \
       --tag=minimal_weblogic:12.2.1.3 \
       --pull \
       --jdkVersion=8u291 \
       --type=wls \
       --version=12.2.1.3.0 \
       --patches=29135930_12.2.1.3.0,27117282_12.2.1.3.0 \
       --user=myusername@mycompany.com \
       --passwordEnv=MYPWD
     ```

   - Create a WebLogic Server image named `latest_weblogic:12.2.1.4` with
     - the WebLogic Server 12.2.1.4.0 slim installer
     - JDK 8u291
     - the latest version of the Oracle Linux 7 slim container image
     - the latest quarterly Patch Set Update (PSU), which include security fixes, or with one-off patches.

     ```shell
     $ imagetool create \
       --tag latest_weblogic:12.2.1.4 \
       --pull \
       --jdkVersion=8u291 \
       --type=wls \
       --version=12.2.1.4.0 \
       --recommendedPatches \
       --user testuser@xyz.com \
       --passwordEnv=MYPWD
     ```

1. After the tool creates the image, verify that the image is in your local repository:

    ```shell
    $ docker images
    ```

**Notes:**
- The `--type` parameter designates the type of WebLogic Server,
  Fusion Middleware (FMW) Infrastructure install, or TBD (Derek),
  to include in the generated image. For example,
  the `wls` type corresponds to the WebLogic Server (WLS) generic install,
  the `wlsslim` type to the WLS slim install,
  and the `wlsdev` type to the WLS developer install.
  See
  [Understanding Oracle Container Registry WebLogic Server images](#understanding-oracle-container-registry-weblogic-server-images)
  for a discussion of each install type.
- The `--recommendedPatches` parameter finds and applies
  the latest PatchSet Update (PSU)
  and recommended patches. This takes precedence over `--latestPSU`.
  TBD confirm with Derek this doesn't cover JDK patches...
- These sample commands use a default base image,
  which is an Oracle Linux OS image,
  and downloads (pulls) this image only if it is not already cached locally.
  You can use `docker images` to view your image cache.
  The `--pull` parameter download (pulls) a newer version of the base
  image if there is already a locally cached OS image
  which is older than the newest version found in OCR.
  TBD Does `--pull` pull apply to `update --fromImage` image? Derek
- To enable WIT to download patches,
  you must supply your My Oracle Support credentials.
  This example assumes that you are supplying your My Oracle Support password by putting
  it in a shell environment variable named `MYPWD`.
  - TBD: Should we use the term "My Oracle Support" credentials here?
    Elsewhere, we describe these credentials as "Oracle Single Sign-On".
    Is this having a "My Oracle Support" password a way of saying one has permission to get the patches
    beyond simply having an SSO?
- See the [WebLogic Image Tool User Guide](https://oracle.github.io/weblogic-image-tool/userguide/tools/)
  for details about each parameter.

#### Create a custom image with your domain inside the image

You can also create an image with the WebLogic domain inside the image.
[Samples]({{< relref "/samples/domains/domain-home-in-image/_index.md" >}})
are provided that demonstrate how to create the image using either:

* WLST to define the domain, or
* [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) to define the domain.

In these samples, you will see a reference to a "base" or `--fromImage` image.
You should use an image with the mandatory patches installed as this base image.
This image could be an OCR image or a custom image.
See
[Obtain WebLogic Server images from the Oracle Container Registry](#obtain-weblogic-server-images-from-the-oracle-container-registry)
and
[Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)).

TBD Tom These samples use the WIT `update` command. Explain when to use `rebase` or `create` instead, and how...

{{% notice warning %}}
Oracle strongly recommends storing a domain image as private in the registry.
A container image that contains a WebLogic domain home has sensitive information
including keys and credentials that are used to access external resources
(for example, the data source password). For more information, see
[WebLogic domain in container image protection]({{<relref "/security/domain-security/image-protection#weblogic-domain-in-container-image-protection">}}).
{{% /notice %}}

#### Create a custom image with your model inside the image

TBD Tom point to model sample for `update`, to ^^^ for `create`, and to `rebase` below for rebase.
TBD Tom mention auxiliary image alternative...

In these samples, you will see a reference to a "base" or `--fromImage` image.
This image could be an OCR image or a custom image.
See
[Obtain WebLogic Server images from the Oracle Container Registry](#obtain-weblogic-server-images-from-the-oracle-container-registry)
and
[Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)).


#### Apply patched images to a running domain

When updating the WebLogic binaries of a running domain in Kubernetes with a patched container image,
the operator applies the update in a zero downtime fashion.
The procedure for the operator to update the running domain differs depending on the
[domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).
One difference between the domain home source types is the location of the domain home:

For _Model in Image (MII) without auxiliary images_ and _Domain in Image_,
before the operator can apply the update,
the patched container images must be modified to add the domain home or a WDT model and archive.
You use WebLogic Image Tool (WIT) [Rebase Image](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/)
to update the Oracle Home of the original image using the patched Oracle Home from a patched container image.

In all cases, you edit the
[Domain Resource]({{< relref "/userguide/managing-domains/domain-resource#domain-resource-attribute-references" >}})
to inform the operator of the name of the new patched image so that it can manage the update of the WebLogic domain.

For a broader description of managing the evolution and mutation
of container images to run WebLogic Server in Kubernetes,
see [CI/CD]({{< relref "/userguide/cicd/_index.md" >}}).

TBD Tom The following sections are repetitive/verbose. Consolidate/remove
plus replace with bullets. Plus move 'rebase' details to earlier sections.

##### Domain in PV

For the Domain in PV domain home source type,
the container image only contains the JDK and WebLogic Server binaries,
and its domain home is located in a Persistent Volume (PV)
where the domain home is generated by the user.

To apply patched images to a running domain,
edit the Domain Resource image reference with the new image name/tag
(for example, `oracle/weblogic:12.2.1.4-patched`).
Then, the operator will automatically perform a
[rolling restart]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.
For information on server restarts,
see [Restarting]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting.md" >}}).

##### Model in Image with auxiliary images

For the Model in Image domain home source type when using auxiliary images:
- The container image only contains the JDK and WebLogic Server binaries.
- The [WebLogic Deployment Tooling](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) installation,
  WDT model files, and application archive files,
  are located in auxiliary images TBD link, or elsewhere.
- The domain home is generated by the operator during runtime.

To apply patched images to a running domain,
edit the Domain Resource image reference with the new image name/tag (for example, `oracle/weblogic:12.2.1.4-patched`).
Then, the operator performs a [rolling restart]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.
For information on server restarts, see [Restarting]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting.md" >}}).

TBD Tom mention on-line update option.

##### Model in Image without auxiliary images

TBD Derek discuss with Derek pros and cons of using `rebase` instead of `create`.

For the Model in Image domain home source type _without_ using auxiliary images:
- The container image contains the JDK, WebLogic Server binaries,
  a [WebLogic Deployment Tooling](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) installation,
  and potentially also WDT model files application archive files.
- The domain home is generated by the operator during runtime.

TBD Tom move rebase to Create a custom image with your model inside the image.

Use the WIT [`rebase`](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/) command
to update the Oracle Home for an existing image with the model and archive files in the image using the patched Oracle Home from a
patched container image. For example, the following command
copies a WDT model and WDT archive from the source image `mydomain:v1`,
to a new image, `mydomain:v2`,
ased on a target image named `oracle/weblogic:generic-12.2.1.4.0-patched`.

**Note**: Oracle Home and the JDK must be installed in the same directories on each image.

```shell
$ imagetool rebase \
  --tag mydomain:v2 \
  --sourceImage mydomain:v1 \
  --targetImage oracle/weblogic:generic-12.2.1.4.0-patched  \
  --wdtModelOnly
```

Then, edit the Domain Resource `domain.spec.image` attribute with the new image name/tag (`mydomain:2`).

TBD Tom mention on-line update option.

##### Domain in Image

For the Domain in Image domain home source type:
- The container image contains the JDK, WebLogic Server binaries, and domain home.
- The domain home is generated during image creation using either WLST or WDT,
  usually with the assistance of the WebLogic Image Tool (WIT).

Use the WIT [`rebase`](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/)
command to update the Oracle Home
for an existing domain image using the patched Oracle Home from a patched container image.

Example: WIT copies the domain from the source image, `mydomain:v1`, to a new image, `mydomain:v2`, based on a target
image named `oracle/weblogic:12.2.1.4-patched`.

**Note**: Oracle Home and the JDK must be installed in the same directories on each image.

TBD Tom move rebase to Create a custom image with your domain inside the image.

```shell
$ imagetool rebase \
  --tag mydomain:v2 \
  --sourceImage mydomain:v1 \
  --targetImage oracle/weblogic:12.2.1.4-patched
```

TBD Ask Derek for an example that uses rebase to change the domain home - not just the WL version... Also
ask him to update the corresponding documentation in the imagetool (according to Monica
there is no such documentation currently.)

Then, edit the Domain Resource `domain.spec.image` attribute with the new image name/tag (`mydomain:2`).
Then, the operator performs a
[rolling update]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}}) of the domain,
updating the Oracle Home of each server pod.


### more TBD

TBD Derek Tom Rosemary:
 - TBD Derek wonders if the overview sectoin could be organized more clearly, as does Tom,
   but neither of us could come up with a better approach on the spot.
 - Derek has volunteered to give it a shot, or we can ask Rosemary.

- TBD Tom: We used the term 'standard images' in some places and 'WebLogic Server images' in others.
  Monica: use "WLS Images" or "FMW Infrastructore Images" throughout...
- TBD Tom: Update all references to WL images (references to container-registry, etc), in the entire doc set, and sample YAML?, to:
  - (a) reference back to this file, especially the "use latest patches" section
  - (b) avoid repeating information that is in this file (instead link to this file)
  - (c) make it clear when the image name is only suitable for development (not fully patched!).

- TBD Work with Monica and Derek to come up with a way to discourage
    users from using unpatched images. This discussion needs
    to discuss pros/cons of the various imagePullPullPolicies
    (Always typically not suitable for production due
     to latency/connectivity and/or potential for grabbing
     an image update that has not been tested).
  Monica: We can do this in detail in a future pull.


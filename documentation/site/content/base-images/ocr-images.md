---
title: "OCR images"
date: 2019-02-23T16:45:55-05:00
weight: 1
description: "Obtain and inspect images for WebLogic Server or Fusion Middleware Infrastructure deployments from the Oracle Container Registry (OCR)."
---


{{< table_of_contents >}}


### Overview

A container image with WebLogic Server or Fusion Middleware Infrastructure is required to run WebLogic domains in Kubernetes.
Oracle recommends obtaining these WebLogic images from the Oracle Container Registry (OCR)
or creating custom images using the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/).
Note that all of the OCR images that are described in this document are built using the WebLogic Image Tool (WIT).
You can also use WIT to build your own WebLogic Server or Fusion Middleware Infrastructure images.
For more information, see [Create custom images]({{< relref "/base-images/custom-images.md" >}}).

This document describes how to obtain and inspect container images
with WebLogic Server or Fusion Middleware Infrastructure
from the Oracle Container Registry (OCR).


### Understand Oracle Container Registry images

The Oracle Container Registry (OCR) is located at [https://container-registry.oracle.com/](https://container-registry.oracle.com/) and contains images for licensed commercial Oracle software products that may be used in your enterprise for deployment using a container engine and Kubernetes.

OCR contains WebLogic Server images, which have a pre-installed Oracle Home with Oracle WebLogic Server and Coherence. OCR, also, contains Fusion Middleware Infrastructure images, which have a pre-installed Oracle Home with Oracle WebLogic Server, Coherence, Fusion Middleware Control, and Java Required Files (JRF). **NOTE**: Oracle strongly recommends that you use _only_ images with the latest set of recommended patches applied.

{{% notice note %}}
As of June, 2023, Oracle WebLogic Server 12.2.1.3 is no longer supported. The last Critical Patch Updates (CPU) images for WebLogic Server 12.2.1.3 were published in April, 2023. As of December, 2022, Fusion Middleware 12.2.1.3 is no longer supported.  The last CPU images for FMW Infrastructure 12.2.1.3 were published in October, 2022.
{{% /notice %}}

See the following sections for information about OCR images:
- [Compare General Availability to Critical Patch Updates images](#compare-general-availability-to-critical-patch-updates-images)
- [WebLogic distribution installer type](#weblogic-distribution-installer-type)
- [Compare "dated" and "undated" images](#compare-dated-and-undated-images)
- [Example OCR image names](#example-ocr-image-names)

#### Compare General Availability to Critical Patch Updates images

- General Availability (GA) images:
  - Located in the OCR repositories `middleware/weblogic` and `middleware/fmw-infrastructure`.
  - Updated quarterly with Oracle Linux and Oracle Java security updates.
  - GA images are free to use and are subject to Oracle Technology Network (OTN) [Developer License Terms](https://www.oracle.com/downloads/licenses/standard-license.html), which include, but are not limited to:
    - Must be used only for the purpose of developing, testing, prototyping, and demonstrating applications.
    - Must _not_ be used for any data processing, business, commercial, or production purposes.

- Critical Patch Updates (CPU) images:
  - Located in the OCR repositories `middleware/weblogic_cpu` and `middleware/fmw-infrastructure_cpu`.
  - Updated quarterly with Oracle Linux and Oracle Java security updates.
  - Updated quarterly with the latest Patch Set Update (PSU) for WebLogic Server or Fusion Middleware, and all  of the recommended security patches for products included in that distribution.
  - Suitable for production use.

  {{% notice warning %}}
  WebLogic Server GA images and Fusion Middleware Infrastructure GA images on OCR **do not include the latest security patches** for WebLogic Server or Fusion Middleware Infrastructure. Oracle strongly recommends using images with the latest set of recommended patches applied, such as the Critical Patch Updates (CPU) images provided quarterly on OCR or custom generated images using the WebLogic Image Tool (WIT) with the [`--recommendedPatches`]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}}) option.  See [Ensure you are using recently patched images](#ensure-you-are-using-recently-patched-images).
  {{% /notice %}}

#### WebLogic distribution installer type

OCR image tags may include keywords like `generic`, `slim`, and such.
This reflects the type of WebLogic distribution installed in the image's Oracle Home.
There are multiple types
and usually, the type can be determined by examining the image name and tag:

- `.../weblogic...:...generic...`
  - The WebLogic generic image.
  - Contains the same binaries as those installed by the WebLogic generic installer.

- `.../weblogic...:...slim...`:
  - The WebLogic slim image.
  - To reduce image size,
    it contains a subset of the binaries included in the WebLogic generic image:
    - The WebLogic Server Administration Console, WebLogic examples, WebLogic clients, Maven plug-ins,
      and Java DB have been removed.
    - All binaries that remain included are
      the same as those in the WebLogic generic image.
  - If there are requirements to monitor the WebLogic configuration, then:
    - You should address them using Prometheus and Grafana, or other alternatives.
    - Note that you can use the open source
      [WebLogic Remote Console]({{< relref "/managing-domains/accessing-the-domain/remote-admin-console.md" >}})
      as an alternative for the WebLogic Server Administration Console.

- `.../weblogic...:...dev...`:
  - The WebLogic developer image.
  - To reduce image size,
    it contains a subset of the binaries included in the WebLogic generic image:
    - WebLogic examples and Console help files have been removed
      (the WebLogic Server Administration Console is still included).
    - All binaries that remain included are the same as those in the WebLogic generic image.
  - This image type is primarily intended to provide a container image
    that is consistent with the WebLogic "quick installers" intended for development only.
    **NOTE**: Production WebLogic domains should use the WebLogic generic, WebLogic slim,
    or Fusion Middleware Infrastructure images.

- `.../fmw-infrastructure...:...`:
  - The Fusion Middleware (FMW) Infrastructure image.
  - Contains the same binaries as those installed by the WebLogic generic installer
    and adds Fusion Middleware Control and Java Required Files (JRF).

- None of the above

  - If the tag portion of a `.../weblogic...` OCR image name
    does _not_ include a keyword like `slim`, `dev`, or `generic`,
    then you can assume that the image contains
    the same binaries as those installed by the WebLogic generic installer.

#### Compare "dated" and "undated" images

OCR images are "dated" or "undated"
depending on whether the name tags include an embedded date stamp
in the form `YYMMDD`, which represent a specific version that was released on a specific date.
Unlike dated images, undated images are periodically updated to
the latest available versions of their GA or CPU equivalents.
Therefore, undated images _change over time in the repository_
even though their name and tag remain the same.

#### Example OCR image names

Here are some example WebLogic Server Oracle Container Repository (OCR) images,
where the names are abbreviated to omit their `container-registry.oracle.com/middleware/` prefix:

| Abbreviated Name | Description |
|-|-|
|`weblogic:12.2.1.4`|GA image with latest JDK 8, latest Oracle Linux 7, and the GA Oracle WebLogic Server 12.2.1.4 generic distribution. Note that this image has no date stamp, so it can change over time with potential updates to JDK 8 and Oracle Linux 7.|
|`weblogic:12.2.1.4-YYMMDD`|GA image with JDK 8, Oracle Linux 7, and the GA Oracle WebLogic Server 12.2.1.4 generic distribution for the given date.|
|`weblogic_cpu:12.2.1.4-generic-jdk8-ol7`|CPU image with latest JDK 8, latest Oracle Linux 7, and the GA Oracle WebLogic Server 12.2.1.4 generic distribution CPU. Note that this image has no date stamp, so it can change over time with potential updates to JDK 8, to Oracle Linux 7, and to the latest CPU.|
|`weblogic_cpu:12.2.1.4-slim-jdk8-ol8-220204`|CPU image with latest JDK 8, latest Oracle Linux 8, and the GA Oracle WebLogic Server 12.2.1.4 slim distribution, January 2022 CPU.|

### Obtain images from the Oracle Container Registry

The Oracle Container Registry (OCR) contains images for licensed commercial Oracle software products
that you may use in your enterprise.
To access the Oracle Registry Server, you must have an Oracle Single Sign-On (SSO) account.
OCR provides a web interface that allows an administrator to authenticate
and then to select the images for the software that your organization wishes to use.
You must agree to the Oracle Standard Terms and Restrictions using the web interface.
Then,
you can pull images of the software from OCR using the standard `docker pull` command
while using your SSO for your `docker login` credentials.

For example, to use Docker to pull an image from OCR:

1. Accept the Oracle Standard Terms and Restrictions
   for the Oracle software images that you intend to deploy:

   - In a web browser, navigate to
     [https://container-registry.oracle.com](https://container-registry.oracle.com)
     and log in using the Oracle Single Sign-On (SSO) authentication service.
     If you do not already have SSO credentials,
     then at the top of the page, click **Sign In** to create them.

   - Use the web interface to accept the Oracle Standard Terms and Restrictions
     for the Oracle software images that you intend to deploy:

     1. Click **Middleware**.

     1. Select one of `weblogic`, `weblogic_cpu`, `fmw-infrastructure_cpu`, or such,
        depending in your [image type](#weblogic-distribution-installer-type).

        For example, if you are following the operator Quick Start guide
        (which uses WebLogic GA images), then select `weblogic`. **NOTE**:
        GA images are suitable for demonstration and development purposes _only_ where the environments
        are not available from the public Internet; they are _not_
        acceptable for production use. In production, you should always use CPU (patched) images
        from OCR or create your images using the [WebLogic Image Tool]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}})
        (WIT) with the `--recommendedPatches` option.  

     1. Click **Continue**.


     1. Follow the prompts to sign in with your SSO and accept the terms.

        The newly available patched images in OCR require accepting a second,
        different Terms and Restrictions agreement.
        Your acceptance of these terms is stored in a database
        that links the software images
        to your Oracle Single Sign-On login credentials.
        This database is automatically checked when
        you use `docker pull` to obtain images from OCR.

   **NOTE**: This step is needed only once for each image name (not the tag level).
   For example, if you accept the terms for `weblogic_cpu`
   in the `middleware` repository, then
   the acceptance applies to all versions of WebLogic CPU images.

1. Provide Docker with credentials for accessing
   the Oracle Container Registry. For example,
   the following command will prompt for your SSO credentials:

   ```shell
   $ docker login container-registry.oracle.com
   ```

1. Use Docker to pull the desired image:

   ```shell
   $ docker pull container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol8
   ```

1. Use Docker to display an inventory of your local image cache:

   ```shell
   $ docker images
   ```

1. If desired, then you can [inspect](#inspect-images) the content of the image.

**NOTES**:
- If you are using a multi-node Kubernetes cluster,
  or your Kubernetes cluster is remote from your locally created or pulled domain image,
  then additional steps are usually required to ensure that your Kubernetes cluster can access the image.
  See [Access domain images]({{< relref "/base-images/access-images.md" >}}).
- The operator requires domain images to contain WebLogic Server 12.2.1.4.0 or later.

### Inspect images

If you have local access to a WebLogic Server or Fusion Middleware Infrastructure image
and the image originates from the Oracle Container Registry or
was created using the WebLogic Image Tool,
then you can use the following commands to determine their contents:

* Check the WLS version:
  ```text
  $ docker run \
    container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol8 \
    sh -c 'source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh > /dev/null 2>&1 && java weblogic.version'
  ```

* Check the WLS patches:
  ```text
  $ docker run \
  container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol8 \
  sh -c '$ORACLE_HOME/OPatch/opatch lspatches'
  ```

* If you have installed the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) (WIT),
  then you can obtain useful version and patch information using the WIT
  [inspect](https://oracle.github.io/weblogic-image-tool/userguide/tools/inspect-image/) command with
  the `--patches` option.
  For example:
  ```
  $ /tmp/imagetool/bin/imagetool.sh inspect \
  --image=container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol8 \
  --patches
  ```
### Ensure you are using recently patched images

You **should not use** images without the latest set of recommended patches applied.
Please review the following guidance to ensure that you are using recently patched images:

  - For production deployments,
    Oracle requires using
    fully patched custom images that you generate yourself
    or Critical Patch Update (CPU) images from the
    Oracle Container Registry (OCR).
    CPU images contain `_cpu` in their image name,
    for example `container-registry.oracle.com/middleware/weblogic_cpu:TAG`.

  - General Availability (GA) images **do not include** the latest security patches for WebLogic Server
    or Fusion Middleware Infrastructure. They are not licensable and are **not suitable for production use**.

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

  For detailed information about OCR image naming and the differences between GA and CPU images,
  see [Understand Oracle Container Registry images](#understand-oracle-container-registry-images).

  To determine the patches and versions of software within a particular image, see [Inspect images](#inspect-images).

  For information about licensed access to WebLogic patches and CPU images, see [Supported environments]({{< relref "/introduction/platforms/environments.md" >}}).

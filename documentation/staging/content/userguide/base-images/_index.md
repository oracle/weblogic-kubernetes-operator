---
title: "WebLogic Server images"
date: 2019-02-23T16:45:55-05:00
weight: 6
description: "Create or obtain WebLogic Server images."
---

#### Contents

* [Overview](#overview)
* [Understanding Oracle Container Registry WebLogic Server images](#understanding-oracle-container-registry-weblogic-server-images)
* [Obtain WebLogic Server images from the Oracle Container Registry](#obtain-weblogic-server-images-from-the-oracle-container-registry)
* [Set up Kubernetes to access a container registry](#set-up-kubernetes-to-access-a-container-registry)
* [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)
* [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image)
* [Patch WebLogic Server images](#patch-weblogic-server-images)
* [Apply patched images to a running domain](#apply-patched-images-to-a-running-domain)

TBD:
- We used the term 'standard images' in some places and 'WebLogic Server images' in others.
  - Is it OK to use 'WebLogic Server images' throughout?
  - Does this also cover 'FMW' images?
- Update all references to WL images (references to container-registry, etc), in the entire doc set to:
  - (a) reference back to this file
  - (b) avoid repeating information that is in this file (instead link to this file)
  - (c) make it clear when the image name is only suitable for development (not fully patched!).

#### Overview

You will need WebLogic Server images to run your WebLogic domains in Kubernetes.
There are three main options available:

* Use an image which only contains the WebLogic Server binaries.
  * This approach can be used for:
    * Domain in PV domains.
    * Model in Image domains where all model files are supplied externally to the `domain.spec.image` image.
      For example, a Model in Image domain that uses auxiliary images to store model files.
  * See:
    * [Obtain WebLogic Server images from the Oracle Container Registry](#obtain-weblogic-server-images-from-the-oracle-container-registry)
    * [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)

* Create an image which contains both the WebLogic Server binaries and a WebLogic domain directory.
  * This approach can be used for Domain in Image domains.
  * See [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image).

* Create an image which contains WebLogic Server binaries and WebLogic Deploy Tooling files.
  * This approach can be used for Model in Image domains where 
    the WebLogic Deploy Tooling install and potentially also model files are supplied in the `domain.spec.image` image.
  * See TBD

If you are unfamiliar with the Model in Image, Domain in PV, and Domain in Image domain types,
then see [Choose a domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

TBD link to other interesting information in this file here, for example:
- For a description of WebLogic Server image support, licensing, and  TBD

#### Understanding Oracle Container Registry WebLogic Server images

{{% notice warning %}}
For production deployments,
Oracle requires using dated Critical Patch Update (CPU) images from the
[Oracle Container Registry](https://container-registry.oracle.com/) (OCR)
with an image name that contains "_cpu" and an embedded date stamp, 
or fully patched custom images that you generate yourself.
General Availabity (GA) images are not licensable or suitable for production use.
{{% /notice %}}

{{% notice note %}}
All of the OCR images that are described in this section are built using
the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) (WIT).
Customers can use WIT to build their own WebLogic Server images
(with the latest Oracle Linux images, Java updates, and WebLogic Server patches),
apply one-off patches to existing OCR images,
or overlay their own files and applications on top of an OCR image.
See [Contents](#contents) for information about using this tool in combination with
the WebLogic Kubernetes Operator.
{{% /notice %}}

TBD:
- Work with Monica to better distinguish the case for those do not have support,
  but only have development only licenses.
- Is any image type available to someone with a free developer only license?

With WebLogic Server licenses and support, customers have access to:
- The latest WebLogic Server images which bundle Java SE and the latest slim Oracle Linux images.
- Oracle support for Linux.
- Oracle support for WebLogic Server images.

Note that WebLogic Server licenses and support do _not_ include customer entitlements
for direct access to Oracle Linux support or Unbreakable Linux Network
(to directly access the standalone Oracle Linux patches). The
latest Oracle Linux patches are included the latest WebLogic Server images.

The [Oracle Container Registry](https://container-registry.oracle.com/) (OCR)
supplies two types of WebLogic Server or Fusion Middleware Infrastructure images:

- Critical Patch Updates (CPU) images.
  - Located in OCR repositories "middleware/weblogic_cpu" and "middleware/fmw-infrastructure_cpu".
  - Updated quarterly (every CPU cycle).
  - Includes critical security fixes for Oracle Linux, Java, and Oracle WebLogic Server.
  - Suitable for production use.

- General Availability (GA) images.
  - Located in OCR repositories "middleware/weblogic" and "middleware/fmw-infrastructure".
  - Updated quarterly.
  - Includes latest updates for Oracle Linux, and Java, but _not_ for Oracle WebLogic Server.
  - GA images are subject to [Oracle Technology Network (OTN) Developer License Terms](https://www.oracle.com/downloads/licenses/standard-license.html), 
    which include, but are not limited to:
     - Must only be used for the purpose of developing, testing, prototyping, and demonstrating applications.
     - Must _not_ be used for any data processing, business, commercial, or production purposes.

Example GA images:

| Sample GA image name | Description |
|-|-|
| container-registry.oracle.com/middleware/weblogic:12.2.1.4-generic-jdk8-ol7-NNNNNNTBD | JDK 8u311, Oracle Linux 7u9, and GA Oracle WebLogic Server 12.2.1.4 generic distribution for the given date |
| 12.2.1.4-generic-jdk8-ol7 | Represents latest JDK 8, latest Oracle Linux 7, and GA Oracle WebLogic Server 12.2.1.4 generic distribution |

Example CPU images:

| Sample CPU image name | Description |
|-|-|
| container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol7-211124 | JDK 8u311, Oracle Linux 7u9, and Oracle WebLogic Server 12.2.1.4 generic distribution October 2021 CPU |
| container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol7 | Represents latest JDK 8, latest Oracle Linux 7, and GA Oracle WebLogic Server 12.2.1.4 generic distribution CPU |

You may have noticed that the image tags may include keywords like `generic`, `slim`, etc.
This reflects the type of WebLogic install in the image. There are multiple types,
and the type usually can be determined by examining the image name and tag:

- `.../weblogic...:...generic...`:
  - The WebLogic generic image is supported for development and production deployment
    of WebLogic configurations using Docker.
  - Contains the same binaries as those installed by the WebLogic generic installer.
- `.../weblogic...:...slim...`:
  - The WebLogic slim image is supported for development and production deployment
    of WebLogic configurations using Docker.
  - In order to reduce image size,
    it contains a subset of the binaries included in the WebLogic generic image:
    - The WebLogic Console, WebLogic examples, WebLogic clients, Maven plug-ins,
      and Java DB have been removed.
    - All binaries that remain included are
      the same as those in the WebLogic generic image.
  - If there are requirements to monitor the WebLogic configuration,
    they should be addressed using Prometheus and Grafana, or other alternatives.
- `.../weblogic...:...dev...`:
  - The WebLogic developer image is supported for development 
    of WebLogic applications in Docker containers.
  - In order to reduce image size, it contains a subset
    of the binaries included in the WebLogic generic image:
    - WebLogic examples and Console help files have been removed.
    - All binaries that remain included are the same as those in the WebLogic generic image.
  - This image type is primarily intended to provide a Docker image
    that is consistent with the WebLogic "quick installers" intended for development only.
    Production WebLogic domains should use the WebLogic generic or WebLogic slim images.
- `.../fmw-infrastructure...:...`:
  - The Fusion Middleware (FMW) Infrastructure image is supported for
    development and production deployment of FMW configurations using Docker.
  - It contains the same binaries as those installed by the WebLogic generic installer
    and adds Fusion Middleware Control and Java Required Files (JRF)

Notes about "undated" OCR images with name tags that do _not_ include an embedded date stamp:

- They represent a GA version. 
  _Therefore they may be used in samples and development, but are
  not recommended for production use._
- Unlike images with an embedded datastamp,
  which represent a specific version,
  undated images are periodically updated to 
  the latest available versions of their GA equivalents.
  _Therefore they change over time in the repository
  even though their name and tag remain the same._
- Examples of undated images include
  `registry.oracle.com/middleware/weblogic:TAG` images
  where `TAG` is one of `12.2.1.3`, `12.2.1.4`, `14.1.1.0-11`, or `14.1.1.0-8`.
  These are created with the generic installer, 
  Oracle Linux 7-slim, and JDK8 
  except for `14.1.1.0-11` (which has JDK11).
- The tag for an undated image may not 
  specify its WebLogic installer type, 
  in which case you can assume it is `generic`.
  Otherwise, such images may have
  a tag that includes the string `slim` or `dev`
  string, in which case they have the 
  related installer type and are _not_ `generic` images.

TBD 
- Monica:
  - A Monica will update 14.1.1.0 images, and continue to update them
  - B Provide exact names for image table above
  - F Monica plans to create a a table 
      in the landing page for OCR with all variations.
      When ready, we can add a link.
- Tom:
  - C Update GA image table above to have exact correct image names
      when A & B are available.
  - D Link to this new information from the domain images doc in key places.
  - Link to F when avaialble.

#### Obtain WebLogic Server images from the Oracle Container Registry

{{% notice note %}}
The operator requires WebLogic Server 12.2.1.3.0 plus patch 29135930, or WebLogic Server version 12.2.1.4.0 or later.
The Oracle Container Registry WebLogic 12.2.1.3.0 images include patch 29135930 pre-applied.
Images for WebLogic Server 12.2.1.4.0 do not require any patches.
{{% /notice %}} 

{{% notice warning %}}
The `container-registry.oracle.com/middleware/weblogic:12.2.1.4` image that is mentioned in this section
is a "GA" type image that is only licensable for development or demonstration purposes.
For example, it does not have the latest WebLogic Server install security patches.
See [Understanding Oracle Container Registry WebLogic Server images](#understanding-oracle-container-registry-weblogic-server-images).
{{% /notice %}} 

The Oracle Container Registry contains images for licensed commercial Oracle software products that you may use in your enterprise.
To access the Oracle Registry Server, you must have an Oracle Single Sign-On (SSO) account.
The Oracle Container Registry provides a web interface that allows an administrator to authenticate
and then to select the images for the software that your organization wishes to use.
Oracle Standard Terms and Restrictions terms must be agreed to using the web interface.
After the Oracle Standard Terms and Restrictions have been accepted,
you can pull images of the software from the Oracle Container Registry using the standard `docker pull` command.

To pull an image from the Oracle Container Registry, in a web browser, navigate to https://container-registry.oracle.com and log in
using the Oracle Single Sign-On authentication service. If you do not already have SSO credentials, at the top of the page, click the Sign In link to create them.  

Use the web interface to accept the Oracle Standard Terms and Restrictions for the Oracle software images that you intend to deploy.
Your acceptance of these terms is stored in a database that links the software images to your Oracle Single Sign-On login credentials.

The Oracle Container Registry provides a WebLogic Server 12.2.1.3.0 image, which already has the necessary patches applied, and the Oracle WebLogic Server 12.2.1.4.0 and 14.1.1.0.0 images, which do not require any patches.

First, you will need to log in to the Oracle Container Registry:

```shell
$ docker login container-registry.oracle.com
```

Then, you can pull the image with this command:

```shell
$ docker pull container-registry.oracle.com/middleware/weblogic:12.2.1.4
```
If desired, you can:

* Check the WLS version with `docker run container-registry.oracle.com/middleware/weblogic:12.2.1.4 sh -c` `'source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh > /dev/null 2>&1 && java weblogic.version'`

* Check the WLS patches with `docker run container-registry.oracle.com/middleware/weblogic:12.2.1.4 sh -c` `'$ORACLE_HOME/OPatch/opatch lspatches'`

Additional information about using this image is available in the Oracle Container Registry.

#### Set up Kubernetes to access a container registry

If Kubernetes needs to directly obtain a WebLogic Server image from a
container image registry or repository that requires authentication,
such as the Oracle Container Registry (OCR), then:


- A Kubernetes "docker-registry" secret containing the registry credentials must be created.
  For example, to create a secret with OCR credentials, issue the following command:
  ```shell
  $ kubectl create secret docker-registry SECRET_NAME \
    -n NAMESPACE_WHERE_YOU_DEPLOY_DOMAINS \
    --docker-server=container-registry.oracle.com \
    --docker-username=YOUR_USERNAME \
    --docker-password=YOUR_PASSWORD \
    --docker-email=YOUR_EMAIL
  ```
  Note that the secret must be created in the same namespace as domain resources with a `domain.spec.image` attribute that reference the image.

- The name of the secret must be added to these domain resources using the `domain.spec.imagePullSecrets` field. For example:
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

#### Create a custom image with patches applied

The WebLogic Kubernetes Operator and WebLogic Server 12.2.1.3.0 image requires patch 29135930.
This patch has some prerequisite patches that will also need to be applied.
The WebLogic Server 12.2.1.4.0 image does not require any patches. To create and customize the WebLogic Server image,
and apply the required patches, use the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/).

To use the Image Tool, follow the [Setup](https://oracle.github.io/weblogic-image-tool/quickstart/setup/) instructions
and [Quick Start](https://oracle.github.io/weblogic-image-tool/quickstart/quickstart/) Guide.

To build the WebLogic Server image and apply the patches:

1. Add the Server JRE and the WebLogic Server installer to the [`cache` command](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/).

    ```shell
    $ imagetool cache addInstaller \
    --type=jdk \
    --version=8u241 \
    --path=/home/acmeuser/wls-installers/jre-8u241-linux-x64.tar.gz
    ```
    ```shell
    $ imagetool cache addInstaller \
    --type=wls \
    --version=12.2.1.4.0 \
    --path=/home/acmeuser/wls-installers/fmw_12.2.1.4.0_wls_Disk1_1of1.zip
    ```
2. Use the [Create Tool](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-image/)
to build the image and apply the patches.

    For the Create Tool to download the patches,
    you must supply your My Oracle Support credentials.
    For example:
    ```shell
    $ imagetool create \
    --tag=weblogic:12.2.1.3 \
    --type=wls \
    --version=12.2.1.3.0 \
    --jdkVersion=8u241 \
    --patches=29135930_12.2.1.3.0,27117282_12.2.1.3.0 \
    --user=myusername@mycompany.com \
    --passwordEnv=MYPWD  
    ```

    This example assumes that you are supplying your My Oracle Support password by putting
    it in a shell environment variable named `MYPWD`.

    TBD: Should we use the term "My Oracle Support" credentials here?
    Elsewhere, we describe these credentials as "Oracle Single Sign-On".

3. After the tool creates the image, verify that the image is in your local repository:

    ```shell
    $ docker images
    ```

#### Create a custom image with your domain inside the image

You can also create an image with the WebLogic domain inside the image.
[Samples]({{< relref "/samples/domains/domain-home-in-image/_index.md" >}})
are provided that demonstrate how to create the image using either:

* WLST to define the domain, or
* [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) to define the domain.

In these samples, you will see a reference to a "base" or `FROM` image.
You should use an image with the mandatory patches installed as this base image.
This image could be an OCR image or a custom image
(see [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)).

{{% notice warning %}}
Oracle strongly recommends storing a domain image as private in the registry.
A container image that contains a WebLogic domain home has sensitive information
including keys and credentials that are used to access external resources
(for example, the data source password). For more information, see
[WebLogic domain in container image protection]({{<relref "/security/domain-security/image-protection#weblogic-domain-in-container-image-protection">}}).
{{% /notice %}}

#### Patch WebLogic Server images

Use the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) (WIT) to patch
WebLogic Server images with quarterly Patch Set Updates (PSUs), which include security fixes, or with one-off patches.

Use either the WIT [`create`](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-image/) or
[`update`](https://oracle.github.io/weblogic-image-tool/userguide/tools/update-image/) command, however,
patching using the `create` command results in a smaller WebLogic Server image size. Note that you will need to
download the WebLogic Server 12.2.1.4.0 installer and JDK installer prior to running the `create` command. For details, see
the WIT [Quick Start](https://oracle.github.io/weblogic-image-tool/quickstart/quickstart/) guide.

Example:
Create an image named `sample:wls` with the WebLogic Server 12.2.1.4.0 slim installer,
JDK 8u291, a slim version of the Oracle Linux 7 container image,
and latest PSU and recommended CPU and SPU patches applied.

```shell
$ imagetool create \
  --tag sample:wls \
  --type=wlsslim \
  --recommendedPatches \
  --pull \
  --user testuser@xyz.com \
  --password hello \
  --version=12.2.1.4.0 \
  --jdkVersion=8u291
```
    --passwordEnv=MYPWD  
    ```

Note:
for the Create Tool to download the patches,
you must supply your My Oracle Support credentials;
this example assumes that you are supplying your My Oracle Support password by putting
it in a shell environment variable named `MYPWD`.

#### Apply patched images to a running domain

When updating the WebLogic binaries of a running domain in Kubernetes with a patched container image,
the operator applies the update in a zero downtime fashion.
The procedure for the operator to update the running domain differs depending on the
[domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).
One difference between the domain home source types is the location of the domain home:

* _Domain in PV_:
  The container image only contains the JDK and WebLogic Server binaries.
  The domain home is located in a Persistent Volume (PV).
* _Model in Image with auxiliary images_:
  The container image only contains the JDK and WebLogic Server binaries.
  The [WebLogic Deployment Tooling](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) installation,
  WDT model files, and application archive files,
  are located in auxiliary images TBD link, or elsewhere.
* _Model in Image without auxiliary images_:
  The container image contains the JDK, WebLogic Server binaries,
  a [WebLogic Deployment Tooling](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) installation,
  and potentially also WDT model files application archive files.
* Domain in Image:
  The container image contains the JDK, WebLogic Server binaries, and domain home.   
  The domain home is generated during image creation using either WLST or WDT.

For _Domain in PV_ and _Model in Image with auxiliary images_,
the operator can apply the update to the running domain without modifying the patched container image.

For _Model in Image (MII) without auxiliary images_ and _Domain in Image_,
before the operator can apply the update,
the patched container images must be modified to add the domain home or a WDT model and archive.
You use WebLogic Image Tool (WIT) [Rebase Image](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/)
to update the Oracle Home of the original image using the patched Oracle Home from a patched container image.

In all cases,
you edit the [Domain Resource]({{< relref "/userguide/managing-domains/domain-resource#domain-resource-attribute-references" >}})
to inform the operator of the name of the new patched image so that it can manage the update of the WebLogic domain.

For a broader description of managing the evolution and mutation of container images to run WebLogic Server in Kubernetes,
see [CI/CD]({{< relref "/userguide/cicd/_index.md" >}}).

##### Domain in PV

Edit the Domain Resource image reference with the new image name/tag (for example, `oracle/weblogic:12.2.1.4-patched`).
Then, the operator performs a [rolling restart]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.
For information on server restarts, see [Restarting]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting.md" >}}).

##### Model in Image with auxiliary images

Edit the Domain Resource image reference with the new image name/tag (for example, `oracle/weblogic:12.2.1.4-patched`).
Then, the operator performs a [rolling restart]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.
For information on server restarts, see [Restarting]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting.md" >}}).

##### Model in Image without auxiliary images

Use the WIT [`rebase`](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/) command
to update the Oracle Home for an existing image with the model and archive files in the image using the patched Oracle Home from a
patched container image. Then, the operator performs a [rolling update]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}})
of the domain, updating the Oracle Home of each server pod.

Example: WIT copies a WDT model and WDT archive from the source image, `mydomain:v1`, 
to a new image, `mydomain:v2`, based on a target image named `oracle/weblogic:generic-12.2.1.4.0-patched`. 

**Note**: Oracle Home and the JDK must be installed in the same directories on each image.

```shell
$ imagetool rebase --tag mydomain:v2 --sourceImage mydomain:v1 --targetImage oracle/weblogic:generic-12.2.1.4.0-patched  --wdtModelOnly
```

Then, edit the Domain Resource image reference with the new image name/tag (`mydomain:2`).

##### Domain in Image

Use the WIT [`rebase`](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/) command to update the Oracle Home
for an existing domain image using the patched Oracle Home from a patched container image. Then, the operator performs a
[rolling update]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}}) of the domain,
updating the Oracle Home of each server pod.

Example: WIT copies the domain from the source image, `mydomain:v1`, to a new image, `mydomain:v2`, based on a target
image named `oracle/weblogic:12.2.1.4-patched`.

**Note**: Oracle Home and the JDK must be installed in the same directories on each image.

```shell
$ imagetool rebase --tag mydomain:v2 --sourceImage mydomain:v1 --targetImage oracle/weblogic:12.2.1.4-patched
```

Then, edit the Domain Resource image reference with the new image name/tag (`mydomain:2`).

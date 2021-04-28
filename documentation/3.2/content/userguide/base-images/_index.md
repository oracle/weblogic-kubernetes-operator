---
title: "WebLogic Server images"
date: 2019-02-23T16:45:55-05:00
weight: 4
description: "Create or obtain WebLogic Server images."
---

#### Contents

* [Create or obtain WebLogic Server images](#create-or-obtain-weblogic-server-images)
* [Set up secrets to access the Oracle Container Registry](#set-up-secrets-to-access-the-oracle-container-registry)
* [Obtain standard images from the Oracle Container Registry](#obtain-standard-images-from-the-oracle-container-registry)
* [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)
* [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image)
* [Patch WebLogic Server images](#patch-weblogic-server-images)
* [Apply patched images to a running domain](#apply-patched-images-to-a-running-domain)


#### Create or obtain WebLogic Server images

You will need container images to run your WebLogic domains in Kubernetes.
There are two main options available:

* Use an image which contains the WebLogic Server binaries but
  not the domain, or
* Use an image which contains both the WebLogic Server binaries
  and the domain directory.

If you want to use the first option, then you will need to obtain the standard
WebLogic Server image from the Oracle Container Registry; see [Obtain standard images from the Oracle Container Registry](#obtain-standard-images-from-the-oracle-container-registry).
This image already contains the mandatory patches applied, as described in [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied).
If you want to use additional patches, you can customize that process to include additional patches.

If you want to use the second option, which includes the domain directory
inside the image, then you will need to build your own images,
as described in [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image).

#### Set up secrets to access the Oracle Container Registry

{{% notice note %}}
This version of the operator requires WebLogic Server 12.2.1.3.0 plus patch 29135930; the standard image, `container-registry.oracle.com/middleware/weblogic:12.2.1.3`, already includes this patch pre-applied. Images for WebLogic Server 12.2.1.4.0 do not require any patches.
{{% /notice %}}  

In order for Kubernetes to obtain the WebLogic Server image from the Oracle Container Registry (OCR), which requires authentication, a Kubernetes Secret containing the registry credentials must be created. To create a secret with the OCR credentials, issue the following command:

```shell
$ kubectl create secret docker-registry SECRET_NAME \
  -n NAMESPACE \
  --docker-server=container-registry.oracle.com \
  --docker-username=YOUR_USERNAME \
  --docker-password=YOUR_PASSWORD \
  --docker-email=YOUR_EMAIL
```

In this command, replace the uppercase items with the appropriate values. The `SECRET_NAME` will be needed in later parameter files.  The `NAMESPACE` must match the namespace where the first domain will be deployed, otherwise, Kubernetes will not be able to find it.  

It may be preferable to manually pull the image in advance, on each Kubernetes worker node, as described in the next section.
If you choose this approach, you do not require the Kubernetes Secret.

#### Obtain standard images from the Oracle Container Registry

The Oracle Container Registry contains images for licensed commercial Oracle software products that you may use in your enterprise. To access the Oracle Registry Server, you must have an Oracle Single Sign-On (SSO) account. The Oracle Container Registry provides a web interface that allows an administrator to authenticate and then to select the images for the software that your organization wishes to use. Oracle Standard Terms and Restrictions terms must be agreed to using the web interface. After the Oracle Standard Terms and Restrictions have been accepted, you can pull images of the software from the Oracle Container Registry using the standard `docker pull` command.

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

#### Create a custom image with patches applied

The Oracle WebLogic Server Kubernetes Operator and WebLogic Server 12.2.1.3.0 image requires patch 29135930.
This patch has some prerequisite patches that will also need to be applied. The WebLogic Server 12.2.1.4.0 image does not require any patches. To create and customize the WebLogic Server image,
and apply the required patches, use the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool).

To use the Image Tool, follow the [Setup](https://github.com/oracle/weblogic-image-tool/blob/master/README.md#setup) instructions
and [Quick Start](https://github.com/oracle/weblogic-image-tool/blob/master/site/quickstart.md) Guide.

To build the WebLogic Server image and apply the patches:

1. Add the Server JRE and the WebLogic Server installer to the [`cache` command](https://github.com/oracle/weblogic-image-tool/blob/master/site/cache.md).

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
2. Use the [Create Tool](https://github.com/oracle/weblogic-image-tool/blob/master/site/create-image.md)
to build the image and apply the patches.

    For the Create Tool to download the patches, you must supply your My Oracle Support credentials.
    ```shell
    $ imagetool create \
    --tag=weblogic:12.2.1.3 \
    --type=wls \
    --version=12.2.1.3.0 \
    --jdkVersion=8u241 \
    -–patches=29135930_12.2.1.3.0,27117282_12.2.1.3.0 \
    --user=username.mycompany.com \
    --passwordEnv=MYPWD  
    ```

3. After the tool creates the image, verify that the image is in your local repository:

    ```shell
    $ docker images
    ```
#### Create a custom image with your domain inside the image

You can also create an image with the WebLogic domain inside the image.
[Samples]({{< relref "/samples/simple/domains/domain-home-in-image/_index.md" >}})
are provided that demonstrate how to create the image using either:

* WLST to define the domain, or
* [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling) to define the domain.

In these samples, you will see a reference to a "base" or `FROM` image.  You should use an image
with the mandatory patches installed as this base image.  This image could be either
the standard `container-registry.oracle.com/middleware/weblogic:12.2.1.3` pre-patched image or an image you created using the instructions above.
WebLogic Server 12.2.1.4.0 images do not require patches.

{{% notice warning %}}
Oracle strongly recommends storing a domain image as private in the registry.
A container image that contains a WebLogic domain home has sensitive information
including keys and credentials that are used to access external resources
(for example, the data source password). For more information, see
[WebLogic domain in container image protection]({{<relref "/security/domain-security/image-protection#weblogic-domain-in-container-image-protection">}}).
{{% /notice %}}

#### Patch WebLogic Server images

Use the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) (WIT) to patch
WebLogic Server images with quarterly Patch Set Updates (PSUs), which include security fixes, or with one-off patches.

Use either the WIT [`create`](https://github.com/oracle/weblogic-image-tool/blob/master/site/create-image.md) or
[`update`](https://github.com/oracle/weblogic-image-tool/blob/master/site/update-image.md) command, however,
patching using the `create` command results in a smaller WebLogic Server image size. Note that you will need to
download the WebLogic Server 12.2.1.4.0 installer and JDK installer prior to running the `create` command. For details, see
the WIT [Quick Start](https://github.com/oracle/weblogic-image-tool/blob/master/site/quickstart.md) guide.

Example: Create an image named `sample:wls` with the WebLogic Server 12.2.1.4.0 slim installer, JDK 8u291, a slim version of the Oracle Linux 7 container image,
and latest PSU and recommended CPU and SPU patches applied.

```shell
$ imagetool create --tag sample:wls --type=wlsslim --recommendedPatches --pull --user testuser@xyz.com --password hello --version=12.2.1.4.0 --jdkVersion=8u291
```

#### Apply patched images to a running domain

When updating the WebLogic binaries of a running domain in Kubernetes with a patched container image,
the operator applies the update in a zero downtime fashion. The procedure for the operator
to update the running domain differs depending on the [domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}). One
difference between the domain home source types is the location of the domain home:

* Domain in PV: The container image contains the JDK and WebLogic Server binaries. The domain home is located in a Persistent Volume (PV).
* Model in Image: The container image contains the JDK, WebLogic Server binaries, and a [WebLogic Deployment Tooling](https://github.com/oracle/weblogic-deploy-tooling) (WDT) installation, WDT model file, and application archive file.
* Domain in Image: The container image contains the JDK, WebLogic Server binaries, and domain home.   

For Domain in PV, the operator can apply the update to the running domain without modifying the patched container image. For Model in Image (MII) and Domain in Image,
before the operator can apply the update, the patched container images must be modified to add the domain home or a
WDT model and archive. You use WebLogic Image Tool (WIT) [Rebase Image](https://github.com/oracle/weblogic-image-tool/blob/master/site/rebase-image.md)
to update the Oracle Home of the original image using the patched Oracle Home from a patched container image.

In all three domain home source types, you edit the [Domain Resource]({{< relref "/userguide/managing-domains/domain-resource#domain-resource-attribute-references" >}})
to inform the operator of the name of the new patched image so that it can manage the update of the WebLogic domain.

For a broader description of managing the evolution and mutation of container images to run WebLogic Server in Kubernetes, see [CI/CD]({{< relref "/userguide/cicd/_index.md" >}}).

##### Domain in PV

Edit the Domain Resource image reference with the new image name/tag (for example, `oracle/weblogic:12.2.1.4-patched`).
Then, the operator performs a [rolling restart]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.
For information on server restarts, see [Restarting]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting.md" >}}).

##### Model in Image
Use the WIT [`rebase`](https://github.com/oracle/weblogic-image-tool/blob/master/site/rebase-image.md) command
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
Use the WIT [`rebase`](https://github.com/oracle/weblogic-image-tool/blob/master/site/rebase-image.md) command to update the Oracle Home
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

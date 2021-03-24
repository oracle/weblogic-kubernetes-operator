---
title: "Base images"
date: 2019-02-23T16:45:55-05:00
weight: 1
description: "Creating or obtaining WebLogic Server images."
---

#### Contents

* [Creating or obtaining WebLogic Server images](#creating-or-obtaining-weblogic-server-images)
* [Setting up secrets to access the Oracle Container Registry](#setting-up-secrets-to-access-the-oracle-container-registry)
* [Obtaining standard images from the Oracle Container Registry](#obtaining-standard-images-from-the-oracle-container-registry)
* [Creating a custom image with patches applied](#creating-a-custom-image-with-patches-applied)
* [Creating a custom image with your domain inside the image](#creating-a-custom-image-with-your-domain-inside-the-image)

#### Creating or obtaining WebLogic Server images

You will need container images to run your WebLogic domains in Kubernetes.
There are two main options available:

* Use an image which contains the WebLogic Server binaries but
  not the domain, or
* Use an image which contains both the WebLogic Server binaries
  and the domain directory.

If you want to use the first option, then you will need to obtain the standard
WebLogic Server image from the Oracle Container Registry; see [Obtaining standard images from the Oracle Container Registry](#obtaining-standard-images-from-the-oracle-container-registry).
This image already contains the mandatory patches applied, as described in [Creating a custom image with patches applied](#creating-a-custom-image-with-patches-applied).
If you want to use additional patches, you can customize that process to include additional patches.

If you want to use the second option, which includes the domain directory
inside the image, then you will need to build your own images,
as described in [Creating a custom image with your domain inside the image](#creating-a-custom-image-with-your-domain-inside-the-image).

#### Setting up secrets to access the Oracle Container Registry

{{% notice note %}}
This version of the operator requires WebLogic Server 12.2.1.3.0 plus patch 29135930; the standard image, `container-registry.oracle.com/middleware/weblogic:12.2.1.3`, already includes this patch pre-applied. Images for WebLogic Server 12.2.1.4.0 do not require any patches.
{{% /notice %}}  

In order for Kubernetes to obtain the WebLogic Server image from the Oracle Container Registry (OCR), which requires authentication, a Kubernetes Secret containing the registry credentials must be created. To create a secret with the OCR credentials, issue the following command:

```
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

#### Obtaining standard images from the Oracle Container Registry

The Oracle Container Registry contains images for licensed commercial Oracle software products that you may use in your enterprise. To access the Oracle Registry Server, you must have an Oracle Single Sign-On (SSO) account. The Oracle Container Registry provides a web interface that allows an administrator to authenticate and then to select the images for the software that your organization wishes to use. Oracle Standard Terms and Restrictions terms must be agreed to using the web interface. After the Oracle Standard Terms and Restrictions have been accepted, you can pull images of the software from the Oracle Container Registry using the standard `docker pull` command.

To pull an image from the Oracle Container Registry, in a web browser, navigate to https://container-registry.oracle.com and log in
using the Oracle Single Sign-On authentication service. If you do not already have SSO credentials, at the top of the page, click the Sign In link to create them.  

Use the web interface to accept the Oracle Standard Terms and Restrictions for the Oracle software images that you intend to deploy.
Your acceptance of these terms is stored in a database that links the software images to your Oracle Single Sign-On login credentials.

The Oracle Container Registry provides a WebLogic Server 12.2.1.3.0 image, which already has the necessary patches applied, and the Oracle WebLogic Server 12.2.1.4.0 and 14.1.1.0.0 images, which do not require any patches.

First, you will need to log in to the Oracle Container Registry:

```
$ docker login container-registry.oracle.com
```

Then, you can pull the image with this command:

```
$ docker pull container-registry.oracle.com/middleware/weblogic:12.2.1.4
```
If desired, you can:

* Check the WLS version with `docker run container-registry.oracle.com/middleware/weblogic:12.2.1.4 sh -c` `'source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh > /dev/null 2>&1 && java weblogic.version'`

* Check the WLS patches with `docker run container-registry.oracle.com/middleware/weblogic:12.2.1.4 sh -c` `'$ORACLE_HOME/OPatch/opatch lspatches'`

Additional information about using this image is available on the
Oracle Container Registry.

#### Creating a custom image with patches applied

The Oracle WebLogic Server Kubernetes Operator and WebLogic Server 12.2.1.3.0 image requires patch 29135930.
This patch has some prerequisite patches that will also need to be applied. The WebLogic Server 12.2.1.4.0 image does not require any patches. To create and customize the WebLogic Server image,
and apply the required patches, use the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool).

To use the Image Tool, follow the [Setup](https://github.com/oracle/weblogic-image-tool/blob/master/README.md#setup) instructions
and [Quick Start](https://github.com/oracle/weblogic-image-tool/blob/master/site/quickstart.md) Guide.

To build the WebLogic Server image and apply the patches:

1. Add the Server JRE and the WebLogic Server installer to the [`cache` command](https://github.com/oracle/weblogic-image-tool/blob/master/site/cache.md).

    ```
    $ imagetool cache addInstaller \
    --type=jdk \
    --version=8u241 \
    --path=/home/acmeuser/wls-installers/jre-8u241-linux-x64.tar.gz

    $ imagetool cache addInstaller \
    --type=wls \
    --version=12.2.1.4.0 \
    --path=/home/acmeuser/wls-installers/fmw_12.2.1.4.0_wls_Disk1_1of1.zip
    ```
2. Use the [Create Tool](https://github.com/oracle/weblogic-image-tool/blob/master/site/create-image.md)
to build the image and apply the patches.

    For the Create Tool to download the patches, you must supply your My Oracle Support credentials.
    ```

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

    ```

    $ docker images
    ```


#### Creating a custom image with your domain inside the image

You can also create an image with the WebLogic domain inside the image.
[Samples]({{< relref "/samples/simple/domains/domain-home-in-image/_index.md" >}})
are provided that demonstrate how to create the image using either:

* WLST to define the domain, or
* [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling)
  to define the domain.

In these samples, you will see a reference to a "base" or `FROM` image.  You should use an image
with the mandatory patches installed as this base image.  This image could be either
the standard `container-registry.oracle.com/middleware/weblogic:12.2.1.3` pre-patched image or an image you created using the instructions above.
WebLogic Server 12.2.1.4.0 images do not require patches.

{{% notice note %}}
Oracle recommends that images containing WebLogic domains
be kept in a private repository.
{{% /notice %}}

---
title: "Base images"
date: 2019-02-23T16:45:55-05:00
weight: 1
description: "Creating or obtaining WebLogic Docker images."
---

#### Contents

* [Creating or obtaining WebLogic Docker images](#creating-or-obtaining-weblogic-docker-images)
* [Setting up secrets to access the Oracle Container Registry](#setting-up-secrets-to-access-the-oracle-container-registry)
* [Obtaining standard images from the Oracle Container Registry](#obtaining-standard-images-from-the-oracle-container-registry)
* [Creating a custom image with patches applied](#creating-a-custom-image-with-patches-applied)
* [Creating a custom image with your domain inside the image](#creating-a-custom-image-with-your-domain-inside-the-image)

#### Creating or obtaining WebLogic Docker images

You will need Docker images to run your WebLogic domains in Kubernetes.
There are two main options available:

* Use a Docker image which contains the WebLogic Server binaries but
  not the domain, or
* Use a Docker image which contains both the WebLogic Server binaries
  and the domain directory.

If you want to use the first option, you will need to obtain the standard
WebLogic Server image from the Oracle Container Registry, [see here](#obtaining-standard-images-from-the-oracle-container-registry);
this image already contains the mandatory patches applied as described in [this section](#creating-a-custom-image-with-patches-applied).
If you want to use additional patches, you can customize that process to include additional patches.

If you want to use the second option, which includes the domain directory
inside the Docker image, then you will need to build your own Docker images
as described in [this section](#creating-a-custom-image-with-your-domain-inside-the-image).

#### Setting up secrets to access the Oracle Container Registry

{{% notice note %}}
This version of the operator requires WebLogic Server 12.2.1.3.0 plus patch 29135930; the standard image, `container-registry.oracle.com/middleware/weblogic:12.2.1.3`, already includes this patch pre-applied.
{{% /notice %}}  

In order for Kubernetes to obtain the WebLogic Server Docker image from the Oracle Container Registry (OCR), which requires authentication, a Kubernetes secret containing the registry credentials must be created. To create a secret with the OCR credentials, issue the following command:

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
If you choose this approach, you do not require the Kubernetes secret.

#### Obtaining standard images from the Oracle Container Registry

The Oracle Container Registry contains images for licensed commercial Oracle software products that you may use in your enterprise. To access the Oracle Registry Server, you must have an Oracle Single Sign-On (SSO) account. The Oracle Container Registry provides a web interface that allows an administrator to authenticate and then to select the images for the software that your organization wishes to use. Oracle Standard Terms and Restrictions terms must be agreed to via the web interface. After the Oracle Standard Terms and Restrictions have been accepted, you can pull images of the software from the Oracle Container Registry using the standard Docker pull command.

To pull an image from the Oracle Container Registry, in a web browser, navigate to https://container-registry.oracle.com and log in
using the Oracle Single Sign-On authentication service. If you do not already have SSO credentials, at the top of the page, click the Sign In link to create them.  

Use the web interface to accept the Oracle Standard Terms and Restrictions for the Oracle software images that you intend to deploy.
Your acceptance of these terms is stored in a database that links the software images to your Oracle Single Sign-On login credentials.

The Oracle Container Registry provides a WebLogic Server 12.2.1.3.0 Docker image which already has the necessary patches applied.

First, you will need to log in to the Oracle Container Registry:

```
$ docker login container-registry.oracle.com
```

Then, you can pull the image with this command:

```
$ docker pull container-registry.oracle.com/middleware/weblogic:12.2.1.3
```
If desired, you can:

* Check the WLS version with `docker run container-registry.oracle.com/middleware/weblogic:12.2.1.3 sh -c` `'source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh > /dev/null 2>&1 && java weblogic.version'`

* Check the WLS patches with `docker run container-registry.oracle.com/middleware/weblogic:12.2.1.3 sh -c` `'$ORACLE_HOME/OPatch/opatch lspatches'`

Additional information about using this image is available on the
Oracle Container Registry.

#### Creating a custom image with patches applied

The Oracle WebLogic Server Kubernetes Operator requires patch 29135930.
This patch does have some prerequisite patches that will also need to be applied. The standard image `container-registry.oracle.com/middleware/weblogic:12.2.1.3` already has all of these patches applied.

[This sample](https://github.com/oracle/docker-images/blob/master/OracleWebLogic/samples/12213-patch-wls-for-k8s/README.md) in
the Oracle GitHub Docker images repository demonstrates how to create an image with arbitrary patches, starting from an unpatched WebLogic Server 12.2.1.3 image (not the standard `container-registry/middleware/weblogic:12.2.1.3` pre-patched image).  You can customize that sample to apply a different set of patches, if you require additional patches or PSUs.

When using that sample, you will need to download the required patch and also
some prerequisite patches.  To find the correct version of the patch, you should
use the "Product or Family (Advanced)" option, then choose "Oracle WebLogic Server"
as the product, and set the release to "Oracle WebLogic Server 12.2.1.3.181016" as
shown in the image below:

![patch download page](/weblogic-kubernetes-operator/images/patch-download.png)


The `Dockerfile` in that sample lists the base image as follows:

```
FROM oracle/weblogic:12.2.1.3-developer
```

You can change this to use the standard WebLogic Server image you
downloaded from the OCR by updating the `FROM` statement
as follows:

```
FROM container-registry.oracle.com/middleware/weblogic:12.2.1.3
```

After running `docker build` as described in the sample, you
will have created a Docker image with the necessary patches to
run WebLogic 12.2.1.3 in Kubernetes using the operator.

#### Creating a custom image with your domain inside the image

You can also create a Docker image with the WebLogic domain inside the image.
[Samples]({{< relref "/samples/simple/domains/domain-home-in-image/_index.md" >}})
are provided that demonstrate how to create the image using:

* WLST to define the domain, or
* [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling)
  to define the domain.

In these samples, you will see a reference to a "base" or `FROM` image.  You should use an image
with the mandatory patches installed as this base image.  This image could be either
the standard `container-registry.oracle.com/middleware/weblogic:12.2.1.3` pre-patched image or an image you created using the instructions above.

{{% notice note %}}
Oracle recommends that Docker images containing WebLogic domains
be kept in a private repository.
{{% /notice %}}

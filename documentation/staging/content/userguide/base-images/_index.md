---
title: "Domain images"
date: 2019-02-23T16:45:55-05:00
weight: 6
description: "Create, obtain, or inspect images for WebLogic Server or Fusion Middleware Infrastructure deployments."
---

### Contents

- [Understanding WebLogic images](#understanding-weblogic-images)
  - [Overview](#overview)
  - [Understand Oracle Container Registry images](#understand-oracle-container-registry-images)
    - [Compare General Availability to Critical Patch Updates images](#compare-general-availability-to-critical-patch-updates-images)
    - [WebLogic distribution installer type](#weblogic-distribution-installer-type)
    - [Compare "dated" and "undated" images](#compare-dated-and-undated-images)
    - [Example OCR image names](#example-ocr-image-names)
    - [Obtain images from the Oracle Container Registry](#obtain-images-from-the-oracle-container-registry)
  - [Inspect images](#inspect-images)
  - [Set up Kubernetes to access domain images](#set-up-kubernetes-to-access-domain-images)
    - [Option 1: Store images in a central registry and set up image pull secrets on each domain resource](#option-1-store-images-in-a-central-registry-and-set-up-image-pull-secrets-on-each-domain-resource)
    - [Option 2: Store images in a central registry and set up a Kubernetes service account with image pull secrets in each domain namespace](#option-2-store-images-in-a-central-registry-and-set-up-a-kubernetes-service-account-with-image-pull-secrets-in-each-domain-namespace)
    - [Option 3: Manually place images on Kubernetes cluster nodes](#option-3-manually-place-images-on-kubernetes-cluster-nodes)
  - [Ensure you are using recently patched images](#ensure-you-are-using-recently-patched-images)

- [Using the WebLogic image tool (WIT)](#using-the-weblogic-image-tool-wit)
  - [Install the WebLogic Image Tool](#install-the-weblogic-image-tool)
  - [WIT options overview](#wit-options-overview)
    - [WIT `create` command](#wit-create-command)
    - [WIT `update` command](#wit-update-command)
    - [WIT `rebase` command](#wit-rebase-command)
    - [WIT `createAuxImage` command](#wit-createauximage-command)
  - [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)
    - [Create a custom base image](#create-a-custom-base-image)
    - [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image)
    - [Create a custom image with your model inside the image](#create-a-custom-image-with-your-model-inside-the-image)
  - [Apply patched images to a running domain](#apply-patched-images-to-a-running-domain)
    - [Domain in PV](#domain-in-pv)
    - [Model in Image with auxiliary images](#model-in-image-with-auxiliary-images)
    - [Model in Image without auxiliary images](#model-in-image-without-auxiliary-images)
    - [Domain in Image](#domain-in-image)

### Understanding WebLogic images

#### Overview

You will need WebLogic Server or Fusion Middleware Infrastructure
images to run your WebLogic domains in Kubernetes,
where the image location is specified in the domain resource's `domain.spec.image` attribute.
Oracle recommends obtaining such images
from the Oracle Container Registry (OCR)
or creating custom images using the WebLogic Image Tool.
The recommended approach depends on your
[domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}):

* Model in Image domains that leverage auxiliary images:
  * Directly use an OCR or custom image which only contains the WebLogic Server or Fusion Middleware Infrastructure binaries.
  * Also use separate auxiliary images to supply WebLogic Deploy Tool install, configuration model files, and application archives.
  * See:
    * [Understand Oracle Container Registry images](#understand-oracle-container-registry-images)
    * [Obtain images from the Oracle Container Registry](#obtain-images-from-the-oracle-container-registry)
    * [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)
    * [Auxiliary images]({{< relref "/userguide/managing-domains/model-in-image/auxiliary-images.md" >}})

* Model in Image domains that _do not_ leverage auxiliary images:
  * This approach is for Model in Image type domains where
    the WebLogic Deploy Tooling install and potentially also model files
    are supplied in the same image that includes WebLogic Server or Fusion Middleware Infrastructure binaries.
  * May use an OCR or custom image as a base image which only contains the WebLogic Server or Fusion Middleware Infrastructure binaries.
  * See [Create a custom image with your model inside the image](#create-a-custom-image-with-your-model-inside-the-image).

* Domain in Image domains:
  * Create a custom image which contains a WebLogic domain home directory
    and WebLogic Server or Fusion Middleware Infrastructure binaries.
  * May use an OCR or custom image as a base image which only contains the WebLogic Server or Fusion Middleware Infrastructure binaries.
  * See [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image).

* Domain in PV type domains:
  * Directly use an OCR or custom image which only contains the WebLogic Server or Fusion Middleware Infrastructure binaries.
  * WebLogic configuration and applications are separately supplied
    in a domain home on the PV. It is up to the customer
    to create and update the domain home (the operator does not do this).
  * See:
    * [Understand Oracle Container Registry images](#understand-oracle-container-registry-images)
    * [Obtain images from the Oracle Container Registry](#obtain-images-from-the-oracle-container-registry)
    * [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)

To understand how to update images for a running domain,
see [Apply patched images to a running domain](#apply-patched-images-to-a-running-domain).

#### Understand Oracle Container Registry images

{{% notice note %}}
All of the OCR images that are described in this section are built using
the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) (WIT).
Customers can use WIT to build their own WebLogic Server or Fusion Middleware Infrastructure images
(with the latest Oracle Linux images, Java updates, and WebLogic Server patches),
apply one-off patches to existing OCR images,
or overlay their own files and applications on top of an OCR image.
See [Contents](#contents) for information about using this tool
to create custom WebLogic Server or Fusion Middleware Infrastructure images for the WebLogic Kubernetes Operator.
{{% /notice %}}

The Oracle Container Registry (OCR) is
located at [https://container-registry.oracle.com/](https://container-registry.oracle.com/)
and contains images for licensed commercial Oracle software products
that you may use in your enterprise for deployment using Docker.

OCR supplies _WebLogic Server images_ which have a pre-installed Oracle Home
with Oracle WebLogic Server and Coherence.
OCR also supplies _Fusion Middleware Infrastructure images_
which  have a pre-installed Oracle Home with Oracle WebLogic Server,
Coherence, Fusion Middleware Control, and Java Required Files (JRF).

See the following sections for information about OCR image names and accessing OCR images:

- [Compare General Availability to Critical Patch Updates images](#compare-general-availability-to-critical-patch-updates-images)
- [WebLogic distribution installer type](#weblogic-distribution-installer-type)
- [Compare "dated" and "undated" images](#compare-dated-and-undated-images)
- [Example OCR image names](#example-ocr-image-names)
- [Obtain images from the Oracle Container Registry](#obtain-images-from-the-oracle-container-registry)

##### Compare General Availability to Critical Patch Updates images

{{% notice warning %}}
The latest Oracle Container Registry (OCR) **GA images**
include the latest security patches for Oracle Linux and Java,
and do _not_ include the latest security patches for WebLogic Server.
Oracle strongly recommends using images with the latest security patches,
such as OCR Critical Patch Updates (CPU) images or custom generated images.
See [Ensure you are using recently patched images]({{< relref "/userguide/base-images/_index.md#ensure-you-are-using-recently-patched-images" >}}).
{{% /notice %}}

OCR images WebLogic Server or Fusion Middleware Infrastructure can be 
either General Availability (GA) images or Critical Patch Updates (CPU) images:

- General Availability (GA) images.
  - Located in OCR repositories "middleware/weblogic" and "middleware/fmw-infrastructure".
  - Updated quarterly.
  - Includes latest updates for Oracle Linux, and Java, but _not_ for Oracle WebLogic Server.
  - GA images are free to use and are subject to
    [Oracle Technology Network (OTN) Developer License Terms](https://www.oracle.com/downloads/licenses/standard-license.html),
    which include, but are not limited to:
    - Must only be used for the purpose of developing, testing, prototyping, and demonstrating applications.
    - Must _not_ be used for any data processing, business, commercial, or production purposes.

- Critical Patch Updates (CPU) images.
  - Located in OCR repositories "middleware/weblogic_cpu" and "middleware/fmw-infrastructure_cpu".
  - Updated quarterly (every CPU cycle).
  - Includes critical security fixes for Oracle Linux, Java, and Oracle WebLogic Server and Coherence.
  - Suitable for production use.

##### WebLogic distribution installer type

You may have noticed that OCR image tags may include keywords like `generic`, `slim`, etc.
This reflects the type of WebLogic distribution installed in the image's Oracle Home.
There are multiple types,
and the type usually can be determined by examining the image name and tag:

- `.../weblogic...:...generic...`
  - The _WebLogic generic image_.
  - Contains the same binaries as those installed by the WebLogic generic installer.

- `.../weblogic...:...slim...`:
  - The _WebLogic slim image_.
  - In order to reduce image size,
    contains a subset of the binaries included in the WebLogic generic image:
    - The WebLogic Administration Console, WebLogic examples, WebLogic clients, Maven plug-ins,
      and Java DB have been removed.
    - All binaries that remain included are
      the same as those in the WebLogic generic image.
  - If there are requirements to monitor the WebLogic configuration, then:
    - They should be addressed using Prometheus and Grafana, or other alternatives.
    - Note that you can use the open source
      [WebLogic Remote Console]({{< relref "/userguide/managing-domains/accessing-the-domain/admin-console.md" >}})
      as an alternative for the WebLogic Administration Console.

- `.../weblogic...:...dev...`:
  - The _WebLogic developer image_.
  - In order to reduce image size,
    contains a subset of the binaries included in the WebLogic generic image:
    - WebLogic examples and Console help files have been removed
      (the WebLogic Administration Console is still included).
    - All binaries that remain included are the same as those in the WebLogic generic image.
  - This image type is primarily intended to provide a Docker image
    that is consistent with the WebLogic "quick installers" intended for development only.
    Production WebLogic domains should use the WebLogic generic, WebLogic slim,
    or Fusion Middleware Infrastructure images.

- `.../fmw-infrastructure...:...`:
  - The _Fusion Middleware (FMW) Infrastructure image_.
  - Contains the same binaries as those installed by the WebLogic generic installer
    and adds Fusion Middleware Control and Java Required Files (JRF)

- None of the above

  - If the tag portion of a `.../weblogic...` OCR image name
    does _not_ include a keyword like `slim`, `dev`, or `generic`,
    then you can assume the image contains 
    the same binaries as those installed by the WebLogic generic installer.

##### Compare "dated" and "undated" images

OCR images are "dated" or "undated"
depending on whether the name tags include an embedded date stamp
of the form `YYMMDD`.
Unlike dated images with an embedded date stamp,
which represent a specific version that was released on a specific date,
undated images are periodically updated to
the latest available versions of their GA or CPU equivalents.
_Therefore they change over time in the repository
even though their name and tag remain the same._

##### Example OCR image names

Here are some example WebLogic Server Oracle Container Repository (OCR) images,
where the names are abbreviated to omit their `container-registry.oracle.com/middleware/` prefix:

| Abbreviated Name | Descripton |
|-|-|
|`weblogic:12.2.1.4`|GA image with latest JDK 8, latest Oracle Linux 7, and the GA Oracle WebLogic Server 12.2.1.4 generic distribution. Note that this image has no date stamp, so it can change over time with potential updates to JDK 8 and Oracle Linux 7.|
|`weblogic:12.2.1.4-YYMMDD`|GA image with JDK 8, Oracle Linux 7, and the GA Oracle WebLogic Server 12.2.1.4 generic distribution for the given date.|
|`weblogic_cpu:12.2.1.4-generic-jdk8-ol7`|CPU image with latest JDK 8, latest Oracle Linux 7, and GA Oracle WebLogic Server 12.2.1.4 generic distribution CPU. Note that this image has no date stamp, so it can change over time with potential updates to JDK 8, to Oracle Linux 7, and to the latest CPU.|
|`weblogic_cpu:12.2.1.4-generic-jdk8-ol7-211124`|CPU image with JDK 8u311, Oracle Linux 7u9, and the Oracle WebLogic Server 12.2.1.4 generic distribution October 2021 CPU.|

##### Obtain images from the Oracle Container Registry

{{% notice warning %}}
The latest Oracle Container Registry (OCR) **GA images**
include the latest security patches for Oracle Linux and Java,
and do _not_ include the latest security patches for WebLogic Server.
Oracle strongly recommends using images with the latest security patches,
such as OCR Critical Patch Updates (CPU) images or custom generated images.
See [Ensure you are using recently patched images]({{< relref "/userguide/base-images/_index.md#ensure-you-are-using-recently-patched-images" >}}).
{{% /notice %}}

The Oracle Container Registry (OCR) contains images for licensed commercial Oracle software products
that you may use in your enterprise.
To access the Oracle Registry Server, you must have an Oracle Single Sign-On (SSO) account.
OCR provides a web interface that allows an administrator to authenticate
and then to select the images for the software that your organization wishes to use.
Oracle Standard Terms and Restrictions terms must be agreed to using the web interface.
After the Oracle Standard Terms and Restrictions have been accepted,
you can pull images of the software from OCR using the standard `docker pull` command
while using your SSO for your `docker login` credentials.

For example, to use docker to pull an image from OCR:

1. Accept the Oracle Standard Terms and Restrictions
   for the Oracle software images that you intend to deploy:

   - In a web browser, navigate to
     [https://container-registry.oracle.com](https://container-registry.oracle.com)
     and log in using the Oracle Single Sign-On (SSO) authentication service.
     If you do not already have SSO credentials,
     then at the top of the page, click the Sign In link to create them.

   - Use the web interface to accept the Oracle Standard Terms and Restrictions
     for the Oracle software images that you intend to deploy:

     1. Click the "Middleware" button.

     1. Click one of "weblogic", "weblogic_cpu", "fmw-infrastructure_cpu", etc,
        depending in your [image type](#understand-oracle-container-registry-images).

        For example: If you are following the operator quick start sample
        (which uses "weblogic" GA images), then click the "weblogic" link.

     1. Follow the prompts to sign in with your SSO and accept the terms.

        Your acceptance of these terms is stored in a database
        that links the software images
        to your Oracle Single Sign-On login credentials.
        This database is automatically checked when
        you use docker pull to obtain images from OCR.

   **Note**: This step is only needed once for each image name (not the tag level).
   For example, if you accept the terms for the "weblogic_cpu"
   link in the "middleware" repository, then
   the acceptance applies to all versions of WebLogic CPU images.

1. Log docker in to the Oracle Container Registry. For example,
   the following command will prompt for your SSO credentials:

   ```shell
   $ docker login container-registry.oracle.com
   ```

1. Use docker to pull the desired image:

   ```shell
   $ docker pull container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol8
   ```

   **Important**: If you are following the quick start sample
   (which uses "weblogic" GA images with version 12.2.1.4),
   then pull `container-registry.oracle.com/middleware/weblogic:12.2.1.4`).

1. Use docker to display an inventory of your local image cache:

   ```shell
   $ docker images
   ```

1. If desired, you can [inspect](inspect-images) the content of the image.

__Notes:__
- If you are using a multi-node Kubernetes cluster,
  or your Kubernetes cluster is remote from your locally created or pulled domain image,
  then additional steps are usually required to enusure that your Kubernetes cluster can access the image.
  See [Set up Kubernetes to access domain images](#set-up-kubernetes-to-access-domain-images).
- The operator requires domain images to contain WebLogic Server 12.2.1.3.0 or later.
  When using 12.2.1.3 images, the operator requires that
  the images contain patches 29135930 and 27117282;
  these patches are included in OCR 12.2.1.3 GA and CPU images.

#### Inspect images

If you have local access to a WebLogic Server or Fusion Middleware Infrastructure image
and the image originates from the Oracle Container Registry or
was created using the WebLogic Image Tool,
then you can use the following commands to determine their contents:

**Note**: If you are following the quick start sample
(which uses "weblogic" GA images with version 12.2.1.4),
then replace the image references
below with `container-registry.oracle.com/middleware/weblogic:12.2.1.4`.

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

* If you have images that were generated using the WebLogic Image Tool (WIT), including OCR images,
  and you have installed the tool, then you can obtain useful version and patch information
  using the
  [WIT inspect command](https://oracle.github.io/weblogic-image-tool/userguide/tools/inspect-image/).
  For example:
  ```
  $ imagetool inspect \
  --image=container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol8 \
  --patches
  ```

#### Set up Kubernetes to access domain images

In most operator samples, it is assumed that Kubernetes cluster has a single worker node,
and any images that are needed by that node have either been created on that node or
externally pulled to the node from a registry (using `docker pull`).
This is fine for most demonstration purposes,
and if this assumption is correct, then no additional steps
are needed to ensure that Kubernetes has access to the image.
_Otherwise, additional steps are typically required to ensure that a Kubernetes cluster has access to domain images._

For example, it is typical in production deployments
for the Kubernetes cluster to be remote and have multiple worker nodes,
and to store domain images in a central repository that requires authentication.

Here are three typical options for supplying domain images to such deployments:

- [Option 1: Store images in a central registry and set up image pull secrets on each domain resource](#option-1-store-images-in-a-central-registry-and-set-up-image-pull-secrets-on-each-domain-resource)

- [Option 2: Store images in a central registry and set up a Kubernetes service account with image pull secrets in each domain namespace](#option-2-store-images-in-a-central-registry-and-set-up-a-kubernetes-service-account-with-image-pull-secrets-in-each-domain-namespace)

- [Option 3: Manually place images on Kubernetes cluster nodes](#option-3-manually-place-images-on-kubernetes-cluster-nodes)

##### Option 1: Store images in a central registry and set up image pull secrets on each domain resource

The most commonly used option is to store the image is a central registry,
and set up image pull secrets for a domain resource:

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

  If you are following the Quick Start sample (which creates a domain resource for you),
  then you can set up this action by uncommenting and setting the `imagePullSecretName` setting
  in the sample's `create-domain-inputs.yaml` file.

- If you are using the Oracle Container Registry, then
  you must use the web interface to accept the Oracle Standard Terms and Restrictions
  for the Oracle software images that you intend to deploy.
  You only need to do this once for a particular image.
  See [Obtain images from the Oracle Container Registry](#obtain-images-from-the-oracle-container-registry).

For more information about creating Kubernetes Secrets for accessing
the registry, see the Kubernetes documentation about
[pulling an image from a private registry](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/).

##### Option 2: Store images in a central registry and set up a Kubernetes service account with image pull secrets in each domain namespace

An additional option for accessing an image that is stored in a private registry
is to set up the Kubernetes `ServiceAccount` in the namespace running the
WebLogic domain with a set of image pull secrets thus avoiding the need to
set `imagePullSecrets` for each `Domain` resource being created (because each resource
instance represents a WebLogic domain that the operator is managing):

- Create a Kubernetes "docker-registry" secret in the same manner as shown 
  in the previous option.

- Modify the `ServiceAccount` that is in the same namespace
  as your domain resources to include this image pull secret:

  ```shell
  $ kubectl patch serviceaccount default -n domain1-ns \
  -p '{"imagePullSecrets": [{"name": "my-registry-pull-secret"}]}'
  ```

  Note that this patch command entirely replaces the current list of
  image pull secrets (if any). To include multiple secrets, use
  the following format:
  `-p '{"imagePullSecrets": [{"name": "my-registry-pull-secret"}, {"name": "my-registry-pull-secret2"}]}'`.

For more information about updating a Kubernetes `ServiceAccount`
for accessing the registry, see the Kubernetes documentation about
[configuring service accounts](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/#add-image-pull-secrets-to-a-service-account).

##### Option 3: Manually place images on Kubernetes cluster nodes

Alternatively, it may be preferable to manually place an image in advance
on each Kubernetes worker node in your Kubernetes cluster.

For example, if the desired image is located in a docker registry,
then you can manually call `docker login` and `docker pull` on each
worker node. For the steps to do with Orace Container Registry images,
see [Obtain images from the Oracle Container Registry](#obtain-images-from-the-oracle-container-registry).

As another example,
if the docker image is located in a local docker cache,
then you can get an inventory of the cache by calling `docker images`,
you can save the image to a tar file `docker save -o myimage.tar myimagerepo:myimagetag`,
and finally copy the tar file to each node and call `docker load -o myimage.tar` on each node.

If you choose this approach, then a Kubernetes secret is not required
and your domain resource `domain.spec.imagePullPolicy` must be set to `Never` or `IfNotPresent`.

#### Ensure you are using recently patched images

Please review the following guidance
to ensure that you are using recently patched images:

- For production deployments,
  Oracle requires using
  fully patched custom images that you generate yourself,
  or Critical Patch Update (CPU) images from the
  Oracle Container Registry (OCR).
  CPU images contain `_cpu` in their image name,
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

See the [Overview](#overview) for general information about OCR and custom images,
and [Understand Oracle Container Registry images](#understand-oracle-container-registry-images)
for detailed information about OCR image naming and the differences between GA and CPU images.

See [supported environments]({{< relref "/userguide/platforms/environments.md" >}})
for information about licensed access to WebLogic patches and CPU images.

See [inspect images](#inspect-images)
to learn how to determine the patches and versions of software within a particular image.

### Using the WebLogic Image Tool (WIT)

You can use the
[WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) (WIT)
to create custom images for your domain resource.

#### Install the WebLogic Image Tool

To download and install the WebLogic Image Tool (WIT),
follow the WIT [Setup](https://oracle.github.io/weblogic-image-tool/quickstart/setup/) instructions
and refer to WIT [Quick Start](https://oracle.github.io/weblogic-image-tool/quickstart/quickstart/) Guide.
For example, to download the latest version of the tool,
install it in `/tmp/imagetool`,
and get its command line help:

```
$ curl -m 120 \
  -fL https://github.com/oracle/weblogic-image-tool/releases/latest/download/imagetool.zip \
  -o /tmp/imagetool.zip
$ unzip /tmp/imagetool.zip -d /tmp
$ /tmp/imagetool/bin/imagetool.sh -?
```

#### WIT options overview

The WebLogic Image Tool (WIT) `create`, `update`, or `rebase` commands supply
three different ways to generate a custom WebLogic Server install image
from a base OS image (optionally with WebLogic patches). See:

- [WIT `create` command](#wit-create-command)
- [WIT `update` command](#wit-update-command)
- [WIT `rebase` command](#wit-rebase-command)

In addition, the [WIT `createAuxImage` command](#wit-createauximage-command)
supports creating auxiliary images which
do _not_ contain a WebLogic Server install, 
and instead solely contain WebLogic Deploy Tool binary, model, or archive files;
this option is designed for the Model in Image use case.

Finally, you can use the WIT `inspect` command to inspect images.
See [inspect images](#inspect-images).

##### WIT `create` command

The WIT `create` command:

- Creates a new WebLogic image from a base OS image.

- Can be used for all domain home source types (Domain in Image, Model in Image, and Domain in PV).

- Optionally includes a WDT install and model files in the image
  (for the Model in Image domain home source type).
  See also [Create a custom image with your model inside the image](#create-a-custom-image-with-your-model-inside-the-image).

- Optionally generates a domain home in the image using WLST or WDT
  (for the Domain in Image domain home source type).
  See also [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image).

_Important_:

- The `create` command is _not_ suitable for updating an existing domain home
  in existing Domain in Image images
  when the update is intended for a running domain. Use `rebase` instead, 
  or shutdown the running domain entirely before applying the new image.
  See [Create a custom image with your domain inside the image](#create-a-custom-image-with-your-domain-inside-the-image) for background.

- If you specify the `--pull` parameter,
  and the `--fromImage` base OS image
  refers to an image in a repository,
  and the repository image is newer than the locally cached version of the image,
  then the command will download the repository image
  to the local docker cache and use it
  instead of using the outdated local image.

See [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied).

##### WIT `update` command

The WIT `update` command:

- Can be used for all domain home source types (Domain in Image, Model in Image, and Domain in PV).

- Creates a new WebLogic image layered on
  an existing WebLogic image specified in its `--fromImage` parameter
  (which is in turn layered on a base OS image).

- Optionally generates a domain home in the image using WLST or WDT
  (for the Domain in Image domain home source type).

- Optionally include a WDT install and model files in the image
  (for the Model in Image domain home soure type).

_Important_:

  - Patching using the `update` command results
    in a larger WebLogic Server image size than `rebase` or `create`.

  - The WIT `update` command is not suitable for updating an existing domain home in 
    an existing Domain in Image image
    when the update is intended for a running domain.
    Use the [WIT `rebase` command](#wit-rebase-command) instead,
    or shutdown the running domain entirely before applying the new image.
    See
    [Apply patched images to a running domain](#apply-patched-images-to-a-running-domain)
    for background.

  - If you specify `--pull` parameter,
    and the `--fromImage` refers to an image in a repository,
    and the repository image is newer than the locally cached version of the image,
    then the command will download the repository image
    to the local docker cache and use it
    instead of using the outdated local image.

See [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied).

##### WIT `rebase` command

The WIT `rebase` command is designed to be used with the Domain in Image
domain home source type, and is only needed when you already have a running
domain and want to update the domain with a new image without shutting
down the entire domain first. For details, see [Domain in Image](#domain-in-image).

##### WIT `createAuxImage` command

The WIT `createAuxImage` command
supports creating auxiliary images
for the Model in Image domain home source type
The auxiliary images
solely contain WDT files for the Model in Image use case,
and are used in addition to the
domain resource image that contains your WebLogic and Java installs.

See [Auxiliary images]({{< relref "/userguide/managing-domains/model-in-image/auxiliary-images.md" >}})
in the Model in Image section of the user guide.

#### Create a custom image with patches applied

##### Create a custom base image

Here's an example of using the WIT `create` command to create a base WebLogic Server image
from a base Oracle Linux image, a WebLogic installer download, and a JRE installer download:

1. First, [install the WebLogic Image Tool](#install-the-weblogic-image-tool).

1. Download your desired JRE installer from the 
   [Oracle Technology Network Java download page](https://www.oracle.com/java/technologies/downloads/)
   or from the
   [Oracle Software Delivery Cloud (OSDC)](https://edelivery.oracle.com/osdc/faces/Home.jspx).

1. Download your desired WebLogic Server installer from the
   [Oracle Technology Network WebLogic Server download page](https://www.oracle.com/middleware/technologies/weblogic-server-installers-downloads.html)
   or from the
   [Oracle Software Delivery Cloud (OSDC)](https://edelivery.oracle.com/osdc/faces/Home.jspx).

   Note that the WebLogic Server installers may not be fully patched.
   You will use the "--patches" or "--recommendedPatches" Image Tool options
   in a later step to add patches.

1. Add the installers to your WIT cache using the
   [WIT `cache` command](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/).
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

   For details, see the WIT
   [Quick Start](https://oracle.github.io/weblogic-image-tool/quickstart/quickstart/) guide.

1. Use the WIT [Create Tool](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-image/)
   to build the image using a default Oracle Linux image as its base,
   download the patches,
   and apply the patches.

   For example, use the following command to create a WebLogic Server image
   named `latest_weblogic:12.2.1.4` with
   - the WebLogic Server 12.2.1.4.0 generic installer
   - JDK 8u291
   - the latest version of the Oracle Linux 7 slim container image
   - the latest quarterly Patch Set Update (PSU), which include security fixes, or with one-off patches
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
   named `minimal_weblogic:12.2.1.3` with
   - the WebLogic slim installer instead of the generic installer
   - JDK 8u291
   - the latest version of the Oracle Linux 7 slim container image
   - the minimal patches required for the operator to run a 12.2.1.3 image
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

   Notes:

   - To enable WIT to download patches,
     you must supply your My Oracle Support (Oracle Single Sign-On) credentials
     using the `--user` and `--passwordEnv` parameters.
     This example assumes that you have set the `MYPWD`
     shell environment variable so that it contains your password.
   - The `--type` parameter designates the type of WebLogic Server,
     Fusion Middleware (FMW) Infrastructure install, etc,
     to include in the generated image.
     - For example,
       the `wls` type corresponds to the WebLogic Server (WLS) generic install,
       the `wlsslim` type to the WLS slim install,
       and the `wlsdev` type to the WLS developer install.
     - See
       [Understand Oracle Container Registry images](#understand-oracle-container-registry-images)
       for a discussion of some install types.
     - Run `imagetool -create -h` to get the list of accepted types.  TBD there's no dash
   - The `--recommendedPatches` parameter finds and applies
     the latest PatchSet Update (PSU)
     and recommended patches. This takes precedence over `--latestPSU`.
   - These sample commands use a default base image,
     which is an Oracle Linux OS image,
     and, due to specifying the `--pull` parameter,
     the commands will download (pull) the latest version of this image
     if the latest version is not already cached locally.
   - See the
     [WebLogic Image Tool User Guide](https://oracle.github.io/weblogic-image-tool/userguide/tools/)
     for details about each parameter.

1. After the tool creates the image, verify that the image is in your local repository:

    ```shell
    $ docker images
    ```

   You can also [inspect](#inspect-images) the contents of the image.

##### Create a custom image with your domain inside the image

{{% notice warning %}}
Oracle strongly recommends storing Domain in Image images in a private registry.
A container image that contains a WebLogic domain home has sensitive information
including credentials that are used to access external resources
(for example, a data source password),
and decryption keys
(for example, the `DOMAIN_HOME/security/SerializedSystemIni.dat` domain secret file).
For more information,
see [WebLogic domain in container image protection]({{<relref "/security/domain-security/image-protection.md">}}).
{{% /notice %}}

For the Domain in Image domain home source type,
you must create an image with the WebLogic domain inside the image.
[Samples]({{< relref "/samples/domains/domain-home-in-image/_index.md" >}})
are provided that demonstrate how to create the image using either
WLST to define the domain,
or [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) models to define the domain.

In these samples, you will see a reference to a "base" or `--fromImage` image.
You should use an image with the mandatory patches installed as this base image
that contains the domain home of the sample image.
This image could be an OCR image or a custom image.
See
[Obtain images from the Oracle Container Registry](#obtain-images-from-the-oracle-container-registry)
and
[Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)).
Alternatively, you can create the image with a domain home, and then generate 
a fully patched image using the WIT `rebase` command (the `rebase` command
will copy the domain home from your image into a fully patched image that it generates).

The samples perform multiple steps for you
using a single provided script,
and are not intended for production use.
To help you understand the individual steps,
the following discuss step-by-step approaches for using WLST or WDT
to create the domain home in Domain in Image. 

Let us start with the step-by-step approach for Domain in Image 
images using WLST. These steps stage files to `dii-wlst-stage`,
puts the domain home inside the image at `/u01/oracle/user_projects/domains/dii-wlst`,
assumes the operator source is in `/tmp/weblogic-kubernetes-operator`,
and assumes you have installed WIT in `TBD` following the steps in TBDLink:

{{%expand "CLICK HERE TO VIEW THE SCRIPT." %}}

```
#!/bin/bash

set -eux

# Define paths for:
# - the operator source (assumed to already be downloaded)
# - the WIT install (assumed to already be installed)
# - a staging directory for the image build

srcDir=/tmp/weblogic-kubernetes-operator
imageToolBin=/tmp/imagetool/bin
stageDir=/tmp/dii-wlst-stage

# setup

sampleDir=$srcDir/kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image
mkdir -p $stageDir

# Define location of the domain home within the image

domainHome=/u01/oracle/user_projects/domains/dii-wlst

# Define base image and final image

fromImage=container-registry.oracle.com/middleware/weblogic:12.2.1.4
finalImage=my-dii-wlst:v1

# Copy setup scripts to the staging directory and modify to point to the domain home

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
# - This will run the provided WLST during image creation in order to create the domain home.
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

The image tool will update the base image by running
the provided commands in `--additionalBuildCommands`
using the files provided in `--additionalBuildFiles`.

**Note:** The sample scripts provided in the
`--additionalBuildCommands` and `--additionalBuildFiles`
parameters are not intended for direct production use,
they can change substantially in new versions of WKO
and must be customized to suite you particular use case.

Now we explore the step-by-step approach for Domain in Image 
for using WDT models to create the domain home.
These steps stage files to `/tmp/dii-wdt-stage`,
generate a domain home inside the image at `TBD`,
assume the operator source is in `/tmp/weblogic-kubernetes-operator`,
and assume you have installed WIT in `TBD` following the steps in TBDLink:

{{%expand "CLICK HERE TO VIEW THE SCRIPT." %}}

```
#!/bin/bash

set -eux

# Define paths for:
# - the operator source (assumed to already be downloaded)
# - the WIT install (assumed to already be installed)
# - a staging directory for the image build

srcDir=/tmp/weblogic-kubernetes-operator
imageToolBin=/tmp/imagetool/bin
stageDir=/tmp/dii-wdt-stage
mkdir -p $stageDir

# Define location of the domain home within the image

domainHome=/u01/oracle/user_projects/domains/dii-wdt

# Define base image and final image

fromImage=container-registry.oracle.com/middleware/weblogic:12.2.1.4
finalImage=my-dii-wdt:v1

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

# Create the image 
# (this will run the latest version of the WIT tool during image creation
#  in order to create the domain home from the provided model files)

$imageToolBin/imagetool.sh update \
  --fromImage "$fromImage" \
  --tag "$finalImage" \
  --wdtModel "$stageDir/wdt_model_dynamic.yaml" \
  --wdtVariables "$stageDir/domain.properties" \
  --wdtOperation CREATE \
  --wdtVersion LATEST \
  --wdtDomainHome "$domainHome" \
  --chown=oracle:root

# TBD discuss importance of protecting 'domain.properties'
#     Conclusion: Derek - not much point in discussing 'encrypt option', or 'create' --
#                 just emphasize importance of keeping prop file itself secure due to ADMIN_USER_PASS=
# TBD ditto for WLST
# TBD modify this and WLST to use standard location for image-tool
```

{{% /expand %}}

TBD Tom These samples use the WIT `update` command. Explain when to use `rebase` instead, and how...
TBD Add warning about protecting the images...

##### Create a custom image with your model inside the image

TBD Tom point to model sample for `update`, to ^^^ for `create`, and to `rebase` below for rebase.
TBD Tom mention auxiliary image alternative...

In these samples, you will see a reference to a "base" or `--fromImage` image.
This image could be an OCR image or a custom image.
See
[Obtain images from the Oracle Container Registry](#obtain-images-from-the-oracle-container-registry)
and
[Create a custom image with patches applied](#create-a-custom-image-with-patches-applied).


#### Apply patched images to a running domain

When updating the WebLogic binaries of a running domain in Kubernetes with a patched container image,
the operator applies the update in a zero downtime fashion.
The procedure for the operator to update the running domain differs depending on the
[domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

For a broader description of managing the evolution and mutation
of container images to run WebLogic Server in Kubernetes,
see [CI/CD]({{< relref "/userguide/cicd/_index.md" >}}).

TBD Tom The following sections are repetitive/verbose. Consolidate/remove
plus replace with bullets. Plus move 'rebase' details to earlier sections.
Finally, update the overview to mention this section.

##### Domain in PV

{{% notice warning %}}
Oracle strongly recommends strictly limiting access to Domain in PV domain home files.
A WebLogic domain home has sensitive information
including credentials that are used to access external resources
(for example, a data source password),
and decryption keys
(for example, the `DOMAIN_HOME/security/SerializedSystemIni.dat` domain secret file).
{{% /notice %}}

For the Domain in PV domain home source type,
the container image only contains the JDK and WebLogic Server binaries,
and its domain home is located in a Persistent Volume (PV)
where the domain home is generated by the user.

For this type, you can create your own patched images using the steps
in [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied)
or you can obtain patched images from the Oracle Container Registry
using [Understand Oracle Container Registry images](#understand-oracle-container-registry-images).

To apply the patched image to a running domain of this type,
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

To apply patched images to a running domain of this type,
edit the Domain Resource image reference with the new image name/tag (for example, `oracle/weblogic:12.2.1.4-patched`).
Then, the operator performs a [rolling restart]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.
For information on server restarts, see [Restarting]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting.md" >}}).

##### Model in Image without auxiliary images

For the Model in Image domain home source type _without_ using auxiliary images:

- The container image contains the JDK, WebLogic Server binaries,
  a [WebLogic Deployment Tooling](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) installation,
  and potentially also WDT model files application archive files.

- The domain home is generated by the operator during runtime.

TBD Tom move rebase to Create a custom image with your model inside the image.

Steps:

- Use the WIT `create` and `update` commands 
  as described in [Create a custom image with patches applied](#create-a-custom-image-with-patches-applied).

- Then, edit the Domain Resource `domain.spec.image` attribute with the new image name/tag (`mydomain:v2`).

See also TBD link to MII doc.

##### Domain in Image

For the Domain in Image domain home source type:

- The container image contains the JDK, WebLogic Server binaries, and domain home.

- The domain home is generated during image creation using either WLST or WDT,
  usually with the assistance of the WebLogic Image Tool (WIT).

If you need to update the image for a running Domain in Image domain,
then use the WIT [`rebase`](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/)
command to update the Oracle Home
for an existing domain image using the patched Oracle Home from a patched container image.

The `rebase` command serves two purposes:

- It minimizes the image size. The alternative `update` command does not remove old WebLogic installs
  in the image but instead layers new WebLogic installs on top of the original install.

- It can be used to modify WebLogic domain home configuration instead of entirely replacing it.
  This preserves the original domain home's security configuration
  files so that your updated images and original images
  can interoperate without a 
  [domain secret mismatch]({{< relref "/faq/domain-secret-mismatch.md" >}}).
  This ensures that pods that are based on the new image
  are capable of joining an already running
  domain with pods on an older version of the image with same security configuration.

The `rebase` does the following:

- Creates a new WebLogic image by copying an existing WebLogic domain home
  from an existing image to a new image.
  It finds the domain home location within the original image
  using the image's internal DOMAIN_HOME environment variable.

- Maintains the same security configuration
  as the original image because the domain home is copied
  (for example, the 'DOMAIN_HOME/security/SerializedSystemIni.dat' file).
  This ensures that pods that are based on the new image
  are capable of joining an already running
  domain with pods on an older version of the image with same security configuration.

Using `rebase`, the new image can be created in one of two ways:

- As a new layer on an existing
  WebLogic image in the repository
  that doesn't already have a domain home (similar to the `update` command).
  To activate:
  - Set `--tag` to the name of the final new image.
  - Set `--sourceImage` to the WebLogic image that contains the WebLogic configuration.
  - Set `--targetImage` to the image that you will you use as a based for the new layer.

- Or as a new WebLogic image from a base OS image (similar to the `create` command).
  To activate:
  - Set `--tag` to the name of the final new image.
  - Set `--sourceImage` to the WebLogic image that contains the WebLogic configuration.
  - Do _not_ set `--targetImage`.
  - Set additional fields (such as the WebLogic kit and JDK locations),
    similar to those used by 'create'. When
    you don't specify a `--targetImage`, 'rebase' will use
    the same options and defaults as 'create'.
    - TBD link to create example above.

TBD Derek modify wording above to cover case where rebase's
    new image doesn't already contain an ORACLE_HOME install.

See also
[the `rebase` command reference](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/)
in the WebLogic Image Tool user guide.

- Use the WIT [`rebase`](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/) command
  to update the Oracle Home for an existing image with the model and archive files in the image using the patched Oracle Home from a
  patched container image. The patched image can be an image that you
  created using the 
  For example, the following command
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

  The `--tag` is the TBD

Example: WIT copies the domain from the source image, `mydomain:v1`, to a new image, `mydomain:v2`, based on a target
image named `oracle/weblogic:12.2.1.4-patched`.

**Note**: Oracle Home and the JDK must be installed in the same directories on each image.

```shell
$ imagetool rebase \
  --tag mydomain:v2 \
  --sourceImage mydomain:v1 \
  --targetImage oracle/weblogic:12.2.1.4-patched
```

TBD Note that '--targetImage' is the base image for the '--tag' image...

TBD Ask Derek. How does this command figure out the WL install/patches you want to use?
    Conclusion: If targetImage not specified, then use the same parameters as create,
    such as "--fromImage", "--version", "--patches", --recommendedPatches, --latestPSU, 

Then, edit the Domain Resource `domain.spec.image` attribute with the new image name/tag (`mydomain:2`).
Then, the operator performs a
[rolling update]({{< relref "/userguide/managing-domains/domain-lifecycle/restarting#overview" >}}) of the domain,
updating the Oracle Home of each server pod.

### more TBD

Tom word-smith with Derek & Monica to make this suitable
for all locations in the doc, including "quick start":

- For samples that mention a specific GA WebLogic image:
{{% notice warning %}}
The `container-registry.oracle.com/middleware/weblogic:12.2.1.4` 
Oracle Container Registry (OCR) image is a "GA" image
which includes the latest security patches for Oracle Linux and Java,
_and does not include the latest security patches for WebLogic Server_.
GA images are intended for single desktop demonstration and development purposes. 
For all other uses, Oracle strongly recommends using images with the latest security patches,
such as OCR Critical Patch Updates (CPU) images or custom generated images.
See [Ensure you are using recently patched images]({{< relref "/userguide/base-images/_index.md#ensure-you-are-using-recently-patched-images" >}}).
{{% /notice %}}

- For samples that mention a specific GA FMW Infra image
  replace 'WebLogic Server' with 'Fusion Middleware Infrastructure' in the warning above.

- For the generic GA case:
{{% notice warning %}}
The latest Oracle Container Registry (OCR) "GA"
include the latest security patches for Oracle Linux and Java,
_and do not include the latest security patches_ for WebLogic Server,
or, when applicable, for Fusion Middleware Infrastructure.
Oracle strongly recommends using images with the latest security patches,
such as OCR Critical Patch Updates (CPU) images or custom generated images.
See [Ensure you are using recently patched images]({{< relref "/userguide/base-images/_index.md#ensure-you-are-using-recently-patched-images" >}}).
{{% /notice %}}

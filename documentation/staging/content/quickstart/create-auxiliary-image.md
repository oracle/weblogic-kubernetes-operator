+++
title = "Create auxiliary image (optional)"
date = 2019-02-23T16:45:16-05:00
weight = 6
pre = "<b> </b>"
description = "Auxiliary images are an alternative approach for supplying a domain's model files or other types of files."
+++

#### Contents
* [Introduction](#introduction)
* [Prerequisites](#prerequisites)
* [Create the auxiliary image](#create-the-auxiliary-image)

#### Introduction
The quick start guide uses the Model in Image [domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}) and supplies the WebLogic configuration using the [WebLogic Deployment Tool](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) model YAML file in a separate [auxiliary image]({{< relref "/userguide/managing-domains/model-in-image/auxiliary-images.md" >}}). The WDT model YAML file compactly defines a WebLogic domain and is a convenient and simple alternative to WebLogic Scripting Tool (WLST) configuration scripts and templates. WDT model supports including application archives in a ZIP file which is also supplied using the auxiliary image. 

The instructions in following sections will guide you, step-by-step, through the process of creating an auxiliary image using [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) (WIT). This lets you understand and customize the auxiliary image creation steps. If you wish to use a ready-made, off-the-shelf auxiliary image instead of creating your own image, then you can skip to the next section and revisit this section at a later time to learn the auxiliary image creation process.


#### Prerequisites
1. The JAVA_HOME environment variable must be set and must reference a valid JDK 8 or 11 installation.

1. Download the latest [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling/releases) (WDT) and [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool/releases) (WIT) installer ZIP files to a new directory; for example, use directory `/tmp/quickstart/tools`. Both WDT and WIT are required to create your Model in Image auxiliary images.

   For example:
   ```
   $ rm -rf /tmp/quickstart/tools
   $ mkdir -p /tmp/quickstart/tools
   ```
   The `rm -rf` command is included in case there's an
   old version of the tool from a
   previous run of this quickstart tutorial.

   ```shell
   $ cd /tmp/quickstart/tools
   ```
   ```shell
   $ curl -m 120 -fL https://github.com/oracle/weblogic-deploy-tooling/releases/latest/download/weblogic-deploy.zip \
     -o /tmp/quickstart/tools/weblogic-deploy.zip
   ```
   ```shell
   $ curl -m 120 -fL https://github.com/oracle/weblogic-image-tool/releases/latest/download/imagetool.zip \
     -o /tmp/quickstart/tools/imagetool.zip
   ```

1. To set up the WebLogic Image Tool, run the following commands:

   ```shell
   $ unzip imagetool.zip
   ```
   ```shell
   $ ./imagetool/bin/imagetool.sh cache deleteEntry --key wdt_latest
   ```
   ```shell
   $ ./imagetool/bin/imagetool.sh cache addInstaller \
     --type wdt \
     --version latest \
     --path /tmp/quickstart/tools/weblogic-deploy.zip
   ```

   Note that the WebLogic Image Tool `cache deleteEntry` command does nothing
   if the `wdt_latest` key doesn't have a corresponding cache entry. It is included
   because the WIT cache lookup information is stored in the `$HOME/cache/.metadata`
   file by default, and if the cache already
   has a version of WDT in its `--type wdt --version latest` location, then the
   `cache addInstaller` command would fail.
   For more information about the WIT cache, see the
   [WIT Cache documentation](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/).

   These steps will install WIT to the `/tmp/quickstart/tools/imagetool` directory,
   plus put a `wdt_latest` entry in the tool's cache which points to the WDT ZIP file installer.
   You will use WIT and its cached reference to the WDT installer later in the sample for creating model images.

1. Get the WDT model, archives and properties files to be included in the auxiliary image and put them in your `/tmp/quickstart/models` directory.

   For example:
   ```
   $ rm -rf /tmp/quickstart/models
   $ mkdir -p /tmp/quickstart/models
   ```
   The `rm -rf` command is included in case there's an
   old version of the archive ZIP file from a
   previous run of this sample.


   ```
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/resources/model.yaml -o /tmp/quickstart/models/model.yaml
   ```

   ```
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/resources/variables.properties -o /tmp/quickstart/models/variables.properties
   ```

   ```
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/resources/archive.zip -o /tmp/quickstart/models/archive.zip
   ```

#### Create the auxiliary image

Follow these steps to create an auxiliary image containing
Model In Image model files, application archives, and the WDT installation files:


1. Use the `createAuxImage` option of the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-aux-image/) (WIT) to create the auxiliary image. Run the following command:

     ```shell
     $ /tmp/quickstart/tools/imagetool/bin/imagetool.sh createAuxImage \
       --tag mii-aux-image:v1 \
       --wdtModel /tmp/quickstart/models/model.yaml \
       --wdtVariables /tmp/quickstart/models/variables.properties \
       --wdtArchive /tmp/quickstart/models/archive.zip
     ```

     When you run this command, the Image Tool will create an auxiliary image with the specified model, variables, and archive files in the
     image's `/auxiliary/models` directory. It will also add the latest version of the WDT installation in its `/auxiliary/weblogic-deploy` directory.
     See [Create Auxiliary Image](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-aux-image/) for additional Image Tool options.
     The operator auxiliary image feature looks for WDT model and WDT install files in these specific directories by default; if you change
     the location of these directories, then change the corresponding domain resource auxiliary image [source locations attributes](#source-locations).

1. If you have successfully created the image, then it should now be in your local machine's Docker repository. For example:

    ```
    $ docker images mii-aux-image:v1
    REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
    model-in-image      WLS-AI-v1           eac9030a1f41        1 minute ago        4.04MB
    ```


1. After the image is created, it should have the WDT executables in
   `/auxiliary/weblogic-deploy`, and WDT model, property, and archive
   files in `/auxiliary/models`. You can run `ls` in the Docker
   image to verify this:

   ```shell
   $ docker run -it --rm mii-aux-image:v1 ls -l /auxiliary
     total 8
     drwxr-xr-x    1 oracle   root          4096 Jun  1 21:53 models
     drwxr-xr-x    1 oracle   root          4096 May 26 22:29 weblogic-deploy

   $ docker run -it --rm mii-aux-image:v1 ls -l /auxiliary/models
     total 16
     -rw-rw-r--    1 oracle   root          5112 Jun  1 21:52 archive.zip
     -rw-rw-r--    1 oracle   root           173 Jun  1 21:59 model.10.properties
     -rw-rw-r--    1 oracle   root          1515 Jun  1 21:59 model.10.yaml

   $ docker run -it --rm mii-aux-image:v1 ls -l /auxiliary/weblogic-deploy
     total 28
     -rw-r-----    1 oracle   root          4673 Oct 22  2019 LICENSE.txt
     -rw-r-----    1 oracle   root            30 May 25 11:40 VERSION.txt
     drwxr-x---    1 oracle   root          4096 May 26 22:29 bin
     drwxr-x---    1 oracle   root          4096 May 25 11:40 etc
     drwxr-x---    1 oracle   root          4096 May 25 11:40 lib
     drwxr-x---    1 oracle   root          4096 Jan 22  2019 samples

   ```

1. Copy the image to all the nodes in your cluster, or put it in a container registry that your cluster can access.

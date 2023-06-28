---
title: "Under the covers"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 5
---

Here's some insight into what's happening under the covers during the Quick Start tutorial.

- The Quick Start guide first installs the WebLogic Kubernetes Operator, then creates a domain using the _Model in Image_ domain home source type.

  - For a comparison of Model in Image to other domain home source types, see [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).
  - To learn more about Model in Image domains, see the detailed [Model in Image]({{< relref "/managing-domains/model-in-image/_index.md" >}}) user guide.
  - Also recommended, review a detailed Model in Image sample [here]({{< relref "/samples/domains/model-in-image/_index.md" >}}).

- The WebLogic domain configuration is specified using the [WebLogic Deployment Tool](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) model YAML file in a separate _auxiliary image_.

  - The auxiliary image contains a WebLogic domain and WebLogic application defined by using WDT model YAML and application archive files.
  - To learn more about auxiliary images, see the [user guide]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).
  - If you want to step through the auxiliary image creation process, follow the instructions in the Advanced [do-it-yourself](#advanced-do-it-yourself) section.

- The operator detects the domain and cluster resources, and deploys their WebLogic Server Administration Server and Managed Server pods.

### Advanced do-it-yourself

The following instructions guide you, step-by-step, through the process of creating the Quick Start auxiliary image using the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) (WIT).
These steps help you understand and customize auxiliary image creation. Then you'll see how to use that image in the domain creation.

#### Prerequisites.
1. The `JAVA_HOME` environment variable must be set and must reference a valid [JDK](https://www.oracle.com/java/technologies/downloads/) 8 or 11 installation.

1. Download the latest [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling/releases) (WDT) and [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool/releases) (WIT) installer ZIP files to a new directory; for example, use directory `/tmp/quickstart/tools`. Both WDT and WIT are required to create your Model in Image auxiliary images.

   For example:
   ```shell
   $ mkdir -p /tmp/quickstart/tools
   ```

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

1. To set up the WebLogic Image Tool, run the following commands.

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
   `cache addInstaller` command will fail.
   For more information about the WIT cache, see the
   [cache](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/) documentation.

   These steps install WIT to the `/tmp/quickstart/tools/imagetool` directory
   and put a `wdt_latest` entry in the tool's cache, which points to the WDT ZIP file installer.
   You will use WIT and its cached reference to the WDT installer later in the sample for creating model images.

1. Download the sample WDT model, web application, and properties files to be included in the auxiliary image and put them in your `/tmp/quickstart/models` directory.
Then use the `jar` command to put the web application files into a model archive ZIP file.

   For example:
   ```shell
   $ mkdir -p /tmp/quickstart/models/archive/wlsdeploy/applications/quickstart/WEB-INF
   ```

   ```shell
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/quick-start/model.yaml -o /tmp/quickstart/models/model.yaml
   ```

   ```shell
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/quick-start/model.properties -o /tmp/quickstart/models/model.properties
   ```

   ```shell
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/quick-start/archive/wlsdeploy/applications/quickstart/index.jsp -o /tmp/quickstart/models/archive/wlsdeploy/applications/quickstart/index.jsp
   ```

   ```shell
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/quick-start/archive/wlsdeploy/applications/quickstart/WEB-INF/web.xml -o /tmp/quickstart/models/archive/wlsdeploy/applications/quickstart/WEB-INF/web.xml
   ```

   ```shell
   $ jar cvf /tmp/quickstart/models/archive.zip -C /tmp/quickstart/models/archive/ wlsdeploy
   ```

#### Create the auxiliary image.

Follow these steps to create the auxiliary image containing
WDT model YAML files, application archives, and the WDT installation files.


1. Use the `createAuxImage` option of the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-aux-image/) (WIT) to create the auxiliary image.

     ```shell
     $ /tmp/quickstart/tools/imagetool/bin/imagetool.sh createAuxImage \
       --tag quick-start-aux-image:v1 \
       --wdtModel /tmp/quickstart/models/model.yaml \
       --wdtVariables /tmp/quickstart/models/model.properties \
       --wdtArchive /tmp/quickstart/models/archive.zip
     ```

     When you run this command, the Image Tool will create an auxiliary image with the specified model, variables, and archive files in the
     image's `/auxiliary/models` directory. It will also add the latest version of the WDT installation in its `/auxiliary/weblogic-deploy` directory.
     See [Create Auxiliary Image](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-aux-image/) for additional Image Tool options.

1. If you have successfully created the image, then it should now be in your local machine's Docker repository. For example:

    ```
    $ docker images quick-start-aux-image:v1
    REPOSITORY                 TAG                 IMAGE ID            CREATED             SIZE
    quick-start-aux-image      v1                  eac9030a1f41        1 minute ago        4.04MB
    ```


1. After the image is created, it will have the WDT executables in
   `/auxiliary/weblogic-deploy`, and WDT model, property, and archive
   files in `/auxiliary/models`. You can run `ls` in the Docker
   image to verify this.

   ```shell
   $ docker run -it --rm quick-start-aux-image:v1 ls -l /auxiliary
     total 8
     drwxr-xr-x    1 oracle   root          4096 Jun  1 21:53 models
     drwxr-xr-x    1 oracle   root          4096 May 26 22:29 weblogic-deploy

   $ docker run -it --rm quick-start-aux-image:v1 ls -l /auxiliary/models
     total 16
     -rw-rw-r--    1 oracle   root          1663 Jun  1 21:52 archive.zip
     -rw-rw-r--    1 oracle   root           173 Jun  1 21:59 model.10.properties
     -rw-rw-r--    1 oracle   root          1515 Jun  1 21:59 model.10.yaml

   $ docker run -it --rm quick-start-aux-image:v1 ls -l /auxiliary/weblogic-deploy
     total 28
     -rw-r-----    1 oracle   root          4673 Oct 22  2019 LICENSE.txt
     -rw-r-----    1 oracle   root            30 May 25 11:40 VERSION.txt
     drwxr-x---    1 oracle   root          4096 May 26 22:29 bin
     drwxr-x---    1 oracle   root          4096 May 25 11:40 etc
     drwxr-x---    1 oracle   root          4096 May 25 11:40 lib
     drwxr-x---    1 oracle   root          4096 Jan 22  2019 samples

   ```

1. Copy the image to all the nodes in your cluster or put it in a container registry that your cluster can access.

#### Create the domain.

If you followed the previous steps to create an auxiliary image, then use these steps to create the domain.

1. Prepare the domain resource.

    a. Download the domain and cluster resource [sample YAML file](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/quick-start/domain-resource.yaml) to a file called `/tmp/quickstart/domain-resource.yaml` or similar.

    b. If you chose a different name and tag for the auxiliary image you created, then update the image field under the `spec.configuration.model.auxiliaryImages`
    section to use that name and tag. For example, if you named the auxiliary image `my-aux-image:v1`, then update the `spec.configuration.model.auxiliaryImages` section as shown.

      ```shell
          auxiliaryImages:
          - image: "my-aux-image:v1"
      ```

    c. If you chose non-default values for any other fields, such as `spec.image`, `spec.imagePullSecrets`, `spec.webLogicCredentialsSecret`, and `spec.configuration.model.runtimeEncryptionSecret`, then update those fields accordingly.

2. Create the domain by applying the domain resource.

      ```shell
       $ kubectl apply -f /tmp/quickstart/domain-resource.yaml
      ```
#### Delete the generated image and directories for tools and models.

Use following commands to delete the generated image and directories for tools and models.

1. Delete the generated image by using the `docker rmi` command. Use the following command to delete an image tagged with `quick-start-aux-image:v1`.

   ```shell
   $ docker rmi quick-start-aux-image:v1
   ```

1. Delete the directory where WebLogic Deploy Tooling and WebLogic Image Tool are installed.

   ```shell
   $ rm -rf /tmp/quickstart/tools/
   ```

1. Delete the directory where the WDT model file, archive, and variable files are copied.

   ```shell
   $ rm -rf /tmp/quickstart/models/
   ```

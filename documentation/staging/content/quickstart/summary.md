---
title: "Under the covers"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 5
---
Here's some insight into what's happening under the covers during the Quick Start tutorial.

- The Quick Start guide first installs the WebLogic Kubernetes operator, then creates a domain using the _Model in Image_ domain home source type.

  - For a comparison of Model in Image to other domain home source types, see [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).
  - To learn more about Model in Image domains, see the detailed [Model in Image]({{< relref "/managing-domains/model-in-image/_index.md" >}}) user guide.
  - Also recommended, review a detailed Model in Image sample [here]({{< relref "/samples/domains/model-in-image/_index.md" >}}).

- The WebLogic domain configuration is specified using the [WebLogic Deployment Tool](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) model YAML file in a separate _auxiliary image_.

  - The auxiliary image contains a WebLogic domain and WebLogic application defined by using WDT model YAML and application archive files.
  - To learn more about auxiliary images, see the [user guide]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).
  - If you want to step through the auxiliary image creation process, follow the instructions in the Advanced [do-it-yourself](#advanced-do-it-yourself) section.

- The operator detects the domain resource and deploys the domain's WebLogic Administration Server and WebLogic Managed Server pods.


### Advanced do-it-yourself

The following instructions guide you, step-by-step, through the process of creating the Quick Start auxiliary image using the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) (WIT).
These steps help you understand and customize auxiliary image creation. Then you'll see how to use that image in the domain creation.

#### Prerequisites
1. The `JAVA_HOME` environment variable must be set and must reference a valid [JDK](https://www.oracle.com/java/technologies/downloads/) 8 or 11 installation.

1. Download the latest [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling/releases) (WDT) and [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool/releases) (WIT) installer ZIP files to a new directory; for example, use directory `/tmp/quickstart/tools`. Both WDT and WIT are required to create your Model in Image auxiliary images.

   For example:
   ```
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
   [cache](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/) documentation.

   These steps install WIT to the `/tmp/quickstart/tools/imagetool` directory,
   plus put a `wdt_latest` entry in the tool's cache which points to the WDT ZIP file installer.
   You will use WIT and its cached reference to the WDT installer later in the sample for creating model images.

1. Download the sample WDT model, archives and properties files to be included in the auxiliary image and put them in your `/tmp/quickstart/models` directory.

   For example:
   ```
   $ mkdir -p /tmp/quickstart/models/archive/wlsdeploy/applications/quickstart/WEB-INF
   ```

   ```
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/quick-start/model.yaml -o /tmp/quickstart/models/model.yaml
   ```

   ```
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/quick-start/variables.properties -o /tmp/quickstart/models/variables.properties
   ```

   ```
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/quick-start/index.jsp -o /tmp/quickstart/models/archive/wlsdeploy/applications/quickstart/index.jsp
   ```

   ```
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/quick-start/WEB-INF/web.xml -o /tmp/quickstart/models/archive/wlsdeploy/applications/quickstart/WEB-INF/web.xml
   ```

   ```
   $ jar cvf /tmp/quickstart/models/archive.zip -C /tmp/quickstart/models/archive/ wlsdeploy
   ```

#### Create the auxiliary image

Follow these steps to create the auxiliary image containing
WDT model YAML files, application archives, and the WDT installation files:


1. Use the `createAuxImage` option of the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-aux-image/) (WIT) to create the auxiliary image. Run the following command:

     ```shell
     $ /tmp/quickstart/tools/imagetool/bin/imagetool.sh createAuxImage \
       --tag quick-start-aux-image:v1 \
       --wdtModel /tmp/quickstart/models/model.yaml \
       --wdtVariables /tmp/quickstart/models/variables.properties \
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
   image to verify this:

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

#### Create the domain

If you followed the previous steps to create an auxiliary image, then use these steps to create the domain.

1. Prepare the domain resource.

    a. Copy the following WLS Domain YAML to a file called `/tmp/quickstart/quick-start-domain-resource.yaml` or similar.

    {{%expand "Click here to view the WLS Domain YAML file using auxiliary images." %}}
```    
 # Copyright (c) 2022, Oracle and/or its affiliates.
 # Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

 apiVersion: "weblogic.oracle/v9"
 kind: Domain
 metadata:
   name: sample-domain1
   namespace: sample-domain1-ns
   labels:
     weblogic.domainUID: sample-domain1

 spec:
   configuration:

     model:
       # Optional auxiliary image(s) containing WDT model, archives, and install.
       # Files are copied from `sourceModelHome` in the aux image to the `/aux/models` directory
       # in running WebLogic Server pods, and files are copied from `sourceWDTInstallHome`
       # to the `/aux/weblogic-deploy` directory. Set `sourceModelHome` and/or `sourceWDTInstallHome`
       # to "None" if you want skip such copies.
       #   `image`                - Image location
       #   `imagePullPolicy`      - Pull policy, default `IfNotPresent`
       #   `sourceModelHome`      - Model file directory in image, default `/auxiliary/models`.
       #   `sourceWDTInstallHome` - WDT install directory in image, default `/auxiliary/weblogic-deploy`.
       auxiliaryImages:
       - image: "quick-start-aux-image:v1"
         #imagePullPolicy: IfNotPresent
         #sourceWDTInstallHome: /auxiliary/weblogic-deploy
         #sourceModelHome: /auxiliary/models

       # Optional configmap for additional models and variable files
       #configMap: sample-domain1-wdt-config-map

       # All 'FromModel' domains require a runtimeEncryptionSecret with a 'password' field
       runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret

   # Set to 'FromModel' to indicate 'Model in Image'.
   domainHomeSourceType: FromModel

   # The WebLogic Domain Home, this must be a location within
   # the image for 'Model in Image' domains.
   domainHome: /u01/domains/sample-domain1

   # The WebLogic Server image that the Operator uses to start the domain
   image: "container-registry.oracle.com/middleware/weblogic:12.2.1.4"

   # Defaults to "Always" if image tag (version) is ':latest'
   imagePullPolicy: "IfNotPresent"

   # Identify which Secret contains the credentials for pulling an image
   imagePullSecrets:
   - name: weblogic-repo-credentials

   # Identify which Secret contains the WebLogic Admin credentials,
   # the secret must contain 'username' and 'password' fields.
   webLogicCredentialsSecret:
     name: sample-domain1-weblogic-credentials

   # Whether to include the WebLogic Server stdout in the pod's stdout, default is true
   includeServerOutInPodLog: true

   # Whether to enable overriding your log file location, see also 'logHome'
   #logHomeEnabled: false

   # The location for domain log, server logs, server out, introspector out, and Node Manager log files
   # see also 'logHomeEnabled', 'volumes', and 'volumeMounts'.
   #logHome: /shared/logs/sample-domain1

   # Set which WebLogic Servers the Operator will start
   # - "NEVER" will not start any server in the domain
   # - "ADMIN_ONLY" will start up only the administration server (no managed servers will be started)
   # - "IF_NEEDED" will start all non-clustered servers, including the administration server, and clustered servers up to their replica count.
   serverStartPolicy: "IF_NEEDED"

   # Settings for all server pods in the domain including the introspector job pod
   serverPod:
     # Optional new or overridden environment variables for the domain's pods
     # - This sample uses CUSTOM_DOMAIN_NAME in its image model file
     #   to set the WebLogic domain name
     env:
     - name: CUSTOM_DOMAIN_NAME
       value: "domain1"
     - name: JAVA_OPTIONS
       value: "-Dweblogic.StdoutDebugEnabled=false"
     - name: USER_MEM_ARGS
       value: "-Djava.security.egd=file:/dev/./urandom -Xms256m -Xmx512m "
     resources:
       requests:
         cpu: "250m"
         memory: "768Mi"

     # Optional volumes and mounts for the domain's pods. See also 'logHome'.
     #volumes:
     #- name: weblogic-domain-storage-volume
     #  persistentVolumeClaim:
     #    claimName: sample-domain1-weblogic-sample-pvc
     #volumeMounts:
     #- mountPath: /shared
     #  name: weblogic-domain-storage-volume

   # The desired behavior for starting the domain's administration server.
   adminServer:
     # The serverStartState legal values are "RUNNING" or "ADMIN"
     # "RUNNING" means the listed server will be started up to "RUNNING" mode
     # "ADMIN" means the listed server will be start up to "ADMIN" mode
     serverStartState: "RUNNING"
     # Setup a Kubernetes node port for the administration server default channel
     #adminService:
     #  channels:
     #  - channelName: default
     #    nodePort: 30701

   # The number of managed servers to start for unlisted clusters
   replicas: 1

   # The desired behavior for starting a specific cluster's member servers
   clusters:
   - clusterName: cluster-1
     serverStartState: "RUNNING"
     serverPod:
       # Instructs Kubernetes scheduler to prefer nodes for new cluster members where there are not
       # already members of the same cluster.
       affinity:
         podAntiAffinity:
           preferredDuringSchedulingIgnoredDuringExecution:
             - weight: 100
               podAffinityTerm:
                 labelSelector:
                   matchExpressions:
                     - key: "weblogic.clusterName"
                       operator: In
                       values:
                         - $(CLUSTER_NAME)
                 topologyKey: "kubernetes.io/hostname"
     # The number of managed servers to start for this cluster
     replicas: 2

   # Change the restartVersion to force the introspector job to rerun
   # and apply any new model configuration, to also force a subsequent
   # roll of your domain's WebLogic Server pods.
   restartVersion: '1'

   # Changes to this field cause the operator to repeat its introspection of the
   #  WebLogic domain configuration.
   introspectVersion: '1'

     # Secrets that are referenced by model yaml macros
     # (the model yaml in the optional configMap or in the image)
     #secrets:
     #- sample-domain1-datasource-secret
     ```
     
    {{% /expand %}}

    b. If you chose a different name and tag for the auxiliary image you created, then update the image field under the `spec.configuration.model.auxiliaryImages`
    section to use that name and tag. For example, if you named the auxiliary image `my-aux-image:v1`, then update the `spec.configuration.model.auxiliaryImages` section as shown:

      ```shell
          auxiliaryImages:
          - image: "my-aux-image:v1"
      ```

    c. If you chose non-default values for any other fields, such as `spec.image`, `spec.imagePullSecrets`, `spec.webLogicCredentialsSecret`, and `spec.configuration.model.runtimeEncryptionSecret`, then update those fields accordingly.

2. Create the domain by applying the domain resource. Run the following command:

      ```shell
       $ kubectl apply -f /tmp/quickstart/quick-start-domain-resource.yaml
      ```

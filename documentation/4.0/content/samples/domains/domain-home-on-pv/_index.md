---
title: "Domain home on a PV"
date: 2019-02-23T17:32:31-05:00
weight: 2
description: "Sample for creating a WebLogic domain home on an PV or PVC for deploying the generated WebLogic domain."
---

### Contents

 - [Overview](#overview)
 - [Prerequisites](#prerequisites)
 - Image creation
    - [Image creation - Introduction](#image-creation---introduction)
    - [Understanding your first archive](#understanding-your-first-archive)
    - [Staging a ZIP file of the archive](#staging-a-zip-file-of-the-archive)
    - [Staging model files](#staging-model-files)
    - [Creating the image with WIT](#creating-the-image-with-wit)
 - Deploy resources
    - [Deploy resources - Introduction](#deploy-resources---introduction)
    - [Secrets](#secrets)
    - [Domain resource](#domain-resource)

#### Overview

The sample demonstrate the setting up of a WebLogic domain with domain home on an Kubernetes PersistentVolume (PV) and PersistentVolumeClaim (PVC). This involves:

  - Building a domain creation image with:
    - A WDT model that describes your WebLogic domain configuration.
    - A WDT archive ZIP file that contains your applications.
    - A WDT installation that contains the binaries for running WDT.
  - Creating secrets for the domain.
  - Creating a Domain YAML file for the domain that references your Secrets, domain creation image, and a WebLogic image.

After the Domain is deployed, the operator starts an 'introspector job' that converts your models into a WebLogic configuration, and then passes this configuration to each WebLogic Server in the domain.

#### Prerequisites

Before you begin, read this document, [Domain resource]({{< relref "/managing-domains/domain-resource/_index.md" >}}).

The following prerequisites must be met prior to running the create domain script:

* Make sure the WebLogic Kubernetes Operator is running.
* The operator requires a WebLogic image with Oracle WebLogic Server 12.2.1.4.0, or Oracle WebLogic Server 14.1.1.0.0. 

   {{% notice warning %}}
   This sample uses General Availability (GA) images. GA images are suitable for demonstration and development purposes _only_ where the environments are not available from the public Internet; they are **not acceptable for production use**. In production, you should always use CPU (patched) images from [OCR]({{< relref "/base-images/ocr-images.md" >}}) or create your images using the [WebLogic Image Tool]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}}) (WIT) with the `--recommendedPatches` option. For more guidance, see [Apply the Latest Patches and Updates](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/14.1.1.0&id=LOCKD-GUID-2DA84185-46BA-4D7A-80D2-9D577A4E8DE2) in _Securing a Production Environment for Oracle WebLogic Server_.
   {{% /notice %}}

* Create a Kubernetes Namespace for the domain unless you intend to use the default namespace.
* Create the Kubernetes Secrets `username` and `password` of the administrative account in the same Kubernetes Namespace as the domain.

{{% notice note %}}
Please note the following important considerations about using persistent storage.
{{% /notice %}}

There are a number of different Kubernetes storage providers that can be used to create persistent
volumes.  You must use a storage provider that supports the `ReadWriteMany` option.

This sample will automatically set the owner of all files on the persistent
volume to `uid 1000`.  If you want to change that behavior, you can configure the desired uid and 
gid in the security context under `serverPod.securityContext` section of the domain YAML file.

#### Domain creation image - Introduction

The sample demonstrates building the domain creation image using the WebLogic Image Tool with an image named `domain-on-pv-image:WLS-v1` from files that you will stage to `/tmp/domain-on-pv-sample/images/domain-on-pv:WLS-v1/`. The staged files will contain a web application in a WDT archive, and WDT model configuration for a WebLogic Administration Server called `admin-server` and a WebLogic cluster called `cluster-1`.

A Domain on PV domain typically supplies one or more domain initialization images with:

A WebLogic Deploy Tooling installation (expected in an image's /auxiliary/weblogic-deploy directory by default).
WDT model YAML, property, and archive files (expected in directory /auxiliary/models by default).
If you do not specify a WDT model YAML file in a domain creation image, then the model YAML file alternately can be supplied dynamically using a Kubernetes ConfigMap that is referenced by your Domain initializeDomainOnPV.domain.domainCreationConfigMap field. We provide an example of using a model ConfigMap later in this sample.

Here are the steps for creating the image `domain-on-pv-image:WLS-v1`:

- [Understanding your first archive](#understanding-your-first-archive)
- [Staging a ZIP file of the archive](#staging-a-zip-file-of-the-archive)
- [Staging model files](#staging-model-files)
- [Creating the image with WIT](#creating-the-image-with-wit)

#### Understanding your first archive

The sample includes a predefined archive directory in `/tmp/domain-on-pv-sample/archives/archive-v1` that you will use to create an archive ZIP file for the image.

The archive top directory, named `wlsdeploy`, contains a directory named `applications`, which includes an 'exploded' sample JSP web application in the directory, `myapp-v1`. Three useful aspects to remember about WDT archives are:
  - A model image can contain multiple WDT archives.
  - WDT archives can contain multiple applications, libraries, and other components.
  - WDT archives have a [well defined directory structure](https://oracle.github.io/weblogic-deploy-tooling/concepts/archive/), which always has `wlsdeploy` as the top directory.

{{%expand "If you are interested in the web application source, click here to see the JSP code." %}}

```java
<%-- Copyright (c) 2019, 2023, Oracle and/or its affiliates. --%>
<%-- Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl. --%>
<%@ page import="javax.naming.InitialContext" %>
<%@ page import="javax.management.*" %>
<%@ page import="java.io.*" %>
<%
  InitialContext ic = null;
  try {
    ic = new InitialContext();

    String srName=System.getProperty("weblogic.Name");
    String domainUID=System.getenv("DOMAIN_UID");
    String domainName=System.getenv("CUSTOM_DOMAIN_NAME");

    out.println("<html><body><pre>");
    out.println("*****************************************************************");
    out.println();
    out.println("Hello World! This is version 'v1' of the domain-on-pv-sample JSP web-app.");
    out.println();
    out.println("Welcome to WebLogic Server '" + srName + "'!");
    out.println();
    out.println("  domain UID  = '" + domainUID +"'");
    out.println("  domain name = '" + domainName +"'");
    out.println();

    MBeanServer mbs = (MBeanServer)ic.lookup("java:comp/env/jmx/runtime");

    // display the current server's cluster name

    Set<ObjectInstance> clusterRuntimes = mbs.queryMBeans(new ObjectName("*:Type=ClusterRuntime,*"), null);
    out.println("Found " + clusterRuntimes.size() + " local cluster runtime" + (String)((clusterRuntimes.size()!=1)?"s":"") + ":");
    for (ObjectInstance clusterRuntime : clusterRuntimes) {
       String cName = (String)mbs.getAttribute(clusterRuntime.getObjectName(), "Name");
       out.println("  Cluster '" + cName + "'");
    }
    out.println();

    // display the Work Manager configuration created by the sample

    Set<ObjectInstance> minTCRuntimes = mbs.queryMBeans(new ObjectName("*:Type=MinThreadsConstraintRuntime,Name=SampleMinThreads,*"), null);
    for (ObjectInstance minTCRuntime : minTCRuntimes) {
       String cName = (String)mbs.getAttribute(minTCRuntime.getObjectName(), "Name");
       int count = (int)mbs.getAttribute(minTCRuntime.getObjectName(), "ConfiguredCount");
       out.println("Found min threads constraint runtime named '" + cName + "' with configured count: " + count);
    }
    out.println();

    Set<ObjectInstance> maxTCRuntimes = mbs.queryMBeans(new ObjectName("*:Type=MaxThreadsConstraintRuntime,Name=SampleMaxThreads,*"), null);
    for (ObjectInstance maxTCRuntime : maxTCRuntimes) {
       String cName = (String)mbs.getAttribute(maxTCRuntime.getObjectName(), "Name");
       int count = (int)mbs.getAttribute(maxTCRuntime.getObjectName(), "ConfiguredCount");
       out.println("Found max threads constraint runtime named '" + cName + "' with configured count: " + count);
    }
    out.println();

    // display local data sources
    // - note that data source tests are expected to fail until the sample Update 4 use case updates the data source's secret

    ObjectName jdbcRuntime = new ObjectName("com.bea:ServerRuntime=" + srName + ",Name=" + srName + ",Type=JDBCServiceRuntime");
    ObjectName[] dataSources = (ObjectName[])mbs.getAttribute(jdbcRuntime, "JDBCDataSourceRuntimeMBeans");
    out.println("Found " + dataSources.length + " local data source" + (String)((dataSources.length!=1)?"s":"") + ":");
    for (ObjectName dataSource : dataSources) {
       String dsName  = (String)mbs.getAttribute(dataSource, "Name");
       String dsState = (String)mbs.getAttribute(dataSource, "State");
       String dsTest  = (String)mbs.invoke(dataSource, "testPool", new Object[] {}, new String[] {});
       out.println(
           "  Datasource '" + dsName + "': "
           + " State='" + dsState + "',"
           + " testPool='" + (String)(dsTest==null ? "Passed" : "Failed") + "'"
       );
       if (dsTest != null) {
         out.println(
               "    ---TestPool Failure Reason---\n"
             + "    NOTE: Ignore 'mynewdatasource' failures until the MII sample's Update 4 use case.\n"
             + "    ---\n"
             + "    " + dsTest.replaceAll("\n","\n   ").replaceAll("\n *\n","\n") + "\n"
             + "    -----------------------------");
       }
    }
    out.println();

    out.println("*****************************************************************");

  } catch (Throwable t) {
    t.printStackTrace(new PrintStream(response.getOutputStream()));
  } finally {
    out.println("</pre></body></html>");
    if (ic != null) ic.close();
  }
%>
```
{{% /expand %}}

The application displays important details about the WebLogic Server instance that it's running on: namely its domain name, cluster name, and server name, as well as the names of any data sources that are targeted to the server. Also, you can see that application output reports that it's at version `v1`.

#### Staging a ZIP file of the archive

When you create the image, you will use the files in the staging directory, `/tmp/domain-on-pv-sample/images/domain-on-pv-image__WLS-v1`. In preparation, you need it to contain a ZIP file of the WDT application archive.

Run the following commands to create your application archive ZIP file and put it in the expected directory:

```
# Delete existing archive.zip in case we have an old leftover version
```
```shell
$ rm -f /tmp/domain-on-pv-sample/images/domain-on-pv-image__WLS-v1/archive.zip
```
```
# Move to the directory which contains the source files for our archive
```
```shell
$ cd /tmp/domain-on-pv-sample/archives/archive-v1
```
```
# Zip the archive to the location will later use when we run the WebLogic Image Tool
```
```shell
$ zip -r /tmp/domain-on-pv-sample/images/domain-on-pv-image__WLS-v1/archive.zip wlsdeploy
```

#### Staging model files

In this step, you explore the staged WDT model YAML file and properties in the `/tmp/domain-on-pv-sample/images/domain-on-pv-image__WLS-v1` directory. The model in this directory references the web application in your archive, configures a WebLogic Server Administration Server, and configures a WebLogic cluster. It consists of two files only, `model.properties`, a file with a single property, and, `model.yaml`, a YAML file with your WebLogic configuration `model.yaml`.

```
CLUSTER_SIZE=5
```

Here is the WLS `model.yaml`:

```yaml
domainInfo:
    AdminUserName: '@@SECRET:__weblogic-credentials__:username@@'
    AdminPassword: '@@SECRET:__weblogic-credentials__:password@@'
    ServerStartMode: 'prod'

topology:
    Name: '@@ENV:CUSTOM_DOMAIN_NAME@@'
    AdminServerName: 'admin-server'
    Cluster:
        'cluster-1':
            DynamicServers:
                ServerTemplate:  'cluster-1-template'
                ServerNamePrefix: 'managed-server'
                DynamicClusterSize: '@@PROP:CLUSTER_SIZE@@'
                MaxDynamicClusterSize: '@@PROP:CLUSTER_SIZE@@'
                MinDynamicClusterSize: '0'
                CalculatedListenPorts: false
    Server:
        'admin-server':
            ListenPort: 7001
    ServerTemplate:
        'cluster-1-template':
            Cluster: 'cluster-1'
            ListenPort: 8001

appDeployments:
    Application:
        myapp:
            SourcePath: 'wlsdeploy/applications/myapp-v1'
            ModuleType: ear
            Target: 'cluster-1'

resources:
  SelfTuning:
    MinThreadsConstraint:
      SampleMinThreads:
        Target: 'cluster-1'
        Count: 1
    MaxThreadsConstraint:
      SampleMaxThreads:
        Target: 'cluster-1'
        Count: 10
    WorkManager:
      SampleWM:
        Target: 'cluster-1'
        MinThreadsConstraint: 'SampleMinThreads'
        MaxThreadsConstraint: 'SampleMaxThreads'
```

{{%expand "Click here to view the JRF `model.yaml`, and note the `RCUDbInfo` stanza and its references to a `DOMAIN_UID-rcu-access` secret." %}}

```yaml
domainInfo:
    AdminUserName: '@@SECRET:__weblogic-credentials__:username@@'
    AdminPassword: '@@SECRET:__weblogic-credentials__:password@@'
    ServerStartMode: 'prod'
    RCUDbInfo:
        rcu_prefix: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_prefix@@'
        rcu_schema_password: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_schema_password@@'
        rcu_db_conn_string: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_db_conn_string@@'

topology:
    AdminServerName: 'admin-server'
    Name: '@@ENV:CUSTOM_DOMAIN_NAME@@'
    Cluster:
        'cluster-1':
    Server:
        'admin-server':
            ListenPort: 7001
        'managed-server1-c1-':
            Cluster: 'cluster-1'
            ListenPort: 8001
        'managed-server2-c1-':
            Cluster: 'cluster-1'
            ListenPort: 8001
        'managed-server3-c1-':
            Cluster: 'cluster-1'
            ListenPort: 8001
        'managed-server4-c1-':
            Cluster: 'cluster-1'
            ListenPort: 8001

appDeployments:
    Application:
        myapp:
            SourcePath: 'wlsdeploy/applications/myapp-v1'
            ModuleType: ear
            Target: 'cluster-1'

resources:
  SelfTuning:
    MinThreadsConstraint:
      SampleMinThreads:
        Target: 'cluster-1'
        Count: 1
    MaxThreadsConstraint:
      SampleMaxThreads:
        Target: 'cluster-1'
        Count: 10
    WorkManager:
      SampleWM:
        Target: 'cluster-1'
        MinThreadsConstraint: 'SampleMinThreads'
        MaxThreadsConstraint: 'SampleMaxThreads'
```
{{% /expand %}}


The model files:

- Define a WebLogic domain with:
  - Cluster `cluster-1`
  - Administration Server `admin-server`
  - A `cluster-1` targeted `ear` application that's located in the WDT archive ZIP file at `wlsdeploy/applications/myapp-v1`
  - A Work Manager `SampleWM` configured with minimum threads constraint `SampleMinThreads` and maximum threads constraint `SampleMaxThreads`

- Leverage macros to inject external values:
  - The property file `CLUSTER_SIZE` property is referenced in the model YAML file `DynamicClusterSize` and `MaxDynamicClusterSize` fields using a PROP macro.
  - The model file domain name is injected using a custom environment variable named `CUSTOM_DOMAIN_NAME` using an ENV macro.
    - You set this environment variable later in this sample using an `env` field in its Domain.
    - _This conveniently provides a simple way to deploy multiple differently named domains using the same model image._
  - The model file administrator user name and password are set using a `weblogic-credentials` secret macro reference to the WebLogic credential secret.
    - This secret is in turn referenced using the `webLogicCredentialsSecret` field in the Domain.
    - The `weblogic-credentials` is a reserved name that always dereferences to the owning Domain actual WebLogic credentials secret name.

A Domain on PV image can contain multiple properties files, archive ZIP files, and YAML files but in this sample you use just one of each. For a complete description of Domain on PV model file naming conventions, file loading order, and macro syntax, see [Model files]({{< relref "/managing-domains/working-with-wdt-models/model-files.md" >}}) in the Domain on PV user documentation.

#### Creating the image with WIT

**Note**: If you are using JRF in this sample, substitute `JRF` for each occurrence of `WLS` in the following `imagetool` command line.

At this point, you have staged all of the files needed for image `domain-on-pv-image:WLS-v1`; they include:

  - `/tmp/domain-on-pv-sample/images/weblogic-deploy.zip`
  - `/tmp/domain-on-pv-sample/images/domain-on-pv-image__WLS-v1/model.yaml`
  - `/tmp/domain-on-pv-sample/images/domain-on-pv-image__WLS-v1/model.properties`
  - `/tmp/domain-on-pv-sample/images/domain-on-pv-image__WLS-v1/archive.zip`

If you don't see the `weblogic-deploy.zip` file, then you missed a step in the [prerequisites]({{< relref "#prerequisites-1" >}}).

Now, you use the Image Tool to create an auxiliary image named `domain-on-pv-image:WLS-v1`. You've already set up this tool during the prerequisite steps.

Run the following commands to create the image and verify that it worked:

  ```shell
  $ cd /tmp/domain-on-pv-sample/images/domain-on-pv-image__WLS-v1
  ```
  ```shell
  $ /tmp/domain-on-pv-sample/images/imagetool/bin/imagetool.sh createDomainOnPVInitImage \
    --tag domain-on-pv-image:WLS-v1 \
    --wdtModel ./model.yaml \
    --wdtVariables ./model.properties \
    --wdtArchive ./archive.zip
  ```

If you don't see the `imagetool` directory, then you missed a step in the [prerequisites]({{< relref "#prerequisites-1" >}}).

This command runs the WebLogic Image Tool in its Domain on PV mode, and does the following:

  - Builds the final container image as a layer on a small `busybox` base image.
  - Copies the WDT ZIP file that's referenced in the WIT cache into the image.
    - Note that you cached WDT in WIT using the keyword `latest` when you set up the cache during the sample prerequisites steps.
    - This lets WIT implicitly assume it's the desired WDT version and removes the need to pass a `-wdtVersion` flag.
  - Copies the specified WDT model, properties, and application archives to image location `/u01/wdt/models`.

When the command succeeds, it should end with output like the following:

```
[INFO   ] Build successful. Build time=36s. Image tag=domain-on-pv-image:WLS-v1
```

Also, if you run the `docker images` command, then you will see an image named `domain-on-pv-image:WLS-v1`.

After the image is created, it should have the WDT executables in
`/auxiliary/weblogic-deploy`, and WDT model, property, and archive
files in `/auxiliary/models`. You can run `ls` in the Docker
image to verify this:

```shell
$ docker run -it --rm domain-on-pv-image:WLS-v1 ls -l /auxiliary
  total 8
  drwxr-xr-x    1 oracle   root          4096 Jun  1 21:53 models
  drwxr-xr-x    1 oracle   root          4096 May 26 22:29 weblogic-deploy

$ docker run -it --rm domain-on-pv-image:WLS-v1 ls -l /auxiliary/models
  total 16
  -rw-rw-r--    1 oracle   root          5112 Jun  1 21:52 archive.zip
  -rw-rw-r--    1 oracle   root           173 Jun  1 21:59 model.properties
  -rw-rw-r--    1 oracle   root          1515 Jun  1 21:59 model.yaml

$ docker run -it --rm domain-on-pv-image:WLS-v1 ls -l /auxiliary/weblogic-deploy
  total 28
  -rw-r-----    1 oracle   root          4673 Oct 22  2019 LICENSE.txt
  -rw-r-----    1 oracle   root            30 May 25 11:40 VERSION.txt
  drwxr-x---    1 oracle   root          4096 May 26 22:29 bin
  drwxr-x---    1 oracle   root          4096 May 25 11:40 etc
  drwxr-x---    1 oracle   root          4096 May 25 11:40 lib
  drwxr-x---    1 oracle   root          4096 Jan 22  2019 samples
```

**NOTE**: If you have Kubernetes cluster worker nodes that are remote to your local machine, then you need to put the image in a location that these nodes can access. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).
#### Build the Domain creation image 

The following instructions guide you, step-by-step, through the process of building the domain creation image for a Domain on PV using the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) (WIT).
These steps help you understand and customize the domain creation image. Then you'll see how to use that image in the domain creation.

#### Prerequisites.
1. The `JAVA_HOME` environment variable must be set and must reference a valid [JDK](https://www.oracle.com/java/technologies/downloads/) 8 or 11 installation.

1. Download the latest [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling/releases) (WDT) and [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool/releases) (WIT) installer ZIP files to a new directory; for example, use directory `/tmp/domain-init-image/tools`. Both WDT and WIT are required to create your domain creation image for Domain on PV.

   For example:
   ```shell
   $ mkdir -p /tmp/domain-init-image/tools
   ```

   ```shell
   $ cd /tmp/domain-init-image/tools
   ```
   ```shell
   $ curl -m 120 -fL https://github.com/oracle/weblogic-deploy-tooling/releases/latest/download/weblogic-deploy.zip \
     -o /tmp/domain-init-image/tools/weblogic-deploy.zip
   ```
   ```shell
   $ curl -m 120 -fL https://github.com/oracle/weblogic-image-tool/releases/latest/download/imagetool.zip \
     -o /tmp/domain-init-image/tools/imagetool.zip
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
     --path /tmp/domain-init-image/tools/weblogic-deploy.zip
   ```

   Note that the WebLogic Image Tool `cache deleteEntry` command does nothing
   if the `wdt_latest` key doesn't have a corresponding cache entry. It is included
   because the WIT cache lookup information is stored in the `$HOME/cache/.metadata`
   file by default, and if the cache already
   has a version of WDT in its `--type wdt --version latest` location, then the
   `cache addInstaller` command will fail.
   For more information about the WIT cache, see the
   [cache](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/) documentation.

   These steps install WIT to the `/tmp/domain-init-image/tools/imagetool` directory
   and put a `wdt_latest` entry in the tool's cache, which points to the WDT ZIP file installer.
   You will use WIT and its cached reference to the WDT installer later in the sample for creating model images.

1. Download the sample WDT model, web application, and properties files to be included in the auxiliary image and put them in your `/tmp/domain-init-image/models` directory.
Then use the JAR command to put the web application files into a model archive ZIP file.

   For example:
   ```shell
   $ mkdir -p /tmp/domain-init-image/models/archive/wlsdeploy/applications/domain-init-image/WEB-INF
   ```

   ```shell
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/domain-init-image/model.yaml -o /tmp/domain-init-imagedomain-init-image/models/model.yaml
   ```

   ```shell
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/domain-init-image/model.properties -o /tmp/domain-init-image/models/model.properties
   ```

   ```shell
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/domain-init-image/archive/wlsdeploy/applications/domain-init-image/index.jsp -o /tmp/domain-init-image/models/archive/wlsdeploy/applications/domain-init-image/index.jsp
   ```

   ```shell
   $ curl -m 120 -fL https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/domain-init-image/archive/wlsdeploy/applications/domain-init-image/WEB-INF/web.xml -o /tmp/domain-init-image/models/archive/wlsdeploy/applications/domain-init-image/WEB-INF/web.xml
   ```

   ```shell
   $ jar cvf /tmp/domain-init-image/models/archive.zip -C /tmp/domain-init-image/models/archive/ wlsdeploy
   ```

#### Build the domain creation image.

Follow these steps to create the domain creation image containing
WDT model YAML files, application archives, and the WDT installation files.


1. Use the `buildDomainCreationImage` option of the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-aux-image/) (WIT) to create the auxiliary image.

     ```shell
     $ /tmp/domain-init-image/tools/imagetool/bin/imagetool.sh buildDomainCreationImage \
       --tag domain-init-image:v1 \
       --wdtModel /tmp/domain-init-image/models/model.yaml \
       --wdtVariables /tmp/domain-init-image/models/model.properties \
       --wdtArchive /tmp/domain-init-image/models/archive.zip
     ```

     When you run this command, the Image Tool will create an auxiliary image with the specified model, variables, and archive files in the
     image's `/auxiliary/models` directory. It will also add the latest version of the WDT installation in its `/auxiliary/weblogic-deploy` directory.
     See [Build Domain Creation Image](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-aux-image/) for additional Image Tool options.

1. If you have successfully created the image, then it should now be in your local machine's Docker repository. For example:

    ```
    $ docker images domain-init-image:v1
    REPOSITORY                 TAG                 IMAGE ID            CREATED             SIZE
    domain-init-image          v1                  eac9030a1f41        1 minute ago        4.04MB
    ```


1. After the image is created, it will have the WDT executables in
   `/auxiliary/weblogic-deploy`, and WDT model, property, and archive
   files in `/auxiliary/models`. You can run `ls` in the Docker
   image to verify this.

   ```shell
   $ docker run -it --rm domain-init-image:v1 ls -l /auxiliary
     total 8
     drwxr-xr-x    1 oracle   root          4096 Jun  1 21:53 models
     drwxr-xr-x    1 oracle   root          4096 May 26 22:29 weblogic-deploy

   $ docker run -it --rm domain-init-image:v1 ls -l /auxiliary/models
     total 16
     -rw-rw-r--    1 oracle   root          1663 Jun  1 21:52 archive.zip
     -rw-rw-r--    1 oracle   root           173 Jun  1 21:59 model.properties
     -rw-rw-r--    1 oracle   root          1515 Jun  1 21:59 model.yaml

   $ docker run -it --rm domain-init-image:v1 ls -l /auxiliary/weblogic-deploy
     total 28
     -rw-r-----    1 oracle   root          4673 Oct 22  2019 LICENSE.txt
     -rw-r-----    1 oracle   root            30 May 25 11:40 VERSION.txt
     drwxr-x---    1 oracle   root          4096 May 26 22:29 bin
     drwxr-x---    1 oracle   root          4096 May 25 11:40 etc
     drwxr-x---    1 oracle   root          4096 May 25 11:40 lib
     drwxr-x---    1 oracle   root          4096 Jan 22  2019 samples

   ```

1. Copy the image to all the nodes in your cluster or put it in a container registry that your cluster can access.

#### Deploy resources - Introduction

In this section, you will use the new image in the domain resource and deploy it to namespace `sample-domain1-ns`, including the following steps:

  - Create a Secret containing your WebLogic administrator user name and password.
  - Create a Secret containing your domain runtime encryption password:
    - All domains must supply a runtime encryption Secret with a `password` value.
    - It is used to encrypt configuration that is passed around internally by the operator.
    - The value must be kept private but can be arbitrary; you can optionally supply a different secret value every time you restart the domain.
  - If your domain type is `JRF`, create secrets containing your RCU access URL, credentials, and prefix.
  - Deploy a Domain YAML file that references the new image.
  - Wait for the domain's Pods to start and reach their ready state.

#### Secrets

First, create the secrets needed by both `WLS` and `JRF` type model domains. In this case, you have two secrets.

Run the following `kubectl` commands to deploy the required secrets:

  __NOTE:__ Substitute a password of your choice for MY_WEBLOGIC_ADMIN_PASSWORD. This
  password should contain at least seven letters plus one digit.

  __NOTE:__ Substitute a password of your choice for MY_RUNTIME_PASSWORD. It should
  be unique and different than the admin password, but this is not required.

  ```shell
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-weblogic-credentials \
     --from-literal=username=weblogic --from-literal=password=MY_WEBLOGIC_ADMIN_PASSWORD
  ```
  ```shell
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-weblogic-credentials \
    weblogic.domainUID=sample-domain1
  ```
  ```shell
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-runtime-encryption-secret \
     --from-literal=password=MY_RUNTIME_PASSWORD
  ```
  ```shell
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-runtime-encryption-secret \
    weblogic.domainUID=sample-domain1
  ```

  Some important details about these secrets:

  - The WebLogic credentials secret:
    - It is required and must contain `username` and `password` fields.
    - It must be referenced by the `spec.webLogicCredentialsSecret` field in your Domain.
    - It also must be referenced by macros in the `domainInfo.AdminUserName` and `domainInfo.AdminPassWord` fields in your model YAML file.

  - The Model WDT runtime secret:
    - This is a special secret required by Domain on PV.
    - It must contain a `password` field.
    - It must be referenced using the `spec.model.runtimeEncryptionSecret` field in its Domain.
    - It must remain the same for as long as the domain is deployed to Kubernetes but can be changed between deployments.
    - It is used to encrypt data as it's internally passed using log files from the domain's introspector job and on to its WebLogic Server pods.

  - Deleting and recreating the secrets:
    - You delete a secret before creating it, otherwise the create command will fail if the secret already exists.
    - This allows you to change the secret when using the `kubectl create secret` command.

  - You name and label secrets using their associated domain UID for two reasons:
    - To make it obvious which secrets belong to which domains.
    - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all resources associated with a domain.

  If you're following the `JRF` path through the sample, then you also need to deploy the additional secret referenced by macros in the `JRF` model `RCUDbInfo` clause, plus an `OPSS` wallet password secret. For details about the uses of these secrets, see the [Domain on PV]({{< relref "/managing-domains/model-in-image/_index.md" >}}) user documentation.

  {{%expand "Click here for the commands for deploying additional secrets for JRF." %}}

  __NOTE__: Replace `MY_RCU_SCHEMA_PASSWORD` with the RCU schema password
  that you chose in the prequisite steps when
  [setting up JRF]({{< relref "/samples/domains/model-in-image/prerequisites#additional-prerequisites-for-jrf-domains" >}}).

  ```shell
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-rcu-access \
     --from-literal=rcu_prefix=FMW1 \
     --from-literal=rcu_schema_password=MY_RCU_SCHEMA_PASSWORD \
     --from-literal=rcu_db_conn_string=oracle-db.default.svc.cluster.local:1521/devpdb.k8s
  ```
  ```shell
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-rcu-access \
    weblogic.domainUID=sample-domain1
  ```

  __NOTES__:
  - Replace `MY_OPSS_WALLET_PASSWORD` with a password of your choice.
    The password can contain letters and digits.
  - The domain's JRF RCU schema will be automatically initialized
    plus generate a JRF OPSS wallet file upon first use.
    If you plan to save and reuse this wallet file,
    as is necessary for reusing RCU schema data after a migration or restart,
    then it will also be necessary to use this same password again.

  ```shell
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-opss-wallet-password-secret \
     --from-literal=walletPassword=MY_OPSS_WALLET_PASSWORD
  ```
  ```shell
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-opss-wallet-password-secret \
    weblogic.domainUID=sample-domain1
  ```

  {{% /expand %}}

#### Domain resource

Now, you create a `sample-domain1` domain resource and an associated `sample-domain1-cluster-1` cluster resource using a single YAML resource file which defines both resources. The domain resource and cluster resource tells the operator how to deploy a WebLogic domain. They do not replace the traditional WebLogic configuration files, but instead cooperates with those files to describe the Kubernetes artifacts of the corresponding domain.

Copy the following to a file called `/tmp/domain-on-pv-sample/domain-on-pv.yaml` or similar, or use [the domain resource YAML file](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/main/kubernetes/samples/scripts/create-weblogic-domain/domain-on-pv/domain-resources/WLS-AI/domain-on-pv-WLS-v1.yaml) that is included in the sample source.

   - Use the following command to apply the two sample resources.

     ```shell
     $ kubectl apply -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/quick-start/domain-resource.yaml
     ```

   - **NOTE**: If you want to view or need to modify it, you can download the [sample domain resource](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/quick-start/domain-resource.yaml) to a file called `/tmp/quickstart/domain-resource.yaml` or similar. Then apply the file using `kubectl apply -f /tmp/quickstart/domain-resource.yaml`.


   The domain resource references the cluster resource, a WebLogic Server installation image, the secrets you defined, and a sample "auxiliary image", which contains traditional WebLogic configuration and a WebLogic application.

     - To examine the domain resource, click [here](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/quick-start/domain-resource.yaml).
     - For detailed information, see [Domain resource]({{< relref "/managing-domains/domain-resource.md" >}}).

   {{% notice note %}}
   The Quick Start guide's sample domain resource references a WebLogic Server version 12.2.1.4 General Availability (GA) image. GA images are suitable for demonstration and development purposes _only_ where the environments are not available from the public Internet; they are **not acceptable for production use**. In production, you should always use CPU (patched) images from [OCR]({{< relref "/base-images/ocr-images.md" >}}) or create your images using the [WebLogic Image Tool]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}}) (WIT) with the `--recommendedPatches` option. For more guidance, see [Apply the Latest Patches and Updates](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/14.1.1.0&id=LOCKD-GUID-2DA84185-46BA-4D7A-80D2-9D577A4E8DE2) in _Securing a Production Environment for Oracle WebLogic Server_.
   {{% /notice %}}

Now, you create a Domain YAML file. A Domain is the key resource that tells the operator how to deploy a WebLogic domain.

Copy the following to a file called `/tmp/domain-on-pv-sample/domain-on-pv.yaml` or similar, or use the file `/tmp/domain-on-pv-sample/domain-resources/WLS-AI/domain-on-pv-WLS-v1.yaml` that is included in the sample source.

{{%expand "Click here to view the WLS Domain YAML file." %}}
```yaml
# Copyright (c) 2021, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This is an example of how to define a Domain resource.
#
apiVersion: "weblogic.oracle/v9"
kind: Domain
metadata:
  name: sample-domain1
  namespace: sample-domain1-ns
  labels:
    weblogic.domainUID: sample-domain1

spec:
  # Set to 'PersistentVolume' to indicate 'Domain on PV'.
  domainHomeSourceType: PersistentVolume

  # The WebLogic Domain Home, this must be a location within
  # the image for 'Domain on PV' domains.
  domainHome: /shared/domains/sample-domain1

  # The WebLogic Server image that the Operator uses to start the domain
  # **NOTE**:
  # This sample uses General Availability (GA) images. GA images are suitable for demonstration and
  # development purposes only where the environments are not available from the public Internet;
  # they are not acceptable for production use. In production, you should always use CPU (patched)
  # images from OCR or create your images using the WebLogic Image Tool.
  # Please refer to the `OCR` and `WebLogic Images` pages in the WebLogic Kubernetes Operator
  # documentation for details.
  image: "container-registry.oracle.com/middleware/weblogic:12.2.1.4"

  # Defaults to "Always" if image tag (version) is ':latest'
  imagePullPolicy: IfNotPresent

  # Identify which Secret contains the credentials for pulling an image
  #imagePullSecrets:
  #- name: regsecret
  #- name: regsecret2
  
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
  
  configuration:
    # Settings for domainHomeSourceType 'PersistentVolume'
    initializeDomainOnPV:
      persistentVolume:
        metadata:
          name: weblogic-domain-pv
        spec:
          storageClassName: manual
          capacity:
            storage: 5Gi
          hostPath:
            path: "/shared"
      persistentVolumeClaim:
        metadata:
          name: weblogic-domain-pvc
        spec:
          volumeName: weblogic-domain-pv
          storageClassName: manual
          resources:
            requests:
              storage: 1Gi
      domain:
         # Valid model domain types are 'WLS', and 'JRF', default is 'JRF'
         domainType: WLS

         # Domain creation image(s) containing WDT model, archives, and install.
         #   `image`                - Image location
         #   `imagePullPolicy`      - Pull policy, default `IfNotPresent`
         #   `sourceModelHome`      - Model file directory in image, default `/auxiliary/models`.
         #   `sourceWDTInstallHome` - WDT install directory in image, default `/auxiliary/weblogic-deploy`.
         domainCreationImages:
         - image: phx.ocir.io/weblogick8s/domain-on-pv-image:WLS-v1
           #imagePullPolicy: IfNotPresent
           #sourceWDTInstallHome: /auxiliary/weblogic-deploy
           #sourceModelHome: /auxiliary/models

         # Optional configmap for additional models and variable files
         #domainCreationConfigMap: sample-domain1-wdt-config-map

    # Secrets that are referenced by model yaml macros
    # (the model yaml in the optional configMap or in the image)
    #secrets:
    #- sample-domain1-datasource-secret

  # Set which WebLogic Servers the Operator will start
  serverStartPolicy: IfNeeded

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

    # Volumes and mounts for the domain's pods. See also 'logHome'.
    volumes:
    - name: weblogic-domain-storage-volume
      persistentVolumeClaim:
        claimName: sample-domain1-weblogic-sample-pvc
    volumeMounts:
    - mountPath: /shared
      name: weblogic-domain-storage-volume

  # The desired behavior for starting the domain's administration server.
  # adminServer:
    # Setup a Kubernetes node port for the administration server default channel
    #adminService:
    #  channels:
    #  - channelName: default
    #    nodePort: 30701
   
  # The number of managed servers to start for unlisted clusters
  replicas: 1

  # The name of each Cluster resource
  clusters:
  - name: sample-domain1-cluster-1

  # Change the restartVersion to force the introspector job to rerun
  # and apply any new model configuration, to also force a subsequent
  # roll of your domain's WebLogic Server pods.
  restartVersion: '1'

  # Changes to this field cause the operator to repeat its introspection of the
  #  WebLogic domain configuration.
  introspectVersion: '1'

---

apiVersion: "weblogic.oracle/v1"
kind: Cluster
metadata:
  name: sample-domain1-cluster-1
  # Update this with the namespace your domain will run in:
  namespace: sample-domain1-ns
  labels:
    # Update this with the `domainUID` of your domain:
    weblogic.domainUID: sample-domain1
spec:
  # This must match a cluster name that is  specified in the WebLogic configuration
  clusterName: cluster-1
  # The number of managed servers to start for this cluster
  replicas: 2
```
{{% /expand %}}

{{%expand "Click here to view the JRF Domain YAML file." %}}
```yaml
# Copyright (c) 2020, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This is an example of how to define a Domain resource.
#
apiVersion: "weblogic.oracle/v9"
kind: Domain
metadata:
  name: sample-domain1
  namespace: sample-domain1-ns
  labels:
    weblogic.domainUID: sample-domain1

spec:
  # Set to 'PersistentVolume' to indicate 'Domain on PV'.
  domainHomeSourceType: PersistentVolume

  # The WebLogic Domain Home, this must be a location within
  # the image for 'Domain on PV' domains.
  domainHome: /shared/domains/sample-domain1

  # The WebLogic Server image that the Operator uses to start the domain
  # **NOTE**:
  # This sample uses General Availability (GA) images. GA images are suitable for demonstration and
  # development purposes only where the environments are not available from the public Internet;
  # they are not acceptable for production use. In production, you should always use CPU (patched)
  # images from OCR or create your images using the WebLogic Image Tool.
  # Please refer to the `OCR` and `Manage FMW infrastructure domains` pages in the WebLogic
  # Kubernetes Operator documentation for details.
  image: "container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4"

  # Defaults to "Always" if image tag (version) is ':latest'
  imagePullPolicy: IfNotPresent

  # Identify which Secret contains the credentials for pulling an image
  #imagePullSecrets:
  #- name: regsecret
  #- name: regsecret2
  
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

  configuration:
    # Settings for domainHomeSourceType 'PersistentVolume'
    initializeDomainOnPV:
      persistentVolume:
        metadata:
          name: sample-domain1-weblogic-sample-pv
        spec:
          storageClassName: manual
          capacity:
            storage: 5Gi
          hostPath:
            path: "/shared"
      persistentVolumeClaim:
        metadata:
          name: sample-domain1-weblogic-sample-pvc
        spec:
          volumeName: sample-domain1-weblogic-sample-pv
          storageClassName: manual
          resources:
            requests:
              storage: 1Gi
      domain:
         # Valid model domain types are 'WLS', and 'JRF', default is 'JRF'
         domainType: JRF

         # Domain creation image(s) containing WDT model, archives, and install.
         #   `image`                - Image location
         #   `imagePullPolicy`      - Pull policy, default `IfNotPresent`
         #   `sourceModelHome`      - Model file directory in image, default `/auxiliary/models`.
         #   `sourceWDTInstallHome` - WDT install directory in image, default `/auxiliary/weblogic-deploy`.
         domainCreationImages:
         - image: phx.ocir.io/weblogick8s/domain-on-pv-image:WLS-v1
           #imagePullPolicy: IfNotPresent
           #sourceWDTInstallHome: /auxiliary/weblogic-deploy
           #sourceModelHome: /auxiliary/models

         # Optional configmap for additional models and variable files
         #domainCreationConfigMap: sample-domain1-wdt-config-map

         opss:
           walletPasswordSecret: wallet-password-secret
           #walletFileSecret: wallet-file-secret

    # Secrets that are referenced by model yaml macros
    # (the model yaml in the optional configMap or in the image)
    #secrets:
    #- sample-domain1-datasource-secret
  
  # Set which WebLogic Servers the Operator will start
  # - "Never" will not start any server in the domain
  # - "AdminOnly" will start up only the administration server (no managed servers will be started)
  # - "IfNeeded" will start all non-clustered servers, including the administration server, and clustered servers up to their replica count.
  serverStartPolicy: IfNeeded

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
      value: "-Djava.security.egd=file:/dev/./urandom -Xms256m -Xmx1024m "
    resources:
      requests:
        cpu: "500m"
        memory: "1280Mi"

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
    # Setup a Kubernetes node port for the administration server default channel
    #adminService:
    #  channels:
    #  - channelName: default
    #    nodePort: 30701
    serverPod:
      # Optional new or overridden environment variables for the admin pods
      env:
      - name: USER_MEM_ARGS
        value: "-Djava.security.egd=file:/dev/./urandom -Xms512m -Xmx1024m "
   
  # The number of managed servers to start for unlisted clusters
  replicas: 1

  # The name of each Cluster resource
  clusters:
  - name: sample-domain1-cluster-1

  # Change the restartVersion to force the introspector job to rerun
  # and apply any new model configuration, to also force a subsequent
  # roll of your domain's WebLogic Server pods.
  restartVersion: '1'

  # Changes to this field cause the operator to repeat its introspection of the
  #  WebLogic domain configuration.
  introspectVersion: '1'
---

apiVersion: "weblogic.oracle/v1"
kind: Cluster
metadata:
  name: sample-domain1-cluster-1
  # Update this with the namespace your domain will run in:
  namespace: sample-domain1-ns
  labels:
    # Update this with the `domainUID` of your domain:
    weblogic.domainUID: sample-domain1
spec:
  # This must match a cluster name that is  specified in the WebLogic configuration
  clusterName: cluster-1
  # The number of managed servers to start for this cluster
  replicas: 2
```
{{% /expand %}}

  **NOTE**: Before you deploy the domain custom resource, determine if you have Kubernetes cluster worker nodes that are remote to your local machine. If so, you need to put the Domain's image in a location that these nodes can access and you may also need to modify your Domain YAML file to reference the new location. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

  Run the following command to create the domain custom resource:

  ```shell
  $ kubectl apply -f /tmp/domain-on-pv-sample/domain-resources/WLS/domain-on-pv-WLS-v1.yaml
  ```

  **NOTE**: If you are choosing _not_ to use the predefined Domain YAML file and instead created your own Domain YAML file earlier, then substitute your custom file name in the previous command. Previously, we suggested naming it `/tmp/domain-on-pv-sample/domain-on-pv.yaml`.

#### Verify the PV, PVC and domain

To confirm that the PV, PVC and domain were created, use these command:

##### Verify the persistent volume
```shell
$ kubectl get pv
```

Here is an example of the output of this command:
```
NAME                                CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS   CLAIM                                                  STORAGECLASS   REASON   AGE
sample-domain1-weblogic-sample-pv   5Gi        RWX            Retain           Bound    sample-domain1-ns/sample-domain1-weblogic-sample-pvc   manual                  14m
```

##### Verify the persistent volume claim
```shell
$ kubectl get pvc -n NAMESPACE
```

Replace `NAMESPACE` with the actual namespace.

Here is an example of the output of this command:

```shell
$ kubectl get pvc -n sample-domain1-ns
```
```
NAME                                 STATUS   VOLUME                              CAPACITY   ACCESS MODES   STORAGECLASS   AGE
sample-domain1-weblogic-sample-pvc   Bound    sample-domain1-weblogic-sample-pv   5Gi        RWX            manual         11m
```

##### Verify the domain
```shell
$ kubectl describe domain DOMAINUID -n NAMESPACE
```

Replace `DOMAINUID` with the `domainUID` and `NAMESPACE` with the actual namespace.

Here is an example of the output of this command:

```shell
$ kubectl describe domain sample-domain1 -n sample-domain1-ns
```
```
Name:         sample-domain1
Namespace:    sample-domain1-ns
Labels:       weblogic.domainUID=sample-domain1
Annotations:  <none>
API Version:  weblogic.oracle/v9
Kind:         Domain
Metadata:
  Creation Timestamp:  2023-04-28T20:31:09Z
  Generation:          1
  Resource Version:  345297
  UID:               69b69019-c430-4f01-b96c-4364bf1f39c1
Spec:
  Clusters:
    Name:  sample-domain1-cluster-1
  Configuration:
    Initialize Domain On PV:
      Domain:
        Create If Not Exists:  domain
        Domain Creation Images:
          Image:      phx.ocir.io/weblogick8s/domain-on-pv-image:WLS-v1
        Domain Type:  WLS
      Persistent Volume:
        Metadata:
          Name:  sample-domain1-weblogic-sample-pv
        Spec:
          Capacity:
            Storage:  5Gi
          Host Path:
            Path:              /shared
          Storage Class Name:  manual
      Persistent Volume Claim:
        Metadata:
          Name:  sample-domain1-weblogic-sample-pvc
        Spec:
          Resources:
            Requests:
              Storage:               1Gi
          Storage Class Name:        manual
          Volume Name:               sample-domain1-weblogic-sample-pv
    Override Distribution Strategy:  Dynamic
  Domain Home:                       /shared/domains/sample-domain1
  Domain Home Source Type:           PersistentVolume
  Failure Retry Interval Seconds:    120
  Failure Retry Limit Minutes:       1440
  Http Access Log In Log Home:       true
  Image:                             container-registry.oracle.com/middleware/weblogic:12.2.1.4
  Image Pull Policy:                 IfNotPresent
  Include Server Out In Pod Log:     true
  Introspect Version:                1
  Log Home Layout:                   ByServers
  Max Cluster Concurrent Shutdown:   1
  Max Cluster Concurrent Startup:    0
  Max Cluster Unavailable:           1
  Replicas:                          1
  Restart Version:                   1
  Server Pod:
    Env:
      Name:   CUSTOM_DOMAIN_NAME
      Value:  domain1
      Name:   JAVA_OPTIONS
      Value:  -Dweblogic.StdoutDebugEnabled=false
      Name:   USER_MEM_ARGS
      Value:  -Djava.security.egd=file:/dev/./urandom -Xms256m -Xmx512m
    Resources:
      Requests:
        Cpu:     250m
        Memory:  768Mi
    Volume Mounts:
      Mount Path:  /shared
      Name:        weblogic-domain-storage-volume
    Volumes:
      Name:  weblogic-domain-storage-volume
      Persistent Volume Claim:
        Claim Name:     sample-domain1-weblogic-sample-pvc
  Server Start Policy:  IfNeeded
  Web Logic Credentials Secret:
    Name:  sample-domain1-weblogic-credentials
Status:
  Clusters:
    Cluster Name:  cluster-1
    Conditions:
      Last Transition Time:  2023-04-28T20:33:57.983431Z
      Status:                True
      Type:                  Available
      Last Transition Time:  2023-04-28T20:33:57.983495Z
      Status:                True
      Type:                  Completed
    Label Selector:          weblogic.domainUID=sample-domain1,weblogic.clusterName=cluster-1
    Maximum Replicas:        5
    Minimum Replicas:        0
    Observed Generation:     1
    Ready Replicas:          2
    Replicas:                2
    Replicas Goal:           2
  Conditions:
    Last Transition Time:  2023-04-28T20:33:53.450209Z
    Status:                True
    Type:                  Available
    Last Transition Time:  2023-04-28T20:33:58.076050Z
    Status:                True
    Type:                  Completed
  Observed Generation:     1
  Replicas:                2
  Servers:
    Health:
      Activation Time:  2023-04-28T20:33:01.803000Z
      Overall Health:   ok
      Subsystems:
        Subsystem Name:  ServerRuntime
        Symptoms:
    Node Name:     phx32822d1
    Pod Phase:     Running
    Pod Ready:     True
    Server Name:   admin-server
    State:         RUNNING
    State Goal:    RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2023-04-28T20:33:40.165000Z
      Overall Health:   ok
      Subsystems:
    Node Name:     phx32822d1
    Pod Phase:     Running
    Pod Ready:     True
    Server Name:   managed-server1
    State:         RUNNING
    State Goal:    RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2023-04-28T20:33:44.225000Z
      Overall Health:   ok
      Subsystems:
    Node Name:     phx32822d1
    Pod Phase:     Running
    Pod Ready:     True
    Server Name:   managed-server2
    State:         RUNNING
    State Goal:    RUNNING
    Cluster Name:  cluster-1
    Server Name:   managed-server3
    State:         SHUTDOWN
    State Goal:    SHUTDOWN
    Cluster Name:  cluster-1
    Server Name:   managed-server4
    State:         SHUTDOWN
    State Goal:    SHUTDOWN
    Cluster Name:  cluster-1
    Server Name:   managed-server5
    State:         SHUTDOWN
    State Goal:    SHUTDOWN
  Start Time:      2023-04-28T20:32:02.339082Z
Events:
  Type     Reason                      Age    From               Message
  ----     ------                      ----   ----               -------
  Warning  Failed                      4m7s   weblogic.operator  Domain sample-domain1 failed due to 'Persistent volume claim unbound': PersistentVolumeClaim 'sample-domain1-weblogic-sample-pvc' is not bound; the status phase is 'Pending'.. Operator is waiting for the persistent volume claim to be bound, it may be a temporary condition. If this condition persists, then ensure that the PVC has a correct volume name or storage class name and is in bound status..
  Normal   PersistentVolumeClaimBound  3m57s  weblogic.operator  The persistent volume claim is bound and ready.
  Normal   Available                   2m16s  weblogic.operator  Domain sample-domain1 is available: a sufficient number of its servers have reached the ready state.
  Normal   Completed                   2m11s  weblogic.operator  Domain sample-domain1 is complete because all of the following are true: there is no failure detected, there are no pending server shutdowns, and all servers expected to be running are ready and at their target image, auxiliary images, restart version, and introspect version.
```

In the `Status` section of the output, the available servers and clusters are listed.  Note that if this command is issued very soon after the script finishes, there may be no servers available yet, or perhaps only the Administration Server but no Managed Servers.  The operator will start up the Administration Server first and wait for it to become ready before starting the Managed Servers.

#### Verify the pods

If you run kubectl get pods -n sample-domain1-ns --watch, then you will see the introspector job run and your WebLogic Server pods start. The output will look something like this:
  {{%expand "Click here to expand." %}}
  ```shell
  $ kubectl get pods -n sample-domain1-ns --watch
  NAME                                         READY   STATUS    RESTARTS   AGE
  sample-domain1-introspector-lqqj9   0/1   Pending   0     0s
  sample-domain1-introspector-lqqj9   0/1   ContainerCreating   0     0s
  sample-domain1-introspector-lqqj9   1/1   Running   0     1s
  sample-domain1-introspector-lqqj9   0/1   Completed   0     65s
  sample-domain1-introspector-lqqj9   0/1   Terminating   0     65s
  sample-domain1-admin-server   0/1   Pending   0     0s
  sample-domain1-admin-server   0/1   ContainerCreating   0     0s
  sample-domain1-admin-server   0/1   Running   0     1s
  sample-domain1-admin-server   1/1   Running   0     32s
  sample-domain1-managed-server1   0/1   Pending   0     0s
  sample-domain1-managed-server2   0/1   Pending   0     0s
  sample-domain1-managed-server1   0/1   ContainerCreating   0     0s
  sample-domain1-managed-server2   0/1   ContainerCreating   0     0s
  sample-domain1-managed-server1   0/1   Running   0     2s
  sample-domain1-managed-server2   0/1   Running   0     2s
  sample-domain1-managed-server1   1/1   Running   0     43s
  sample-domain1-managed-server2   1/1   Running   0     42s
  ```
  {{% /expand %}}


#### Verify the services

Use the following command to see the services for the domain:

```shell
$ kubectl get services -n NAMESPACE
```

Here is an example of the output of this command:
```shell
$ kubectl get services -n sample-domain1-ns
```
```
NAME                               TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)    AGE
sample-domain1-admin-server        ClusterIP   None             <none>        7001/TCP   10m
sample-domain1-cluster-cluster-1   ClusterIP   10.107.178.255   <none>        8001/TCP   9m49s
sample-domain1-managed-server1     ClusterIP   None             <none>        8001/TCP   9m49s
sample-domain1-managed-server2     ClusterIP   None             <none>        8001/TCP   9m43s
```

#### Delete the generated domain home
Sometimes in production, but most likely in testing environments, you might want to remove the domain home that is generated using this sample.
You can use the `domain-on-pv-helper.sh` helper script for this.
The script launches a a Kubernetes pod named as 'pvhelper' using the provided persistent volume claim name and the mount path.
You can run the '${KUBERNETES_CLI} exec' to get a shell to the running pod container and run commands to examine or clean up the 
contents of shared directories on persistent volume.
For example:
```shell
$ cd /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/domain-lifecycle
$ ./domain-on-pv-helper.sh -n sample-domain1-ns -c sample-domain1-weblogic-sample-pvc -m /shared
```
{{%expand "Click here to expand." %}}
```
$ ./domain-on-pv-helper.sh -n sample-domain1-ns -c sample-domain1-weblogic-sample-pvc -m /shared
[2023-04-28T21:02:42.851294126Z][INFO] Creating pod 'pvhelper' using image 'ghcr.io/oracle/oraclelinux:8-slim', persistent volume claim 'sample-domain1-weblogic-sample-pvc' and mount path '/shared'.
pod/pvhelper created
[pvhelper] already initialized ..
Checking Pod READY column for State [1/1]
Pod [pvhelper] Status is NotReady Iter [1/120]
Pod [pvhelper] Status is Ready Iter [2/120]
NAME       READY   STATUS    RESTARTS   AGE
pvhelper   1/1     Running   0          10s
[2023-04-28T21:02:58.972826241Z][INFO] Executing 'kubectl -n sample-domain1-ns exec -i pvhelper -- ls -l /shared' command to print the contents of the mount path in the persistent volume.
[2023-04-28T21:02:59.115119523Z][INFO] =============== Command output ====================
total 0
drwxr-xr-x 4 1000 root 43 Apr 26 19:41 domains
drwxr-xr-x 4 1000 root 43 Apr 26 19:41 logs
[2023-04-28T21:02:59.118355423Z][INFO] ===================================================
[2023-04-28T21:02:59.121482356Z][INFO] Use command 'kubectl -n sample-domain1-ns exec -it pvhelper -- /bin/sh' and cd to '/shared' directory to view or delete the contents on the persistent volume.
[2023-04-28T21:02:59.124658180Z][INFO] Use command 'kubectl -n sample-domain1-ns delete pod pvhelper' to delete the pod created by the script.
```
```
$ kubectl -n sample-domain1-ns exec -it pvhelper -- /bin/sh
sh-4.4$ cd /shared/domains
sh-4.4$ ls
sample-domain1
```
{{% /expand %}}

#### Troubleshooting

**Message**: `status on iteration 20 of 20
pod domain1-create-weblogic-sample-domain-job-4qwt2 status is Pending
The create domain job is not showing status completed after waiting 300 seconds.`  
The most likely cause is related to the value of `persistentVolumeClaimName`, defined in `domain-home-on-pv/create-domain-inputs.yaml`.  
To determine if this is the problem:

    * Execute `kubectl get all --all-namespaces` to find the name of the `create-weblogic-sample-domain-job`.
    * Execute  `kubectl describe pod <name-of-create-weblogic-sample-domain-job>` to see if there is an event that has text similar to `persistentvolumeclaim "domain1-weblogic-sample-pvc" not found`.
    * Find the name of the PVC that was created by executing [create-pv-pvc.sh](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc.sh), using `kubectl describe pvc`. It is likely to be `weblogic-sample-pvc`.
    * Change the value of `persistentVolumeClaimName` to match the name created when you executed [create-pv-pvc.sh](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc.sh).
    * Rerun the `create-domain.sh` script with the same arguments as you did before.
    * Verify that the operator is deployed. Use the command:
```shell
$ kubectl  get all --all-namespaces
```
Look for lines similar to:
```
weblogic-operator1   pod/weblogic-operator-
```
   If you do not find something similar in the output, the WebLogic Kubernetes Operator might not have been installed completely. Review the operator [installation instructions]({{< relref "/managing-operators/installation.md" >}}).


**Message**: `ERROR: Unable to create folder /shared/domains`  
The most common cause is a poor choice of value for `weblogicDomainStoragePath` in the input file used when you executed:
```shell
$ create-pv-pvc.sh
```
   You should [delete the resources for your sample domain]({{< relref "/samples/domains/delete-domain/_index.md" >}}), correct the value in that file, and rerun the commands to create the PV/PVC and the credential before you attempt to rerun:
```shell
$ create-domain.sh
```
   A correct value for `weblogicDomainStoragePath` will meet the following requirements:

  * Must be the name of a directory.
  * The directory must be world writable.  

Optionally, follow these steps to tighten permissions on the named directory after you run the sample the first time:

  * Become the root user.
  * `ls -nd $value-of-weblogicDomainStoragePath`
    * Note the values of the third and fourth field of the output.
  * `chown $third-field:$fourth-field $value-of-weblogicDomainStoragePath`
  * `chmod 755 $value-of-weblogicDomainStoragePath`
  * Return to your normal user ID.


**Message**: `ERROR: The create domain job will not overwrite an existing domain. The domain folder /shared/domains/domain1 already exists`  
You will see this message if the directory `domains/domain1` exists in the directory named as the value of `weblogicDomainStoragePath` in `create-pv-pvc-inputs.yaml`. For example, if the value of  `weblogicDomainStoragePath` is `/tmp/wls-op-4-k8s`, you would need to remove (or move) `/tmp/wls-op-4-k8s/domains/domain1`.

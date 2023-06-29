---
title: "Build domain creation image"
date: 2019-02-23T17:32:31-05:00
weight: 2
description: "Create WebLogic images using the WebLogic Image Tool and WebLogic Deploy Tooling."
---

{{< table_of_contents >}}

**Before you begin**: Complete the steps in [Prerequisites]({{< relref "/samples/domains/domain-home-on-pv/prerequisites.md" >}}).

### Overview

The image build process uses the WebLogic Image Tool to create a Domain on PV `domain creation image`.  This image contains:
- The directory where the WebLogic Deploy Tooling software is installed (also known as WDT Home), expected in an image's `/auxiliary/weblogic-deploy` directory, by default.
- WDT model YAML (model), WDT variable (property), and WDT archive ZIP (archive) files, expected in directory `/auxiliary/models`, by default.

### Build the domain creation image

Use the steps in the following sections to build the domain creation image.

#### Understand your first archive

The sample includes a predefined archive directory in `/tmp/sample/wdt-artifacts/archives/archive-v1` that you will use to create an archive ZIP file for the image.

The archive top directory, named `wlsdeploy`, contains a directory named `applications`, which includes an 'exploded' sample JSP web application in the directory, `myapp-v1`. Three useful aspects to remember about WDT archives are:
  - A domain creation image can contain multiple WDT archives.
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
    out.println("Hello World! This is version 'v1' of the mii-sample JSP web-app.");
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

#### Stage the archive ZIP file

When you create the image, you will use the files in the staging directory, `/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1`. In preparation, you need it to contain a WDT application archive ZIP file.

Run the following commands to create your application archive ZIP file and put it in the expected directory:

```
# Delete existing archive.zip in case we have an old leftover version
```
```shell
$ rm -f /tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1/archive.zip
```
```
# Move to the directory which contains the source files for our archive
```
```shell
$ cd /tmp/sample/wdt-artifacts/archives/archive-v1
```

Using the [WDT archive helper tool](https://oracle.github.io/weblogic-deploy-tooling/userguide/tools/archive_helper/), create the archive in the location that we will use later when we run the WebLogic Image Tool.

```shell
$ /tmp/sample/wdt-artifacts/weblogic-deploy/bin/archiveHelper.sh add application -archive_file=/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1/archive.zip -source=wlsdeploy/applications/myapp-v1
```

#### Stage the model files

In this step, you explore the staged WDT model YAML file and properties in the `/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1` directory. The model in this directory references the web application in your archive, configures a WebLogic Administration Server, and configures a WebLogic cluster. It consists of two files only, `model.10.properties`, a file with a single property, and, `model.10.yaml`, a model YAML file with your WebLogic configuration `model.10.yaml`.

```
CLUSTER_SIZE=5
```

Here is the WLS `model.10.yaml`:

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

{{%expand "Click here to view the JRF `model.10.yaml`, and note the `RCUDbInfo` stanza and its references to a `DOMAIN_UID-rcu-access` secret." %}}

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

- Use macros to inject external values:
  - The property file `CLUSTER_SIZE` property is referenced in the model YAML file `DynamicClusterSize` and `MaxDynamicClusterSize` fields using a PROP macro.
  - The model file domain name is injected using a custom environment variable named `CUSTOM_DOMAIN_NAME` using an ENV macro.
    - You set this environment variable later in this sample using an `env` field in its Domain.
    - _This conveniently provides a simple way to deploy multiple differently named domains using the same domain creation image._
  - The model file administrator user name and password are set using a `weblogic-credentials` secret macro reference to the WebLogic credential secret.
    - This secret is in turn referenced using the `webLogicCredentialsSecret` field in the Domain.
    - The `weblogic-credentials` is a reserved name that always dereferences to the owning Domain actual WebLogic credentials secret name.

An image can contain multiple properties files, archive ZIP files, and model YAML files but in this sample you use just one of each. For a complete description of WDT model file naming conventions, file loading order, and macro syntax, see [Model files]({{< relref "/managing-domains/domain-on-pv/model-files.md" >}}) in the user documentation.

#### Create the image with WIT

**NOTE**: If you are using JRF in this sample, substitute `JRF` for each occurrence of `WLS` in the following `imagetool` command line.

At this point, you have all of the files needed for image `wdt-domain-image:WLS-v1` staged; they include:

  - `/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1/model.10.yaml`
  - `/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1/model.10.properties`
  - `/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1/archive.zip`

Now, you use the Image Tool to create an image named `wdt-domain-image:WLS-v1`. You've already set up this tool during the prerequisite steps.

Run the following commands to create the image and verify that it worked:

  ```shell
  $ cd /tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1
  ```
  ```shell
  $ /tmp/sample/wdt-artifacts/imagetool/bin/imagetool.sh createAuxImage \
    --tag wdt-domain-image:WLS-v1 \
    --wdtModel ./model.10.yaml \
    --wdtVariables ./model.10.properties \
    --wdtArchive ./archive.zip
  ```

If you don't see the `imagetool` directory, then you missed a step in the [prerequisites]({{< relref "/samples/domains/domain-home-on-pv/prerequisites.md" >}}).

This command runs the WebLogic Image Tool to create the domain creation image and does the following:

  - Builds the final container image as a layer on a small `busybox` base image.
  - Copies the WDT ZIP file that's referenced in the WIT cache into the image.
    - Note that you cached WDT in WIT using the keyword `latest` when you set up the cache during the sample prerequisites steps.
    - This lets WIT implicitly assume it's the desired WDT version and removes the need to pass a `-wdtVersion` flag.
  - Copies the specified WDT model, properties, and application archives to image location `/auxiliary/models`.

When the command succeeds, it should end with output like the following:
```
[INFO   ] Build successful. Build time=36s. Image tag=wdt-domain-image:WLS-v1
```

Also, if you run the `docker images` command, then you will see an image named `wdt-domain-image:WLS-v1`.

After the image is created, it should have the WDT executables in
`/auxiliary/weblogic-deploy`, and WDT model, property, and archive
files in `/auxiliary/models`. You can run `ls` in the Docker
image to verify this:

```shell
$ docker run -it --rm wdt-domain-image:WLS-v1 ls -l /auxiliary
  total 8
  drwxr-xr-x    1 oracle   root          4096 Jun  1 21:53 models
  drwxr-xr-x    1 oracle   root          4096 May 26 22:29 weblogic-deploy

$ docker run -it --rm wdt-domain-image:WLS-v1 ls -l /auxiliary/models
  total 16
  -rw-rw-r--    1 oracle   root          5112 Jun  1 21:52 archive.zip
  -rw-rw-r--    1 oracle   root           173 Jun  1 21:59 model.10.properties
  -rw-rw-r--    1 oracle   root          1515 Jun  1 21:59 model.10.yaml

$ docker run -it --rm wdt-domain-image:WLS-v1 ls -l /auxiliary/weblogic-deploy
  total 28
  -rw-r-----    1 oracle   root          4673 Oct 22  2019 LICENSE.txt
  -rw-r-----    1 oracle   root            30 May 25 11:40 VERSION.txt
  drwxr-x---    1 oracle   root          4096 May 26 22:29 bin
  drwxr-x---    1 oracle   root          4096 May 25 11:40 etc
  drwxr-x---    1 oracle   root          4096 May 25 11:40 lib
  drwxr-x---    1 oracle   root          4096 Jan 22  2019 samples
```

**NOTE**: If you have Kubernetes cluster worker nodes that are remote to your local machine, then you need to put the image in a location that these nodes can access. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/domains/domain-home-on-pv#ensuring-your-kubernetes-cluster-can-access-images" >}}).

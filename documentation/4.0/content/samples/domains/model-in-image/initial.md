---
title: "Initial use case"
date: 2019-02-23T17:32:31-05:00
weight: 2
---

### Contents

 - [Overview](#overview)
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

In this use case, you set up an initial WebLogic domain. This involves:

  - Creating an auxiliary image with:
    - A WDT archive ZIP file that contains your applications.
    - A WDT model that describes your WebLogic configuration.
    - A WDT installation that contains the binaries for running WDT.
  - Creating secrets for the domain.
  - Creating a Domain YAML file for the domain that references your Secrets, auxiliary image, and a WebLogic image.

After the Domain is deployed, the operator starts an 'introspector job' that converts your models into a WebLogic configuration, and then passes this configuration to each WebLogic Server in the domain.

{{% notice note %}}
Perform the steps in [Prerequisites for all domain types]({{< relref "/samples/domains/model-in-image/prerequisites.md" >}}) before performing the steps in this use case.  
If you are taking the `JRF` path through the sample, then substitute `JRF` for `WLS` in your image names and directory paths. Also note that the JRF-AI-v1 model YAML file differs from the WLS-AI-v1 YAML file (it contains an additional `domainInfo -> RCUDbInfo` stanza).
{{% /notice %}}

#### Image creation - Introduction

The goal of the initial use case 'image creation' step is to demonstrate using the WebLogic Image Tool to create an auxiliary image named `model-in-image:WLS-AI-v1` from files that you will stage to `/tmp/mii-sample/model-images/model-in-image__WLS-AI-v1/`. The staged files will contain a web application in a WDT archive, and WDT model configuration for a WebLogic Server Administration Server called `admin-server` and a WebLogic cluster called `cluster-1`.

A Model in Image domain typically supplies one or more auxiliary images with:
- A WebLogic Deploy Tooling installation (expected in an image's `/auxiliary/weblogic-deploy` directory by default).
- WDT model YAML, property, and archive files (expected in directory `/auxiliary/models` by default).

If you do not specify a WDT model YAML file in an auxiliary image,
then the model YAML file alternately can be supplied dynamically using a Kubernetes ConfigMap
that is referenced by your Domain `spec.model.configMap` field.
We provide an example of using a model ConfigMap later in this sample.

Here are the steps for creating the image `model-in-image:WLS-AI-v1`:

- [Understanding your first archive](#understanding-your-first-archive)
- [Staging a ZIP file of the archive](#staging-a-zip-file-of-the-archive)
- [Staging model files](#staging-model-files)
- [Creating the image with WIT](#creating-the-image-with-wit)

#### Understanding your first archive

The sample includes a predefined archive directory in `/tmp/mii-sample/archives/archive-v1` that you will use to create an archive ZIP file for the image.

The archive top directory, named `wlsdeploy`, contains a directory named `applications`, which includes an 'exploded' sample JSP web application in the directory, `myapp-v1`. Three useful aspects to remember about WDT archives are:
  - A model image can contain multiple WDT archives.
  - WDT archives can contain multiple applications, libraries, and other components.
  - WDT archives have a [well defined directory structure](https://oracle.github.io/weblogic-deploy-tooling/concepts/archive/), which always has `wlsdeploy` as the top directory.

{{%expand "If you are interested in the web application source, click here to see the JSP code." %}}

```java
<%-- Copyright (c) 2019, 2021, Oracle and/or its affiliates. --%>
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

The application displays important details about the WebLogic Server instance that it's running on: namely its domain name, cluster name, and server name, as well as the names of any data sources that are targeted to the server. Also, you can see that application output reports that it's at version `v1`; you will update this to `v2` in a later use case that demonstrates upgrading the application.

#### Staging a ZIP file of the archive

When you create the image, you will use the files in the staging directory, `/tmp/mii-sample/model-images/model-in-image__WLS-AI-v1`. In preparation, you need it to contain a ZIP file of the WDT application archive.

Run the following commands to create your application archive ZIP file and put it in the expected directory:

```
# Delete existing archive.zip in case we have an old leftover version
```
```shell
$ rm -f /tmp/mii-sample/model-images/model-in-image__WLS-AI-v1/archive.zip
```
```
# Move to the directory which contains the source files for our archive
```
```shell
$ cd /tmp/mii-sample/archives/archive-v1
```
```
# Zip the archive to the location will later use when we run the WebLogic Image Tool
```
```shell
$ zip -r /tmp/mii-sample/model-images/model-in-image__WLS-AI-v1/archive.zip wlsdeploy
```

#### Staging model files

In this step, you explore the staged WDT model YAML file and properties in the `/tmp/mii-sample/model-images/model-in-image__WLS-AI-v1` directory. The model in this directory references the web application in your archive, configures a WebLogic Server Administration Server, and configures a WebLogic cluster. It consists of two files only, `model.10.properties`, a file with a single property, and, `model.10.yaml`, a YAML file with your WebLogic configuration `model.10.yaml`.

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

- Leverage macros to inject external values:
  - The property file `CLUSTER_SIZE` property is referenced in the model YAML file `DynamicClusterSize` and `MaxDynamicClusterSize` fields using a PROP macro.
  - The model file domain name is injected using a custom environment variable named `CUSTOM_DOMAIN_NAME` using an ENV macro.
    - You set this environment variable later in this sample using an `env` field in its Domain.
    - _This conveniently provides a simple way to deploy multiple differently named domains using the same model image._
  - The model file administrator user name and password are set using a `weblogic-credentials` secret macro reference to the WebLogic credential secret.
    - This secret is in turn referenced using the `webLogicCredentialsSecret` field in the Domain.
    - The `weblogic-credentials` is a reserved name that always dereferences to the owning Domain actual WebLogic credentials secret name.

A Model in Image image can contain multiple properties files, archive ZIP files, and YAML files but in this sample you use just one of each. For a complete description of Model in Images model file naming conventions, file loading order, and macro syntax, see [Model files]({{< relref "/managing-domains/model-in-image/model-files.md" >}}) in the Model in Image user documentation.

#### Creating the image with WIT

**Note**: If you are using JRF in this sample, substitute `JRF` for each occurrence of `WLS` in the following `imagetool` command line.

At this point, you have staged all of the files needed for image `model-in-image:WLS-AI-v1`; they include:

  - `/tmp/mii-sample/model-images/weblogic-deploy.zip`
  - `/tmp/mii-sample/model-images/model-in-image__WLS-AI-v1/model.10.yaml`
  - `/tmp/mii-sample/model-images/model-in-image__WLS-AI-v1/model.10.properties`
  - `/tmp/mii-sample/model-images/model-in-image__WLS-AI-v1/archive.zip`

If you don't see the `weblogic-deploy.zip` file, then you missed a step in the [prerequisites]({{< relref "/samples/domains/model-in-image/prerequisites.md" >}}).

Now, you use the Image Tool to create an auxiliary image named `model-in-image:WLS-AI-v1`. You've already set up this tool during the prerequisite steps.

Run the following commands to create the image and verify that it worked:

  ```shell
  $ cd /tmp/mii-sample/model-images/model-in-image__WLS-AI-v1
  ```
  ```shell
  $ /tmp/mii-sample/model-images/imagetool/bin/imagetool.sh createAuxImage \
    --tag model-in-image:WLS-AI-v1 \
    --wdtModel ./model.10.yaml \
    --wdtVariables ./model.10.properties \
    --wdtArchive ./archive.zip
  ```

If you don't see the `imagetool` directory, then you missed a step in the [prerequisites]({{< relref "/samples/domains/model-in-image/prerequisites.md" >}}).

This command runs the WebLogic Image Tool in its Model in Image mode, and does the following:

  - Builds the final container image as a layer on a small `busybox` base image.
  - Copies the WDT ZIP file that's referenced in the WIT cache into the image.
    - Note that you cached WDT in WIT using the keyword `latest` when you set up the cache during the sample prerequisites steps.
    - This lets WIT implicitly assume it's the desired WDT version and removes the need to pass a `-wdtVersion` flag.
  - Copies the specified WDT model, properties, and application archives to image location `/u01/wdt/models`.

When the command succeeds, it should end with output like the following:

```
[INFO   ] Build successful. Build time=36s. Image tag=model-in-image:WLS-AI-v1
```

Also, if you run the `docker images` command, then you will see an image named `model-in-image:WLS-AI-v1`.

After the image is created, it should have the WDT executables in
`/auxiliary/weblogic-deploy`, and WDT model, property, and archive
files in `/auxiliary/models`. You can run `ls` in the Docker
image to verify this:

```shell
$ docker run -it --rm model-in-image:WLS-AI-v1 ls -l /auxiliary
  total 8
  drwxr-xr-x    1 oracle   root          4096 Jun  1 21:53 models
  drwxr-xr-x    1 oracle   root          4096 May 26 22:29 weblogic-deploy

$ docker run -it --rm model-in-image:WLS-AI-v1 ls -l /auxiliary/models
  total 16
  -rw-rw-r--    1 oracle   root          5112 Jun  1 21:52 archive.zip
  -rw-rw-r--    1 oracle   root           173 Jun  1 21:59 model.10.properties
  -rw-rw-r--    1 oracle   root          1515 Jun  1 21:59 model.10.yaml

$ docker run -it --rm model-in-image:WLS-AI-v1 ls -l /auxiliary/weblogic-deploy
  total 28
  -rw-r-----    1 oracle   root          4673 Oct 22  2019 LICENSE.txt
  -rw-r-----    1 oracle   root            30 May 25 11:40 VERSION.txt
  drwxr-x---    1 oracle   root          4096 May 26 22:29 bin
  drwxr-x---    1 oracle   root          4096 May 25 11:40 etc
  drwxr-x---    1 oracle   root          4096 May 25 11:40 lib
  drwxr-x---    1 oracle   root          4096 Jan 22  2019 samples
```

**NOTE**: If you have Kubernetes cluster worker nodes that are remote to your local machine, then you need to put the image in a location that these nodes can access. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

#### Deploy resources - Introduction

In this section, you will deploy the new image to namespace `sample-domain1-ns`, including the following steps:

  - Create a Secret containing your WebLogic administrator user name and password.
  - Create a Secret containing your Model in Image runtime encryption password:
    - All Model in Image domains must supply a runtime encryption Secret with a `password` value.
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
    - This is a special secret required by Model in Image.
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

  If you're following the `JRF` path through the sample, then you also need to deploy the additional secret referenced by macros in the `JRF` model `RCUDbInfo` clause, plus an `OPSS` wallet password secret. For details about the uses of these secrets, see the [Model in Image]({{< relref "/managing-domains/model-in-image/_index.md" >}}) user documentation.

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

Now, you create a Domain YAML file. A Domain is the key resource that tells the operator how to deploy a WebLogic domain.

Copy the following to a file called `/tmp/mii-sample/mii-initial.yaml` or similar, or use the file `/tmp/mii-sample/domain-resources/WLS-AI/mii-initial-d1-WLS-AI-v1.yaml` that is included in the sample source.

{{%expand "Click here to view the WLS Domain YAML file." %}}
```yaml
# Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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
  # Set to 'FromModel' to indicate 'Model in Image'.
  domainHomeSourceType: FromModel

  # The WebLogic Domain Home, this must be a location within
  # the image for 'Model in Image' domains.
  domainHome: /u01/domains/sample-domain1

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

  configuration:

    # Settings for domainHomeSourceType 'FromModel'
    model:
      # Valid model domain types are 'WLS', 'JRF', and 'RestrictedJRF', default is 'WLS'
      domainType: WLS

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
      - image: "model-in-image:WLS-AI-v1"
        #imagePullPolicy: IfNotPresent
        #sourceWDTInstallHome: /auxiliary/weblogic-deploy
        #sourceModelHome: /auxiliary/models

      # Optional configmap for additional models and variable files
      #configMap: sample-domain1-wdt-config-map

      # All 'FromModel' domains require a runtimeEncryptionSecret with a 'password' field
      runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret

    # Secrets that are referenced by model yaml macros
    # (the model yaml in the optional configMap or in the image)
    #secrets:
    #- sample-domain1-datasource-secret

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
# Copyright (c) 2020, 2022, Oracle and/or its affiliates.
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
  # Set to 'FromModel' to indicate 'Model in Image'.
  domainHomeSourceType: FromModel

  # The WebLogic Domain Home, this must be a location within
  # the image for 'Model in Image' domains.
  domainHome: /u01/domains/sample-domain1

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

  configuration:

    # Settings for domainHomeSourceType 'FromModel'
    model:
      # Valid model domain types are 'WLS', 'JRF', and 'RestrictedJRF', default is 'WLS'
      domainType: JRF

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
      - image: "model-in-image:JRF-AI-v1"
        #imagePullPolicy: IfNotPresent
        #sourceWDTInstallHome: /auxiliary/weblogic-deploy
        #sourceModelHome: /auxiliary/models

      # Optional configmap for additional models and variable files
      #configMap: sample-domain1-wdt-config-map

      # All 'FromModel' domains require a runtimeEncryptionSecret with a 'password' field
      runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret

    # Secrets that are referenced by model yaml macros
    # (the model yaml in the optional configMap or in the image)
    secrets:
    #- sample-domain1-datasource-secret
    - sample-domain1-rcu-access

    # Increase the introspector job active timeout value for JRF use cases
    introspectorJobActiveDeadlineSeconds: 600

    opss:

      # Name of secret with walletPassword for extracting the wallet, used for JRF domains
      walletPasswordSecret: sample-domain1-opss-wallet-password-secret

      # Name of secret with walletFile containing base64 encoded opss wallet, used for JRF domains
      #walletFileSecret: sample-domain1-opss-walletfile-secret

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
  $ kubectl apply -f /tmp/mii-sample/domain-resources/WLS-AI/mii-initial-d1-WLS-AI-v1.yaml
  ```

  **NOTE**: If you are choosing _not_ to use the predefined Domain YAML file and instead created your own Domain YAML file earlier, then substitute your custom file name in the previous command. Previously, we suggested naming it `/tmp/mii-sample/mii-initial.yaml`.

  If you run `kubectl get pods -n sample-domain1-ns --watch`, then you will see the introspector job run and your WebLogic Server pods start. The output will look something like this:

  {{%expand "Click here to expand." %}}
  ```shell
  $ kubectl get pods -n sample-domain1-ns --watch
    ```
    ```
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

For a more detailed view of this activity,
you can use the `waitForDomain.sh` sample lifecycle script.
This script provides useful information about a domain's pods and
optionally waits for its `Completed` status condition to become `True`.
A `Completed` domain indicates that all of its expected
pods have reached a `ready` state
plus their target `restartVersion`, `introspectVersion`, and `image`.
For example:
```shell
$ cd /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/domain-lifecycle
$ ./waitForDomain.sh -n sample-domain1-ns -d sample-domain1 -p Completed
```

If you see an error, then consult [Debugging]({{< relref "/managing-domains/debugging.md" >}}).

#### Invoke the web application

Now that all the initial use case resources have been deployed, you can invoke the sample web application through the Traefik ingress controller's NodePort.

**Note**: The web application will display a list of any data sources it finds, but at this point, we don't expect it to find any because the model doesn't contain any.

Send a web application request to the load balancer:

   ```shell
   $ curl -s -S -m 10 -H 'host: sample-domain1-cluster-cluster-1.mii-sample.org' \
      http://localhost:30305/myapp_war/index.jsp
   ```
Or, if Traefik is unavailable and your Administration Server pod is running, you can use `kubectl exec`:

   ```shell
   $ kubectl exec -n sample-domain1-ns sample-domain1-admin-server -- bash -c \
     "curl -s -S -m 10 http://sample-domain1-cluster-cluster-1:8001/myapp_war/index.jsp"
   ```

You will see output like the following:

   ```html
   <html><body><pre>
   *****************************************************************

   Hello World! This is version 'v1' of the mii-sample JSP web-app.

   Welcome to WebLogic Server 'managed-server2'!

     domain UID  = 'sample-domain1'
     domain name = 'domain1'

   Found 1 local cluster runtime:
     Cluster 'cluster-1'

   Found min threads constraint runtime named 'SampleMinThreads' with configured count: 1

   Found max threads constraint runtime named 'SampleMaxThreads' with configured count: 10

   Found 0 local data sources:

   *****************************************************************
   </pre></body></html>
   ```

 **Note**: If you're running your `curl` commands on a remote machine, then substitute `localhost` with an external address suitable for contacting your Kubernetes cluster. A Kubernetes cluster address that often works can be obtained by using the address just after `https://` in the KubeDNS line of the output from the `kubectl cluster-info` command.

 If you want to continue to the [Update 1]({{< relref "/samples/domains/model-in-image/update1.md" >}}) use case, then leave your domain running.

 To remove the resources you have created in this sample, see [Cleanup]({{< relref "/samples/domains/model-in-image/cleanup.md" >}}).

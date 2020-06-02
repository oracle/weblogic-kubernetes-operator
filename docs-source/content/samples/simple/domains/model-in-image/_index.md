---
title: "Model in image"
date: 2019-02-23T17:32:31-05:00
weight: 4
description: "Sample for supplying a WebLogic Deploy Tooling (WDT) model that the operator expands into a full domain home during runtime."
---

### Contents

   - [Introduction](#introduction)
     - [Model in Image domain types (WLS, JRF, and Restricted JRF)](#model-in-image-domain-types-wls-jrf-and-restricted-jrf)
     - [Use cases](#use-cases)
     - [Sample directory structure](#sample-directory-structure)
   - [Prerequisites for all domain types](#prerequisites-for-all-domain-types)
   - [Additional prerequisites for JRF domains](#additional-prerequisites-for-jrf-domains)
   - [Initial](#initial-use-case) use case: An initial WebLogic domain
     - [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}): Dynamically adding a data source using a model ConfigMap
     - [Update 2]({{< relref "/samples/simple/domains/model-in-image/update2.md" >}}): Deploying an additional domain
     - [Update 3]({{< relref "/samples/simple/domains/model-in-image/update3.md" >}}): Updating an application in an image
   - [Cleanup]({{< relref "/samples/simple/domains/model-in-image/cleanup.md" >}})
   - [References]({{< relref "/samples/simple/domains/model-in-image/cleanup#references" >}})


### Introduction


This sample demonstrates deploying a Model in Image [domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}). Unlike Domain in PV and Domain in Image, Model in Image eliminates the need to pre-create your WebLogic domain home prior to deploying your domain resource. Instead, Model in Image uses a WebLogic Deploy Tooling (WDT) model to specify your WebLogic configuration.

WDT models are a convenient and simple alternative to WebLogic WLST configuration scripts and templates. They compactly define a WebLogic domain using YAML files and support including application archives in a ZIP file. The WDT model format is described in the open source, [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling) GitHub project, and the required directory structure for a WDT archive is specifically discussed [here](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/archive.md).

For more information on Model in Image, see the [Model in Image user guide]({{< relref "/userguide/managing-domains/model-in-image/_index.md" >}}). For a comparison of Model in Image to other domain home source types, see [Choose a domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

#### Model in Image domain types (WLS, JRF, and Restricted JRF)

There are three types of domains supported by Model in Image: a standard `WLS` domain, an Oracle Fusion Middleware Infrastructure Java Required Files (`JRF`) domain, and a `RestrictedJRF` domain. This sample demonstrates the `WLS` and `JRF` types.

The `JRF` domain path through the sample includes additional steps required for JRF: deploying an infrastructure database, initializing the database using the Repository Creation Utility (RCU) tool, referencing the infrastructure database from the WebLogic configuration, setting an Oracle Platform Security Services (OPSS) wallet password, and exporting/importing an OPSS wallet file. `JRF` domains may be used by Oracle products that layer on top of WebLogic Server, such as SOA and OSB. Similarly, `RestrictedJRF` domains may be used by Oracle layered products, such as Oracle Communications products.

#### Use cases

This sample demonstrates four Model in Image use cases:

- [Initial](#initial-use-case): An initial WebLogic domain with the following characteristics:

   - Image `model-in-image:WLS-v1` with:
     - A WebLogic installation
     - A WebLogic Deploy Tooling (WDT) installation
     - A WDT archive with version `v1` of an exploded Java EE web application
     - A WDT model with:
       - A WebLogic Administration Server
       - A WebLogic cluster
       - A reference to the web application
   - Kubernetes Secrets:
     - WebLogic credentials
     - Required WDT runtime password
   - A domain resource with:
     - `metadata.name` and `weblogic.domainUID` label set to `sample-domain1`
     - `spec.domainHomeSourceType: FromModel`
     - `spec.image: model-in-image:WLS-v1`
     - References to the secrets

- [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}): Demonstrates updating the initial domain by dynamically adding a data source using a model ConfigMap.

   - Image `model-in-image:WLS-v1`:
     - Same image as Initial use case
   - Kubernetes Secrets:
     - Same as Initial use case, plus secrets for data source credentials and URL
   - Kubernetes ConfigMap with:
     - A WDT model for a data source targeted to the cluster
   - A domain resource, same as Initial use case, plus:
     - `spec.model.configMap` referencing the ConfigMap
     - References to data source secrets

- [Update 2]({{< relref "/samples/simple/domains/model-in-image/update2.md" >}}): Demonstrates deploying a second domain (similar to the Update 1  domain).

  - Image `model-in-image:WLS-v1`:
    - Same image as the Initial and Update 1 use cases
  - Kubernetes Secrets and ConfigMap:
    - Similar to the Update 1 use case, except names and labels are decorated with a new domain UID
  - A domain resource, similar to Update 1 use case, except:
    - Its `metadata.name` and `weblogic.domainUid` label become `sample-domain2` instead of `sample-domain1`
    - Its secret/ConfigMap references are decorated with `sample-domain2` instead of `sample-domain1`
    - Has a changed `env` variable that sets a new domain name

- [Update 3]({{< relref "/samples/simple/domains/model-in-image/update3.md" >}}): Demonstrates deploying an updated image with an updated application to the Update 1  domain.

  - Image `model-in-image:WLS-v2`, similar to `model-in-image:WLS-v1` image with:
    - An updated web application `v2` at the `myapp-v2` directory path instead of `myapp-v1`
    - An updated model that points to the new web application path
  - Kubernetes Secrets and ConfigMap:
    - Same as the Update 1 use case
  - A domain resource:
    - Same as the Update 1 use case, except `spec.image` is `model-in-image:WLS-v2`

#### Sample directory structure

The sample contains the following files and directories:

Location | Description |
------------- | ----------- |
`domain-resources` | JRF and WLS domain resources. |
`archives` | Source code location for WebLogic Deploy Tooling application ZIP archives. |
`model-images` | Staging for each model image's WDT YAML, WDT properties, and WDT archive ZIP files. The directories in `model images` are named for their respective images. |
`model-configmaps` | Staging files for a model ConfigMap that configures a data source. |
`ingresses` | Ingress resources. |
`utils/wl-pod-wait.sh` | Utility for watching the pods in a domain reach their expected `restartVersion`, image name, and ready state. |
`utils/patch-restart-version.sh` | Utility for updating a running domain `spec.restartVersion` field (which causes it to 're-instrospect' and 'roll'). |
`utils/opss-wallet.sh` | Utility for exporting or importing a JRF domain OPSS wallet file. |

### Prerequisites for all domain types

1. Choose the type of domain you're going to use throughout the sample, `WLS` or `JRF`.

   - The first time you try this sample, we recommend that you choose `WLS` even if you're familiar with `JRF`.
   - This is because `WLS` is simpler and will more easily familiarize you with Model in Image concepts.
   - We recommend choosing `JRF` only if you are already familiar with `JRF`, you have already tried the `WLS` path through this sample, and you have a definite use case where you need to use `JRF`.

1. The `JAVA_HOME` environment variable must be set and must reference a valid JDK 8 or 11 installation.

1. Get the operator source and put it in `/tmp/operator-source`.

   For example:

   ```
   $ mkdir /tmp/operator-source
   $ cd /tmp/operator-source
   $ git clone https://github.com/oracle/weblogic-kubernetes-operator.git
   ```

   > **Note**: We will refer to the top directory of the operator source tree as `/tmp/operator-source`; however, you can use a different location.

   For additional information about obtaining the operator source, see the [Developer Guide Requirements](https://oracle.github.io/weblogic-kubernetes-operator/developerguide/requirements/).

1. Copy the sample to a new directory; for example, use directory `/tmp/mii-sample`.


   ```
   $ mkdir /tmp/mii-sample
   $ cp -r /tmp/operator-source/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/* /tmp/mii-sample
   ```

   > **Note**: We will refer to this working copy of the sample as `/tmp/mii-sample`; however, you can use a different location.
   {{< rawhtml >}}
   <a name="resume"></a>
   {{< /rawhtml >}}
1. Make sure an operator is set up to manage namespace `sample-domain1-ns`. Also, make sure a Traefik ingress controller is managing the same namespace and listening on port 30305.

   For example, follow the same steps as the [Quick Start](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/) guide up through the [Prepare for a domain]({{< relref "/quickstart/prepare.md" >}}) step.

   {{% notice info %}}
   Make sure you stop when you complete the "Prepare for a domain" step and then resume following these instructions.
   {{% /notice %}}

1. Set up ingresses that will redirect HTTP from Traefik port 30305 to the clusters in this sample's WebLogic domains.

    - Option 1: To create the ingresses, use the following YAML file to create a file called `/tmp/mii-sample/ingresses/myingresses.yaml` and then call `kubectl apply -f /tmp/mii-sample/ingresses/myingresses.yaml`:

       ```
       apiVersion: extensions/v1beta1
       kind: Ingress
       metadata:
         name: traefik-ingress-sample-domain1-admin-server
         namespace: sample-domain1-ns
         labels:
           weblogic.domainUID: sample-domain1
         annotations:
           kubernetes.io/ingress.class: traefik
       spec:
         rules:
         - host:
           http:
             paths:
             - path: /console
               backend:
                 serviceName: sample-domain1-admin-server
                 servicePort: 7001
       ---
       apiVersion: extensions/v1beta1
       kind: Ingress
       metadata:
         name: traefik-ingress-sample-domain1-cluster-cluster-1
         namespace: sample-domain1-ns
         labels:
           weblogic.domainUID: sample-domain1
         annotations:
           kubernetes.io/ingress.class: traefik
       spec:
         rules:
         - host: sample-domain1-cluster-cluster-1.mii-sample.org
           http:
             paths:
             - path:
               backend:
                 serviceName: sample-domain1-cluster-cluster-1
                 servicePort: 8001
       ---
       apiVersion: extensions/v1beta1
       kind: Ingress
       metadata:
         name: traefik-ingress-sample-domain1-cluster-cluster-2
         namespace: sample-domain1-ns
         labels:
           weblogic.domainUID: sample-domain1
         annotations:
           kubernetes.io/ingress.class: traefik
       spec:
         rules:
         - host: sample-domain1-cluster-cluster-2.mii-sample.org
           http:
             paths:
             - path:
               backend:
                 serviceName: sample-domain1-cluster-cluster-2
                 servicePort: 8001
       ---
       apiVersion: extensions/v1beta1
       kind: Ingress
       metadata:
         name: traefik-ingress-sample-domain2-cluster-cluster-1
         namespace: sample-domain1-ns
         labels:
           weblogic.domainUID: sample-domain2
         annotations:
           kubernetes.io/ingress.class: traefik
       spec:
         rules:
         - host: sample-domain2-cluster-cluster-1.mii-sample.org
           http:
             paths:
             - path:
               backend:
                 serviceName: sample-domain2-cluster-cluster-1
                 servicePort: 8001
       ```

   - Option 2: Run `kubectl apply -f` on each of the ingress YAML files that are already included in the sample source `/tmp/mii-sample/ingresses` directory:

       ```
       $ cd /tmp/mii-sample/ingresses
       $ kubectl apply -f traefik-ingress-sample-domain1-admin-server.yaml
       $ kubectl apply -f traefik-ingress-sample-domain1-cluster-cluster-1.yaml
       $ kubectl apply -f traefik-ingress-sample-domain1-cluster-cluster-2.yaml
       $ kubectl apply -f traefik-ingress-sample-domain2-cluster-cluster-1.yaml
       $ kubectl apply -f traefik-ingress-sample-domain2-cluster-cluster-2.yaml
       ```

   > **NOTE**: We give each cluster ingress a different host name that is decorated using both its operator domain UID and its cluster name. This makes each cluster uniquely addressable even when cluster names are the same across different clusters.  When using `curl` to access the WebLogic domain through the ingress, you will need to supply a host name header that matches the host names in the ingress.

   For more information on ingresses and load balancers, see [Ingress]({{< relref "/userguide/managing-domains/ingress/_index.md" >}}).

1. Obtain the WebLogic 12.2.1.4 image that is required to create the sample's model images.

   a. Use a browser to access [Oracle Container Registry](http://container-registry.oracle.com).

   b. Choose an image location: for `JRF` domains, select `Middleware`, then `fmw-infrastructure`; for `WLS` domains, select `Middleware`, then `weblogic`.

   c. Select Sign In and accept the license agreement.

   d. Use your terminal to log in to Docker locally: `docker login container-registry.oracle.com`.

   e. Later in this sample, when you run WebLogic Image Tool commands, the tool will use the image as a base image for creating model images. Specifically, the tool will implicitly call `docker pull` for one of the above licensed images as specified in the tool's command line using the `--fromImage` parameter. For `JRF`, this sample specifies `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4`, and for `WLS`, the sample specifies `container-registry.oracle.com/middleware/weblogic:12.2.1.4`.

     {{% notice info %}}
   If you prefer, you can create your own base image and then substitute this image name in the WebLogic Image Tool `--fromImage` parameter throughout this sample. See [Preparing a Base Image]({{< relref "/userguide/managing-domains/domain-in-image/base-images/_index.md" >}}).
     {{% /notice %}}

1. Download the latest WebLogic Deploying Tooling and WebLogic Image Tool installer ZIP files to your `/tmp/mii-sample/model-images` directory. Both WDT and WIT are required to create your Model in Image Docker images.

   For example, visit the GitHub [WebLogic Deploy Tooling Releases](https://github.com/oracle/weblogic-deploy-tooling/releases) and [WebLogic Image Tool Releases](https://github.com/oracle/weblogic-image-tool/releases) web pages to determine the latest release version for each, and then, assuming the version numbers are `1.8.1` and `1.8.5` respectively, call:

   ```
   $ cd /tmp/mii-sample/model-images

   $ curl -m 30 -fL https://github.com/oracle/weblogic-deploy-tooling/releases/download/weblogic-deploy-tooling-1.8.1/weblogic-deploy.zip \
     -o /tmp/mii-sample/model-images/weblogic-deploy.zip

   $ curl -m 30 -fL https://github.com/oracle/weblogic-image-tool/releases/download/release-1.8.5/imagetool.zip \
     -o /tmp/mii-sample/model-images/imagetool.zip
   ```

1. To set up the WebLogic Image Tool, run the following commands:

   ```
   $ cd /tmp/mii-sample/model-images

   $ unzip imagetool.zip

   $ ./imagetool/bin/imagetool.sh cache addInstaller \
     --type wdt \
     --version latest \
     --path /tmp/mii-sample/model-images/weblogic-deploy.zip
   ```

   These steps will install WIT to the `/tmp/mii-sample/model-images/imagetool` directory, plus put a `wdt_latest` entry in the tool's cache which points to the WDT ZIP installer. You will use WIT later in the sample for creating model images.


### Additional prerequisites for JRF domains

> __NOTE__: If you're using a `WLS` domain type, skip this section and continue [here](#initial-use-case).

#### JRF Prerequisites Contents

 - [Introduction to JRF setups](#introduction-to-jrf-setups)
 - [Set up and initialize an infrastructure database](#set-up-and-initialize-an-infrastructure-database)
 - [Increase introspection job timeout](#increase-introspection-job-timeout)
 - [Important considerations for RCU model attributes, domain resource attributes, and secrets](#important-considerations-for-rcu-model-attributes-domain-resource-attributes-and-secrets)

##### Introduction to JRF setups

> __NOTE__: The requirements in this section are in addition to [Prerequisites for all domain types](#prerequisites-for-all-domain-types).

A JRF domain requires an infrastructure database, initializing this database with RCU, and configuring your domain to access this database. You must perform all of these steps _before_ you create your domain.


##### Set up and initialize an infrastructure database

A JRF domain requires an infrastructure database and also requires initializing this database with a schema and a set of tables. The following example shows how to set up a database and use the RCU tool to create the infrastructure schema for a JRF domain. The database is set up with the following attributes:

| Attribute | Value |
| --------- | ----- |
| database Kubernetes namespace | `default` |
| database Kubernetes pod | `oracle-db` |
| database image | `container-registry.oracle.com/database/enterprise:12.2.0.1-slim` |
| database password | `Oradoc_db1` |
| infrastructure schema prefix | `FMW1` |
| infrastructure schema password | `Oradoc_db1` |
| database URL | `oracle-db.default.svc.cluster.local:1521/devpdb.k8s` |


1. Ensure that you have access to the database image, and then create a deployment using it:

   - Use a browser to log in to `https://container-registry.oracle.com`, select `Database -> enterprise` and accept the license agreement.

   - Get the database image:
     - In the local shell, `docker login container-registry.oracle.com`.
     - In the local shell, `docker pull container-registry.oracle.com/database/enterprise:12.2.0.1-slim`.

   - Use the sample script in `/tmp/operator-source/kubernetes/samples/scripts/create-oracle-db-service` to create an Oracle database running in the pod, `oracle-db`.

     ```
     $ cd /tmp/operator-source/kubernetes/samples/scripts/create-oracle-db-service
     $ start-db-service.sh
     ```

     This script will deploy a database in the `default` namespace with the connect string `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`, and administration password `Oradoc_db1`.

     This step is based on the steps documented in [Run a Database](https://oracle.github.io/weblogic-kubernetes-operator/userguide/overview/database/).

     **WARNING:** The Oracle Database Docker images are supported only for non-production use. For more details, see My Oracle Support note: Oracle Support for Database Running on Docker (Doc ID 2216342.1).


2. Use the sample script in `/tmp/operator-source/kubernetes/samples/scripts/create-rcu-schema` to create the RCU schema with the schema prefix `FMW1`.

   Note that this script assumes `Oradoc_db1` is the DBA password, `Oradoc_db1` is the schema password, and that the database URL is `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`.

   ```
   $ cd /tmp/operator-source/kubernetes/samples/scripts/create-rcu-schema
   $ ./create-rcu-schema.sh -s FMW1 -i container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4
   ```

   __NOTE__:  If you need to drop the repository, use this command:

   ```
   $ drop-rcu-schema.sh -s FMW1
   ```


##### Increase introspection job timeout

The JRF domain home creation can take more time than the introspection job's default timeout. You should increase the timeout for the introspection job. Use the `configuration.introspectorJobActiveDeadlineSeconds` in your domain resource to override the default with a value of at least 300 seconds (the default is 120 seconds). Note that the `JRF` versions of the domain resource files that are provided in `/tmp/mii-sample/domain-resources` already set this value.

##### Important considerations for RCU model attributes, domain resource attributes, and secrets

To allow Model in Image to access the database and OPSS wallet, you must create an RCU access secret containing the database connect string, user name, and password that's referenced from your model and an OPSS wallet password secret that's referenced from your domain resource before deploying your domain.  It's also necessary to define an `RCUDbInfo` stanza in your model.

The sample includes examples of JRF models and domain resources in the `/tmp/mii-sample/model-images` and `/tmp/mii-sample/domain-resources` directories, and instructions in the following sections will describe setting up the RCU and OPSS secrets.

When you follow the instructions later in this sample, avoid instructions that are `WLS` only, and substitute `JRF` for `WLS` in the corresponding model image tags and domain resource file names.

For example, in this sample:

  - JRF domain resources have an `configuration.opss.walletPasswordSecret` field that references a secret named `sample-domain1-opss-wallet-password-secret`, with `password=welcome1`.

  - JRF image models have a `domainInfo -> RCUDbInfo` stanza that reference a `sample-domain1-rcu-access` secret with appropriate values for attributes `rcu_prefix`, `rcu_schema_password`, and `rcu_db_conn_string` for accessing the Oracle database that you deployed to the default namespace as one of the prerequisite steps.

##### Important considerations for reusing or sharing OPSS tables

{{% notice warning %}}
We do not recommend that users share OPSS tables.  Extreme caution is required when sharing OPSS tables between domains.
{{% /notice %}}

When you successfully deploy your JRF domain resource for the first time, the introspector job will initialize the OPSS tables for the domain using the `domainInfo -> RCUDbInfo` stanza in the WDT model plus the `configuration.opss.walletPasswordSecret` specified in the domain resource. The job will also create a new domain home. Finally, the operator will also capture an OPSS wallet file from the new domain's local directory and place this file in a new Kubernetes ConfigMap.

There are scenarios when the domain needs to be recreated between updates, such as when WebLogic credentials are changed, security roles defined in the WDT model have been changed, or you want to share the same infrastructure tables with different domains.  In these scenarios, the operator needs the `walletPasswordSecret` as well as the OPSS wallet file, together with the exact information in `domainInfo -> RCUDbInfo` so that the domain can be recreated and access the same set of tables.  Without the wallet file and wallet password, you will not be able to recreate a domain accessing the same set of  tables, therefore we strongly recommend that you back up the wallet file.

To recover a domain's OPSS tables between domain restarts or to share an OPSS schema between different domains, it is necessary to extract this wallet file from the domain's automatically deployed introspector ConfigMap and save the OPSS wallet password secret that was used for the original domain. The wallet password and wallet file are needed again when you recreate the domain or share the database with other domains.

To save the wallet file, assuming that your namespace is `sample-domain1-ns` and your domain UID is `sample-domain1`:

```
  $ kubectl -n sample-domain1-ns \
    get configmap sample-domain1-weblogic-domain-introspect-cm \
    -o jsonpath='{.data.ewallet\.p12}' \
    > ./ewallet.p12
```

Alternatively, you can save the file using the sample's wallet utility:

```
  $ /tmp/mii-sample/utils/opss-wallet.sh -n sample-domain1-ns -d sample-domain1 -wf ./ewallet.p12
  # For help: /tmp/mii-sample/utils/opss-wallet.sh -?
```

__Important! Back up your wallet file to a safe location that can be retrieved later.__

To reuse the wallet file in subsequent redeployments or to share the domain's OPSS tables between different domains:

1. Load the saved wallet file into a secret with a key named `walletFile` (again, assuming that your domain UID is `sample-domain1` and your namespace is `sample-domain1-ns`):

```
  $ kubectl -n sample-domain1-ns create secret generic sample-domain1-opss-walletfile-secret \
     --from-file=walletFile=./ewallet.p12
  $ kubectl -n sample-domain1-ns label secret sample-domain1-opss-walletfile-secret \
     weblogic.domainUID=`sample-domain1`
```

Alternatively, use the sample's wallet utility:
```
  $ /tmp/mii-sample/utils/opss-wallet.sh -n sample-domain1-ns -d sample-domain1 -wf ./ewallet.p12 -ws sample-domain1-opss-walletfile-secret
  # For help: /tmp/mii-sample/utils/opss-wallet.sh -?
```

2. Modify your domain resource JRF YAML files to provide the wallet file secret name, for example:

```
  configuration:
    opss:
      # Name of secret with walletPassword for extracting the wallet
      walletPasswordSecret: sample-domain1-opss-wallet-password-secret      
      # Name of secret with walletFile containing base64 encoded opss wallet
      walletFileSecret: sample-domain1-opss-walletfile-secret
```
> **Note**: The sample JRF domain resource files included in `/tmp/mii-sample/domain-resources` already have the above YAML file stanza.

### Initial use case

#### Contents

 - [Overview](#overview)
 - Image creation
    - [Image creation - Introduction](#image-creation---introduction)
    - [Understanding our first archive](#understanding-our-first-archive)
    - [Staging a ZIP file of the archive](#staging-a-zip-file-of-the-archive)
    - [Staging model files](#staging-model-files)
    - [Creating the image with WIT](#creating-the-image-with-wit)
 - Deploy resources
    - [Deploy resources - Introduction](#deploy-resources---introduction)
    - [Secrets](#secrets)
    - [Domain resource](#domain-resource)

#### Overview

In this use case, you set up an initial WebLogic domain. This involves:

  - A WDT archive ZIP file that contains your applications.
  - A WDT model that describes your WebLogic configuration.
  - A Docker image that contains your WDT model files and archive.
  - Creating secrets for the domain.
  - Creating a domain resource for the domain that references your secrets and image.

After the domain resource is deployed, the operator starts an 'introspector job' that converts your models into a WebLogic configuration, and then passes this configuration to each WebLogic Server in the domain.

{{% notice note %}}
Perform the steps in [Prerequisites for all domain types](#prerequisites-for-all-domain-types) before performing the steps in this use case.  
If you are taking the `JRF` path through the sample, then substitute `JRF` for `WLS` in your image names and directory paths. Also note that the JRF-v1 model YAML file differs from the WLS-v1 YAML file (it contains an additional `domainInfo -> RCUDbInfo` stanza).
{{% /notice %}}

#### Image creation - Introduction

The goal of the initial use case 'image creation' is to demonstrate using the WebLogic Image Tool to create an image named `model-in-image:WLS-v1` from files that you will stage to `/tmp/mii-sample/model-images/model-in-image:WLS-v1/`. The staged files will contain a web application in a WDT archive, and WDT model configuration for a WebLogic Administration Server called `admin-server` and a WebLogic cluster called `cluster-1`.

Overall, a Model in Image image must contain a WebLogic installation and also a WebLogic Deploy Tooling installation in its `/u01/wdt/weblogic-deploy` directory. In addition, if you have WDT model archive files, then the image must also contain these files in its `/u01/wdt/models` directory. Finally, an image optionally may also contain your WDT model YAML file and properties files in the same `/u01/wdt/models` directory. If you do not specify a WDT model YAML file in your `/u01/wdt/models` directory, then the model YAML file must be supplied dynamically using a Kubernetes ConfigMap that is referenced by your domain resource `spec.model.configMap` attribute. We provide an example of using a model ConfigMap later in this sample.

Here are the steps for creating the image `model-in-image:WLS-v1`:

- [Understanding your first archive](#understanding-your-first-archive)
- [Staging a ZIP file of the archive](#staging-a-zip-file-of-the-archive)
- [Staging model files](#staging-model-files)
- [Creating the image with WIT](#creating-the-image-with-wit)

#### Understanding your first archive

The sample includes a predefined archive directory in `/tmp/mii-sample/archives/archive-v1` that you will use to create an archive ZIP file for the image.

The archive top directory, named `wlsdeploy`, contains a directory named `applications`, which includes an 'exploded' sample JSP web application in the directory, `myapp-v1`. Three useful aspects to remember about WDT archives are:
  - A model image can contain multiple WDT archives.
  - WDT archives can contain multiple applications, libraries, and other components.
  - WDT archives have a [well defined directory structure](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/archive.md), which always has `wlsdeploy` as the top directory.

{{%expand "If you are interested in the web application source, click here to see the JSP code." %}}

```
<%-- Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates. --%>
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
    out.println("Welcome to WebLogic server '" + srName + "'!");
    out.println();
    out.println(" domain UID  = '" + domainUID +"'");
    out.println(" domain name = '" + domainName +"'");
    out.println();

    MBeanServer mbs = (MBeanServer)ic.lookup("java:comp/env/jmx/runtime");

    // display the current server's cluster name
    Set<ObjectInstance> clusterRuntimes = mbs.queryMBeans(new ObjectName("*:Type=ClusterRuntime,*"), null);
    out.println("Found " + clusterRuntimes.size() + " local cluster runtime" + (String)((clusterRuntimes.size()!=1)?"s:":":"));
    for (ObjectInstance clusterRuntime : clusterRuntimes) {
       String cName = (String)mbs.getAttribute(clusterRuntime.getObjectName(), "Name");
       out.println("  Cluster '" + cName + "'");
    }
    out.println();


    // display local data sources
    ObjectName jdbcRuntime = new ObjectName("com.bea:ServerRuntime=" + srName + ",Name=" + srName + ",Type=JDBCServiceRuntime");
    ObjectName[] dataSources = (ObjectName[])mbs.getAttribute(jdbcRuntime, "JDBCDataSourceRuntimeMBeans");
    out.println("Found " + dataSources.length + " local data source" + (String)((dataSources.length!=1)?"s:":":"));
    for (ObjectName dataSource : dataSources) {
       String dsName  = (String)mbs.getAttribute(dataSource, "Name");
       String dsState = (String)mbs.getAttribute(dataSource, "State");
       out.println("  Datasource '" + dsName + "': State='" + dsState +"'");
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

When you create the image, you will use the files in the staging directory, `/tmp/mii-sample/model-in-image__WLS-v1`. In preparation, you need it to contain a ZIP file of the WDT application archive.

Run the following commands to create your application archive ZIP file and put it in the expected directory:

```
# Delete existing archive.zip in case we have an old leftover version
$ rm -f /tmp/mii-sample/model-images/model-in-image__WLS-v1/archive.zip

# Move to the directory which contains the source files for our archive
$ cd /tmp/mii-sample/archives/archive-v1

# Zip the archive to the location will later use when we run the WebLogic Image Tool
$ zip -r /tmp/mii-sample/model-images/model-in-image__WLS-v1/archive.zip wlsdeploy
```

#### Staging model files

In this step, you explore the staged WDT model YAML file and properties in the `/tmp/mii-sample/model-in-image__WLS-v1` directory. The model in this directory references the web application in your archive, configures a WebLogic Administration Server, and configures a WebLogic cluster. It consists of only two files, `model.10.properties`, a file with a single property, and, `model.10.yaml`, a YAML file with your WebLogic configuration `model.10.yaml`.

```
CLUSTER_SIZE=5
```

Here is the WLS `model.10.yaml`:

```
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
```


{{%expand "Click here to view the JRF `model.10.yaml`, and note the `RCUDbInfo` stanza and its references to a `DOMAIN_UID-rcu-access` secret." %}}

```
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
```
{{% /expand %}}


The model files:

- Define a WebLogic domain with:
  - Cluster `cluster-1`
  - Administration Server `admin-server`
  - A `cluster-1` targeted `ear` application that's located in the WDT archive ZIP file at `wlsdeploy/applications/myapp-v1`

- Leverage macros to inject external values:
  - The property file `CLUSTER_SIZE` property is referenced in the model YAML file `DynamicClusterSize` and `MaxDynamicClusterSize` fields using a PROP macro.
  - The model file domain name is injected using a custom environment variable named `CUSTOM_DOMAIN_NAME` using an ENV macro.
    - You set this environment variable later in this sample using an `env` field in its domain resource.
    - _This conveniently provides a simple way to deploy multiple differently named domains using the same model image._
  - The model file administrator user name and password are set using a `weblogic-credentials` secret macro reference to the WebLogic credential secret.
    - This secret is in turn referenced using the `webLogicCredentialsSecret` field in the domain resource.
    - The `weblogic-credentials` is a reserved name that always dereferences to the owning domain resource actual WebLogic credentials secret name.

A Model in Image image can contain multiple properties files, archive ZIP files, and YAML files, but in this sample you use just one of each. For a complete discussion of Model in Images model file naming conventions, file loading order, and macro syntax, see [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}) in the Model in Image user documentation.


#### Creating the image with WIT

> **Note**: If you are using JRF in this sample, substitute `JRF` for each occurrence of `WLS` in the `imagetool` command line below, plus substitute `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4` for the `--fromImage` value.

At this point, you have staged all of the files needed for image `model-in-image:WLS-v1`; they include:

  - `/tmp/mii-sample/model-images/weblogic-deploy.zip`
  - `/tmp/mii-sample/model-images/model-in-image__WLS-v1/model.10.yaml`
  - `/tmp/mii-sample/model-images/model-in-image__WLS-v1/model.10.properties`
  - `/tmp/mii-sample/model-images/model-in-image__WLS-v1/archive.zip`

If you don't see the `weblogic-deploy.zip` file, then you missed a step in the prerequisites.

Now, you use the Image Tool to create an image named `model-in-image:WLS-v1` that's layered on a base WebLogic image. You've already set up this tool during the prerequisite steps.

Run the following commands to create the model image and verify that it worked:

  ```
  $ cd /tmp/mii-sample/model-images
  $ ./imagetool/bin/imagetool.sh update \
    --tag model-in-image:WLS-v1 \
    --fromImage container-registry.oracle.com/middleware/weblogic:12.2.1.4 \
    --wdtModel      ./model-in-image__WLS-v1/model.10.yaml \
    --wdtVariables  ./model-in-image__WLS-v1/model.10.properties \
    --wdtArchive    ./model-in-image__WLS-v1/archive.zip \
    --wdtModelOnly \
    --wdtDomainType WLS
  ```

If you don't see the `imagetool` directory, then you missed a step in the prerequisites.

This command runs the WebLogic Image Tool in its Model in Image mode, and does the following:

  - Builds the final Docker image as a layer on the `container-registry.oracle.com/middleware/weblogic:12.2.1.4` base image.
  - Copies the WDT ZIP file that's referenced in the WIT cache into the image.
    - Note that you cached WDT in WIT using the keyword `latest` when you set up the cache during the sample prerequisites steps.
    - This lets WIT implicitly assume it's the desired WDT version and removes the need to pass a `-wdtVersion` flag.
  - Copies the specified WDT model, properties, and application archives to image location `/u01/wdt/models`.

When the command succeeds, it should end with output like the following:

  ```
  [INFO   ] Build successful. Build time=36s. Image tag=model-in-image:WLS-v1
  ```

Also, if you run the `docker images` command, then you will see a Docker image named `model-in-image:WLS-v1`.

#### Deploy resources - Introduction

In this section, you will deploy the new image to namespace `sample-domain1-ns`, including the following steps:

  - Create a secret containing your WebLogic administrator user name and password.
  - Create a secret containing your Model in Image runtime encryption password:
    - All Model in Image domains must supply a runtime encryption secret with a `password` value.
    - It is used to encrypt configuration that is passed around internally by the operator.
    - The value must be kept private but can be arbitrary; you can optionally supply a different secret value every time you restart the domain.
  - If your domain type is `JRF`, create secrets containing your RCU access URL, credentials, and prefix.
  - Deploy a domain resource YAML file that references the new image.
  - Wait for the domain's pods to start and reach their ready state.

#### Secrets

First, create the secrets needed by both `WLS` and `JRF` type model domains. In this case, you have two secrets.

Run the following `kubectl` commands to deploy the required secrets:

  ```
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-weblogic-credentials \
     --from-literal=username=weblogic --from-literal=password=welcome1
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-weblogic-credentials \
    weblogic.domainUID=sample-domain1

  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-runtime-encryption-secret \
     --from-literal=password=my_runtime_password
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-runtime-encryption-secret \
    weblogic.domainUID=sample-domain1
  ```

  Some important details about these secrets:

  - The WebLogic credentials secret:
    - It is required and must contain `username` and `password` fields.
    - It must be referenced by the `spec.webLogicCredentialsSecret` field in your domain resource.
    - It also must be referenced by macros in the `domainInfo.AdminUserName` and `domainInfo.AdminPassWord` fields in your model YAML file.

  - The Model WDT runtime secret:
    - This is a special secret required by Model in Image.
    - It must contain a `password` field.
    - It must be referenced using the `spec.model.runtimeEncryptionSecret` attribute in its domain resource.
    - It must remain the same for as long as the domain is deployed to Kubernetes, but can be changed between deployments.
    - It is used to encrypt data as it's internally passed using log files from the domain's introspector job and on to its WebLogic Server pods.

  - Deleting and recreating the secrets:
    - You delete a secret before creating it, otherwise the create command will fail if the secret already exists.
    - This allows you to change the secret when using the `kubectl create secret` command.

  - You name and label secrets using their associated domain UID for two reasons:
    - To make it obvious which secrets belong to which domains.
    - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all resources associated with a domain.


  If you're following the `JRF` path through the sample, then you also need to deploy the additional secret referenced by macros in the `JRF` model `RCUDbInfo` clause, plus an `OPSS` wallet password secret. For details about the uses of these secrets, see the [Model in Image]({{< relref "/userguide/managing-domains/model-in-image/_index.md" >}}) user documentation.

  {{%expand "Click here for the commands for deploying additional secrets for JRF." %}}

  ```
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-rcu-access \
     --from-literal=rcu_prefix=FMW1 \
     --from-literal=rcu_schema_password=Oradoc_db1 \
     --from-literal=rcu_db_conn_string=oracle-db.default.svc.cluster.local:1521/devpdb.k8s
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-rcu-access \
    weblogic.domainUID=sample-domain1

  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-opss-wallet-password-secret \
     --from-literal=walletPassword=welcome1
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-opss-wallet-password-secret \
    weblogic.domainUID=sample-domain1
  ```

  {{% /expand %}}


#### Domain resource

Now, you create a domain resource. A domain resource is the key resource that tells the operator how to deploy a WebLogic domain.

Copy the following to a file called `/tmp/mii-sample/mii-initial.yaml` or similar, or use the file `/tmp/mii-sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml` that is included in the sample source.

  {{%expand "Click here to view the WLS domain resource YAML file." %}}
  ```
    #
    # This is an example of how to define a Domain resource.
    #
    apiVersion: "weblogic.oracle/v7"
    kind: Domain
    metadata:
      name: sample-domain1
      namespace: sample-domain1-ns
      labels:
        weblogic.resourceVersion: domain-v2
        weblogic.domainUID: sample-domain1

    spec:
      # Set to 'FromModel' to indicate 'Model in Image'.
      domainHomeSourceType: FromModel

      # The WebLogic Domain Home, this must be a location within
      # the image for 'Model in Image' domains.
      domainHome: /u01/domains/sample-domain1

      # The WebLogic Server Docker image that the Operator uses to start the domain
      image: "model-in-image:WLS-v1"

      # Defaults to "Always" if image tag (version) is ':latest'
      imagePullPolicy: "IfNotPresent"

      # Identify which Secret contains the credentials for pulling an image
      #imagePullSecrets:
      #- name: regsecret

      # Identify which Secret contains the WebLogic Admin credentials,
      # the secret must contain 'username' and 'password' fields.
      webLogicCredentialsSecret:
        name: sample-domain1-weblogic-credentials

      # Whether to include the WebLogic server stdout in the pod's stdout, default is true
      includeServerOutInPodLog: true

      # Whether to enable overriding your log file location, see also 'logHome'
      #logHomeEnabled: false

      # The location for domain log, server logs, server out, and Node Manager log files
      # see also 'logHomeEnabled', 'volumes', and 'volumeMounts'.
      #logHome: /shared/logs/sample-domain1

      # Set which WebLogic servers the Operator will start
      # - "NEVER" will not start any server in the domain
      # - "ADMIN_ONLY" will start up only the administration server (no managed servers will be started)
      # - "IF_NEEDED" will start all non-clustered servers, including the administration server, and clustered servers up to their replica count.
      serverStartPolicy: "IF_NEEDED"

      # Settings for all server pods in the domain including the introspector job pod
      serverPod:
        # Optional new or overridden environment variables for the domain's pods
        # - This sample uses CUSTOM_DOMAIN_NAME in its image model file
        #   to set the Weblogic domain name
        env:
        - name: CUSTOM_DOMAIN_NAME
          value: "domain1"
        - name: JAVA_OPTIONS
          value: "-Dweblogic.StdoutDebugEnabled=false"
        - name: USER_MEM_ARGS
          value: "-XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom "

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
        replicas: 2

      # Change the `restartVersion` to force the introspector job to rerun
      # and apply any new model configuration, to also force a subsequent
      # roll of your domain's WebLogic pods.
      restartVersion: '1'

      configuration:

        # Settings for domainHomeSourceType 'FromModel'
        model:
          # Valid model domain types are 'WLS', 'JRF', and 'RestrictedJRF', default is 'WLS'
          domainType: "WLS"

          # Optional configmap for additional models and variable files
          #configMap: sample-domain1-wdt-config-map

          # All 'FromModel' domains require a runtimeEncryptionSecret with a 'password' field
          runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret

        # Secrets that are referenced by model yaml macros
        # (the model yaml in the optional configMap or in the image)
        #secrets:
        #- sample-domain1-datasource-secret
  ```
  {{% /expand %}}

  {{%expand "Click here to view the JRF domain resource YAML file." %}}
  ```
  # Copyright (c) 2020, Oracle Corporation and/or its affiliates.
  # Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
  #
  # This is an example of how to define a Domain resource.
  #
  apiVersion: "weblogic.oracle/v7"
  kind: Domain
  metadata:
    name: sample-domain1
    namespace: sample-domain1-ns
    labels:
      weblogic.resourceVersion: domain-v2
      weblogic.domainUID: sample-domain1

  spec:
    # Set to 'FromModel' to indicate 'Model in Image'.
    domainHomeSourceType: FromModel

    # The WebLogic Domain Home, this must be a location within
    # the image for 'Model in Image' domains.
    domainHome: /u01/domains/sample-domain1

    # The WebLogic Server Docker image that the Operator uses to start the domain
    image: "model-in-image:JRF-v1"

    # Defaults to "Always" if image tag (version) is ':latest'
    imagePullPolicy: "IfNotPresent"

    # Identify which Secret contains the credentials for pulling an image
    #imagePullSecrets:
    #- name: regsecret

    # Identify which Secret contains the WebLogic Admin credentials,
    # the secret must contain 'username' and 'password' fields.
    webLogicCredentialsSecret:
      name: sample-domain1-weblogic-credentials

    # Whether to include the WebLogic server stdout in the pod's stdout, default is true
    includeServerOutInPodLog: true

    # Whether to enable overriding your log file location, see also 'logHome'
    #logHomeEnabled: false

    # The location for domain log, server logs, server out, and Node Manager log files
    # see also 'logHomeEnabled', 'volumes', and 'volumeMounts'.
    #logHome: /shared/logs/sample-domain1

    # Set which WebLogic servers the Operator will start
    # - "NEVER" will not start any server in the domain
    # - "ADMIN_ONLY" will start up only the administration server (no managed servers will be started)
    # - "IF_NEEDED" will start all non-clustered servers, including the administration server, and clustered servers up to their replica count.
    serverStartPolicy: "IF_NEEDED"

    # Settings for all server pods in the domain including the introspector job pod
    serverPod:
      # Optional new or overridden environment variables for the domain's pods
      # - This sample uses CUSTOM_DOMAIN_NAME in its image model file
      #   to set the Weblogic domain name
      env:
      - name: CUSTOM_DOMAIN_NAME
        value: "domain1"
      - name: JAVA_OPTIONS
        value: "-Dweblogic.StdoutDebugEnabled=false"
      - name: USER_MEM_ARGS
        value: "-XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom "

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
      replicas: 2

    # Change the restartVersion to force the introspector job to rerun
    # and apply any new model configuration, to also force a subsequent
    # roll of your domain's WebLogic pods.
    restartVersion: '1'

    configuration:

      # Settings for domainHomeSourceType 'FromModel'
      model:
        # Valid model domain types are 'WLS', 'JRF', and 'RestrictedJRF', default is 'WLS'
        domainType: "JRF"

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
      introspectorJobActiveDeadlineSeconds: 300

      opss:

        # Name of secret with walletPassword for extracting the wallet, used for JRF domains
        walletPasswordSecret: sample-domain1-opss-wallet-password-secret

        # Name of secret with walletFile containing base64 encoded opss wallet, used for JRF domains
        #walletFileSecret: sample-domain1-opss-walletfile-secret
  ```
  {{% /expand %}}


  Run the following command to create the domain custom resource:

  ```
  $ kubectl apply -f /tmp/mii-sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml
  ```

  > Note: If you are choosing not to use the predefined domain resource YAML file and instead created your own domain resource file earlier, then substitute your custom file name in the above command. Previously, we suggested naming it `/tmp/mii-sample/mii-initial.yaml`.

  If you run `kubectl get pods -n sample-domain1-ns --watch`, then you will see the introspector job run and your WebLogic Server pods start. The output will look something like this:

  {{%expand "Click here to expand." %}}
  ```
  $ kubectl get pods -n sample-domain1-ns --watch
  NAME                                         READY   STATUS    RESTARTS   AGE
  sample-domain1-introspect-domain-job-lqqj9   0/1   Pending   0     0s
  sample-domain1-introspect-domain-job-lqqj9   0/1   ContainerCreating   0     0s
  sample-domain1-introspect-domain-job-lqqj9   1/1   Running   0     1s
  sample-domain1-introspect-domain-job-lqqj9   0/1   Completed   0     65s
  sample-domain1-introspect-domain-job-lqqj9   0/1   Terminating   0     65s
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

Alternatively, you can run `/tmp/mii-sample/utils/wl-pod-wait.sh -p 3`. This is a utility script that provides useful information about a domain's pods and waits for them to reach a `ready` state, reach their target `restartVersion`, and reach their target `image` before exiting.

  {{%expand "Click here to display the `wl-pod-wait.sh` usage." %}}
  ```
  $ ./wl-pod-wait.sh -?

    Usage:

      wl-pod-wait.sh [-n mynamespace] [-d mydomainuid] \
         [-p expected_pod_count] \
         [-t timeout_secs] \
         [-q]

      Exits non-zero if 'timeout_secs' is reached before 'pod_count' is reached.

    Parameters:

      -d <domain_uid> : Defaults to 'sample-domain1'.

      -n <namespace>  : Defaults to 'sample-domain1-ns'.

      pod_count > 0   : Wait until exactly 'pod_count' WebLogic server pods for
                        a domain all (a) are ready, (b) have the same
                        'domainRestartVersion' label value as the
                        current domain resource's 'spec.restartVersion, and
                        (c) have the same image as the current domain
                        resource's image.

      pod_count = 0   : Wait until there are no running WebLogic server pods
                        for a domain. The default.

      -t <timeout>    : Timeout in seconds. Defaults to '600'.

      -q              : Quiet mode. Show only a count of wl pods that
                        have reached the desired criteria.

      -?              : This help.
  ```
  {{% /expand %}}

  {{%expand "Click here to view sample output from `wl-pod-wait.sh`." %}}
  ```
  @@ [2020-04-30T13:50:42][seconds=0] Info: Waiting up to 600 seconds for exactly '3' WebLogic server pods to reach the following criteria:
  @@ [2020-04-30T13:50:42][seconds=0] Info:   ready='true'
  @@ [2020-04-30T13:50:42][seconds=0] Info:   image='model-in-image:WLS-v1'
  @@ [2020-04-30T13:50:42][seconds=0] Info:   domainRestartVersion='1'
  @@ [2020-04-30T13:50:42][seconds=0] Info:   namespace='sample-domain1-ns'
  @@ [2020-04-30T13:50:42][seconds=0] Info:   domainUID='sample-domain1'

  @@ [2020-04-30T13:50:42][seconds=0] Info: '0' WebLogic pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:50:42][seconds=0] Info: Introspector and WebLogic pods with same namespace and domain-uid:

  NAME                                          VERSION  IMAGE  READY  PHASE
  --------------------------------------------  -------  -----  -----  ---------
  'sample-domain1-introspect-domain-job-rkdkg'  ''       ''     ''     'Pending'

  @@ [2020-04-30T13:50:45][seconds=3] Info: '0' WebLogic pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:50:45][seconds=3] Info: Introspector and WebLogic pods with same namespace and domain-uid:

  NAME                                          VERSION  IMAGE  READY  PHASE
  --------------------------------------------  -------  -----  -----  ---------
  'sample-domain1-introspect-domain-job-rkdkg'  ''       ''     ''     'Running'


  @@ [2020-04-30T13:51:50][seconds=68] Info: '0' WebLogic pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:51:50][seconds=68] Info: Introspector and WebLogic pods with same namespace and domain-uid:

  NAME  VERSION  IMAGE  READY  PHASE
  ----  -------  -----  -----  -----

  @@ [2020-04-30T13:51:59][seconds=77] Info: '0' WebLogic pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:51:59][seconds=77] Info: Introspector and WebLogic pods with same namespace and domain-uid:

  NAME                           VERSION  IMAGE                    READY    PHASE
  -----------------------------  -------  -----------------------  -------  ---------
  'sample-domain1-admin-server'  '1'      'model-in-image:WLS-v1'  'false'  'Pending'

  @@ [2020-04-30T13:52:02][seconds=80] Info: '0' WebLogic pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:52:02][seconds=80] Info: Introspector and WebLogic pods with same namespace and domain-uid:

  NAME                           VERSION  IMAGE                    READY    PHASE
  -----------------------------  -------  -----------------------  -------  ---------
  'sample-domain1-admin-server'  '1'      'model-in-image:WLS-v1'  'false'  'Running'

  @@ [2020-04-30T13:52:32][seconds=110] Info: '1' WebLogic pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:52:32][seconds=110] Info: Introspector and WebLogic pods with same namespace and domain-uid:

  NAME                              VERSION  IMAGE                    READY    PHASE
  --------------------------------  -------  -----------------------  -------  ---------
  'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v1'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'false'  'Pending'
  'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'false'  'Pending'

  @@ [2020-04-30T13:52:34][seconds=112] Info: '1' WebLogic pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:52:34][seconds=112] Info: Introspector and WebLogic pods with same namespace and domain-uid:

  NAME                              VERSION  IMAGE                    READY    PHASE
  --------------------------------  -------  -----------------------  -------  ---------
  'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v1'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'false'  'Running'
  'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'false'  'Running'

  @@ [2020-04-30T13:53:14][seconds=152] Info: '3' WebLogic pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:53:14][seconds=152] Info: Introspector and WebLogic pods with same namespace and domain-uid:

  NAME                              VERSION  IMAGE                    READY   PHASE
  --------------------------------  -------  -----------------------  ------  ---------
  'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v1'  'true'  'Running'
  'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'  'Running'
  'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'  'Running'


  @@ [2020-04-30T13:53:14][seconds=152] Info: Success!

  ```
  {{% /expand %}}


If you see an error, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

#### Invoke the web application

Now that all the initial use case resources have been deployed, you can invoke the sample web application through the Traefik ingress controller's NodePort. Note: The web application will display a list of any data sources it finds, but, at this point, we don't expect it to find any because the model doesn't contain any.

Send a web application request to the load balancer:

   ```
   $ curl -s -S -m 10 -H 'host: sample-domain1-cluster-cluster-1.mii-sample.org' \
      http://localhost:30305/myapp_war/index.jsp
   ```
Or, if Traefik is unavailable and your Administration Server pod is running, you can use `kubectl exec`:

   ```
   $ kubectl exec -n sample-domain1-ns sample-domain1-admin-server -- bash -c \
     "curl -s -S -m 10 http://sample-domain1-cluster-cluster-1:8001/myapp_war/index.jsp"
   ```

You will see output like the following:

   ```
   <html><body><pre>
   *****************************************************************

   Hello World! This is version 'v1' of the mii-sample JSP web-app.

   Welcome to WebLogic server 'managed-server2'!

    domain UID  = 'sample-domain1'
    domain name = 'domain1'

   Found 1 local cluster runtime:
     Cluster 'cluster-1'

   Found 0 local data sources:

   *****************************************************************
   </pre></body></html>
   ```

 **Note**: If you're running your `curl` commands on a remote machine, then substitute `localhost` with an external address suitable for contacting your Kubernetes cluster. A Kubernetes cluster address that often works can be obtained by using the address just after `https://` in the KubeDNS line of the output from the `kubectl cluster-info` command.

 If you want to continue to the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case, then leave your domain running.

 To remove the resources you have created in this sample, see [Cleanup]({{< relref "/samples/simple/domains/model-in-image/cleanup.md" >}}).

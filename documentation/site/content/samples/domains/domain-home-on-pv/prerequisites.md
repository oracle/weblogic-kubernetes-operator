---
title: "Prerequisites"
date: 2019-02-23T17:32:31-05:00
weight: 1
description: "Follow these prerequisite steps for WLS and JRF domain types."
---

### Contents

- [Prerequisites for WLS and JRF domain types](#prerequisites-for-wls-and-jrf-domain-types)
- [Additional prerequisites for JRF domains](#additional-prerequisites-for-jrf-domains)


### Prerequisites for WLS and JRF domain types

1. Choose the type of domain you’re going to use throughout the sample, `WLS` or `JRF`.

1. The `JAVA_HOME` environment variable must be set and must reference a valid JDK 8 or 11 installation.

1. Get the operator source and put it in `/tmp/weblogic-kubernetes-operator`.

   For example:

   ```shell
   $ cd /tmp
   ```
   ```shell
   $ git clone --branch v{{< latestVersion >}} https://github.com/oracle/weblogic-kubernetes-operator.git
   ```

   **NOTE**: We will refer to the top directory of the operator source tree as `/tmp/weblogic-kubernetes-operator`; however, you can use a different location.

   For additional information about obtaining the operator source, see the [Developer Guide Requirements](https://oracle.github.io/weblogic-kubernetes-operator/developerguide/requirements/).

1. Copy the Domain on PV sample to a new directory; for example, use directory `/tmp/sample`.
   ```
   $ mkdir -p /tmp/sample
   ```

   ```
   $ cp -r /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/domain-on-pv/* /tmp/sample
   ```
   **NOTE**: We will refer to this working copy of the sample as `/tmp/sample`; however, you can use a different location.

1. Copy the `wdt-artifacts` directory of the sample to a new directory; for example, use directory `/tmp/sample/wdt-artifacts`.


   ```shell
   $ mkdir -p /tmp/sample/wdt-artifacts
   ```
   ```shell
   $ cp -r /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/wdt-artifacts/* /tmp/sample/wdt-artifacts
   ```

1. Download the latest [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling/releases) (WDT) and [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool/releases) (WIT) installer ZIP files to your `/tmp/sample/wdt-artifacts` directory. Both WDT and WIT are required to create the images.

   ```shell
   $ cd /tmp/sample/wdt-artifacts
   ```
   ```shell
   $ curl -m 120 -fL https://github.com/oracle/weblogic-deploy-tooling/releases/latest/download/weblogic-deploy.zip \
     -o /tmp/sample/wdt-artifacts/weblogic-deploy.zip
   ```
   ```shell
   $ curl -m 120 -fL https://github.com/oracle/weblogic-image-tool/releases/latest/download/imagetool.zip \
     -o /tmp/sample/wdt-artifacts/imagetool.zip
   ```

1. To set up the WebLogic Image Tool, run the following commands:

   ```shell
   $ cd /tmp/sample/wdt-artifacts
   ```
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
     --path /tmp/sample/wdt-artifacts/weblogic-deploy.zip
   ```

   Note that the WebLogic Image Tool `cache deleteEntry` command does nothing
   if the `wdt_latest` key doesn't have a corresponding cache entry. It is included
   because the WIT cache lookup information is stored in the `$HOME/cache/.metadata`
   file by default, and if the cache already
   has a version of WDT in its `--type wdt --version latest` location, then the
   `cache addInstaller` command would fail.
   For more information about the WIT cache, see the
   [WIT Cache documentation](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/).

   These steps will install WIT to the `/tmp/sample/wdt-artifacts/imagetool` directory,
   plus put a `wdt_latest` entry in the tool's cache which points to the WDT ZIP file installer.
   You will use WIT and its cached reference to the WDT installer later in the sample for creating model images.

1. To set up the WebLogic Deploy Tooling that we will use later for the archive helper, run the following command:

   ```shell
   $ unzip /tmp/sample/wdt-artifacts/weblogic-deploy.zip
   ```
   {{< rawhtml >}}
   <a name="resume"></a>
   {{< /rawhtml >}}

1. Make sure an operator is set up to manage the namespace, `sample-domain1-ns`. Also, make sure a Traefik ingress controller is managing the same namespace and listening on port `30305`.
To do this, follow the same steps as the [Quick Start](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/) guide up through the [Prepare for a domain]({{< relref "/quickstart/prepare.md" >}}) step.


   {{% notice note %}}
   Make sure you **stop** when you complete the "Prepare for a domain" step and then resume following these instructions.
   {{% /notice %}}

1. Set up ingresses that will redirect HTTP from the Traefik port `30305` to the clusters in this sample's WebLogic domains.
   - Run `kubectl apply -f` on each of the ingress YAML files that are already included in the sample source directory:

       ```
       $ kubectl apply -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/ingresses/traefik-ingress-sample-domain1-admin-server.yaml
       $ kubectl apply -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/ingresses/traefik-ingress-sample-domain1-cluster-cluster-1.yaml
       ```

   **NOTE**: We give each cluster ingress a different host name that is decorated using both its operator domain UID and its cluster name. This makes each cluster uniquely addressable even when cluster names are the same across different clusters.  When using `curl` to access the WebLogic domain through the ingress, you will need to supply a host name header that matches the host names in the ingress.

   For more information on ingresses and load balancers, see [Ingress]({{< relref "/managing-domains/accessing-the-domain/ingress/_index.md" >}}).


1. Obtain the WebLogic 12.2.1.4 image that is referenced by the sample's Domain resource YAML.

   a. Use a browser to access the [Oracle Container Registry](http://container-registry.oracle.com).

   b. Choose an image location: for JRF domains, select `Middleware`, then `fmw-infrastructure`; for WLS domains, select `Middleware`, then `weblogic`.

   c. Select Sign In and accept the license agreement.

   d. Use your terminal to log in to the container registry: `docker login container-registry.oracle.com`.

   e. Later in this sample, when you run WebLogic Image Tool commands, the tool will use the image as a base image for creating model images. Specifically, the tool will implicitly call `docker pull` for one of the previous licensed images as specified in the tool's command line using the `--fromImage` parameter. For JRF, this sample specifies `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4`, and for WLS, the sample specifies `container-registry.oracle.com/middleware/weblogic:12.2.1.4`.

   {{% notice warning %}}
   The example base images are General Availability (GA) images that are suitable for demonstration and development purposes _only_ where the environments are not available from the public Internet; they are **not acceptable for production use**. In production, you should always use CPU (patched) images from [OCR]({{< relref "/base-images/ocr-images.md" >}}) or create your images using the [WebLogic Image Tool]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}}) (WIT) with the `--recommendedPatches` option. For more guidance, see [Apply the Latest Patches and Updates](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/14.1.1.0&id=LOCKD-GUID-2DA84185-46BA-4D7A-80D2-9D577A4E8DE2) in _Securing a Production Environment for Oracle WebLogic Server_.

     {{% /notice %}}

### Additional prerequisites for JRF domains

**NOTE**: If you're using a Domain on PV, WLS domain type, skip this section and continue on to [Build the domain creation image]({{< relref "/samples/domains/domain-home-on-pv/build-domain-creation-image.md" >}})
for the Domain on PV sample.

#### JRF prerequisites

 - [Introduction to JRF setups](#introduction-to-jrf-setups)
 - [Set up and initialize an infrastructure database](#set-up-and-initialize-an-infrastructure-database)
 - [Important considerations for RCU model attributes, Domain fields, and secrets](#important-considerations-for-rcu-model-attributes-domain-fields-and-secrets)

##### Introduction to JRF setups

**NOTE**: The requirements in this section are in addition to [Prerequisites for WLS and JRF domain types](#prerequisites-for-wls-and-jrf-domain-types).

A JRF domain requires an infrastructure database, and configuring your domain to access this database. For more details, see [JRF domains]({{< relref "/managing-domains/domain-on-pv/jrf-domain.md" >}}) in the user documentation. You must perform all these steps _before_ you create your domain.

##### Set up and initialize an infrastructure database

A JRF domain requires an infrastructure database and requires initializing this database with a schema and a set of tables for each different domain. The following example shows how to set up a database. The database is set up with the following attributes:

| Attribute | Value |
| --------- | ----- |
| database Kubernetes namespace | `default` |
| database Kubernetes pod | `oracle-db` |
| database image | `container-registry.oracle.com/database/enterprise:12.2.0.1-slim` |
| database password | MY_DBA_PASSWORD |
| infrastructure schema prefix | `FMW1` (for domain1) |
| infrastructure schema password | MY_RCU_SCHEMA_PASSWORD |
| database URL | `oracle-db.default.svc.cluster.local:1521/devpdb.k8s` |


1. Ensure that you have access to the database image, and then create a deployment using it:

   - Use a browser to log in to `https://container-registry.oracle.com`, select `Database -> enterprise` and accept the license agreement.

   - Get the database image:
     - In the local shell, `docker login container-registry.oracle.com`.
     - In the local shell, `docker pull container-registry.oracle.com/database/enterprise:12.2.0.1-slim`.

   - Deploy a secret named `oracle-db-secret` with your desired Oracle DBA password for its `SYS` account.
     - In the local shell:
       ```shell
       $ kubectl -n default create secret generic oracle-db-secret \
         --from-literal='password=MY_DBA_PASSWORD'
       ```
     - Replace MY_DBA_PASSWORD with your desired value.
     - Oracle Database passwords can contain upper case, lower case, digits, and special characters.
       Use only `_` and `#` as special characters to eliminate potential parsing errors in Oracle connection strings.
     - **NOTE**: Record or memorize the value you chose for MY_DBA_PASSWORD. It will be be needed again in other parts of this sample.

   - Use the sample script in `/tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-oracle-db-service` to create an Oracle database running in the pod, `oracle-db`.

     ```shell
     $ cd /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-oracle-db-service
     ```
     ```shell
     $ start-db-service.sh
     ```

     This script will deploy a database in the `default` namespace with the connect string `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`, and administration password MY_DBA_PASSWORD.

     This step is based on the steps documented in [Run a Database]({{< relref "/samples/database/_index.md" >}}).

     **NOTE**: If your Kubernetes cluster nodes do not all have access to the database image in a local cache, then deploy a Kubernetes `docker secret` to the default namespace with login credentials for `container-registry.oracle.com`, and pass the name of this secret as a parameter to `start-db-service.sh` using `-s your-image-pull-secret`. Alternatively, copy the database image to each local Docker cache in the cluster.  For more information, see the [Cannot pull image FAQ]({{<relref "/faq/cannot-pull-image">}}).

     **WARNING:** The Oracle Database images are supported only for non-production use. For more details, see My Oracle Support note: Oracle Support for Database Running on Docker (Doc ID 2216342.1).

##### Important considerations for RCU model attributes, Domain fields, and secrets

To allow the operator to access the database and OPSS wallet, you must create an RCU access secret containing the database connect string, user name, and password that's referenced from your model and an OPSS wallet password secret that's referenced from your Domain before deploying your domain.  It's also necessary to define an `RCUDbInfo` stanza in your model.

The sample includes examples of JRF models and Domain YAML files in the `/tmp/sample/wdt-artifacts/wdt-model-files` and `/tmp/sample/domain-resources` directories, and instructions in the following sections will describe setting up the RCU and OPSS secrets.

When you follow the instructions in the samples, avoid instructions that are `WLS` only, and substitute `JRF` for `WLS` in the corresponding model image tags and Domain YAML file names.

For example, in this sample:

  - [JRF Domain YAML](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/domain-on-pv/domain-resources/JRF/domain-on-pv-JRF-v1.yaml) file has an `configuration.opss.walletPasswordSecret` field that references a secret named `sample-domain1-opss-wallet-password-secret`, with a `walletPassword` of your choice.

  - JRF domain creation image models have the following `domainInfo -> RCUDbInfo` stanza that references a `sample-domain1-rcu-access` secret with the appropriate values for attributes, `rcu_prefix`, `rcu_schema_password`, and `rcu_db_conn_string`, for accessing the Oracle database that you deployed to the default namespace as one of the prerequisite steps.

```
    RCUDbInfo:
        rcu_prefix: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_prefix@@'
        rcu_schema_password: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_schema_password@@'
        rcu_db_conn_string: '@@SECRET:@@ENV:DOMAIN_UID@@-rcu-access:rcu_db_conn_string@@'
```

For important JRF domain information, refer to [JRF domains]({{< relref "/managing-domains/domain-on-pv/jrf-domain.md" >}}).

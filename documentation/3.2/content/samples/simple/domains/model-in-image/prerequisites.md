---
title: "Prerequisites"
date: 2019-02-23T17:32:31-05:00
weight: 1
---


### Prerequisites for all domain types

1. Choose the type of domain you're going to use throughout the sample, `WLS` or `JRF`.

   - The first time you try this sample, we recommend that you choose `WLS` even if you're familiar with `JRF`.
   - This is because `WLS` is simpler and will more easily familiarize you with Model in Image concepts.
   - We recommend choosing `JRF` only if you are already familiar with `JRF`, you have already tried the `WLS` path through this sample, and you have a definite use case where you need to use `JRF`.

1. The `JAVA_HOME` environment variable must be set and must reference a valid JDK 8 or 11 installation.

1. Get the operator source and put it in `/tmp/weblogic-kubernetes-operator`.

   For example:

   ```shell
   $ cd /tmp
   ```
   ```shell
   $ git clone --branch v3.2.1 https://github.com/oracle/weblogic-kubernetes-operator.git
   ```

   > **Note**: We will refer to the top directory of the operator source tree as `/tmp/weblogic-kubernetes-operator`; however, you can use a different location.

   For additional information about obtaining the operator source, see the [Developer Guide Requirements](https://oracle.github.io/weblogic-kubernetes-operator/developerguide/requirements/).

1. Copy the sample to a new directory; for example, use directory `/tmp/mii-sample`.


   ```shell
   $ mkdir /tmp/mii-sample
   ```
   ```shell
   $ cp -r /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/* /tmp/mii-sample
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

       ```yaml
       apiVersion: traefik.containo.us/v1alpha1
       kind: IngressRoute
       metadata:
         name: traefik-ingress-sample-domain1-admin-server
         namespace: sample-domain1-ns
         labels:
           weblogic.domainUID: sample-domain1
         annotations:
           kubernetes.io/ingress.class: traefik
       spec:
         routes:
         - kind: Rule
           match: PathPrefix(`/console`)
           services:
           - kind: Service
             name: sample-domain1-admin-server
             port: 7001
       ---
       apiVersion: traefik.containo.us/v1alpha1
       kind: IngressRoute
       metadata:
         name: traefik-ingress-sample-domain1-cluster-cluster-1
         namespace: sample-domain1-ns
         labels:
           weblogic.domainUID: sample-domain1
         annotations:
           kubernetes.io/ingress.class: traefik
       spec:
         routes:
         - kind: Rule
           match: Host(`sample-domain1-cluster-cluster-1.mii-sample.org`)
           services:
           - kind: Service
             name: sample-domain1-cluster-cluster-1
             port: 8001
       ---
       apiVersion: traefik.containo.us/v1alpha1
       kind: IngressRoute
       metadata:
         name: traefik-ingress-sample-domain2-cluster-cluster-1
         namespace: sample-domain1-ns
         labels:
           weblogic.domainUID: sample-domain2
         annotations:
           kubernetes.io/ingress.class: traefik
       spec:
         routes:
         - kind: Rule
           match: Host(`sample-domain2-cluster-cluster-1.mii-sample.org`)
           services:
           - kind: Service
             name: sample-domain2-cluster-cluster-1
             port: 8001
       ```

   - Option 2: Run `kubectl apply -f` on each of the ingress YAML files that are already included in the sample source `/tmp/mii-sample/ingresses` directory:

       ```
       $ cd /tmp/mii-sample/ingresses
       $ kubectl apply -f traefik-ingress-sample-domain1-admin-server.yaml
       $ kubectl apply -f traefik-ingress-sample-domain1-cluster-cluster-1.yaml
       $ kubectl apply -f traefik-ingress-sample-domain2-cluster-cluster-1.yaml
       ```

   > **NOTE**: We give each cluster ingress a different host name that is decorated using both its operator domain UID and its cluster name. This makes each cluster uniquely addressable even when cluster names are the same across different clusters.  When using `curl` to access the WebLogic domain through the ingress, you will need to supply a host name header that matches the host names in the ingress.

   For more information on ingresses and load balancers, see [Ingress]({{< relref "/userguide/managing-domains/ingress/_index.md" >}}).

1. Obtain the WebLogic 12.2.1.4 image that is required to create the sample's model images.

   a. Use a browser to access [Oracle Container Registry](http://container-registry.oracle.com).

   b. Choose an image location: for `JRF` domains, select `Middleware`, then `fmw-infrastructure`; for `WLS` domains, select `Middleware`, then `weblogic`.

   c. Select Sign In and accept the license agreement.

   d. Use your terminal to log in to the container registry: `docker login container-registry.oracle.com`.

   e. Later in this sample, when you run WebLogic Image Tool commands, the tool will use the image as a base image for creating model images. Specifically, the tool will implicitly call `docker pull` for one of the above licensed images as specified in the tool's command line using the `--fromImage` parameter. For `JRF`, this sample specifies `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4`, and for `WLS`, the sample specifies `container-registry.oracle.com/middleware/weblogic:12.2.1.4`.

     {{% notice info %}}
   If you prefer, you can create your own base image and then substitute this image name in the WebLogic Image Tool `--fromImage` parameter throughout this sample. For example, you may wish to start with a base image that has patches applied. See [Preparing a Base Image]({{< relref "/userguide/managing-domains/domain-in-image/base-images/_index.md" >}}).
     {{% /notice %}}

1. Download the latest [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling) (WDT) and [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) (WIT) installer ZIP files to your `/tmp/mii-sample/model-images` directory. Both WDT and WIT are required to create your Model in Image container images.

   ```shell
   $ cd /tmp/mii-sample/model-images
   ```
   ```shell
   $ curl -m 120 -fL https://github.com/oracle/weblogic-deploy-tooling/releases/latest/download/weblogic-deploy.zip \
     -o /tmp/mii-sample/model-images/weblogic-deploy.zip
   ```
   ```shell
   $ curl -m 120 -fL https://github.com/oracle/weblogic-image-tool/releases/latest/download/imagetool.zip \
     -o /tmp/mii-sample/model-images/imagetool.zip
   ```

1. To set up the WebLogic Image Tool, run the following commands:

   ```shell
   $ cd /tmp/mii-sample/model-images
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
     --path /tmp/mii-sample/model-images/weblogic-deploy.zip
   ```

   Note that the WebLogic Image Tool `cache deleteEntry` command does nothing
   if the `wdt_latest` key doesn't have a corresponding cache entry. It is included
   because the WIT cache lookup information is stored in the `$HOME/cache/.metadata`
   file by default, and if the cache already
   has a version of WDT in its `--type wdt --version latest` location, then the
   `cache addInstaller` command would fail.
   For more information about the WIT cache, see the
   [WIT Cache documentation](https://github.com/oracle/weblogic-image-tool/blob/master/site/cache.md).

   These steps will install WIT to the `/tmp/mii-sample/model-images/imagetool` directory,
   plus put a `wdt_latest` entry in the tool's cache which points to the WDT ZIP file installer.
   You will use WIT and its cached reference to the WDT installer later in the sample for creating model images.

### Additional prerequisites for JRF domains

> __NOTE__: If you're using a `WLS` domain type, skip this section and continue [here]({{< relref "/samples/simple/domains/model-in-image/initial.md" >}}).

#### JRF Prerequisites Contents

 - [Introduction to JRF setups](#introduction-to-jrf-setups)
 - [Set up and initialize an infrastructure database](#set-up-and-initialize-an-infrastructure-database)
 - [Increase introspection job timeout](#increase-introspection-job-timeout)
 - [Important considerations for RCU model attributes, Domain fields, and secrets](#important-considerations-for-rcu-model-attributes-domain-fields-and-secrets)

##### Introduction to JRF setups

> __NOTE__: The requirements in this section are in addition to [Prerequisites for all domain types](#prerequisites-for-all-domain-types).

A JRF domain requires an infrastructure database, initializing this database with RCU, and configuring your domain to access this database. You must perform all these steps _before_ you create your domain.


##### Set up and initialize an infrastructure database

A JRF domain requires an infrastructure database and requires initializing this database with a schema and a set of tables for each different domain. The following example shows how to set up a database and use the RCU tool to create the infrastructure schemas for two JRF domains. The database is set up with the following attributes:

| Attribute | Value |
| --------- | ----- |
| database Kubernetes namespace | `default` |
| database Kubernetes pod | `oracle-db` |
| database image | `container-registry.oracle.com/database/enterprise:12.2.0.1-slim` |
| database password | `Oradoc_db1` |
| infrastructure schema prefixes | `FMW1` and `FMW2` (for domain1 and domain2) |
| infrastructure schema password | `Oradoc_db1` |
| database URL | `oracle-db.default.svc.cluster.local:1521/devpdb.k8s` |


1. Ensure that you have access to the database image, and then create a deployment using it:

   - Use a browser to log in to `https://container-registry.oracle.com`, select `Database -> enterprise` and accept the license agreement.

   - Get the database image:
     - In the local shell, `docker login container-registry.oracle.com`.
     - In the local shell, `docker pull container-registry.oracle.com/database/enterprise:12.2.0.1-slim`.

   - Use the sample script in `/tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-oracle-db-service` to create an Oracle database running in the pod, `oracle-db`.

     ```shell
     $ cd /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-oracle-db-service
     ```
     ```shell
     $ start-db-service.sh
     ```

     This script will deploy a database in the `default` namespace with the connect string `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`, and administration password `Oradoc_db1`.

     This step is based on the steps documented in [Run a Database](https://oracle.github.io/weblogic-kubernetes-operator/userguide/overview/database/).

     __NOTE__: If your Kubernetes cluster nodes do not all have access to the database image in a local cache, then deploy a Kubernetes `docker secret` to the default namespace with login credentials for `container-registry.oracle.com`, and pass the name of this secret as a parameter to `start-db-service.sh` using `-s your-image-pull-secret`. Alternatively, copy the database image to each local Docker cache in the cluster.  For more information, see the [Cannot pull image FAQ]({{<relref "/faq/cannot-pull-image">}}).

     **WARNING:** The Oracle Database images are supported only for non-production use. For more details, see My Oracle Support note: Oracle Support for Database Running on Docker (Doc ID 2216342.1).


2. Use the sample script in `/tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-rcu-schema` to create an RCU schema for each domain (schema prefixes `FMW1` and `FMW2`).

   Note that this script assumes `Oradoc_db1` is the DBA password, `Oradoc_db1` is the schema password, and that the database URL is `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`.

   ```shell
   $ cd /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-rcu-schema
   ```
   ```shell
   $ ./create-rcu-schema.sh -s FMW1 -i container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4
   ```
   ```shell
   $ ./create-rcu-schema.sh -s FMW2 -i container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4
   ```

   __NOTE__: If your Kubernetes cluster nodes do not all have access to the FMW infrastructure image in a local cache, then deploy a Kubernetes `docker secret` to the default namespace with login credentials for `container-registry.oracle.com`, and pass the name of this secret as a parameter to `./create-rcu-schema.sh` using `-p your-image-pull-secret`. Alternatively, copy the FMW infrastructure image to each local Docker cache in the cluster. For more information, see the [Cannot pull image FAQ]({{<relref "/faq/cannot-pull-image">}}).

   __NOTE__: If you need to drop the repository, use this command:

   ```shell
   $ drop-rcu-schema.sh -s FMW1
   ```



##### Increase introspection job timeout

The JRF domain home creation can take more time than the introspection job's default timeout. You should increase the timeout for the introspection job. Use the `configuration.introspectorJobActiveDeadlineSeconds` in your Domain to override the default with a value of at least 300 seconds (the default is 120 seconds). Note that the `JRF` versions of the Domain YAML files that are provided in `/tmp/mii-sample/domain-resources` already set this value.

##### Important considerations for RCU model attributes, Domain fields, and secrets

To allow Model in Image to access the database and OPSS wallet, you must create an RCU access secret containing the database connect string, user name, and password that's referenced from your model and an OPSS wallet password secret that's referenced from your Domain before deploying your domain.  It's also necessary to define an `RCUDbInfo` stanza in your model.

The sample includes examples of JRF models and Domain YAML files in the `/tmp/mii-sample/model-images` and `/tmp/mii-sample/domain-resources` directories, and instructions in the following sections will describe setting up the RCU and OPSS secrets.

When you follow the instructions in the samples, avoid instructions that are `WLS` only, and substitute `JRF` for `WLS` in the corresponding model image tags and Domain YAML file names.

For example, in this sample:

  - JRF Domain YAML files have an `configuration.opss.walletPasswordSecret` field that references a secret named `sample-domain1-opss-wallet-password-secret`, with `password=welcome1`.

  - JRF image models have a `domainInfo -> RCUDbInfo` stanza that reference a `sample-domain1-rcu-access` secret with appropriate values for attributes `rcu_prefix`, `rcu_schema_password`, and `rcu_db_conn_string` for accessing the Oracle database that you deployed to the default namespace as one of the prerequisite steps.

##### Important considerations for reusing or sharing OPSS tables

{{% notice warning %}}
We do not recommend that users share OPSS tables.  Extreme caution is required when sharing OPSS tables between domains.
{{% /notice %}}

When you successfully deploy your JRF Domain YAML file for the first time, the introspector job will initialize the OPSS tables for the domain using the `domainInfo -> RCUDbInfo` stanza in the WDT model plus the `configuration.opss.walletPasswordSecret` specified in the Domain YAML file. The job will also create a new domain home. Finally, the operator will also capture an OPSS wallet file from the new domain's local directory and place this file in a new Kubernetes ConfigMap.

There are scenarios when the domain needs to be recreated between updates, such as when WebLogic credentials are changed, security roles defined in the WDT model have been changed, or you want to share the same infrastructure tables with different domains.  In these scenarios, the operator needs the `walletPasswordSecret` as well as the OPSS wallet file, together with the exact information in `domainInfo -> RCUDbInfo` so that the domain can be recreated and access the same set of tables.  Without the wallet file and wallet password, you will not be able to recreate a domain accessing the same set of  tables, therefore we strongly recommend that you back up the wallet file.

To recover a domain's OPSS tables between domain restarts or to share an OPSS schema between different domains, it is necessary to extract this wallet file from the domain's automatically deployed introspector ConfigMap and save the OPSS wallet password secret that was used for the original domain. The wallet password and wallet file are needed again when you recreate the domain or share the database with other domains.

To save the wallet file, assuming that your namespace is `sample-domain1-ns` and your domain UID is `sample-domain1`:

```shell
  $ kubectl -n sample-domain1-ns \
    get configmap sample-domain1-weblogic-domain-introspect-cm \
    -o jsonpath='{.data.ewallet\.p12}' \
    > ./ewallet.p12
```

Alternatively, you can save the file using the sample's wallet utility:

```shell
  $ /tmp/mii-sample/utils/opss-wallet.sh -n sample-domain1-ns -d sample-domain1 -wf ./ewallet.p12
```
```
  # For help: /tmp/mii-sample/utils/opss-wallet.sh -?
```

__Important! Back up your wallet file to a safe location that can be retrieved later.__

To reuse the wallet file in subsequent redeployments or to share the domain's OPSS tables between different domains:

1. Load the saved wallet file into a secret with a key named `walletFile` (again, assuming that your domain UID is `sample-domain1` and your namespace is `sample-domain1-ns`):

```shell
  $ kubectl -n sample-domain1-ns create secret generic sample-domain1-opss-walletfile-secret \
     --from-file=walletFile=./ewallet.p12
```
```shell
  $ kubectl -n sample-domain1-ns label secret sample-domain1-opss-walletfile-secret \
     weblogic.domainUID=`sample-domain1`
```

Alternatively, use the sample's wallet utility:
```shell
  $ /tmp/mii-sample/utils/opss-wallet.sh -n sample-domain1-ns -d sample-domain1 -wf ./ewallet.p12 -ws sample-domain1-opss-walletfile-secret
```
```
  # For help: /tmp/mii-sample/utils/opss-wallet.sh -?
```

2. Modify your Domain JRF YAML files to provide the wallet file secret name, for example:

```yaml
  configuration:
    opss:
      # Name of secret with walletPassword for extracting the wallet
      walletPasswordSecret: sample-domain1-opss-wallet-password-secret
      # Name of secret with walletFile containing base64 encoded opss wallet
      walletFileSecret: sample-domain1-opss-walletfile-secret
```
> **Note**: The sample JRF Domain YAML files included in `/tmp/mii-sample/domain-resources` already have the above YAML file stanza.

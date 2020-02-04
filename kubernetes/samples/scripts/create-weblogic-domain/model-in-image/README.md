# Model in Image Sample

This sample demonstrates the WebLogic Kubernetes Operator "Model in Image" feature. Model in Image allows you to supply a Weblogic Deploy Tool (WDT) model that the operator automatically expands into a full domain home during runtime. This eliminates the need to pre-create a WebLogic domain home prior to deploying your domain resource.

WDT models are a convenient and succinct alternative to WebLogic configuration scripts and templates. They compactly define a WebLogic domain via yaml files, plus support for application archives. The WDT model format is described in the open source [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling) GitHub project.
 
When using `Model In Image`, you can include your WDT models, WDT archives, and the WDT install in your image. In addition, you can also provide additional models and variable files in a Kubernetes configuration map (configmap). When you deploy your domain resource, the operator will combine the WDT artifacts and then run the WDT tooling to generate the domain. Life cycle updates can be applied to the image or the configmap after initial deployment.

This sample demonstrates deploying a WebLogic servlet application within a basic `WLS` domain, an Oracle Fusion Middleware Infrastructure Java Required Files (`JRF`) domain, or a `RestrictedJRF` domain. The `JRF` domain path through the sample includes additional steps for deploying an infrastructure database and initializing the database using the Repository Creation Utility (RCU) tool. `JRF` domains may be  used by Oracle products that layer on top of WebLogic Server such as SOA, OSB, and FA. Similarly, `RestrictedJRF` domains may be used by Oracle layered products such as Oracle Communications products.

# Contents

  - [Overview of High Level Steps](#overview-of-high-level-steps)
  - [Using this Sample](#using-this-sample)
    - [Prerequisites for all domain types](#prerequisites-for-all-domain-types)
    - [Use the WebLogic Image Tool to create an image](#use-the-weblogic-image-tool-to-create-an-image)
    - [Create and deploy your Kubernetes resources](#create-and-deploy-your-kubernetes-resources)
    - [Optionally, install nginx to test the sample application](#optionally,-install-nginx-to-test-the-sample-application)
    - [Cleanup](#cleanup)
  - [Model File Naming and Loading Order](#model-file-naming-and-loading-order)
  - [Using Secrets in Model Files](#using-secrets-in-model-files)
  - [Setup Prerequisites for JRF Domains](#setup-prerequisites-for-jrf-domains)

# Overview of High-Level Steps

It is helpful to understand the following high-level flow before running the sample described in [Using this Sample](#using-this-sample):

1. Deploy the operator and ensure that it is monitoring the desired namespace.

2. Define your WDT model files.

   - You can use the `@@FILE` macro to reference your WebLogic credentials secret or other secrets. See [Using Secrets in Model Files](#using-secrets-in-model-files).

   - To control the order which WDT will use to load your model files, see [Model File Naming and Loading Order](#model-file-naming-and-loading-order).

3. Create a deployable image with WebLogic Server and WDT installed, plus optionally with your model files.
   - Optionally include all of your WDT model files in the image using the directory structure described below. To control the order which WDT will use to load your model files, see [Model File Naming and Loading Order](#model-file-naming-and-loading-order).

   - You can start with an image from [Docker Hub](https://github.com/oracle/docker-images/tree/master/OracleWebLogic) and then layer the required WDT artifacts into a new image, or you can create an image using the convenient [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool). The WebLogic Image Tool (WIT) has built-in options for embedding WDT model files, WDT binaries, WebLogic binaries install, and WebLogic patches in an image. The sample takes the (WIT) approach.

   - `Model in Image` requires the following directory structures in the image for (optional) WDT models artifacts and (required) WDT binaries. If you are not using the WebLogic Image Tool to generate your image, then you must follow the structures listed in the table below:

     | Directory                | Contents                           | Extension   |
     | ------------------------ | ---------------------------------- | ----------- |
     | /u01/wdt/models          | optional domain model yaml files   | yaml        |
     | /u01/wdt/models          | optional model variable files      | properties  |
     | /u01/wdt/models          | optional application archives      | zip         |
     | /u01/wdt/weblogic-deploy | unzipped weblogic deploy install   |             |

     Note that the WebLogic Image Tool mentioned in the previous bullet can create and populate this directory structure for you.


4. Create a WDT model config map (optional if the image supplied by step 3 already fully defines your model).

   - You can optionally create a config map containing additional model yaml and model variable property files. They will be applied during domain creation after the models found in the image directory `/u01/wdt/models`. Note that it is a best practice to label the configmap with its domainUID to help ensure that cleanup scripts can find and delete the resource. It is also a best practice to include the name of the domainUID in your configmap name so that it is unique from other domains.

   - For example, in a directory `/home/acmeuser/wdtoverride`, place additional models and variables files in a directory called `/home/acmeuser/wdtoverride` and run the following commands:

     ```
     kubectl -n sample-domain1-ns \
       create configmap domain1-wdt-config-map \
       --from-file /home/acmeuser/wdtoverride
     kubectl -n sample-domain1-ns \
       label configmap domain1-wdt-config-map \
       weblogic.domainUID=sample-domain1
     ```

   - To control the model file loading order, see [Model File Naming and Loading Order](#model-file-naming-and-loading-order).

5. Optionally create an encryption secret.

   - The `WebLogic Deploy Tool` encryption option is one of two options for encrypting sensitive information stored in a model.

     > __NOTE__: Oracle recommends storing sensitive information using Kubernetes secrets when practical instead of relying on the WDT encryption option. See [Using Secrets in Model Files](#using-secrets-in-model-files).

   - If you want to use the WDT encryption option, then you need to create a secret to store the encryption passphrase. The passphrase will be used to decrypt the model during domain creation. The secret can be named anything, but it must contain a key named `passphrase`.  Note that it is a best practice to label secrets with their domain UID to help ensure that cleanup scripts can find and delete them.

     ```
     kubectl -n sample-domain1-ns \
       create secret generic sample-domain1-wdt-encrypt-secret \
       --from-literal=passphrase=welcome1
     kubectl -n sample-domain1-ns \
       label secret sample-domain1-wdt-encrypt-secret \
       weblogic.domainUID=sample-domain1
     ```

6. Update domain resource yaml file attributes:

   - ```domainHomeSourceType``` must set to FromModel in the domain resource yaml file
   
   - Set `domainHome` to the domain home directory that will be created within the image at runtime. It must not already exist in the image. It must not include the mount path of any persistent volume. 
   
   - Set `wdtConfigMap` if you have additional models stored in a config map. For example, assuming the config map is named `domain1-wdt-config-map` as per step 4 above:
   
     ```
     wdtConfigMap : domain1-wdt-config-map
     ```

   - If your models reference Kubernetes secrets, then include them as in the following domain resource attribute as needed:
   
     ```
     configuration:
       overrides:
         secrets: [my-secret, my-other-secret]
     ```

   - Set `wdtDomainType`. Valid values are `WLS`, `JRF`, and `RestrictedJRF` where `WLS` is the default.

     ```
     wdtDomainType: "WLS"
     ```

   - Finally, if the WDT model is encrypted, then create a Kubernetes secret for its `passphrase` as described in step 5 above, and specify the secret name in your domain resource using the `wdtEncryptionPassPhrase` attribute.  For example:
     ```
     wdtEncryptionPassPhrase: 
       name: sample-domain1-wdt-encrypt-secret
     ```

7. If you are using a `JRF` domain type, see [Setup Prerequisites for JRF Domains](#setup-prerequisites-for-jrf-domains).
     
# Using this Sample

## Prerequisites for all domain types

1. The JAVA_HOME environment variable must be set and must reference a valid JDK8 installation.

2. Setup a source directory env variable `SRCDIR`, an empty test directory, and a test directory environment variable `WORKDIR`.  Specifically:

   - Store the location of the Operator source code in an environment variable `SRCDIR`.  For example:

     ```
     mkdir ~/wlopsrc
     cd ~/wlopsrc
     git clone https://github.com/oracle/weblogic-kubernetes-operator.git
     export SRCDIR=$(pwd)/weblogic-kubernetes-operator
     ```

     For additional information about obtaining the operator source, see the [Developer Guide Requirements](https://oracle.github.io/weblogic-kubernetes-operator/developerguide/requirements/).
   
   - Create an empty temporary working directory with 10g of space, and store its location in `WORKDIR`. For example:

     ```
     cd <location of empty temporary directory with 10g of space>
     export WORKDIR=$(pwd)
     ```

3. Copy all the files in this sample to the `$WORKDIR` working directory. For example:

   ```
   cp -R ${SRCDIR?}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/* ${WORKDIR?}
   ```

4. Deploy the Operator and setup the Operator to manage namespace `sample-domain1-ns`. 

   - For example, see [Quick Start](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/) up through the `PREPARE FOR A DOMAIN` step. Note that you can skip the Quick Start steps for obtaining a WebLogic image and for configuring Traefik load balancer - as instead we we will generate our own image and setup an nginx load balancer instead.
   - After you've deployed the Operater, check if it is managing namespace `sample-domain1-ns` and use helm upgrade to add this namespace if needed.  For example:
     ```
     # get the helm release name for the running operator, and use
     # this release name in place of 'my-operator-release' below
     helm ls
  
     # check if operator manages 'sample-domain1-ns'
     helm get values my-operator-release  # shows current managed namespaces
  
     # Use helm upgrade to add 'sample-domain1-ns' if needed. Note that the
     # 'set' below should include all of the namespaces the operator is
     # expected to manage.  Do not include any white-space in the namespace list.
     cd ${SRCDIR?}
     helm upgrade \
       --reuse-values \
       --set "domainNamespaces={default,my-other-ns,sample-domain1-ns}" \
       --wait \
       my-operator-release \
       kubernetes/charts/weblogic-operator
     ```

5. Choose the type of domain you're going to create: `WLS`, `JRF`, or `RestrictedJRF`, and set environment variable WDT_DOMAIN_TYPE accordingly.

   ```
   export WDT_DOMAIN_TYPE=<one of WLS, JRF, or RestrictedJRF>
   ```

6. Setup access to the base WebLogic image at the [Oracle Container Registry](http://container-registry.oracle.com).

   - Use a browser to access [Oracle Container Registry](http://container-registry.oracle.com)
   - Choose an image location
     - For `JRF` and `RestrictedJRF` domains, click on `Middleware` then click on `fmw-infrastructure`
     - For `WLS` domains, click on `Middleware` then click on `weblogic`
   - Accept the license agreement by signing on dialog box on the right 
   - Use your terminal to login into docker `docker login container-registry.oracle.com`
   - If you run the sample, then it will call `docker pull` for your base image based on the domain type:
     - For `JRF` and `RestrictedJRF`, it will pull `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3`.
     - For `WLS`, it will pull `container-registry.oracle.com/middleware/weblogic:12.2.1.3`.

   Alternatively, you can create you're own base image and override the sample's default base image name and tag by experting the BASE_IMAGE_NAME and BASE_IMAGE_TAG environment variables prior to running the sample scripts. If you want to create your own base image, see...

7. If you are using a `JRF` domain type, then this requires an RCU infrastructure database. See [Setup Prerequisites for JRF Domains](#setup-prerequisites-for-jrf-domains) to set one up. You can do this step before or after you create your final image. 

## Use the WebLogic Image Tool to create an image

An image for Model in Image must contain a WebLogic install, a WebLogic Deploy Tool install, and your WDT model files. You can use the sample `./build.sh` script build this image, which will perform the following steps for you:

  - Uses 'docker pull' to obtain a base image (see [Prerequisites for all domain types](#prerequisites-for-all-domain-types) to setup access to the base image).
  - Downloads the latest WebLogic Image Tool and WebLogic Deploy Tool.
  - Creates and populates staging directory `$WORKDIR/models`.
    - Builds the simple servlet app in `$WORKDIR/sample_app` into a WDT model application archive `./models/archive1.zip`.
    - Copies sample model files from `$WORKDIR/` to `$WORKDIR/models`. This uses a model file that is appropriate to the domain type (for example, the `JRF` domain model includes database access configuration).
  - Creates a final image named `model-in-image:v1` that layers on the base image. Specifically, it runs the WebLogic Image Tool with its 'update' option, which:
    - Builds the final image as a layer on the base image.
    - Puts a WDT install in image location `/u01/wdt/weblogic-deploy`.
    - Copies the WDT model, properties, and application archive from `$WORDKIR/models` to image location `/u01/wdt/models`.

The script expects WDT_DOMAIN_TYPE, SRCDIR, and WORKDIR to already be initialized (see [Prerequisites for all domain types](#prerequisites-for-all-domain-types)).

To run the script:

  ```
  cd ${WORKDIR?}
  ./build.sh
  ```

## Create and deploy your Kubernetes resources

To deploy the sample Operator domain and its required kubernetes resources, you can use this sample `./run_domain.sh` script which will perform the following steps for you:

  - Deletes the domain with `DomainUID` of `domain1` in namespace `sample-domain1-ns` if it already exists.
  - Creates a secret containing your WebLogic administrator username and password.
  - Creates secrets containing your RCU access URL, credentials, and prefix (these are unused unless the domain type is `JRF`).
  - Creates a config map containing an additional WDT model properties file './model1.20.properties'.
  - Generates a domain resource yaml file `k8s-domain.yaml` using `k8s-domain.yaml.template`.
  - Deploys `k8s-domain.yaml` 
  - Displays the status of the domain pods. 

The script expects WDT_DOMAIN_TYPE, SRCDIR, and WORKDIR to already be initialized (see [Prerequisites for all domain types](#prerequisites-for-all-domain-types)).

The script uses `domain1`

To run the script:

  ```
  cd $WORKDIR
  ./run_domain.sh
  ```

At the end, you will see the message `Getting pod status - ctrl-c when all is running and ready to exit`.  Once all 
the pods are up, you can ctrl-c to exit the build script.


## Optionally, install nginx to test the sample application

1. Install the nginx ingress controller in your environment.  For example:
   ```
   helm install --name acmecontroller stable/nginx-ingress \
   --namespace sample-domain1-ns \
   --set controller.name=acme \
   --set defaultBackend.enabled=true \
   --set defaultBackend.name=acmedefaultbackend \
   --set rbac.create=true
   ```
2. Install the ingress rule for the sample application:
   ```
   kubectl apply -f k8s-nginx.yaml
   ```
3. Verify ingress is running and note the `EXTERNAL-IP` that it is using:
   ```
   kubectl --namespace sample-domain1-ns get services -o wide -w acmecontroller-nginx-ingress-acme
   ```
4. Send a request to the `EXTERNAL-IP`:
   ```
   curl -kL http://EXTERNAL-IP/sample_war/index.jsp
   ```
   You should see something like:
   ```
   Hello World, you have reached server managed-server1
   ```


## Cleanup 

1. From the WebLogic Kubernetes Operator cloned root directory:
   ```
   kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh -d sample-domain1
   ```
   This deletes the domain and any related resources that are labeled with Domain UID `sample-domain1`. It leaves the namespace intact and leaves the Operator running.
2. If you setup nginx:
   ```
   kubectl delete -f k8s-nginx.yaml
   helm delete acmecontroller
   ```
3. If you setup a database:
   ```
   kubectl delete -f k8s-db-slim.yaml
   ```

# Model File Naming and Loading Order

Refer to this section if you need to control the order in which your model files are loaded. 

During domain home creation, model and property files are first loaded from directory `/u01/model_home/models` within the image and are then loaded from the optional wdt config map. The loading order within each of these locations is first determined using the convention `filename.##.yaml` and `filename.##.properties`, where `##` is a numeric number that specifies the desired order, and then is determined alphabetically as a tie-breaker. File names that don't include `.##.` sort _before_ other files as if they implicitly have the lowest possible `.##.`. If an image file and config map file both have the same name, then both files are loaded.

For example, if you have these files in the image directory `/u01/model_home/models`: 

  ```
  jdbc.20.yaml
  main-model.10.yaml
  my-model.10.yaml
  y.yaml
  ```
And you have these files in the config map:

  ```
  jdbc-dev-urlprops.10.yaml
  z.yaml
  ```
Then the combined model files list passed to the `WebLogic Deploy Tool` becomes:

  ```y.yaml,main-model.10.yaml,my-model.10.yaml,jdbc.20.yaml,z.yaml,jdbc-dev-urlprops.10.yaml```

Property files (ending in `.properties`) use the same sorting algorithm, but they are appended together into a single file prior to passing them to the `WebLogic Deploy Tool`.

# Using Secrets in Model Files

You can use wdt model `@@FILE` macros to reference the WebLogic administrator username and password that is stored in a Kubernetes secret and to optionally reference additional secrets. 

Secret names are specified in your domain resource using the `weblogicCredentialsSecret` and `configOverridesSecrets` fields, and secret mounts are at the following locations:

  |domain resource field|directory location|
  |---------------------|-------------|
  |webLogicCredentialsSecret|/weblogic-operator/secrets|
  |configOverridesSecrets|/weblogic-operator/config-overrides-secrets/SECRET_NAME|

For example:
  
  - You can reference the weblogic credentials secret via `@@FILE:/weblogic-operator/secrets/username@@` and `@@FILE:/weblogic-operator/secrets/password@@`.  
  - You can reference a config overrides secret `mysecret` with value `myvalue` via `@@FILE:/weblogic-operator/config-overrides-secrets/mysecret/myvalue@@`.


# Setup Prerequisites for JRF Domains

> __IMPORTANT__: This section is only required for demonstrating a `JRF` domain type. Skip this section if your domain type is simply a `WLS` or a `RestrictedJRF` domain.

A JRF domain requires an infrastructure database called an RCU Database, requires initializing this database, and requires configuring your domain to access this database. All of these steps must occur before you first deploy your domain. 

Furthermore, if you want to have a restarted JRF domain access updates to the infrastructure database that the domain made at an earlier time, the restarted domain must be supplied a wallet file that was obtained from a previous run of the domain.

The following steps demonstrate how to setup an infrastructure database that will work with this sample:

  - Step 1) See [Set up and initialize an RCU database](#set-up-and-initialize-an-rcu-database).
  - Step 2) See [Increase Introspection Job Timeout](#increase-introspection-job-timeout).
  - Step 3) See [Setup RCU model attributes, domain resource attributes, and secrets](#setup-rcu-model-attributes-domain-resource-attributes-and-secrets).


> __Reusing or sharing RCU tables__: When you deploy a JRF domain for the first time, the domain will further update the RCU tables from step 1 and also create a 'wallet' in the domain's local directory that enables access the domain's data in the RCU DB. To recover a domain's RCU tables between domain restarts or to share an RCU schema between different domains, it is necessary to extract this wallet from the original domain and save the OPSS key that was used for the original domain. The key and wallet are needed again when you recreate the domain or share the database with other domains. See [Reusing an RCU Database between Domain Deployments](#reusing-an-rcu-database-between-domain-deployments) for instructions.


## Set Up and Initialize an RCU Database

A JRF domain requires an infrastructure database and also requires initalizing this database with a schema and a set of tables. The following example shows how to setup a sample RCU Database and use the RCU tool to create the infrastructure schema for a JRF domain. The RCU database is setup with the following aspects so that it works with the sample: 

| Attribute | Value |
| --------- | ----- |
| database Kubernetes namespace | default |
| database Kubernetes pod | oracle-db |
| database image | container-registry.oracle.com/database/enterprise:12.2.0.1-slim |
| database password | Oradoc_db1 |
| infrastructure schema prefix | FMW1 |
| infrastructure schema password | Oradoc_db1 |
| database URL | oracle-db.default.svc.cluster.local:1521/devpdb.k8s |


1. Ensure you have access to the database image, and then deploy it:

   - Use a browser to login to `https://container-registry.oracle.com`, select `database->enterprise` and accept the license agreement.

   - Get the database image
     - In the local shell, `docker login container-registry.oracle.com`.
     - In the local shell, `docker pull container-registry.oracle.com/database/enterprise:12.2.0.1-slim`.

     > __NOTE__: If a local docker login and manual pull of `container-registry.oracle.com/database/enterprise:12.2.0.1-slim` is not sufficient (for example, if you are using a remote k8s cluster), then uncomment the imagePullSecrets stanza in '$WORKDIR/k8s-db-slim.yaml' and create the image pull secret:
       ```
       kubectl create secret docker-registry regsecret \
         --docker-server=container-registry.oracle.com \
         --docker-username=your.email@some.com \
         --docker-password=your-password \
         --docker-email=your.email@some.com 
       ```

   - Use the sample script in '$SRCDIR/kubernetes/samples/scripts/create-oracle-db-service' to create an Oracle DB running in pod 'oracle-db'.

     > __NOTE__: If your database image access requires the `regsecret` image pull secret that you optionally created above, then pass `-s regsecret` to the `start-db-service.sh` command line.

     ```
     cd $SRCDIR/kubernetes/samples/scripts/create-oracle-db-service 
     start-db-service.sh
     ```

     This script will deploy a database with URL `oracle-db.default.svc.cluster.local:1521/devpdb.k8s` and administration password `Oradoc_db1`.
 
     > __WARNING__: The Oracle Database Docker images are only supported for non-production use. For more details, see My Oracle Support note: Oracle Support for Database Running on Docker (Doc ID 2216342.1) 
     >            : All the data is gone when the database is restarted. 
   
     > __NOTE__: This step is based on the steps documented in [Run a Database](https://oracle.github.io/weblogic-kubernetes-operator/userguide/overview/database/).

2. Use the sample script in `WORDIR/kubernetes/samples/scripts/create-rcu-schema` to create the RCU schema with schema prefix `FMW1`.

   Note that this script assumes `Oradoc_db1` is the dba password, `Oradoc_db1` is the schema password, and that the database URL is `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`.

   ```
   cd $SRCDIR/kubernetes/samples/scripts/create-rcu-schema 
   ./create-rcu-schema.sh -s FMW1 -i container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3
   ```

3. __NOTE__:  If you need to drop the repository, you can use this command in the terminal:

   ```
   drop-rcu-schema.sh -s FMW1
   ```


## Increase Introspection Job Timeout

Since JRF domain home creation takes considerable time the first time its created, and since model-in-image creates your domain home for you using the introspection job, you should increase the timeout for the introspection job. Use the command `kubectl -n sample-weblogic-operator-ns edit configmap weblogic-operator-cm` to open up an editor for the operator settings, and then use this editor to add parameter `introspectorJobActiveDeadlineSeconds` with a value of at least 300 seconds (default is 120 seconds).  

TBD These instructions are temporary while we come up with a better way to increase the value.


## Setup RCU model attributes, domain resource attributes, and secrets

To allow model-in-image to access the RCU database and OPSS wallet, it's necessary to setup an RCU access secret and an OPSS secret before deploying your domain. It's also necessary to define an `RCUDbInfo` stanza in your model. The sample already sets up all of these for you.  See:

| Sample file | Description |
| `create_opss_key_secret.sh` | Defines secret `sample-domain1-opss-key-passphrase-secret` with `passphrase=welcome1` |
| `create_rcu_access_secret.sh` | Defines secret `sample-domain1-rcu-access` with appropriate values for attributes `rcu_prefix`, `rcu_schema_password`, `rcu_admin_password`,  and `rcu_db_conn_string` |
| `model1.yaml.jrf` | Populates the `domainInfo -> RCUDbInfo` stanza `rcu_prefix`, `rcu_schema_password`, `rcu_admin_password`,  and `rcu_db_conn_string` attributes by referencing their locations in the `sample-domain1-rcu-access` secret. |
| `k8s-domain.yaml.template` | Ensures the domain mounts the OPSS key secret by setting the domain resource `opssWalletSecret` attribute to `sample-domain1-rcu-access`, and ensures the domain mounts the RCU access secret `sample-domain1-rcu-access` for reference by WDT model macros by setting the domain resource `configOverridesSecrets` attribute. |

> __NOTE__: This step is for information purposes only. Do not run the above sample files directly. The sample's main build and run scripts will run them for you.

## Reusing an RCU Database between Domain Deployments

When you deploy a JRF domain for the first time, the domain will add itself to its RCU database tables, and also create a 'wallet' in the domain's local directory that enables access to the domain's data in the RCU DB. This wallet is encrypted using an OPSS key passphrase that you supply to the domain using a secret.

If it is important to reuse or share the same database and data between deployments, then it is also important locate and preserve its OPSS key and wallet. An OPSS key and wallet allows a JRF deployment to access a FMW infrastructure database that has already been initialized and used before.

When a domain is first deployed, the WebLogic Kubernetes Operator will copy its OPSS wallet from the domain home and store it in the domain's introspect domain configmap. For a domain that has been created using model-in-image, here has how to export a wallet for reuse:

    ```
    kubectl -n MY_NAMESPACE \
      get configmap MY_DOMAIN_UID-weblogic-domain-introspect-cm \
      -o jsonpath='{.data.ewallet\.p12}' \
      > ewallet.p12
    ```

To reuse the wallet, create a secret that contains the OPSS key you specified in the original domain and make sure that your domain resource `opssWalletSecret` attribute names this secret. Here's sample code for deploying the secret that assumes the wallet is in local file 'ewallet.p12' and that the secret passphrase is `welcome1`:

    ```
    kubectl -n sample-domain1-ns \
      create secret generic sample-domain1-opss-key-passphrase-secret \
      --from-literal=passhrase=welcome1 
      --from-file=ewallet.p12
    kubectl -n sample-domain1-ns \
      label secret sample-domain1-opss-key-passphrase-secret \
      weblogic.domainUID=sample-domain1
    ```

# Contents

TBD

# About the Model in Image Sample

## Introduction

This sample demonstrates the WebLogic Kubernetes Operator "Model in Image" feature. It demonstrates:

  - Using the WebLogic Image Tool to create a Docker image that contains a WebLogic install, a WebLogic Deploy Tool (WDT) install, a Java EE servlet application contained within a WDT archive, and a WebLogic domain that's defined using a WDT model file.
  - Modifying the WDT model within the Docker image using a WDT model file that's supplied using Kubernetes config map.
  - Deploying the model image, model config map, domain resource, and corresponding secrets that define usernames, passwords, and URL values for the model and its domain resource. 
  - Deploying and accessing a Traefik load balancer that redirects http protocol calls to its Java EE servlet application.


## Domain Types Supported by this Sample

This sample supports deploying the three types of domain supported by Model in Image: a standard `WLS` domain, an Oracle Fusion Middleware Infrastructure Java Required Files (`JRF`) domain, or a `RestrictedJRF` domain. 

The `JRF` domain path through the sample includes additional steps for deploying an infrastructure database and initializing the database using the Repository Creation Utility (RCU) tool. `JRF` domains may be  used by Oracle products that layer on top of WebLogic Server such as SOA, OSB, and FA. Similarly, `RestrictedJRF` domains may be used by Oracle layered products such as Oracle Communications products.

# References

- TBD Model-in-image documentation.
- TBD WDT documentation.
- TBD WIT documentation.


# Using this Sample

## Prerequisites for all domain types

1. The JAVA_HOME environment variable must be set and must reference a valid JDK8 installation. (JAVA_HOME is used by the WebLogic Image Tool.)

2. Set a source directory env variable `SRCDIR` that references the parent of the Operator source tree. For example:

   ```
   mkdir ~/wlopsrc
   cd ~/wlopsrc
   git clone https://github.com/oracle/weblogic-kubernetes-operator.git
   export SRCDIR=$(pwd)/weblogic-kubernetes-operator
   ```

   For additional information about obtaining the operator source, see the [Developer Guide Requirements](https://oracle.github.io/weblogic-kubernetes-operator/developerguide/requirements/).

3. Create a sample directory env variable `SAMPLEDIR` that references this sample's directory:

   ```
   export SAMPLEDIR=${SRCDIR}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/
   ```

4. Create an empty temporary working directory with 10g of space, and store its location in the `WORKDIR` environment variable. For example:
   
   ```
   cd <location of empty temporary directory with 10g of space>
   export WORKDIR=$(pwd)
   ```

5. Deploy the WebLogic Operator and setup the Operator to manage namespace `sample-domain1-ns`. Optionally also deploy a Traefik load balancer that manages the same namespace. For example:

   - Follow the same steps as [Quick Start](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/) up through the `PREPARE FOR A DOMAIN` step.
   - Deploying the Traefik load balancer is optional, but is a prerequisite for testing the web-app that's deployed to WebLogic as part of this sample.
   - Note that you can skip the Quick Start steps for obtaining a WebLogic image as you will be creating your own Docker image.


6. Choose the type of domain you're going to create: `WLS`, `JRF`, or `RestrictedJRF`, and set environment variable WDT_DOMAIN_TYPE accordingly. The default is `WLS`.

   ```
   export WDT_DOMAIN_TYPE=<one of WLS, JRF, or RestrictedJRF>
   ```

7. Setup access to this sample's base WebLogic image at the [Oracle Container Registry](http://container-registry.oracle.com).

   - Use a browser to access [Oracle Container Registry](http://container-registry.oracle.com)
   - Choose an image location
     - For `JRF` and `RestrictedJRF` domains, click on `Middleware` then click on `fmw-infrastructure`
     - For `WLS` domains, click on `Middleware` then click on `weblogic`
   - Accept the license agreement by signing on dialog box on the right 
   - Use your terminal to locally login into docker `docker login container-registry.oracle.com`
   - When you later run the sample, it will call `docker pull` for your base image based on the domain type:
     - For `JRF` and `RestrictedJRF`, it will pull `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3`.
     - For `WLS`, it will pull `container-registry.oracle.com/middleware/weblogic:12.2.1.3`.

   Alternatively, you can create you're own base image and override the sample's default base image name and tag by exporting the BASE_IMAGE_NAME and BASE_IMAGE_TAG environment variables prior to running the sample scripts. If you want to create your own base image, see TBD.

8. If you are using a `JRF` domain type, then this requires an RCU infrastructure database. See [Setup Prerequisites for JRF Domains](#setup-prerequisites-for-jrf-domains) to set one up. You can do this step before or after you create your final image. 

## Use the WebLogic Image Tool to create an image

An image for Model in Image must contain a WebLogic install, a WebLogic Deploy Tool install, and your WDT model files. You can use the sample `./build.sh` script to build this image, which will perform the following steps for you:

  - Uses 'docker pull' to obtain a base image (see [Prerequisites for all domain types](#prerequisites-for-all-domain-types) to setup access to the base image). 
  - Downloads the latest WebLogic Image Tool and WebLogic Deploy Tool to WORKDIR.
  - Creates and populates staging directory `$WORKDIR/models` that contains your WDT model files and WDT application archive.
    - Builds  simple servlet app in `$SAMPLEDIR/sample_app` into a WDT model application archive `$WORKDIR/models/archive1.zip`.
    - Copies sample model files from `$SAMPLEDIR/model-in-image` to `$WORKDIR/models`. This uses a model file that is appropriate to the domain type (for example, the `JRF` domain model includes database access configuration).
  - Uses the downloaded WebLogic Image Tool and the `$WORKDIR/models` staging directory to creates a final image named `model-in-image:v1` that layers on the base image. Specifically, it runs the WebLogic Image Tool with its 'update' option, which:
    - Builds the final image as a layer on the base image.
    - Puts a WDT install in image location `/u01/wdt/weblogic-deploy`.
    - Copies the WDT model, properties, and application archive from `$WORDKIR/models` to image location `/u01/wdt/models`.

The script expects JAVA_HOME, WDT_DOMAIN_TYPE, and WORKDIR to already be initialized (see [Prerequisites for all domain types](#prerequisites-for-all-domain-types)).

To run the script:

  ```
  $SAMPLEDIR/build.sh
  ```

TBD Add note about remote k8s clusters.  Make sure there's support for a secret in the template file.

## Create and deploy your Kubernetes resources

To deploy the sample Operator domain and its required kubernetes resources, you can use this sample's `$SAMPLEDIR/run_domain.sh` script which will perform the following steps for you:

  - Deletes the domain with `DomainUID` of `domain1` in namespace `sample-domain1-ns` if it already exists.
  - Creates a secret containing your WebLogic administrator username and password.
  - Creates a secret containing your Model in Image encryption password.
  - Creates secrets containing your RCU access URL, credentials, and prefix (these are unused unless the domain type is `JRF`).
  - Creates a config map containing an additional WDT model properties file '$SAMPLEDIR/model1.20.properties'.
  - Generates a domain resource yaml file `$WORKDIR/k8s-domain.yaml` using `$SAMPLEDIR/k8s-domain.yaml.template`.
  - Deploys `k8s-domain.yaml` 
  - Displays the status of the domain pods. 

The script expects WDT_DOMAIN_TYPE and WORKDIR to already be initialized (see [Prerequisites for all domain types](#prerequisites-for-all-domain-types)).

To run the script:

  ```
  $SAMPLEDIR/run_domain.sh
  ```

At the end, you will see the message `Getting pod status - ctrl-c when all is running and ready to exit`. You should then see a WebLogic admin server and two managed server pods start. Once all the pods are up, you can ctrl-c to exit the build script.


## Optionally test the sample application

1. Ensure Traefik has been installed and is servicing external port 30305 as per [Prerequisites for all domain types](#prerequisites-for-all-domain-types).

2. Create a Kubernetes ingress for the domain's WebLogic cluster in the domain's namespace by using the sample Helm chart:

   For Helm 2.x:

   ```
   cd $SRCDIR
   $ helm install kubernetes/samples/charts/ingress-per-domain \
     --name sample-domain1-ingress \
     --namespace sample-domain1-ns \
     --set wlsDomain.domainUID=sample-domain1 \
     --set traefik.hostname=sample-domain1.org
   ```

   For Helm 3.x:

   ```
   cd $SRCDIR
   helm install sample-domain1-ingress kubernetes/samples/charts/ingress-per-domain \
    --namespace sample-domain1-ns \
    --set wlsDomain.domainUID=sample-domain1 \
    --set traefik.hostname=sample-domain1.org
   ```

   This creates an Kubernetes ingress that helps route http traffic from the Traefik load balancer's external port 30305 to the WebLogic domain's 'cluster-1' cluster's 8001 port. Note that the wdt config map in this sample changes the cluster's port from 9001 to 8001 (9001 is the original port configured using the WDT model defined within in the image).

3. Send a web application request to the load balancer:

   ```
   curl -H 'host: sample-domain1.org' http://$(hostname).$(dnsdomainname):30305/sample_war/index.jsp
   ```

   You should see something like:

   ```
   Hello World, you have reached server managed-server1
   ```

   Note: If you're running on a remote k8s cluster, then substitute `$(hostname).$(dnsdomainname)` with an external address suitable for contacting the cluster.

4. Send a ready app request to the load balancer (the 'ready app' is a built-in WebLogic application):

   ```
   curl -v -H 'host: sample-domain1.org' http://$(hostname).$(dnsdomainname):30305/weblogic/ready
   ```

   You should see something like:


   ```
   * About to connect() to myhost.my.dns.domain.name port 30305 (#0)
   *   Trying 100.111.142.32...
   * Connected to myhost.my.dns.domain.name (100.111.142.32) port 30305 (#0)
   > GET /weblogic/ready HTTP/1.1
   > User-Agent: curl/7.29.0
   > Accept: */*
   > host: sample-domain1.org
   > 
   < HTTP/1.1 200 OK
   < Content-Length: 0
   < Date: Mon, 09 Mar 2020 20:40:37 GMT
   < Vary: Accept-Encoding
   < 
   * Connection #0 to host myhost.my.dns.domain.name left intact
   ```

   Note: If you're running on a remote k8s cluster, then substitute `$(hostname).$(dnsdomainname)` with an external address suitable for contacting the cluster.

## Cleanup 

1. Delete the domain resource. 
   ```
   $SRCDIR/kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh -d sample-domain1
   ```
   This deletes the domain and any related resources that are labeled with Domain UID `sample-domain1`. It leaves the namespace intact, leaves the Operator running, leaves the load balancer running (if installed), and leaves the database running (if installed).

2. If you set up the Traefik load balancer:

   ```
   helm delete --purge sample-domain1-ingress
   helm delete --purge traefik-operator
   kubectl delete namespace traefik
   ```

3. If you setup a database:
   TBD update this to reference JRF RCU DB sample cleanup instructions
   ```
   kubectl delete -f k8s-db-slim.yaml
   ```

4. Delete the WebLogic operator and its namespace:
   ```
   helm delete --purge sample-weblogic-operator
   kubectl delete namespace sample-weblogic-operator-ns
   ```

5. Delete the domain's namespace:
   ```
   kubectl delete namepsace sample-domain1-ns
   ```

# Setup Prerequisites for JRF Domains

> __IMPORTANT__: This section is only required for demonstrating a `JRF` domain type. Skip this section if your domain type is simply a `WLS` or a `RestrictedJRF` domain.

A JRF domain requires an infrastructure database called an RCU Database, requires initializing this database, and requires configuring your domain to access this database. All of these steps must occur before you first deploy your domain. 

Furthermore, if you want to have a restarted JRF domain access updates to the infrastructure database that the domain made at an earlier time, the restarted domain must be supplied a wallet file that was obtained from a previous run of the domain.

The following steps demonstrate how to setup an infrastructure database that will work with this sample:

  - Step 1) See [Set up and initialize an RCU database](#set-up-and-initialize-an-rcu-database).
  - Step 2) See [Increase Introspection Job Timeout](#increase-introspection-job-timeout).
  - Step 3) See [Setup RCU model attributes, domain resource attributes, and secrets](#setup-rcu-model-attributes-domain-resource-attributes-and-secrets).
  - Step 4) See [Reusing or sharing RCU tables]#reusing-or-sharing-RCU-tables



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

TBD Move most of the following directions to the create-oracle-db-service sample README 

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

2. Use the sample script in `SRCDIR/kubernetes/samples/scripts/create-rcu-schema` to create the RCU schema with schema prefix `FMW1`.

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

TBD Replace these instructions with instructions for using the new domain resource attribute.


## Setup RCU model attributes, domain resource attributes, and secrets

To allow model-in-image to access the RCU database and OPSS wallet, it's necessary to setup an RCU access secret and an OPSS secret before deploying your domain. It's also necessary to define an `RCUDbInfo` stanza in your model. The sample already sets up all of these for you.  See:

| Sample file | Description |
| `run_domain.sh` | Defines secret `sample-domain1-opss-wallet-password-secret` with `password=welcome1` |
| `run_domain.sh` | Defines secret `sample-domain1-rcu-access` with appropriate values for attributes `rcu_prefix`, `rcu_schema_password`, `rcu_admin_password`,  and `rcu_db_conn_string` |
| `model1.yaml.jrf` | Populates the `domainInfo -> RCUDbInfo` stanza `rcu_prefix`, `rcu_schema_password`, `rcu_admin_password`,  and `rcu_db_conn_string` attributes by referencing their locations in the `sample-domain1-rcu-access` secret. The `build.sh` script uses this model instead of `model.yaml.wls` when the source domain type is `JRF`. |
| `k8s-domain.yaml.template` | Ensures the domain mounts the OPSS key secret by setting the domain resource `configuration.opss.walletPasswordSecret` attribute to `sample-domain1-opss-wallet-password-secret`, and ensures the domain mounts the RCU access secret `sample-domain1-rcu-access` for reference by WDT model macros by setting the domain resource `configuration.secrets` attribute. |

> __NOTE__: This step is for information purposes only. Do not run the above sample files directly. The sample's main build and run scripts will run them for you.

## Reusing or sharing RCU tables

Note that when you succesfully deploy your JRF domain resource for the first time, the introspector job will initialize the RCU tables for the domain using the `domainInfo -> RCUDbInfo` stanza in the WDT model plus the `configuration.opss.walletPasswordSecret` specified in the domain resource. The job will also create a new domain home. Finally, the operator will also capture an OPSS wallet file from the new domain's local directory and place this file in a new Kubernetes config map. 

To recover a domain's RCU tables between domain restarts or to share an RCU schema between different domains, it is necessary to extract this wallet file from the config map and save the OPSS wallet password secret that was used for the original domain. The wallet password and wallet file are needed again when you recreate the domain or share the database with other domains.

TBD add instructions for modifying the domain resource in the sample to specify a wallet file, and the commands for extracting the wallet plus deploying the wallet as a secret, (also decide whether to keep the 'save ewallet' script or updated it.)

See TBD [Reusing an RCU Database between Domain Deployments](#reusing-an-rcu-database-between-domain-deployments) for instructions.

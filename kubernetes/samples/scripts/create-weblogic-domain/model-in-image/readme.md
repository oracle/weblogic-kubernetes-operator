# Model in Image Sample

This sample demonstrates specifying a Weblogic Deploy Tool (WDT) model for a domain resource so that, when the domain resourced is deployed, the model is used to generate a domain home. This is an alternative to pre-creating a WebLogic domain home prior to deploying your domain resource. WDT models are a convenient and succinct alternative to WebLogic configuration scripts, plus also allow you to bundle your applications in an application archive. The WDT model format is described in [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling).

This sample demonstrates a basic `WLS` domain, a `JRF` domain with a database, or a `RestrictedJRF` domain with a database.  For the `JRF` and `RestrictedJRF` domains, 

# Contents

  - [Overview of High Level Steps](#overview-of-high-level-steps)
  - [Model File Naming and Loading Order](#model-file-naming-and-loading-order)
  - [Using Secrets in Model Files](#using-secrets-in-model-files)
  - [Using this Sample](#using-this-sample)
    - [Prerequisites for all domain types](#prerequisites-for-all-domain-types)
    - [Use the WebLogic Image Tool to create an image](#use-the-weblogic-image-tool-to-create-an-image)
    - [Setup prerequisites for JRF and RestrictedJRF domains](#setup-prerequisites-for-jrf-and-restrictedjrf-domains)
    - [Create and deploy your Kubernetes resources](#create-and-deploy-your-kubernetes-resources)
    - [Optionally, install nginx to test the sample application](#optionally,-install-nginx-to-test-the-sample-application)
    - [Cleanup](#cleanup)

# Overview of High Level Steps

1. Deploy the operator and ensure that it's monitoring the desired namespace.

2. Define your WDT model files.

   - You can use the `@@FILE` macro to reference your WebLogic credentials secret or other secrets. See [Using Secrets in Model Files](#using-secrets-in-model-files).

3. Create a deployable image with WebLogic Server and WDT installed, plus optionally, your model files.
   - Optionally include all of your WDT model files in the image using the directory structure described below.
   - You can start with the image from [Docker Hub](https://github.com/oracle/docker-images/tree/master/OracleWebLogic) or create one using the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool). The WebLogic Image Tool has built-in options for embedding WDT model files, a WDT install, a WebLogic Install, and WebLogic patches in an image.
   - In the image, create the following structures and place the `WebLogic Deploy Tool``` artifacts

     | directory | contents | extension |
     |-----------|----------|-----------|
     | /u01/wdt/models| optional domain model yaml files | yaml |
     |                | optional model variable files | properties |
     |                | optional application deployment archive | zip |
     | ---------------| -----------| ---------|
     | /u01/wdt/weblogic-deploy | unzipped weblogic deploy installer | |

   - To control the model file loading order, see [Model File Naming and Loading Order](#model-file-naming-and-loading-order).

4. Create a WDT model config map (optional if step 3 fully defines your model).

   - You can optionally create a config map containing additional model yaml and model variable property files. They will be applied during domain creation after the models found in the image directory `/u01/wdt/models`. Note that it's a best practice to label the configmap with its domainUID to help ensure that cleanup scripts can find and delete the resource. 

   - For example, in a directory ```/home/acmeuser/wdtoverride```, place additional models and variables files in a directory called `/home/acmeuser/wdtoverride` and run the following commands:

     ```
     kubectl -n sample-domain1-ns \
       create configmap sample-domain1-wdt-config-map \
       --from-file /home/acmeuser/wdtoverride
     kubectl -n sample-domain1-ns \
       label configmap sample-domain1-wdt-config-map \
       weblogic.domainUID=sample-domain1
     ```

   - To control the model file loading order, see [Model File Naming and Loading Order](#model-file-naming-and-loading-order).

5. Optionally create an encryption secret

   - The ```WebLogic Deploy Tool``` encryption option is one of two options for encrypting sensitive information that's stored in a model.

     > __NOTE__: Many (most? TBD) models will prefer to store sensitive information using Kubernetes secrets instead of using the WDT encryption option.

   - If you want to use the WDT encryption option, then you need to create a secret to store the encryption passphrase. The passphrase will be used to decrypt the model during domain creation. The secret can be named anything but it must contain a key named ```wdtpassword```.  Note that it's a best practice to label secrets with their domain UID to help ensure that cleanup scripts can find and delete them.

     ```
     kubectl -n sample-domain1-ns \
       create secret generic sample-domain1-wdt-secret \
       --from-literal=wdtpassword=welcome1
     kubectl -n sample-domain1-ns \
       label secret sample-domain1-wdt-secret \
       weblogic.domainUID=sample-domain1
     ```

6. Update the domain resource yaml file 

   - If you have additional models stored in a config map, have encrypted your model(s) using WDT encryption, or the models reference Kubernetes secrets, then include the following keys to the domain resource yaml file as needed:
   
     ```
     wdtConfigMap : wdt-config-map
     wdtConfigMapSecret : sample-domain1-wdt-secret
     configOverrideSecrets: [my-secret, my-other-secret]
     ```

   - In addition, define a `WDT_DOMAIN_TYPE` environment variable that specifies a domain type if it is not `WLS`. Valid values are `WLS`, `JRF`, and `RestrictedJRF`.

     ```
       serverPod:
         env:
         - name: WDT_DOMAIN_TYPE
           value: "WLS|JRF|RestrictedJRF"
     ```

   TBD Should the domain type eventually be defined via a domain attribute instead of as a WDT_DOMAIN_TYPE env var?
   TBD Should the encryption secret attribute be renamed `wdtEncryptionSecret`?  Reason for the rename:  it presumably can also be used to decrypt a wdt model that's stored in the image.
   TBD Move the settings for this POC to 'experimental'.

# Model File Naming and Loading Order

During domain home creation, model and property files are first loaded from directory ```/u01/model_home/models``` within the image and are then loaded from the optional wdt config map.  

The loading order within each of these locations is first determined using the convention ```filename.##.yaml``` and ```filename.##.properties```, where ```##``` is a numeric number that specifies the desired order, and then is determined alphabetically as a tie-breaker. File names that don't include ```.##.``` sort _before_ other files as if they implicitly have the lowest possible ```.##.```. If an image file and config map file both have the same name, then both files are loaded.

TBD Verify with Johnny that file names without a ## sort first.

For example, if you have these files in the image directory ```/u01/model_home/models```: 

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
Then the combined model files list passed to the ```WebLogic Deploy Tool``` becomes:

  ```y.yaml,main-model.10.yaml,my-model.10.yaml,jdbc.20.yaml,z.yaml,jdbc-dev-urlprops.10.yaml```

Property files (ending in `.properties`) use the same sorting algorithm, but they are appended together into a single file prior to passing them to the ```WebLogic Deploy Tool```.

# Using Secrets in Model Files

You can use wdt model `@@FILE` macros to reference the WebLogic administrator username and password that's stored in a Kubernetes secret and to optionally reference additional secrets. 

Secret names are specified in your domain resource using the `weblogicCredentialsSecret` and `configOverridesSecrets` fields, and secret mounts are at the following locations:

  |domain resource field|directory location|
  |---------------------|-------------|
  |webLogicCredentialsSecret|/weblogic-operator/secrets|
  |configOverridesSecrets|/weblogic-operator/config-overrides-secrets/SECRET_NAME|

For example:
  
  - You can reference the weblogic credentials secret via `@@FILE:/weblogic-operator/secrets/username@@` and `@@FILE:/weblogic-operator/secrets/password@@`.  
  - You can reference a config overrides secret `mysecret` with value `myvalue` via `@@FILE:/weblogic-operator/config-overrides-secrets/mysecret/myvalue@@`.

TBD It looks like https://github.com/oracle/weblogic-deploy-tooling doesn't document ```@@FILE```.

TBD Users will predictably want access to some env vars that are predefined in the introspector job, including DOMAIN_UID, LOG_HOME, DOMAIN_HOME, and DATA_HOME. Maybe WDT should be extended to handle them.

# Using this Sample

## Prerequisites for all domain types

1. Deploy the Operator and setup the Operaator to managee namespace `sample-domain1-ns`. 
   - For example, see [Quick Start](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/) up through the `PREPARE FOR A DOMAIN` step. Note that you can skip the Quick Start steps for obtaining a WebLogic image and for configuring Traefik load balancer - as instead we we will generate our own image and setup an nginx load balancer instead.
   - If you've already deployed the Operater, you can use `helm get values my-operator-release` to check if its managing namespace `sample-domain1-ns` and use helm upgrade to add this namespace if needed.  For example:
   ```
   # get the helm release name for the running operator
   helm ls

   # check if operator manages `sample-domain1-ns`
   helm get values my-operator-release  # shows current managed namespaces

   # Use helm upgrade to add `sample-domain1-ns`. Note that the 'set' should
   # include all of the namespaces the operator is expected to manage.
   cd OPERATOR_SRC_DIRECTORY
   helm upgrade \
     --reuse-values \
     --set "domainNamespaces={default, my-other-ns, sample-domain1-ns}" \
     --wait \
     my-operator-release \
     kubernetes/charts/weblogic-operator
   ```

2. Create a temporary directory with 10g of space. We will call this our 'working directory'. Store the working directory in an environment variable called WORKDIR:

   ```
   cd <location of temporary directory>
   export WORKDIR=$(pwd)
   ```

4. Copy all the files in this sample to the working directory (substitute your Operator source location for SRCDIR).

   ```
   cd SRCDIR/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/model-in-image
   cp -R * ${WORKDIR}
   ```

5. Choose the type of domain you're going to create. `WLS`, `JRF`, or `RestrictedJRF`. Note that a `JRF` and `RestrictedJRF` will require a different installer plus extra steps for database setup (we will describe these steps later).

6. Obtain JRE and the appropriate WebLogic installers from [edelivery.oracle.com](https://edelivery.oracle.com)
   - JRE
     - Obtain `Oracle SERVER JRE 1.8.0.221 media upload for Linux x86-64`
       - search for `Oracle JRE`
       - click on `JRE 1.8.0_221` to add it to the shopping cart
       - click on `V982783-01.zip` to download the zip files
   - WebLogic installer for domain type `WLS`
     - Obtain `Oracle Fusion Middleware 12c (12.2.1.3.0) WebLogic Server and Coherence`
     - this is recommended for `WLS` domains, and isn't needed for `JRF` and `RestrictedJRF` domains
       - search for `Oracle WebLogic Server` in [edelivery.oracle.com](https://edelivery.oracle.com)
       - click on `Oracle WebLogic Server 12.2.1.3.0 (Oracle WebLogic Server Enterprise Edition)`
       - click on `Checkout`
       - click `continue` and accept license agreement
       - click on `V886243-01.zip` to download the zip files
   - WebLogic installer for domain type `JRF` or `RestrictedJRF`
     - Obtain `Oracle Fusion Middleware 12c (12.2.1.3.0) Infrastructure`
     - this is required for `JRF` and `RestrictedJRF` domains, and isn't recommended for `WLS` domains
       - search for `Oracle Fusion Middleware` in [edelivery.oracle.com](https://edelivery.oracle.com)
       - click on `Oracle Fusion Middleware 12c Infrastructure 12.2.1.3.0)`
       - click on `Checkout`
       - click `continue` and accept license agreement
       - click on `V886246-01.zip` to download the zip files

7. Copy the installers to the working directory `${WORKDIR}` (V982783-01.zip, V886243-01.zip or V886246-01.zip).

## Use the WebLogic Image Tool to create an image

This image will contain a WebLogic and a WebLogic Deploy Tool install, as well as your WDT model files.

You can use this sample's `./build.sh` script, which will perform the following steps for you:

  - Downloads the latest WebLogic Image Tool and WebLogic Deploy Tool
  - Creates a base image named `model-in-image:x0` that contains a JRE, a WLS install, and required patch(es)
    - Uses the WebLogic Image Tool's 'create' option
    - Uses the JRE and WLS installers downloaded during the pre-requisites step
  - Builds a simple servlet app and puts it in WDT model application archive `models/archive1.zip`
  - Create a final image named `model-in-image:x1` that layers on the base image
    - Uses the WebLogic Image Tool's 'update' option
    - Installs WDT using the installer you downloaded to image location TBD
    - Copies the WDT application archive in `models/archive1.zip` to image location TBD
    - Copies the WDT model and properties files location in `models/*` to image location TBD
      - Uses the model file that's appropriate to the model type (for example, the `JRF` and `RestrictedJRF` domain model defines database access configuration)

Here's how to run the script:

  ```
  cd $WORKDIR
  ./build.sh $(pwd) <oracle support id capable of downloading patches> <oracle support password> <domain type: WLS|RestrictedJRF|JRF>
  ```

TBD: Imagine doing a live demo of this - you'd need to show everyone your SSO! The script should provide a way to prompt for the support id and password, and suppress console echo for the latter. Ideally, it should also provide a way to have these come from some sort of wallet.

TBD: The JRF/RestrictedJRF models contain hard-coded RCU login/URL information. This should be moved to a secret and accessed via the @@FILE macro instead.

## Setup prerequisites for JRF and RestrictedJRF domains

_This step is only required for demonstrating `JRF` and `RestrictedJRF` domains. Skip to the next step [Create and deploy your Kubernetes resources](create-and-deploy-your-kubernetes-resources) if your domain type is simply 'WLS'._

A JRF domain requires an infrastructure database.  This example shows how to setup a sample database and use the RCU tool to create the infrastructure schema.  This example depends on the WebLogic base image that was created in the previous step.

TBD There's a recent comprehensive RCU sample in  kubernetes/samples/scripts/create-rcu-schema, 
so see if we can leverage it instead of rolling our own below.  It looks like Pani is a contact 
per his pull https://github.com/oracle/weblogic-kubernetes-operator/pull/1238/files 

1. Increase the introspection job timeout

   Since JRF domain creation takes considerable time, you should increase the timeout for the introspection job.

   ```
   kubectl -n <operation namespace> edit configmap weblogic-operator-cm 
   ```

   and add the parameter ```introspectorJobActiveDeadlineSeconds```  default is 120s.  Use 300s to start with.

TBD Describe how to tune the deadline via helm, follow example of logLevel, etc.
TBD Should we modify operator to increase this timeout default?


2. Ensure you have access to the database image:
   - Use a browser to login to `https://container-registry.oracle.com`, select `database->enterprise` and accept the license agreement.
   - In the local shell, `docker login container-registry.oracle.com`.
   - In the local shell, `docker pull container-registry.oracle.com/database/enterprise:12.2.0.1-slim`.

   > __NOTE__: If a local docker login and manual pull of `container-registry.oracle.com/database/enterprise:12.2.0.1-slim` is not sufficient (for example, if you are using a remote k8s cluster), then uncomment the imagePullSecrets stanza in '$WORKDIR/k8s-db-slim.yaml' and create the image pull secret:
     ```
     kubectl create secret docker-registry regsecret \
       --docker-server=container-registry.oracle.com \
       --docker-username=your.email@some.com \
       --docker-password=your-password \
       --docker-email=your.email@some.com \
       -n sample-domain1-ns
     ```

   > __WARNING__: The Oracle Database Docker images are only supported for non-production use.  For more details, see My Oracle Support note: Oracle Support for Database Running on Docker (Doc ID 2216342.1) 

TBD: Reference the 'database.md' 
      
3. Deploy a database using:
   ```
   kubectl apply -f k8s-db-slim.yaml
   ```

4. Start an interactive terminal inside a WebLogic pod by:
   ```
   kubectl run rcu -i --tty  --image model-in-image:x0 --restart=Never -- sh
   ```

5. Create the rcu schema using the following command. Note that `Oradoc_db1` is the dba password and `welcome1` is the schema password:
   ```
   /u01/oracle/oracle_common/bin/rcu \
     -silent \
     -createRepository \
     -databaseType ORACLE \
     -connectString  oracle-db.sample-domain1-ns.svc.cluster.local:1521/pdb1.k8s \
     -dbUser sys \
     -dbRole sysdba \
     -useSamePasswordForAllSchemaUsers true \
     -selectDependentsForComponents true \
     -schemaPrefix FMW1 \
     -component MDS \
     -component IAU \
     -component IAU_APPEND \
     -component IAU_VIEWER \
     -component OPSS  \
     -component WLS  \
     -component STB <<EOF
   Oradoc_db1
   welcome1
   EOF
   ```

6. Type ctrl-d to exit the terminal pod.

7. Delete the terminal pod by `kubectl delete pod rcu`.

8. __NOTE__:  If you need to drop the repository, you can use this command in the terminal:

   ```
   /u01/oracle/oracle_common/bin/rcu \
     -silent \
     -dropRepository \
     -databaseType ORACLE \
     -connectString  oracle-db.sample-domain1-ns.svc.cluster.local:1521/pdb1.k8s \
     -dbUser sys \
     -dbRole sysdba \
     -schemaPrefix FMW1 \
     -component MDS \
     -component IAU \
     -component IAU_APPEND \
     -component IAU_VIEWER \
     -component OPSS  \
     -component WLS  \
     -component STB <<EOF
   Oradoc_db1
   EOF
   ```

## Create and deploy your Kubernetes resources

You can use this sample's `./run_domain.sh` script, which will perform the following steps for you:

  - Creates a secret containing your WebLogic administrator username and password
  - Creates a secret containing your RCU access URL, credentials, and prefix (ignored unless domain type is JRF or RestrictedJRF)
  - Creates a config map containing an additional WDT model properties file './model1.20.properties'
  - Deploys a domain resource from `k8s-domain.yaml` 
  - Displays the status of the domain pods 

To run the script:

  ```
  cd $WORKDIR
  ./run_domain.sh
  ```

At the end, you will see the message `Getting pod status - ctrl-c when all is running and ready to exit`. Once all the pods are up, you can ctrl-c to exit the build script.


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
4. Send a request the `EXTERNAL-IP`:
   ```
   curl -kL http://EXTERNAL-IP/sample_war/index.jsp
   ```
   You should see something like:
   ```
   Hello World, you have reached server managed-server1
   ```


## Cleanup 

From the WebLogic Kubernetes Operator cloned root directory

1. From the WebLogic Kubernetes Operator cloned root directory
   ```
   kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh -d domain1u
   ```
2. If you setup nginx:
   ```
   kubectl delete -f k8s-nginx.yaml
   helm delete acmecontroller
   ```
3. If you setup a database:
   ```
   kubectl delete -f k8s-db-slim.yaml
   ```

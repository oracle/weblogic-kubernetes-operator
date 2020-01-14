# Model in Image Sample

This sample demonstrates the WebLogic Kubernetes Operator "Model in Image" feature. Model in Image enables specifying a Weblogic Deploy Tool (WDT) model that the operator uses to generate a full domain home during runtime. This eliminates the need to pre-create a WebLogic domain home prior to deploying your domain resource. 

WDT models are a convenient and succinct alternative to WebLogic configuration scripts. They compactly define a WebLogic domain via yaml files, plus support for application archives. The WDT model 
 format is 
 described in [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling).
 
When using ```Model In Image```, you can specify the WDT models and archives in the image. In addition, you can also 
provide additional models and variable files in a ```Kubernetes Configuration Map (configmap)```.  The WDT artifacts 
will be 
combined together and used by the ```WebLogic Deploy Tool``` to generate the domain.  Life cycle updates can be 
applied to the image or the configmap after initial deployment.

This sample demonstrates deploying a WebLogic servlet application within a basic `WLS` domain, a Oracle Fusion 
Middleware Infrastructure `JRF` domain, or a `RestrictedJRF` domain. The `JRF` domain path through the sample includes additional steps for deploying a database and initializing the database using the RCU tool. `JRF` domains are used by Oracle products that layer on top of WebLogic Server such as SOA, OSB, and FA. `RestrictedJRF` domains are used by other Oracle layered products such as CGBU.

# Contents

  - [Overview of High Level Steps](#overview-of-high-level-steps)
  - [Model File Naming and Loading Order](#model-file-naming-and-loading-order)
  - [Using Secrets in Model Files](#using-secrets-in-model-files)
  - [Using this Sample](#using-this-sample)
    - [Prerequisites for all domain types](#prerequisites-for-all-domain-types)
    - [Use the WebLogic Image Tool to create an image](#use-the-weblogic-image-tool-to-create-an-image)
    - [Setup prerequisites for JRF domains](#setup-prerequisites-for-jrf-domains)
    - [Create and deploy your Kubernetes resources](#create-and-deploy-your-kubernetes-resources)
    - [Optionally, install nginx to test the sample application](#optionally,-install-nginx-to-test-the-sample-application)http://www.grammarly.com/
    - [Cleanup](#cleanup)

# Overview of High-Level Steps

It is helpful to understand the following high-level flow before running the sample described in [Using this Sample]
(#using-this-sample):

1. Deploy the operator and ensure that it is monitoring the desired namespace.

2. Define your WDT model files.

   - You can use the `@@FILE` macro to reference your WebLogic credentials secret or other secrets. See [Using Secrets in Model Files](#using-secrets-in-model-files).

3. Create a deployable image with WebLogic Server, and WDT installed, plus optionally with your model files.
   - Optionally include all of your WDT model files in the image using the directory structure described below.

   - You can start with the image from [Docker Hub](https://github
   .com/oracle/docker-images/tree/master/OracleWebLogic) 
   - You can also create one using the [WebLogic Image Tool](https://github
   .com/oracle/weblogic-image-tool). The WebLogic Image Tool has built-in options for embedding WDT model files, WDT 
   binaries, WebLogic binaries install, and WebLogic patches in an image.

   - ```Model in Image``` requires the following directories structures in the image to locate the WDT models artifacts 
   and WDT binaries. If you are not using WebLogic Image Tool to embed the WDT artifacts, you must follow the structures 
   listed in the table below:

     | Directory                | Contents                           | Extension   |
     | ------------------------ | ---------------------------------- | ----------- |
     | /u01/wdt/models          | optional domain model yaml files   | yaml        |
     | /u01/wdt/models          | optional model variable files      | properties  |
     | /u01/wdt/models          | optional application archives      | zip         |
     | /u01/wdt/weblogic-deploy | unzipped weblogic deploy install   |             |

     Note that the WebLogic Image Tool mentioned in the previous step can create and populate this directory structure for you.

   - To control the model file loading order, see [Model File Naming and Loading Order](#model-file-naming-and-loading-order).

4. Create a WDT model config map (optional if step 3 fully defines your model).

   - You can optionally create a config map containing additional model yaml and model variable property files. They 
   will be applied during domain creation after the models found in the image directory `/u01/wdt/models`. Note that 
   it is a best practice to label the configmap with its domainUID to help ensure that cleanup scripts can find and delete the resource. 

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

   - The ```WebLogic Deploy Tool``` encryption option is one of two options for encrypting sensitive information stored in a model.

     > __NOTE__: Oracle recommends storing sensitive information using Kubernetes secrets when practical instead of relying on the WDT encryption option. See [Using Secrets in Model Files](#using-secrets-in-model-files).

   - If you want to use the WDT encryption option, then you need to create a secret to store the encryption 
   passphrase. The passphrase will be used to decrypt the model during domain creation. The secret can be named 
   anything, but it must contain a key named ```passphrase```.  Note that it is a best practice to label secrets with 
   their 
   domain UID to help ensure that cleanup scripts can find and delete them.

     ```
     kubectl -n sample-domain1-ns \
       create secret generic sample-domain1-wdt-secret \
       --from-literal=passphrase=welcome1
     kubectl -n sample-domain1-ns \
       label secret sample-domain1-wdt-secret \
       weblogic.domainUID=sample-domain1
     ```

6. Update the domain resource yaml file 

   - ```domainHomeInImage``` must set to true in the domain resource yaml file
   
   - ```domainHome``` must be set.  This is the domain home directory to be created, so it must not exist 
   in the image nor in the mount path of any persistent volume. 
   
   - If you have additional models stored in a config map, then include the following keys to the domain resource 
   yaml file as needed:
   
     ```
     wdtConfigMap : wdt-config-map
     ```

   - If you models reference Kubernetes secrets, then include the following keys to the domain resource yaml file as needed:
   
     ```
     configOverrideSecrets: [my-secret, my-other-secret]
     ```

   - In addition, specify the domain type in the domain resource yaml attribute. Valid values are `WLS`, `JRF`, and `RestrictedJRF`.

     ```
       wdtDomainType: "WLS"
     ```

   - In addition, if the WDT model is encrypted, create a Kubernetes secret and then specify the secret name.

     ```
         kubectl -n sample-domain1-ns create secret generic wdt-encrypt-passphrase-secret --from-literal=passhrase=welcome1

  
         wdtEncryptionPassPhrase: 
           name: wdt-encrypt-passphrase-secret
     ```

   - For JRF domain type, since the domain may be recreated in each life cycle deployment and you want to be able to 
   reuse the same infrastructure database.  WebLogic Kubernetes Operator will extract and store the OPSS wallet of 
   the domain and stored in the introspect domain configmap.  However, if the introspect configmap is deleted, the 
   OPSS is gone and you will not be able to attach any domain to that database. Oracle strongly recommends creating a passphrase 
   in a Kubernetes secret and extract the OPSS key from the introspect configmap and back up in a safe location.
   There are additional attributes in the domain resource yaml file that you must set.

     | Attribute                | Usage                                                                          |
     | ------------------------ | ------------------------------------------------------------------------------| 
     | opssWalletSecret        | Kubernetes secret name for the opss wallet  |
     | | When this is set. The operator will use the value of the ```passphrase``` key in the secret to extract the 
     | | opss wallet and store it in the introspector config map.  | 
     | | In situation if it is desirable to share the same infrastructure database across multiple domains, the |
     | | wallet stored in the introspector config map can be extract and store in the same secret with key |
     | | ```ewallet.p12``` |


     Create secret with the passphrase 
     
     ```
        kubectl -n sample-domain1-ns create secret generic opss-key-passphrase-secret --from-literal=passhrase=welcome1
     ```
            
     You can extract the opss wallet from the introspect domain after it is first deployed.
   
    ```
    kubectl -n <ns> describe configmap <domain-uid>-weblogic-domain-introspect-cm | sed -n '/ewallet.p12/ {n;n;p}' > 
    ewallet.p12
    ```
    
    Create the secret with both passphrase and opss wallet
    
    ```
    kubectl -n sample-domain1-ns create secret generic opss-key-passphrase-secret --from-literal=passhrase=welcome1 
    --from-file=ewallet.p12
      
    ```

   - (Experimental) During lifecycle updates, specify the behavior of whether to use dynamic update (no rolling of 
   server). 


     | Attribute                | Usage                              |
     | ------------------------ | ---------------------------------- | 
     | useOnlineUpdate          | (true or false) Default is false. User WLST online update for changes.      |
     | rollbackIfRequireStart   | (true or false) Default is true. If successful, there will be no rolling restart,  |                        
     | | Otherwise, it will cancel the changes and the introspector job will have error.  Note: your changes  |
     || in the image or configmap will not be rollback |



     
# Model File Naming and Loading Order

During domain home creation, model and property files are first loaded from directory `/u01/model_home/models` within the image and are then loaded from the optional wdt config map.  

The loading order within each of these locations is first determined using the convention `filename.##.yaml` and `filename.##.properties`, where `##` is a numeric number that specifies the desired order, and then is determined alphabetically as a tie-breaker. File names that don't include `.##.` sort _before_ other files as if they implicitly have the lowest possible `.##.`. If an image file and config map file both have the same name, then both files are loaded.

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
Then the combined model files list passed to the ```WebLogic Deploy Tool``` becomes:

  ```y.yaml,main-model.10.yaml,my-model.10.yaml,jdbc.20.yaml,z.yaml,jdbc-dev-urlprops.10.yaml```

Property files (ending in `.properties`) use the same sorting algorithm, but they are appended together into a single file prior to passing them to the ```WebLogic Deploy Tool```.

# Using Secrets in Model Files

You can use wdt model `@@FILE` macros to reference the WebLogic administrator username and password that is stored in 
a Kubernetes secret and to optionally reference additional secrets. 

Secret names are specified in your domain resource using the `weblogicCredentialsSecret` and `configOverridesSecrets` fields, and secret mounts are at the following locations:

  |domain resource field|directory location|
  |---------------------|-------------|
  |webLogicCredentialsSecret|/weblogic-operator/secrets|
  |configOverridesSecrets|/weblogic-operator/config-overrides-secrets/SECRET_NAME|

For example:
  
  - You can reference the weblogic credentials secret via `@@FILE:/weblogic-operator/secrets/username@@` and `@@FILE:/weblogic-operator/secrets/password@@`.  
  - You can reference a config overrides secret `mysecret` with value `myvalue` via `@@FILE:/weblogic-operator/config-overrides-secrets/mysecret/myvalue@@`.

# Using this Sample

## Prerequisites for all domain types

1. JAVA_HOME environment must be set and point to a valid JDK8 installation.

2. Setup a test directory, a source directory env variable, and a test directory environment variable.

   - Store the location of the Operator source code in an environment variable `SRCDIR`.
   
   - Create an empty temporary working directory with 10g of space, and store its location in `WORKDIR`.

   ```
   cd <top of source tree - should end with '/weblogic-kubernetes-operator'>
   export SRCDIR=$(pwd)
   cd <location of empty temporary directory with 10g of space>
   export WORKDIR=$(pwd)
   ```

3. Copy all the files in this sample to the working directory (substitute your Operator source location for SRCDIR).

   ```
   cp -R ${SRCDIR?}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/* ${WORKDIR?}
   ```

4. Deploy the Operator and setup the Operaator to manage namespace `sample-domain1-ns`. 
   - For example, see [Quick Start](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/) up through the `PREPARE FOR A DOMAIN` step. Note that you can skip the Quick Start steps for obtaining a WebLogic image and for configuring Traefik load balancer - as instead we we will generate our own image and setup an nginx load balancer instead.
   - If you've already deployed the Operater, you can use `helm get values my-operator-release` to check if it is 
   managing namespace `sample-domain1-ns` and use helm upgrade to add this namespace if needed.  For example:
     ```
     # get the helm release name for the running operator
     helm ls
  
     # check if operator manages `sample-domain1-ns`
     helm get values my-operator-release  # shows current managed namespaces
  
     # Use helm upgrade to add `sample-domain1-ns` if needed. Note that the
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

5. Choose the type of domain you're going to create: `WLS`, `JRF`, or `RestrictedJRF`, and set 
   environment variable WDT_DOMAIN_TYPE accordingly.
   ```
   export WDT_DOMAIN_TYPE=<one of WLS, JRF, or RestrictedJRF>
   ```

6. Obtain the base WebLoic image from [conainer-registry](http://container-registry.oracle.com)
  - Use the browser to login to [conainer-registry](http://container-registry.oracle.com)
  - Click on Middleware
  - Click on fmw-infrastructure
  - Accept the license agreement on dialog box on the right
  - Repeat the same license agreement for weblogic
  - Open a terminal and ```docker loging container-reigstry.oracle.com```


## Use the WebLogic Image Tool to create an image

This image will contain a WebLogic and a WebLogic Deploy Tool install, as well as your WDT model files.

You can use this sample `./build.sh` script, which will perform the following steps for you:

  - Expects WDT_DOMAIN_TYPE and WORKDIR to already be initialized (see [Prerequisites for all domain types](#prerequisites-for-all-domain-types))
  - Downloads the latest WebLogic Image Tool and WebLogic Deploy Tool
  - Creates a base image named `model-in-image:v1` that contains a JRE, a WLS install, and required patch(es)
    - Uses the WebLogic Image Tool 'create' option
    - Uses the JRE and WLS installers downloaded during the pre-requisites step
    - Determines the correct WLS installer based on WDT_DOMAIN_TYPE
  - Creates and populates directory `$WORKDIR/models`
    - Builds a simple servlet app and puts it in WDT model application archive `./models/archive1.zip`
    - Copies sample model files from `$WORKDIR/` to `$WORKDIR/models`
      - Uses a model file that ids appropriate to the domain type (for example, the `JRF` domain model includes 
      database access configuration)
  - Create a final image named `model-in-image:v1` that layers on the base image
    - Uses the WebLogic Image Tool 'update' option
    - Installs WDT using the installer you downloaded to image location `/u01/wdt/weblogic-deploy`.
    - Copies the WDT model, properties, and application archive in `$WORKDIR/models/archive1.zip` to image location `/u01/wdt/models`.

Run the script:

  ```
  cd ${WORKDIR?}
  ./build.sh
  ```

## Setup prerequisites for JRF domains

> __IMPORTANT__: This step is only required for demonstrating a `JRF` domain type. Skip to the next step [Create and deploy your Kubernetes resources](create-and-deploy-your-kubernetes-resources) if your domain type is simply a `WLS` or a `RestrictedJRF` domain.

A JRF domain requires an infrastructure database and also requires initalizing this database. This example shows how to setup a sample database and use the RCU tool to create the infrastructure schema. This step depends on the WebLogic base image that was created in the previous step.

1. Increase the introspection job timeout

   Since JRF domain creation takes considerable time, you should increase the timeout for the introspection job.

   ```
   kubectl -n <operation namespace> edit configmap weblogic-operator-cm 
   ```

   and add the parameter ```introspectorJobActiveDeadlineSeconds```  default is 120s.  Use 300s to start with.

2. Ensure you have access to the database image, and then deploy it:
   - Use a browser to login to `https://container-registry.oracle.com`, select `database->enterprise` and accept the license agreement.

   - Get the database image
     - In the local shell, `docker login container-registry.oracle.com`.https://www.keywhitman.com/new-portal/
     - In the local shell, `docker pull container-registry.oracle.com/database/enterprise:12.2.0.1-slim`.

     > __NOTE__: If a local docker login and manual pull of `container-registry.oracle.com/database/enterprise:12.2.0.1-slim` is not sufficient (for example, if you are using a remote k8s cluster), then uncomment the imagePullSecrets stanza in '$WORKDIR/k8s-db-slim.yaml' and create the image pull secret:
       ```
       kubectl create secret docker-registry regsecret \
         --docker-server=container-registry.oracle.com \
         --docker-username=your.email@some.com \
         --docker-password=your-password \
         --docker-email=your.email@some.com 
       
       ```

   - Use the sample script in <WebLogic Kubernetes Operator Root Directory>/kubernetes/samples/scripts/create-oracle-db-service:

     ```
        start-db-service.sh
     ```

   > __WARNING__: The Oracle Database Docker images are only supported for non-production use. For more details, see My Oracle Support note: Oracle Support for Database Running on Docker (Doc ID 2216342.1) 
   >            : All the data is gone when the database is restarted. 
   
   > __NOTE__: This step is based on the steps documented in [Run a Database](https://oracle.github.io/weblogic-kubernetes-operator/userguide/overview/database/).


3. Use the sample script in <WebLogic Kubernetes Operator Root Directory>/kubernetes/samples/scripts/create-rcu-schema to create the rcu schema using the following command. Note that `Oradoc_db1` is the dba password and `Oradoc_db1` is 
the schema password:

   ```
     create-rcu-schema.sh -s FMW1 -i container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3

   ```


4. __NOTE__:  If you need to drop the repository, you can use this command in the terminal:

   ```
    drop-rcu-schema.sh -s FMW1
   ```

## Create and deploy your Kubernetes resources

You can use this sample `./run_domain.sh` script, which will perform the following steps for you:

  - Creates a secret containing your WebLogic administrator username and password
  - Creates a secret containing your RCU access URL, credentials, and prefix (these are unused unless the domain type is `JRF`)
  - Creates a config map containing an additional WDT model properties file './model1.20.properties'
  - Deploys a domain resource from `k8s-domain.yaml` 
  - Displays the status of the domain pods 

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
4. Send a request the `EXTERNAL-IP`:
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
   This deletes the domain and any related resources that are labeled with Domain UID `sample-domain1`. It leaves the namespace intact.
2. If you setup nginx:
   ```
   kubectl delete -f k8s-nginx.yaml
   helm delete acmecontroller
   ```
3. If you setup a database:
   ```
   kubectl delete -f k8s-db-slim.yaml
   ```

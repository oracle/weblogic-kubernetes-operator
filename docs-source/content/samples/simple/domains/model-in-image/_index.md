---
title: "Model in image"
date: 2019-02-23T17:32:31-05:00
weight: 4
description: "Sample for supplying a WebLogic Deploy Tool (WDT) model that the operator automatically expands into a full domain home during runtime."
---

### Contents

  - [Introduction](#introduction)
  - [References](#references)
  - [Prerequisites for all domain types](#prerequisites-for-all-domain-types)
  - [Prerequisites for JRF domains](#prerequisites-for-jrf-domains)
    - [Set Up and Initialize an RCU Database](#set-up-and-initialize-an-rcu-database)
    - [Increase introspection job timeout](#increase-introspection-job-timeout)
    - [Set up RCU model attributes, domain resource attributes, and secrets](#set-up-rcu-model-attributes-domain-resource-attributes-and-secrets)
    - [Reusing or sharing RCU tables](#reusing-or-sharing-rcu-tables)
  - [Use the WebLogic Image Tool to create an image](#use-the-weblogic-image-tool-to-create-an-image)
  - [Create and deploy your Kubernetes resources](#create-and-deploy-your-kubernetes-resources)
  - [Optionally test the sample application](#optionally-test-the-sample-application)
  - [Optionally access the WebLogic console](#optionally-access-the-weblogic-console)
  - [Cleanup](#cleanup)

### Introduction

This sample demonstrates:

  - Using the WebLogic Image Tool to create a Docker image that contains a WebLogic install, a WebLogic Deploy Tool (WDT) install, a Java EE servlet application contained within a WDT archive, and a WebLogic domain that's defined using a WDT model file.
  - Modifying the WDT model that's embedded within the Docker image using a WDT model file that's supplied using a Kubernetes config map.
  - Defining a `domainHomeSourceType: FromModel` domain resource that references the WDT model image and the WDT config map.
  - Deploying the model image, domain resource, model config map, and associated secrets that define user names, passwords, and URL values for the model and its domain resource.
  - Deploying and accessing a Traefik load balancer that redirects HTTP protocol calls to its Java EE servlet application.

Supported domain types:

There are three types of domains supported by Model in Image: a standard `WLS` domain, an Oracle Fusion Middleware Infrastructure Java Required Files (`JRF`) domain, or a `RestrictedJRF` domain.

The `JRF` domain path through the sample includes additional steps for deploying an infrastructure database and initializing the database using the Repository Creation Utility (RCU) tool. `JRF` domains may be  used by Oracle products that layer on top of WebLogic Server such as SOA, OSB, and FA. Similarly, `RestrictedJRF` domains may be used by Oracle layered products such as Oracle Communications products.

### References

To reference the relevant user documentation, see:
 - [Model in Image User Guide]({{< relref "/userguide/managing-domains/model-in-image/_index.md" >}}) user documentation
 - [Oracle WebLogic Server Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling)
 - [Oracle WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool)



### Prerequisites for all domain types

1. The `JAVA_HOME` environment variable must be set and must reference a valid JDK8 installation. (`JAVA_HOME` is used by the WebLogic Image Tool.)

2. Set a source directory environment variable `SRCDIR` that references the parent of the operator source tree. For example:

   ```
   mkdir ~/wlopsrc
   cd ~/wlopsrc
   git clone https://github.com/oracle/weblogic-kubernetes-operator.git
   export SRCDIR=$(pwd)/weblogic-kubernetes-operator
   ```

   For additional information about obtaining the operator source, see the [Developer Guide Requirements](https://oracle.github.io/weblogic-kubernetes-operator/developerguide/requirements/).

3. Create a sample directory environment variable `SAMPLEDIR` that references this sample's directory:

   ```
   export SAMPLEDIR=${SRCDIR}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/
   ```

4. Create an empty, temporary working directory with 10g of space, and store its location in the `WORKDIR` environment variable. For example:

   ```
   cd <location of empty temporary directory with 10g of space>
   export WORKDIR=$(pwd)
   ```

5. Deploy the operator and set up the operator to manage the namespace, `sample-domain1-ns`. Optionally, deploy a Traefik load balancer that manages the same namespace. For example, follow the same steps as the [Quick Start](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/), up through the [Prepare for a domain]({{< relref "/quickstart/prepare.md" >}}) step.

   Note that:
   - Deploying the Traefik load balancer is optional, but is a prerequisite for testing the web application that's deployed to WebLogic as part of this sample.
   - You can skip the Quick Start steps for obtaining a WebLogic image because you will be creating your own Docker image.


6. Choose the type of domain you're going to create: `WLS`, `JRF`, or `RestrictedJRF`, and set the environment variable, `WDT_DOMAIN_TYPE`, accordingly. The default is `WLS`.

   ```
   export WDT_DOMAIN_TYPE=<one of WLS, JRF, or RestrictedJRF>
   ```

7. Set up access to a base image for this sample that will be used as the base image for creating the final image. Do one of the following:

   - __Option 1, download an existing WebLogic image.__

     Set up access to this sample's base WebLogic image at the [Oracle Container Registry](http://container-registry.oracle.com):

     a. Use a browser to access [Oracle Container Registry](http://container-registry.oracle.com).

     b. Choose an image location:
       - For `JRF` and `RestrictedJRF` domains, select `Middleware`, then `fmw-infrastructure`.
       - For `WLS` domains, select `Middleware`, then `weblogic`.

     c. Select Sign In and accept the license agreement.

     d. Use your terminal to locally log in to Docker: `docker login container-registry.oracle.com`.

     e. Later, when you run the sample, it will call `docker pull` for your base image based on the domain type.
       - For `JRF` and `RestrictedJRF`, it will pull `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4`.
       - For `WLS`, it will pull `container-registry.oracle.com/middleware/weblogic:12.2.1.4`.

   - __Option 2, create your own WebLogic base image.__

     Alternatively, you can create your own base image and override the sample's default base image name and tag by exporting the `BASE_IMAGE_NAME` and `BASE_IMAGE_TAG` environment variables prior to running the sample scripts. If you want to create your own base image, see [Preparing a Base Image]({{< relref "/userguide/managing-domains/domain-in-image/base-images/_index.md" >}}).

8. If you are using a `JRF` domain type, then it requires an RCU infrastructure database. See [Prerequisites for JRF Domains](#prerequisites-for-jrf-domains). You can do this step before or after you create your final image. If you're not using a `JRF` domain type, proceed to [Use the WebLogic Image Tool to create an image](#use-the-weblogic-image-tool-to-create-an-image).

### Prerequisites for JRF domains

> __NOTE__: This section is only required for demonstrating a `JRF` domain type. Skip this section and proceed to [Use the WebLogic Image Tool to create an image](#use-the-weblogic-image-tool-to-create-an-image) if your domain type is `WLS` or `RestrictedJRF`.

A JRF domain requires an infrastructure database called an RCU database, requires initializing this database, and requires configuring your domain to access this database. All of these steps must occur before you first deploy your domain.

Furthermore, if you want to have a restarted JRF domain access updates to the infrastructure database that the domain made at an earlier time, the restarted domain must be supplied a wallet file that was obtained from a previous run of the domain.

The following steps demonstrate how to set up an infrastructure database that will work with this sample:

  1. [Set up and initialize an RCU database](#set-up-and-initialize-an-rcu-database).
  2. [Increase introspection job timeout](#increase-introspection-job-timeout).
  3. [Set up RCU model attributes, domain resource attributes, and secrets](#set-up-rcu-model-attributes-domain-resource-attributes-and-secrets).
  4. [Reusing or sharing RCU tables](#reusing-or-sharing-rcu-tables).



##### Set up and initialize an RCU database

A JRF domain requires an infrastructure database and also requires initializing this database with a schema and a set of tables. The following example shows how to set up a sample RCU database and use the RCU tool to create the infrastructure schema for a JRF domain. The RCU database is set up with the following attributes:

| Attribute | Value |
| --------- | ----- |
| database Kubernetes namespace | `default` |
| database Kubernetes pod | `oracle-db` |
| database image | `container-registry.oracle.com/database/enterprise:12.2.0.1-slim` |
| database password | `Oradoc_db1` |
| infrastructure schema prefix | `FMW1` |
| infrastructure schema password | `Oradoc_db1` |
| database URL | `oracle-db.default.svc.cluster.local:1521/devpdb.k8s` |


1. Ensure you have access to the database image, and then deploy it:

   - Use a browser to log in to `https://container-registry.oracle.com`, select `database->enterprise` and accept the license agreement.

   - Get the database image:
     - In the local shell, `docker login container-registry.oracle.com`.
     - In the local shell, `docker pull container-registry.oracle.com/database/enterprise:12.2.0.1-slim`.


       {{% notice note %}} If a local Docker login and manual pull of `container-registry.oracle.com/database/enterprise:12.2.0.1-slim` is not sufficient (for example, if you are using a remote Kubernetes cluster), then uncomment the `imagePullSecrets` stanza in `$WORKDIR/k8s-db-slim.yaml` and create the image pull secret as follows:

              ```
              kubectl create secret docker-registry regsecret \
                --docker-server=container-registry.oracle.com \
                --docker-username=your.email@some.com \
                --docker-password=your-password \
                --docker-email=your.email@some.com
              ```
       {{% /notice %}}


   - Use the sample script in `$SRCDIR/kubernetes/samples/scripts/create-oracle-db-service` to create an Oracle database running in the pod, `oracle-db`.

      **NOTE**: If your database image access requires the `regsecret` image pull secret that you optionally created above, then pass `-s regsecret` to the `start-db-service.sh` command line.

     ```
     cd $SRCDIR/kubernetes/samples/scripts/create-oracle-db-service
     start-db-service.sh
     ```

     This script will deploy a database with the URL, `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`, and administration password, `Oradoc_db1`.

     {{% notice warning %}} The Oracle database Docker images are supported only for non-production use. For more details, see My Oracle Support note: Oracle Support for Database Running on Docker (Doc ID 2216342.1) : All the data is gone when the database is restarted.
     {{% /notice %}}

     **NOTE**: This step is based on the steps documented in [Run a Database](https://oracle.github.io/weblogic-kubernetes-operator/userguide/overview/database/).

2. Use the sample script in `SRCDIR/kubernetes/samples/scripts/create-rcu-schema` to create the RCU schema with the schema prefix `FMW1`.

   Note that this script assumes `Oradoc_db1` is the DBA password, `Oradoc_db1` is the schema password, and that the database URL is `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`.

   ```
   cd $SRCDIR/kubernetes/samples/scripts/create-rcu-schema
   ./create-rcu-schema.sh -s FMW1 -i container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4
   ```

   __NOTE__:  If you need to drop the repository, use this command:

   ```
   drop-rcu-schema.sh -s FMW1
   ```


##### Increase introspection job timeout

Because JRF domain home creation takes a considerable amount of time the first time it is created, and because Model in Image creates your domain home for you using the introspection job, you should increase the timeout for the introspection job. Use the `configuration.introspectorJobActiveDeadlineSeconds` in `k8s-domain.yaml.template` to override the default with a value of at least 300 seconds (the default is 120 seconds).  

##### Set up RCU model attributes, domain resource attributes, and secrets

To allow Model in Image to access the RCU database and OPSS wallet, it's necessary to set up an RCU access secret and an OPSS secret before deploying your domain. It's also necessary to define an `RCUDbInfo` stanza in your model. The sample already sets up all of these for you.  See:

| Sample file | Description |
| --------- | ----- |
| `run_domain.sh` | Defines secret, `sample-domain1-opss-wallet-password-secret`, with `password=welcome1`. |
| `run_domain.sh` | Defines secret, `sample-domain1-rcu-access`, with appropriate values for attributes `rcu_prefix`, `rcu_schema_password`, `rcu_admin_password`,  and `rcu_db_conn_string`. |
| `model1.yaml.jrf` | Populates the `domainInfo -> RCUDbInfo` stanza `rcu_prefix`, `rcu_schema_password`, `rcu_admin_password`,  and `rcu_db_conn_string` attributes by referencing their locations in the `sample-domain1-rcu-access` secret. The `build.sh` script uses this model instead of `model.yaml.wls` when the source domain type is `JRF`. |
| `k8s-domain.yaml.template` | Ensures that the domain mounts the OPSS key secret by setting the domain resource `configuration.opss.walletPasswordSecret` attribute to `sample-domain1-opss-wallet-password-secret`, and ensures the domain mounts the RCU access secret, `sample-domain1-rcu-access`, for reference by WDT model macros by setting the domain resource `configuration.secrets` attribute. Use configuration.introspectorJobActiveDeadlineSeconds to increase the timeout value of the introspector job; see [Increase introspection job timeout](#increase-introspection-job-timeout). |

 **NOTE**: This step is for information purposes only. Do not run the above sample files directly. The sample's main build and run scripts will run them for you.

##### Reusing or sharing RCU tables

Note that when you succesfully deploy your JRF domain resource for the first time, the introspector job will initialize the RCU tables for the domain using the `domainInfo -> RCUDbInfo` stanza in the WDT model plus the `configuration.opss.walletPasswordSecret` specified in the domain resource. The job will also create a new domain home. Finally, the operator will also capture an OPSS wallet file from the new domain's local directory and place this file in a new Kubernetes config map.

There are scenarios when the domain needs to be re-created between updates such as WebLogic credentials are changed, security roles defined in the WDT model have been changed or you want to share the same RCU tables with different domains.  Under these scenarios, the operator needs the `walletPasswordSecret` as well as the OPSS wallet file, together with the exact information in `domainInfo -> RCUDbInfo` so that the domain can be re-created and access the same set of RCU tables.  Without the wallet file and wallet password, you will not be able to re-create a domain accessing the same set of RCU tables, therefore it is highly recommended to backup the wallet file.

To recover a domain's RCU tables between domain restarts or to share an RCU schema between different domains, it is necessary to extract this wallet file from the config map and save the OPSS wallet password secret that was used for the original domain. The wallet password and wallet file are needed again when you recreate the domain or share the database with other domains.

To save the wallet file:

```
    opss_wallet_util.sh -s [-wf <name of the wallet file. Default ./ewallet.p12>]
```

You should back up this file to a safe location that can be retrieved later.

To reuse the wallet for subsequent redeployments or share the RCU tables between different domains:

1. Store the wallet in a secret:

```
    opss_wallet_util.sh -r [-wf <name of the wallet file. Default ./ewallet.p12>] [-ws <name of the secret. Default DOMAIN_UID-opss-walletfile-secret> ]

```

2. Modify the domain resource YAML file to provide the secret names:

```
  configuration:
    opss:
      # Name of secret with walletPassword for extracting the wallet
      walletPasswordSecret: sample-domain1-opss-wallet-password-secret      
      # Name of secret with walletFile containing base64 encoded opss wallet
      walletFileSecret: sample-domain1-opss-walletfile-secret

```

See [Reusing an RCU database]({{< relref "/userguide/managing-domains/model-in-image/reusing-rcu.md" >}}) for instructions.

### Use the WebLogic Image Tool to create an image

Model in Image must contain a WebLogic install, a WebLogic Deploy Tool install, and your WDT model files. You can use the sample `./build.sh` script to build this image, which will perform the following steps for you:

  - Uses `docker pull` to obtain a base image. (See [Prerequisites for all domain types](#prerequisites-for-all-domain-types) to set up access to the base image.)
  - Downloads the latest WebLogic Image Tool and WebLogic Deploy Tool to `WORKDIR`.
  - Creates and populates a staging directory `$WORKDIR/models` that contains your WDT model files and WDT application archive.
    - Builds  a simple servlet application in `$SAMPLEDIR/sample_app` into a WDT model application archive `$WORKDIR/models/archive1.zip`.
    - Copies sample model files from `$SAMPLEDIR/model-in-image` to `$WORKDIR/models`. This uses a model file that is appropriate to the domain type (for example, the `JRF` domain model includes database access configuration).
  - Uses the WebLogic Image Tool and the `$WORKDIR/models` staging directory to create a final image named `model-in-image:v1` that layers on the base image. Specifically, it runs the WebLogic Image Tool with its `update` option, which:
    - Builds the final image as a layer on the base image.
    - Puts a WDT install in image location, `/u01/wdt/weblogic-deploy`.
    - Copies the WDT model, properties, and application archive from `$WORDKIR/models` to image location, `/u01/wdt/models`.

The script expects `JAVA_HOME`, `WDT_DOMAIN_TYPE`, and `WORKDIR` to already be initialized. (See [Prerequisites for all domain types](#prerequisites-for-all-domain-types).)

Run the script:

  ```
  $SAMPLEDIR/build.sh
  ```

If you intend to use a remote Docker registry, you need to tag and push the image to the remote Docker registry.

1.  Tag the image for the remote Docker registry, for example:

```
docker tag <image-name>:<tag> <region-key>.ocir.io/<tenancy-namespace>/<repo-name>/<image-name>:<tag>
```

2.  Push the image to the remote Docker registry, for example:

```
docker push <region-key>.ocir.io/<tenancy-namespace>/<repo-name>/<image-name>:<tag>
```

3. Create the pull secret for the remote Docker registry:

```
 kubectl -n <domain namespace> create secret docker-registry <secret name> \
     --docker-server=<region-key>.ocir.io/<tenancy-namespace>/<repo-name> \
     --docker-username=your.email@some.com \
     --docker-password=your-password \
     --docker-email=your.email@some.com

```

4. Update the domain template file `$SAMPLEDIR/k8s-domain.yaml.template` to provide the `imagePullSecrets`:

```
  imagePullSecrets:
  - name: <secret name>

```

5. Export the environment variables for the image name and tag using the same values in step 1:

```
export MODEL_IMAGE_NAME="<region-key>.ocir.io/<tenancy-namespace>/<repo-name>/<image-name>"
export MODEL_IMAGE_TAG="<tag>"
```

### Create and deploy your Kubernetes resources

To deploy the sample operator domain and its required Kubernetes resources, use the sample script, `$SAMPLEDIR/run_domain.sh`, which will perform the following steps for you:

  - Deletes the domain with a `DomainUID` of `domain1` in the namespace, `sample-domain1-ns`, if it already exists.
  - Creates a secret containing your WebLogic administrator user name and password.
  - Creates a secret containing your Model in Image runtime encryption password:
    - All model-in-image domains must supply a runtime encryption secret with a `password` value.
    - It is used to encrypt configuration that is passed around internally by the Operator.
    - The value must be kept private but can be arbitrary: you can optionally supply a different secret value every time you restart the domain.
  - Creates secrets containing your RCU access URL, credentials, and prefix (these are unused unless the domain type is `JRF`).
  - Creates a config map containing an additional WDT model properties file, `$SAMPLEDIR/model1.20.properties`.
  - Generates a domain resource YAML file, `$WORKDIR/k8s-domain.yaml`, using `$SAMPLEDIR/k8s-domain.yaml.template`.
  - Deploys `k8s-domain.yaml`.
  - Displays the status of the domain pods.

The script expects `WDT_DOMAIN_TYPE` and `WORKDIR` to already be initialized. (See [Prerequisites for all domain types](#prerequisites-for-all-domain-types).)

Run the script:

  ```
  $SAMPLEDIR/run_domain.sh
  ```

At the end, you will see the message `Getting pod status - ctrl-c when all is running and ready to exit`. Then you should see a WebLogic Administration Server and two Managed Server pods start. After all the pods are up, you can use `ctrl-c` to exit the build script.


### Optionally test the sample application

1. Ensure Traefik has been installed and is servicing external port 30305, as per [Prerequisites for all domain types](#prerequisites-for-all-domain-types).

2. Create a Kubernetes Ingress for the domain's WebLogic cluster in the domain's namespace by using the sample Helm chart:

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

   This creates an Kubernetes Ingress that helps route HTTP traffic from the Traefik load balancer's external port 30305 to the WebLogic domain's `cluster-1` 8001 port. Note that the WDT config map in this sample changes the cluster's port from 9001 to 8001 (9001 is the original port configured using the WDT model defined within in the image).

3. Send a web application request to the load balancer:

   ```
   curl -H 'host: sample-domain1.org' http://$(hostname).$(dnsdomainname):30305/sample_war/index.jsp
   ```

   You should see something like the following:

   ```
   Hello World, you have reached server managed-server1
   ```

   **Note**: If you're running on a remote Kubernetes cluster, then substitute `$(hostname).$(dnsdomainname)` with an external address suitable for contacting the cluster.

4. Send a ReadyApp request to the load balancer (ReadyApp is a built-in WebLogic Server application):

   ```
   curl -v -H 'host: sample-domain1.org' http://$(hostname).$(dnsdomainname):30305/weblogic/ready
   ```

   You should see something like the following:


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

   **Note**: If you're running on a remote Kubernetes cluster, then substitute `$(hostname).$(dnsdomainname)` with an external address suitable for contacting the cluster.

### Optionally access the WebLogic console

You can add an ingress rule to access the WebLogic Console from your local browser

1. Find out the service name of the admin server and service port number.

The name follows the pattern <Domain UID>-<admin server name> all lower case and the port number will be described in your 
WDT model's admin server `listenPort`.

You can also find the information by:

```
kubectl -n sample-domain1-ns get services
```

```
NAME                               TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)    AGE
sample-domain1-admin-server        ClusterIP   None           <none>        7001/TCP   48m
```

This shows the admin service name is `sample-domain1-admin-server` and the port for the console is `7001`

2. Create an ingress rule for the WebLogic console

Create the following file and call it `console-ingress.yaml` in your `$WORKDIR`.

```
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: sample-domain1-console-ingress
  namespace: sample-domain1-ns
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

```

This will route the request path `/console` to the admin service port `7001` at pod `sample-domain1-admin-server` in the `sample-domain1-ns` namespace.

3.  Apply the ingress rule resource

```
kubectl apply -f $WORKDIR/console-ingress.yaml
```

4.  Access the WebLogic console from the brower


```
# If the domain and your browser are running the same machine:
http://localhost:30305/console

# If the domain is on a remote machine from your browser:
http://your-domain-host-address:30305/console
```

Login is 'weblogic/welcome1'.


### Cleanup

1. Delete the domain resource.
   ```
   $SRCDIR/kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh -d sample-domain1
   ```
   This deletes the domain and any related resources that are labeled with the domain UID `sample-domain1`. It leaves the namespace intact, the operator running, the load balancer running (if installed), and the database running (if installed).

2. If you set up the Traefik load balancer:

   ```
   helm delete --purge sample-domain1-ingress
   helm delete --purge traefik-operator
   kubectl delete namespace traefik
   ```

3. If you set up a database:
   ```
   ${SRCDIR}/kubernetes/samples/scripts/create-oracle-db-service/stop-db-service.sh
   ```

4. If you have set up the Traefik ingress rule to the WebLogic console.
   ```
   kubectl delete -f $WORKDIR/console-ingress.yaml
   ```

5. Delete the operator and its namespace:
   ```
   helm delete --purge sample-weblogic-operator
   kubectl delete namespace sample-weblogic-operator-ns
   ```

6. Delete the domain's namespace:
   ```
   kubectl delete namepsace sample-domain1-ns
   ```

---
title: "Model in Image"
date: 2020-11-24T18:22:31-05:00
weight: 3
description: "Sample for creating a WebLogic cluster on the Azure Kubernetes Service with model in image approach."
---

This sample demonstrates how to use the [Oracle WebLogic Server Kubernetes Operator](/weblogic-kubernetes-operator/) (hereafter "the operator") to set up a WebLogic Server (WLS) cluster on the Azure Kubernetes Service (AKS) using the model in image approach. After going through the steps, your WLS domain runs on an AKS cluster instance and you can manage your WLS domain by interacting with the operator.

#### Contents

 - [Prerequisites](#prerequisites)
 - [Create an AKS cluster](#create-the-aks-cluster)
 - [Install WebLogic Server Kubernetes Operator](#install-weblogic-server-kubernetes-operator)
 - [Create Docker image](#create-docker-image)
 - [Create WebLogic domain](#create-weblogic-domain)
 - [Invoke the web application](#invoke-the-web-application)
 - [Rolling updates](#rolling-updates)
 - [Clean up resource](#clean-up-resources)
 - [Troubleshooting](#troubleshooting)
 - [Useful links](#useful-links)

{{< readfile file="/samples/simple/azure-kubernetes-service/includes/prerequisites-02.txt" >}}


{{< readfile file="/samples/simple/azure-kubernetes-service/includes/create-aks-cluster-body-01.txt" >}}

##### Clone WebLogic Server Kubernetes Operator repository

Clone the [Oracle WebLogic Server Kubernetes Operator repository](https://github.com/oracle/weblogic-kubernetes-operator) to your machine. We will use several scripts in this repository to create a WebLogic domain. This sample was tested with v3.1.1, but should work with the latest release.

```bash
$ git clone https://github.com/oracle/weblogic-kubernetes-operator.git
cd weblogic-kubernetes-operator
```

{{< readfile file="/samples/simple/azure-kubernetes-service/includes/create-aks-cluster-body-02.txt" >}}

**Note**: If you run into VM size failure, see [Troubleshooting - Virtual Machine size is not supported]({{< relref "/samples/simple/azure-kubernetes-service/troubleshooting#virtual-machine-size-is-not-supported" >}}).


#### Install WebLogic Server Kubernetes Operator

The Oracle WebLogic Server Kubernetes Operator is an adapter to integrate WebLogic Server and Kubernetes, allowing Kubernetes to serve as a container infrastructure hosting WLS instances.  The operator runs as a Kubernetes Pod and stands ready to perform actions related to running WLS on Kubernetes.

Create a namespace and service account for the operator.

```bash
$ kubectl create namespace sample-weblogic-operator-ns
namespace/sample-weblogic-operator-ns created


$ kubectl create serviceaccount -n sample-weblogic-operator-ns sample-weblogic-operator-sa
serviceaccount/sample-weblogic-operator-sa created
```

Validate the service account was created with this command.

```bash
$ kubectl -n sample-weblogic-operator-ns get serviceaccount

NAME                          SECRETS   AGE
default                       1         9m24s
sample-weblogic-operator-sa   1         9m5s
```

Install the operator. Ensure your current directory is `weblogic-kubernetes-operator`. It may take you several minutes to install the operator.

```bash
# cd weblogic-kubernetes-operator
$ helm install weblogic-operator kubernetes/charts/weblogic-operator \
  --namespace sample-weblogic-operator-ns \
  --set image=ghcr.io/oracle/weblogic-kubernetes-operator:3.1.1 \
  --set serviceAccount=sample-weblogic-operator-sa \
  --set "enableClusterRoleBinding=true" \
  --set "domainNamespaceSelectionStrategy=LabelSelector" \
  --set "domainNamespaceLabelSelector=weblogic-operator\=enabled" \
  --wait

NAME: weblogic-operator
LAST DEPLOYED: Tue Nov 17 09:33:58 2020
NAMESPACE: sample-weblogic-operator-ns
STATUS: deployed
REVISION: 1
TEST SUITE: None
```

{{% notice tip %}} If you wish to use a more recent version of the operator, replace the `3.1.1` in the preceding command with the other version number. To see the list of version numbers, visit the [GitHub releases page](https://github.com/oracle/weblogic-kubernetes-operator/releases).
{{% /notice %}}


Verify the operator with the following commands; the status will be `Running`.

```bash
$ helm list -A
NAME                        NAMESPACE                     REVISION   UPDATED                                 STATUS       CHART                   APP VERSION
sample-weblogic-operator    sample-weblogic-operator-ns   1          2020-11-17 09:33:58.584239273 -0700 PDT deployed     weblogic-operator-3.1


$ kubectl get pods -n sample-weblogic-operator-ns
NAME                                 READY   STATUS    RESTARTS   AGE
weblogic-operator-775b668c8f-nwwnn   1/1     Running   0          32s
```

{{% notice note %}}
You can sepcify the operator image by changing value of `--set image`. If you run into failures, see [Troubleshooting - WebLogic Kubernetes Operator installation failure]({{< relref "/samples/simple/azure-kubernetes-service/troubleshooting#weblogic-kubernetes-operator-installation-failure" >}}).
{{% /notice %}}

{{% notice info %}}
If you have a Docker image built with domain models following [Model in Image]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}}), you can go to [Create WebLogic domain](#create-weblogic-domain) directly.
{{% /notice %}}

#### Create Docker image

  - [Image creation prerequisites](#image-creation-prerequisites)
  - [Image creation - Introduction](#image-creation---introduction)
  - [Understanding your first archive](#understanding-your-first-archive)
  - [Staging a ZIP file of the archive](#staging-a-zip-file-of-the-archive)
  - [Staging model files](#staging-model-files)
  - [Creating the image with WIT](#creating-the-image-with-wit)
  - [Pushing the image to Azure Container Registry](#pushing-the-image-to-azure-container-registry)

##### Image creation prerequisites
1. The `JAVA_HOME` environment variable must be set and must reference a valid JDK 8 or 11 installation.
1. Copy the sample to a new directory; for example, use the directory `/tmp/mii-sample`.

   ```bash
   $ mkdir /tmp/mii-sample
   $ cd kubernetes/samples/scripts/create-weblogic-domain/model-in-image
   $ cp -r * /tmp/mii-sample
   ```

   **Note**: We will refer to this working copy of the sample as `/tmp/mii-sample`, (`mii` is short for model in image); however, you can use a different location.

1. Download the latest WebLogic Deploying Tooling (WDT) and WebLogic Image Tool (WIT) installer ZIP files to your `/tmp/mii-sample/model-images` directory. Both WDT and WIT are required to create your Model in Image Docker images.

   For example, visit the GitHub [WebLogic Deploy Tooling Releases](https://github.com/oracle/weblogic-deploy-tooling/releases) and [WebLogic Image Tool Releases](https://github.com/oracle/weblogic-image-tool/releases) web pages to determine the latest release version for each, and then, assuming the version numbers are `1.9.7` and `1.9.5` respectively, call:

   ```bash
   $ cd /tmp/mii-sample/model-images

   $ curl -m 120 -fL https://github.com/oracle/weblogic-deploy-tooling/releases/download/release-1.9.7/weblogic-deploy.zip \
     -o /tmp/mii-sample/model-images/weblogic-deploy.zip

   $ curl -m 120 -fL https://github.com/oracle/weblogic-image-tool/releases/download/release-1.9.5/imagetool.zip \
     -o /tmp/mii-sample/model-images/imagetool.zip
   ```


   To set up the WebLogic Image Tool, run the following commands:
   ```bash
   $ cd /tmp/mii-sample/model-images

   $ unzip imagetool.zip

   $ ./imagetool/bin/imagetool.sh cache addInstaller \
     --type wdt \
     --version latest \
     --path /tmp/mii-sample/model-images/weblogic-deploy.zip
   ```

   These steps will install WIT to the `/tmp/mii-sample/model-images/imagetool` directory, plus put a `wdt_latest` entry in the tool’s cache which points to the WDT ZIP file installer. You will use WIT later in the sample for creating model images.

##### Image creation - Introduction

The goal of image creation is to demonstrate using the WebLogic Image Tool to create an image named `model-in-image:WLS-v1` from files that you will stage to `/tmp/mii-sample/model-images/model-in-image:WLS-v1/`.
The staged files will contain a web application in a WDT archive, and WDT model configuration for a WebLogic Administration Server called `admin-server` and a WebLogic cluster called `cluster-1`.

A "Model in Image" image contains the following elements:
* A WebLogic Server installation and a WebLogic Deploy Tooling installation in its `/u01/wdt/weblogic-deploy` directory.
* If you have WDT model archive files, then the image must also contain these files in its `/u01/wdt/models` directory.
* If you have WDT model YAML file and properties files, then they go in in the same `/u01/wdt/models` directory. If you do not specify a WDT model YAML file in your `/u01/wdt/models` directory, then the model YAML file must be supplied dynamically using a Kubernetes `ConfigMap` that is referenced by your Domain `spec.model.configMap` field.

We provide an example of using a model `ConfigMap` later in this sample.

The following sections contain the steps for creating the image `model-in-image:WLS-v1`.

##### Understanding your first archive

The sample includes a predefined archive directory in `/tmp/mii-sample/archives/archive-v1` that you will use to create an archive ZIP file for the image.

The archive top directory, named `wlsdeploy`, contains a directory named `applications`, which includes an ‘exploded’ sample JSP web application in the directory, `myapp-v1`. Three useful aspects to remember about WDT archives are:
  - A model image can contain multiple WDT archives.
  - WDT archives can contain multiple applications, libraries, and other components.
  - WDT archives have a [well defined directory structure](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/archive.md), which always has `wlsdeploy` as the top directory.

The application displays important details about the WebLogic Server instance that it’s running on: namely its domain name, cluster name, and server name, as well as the names of any data sources that are targeted to the server.


##### Staging a ZIP file of the archive

When you create the image, you will use the files in the staging directory, `/tmp/mii-sample/model-in-image__WLS-v1`. In preparation, you need it to contain a ZIP file of the WDT application archive.

Run the following commands to create your application archive ZIP file and put it in the expected directory:

```bash
# Delete existing archive.zip in case we have an old leftover version
$ rm -f /tmp/mii-sample/model-images/model-in-image__WLS-v1/archive.zip

# Move to the directory which contains the source files for our archive
$ cd /tmp/mii-sample/archives/archive-v1

# Zip the archive to the location will later use when we run the WebLogic Image Tool
$ zip -r /tmp/mii-sample/model-images/model-in-image__WLS-v1/archive.zip wlsdeploy
```

##### Staging model files

In this step, you explore the staged WDT model YAML file and properties in the `/tmp/mii-sample/model-in-image__WLS-v1` directory. The model in this directory references the web application in your archive, configures a WebLogic Server Administration Server, and configures a WebLogic cluster. It consists of only two files, `model.10.properties`, a file with a single property, and, `model.10.yaml`, a YAML file with your WebLogic configuration.

Here is the WLS `model.10.properties`:

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

The model file:

- Defines a WebLogic domain with:
    - Cluster `cluster-1`
    - Administration Server `admin-server`
    - An EAR application, targeted to `cluster-1`, located in the WDT archive ZIP file at `wlsdeploy/applications/myapp-v1`

- Leverages macros to inject external values:
    - The property file `CLUSTER_SIZE` property is referenced in the model YAML file `DynamicClusterSize` and `MaxDynamicClusterSize` fields using a PROP macro.
    - The model file domain name is injected using a custom environment variable named `CUSTOM_DOMAIN_NAME` using an ENV macro.
        - You set this environment variable later in this sample using an `env` field in its Domain.
        - _This conveniently provides a simple way to deploy multiple differently named domains using the same model image_.
    - The model file administrator user name and password are set using a `weblogic-credentials` secret macro reference to the WebLogic credential secret.
        - This secret is in turn referenced using the `webLogicCredentialsSecret` field in the Domain.
        - The `weblogic-credentials` is a reserved name that always dereferences to the owning Domain actual WebLogic credentials secret name.

A Model in Image image can contain multiple properties files, archive ZIP files, and YAML files but in this sample you use just one of each. For a complete discussion of Model in Images model file naming conventions, file loading order, and macro syntax, see [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}) files in the Model in Image user documentation.

##### Creating the image with WIT

At this point, you have staged all of the files needed for the image `model-in-image:WLS-v1`; they include:

  - `/tmp/mii-sample/model-images/weblogic-deploy.zip`
  - `/tmp/mii-sample/model-images/model-in-image__WLS-v1/model.10.yaml`
  - `/tmp/mii-sample/model-images/model-in-image__WLS-v1/model.10.properties`
  - `/tmp/mii-sample/model-images/model-in-image__WLS-v1/archive.zip`

If you don’t see the `weblogic-deploy.zip` file, then you missed a step in the [prerequisites](#image-creation-prerequisites).

Now, you use the Image Tool to create a Docker image named `model-in-image:WLS-v1` with a `FROM` clause that references a base WebLogic image. You’ve already set up this tool during the prerequisite steps.

Run the following commands to create the model image and verify that it worked:

```bash
$ cd /tmp/mii-sample/model-images
$ ./imagetool/bin/imagetool.sh update \
  --tag model-in-image:WLS-v1 \
  --fromImage container-registry.oracle.com/middleware/weblogic:12.2.1.4 \
  --wdtModel      ./model-in-image__WLS-v1/model.10.yaml \
  --wdtVariables  ./model-in-image__WLS-v1/model.10.properties \
  --wdtArchive    ./model-in-image__WLS-v1/archive.zip \
  --wdtModelOnly \
  --wdtDomainType WLS \
  --chown oracle:root
```

If you don’t see the `imagetool` directory, then you missed a step in the prerequisites.

The preceding command runs the WebLogic Image Tool in its Model in Image mode, and does the following:

  - Builds the final Docker image as a layer on the `container-registry.oracle.com/middleware/weblogic:12.2.1.4` base image.
  - Copies the WDT ZIP file that’s referenced in the WIT cache into the image.
      - Note that you cached WDT in WIT using the keyword `latest` when you set up the cache during the sample prerequisites steps.
      - This lets WIT implicitly assume it’s the desired WDT version and removes the need to pass a `-wdtVersion` flag.
  - Copies the specified WDT model, properties, and application archives to image location `/u01/wdt/models`.

When the command succeeds, you should see output like the following:

```
[INFO   ] Build successful. Build time=36s. Image tag=model-in-image:WLS-v1
```

Verify the image is available in the local Docker server with the following command.

```shell
$ docker images | grep WLS-v1
model-in-image          WLS-v1   012d3bfa3536   5 days ago      1.13GB
```

{{% notice note %}}
You may run into a `Dockerfile` parsing error if your Docker buildkit is enabled, see [Troubleshooting - WebLogic Image Tool failure]({{< relref "/samples/simple/azure-kubernetes-service/troubleshooting#weblogic-image-tool-failure" >}}).
{{% /notice %}}

##### Pushing the image to Azure Container Registry

AKS can pull Docker images from any container registry, but the easiest integration is to use Azure Container Registry (ACR).  In this section, we will create a new Azure Container Registry, connect it to our pre-existing AKS cluster and push the Docker image built in the preceding section to it.  For complete details, see [Azure Container Registry documentation](https://docs.microsoft.com/en-us/azure/container-registry/).

Let's create an instance of ACR in the same resource group we used for AKS. We will use the environment variables used during the steps above.  For simplicity, we use the resource group name as the name of the ACR instance.

```shell
$ az acr create --resource-group $AKS_PERS_RESOURCE_GROUP --name $AKS_PERS_RESOURCE_GROUP --sku Basic --admin-enabled true
```

Closely examine the JSON output from this command. Save the value of the `loginServer` property aside. It will look something like the following.

```json
"loginServer": "contosoresourcegroup1610068510.azurecr.io",
```

Use this value to sign in to the ACR instance. Note that because you are signing in with the `az` cli, you do not need a password because your identity is already conveyed by having done `az login` previously.

```shell
$ export AKS_PERS_ACR=<you-ACR-loginServer>
$ az acr login --name $AKS_PERS_ACR
```

Ensure Docker is running on your local machine.  Run the following commands to tag and push the image to your ACR.

```shell
$ docker tag model-in-image:WLS-v1 $AKS_PERS_ACR/$AKS_PERS_ACR:model-in-image-aks
$ docker push $AKS_PERS_ACR/$AKS_PERS_ACR:model-in-image-aks
The push refers to repository [contosorgresourcegroup1610068510.azurecr.io/contosorgresourcegroup1610068510.azurecr.io]
model-in-image-aks: digest: sha256:208217afe336053e4c524caeea1a415ccc9cc73b206ee58175d0acc5a3eeddd9 size: 2415
```

Finally, connect AKS to the ACR.  For more details on connecting ACR to an existing AKS, see [Configure ACR integration for existing AKS clusters](https://docs.microsoft.com/en-us/azure/aks/cluster-container-registry-integration#configure-acr-integration-for-existing-aks-clusters).

```shell
$ az aks update --name $AKS_CLUSTER_NAME --resource-group $AKS_PERS_RESOURCE_GROUP --attach-acr $AKS_PERS_RESOURCE_GROUP
```

If you see an error that seems related to you not being an **Owner on this subscription**, please refer to the troubleshooting section [Cannot attach ACR due to not being Owner of subscription]({{< relref "/samples/simple/azure-kubernetes-service/troubleshooting#cannot-attach-acr-due-to-not-being-owner-of-subscription" >}}).

Successful output will be a JSON object with the entry `"type": "Microsoft.ContainerService/ManagedClusters"`.


#### Create WebLogic domain

In this section, you will deploy the new image to the namespace `sample-domain1-ns`, including the following steps:

- Create a namespace for the WebLogic domain.
- Upgrade the operator to manage the WebLogic domain namespace.
- Create a Secret containing your WebLogic administrator user name and password.
- Create a Secret containing your Model in Image runtime encryption password:
    - All Model in Image domains must supply a runtime encryption Secret with a `password` value.
    - The runtime encryption password is used to encrypt configuration that is passed around internally by the operator.
    - The value must be kept private but can be arbitrary; you can optionally supply a different secret value every time you restart the domain.
- Deploy a Domain YAML file that references the new image.
- Wait for the domain’s Pods to start and reach their ready state.

##### Namespace

Create a namespace that can host one or more domains:

```bash
$ kubectl create namespace sample-domain1-ns

## label the domain namespace so that the operator can autodetect and create WebLogic Server pods.
$ kubectl label namespace sample-domain1-ns weblogic-operator=enabled
```

##### Kubernetes Secrets for WebLogic

First, create the secrets needed by the WLS type model domain. For more on secrets in the context of running domains, see [Prepare to run a domain]({{< relref "/userguide/managing-domains/prepare" >}}). In this case, you have two secrets.

Run the following `kubectl` commands to deploy the required secrets:

```bash
$ kubectl -n sample-domain1-ns create secret generic \
  sample-domain1-weblogic-credentials \
   --from-literal=username=weblogic --from-literal=password=welcome1
$ kubectl -n sample-domain1-ns label  secret \
  sample-domain1-weblogic-credentials \
  weblogic.domainUID=sample-domain1

$ kubectl -n sample-domain1-ns create secret generic \
  sample-domain1-runtime-encryption-secret \
   --from-literal=password=welcome1
$ kubectl -n sample-domain1-ns label  secret \
  sample-domain1-runtime-encryption-secret \
  weblogic.domainUID=sample-domain1
```

  Some important details about these secrets:

  - The WebLogic credentials secret:
    - It is required and must contain `username` and `password` fields.
    - It must be referenced by the `spec.webLogicCredentialsSecret` field in your Domain resource YAML file.  For complete details about the `Domain` resource, see the [Domain resource reference](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md#domain-spec).
    - It also must be referenced by macros in the `domainInfo.AdminUserName` and `domainInfo.AdminPassWord` fields in your `model.10.yaml` file.

  - The Model WDT runtime encrytion secret:
    - This is a special secret required by Model in Image.
    - It must contain a `password` field.
    - It must be referenced using the `spec.model.runtimeEncryptionSecret` field in your Domain resource YAML file.
    - It must remain the same for as long as the domain is deployed to Kubernetes but can be changed between deployments.
    - It is used to encrypt data as it's internally passed using log files from the domain's introspector job and on to its WebLogic Server pods.

  - Deleting and recreating the secrets:
    - You must delete a secret before creating it, otherwise the `create` command will fail if the secret already exists.
    - This allows you to change the secret when using the `kubectl create secret` command.

  - You name and label secrets using their associated `domainUID` for two reasons:
    - To make it obvious which secrets belong to which domains.
    - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all resources associated with a domain.

##### Kubernetes Secrets for Docker

Deploy a corresponding Kubernetes `docker secret` to the same namespace to access the image during domain creation.

Use `kubernetes/samples/scripts/create-kuberetes-secrets/create-docker-credentials-secret.sh` to create the secret.  Please invoke the script with the `-h` option to see the available switches and usage.

```shell
$ cd weblogic-kubernetes-operator
$ cd kubernetes/samples/scripts/create-kuberetes-secrets
$ ./create-docker-credentials-secret.sh -h
```

Get the password for the ACR and store it in the Kubernetes secret.

```shell
$ az acr credential show --name $AKS_PERS_ACR
The login server endpoint suffix '.azurecr.io' is automatically omitted.
{
  "passwords": [
    {
      "name": "password",
      "value": "f02Ls3jqnNQ0ToXIoyY2g8oJrVk0w5P/"
    },
    {
      "name": "password2",
      "value": "qbZx1bZT7=rha7Ta6Wa0zfCZqoNMNoj1"
    }
  ],
  "username": "contosoresourcegroup1610068510"
}
$ export AKS_PERS_ACR_PASSWORD=<the-password-from-your-output>
```

Use the `create-docker-credentials-secret.sh` script to store the ACR credentials as a Kubernetes secret.

```shell
# cd kubernetes/samples/scripts/create-kuberetes-secrets
$ export SECRET_NAME_DOCKER="regsecret"
$ ./create-docker-credentials-secret.sh -s ${SECRET_NAME_DOCKER} -e $AKS_PERS_RESOURCE_GROUP -p $AKS_PERS_ACR_PASSWORD -u $AKS_PERS_RESOURCE_GROUP -d $AKS_PERS_ACR -n sample-domain1-ns
secret/regsecret created
The secret regsecret has been successfully created in the sample-domain1-ns namespace.
```

##### Domain resource

Now, you create a Domain YAML file. Think of the Domain YAML file as the way to configure some aspects of your WebLogic domain using Kubernetes.  The operator uses the Kubernetes "custom resource" feature to define a Kubernetes resource type called `Domain`.  For more on the `Domain` Kubernetes resource, see [Domain Resource]({{< relref "/userguide/managing-domains/domain-resource" >}}). For more on custom resources see [the Kubernetes documentation](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/).

We provide a sample file at `kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml`, copy it to a file called `/tmp/mii-sample/mii-initial.yaml`.

```bash
$ cd kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources/WLS
$ cp mii-initial-d1-WLS-v1.yaml /tmp/mii-sample/mii-initial.yaml
```

Modify the Domain YAML with your values.

| Name in YAML file | Example value | Notes |
|-------------------|---------------|-------|
|`spec.image`|`$AKS_PERS_ACR/$AKS_PERS_ACR:model-in-image-aks`|Must be the same as the value to which you pushed the image to by running the command `docker push $AKS_PERS_ACR/$AKS_PERS_ACR:model-in-image-aks`.|
|`spec.imagePullSecrets.name`|`regsecret`|Make sure its value is the same value with `${SECRET_NAME_DOCKER}`.|

Run the following command to create the domain custom resource:

```bash
$ kubectl apply -f /tmp/mii-sample/mii-initial.yaml
```

Successfull output will look like:

```
domain.weblogic.oracle/sample-domain1 created
```

Verify the WebLogic Server pods are all running:

```bash
$ kubectl get pods -n sample-domain1-ns --watch
NAME                                READY   STATUS              RESTARTS   AGE
sample-domain1-introspector-xwpbn   0/1     ContainerCreating   0          0s
sample-domain1-introspector-xwpbn   1/1     Running             0          1s
sample-domain1-introspector-xwpbn   0/1     Completed           0          66s
sample-domain1-introspector-xwpbn   0/1     Terminating         0          67s
sample-domain1-introspector-xwpbn   0/1     Terminating         0          67s
sample-domain1-admin-server         0/1     Pending             0          0s
sample-domain1-admin-server         0/1     Pending             0          0s
sample-domain1-admin-server         0/1     ContainerCreating   0          0s
sample-domain1-admin-server         0/1     Running             0          2s
sample-domain1-admin-server         1/1     Running             0          42s
sample-domain1-managed-server1      0/1     Pending             0          0s
sample-domain1-managed-server1      0/1     Pending             0          0s
sample-domain1-managed-server1      0/1     ContainerCreating   0          0s
sample-domain1-managed-server2      0/1     Pending             0          0s
sample-domain1-managed-server2      0/1     Pending             0          0s
sample-domain1-managed-server2      0/1     ContainerCreating   0          0s
sample-domain1-managed-server2      0/1     Running             0          3s
sample-domain1-managed-server2      1/1     Running             0          40s
sample-domain1-managed-server1      0/1     Running             0          53s
sample-domain1-managed-server1      1/1     Running             0          93s

# The success deployment should be:
$ kubectl get all -n sample-domain1-ns
NAME                                 READY   STATUS    RESTARTS   AGE
pod/sample-domain1-admin-server      1/1     Running   0          16m
pod/sample-domain1-managed-server1   1/1     Running   0          15m
pod/sample-domain1-managed-server2   1/1     Running   0          15m

NAME                                       TYPE        CLUSTER-IP    EXTERNAL-IP   PORT(S)    AGE
service/sample-domain1-admin-server        ClusterIP   None          <none>        7001/TCP   16m
service/sample-domain1-cluster-cluster-1   ClusterIP   10.0.188.60   <none>        8001/TCP   15m
service/sample-domain1-managed-server1     ClusterIP   None          <none>        8001/TCP   15m
service/sample-domain1-managed-server2     ClusterIP   None          <none>        8001/TCP   15m
```

It may take you up to 10 minutes to deploy all pods, please wait and make sure everything is ready.

#### Invoke the web application

##### Create Azure load balancer

Create the Azure public standard load balancer to access the WebLogic Server Administration Console and applications deployed in the cluster.

Use the configuration file in `kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service/model-in-image/admin-lb.yaml` to create a load balancer service for the Administration Server. If you are choosing not to use the predefined YAML file and instead created a new one with customized values, then substitute the following content with you domain values.

{{%expand "Click here to view YAML content." %}}
```yaml
apiVersion: v1
kind: Service
metadata:
  name: sample-domain1-admin-server-external-lb
  namespace: sample-domain1-ns
spec:
  ports:
  - name: default
    port: 7001
    protocol: TCP
    targetPort: 7001
  selector:
    weblogic.domainUID: sample-domain1
    weblogic.serverName: admin-server
  sessionAffinity: None
  type: LoadBalancer
```
{{% /expand %}}

Use the configuration file in `kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service/model-in-image/cluster-lb.yaml` to create a load balancer service for the managed servers. If you are choosing not to use the predefined YAML file and instead created new one with customized values, then substitute the following content with you domain values.

{{%expand "Click here to view YAML content." %}}
```yaml
apiVersion: v1
kind: Service
metadata:
  name: sample-domain1-cluster-1-lb
  namespace: sample-domain1-ns
spec:
  ports:
  - name: default
    port: 8001
    protocol: TCP
    targetPort: 8001
  selector:
    weblogic.domainUID: sample-domain1
    weblogic.clusterName: cluster-1
  sessionAffinity: None
  type: LoadBalancer

```
{{% /expand %}}

Create the load balancer services using the following command:

```bash
$ cd kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service/model-in-image
$ kubectl apply -f admin-lb.yaml
service/sample-domain1-admin-server-external-lb created

$ kubectl  apply -f cluster-lb.yaml
service/sample-domain1-cluster-1-external-lb created
```

Get the external IP addresses of the Administration Server and cluster load balancers (please wait for the external IP addresses to be assigned):

```bash
$ kubectl get svc -n sample-domain1-ns --watch
NAME                                      TYPE           CLUSTER-IP     EXTERNAL-IP      PORT(S)          AGE
sample-domain1-admin-server               ClusterIP      None           <none>           7001/TCP         8m33s
sample-domain1-admin-server-external-lb   LoadBalancer   10.0.184.118   52.191.234.149   7001:30655/TCP   2m30s
sample-domain1-cluster-1-lb               LoadBalancer   10.0.76.7      52.191.235.71    8001:30439/TCP   2m25s
sample-domain1-cluster-cluster-1          ClusterIP      10.0.118.225   <none>           8001/TCP         7m53s
sample-domain1-managed-server1            ClusterIP      None           <none>           8001/TCP         7m53s
sample-domain1-managed-server2            ClusterIP      None           <none>           8001/TCP         7m52s
```

In the example, the URL to access the Administration Server is: `http://52.191.234.149:7001/console`.  **IMPORTANT: You must ensure that any Network Security Group rules that govern access to the console allow inbound traffic on port 7001.** The default user name for the Administration Console is `weblogic` and the default password is `welcome1`.  Please change this for production deployments.

If the WLS Administration Console is still not available, use `kubectl describe domain` to check domain status.

```bash
$ kubectl describe domain domain1
```

Make sure the status of cluster-1 is `ServersReady` and `Available`.

{{%expand "Click here to view the example domain status." %}}
```yaml
Name:         sample-domain1
Namespace:    sample-domain1-ns
Labels:       weblogic.domainUID=sample-domain1
Annotations:  <none>
API Version:  weblogic.oracle/v8
Kind:         Domain
Metadata:
  Creation Timestamp:  2020-11-30T05:40:11Z
  Generation:          1
  Resource Version:    9346
  Self Link:           /apis/weblogic.oracle/v8/namespaces/sample-domain1-ns/domains/sample-domain1
  UID:                 9f10a602-714a-46c5-8dcb-815616b587af
Spec:
  Admin Server:
    Server Start State:  RUNNING
  Clusters:
    Cluster Name:  cluster-1
    Replicas:      2
    Server Pod:
      Affinity:
        Pod Anti Affinity:
          Preferred During Scheduling Ignored During Execution:
            Pod Affinity Term:
              Label Selector:
                Match Expressions:
                  Key:       weblogic.clusterName
                  Operator:  In
                  Values:
                    $(CLUSTER_NAME)
              Topology Key:  kubernetes.io/hostname
            Weight:          100
    Server Start State:      RUNNING
  Configuration:
    Model:
      Domain Type:                WLS
      Runtime Encryption Secret:  sample-domain1-runtime-encryption-secret
  Domain Home:                    /u01/domains/sample-domain1
  Domain Home Source Type:        FromModel
  Image:                          docker.io/sleepycat2/wls-on-aks:model-in-image
  Image Pull Policy:              IfNotPresent
  Image Pull Secrets:
    Name:                         regsecret
  Include Server Out In Pod Log:  true
  Replicas:                       1
  Restart Version:                1
  Server Pod:
    Env:
      Name:   CUSTOM_DOMAIN_NAME
      Value:  domain1
      Name:   JAVA_OPTIONS
      Value:  -Dweblogic.StdoutDebugEnabled=false
      Name:   USER_MEM_ARGS
      Value:  -Djava.security.egd=file:/dev/./urandom -Xms256m -Xmx512m
    Resources:
      Requests:
        Cpu:            250m
        Memory:         768Mi
  Server Start Policy:  IF_NEEDED
  Web Logic Credentials Secret:
    Name:  sample-domain1-weblogic-credentials
Status:
  Clusters:
    Cluster Name:      cluster-1
    Maximum Replicas:  5
    Minimum Replicas:  0
    Ready Replicas:    2
    Replicas:          2
    Replicas Goal:     2
  Conditions:
    Last Transition Time:        2020-11-30T05:45:15.493Z
    Reason:                      ServersReady
    Status:                      True
    Type:                        Available
  Introspect Job Failure Count:  0
  Replicas:                      2
  Servers:
    Desired State:  RUNNING
    Health:
      Activation Time:  2020-11-30T05:44:15.652Z
      Overall Health:   ok
      Subsystems:
        Subsystem Name:  ServerRuntime
        Symptoms:
    Node Name:      aks-pool1model-71528953-vmss000001
    Server Name:    admin-server
    State:          RUNNING
    Cluster Name:   cluster-1
    Desired State:  RUNNING
    Health:
      Activation Time:  2020-11-30T05:44:54.699Z
      Overall Health:   ok
      Subsystems:
        Subsystem Name:  ServerRuntime
        Symptoms:
    Node Name:      aks-pool1model-71528953-vmss000000
    Server Name:    managed-server1
    State:          RUNNING
    Cluster Name:   cluster-1
    Desired State:  RUNNING
    Health:
      Activation Time:  2020-11-30T05:45:07.211Z
      Overall Health:   ok
      Subsystems:
        Subsystem Name:  ServerRuntime
        Symptoms:
    Node Name:      aks-pool1model-71528953-vmss000001
    Server Name:    managed-server2
    State:          RUNNING
    Cluster Name:   cluster-1
    Desired State:  SHUTDOWN
    Server Name:    managed-server3
    Cluster Name:   cluster-1
    Desired State:  SHUTDOWN
    Server Name:    managed-server4
    Cluster Name:   cluster-1
    Desired State:  SHUTDOWN
    Server Name:    managed-server5
  Start Time:       2020-11-30T05:40:11.709Z
Events:             <none>
```
{{% /expand %}}

##### Access the application

Access the Administration Console using the admin load balancer IP address, `http://52.191.234.149:7001/console`

Access the sample application using the cluster load balancer IP address.

```bash
## Access the sample application using the cluster load balancer IP (52.191.235.71)
$ curl http://52.191.235.71:8001/myapp_war/index.jsp

<html><body><pre>
*****************************************************************

Hello World! This is version 'v1' of the mii-sample JSP web-app.

Welcome to WebLogic Server 'managed-server1'!

 domain UID  = 'sample-domain1'
 domain name = 'domain1'

Found 1 local cluster runtime:
  Cluster 'cluster-1'

Found 0 local data sources:

*****************************************************************
</pre></body></html>

$ curl http://52.191.235.71:8001/myapp_war/index.jsp

<html><body><pre>
*****************************************************************

Hello World! This is version 'v1' of the mii-sample JSP web-app.

Welcome to WebLogic Server 'managed-server2'!

 domain UID  = 'sample-domain1'
 domain name = 'domain1'

Found 1 local cluster runtime:
  Cluster 'cluster-1'

Found 0 local data sources:

*****************************************************************
</pre></body></html>
```

#### Rolling updates

Naturally, you will want to deploy newer versions of the EAR application, located in the WDT archive ZIP file at `wlsdeploy/applications/myapp-v1`. To learn how to do this, follow the steps in [Update 3]({{< relref "/samples/simple/domains/model-in-image/update3" >}}).

#### Clean up resources

Run the following commands to clean up resources.

{{< readfile file="/samples/simple/azure-kubernetes-service/includes/clean-up-resources-body-02.txt" >}}

#### Troubleshooting

For troubleshooting advice, see [Troubleshooting]({{< relref "/samples/simple/azure-kubernetes-service/troubleshooting.md" >}}).

#### Useful links

- [Model in Image]({{< relref "/userguide/managing-domains/model-in-image/_index.md" >}}) user documentation
- [Model in Image]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}}) sample

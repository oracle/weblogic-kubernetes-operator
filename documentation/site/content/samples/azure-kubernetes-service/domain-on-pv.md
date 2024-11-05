---
title: "Domain home on a PV"
date: 2023-09-14T18:22:31-05:00
weight: 2
description: "Sample for creating a WebLogic domain home on an existing PV or PVC on the Azure Kubernetes Service."
---

This sample demonstrates how to use the [WebLogic Kubernetes Operator](https://oracle.github.io/weblogic-kubernetes-operator) (hereafter "the operator") to set up a WebLogic Server (WLS) cluster on the Azure Kubernetes Service (AKS) using the domain on PV approach. After going through the steps, your WLS domain runs on an AKS cluster and you can manage your WLS domain by accessing the WebLogic Server Administration Console.

#### Contents

 - [Prerequisites](#prerequisites)
 - [Create Resource Group](#create-resource-group)
 - [Create an AKS cluster](#create-the-aks-cluster)
 - [Create and configure storage](#create-storage)
   - [Create an Azure Storage account and NFS share](#create-an-azure-storage-account-and-nfs-share)
   - [Create SC and PVC](#create-sc-and-pvc)
 - [Create a domain creation image](#create-a-domain-creation-image)
 - [Install WebLogic Kubernetes Operator](#install-weblogic-kubernetes-operator-into-the-aks-cluster)
 - [Create WebLogic domain](#create-weblogic-domain)
   - [Create secrets](#create-secrets)
   - [Create WebLogic Domain](#create-weblogic-domain-1)
   - [Create LoadBalancer](#create-loadbalancer)
 - [Automation](#automation)
 - [Access sample application](#access-sample-application)
 - [Validate NFS volume](#validate-nfs-volume)
 - [Clean up resources](#clean-up-resources)
 - [Troubleshooting](#troubleshooting)
 - [Useful links](#useful-links)

{{< readfile file="/samples/azure-kubernetes-service/includes/prerequisites-01.txt" >}}

##### Prepare parameters

Set required parameters by running the following commands.

```shell
# Change these parameters as needed for your own environment
export ORACLE_SSO_EMAIL=<replace with your oracle account email>
export ORACLE_SSO_PASSWORD="<replace with your oracle password>"

# Specify a prefix to name resources, only allow lowercase letters and numbers, between 1 and 7 characters
export BASE_DIR=~
export NAME_PREFIX=wls
export WEBLOGIC_USERNAME=weblogic
export WEBLOGIC_PASSWORD=Secret123456
export domainUID=domain1
# Used to generate resource names.
export TIMESTAMP=`date +%s`
export AKS_CLUSTER_NAME="${NAME_PREFIX}aks${TIMESTAMP}"
export AKS_PERS_RESOURCE_GROUP="${NAME_PREFIX}resourcegroup${TIMESTAMP}"
export AKS_PERS_LOCATION=eastus
export AKS_PERS_STORAGE_ACCOUNT_NAME="${NAME_PREFIX}storage${TIMESTAMP}"
export AKS_PERS_SHARE_NAME="${NAME_PREFIX}-weblogic-${TIMESTAMP}"
export SECRET_NAME_DOCKER="${NAME_PREFIX}regcred"
export ACR_NAME="${NAME_PREFIX}acr${TIMESTAMP}"

```

{{< readfile file="/samples/azure-kubernetes-service/includes/create-aks-cluster-body-01.txt" >}}

{{< readfile file="/samples/azure-kubernetes-service/includes/sign-in-azure.txt" >}}

{{< readfile file="/samples/azure-kubernetes-service/includes/download-samples-zip.txt" >}}

{{% notice info %}} The following sections of the sample instructions will guide you, step-by-step, through the process of setting up a WebLogic cluster on AKS - remaining as close as possible to a native Kubernetes experience. This lets you understand and customize each step. If you wish to have a more automated experience that abstracts some lower level details, you can skip to the [Automation](#automation) section.
{{% /notice %}}

{{< readfile file="/samples/azure-kubernetes-service/includes/create-resource-group.txt" >}}

{{< readfile file="/samples/azure-kubernetes-service/includes/create-aks-cluster-body-02.txt" >}}

 **NOTE**: If you run into VM size failure, see [Troubleshooting - Virtual Machine size is not supported]({{< relref "/samples/azure-kubernetes-service/troubleshooting#virtual-machine-size-is-not-supported" >}}).

{{< readfile file="/samples/azure-kubernetes-service/includes/create-aks-cluster-storage.txt" >}}

#### Create a domain creation image

This sample requires [Domain creation images]({{< relref "/managing-domains/domain-on-pv/domain-creation-images" >}}). For more information, see [Domain on Persistent Volume]({{< relref "/managing-domains/domain-on-pv/overview" >}}).

  - [Image creation prerequisites](#image-creation-prerequisites)
  - [Image creation - Introduction](#image-creation---introduction)
  - [Understanding your first archive](#understanding-your-first-archive)
  - [Staging a ZIP file of the archive](#staging-a-zip-file-of-the-archive)
  - [Staging model files](#staging-model-files)
  - [Creating the image with WIT](#creating-the-image-with-wit)
  - [Pushing the image to Azure Container Registry](#pushing-the-image-to-azure-container-registry)

##### Image creation prerequisites

- The `JAVA_HOME` environment variable must be set and must reference a valid JDK 8 or 11 installation.
- Copy the sample to a new directory; for example, use the directory `/tmp/dpv-sample`. In the directory name, `dpv` is short for "domain on pv". Domain on PV is one of three domain home source types supported by the operator. To learn more, see [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).

   ```shell
   $ rm -rf /tmp/dpv-sample
   $ mkdir /tmp/dpv-sample
   ```

   ```shell
   $ cp -r $BASE_DIR/sample-scripts/create-weblogic-domain/domain-on-pv/* /tmp/dpv-sample
   ```

   **NOTE**: We will refer to this working copy of the sample as `/tmp/dpv-sample`; however, you can use a different location.
- Copy the `wdt-artifacts` directory of the sample to a new directory; for example, use directory `/tmp/dpv-sample/wdt-artifacts`

   ```shell
   $ cp -r $BASE_DIR/sample-scripts/create-weblogic-domain/wdt-artifacts/* /tmp/dpv-sample
   ```

   ```shell
   $ export WDT_MODEL_FILES_PATH=/tmp/dpv-sample/wdt-model-files
   ```

{{< readfile file="/samples/azure-kubernetes-service/includes/download-wls-tools.txt" >}}

##### Image creation - Introduction

{{< readfile file="/samples/azure-kubernetes-service/includes/auxiliary-image-directory.txt" >}}

##### Understanding your first archive

See [Understanding your first archive]({{< relref "/samples/domains/domain-home-on-pv/build-domain-creation-image#understand-your-first-archive" >}}).

##### Staging a ZIP file of the archive

Delete any possible existing archive.zip in case we have an old leftover version.

```shell
$ rm -f ${WDT_MODEL_FILES_PATH}/WLS-v1/archive.zip
```

Create a ZIP file of the archive in the location that we will use when we run the WebLogic Image Tool.

```shell
$ cd /tmp/dpv-sample/archives/archive-v1
$ zip -r ${WDT_MODEL_FILES_PATH}/WLS-v1/archive.zip wlsdeploy
```

##### Staging model files

{{< readfile file="/samples/azure-kubernetes-service/includes/staging-model-files.txt" >}}

An image can contain multiple properties files, archive ZIP files, and model YAML files but in this sample you use just one of each. For a complete description of WDT model file naming conventions, file loading order, and macro syntax, see [Model files]({{< relref "/managing-domains/domain-on-pv/model-files" >}}) in the user documentation.

##### Creating the image with WIT

{{< readfile file="/samples/azure-kubernetes-service/includes/run-mii-to-create-auxiliary-image.txt" >}}

{{% notice note %}}
The `imagetool.sh` is not supported on macOS with Apple Silicon. See [Troubleshooting - exec format error]({{< relref "/samples/azure-kubernetes-service/troubleshooting#exec-weblogic-operatorscriptsintrospectdomainsh-exec-format-error" >}}).
{{% /notice %}}

{{% notice note %}}
You may run into a `Dockerfile` parsing error if your Docker buildkit is enabled, see [Troubleshooting - WebLogic Image Tool failure]({{< relref "/samples/azure-kubernetes-service/troubleshooting#weblogic-image-tool-failure" >}}).
{{% /notice %}}

##### Pushing the image to Azure Container Registry

{{< readfile file="/samples/azure-kubernetes-service/includes/create-acr.txt" >}}

Push the `wdt-domain-image:WLS-v1` image created while satisfying the preconditions to this registry.

```shell
$ docker tag wdt-domain-image:WLS-v1 $LOGIN_SERVER/wdt-domain-image:WLS-v1
$ docker push ${LOGIN_SERVER}/wdt-domain-image:WLS-v1
```

{{< readfile file="/samples/azure-kubernetes-service/includes/aks-connect-acr.txt" >}}

If you see an error that seems related to you not being an **Owner on this subscription**, please refer to the troubleshooting section [Cannot attach ACR due to not being Owner of subscription]({{< relref "/samples/azure-kubernetes-service/troubleshooting#cannot-attach-acr-due-to-not-being-owner-of-subscription" >}}).

#### Install WebLogic Kubernetes Operator into the AKS cluster

The WebLogic Kubernetes Operator is an adapter to integrate WebLogic Server and Kubernetes, allowing Kubernetes to serve as a container infrastructure hosting WLS instances.  The operator runs as a Kubernetes Pod and stands ready to perform actions related to running WLS on Kubernetes.

Kubernetes Operators use [Helm](https://helm.sh/) to manage Kubernetes applications. The operator’s Helm chart is located in the `kubernetes/charts/weblogic-operator` directory. Please install the operator by running the corresponding command.

```shell
$ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update
$ helm repo update
$ helm install weblogic-operator weblogic-operator/weblogic-operator
```

The output will show something similar to the following:

```shell
$ helm install weblogic-operator weblogic-operator/weblogic-operator
NAME: weblogic-operator
LAST DEPLOYED: Tue Jan 18 17:07:56 2022
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None
```

Verify the operator with the following command; the `STATUS` must be `Running`.  The `READY` must be `1/1`.

```shell
$ kubectl get pods -w
```
```
NAME                                         READY   STATUS    RESTARTS   AGE
weblogic-operator-69794f8df7-bmvj9           1/1     Running   0          86s
weblogic-operator-webhook-868db5875b-55v7r   1/1     Running   0          86s
```

{{% notice tip %}} You will have to press Ctrl-C to exit this command due to the `-w` flag.
{{% /notice %}}

#### Create WebLogic domain

  - [Create secrets](#create-secrets)
  - [Create WebLogic Domain](#create-weblogic-domain-1)
  - [Create LoadBalancer](#create-loadbalancer)

Now that you have created the AKS cluster, installed the operator, and verified that the operator is ready to go, you can ask the operator to create a WLS domain.

##### Create secrets

You will use the `$BASE_DIR/sample-scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh` script to create the domain WebLogic administrator credentials as a Kubernetes secret. Please run the following commands:

```
cd $BASE_DIR/sample-scripts/create-weblogic-domain-credentials
```
```shell
$ ./create-weblogic-credentials.sh -u ${WEBLOGIC_USERNAME} -p ${WEBLOGIC_PASSWORD} -d domain1
```

The output will show something similar to the following:

```
secret/domain1-weblogic-credentials created
secret/domain1-weblogic-credentials labeled
The secret domain1-weblogic-credentials has been successfully created in the default namespace.
```

You will use the `kubernetes/samples/scripts/create-kubernetes-secrets/create-docker-credentials-secret.sh` script to create the Docker credentials as a Kubernetes secret. Please run the following commands:

``` shell
$ cd $BASE_DIR/sample-scripts/create-kubernetes-secrets
$ ./create-docker-credentials-secret.sh -s ${SECRET_NAME_DOCKER} -e ${ORACLE_SSO_EMAIL} -p ${ORACLE_SSO_PASSWORD} -u ${ORACLE_SSO_EMAIL}
```

The output will show something similar to the following:

```
secret/wlsregcred created
The secret wlsregcred has been successfully created in the default namespace.
```

Verify secrets with the following command:

```shell
$ kubectl get secret
```

The output will show something similar to the following:

```
NAME                                      TYPE                             DATA   AGE
domain1-weblogic-credentials              Opaque                           2      2m32s
sh.helm.release.v1.weblogic-operator.v1   helm.sh/release.v1               1      5m32s
weblogic-operator-secrets                 Opaque                           1      5m31s
weblogic-webhook-secrets                  Opaque                           2      5m31s
wlsregcred                                kubernetes.io/dockerconfigjson   1      38s
```

**NOTE**: If the `NAME` column in your output is missing any of the values shown above, please review your execution of the preceding steps in this sample to ensure that you correctly followed all of them.

##### Enable Weblogic Operator

Run the following command to enable the operator to monitor the namespace.

```shell
kubectl label namespace default weblogic-operator=enabled
```

##### Create WebLogic Domain
Now, you deploy a `sample-domain1` domain resource and an associated `sample-domain1-cluster-1` cluster resource using a single YAML resource file which defines both resources. The domain resource and cluster resource tells the operator how to deploy a WebLogic domain. They do not replace the traditional WebLogic configuration files, but instead cooperate with those files to describe the Kubernetes artifacts of the corresponding domain.

- Run the following commands to generate resource files.

    Export `Domain_Creation_Image_tag`, which will be referred in `create-domain-on-aks-generate-yaml.sh`.

    ```shell
    export Domain_Creation_Image_tag=${LOGIN_SERVER}/wdt-domain-image:WLS-v1
    ```

    ```shell
    cd $BASE_DIR/sample-scripts/create-weblogic-domain-on-azure-kubernetes-service  

    bash create-domain-on-aks-generate-yaml.sh
    ```

After running above commands, you will get three files: `domain-resource.yaml`, `admin-lb.yaml`, `cluster-lb.yaml`.

The domain resource references the cluster resource, a WebLogic Server installation image, the secrets you defined, PV and PVC configuration details, and a sample `domain creation image`, which contains a traditional WebLogic configuration and a WebLogic application. For detailed information, see [Domain and cluster resources]({{< relref "/managing-domains/domain-resource.md" >}}).

- Run the following command to apply the two sample resources.
    ```shell
    $ kubectl apply -f domain-resource.yaml
    ```

- Create the load balancer services using the following commands:

  ```shell
  $ kubectl apply -f admin-lb.yaml
  ```

  The output will show something similar to the following:

  ```
  service/domain1-admin-server-external-lb created
  ```

  ```shell
  $ kubectl  apply -f cluster-lb.yaml
  ```

  The output will show something similar to the following:

  ```
  service/domain1-cluster-1-external-lb created
  ```

  After a short time, you will see the Administration Server and Managed Servers running.

  Use the following command to check server pod status:

  ```shell
  $ kubectl get pods --watch
  ```

  It may take you up to 20 minutes to deploy all pods, please wait and make sure everything is ready.

  You can tail the logs of the Administration Server with this command:

  ```shell
  kubectl logs -f domain1-admin-server
  ```

  The final example of pod output is as following:

  ```shell
  $ kubectl get pods 
  ```
  ```
  NAME                                        READY   STATUS    RESTARTS   AGE
  domain1-admin-server                        1/1     Running   0          12m
  domain1-managed-server1                     1/1     Running   0          10m
  domain1-managed-server2                     1/1     Running   0          10m
  weblogic-operator-7796bc7b8-qmhzw           1/1     Running   0          48m
  weblogic-operator-webhook-b5b586bc5-ksfg9   1/1     Running   0          48m
  ```

  {{% notice tip %}} If Kubernetes advertises the WebLogic pod as `Running` you can be assured the WebLogic Server actually is running because the operator ensures that the Kubernetes health checks are actually polling the WebLogic health check mechanism.
  {{% /notice %}}

  Get the addresses of the Administration Server and Managed Servers (please wait for the external IP addresses to be assigned):

  ```shell
  $ kubectl get svc --watch
  ```

  The final example of service output is as following:

  ```shell
  $ kubectl get svc --watch
  ```
  ```
    NAME                               TYPE           CLUSTER-IP     EXTERNAL-IP     PORT(S)             AGE
    domain1-admin-server               ClusterIP      None           <none>          7001/TCP            13m
    domain1-admin-server-external-lb   LoadBalancer   10.0.30.252    4.157.147.131   7001:31878/TCP      37m
    domain1-cluster-1-lb               LoadBalancer   10.0.26.96     4.157.147.212   8001:32318/TCP      37m
    domain1-cluster-cluster-1          ClusterIP      10.0.157.174   <none>          8001/TCP            10m
    domain1-managed-server1            ClusterIP      None           <none>          8001/TCP            10m
    domain1-managed-server2            ClusterIP      None           <none>          8001/TCP            10m
    kubernetes                         ClusterIP      10.0.0.1       <none>          443/TCP             60m
    weblogic-operator-webhook-svc      ClusterIP      10.0.41.121    <none>          8083/TCP,8084/TCP   49m

  ```

  In the example, the URL to access the Administration Server is: `http://4.157.147.131:7001/console`.
  The user name and password that you enter for the Administration Console must match the ones you specified for the `domain1-weblogic-credentials` secret in the [Create secrets](#create-secrets) step.

  If the WLS Administration Console is still not available, use `kubectl get events --sort-by='.metadata.creationTimestamp' ` to troubleshoot.

  ```shell
  $ kubectl get events --sort-by='.metadata.creationTimestamp'
  ```

To access the sample application on WLS, skip to the section [Access sample application](#access-sample-application). The next section includes a script that automates all of the preceding steps.

#### Automation

If you want to automate the above steps of creating the AKS cluster and WLS domain, you can use the script `${BASE_DIR}/sample-scripts/create-weblogic-domain-on-azure-kubernetes-service/create-domain-on-aks.sh`.

The sample script will create a WLS domain home on the AKS cluster, including:
  - Creating a new Azure resource group, with a new Azure Storage Account and Azure File Share to allow WebLogic to persist its configuration and data separately from the Kubernetes pods that run WLS workloads.
  - Creating WLS domain home.
  - Generating the domain resource YAML files, which can be used to restart the Kubernetes artifacts of the corresponding domain.

To customize the WLS domain, you can optionally edit `${BASE_DIR}/sample-scripts/create-weblogic-domain-on-azure-kubernetes-service/create-domain-on-aks-inputs.sh`.

You can now run the script.

```shell
$ cd ${BASE_DIR}/sample-scripts/create-weblogic-domain-on-azure-kubernetes-service
```

```shell
$ ./create-domain-on-aks.sh 
```

The script will take some time to run. The script will print the Administration Server address after a successful deployment.
To interact with the cluster using `kubectl`, use `az aks get-credentials` as shown in the script output.

{{% notice info %}} You now have created an AKS cluster with Azure Files NFS share to contain the WLS domain configuration files.  Using those artifacts, you have used the operator to create a WLS domain.
{{% /notice %}}

#### Access sample application

Access the Administration Console using the admin load balancer IP address.

```shell
$ ADMIN_SERVER_IP=$(kubectl get svc domain1-admin-server-external-lb -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')
$ echo "Administration Console Address: http://${ADMIN_SERVER_IP}:7001/console/"
```

Access the sample application using the cluster load balancer IP address.

```shell
$ CLUSTER_IP=$(kubectl get svc domain1-cluster-1-lb -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')
```

```shell
$ curl http://${CLUSTER_IP}:8001/myapp_war/index.jsp
```

The test application will list the server host on the output, like the following:

```html
<html><body><pre>
*****************************************************************

Hello World! This is version 'v1' of the sample JSP web-app.

Welcome to WebLogic Server 'managed-server1'!

  domain UID  = 'domain1'
  domain name = 'domain1'

Found 1 local cluster runtime:
  Cluster 'cluster-1'

Found min threads constraint runtime named 'SampleMinThreads' with configured count: 1

Found max threads constraint runtime named 'SampleMaxThreads' with configured count: 10

Found 0 local data sources:

*****************************************************************
</pre></body></html>
```

#### Validate NFS volume

There are several approaches to validate the NFS volume:

- Use Azure Storage browser. Make sure you have permission to access the NFS server, see [Azure Storage firewalls and virtual networks document](https://docs.microsoft.com/en-us/azure/storage/common/storage-network-security?tabs=azure-portal)
- Mount the same NFS share in an existing virtual machine from Azure. Access files from the mounted path, see [Mount Azure NFS file share to Linux](https://docs.microsoft.com/en-us/azure/storage/files/storage-files-how-to-mount-nfs-shares).

Use `kubectl exec` to enter the admin server pod to check file system status:

```shell
kubectl exec -it domain1-admin-server -- df -h
```

You will find output like the following, with filesystem `${AKS_PERS_STORAGE_ACCOUNT_NAME}.file.core.windows.net:/${AKS_PERS_STORAGE_ACCOUNT_NAME}/${AKS_PERS_SHARE_NAME}`, size `100G`, and mounted on `/shared`:

```text
Filesystem                                                                                Size  Used Avail Use% Mounted on
...
wlsstorage1612795811.file.core.windows.net:/wlsstorage1612795811/wls-weblogic-1612795811  100G   76M  100G   1% /shared
...
```

#### Clean up resources

{{< readfile file="/samples/azure-kubernetes-service/includes/clean-up-resources-body-01.txt" >}}

If you created the AKS cluster step by step, run the following command to clean up resources.

{{< readfile file="/samples/azure-kubernetes-service/includes/clean-up-resources-body-02.txt" >}}

#### Troubleshooting

For troubleshooting advice, see [Troubleshooting]({{< relref "/samples/azure-kubernetes-service/troubleshooting.md" >}}).

#### Useful links

- [Domain on a PV]({{< relref "/samples/domains/domain-home-on-pv/_index.md" >}}) sample

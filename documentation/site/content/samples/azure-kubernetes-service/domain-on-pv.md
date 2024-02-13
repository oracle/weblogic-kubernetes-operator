---
title: "Domain home on a PV"
date: 2023-09-14T18:22:31-05:00
weight: 2
description: "Sample for creating a WebLogic domain home on an existing PV or PVC on the Azure Kubernetes Service."
---

This sample demonstrates how to use the [WebLogic Kubernetes Operator](https://oracle.github.io/weblogic-kubernetes-operator) (hereafter "the operator") to set up a WebLogic Server (WLS) cluster on the Azure Kubernetes Service (AKS) using the domain on PV approach. After going through the steps, your WLS domain runs on an AKS cluster instance and you can manage your WLS domain by accessing the WebLogic Server Administration Console.

#### Contents

 - [Prerequisites](#prerequisites)
 - [Prepare Parameters](#prepare-parameters)
 - [Clone WKO repository](#clone-wko-repository)
 - [Create Resource Group](#create-resource-group)
 - [Create an AKS cluster](#create-the-aks-cluster)
 - [Create and Configure Storage](#create-storage)
   - [Create an Azure Storage account and NFS share](#create-an-azure-storage-account-and-nfs-share)
   - [Create SC and PVC](#create-sc-and-pvc)
 - [Install WebLogic Kubernetes Operator](#install-weblogic-kubernetes-operator-into-the-aks-cluster)
 - [Create WebLogic domain](#create-weblogic-domain)
   - [Create secrets](#create-secrets)
   - [Create WebLogic Domain](#create-weblogic-domain-1)
   - [Create LoadBalancer](#create-loadbalancer)
 - [Automation](#automation)
 - [Deploy sample application](#deploy-sample-application)
 - [Validate NFS volume](#validate-nfs-volume)
 - [Clean up resources](#clean-up-resources)
 - [Troubleshooting](#troubleshooting)
 - [Useful links](#useful-links)

{{< readfile file="/samples/azure-kubernetes-service/includes/prerequisites-01.txt" >}}

{{< readfile file="/samples/azure-kubernetes-service/includes/create-aks-cluster-body-01.txt" >}}

##### Sign in with Azure CLI

The steps in this section show you how to sign in to the Azure CLI.

1. Open a Bash shell.

1. Sign out and delete some authentication files to remove any lingering credentials.

   ```shell
   $ az logout
   $ rm ~/.azure/accessTokens.json
   $ rm ~/.azure/azureProfile.json
   ```

1. Sign in to your Azure CLI.

   ```shell
   $ az login
   ```

1. Set the subscription ID. Be sure to replace the placeholder with the appropriate value.

   ```shell
   $ export SUBSCRIPTION_ID=<your-subscription-id>
   $ az account set -s $SUBSCRIPTION_ID
   ```

{{% notice info %}} The following sections of the sample instructions will guide you, step-by-step, through the process of setting up a WebLogic cluster on AKS - remaining as close as possible to a native Kubernetes experience. This lets you understand and customize each step. If you wish to have a more automated experience that abstracts some lower level details, you can skip to the [Automation](#automation) section.
{{% /notice %}}

#### Prepare parameters

```shell
# Change these parameters as needed for your own environment
export ORACLE_SSO_EMAIL=<replace with your oracle account email>
export ORACLE_SSO_PASSWORD=<replace with your oracle password>

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
export ACR_ACCOUNT_NAME="${NAME_PREFIX}acr${TIMESTAMP}"

```

#### Clone WKO repository

If you have not already done so, clone the [WebLogic Kubernetes Operator repository](https://github.com/oracle/weblogic-kubernetes-operator) to your machine. You will use several scripts in this repository to create a WebLogic domain. This sample was tested with v4.1.1, but should work later releases.

```shell
$ cd $BASE_DIR
$ git clone https://github.com/oracle/weblogic-kubernetes-operator.git

```

{{< readfile file="/samples/azure-kubernetes-service/includes/create-resource-group.txt" >}}

{{< readfile file="/samples/azure-kubernetes-service/includes/create-aks-cluster-body-02.txt" >}}

 **NOTE**: If you run into VM size failure, see [Troubleshooting - Virtual Machine size is not supported]({{< relref "/samples/azure-kubernetes-service/troubleshooting#virtual-machine-size-is-not-supported" >}}).

{{< readfile file="/samples/azure-kubernetes-service/includes/create-aks-cluster-storage.txt" >}}

#### Create the Azure Container Registry and connect it to the AKS cluster

Your AKS cluster must be connected to a container registry so it can pull and interact with container images. The WebLogic Kubernetes Operator assumes that the docker images in the container registry have the correct structure so they are ready to run as WebLogic Docker images. The WebLogic Image Toolkit you used when satisfying the preconditions produces images that meet this requirement. In particular the image `wdt-domain-image:WLS-v1`. The steps in this section show you how to create an Azure Container Registry, connect it to your existing AKS cluster, and push the `wdt-domain-image:WLS-v1` to this registry.

Create the Azure Container Registry in your existing resource group.

```shell
az acr create --resource-group $AKS_PERS_RESOURCE_GROUP --name ${ACR_ACCOUNT_NAME} --sku Basic --admin-enabled
```

Successful output will be a JSON object that includes the property.

```json
"id": "/subscriptions/<your subscription id>/resourceGroups/<your resource group>/providers/Microsoft.ContainerRegistry/registries/<your aks cluster name>"
```

Obtain the credentials to the Azure Container Registry and perform the `docker login`.

```shell
export LOGIN_SERVER=$(az acr show \
    --name ${ACR_ACCOUNT_NAME} \
    --query 'loginServer' \
    --output tsv)
export USER_NAME=$(az acr credential show \
    --name ${ACR_ACCOUNT_NAME} \
    --query 'username' \
    --output tsv)
export PASSWORD=$(az acr credential show \
    --name ${ACR_ACCOUNT_NAME} \
    --query 'passwords[0].value' \
    --output tsv)

docker login $LOGIN_SERVER -u $USER_NAME -p $PASSWORD
```

Push the `wdt-domain-image:WLS-v1` image created while satisfying the preconditions to this registry.

```shell
docker push ${LOGIN_SERVER}/wdt-domain-image:WLS-v1
```

Set an environment variable for use in a later script.

```shell
# An example of Domain_Creation_Image_tag: xxx.azurecr.io/wdt-domain-image:WLS-v1
export Domain_Creation_Image_tag=${LOGIN_SERVER}/wdt-domain-image:WLS-v1
```

Connect the Azure Container Registry to your existing AKS cluster.

```shell
az aks update --name ${AKS_CLUSTER_NAME} --resource-group $AKS_PERS_RESOURCE_GROUP --attach-acr ${ACR_ACCOUNT_NAME}
```

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

You will use the `kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh` script to create the domain WebLogic administrator credentials as a Kubernetes secret. Please run:

```
cd $BASE_DIR/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain-credentials
```
```shell
$ ./create-weblogic-credentials.sh -u ${WEBLOGIC_USERNAME} -p ${WEBLOGIC_PASSWORD} -d domain1
```
```
secret/domain1-weblogic-credentials created
secret/domain1-weblogic-credentials labeled
The secret domain1-weblogic-credentials has been successfully created in the default namespace.
```


You will use the `kubernetes/samples/scripts/create-kubernetes-secrets/create-docker-credentials-secret.sh` script to create the Docker credentials as a Kubernetes secret. Please run:

``` shell
$ cd $BASE_DIR/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-kubernetes-secrets
$ ./create-docker-credentials-secret.sh -s ${SECRET_NAME_DOCKER} -e ${ORACLE_SSO_EMAIL} -p ${ORACLE_SSO_PASSWORD} -u ${ORACLE_SSO_EMAIL}
```
```
secret/wlsregcred created
The secret wlsregcred has been successfully created in the default namespace.
```

Verify secrets with the following command:

```shell
$ kubectl get secret
```
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

- Run the following command to generate resource files.

    ```shell
    cd $BASE_DIR/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service  

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
  ```
  service/domain1-admin-server-external-lb created
  ```
  ```shell
  $ kubectl  apply -f cluster-lb.yaml
  ```
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

  In the example, the URL to access the Administration Server is: `http://4.157.147.131/console`.
  The user name and password that you enter for the Administration Console must match the ones you specified for the `domain1-weblogic-credentials` secret in the [Create secrets](#create-secrets) step.

  If the WLS Administration Console is still not available, use `kubectl get events --sort-by='.metadata.creationTimestamp' ` to troubleshoot.

  ```shell
  $ kubectl get events --sort-by='.metadata.creationTimestamp'
  ```

To deploy a sample application on WLS, you may skip to the section [Deploy sample application](#deploy-sample-application).  The next section includes a script that automates all of the preceding steps.

#### Automation

If you want to automate the above steps of creating AKS cluster and WLS domain, you can use the script `kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service/create-domain-on-aks.sh`.

The sample script will create a WLS domain home on the AKS cluster, including:
  - Creating a new Azure resource group, with a new Azure Storage Account and Azure File Share to allow WebLogic to persist its configuration and data separately from the Kubernetes pods that run WLS workloads.
  - Creating WLS domain home.
  - Generating the domain resource YAML files, which can be used to restart the Kubernetes artifacts of the corresponding domain.

For input values, you can edit `kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service/create-domain-on-aks-inputs.sh` directly. The following values must be specified:

| Name in YAML file | Example value       | Notes                                                                                          |
|-------------------|---------------------|------------------------------------------------------------------------------------------------|
| `dockerEmail` | `yourDockerEmail`   | Oracle Single Sign-On (SSO) account email, used to pull the WebLogic Server Docker image.      |
| `dockerPassword` | `yourDockerPassword` | Password for Oracle SSO account, used to pull the WebLogic Server Docker image, in clear text. |
| `weblogicUserName` | `weblogic`          | Uername for WebLogic user account.                                                             |
| `weblogicAccountPassword` | `Secret123456` | Password for WebLogic user account.                                                            |

```
cd kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service
```

```shell
$ ./create-domain-on-aks.sh 
```

The script will print the Administration Server address after a successful deployment.
To interact with the cluster using `kubectl`, use `az aks get-credentials` as shown in the script output.

{{% notice info %}} You now have created an AKS cluster with Azure Files NFS share to contain the WLS domain configuration files.  Using those artifacts, you have used the operator to create a WLS domain.
{{% /notice %}}

#### Deploy sample application

Now that you have WLS running in AKS, you can test the cluster by deploying the simple sample application included in the repository.

First, package the application with the following command:

```bash
cd $BASE_DIR/weblogic-kubernetes-operator/integration-tests/src/test/resources/bash-scripts
bash build-war-app.sh -s ../apps/testwebapp/ -d /tmp/testwebapp
```

Successful output will look similar to the following:

```text
Found source at ../apps/testwebapp/
build /tmp/testwebapp/testwebapp.war with command jar -cvf /tmp/testwebapp/testwebapp.war *
added manifest
ignoring entry META-INF/
ignoring entry META-INF/MANIFEST.MF
adding: META-INF/maven/(in = 0) (out= 0)(stored 0%)
adding: META-INF/maven/com.oracle.weblogic/(in = 0) (out= 0)(stored 0%)
adding: META-INF/maven/com.oracle.weblogic/testwebapp/(in = 0) (out= 0)(stored 0%)
adding: META-INF/maven/com.oracle.weblogic/testwebapp/pom.properties(in = 117) (out= 113)(deflated 3%)
adding: META-INF/maven/com.oracle.weblogic/testwebapp/pom.xml(in = 1210) (out= 443)(deflated 63%)
adding: WEB-INF/(in = 0) (out= 0)(stored 0%)
adding: WEB-INF/web.xml(in = 951) (out= 428)(deflated 54%)
adding: WEB-INF/weblogic.xml(in = 1140) (out= 468)(deflated 58%)
adding: index.jsp(in = 1001) (out= 459)(deflated 54%)
-rw-r--r-- 1 user user 3528 Jul  5 14:25 /tmp/testwebapp/testwebapp.war
```

Now, you are able to deploy the sample application in `/tmp/testwebapp/testwebapp.war` to the cluster. This sample uses WLS RESTful API [/management/weblogic/latest/edit/appDeployments](https://docs.oracle.com/en/middleware/standalone/weblogic-server/14.1.1.0/wlrer/op-management-weblogic-version-edit-appdeployments-x-operations-1.html) to deploy the sample application.
Replace `${WEBLOGIC_USERNAME}` and `${WEBLOGIC_PASSWORD}` with the values you specified in [Create secrets](#create-secrets) or [Automation](#automation):

```bash
$ ADMIN_SERVER_IP=$(kubectl get svc domain1-admin-server-external-lb -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')
$ curl --user ${WEBLOGIC_USERNAME}:${WEBLOGIC_PASSWORD} -H X-Requested-By:MyClient  -H Accept:application/json -s -v \
  -H Content-Type:multipart/form-data  \
  -F "model={
        name:    'testwebapp',
        targets: [ { identity: [ 'clusters', 'cluster-1' ] } ]
      }" \
  -F "sourcePath=@/tmp/testwebapp/testwebapp.war" \
  -H "Prefer:respond-async" \
  -X POST http://${ADMIN_SERVER_IP}:7001/management/weblogic/latest/edit/appDeployments
```

After the successful deployment, you will find output similar to the following:

{{%expand "Click here to view the output." %}}
```text
*   Trying 52.226.101.43:7001...
* TCP_NODELAY set
* Connected to 52.226.101.43 (52.226.101.43) port 7001 (#0)
* Server auth using Basic with user 'weblogic'
> POST /management/weblogic/latest/edit/appDeployments HTTP/1.1
> Host: 52.226.101.43:7001
> Authorization: Basic ...=
> User-Agent: curl/7.68.0
> X-Requested-By:MyClient
> Accept:application/json
> Prefer:respond-async
> Content-Length: 3925
> Content-Type: multipart/form-data; boundary=------------------------cc76a2c2d819911f
> Expect: 100-continue
>
* Mark bundle as not supporting multiuse
< HTTP/1.1 100 Continue
* We are completely uploaded and fine
* Mark bundle as not supporting multiuse
< HTTP/1.1 202 Accepted
< Date: Thu, 11 Aug 2022 08:32:56 GMT
< Location: http://domain1-admin-server:7001/management/weblogic/latest/domainRuntime/deploymentManager/deploymentProgressObjects/testwebapp
< Content-Length: 764
< Content-Type: application/json
< X-ORACLE-DMS-ECID: 6f205c83-e172-4c34-a638-7f0c6345ce45-00000055
< X-ORACLE-DMS-RID: 0
< Set-Cookie: JSESSIONID=NOCMCQBO7dxyA2lUfCYp4zSYIeFB0S3V1KRRzigmmoOUfmQmlLOh!-546081476; path=/; HttpOnly
< Vary: Accept-Encoding
<
{
    "links": [{
        "rel": "job",
        "href": "http:\/\/domain1-admin-server:7001\/management\/weblogic\/latest\/domainRuntime\/deploymentManager\/deploymentProgressObjects\/testwebapp"
    }],
    "identity": [
        "deploymentManager",
        "deploymentProgressObjects",
        "testwebapp"
    ],
    "rootExceptions": [],
    "deploymentMessages": [],
    "name": "testwebapp",
    "operationType": 3,
    "startTimeAsLong": 1660206785965,
    "state": "STATE_RUNNING",
    "id": "0",
    "type": "DeploymentProgressObject",
    "targets": ["cluster-1"],
    "applicationName": "testwebapp",
    "failedTargets": [],
    "progress": "processing",
    "completed": false,
    "intervalToPoll": 1000,
    "startTime": "2022-08-11T08:33:05.965Z"
* Connection #0 to host 52.226.101.43 left intact
```
{{% /expand %}}

Now, you can go to the application through the `domain1-cluster-1-lb` external IP.

```shell
$ CLUSTER_IP=$(kubectl get svc domain1-cluster-1-lb -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')

$ curl http://${CLUSTER_IP}:8001/testwebapp/
```

The test application will list the server host and server IP on the output, like the following:

```html
<!DOCTYPE html>
<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">

    <link rel="stylesheet" href="/testwebapp/res/styles.css;jsessionid=9uiMDakndtPlZTyDB9A-OKZEFBBAPyIs_9bG3qC4uA3PYaI8DsH1!-1450005246" type="text/css">
    <title>Test WebApp</title>
  </head>
  <body>


    <li>InetAddress: domain1-managed-server1/10.244.1.8
    <li>InetAddress.hostname: domain1-managed-server1

  </body>
</html>
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

If you created the AKS cluster step by step, run the following commands to clean up resources.

{{< readfile file="/samples/azure-kubernetes-service/includes/clean-up-resources-body-02.txt" >}}

#### Troubleshooting

For troubleshooting advice, see [Troubleshooting]({{< relref "/samples/azure-kubernetes-service/troubleshooting.md" >}}).

#### Useful links

- [Domain on a PV]({{< relref "/samples/domains/domain-home-on-pv/_index.md" >}}) sample

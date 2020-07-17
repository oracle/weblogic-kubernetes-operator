---
title: "Azure Kubernetes Service"
date: 2020-07-12T18:22:31-05:00
weight: 8
description: "Sample for using the operator to set up a WLS cluster on the Azure Kubernetes Service."
---

This sample demonstrates how to use the [Oracle WebLogic Server Kubernetes Operator](/weblogic-kubernetes-operator/) (hereafter "the operator") to set up a WebLogic Server (WLS) cluster on the Azure Kubernetes Service (AKS). After going through the steps, your WLS cluster domain runs on an AKS cluster instance and you can manage your WLS domain by accessing the WebLogic Server Administration Console.

AKS is a managed Kubernetes Service that lets you quickly deploy and manage Kubernetes clusters. To learn more, please see the [Azure Kubernetes Service (AKS)](https://docs.microsoft.com/azure/aks/) overview page.

#### Contents

 - [Prerequisites](#prerequisites)
 - [Create the AKS cluster](#create-the-aks-cluster)
 - [Create storage and set up file share](#create-storage-and-set-up-file-share)
 - [Install WebLogic Operator](#install-weblogic-operator)
 - [Create WebLogic domain](#create-weblogic-domain)
 - [Automation](#automation)
 - [Deploy sample application](#deploy-sample-application)
 - [Access WebLogic Server logs](#access-weblogic-server-logs)
 - [Troubleshooting](#troubleshooting)
 - [Useful links](#useful-links)

#### Prerequisites

This sample assumes the following prerequisites.

##### Environment for setup

This sample assumes one of the following two execution environments:

1. Run the commands on your local computer. This allows for the greatest flexibility while requiring some setup effort.
1. Run the commands in the Azure Cloud Shell. Cloud Shell is a browser based utility and runs on the Azure portal. This option may be best for users already familiar with the utility and Azure. It is also suitable for users wanting to avoid installing additional software on their local computer.

* Local Environment Setup

  * Operating System: Linux, Unix, macOS, [WSL for Windows 10](https://docs.microsoft.com/windows/wsl/install-win10)
  * [Git](https://git-scm.com/downloads), use `git --version` to test if `git` works.
  * [Azure CLI](https://docs.microsoft.com/cli/azure), use `az --version` to test if `az` works.
  * [kubectl](https://kubernetes-io-vnext-staging.netlify.com/docs/tasks/tools/install-kubectl/), use `kubectl version` to test if `kubectl` works.
  * [helm](https://helm.sh/docs/intro/install/), version 3.1 and above, use `helm version` to check the `helm` version.

* Azure Cloud Shell

  The Azure Cloud Shell already has the necessary prerequisites installed. 
  To start the Azure Cloud Shell, please go to [Overview of Azure Cloud Shell](https://docs.microsoft.com/azure/cloud-shell/overview).

##### Create Service Principal for AKS

An AKS cluster requires either an [Azure Active Directory (AD) service principal](https://docs.microsoft.com/azure/active-directory/develop/app-objects-and-service-principals) or a [managed identity](https://docs.microsoft.com/azure/aks/use-managed-identity) to interact with Azure resources.

We will use a service principal to create an AKS cluster. Follow the commands below to create a new service principal.

If you run commands in your local environment, please run `az login` first. Skip that command if you run on the Azure Cloud Shell. Do set the subscription you want to work with. You can get a list of your subscriptions by running `az account list`.

```bash
# Login
az login

SUBSCRIPTION_ID=<your-subscription-id>

# Set your working subscription
az account set -s $SUBSCRIPTION_ID

```

Create the new service principal with the following commands:

```bash
SP_NAME=myAKSClusterServicePrincipal

# Create Service Principal

az ad sp create-for-rbac --skip-assignment --name $SP_NAME

# Copy the output to a file, we will use it to
# Specifically we will need the app ID, client secret and tenant ID later.
```

If you see an error similar to the following

```bash
Found an existing application instance of "5ca2f201-ad4d-43a1-a942-c9e9571de3ec". We will patch it
Insufficient privileges to complete the operation.
```

The problem may be a pre-existing service principal with the same name.  Either delete the other Service Principal or pick a different name.

Grant your service principal with a contributor role to create AKS resources.

```bash
# Use the <appId> from the output of the last command
az role assignment create --assignee <appId> --role Contributor
```

##### Docker Hub

You will need a Docker Hub account. If you don't have an existing account, please sign up for a new account at [DockerHub](https://hub.docker.com/). Note down your user name, password and  email for Docker Hub. Because this sample uses a Docker image for a specific version of WLS, and WLS requires accepting license terms, do a Docker Hub "checkout" of [Oracle WebLogic Server](https://hub.docker.com/_/oracle-weblogic-server-12c). This sample was written with 12.2.1.3, but other versions may work as well.

##### Clone WebLogic Operator repository

Please clone this repository to your machine. We will use several scripts in this repository to create a WebLogic domain.

```bash
git clone https://github.com/oracle/weblogic-kubernetes-operator.git
```
  
{{% notice info %}} The following sections of the sample will take you step-by-step through the process of setting up a WebLogic cluster on AKS - remaining as close as possible to a native Kubernetes experience. This allows you to understand and customize each step. If you wish to have a more automated experience that abstracts some lower level details, you can skip to the [Automation](#automation) section.
{{% /notice %}}

#### Create the AKS cluster

This sample requires we disable http-application-routing by default.  If you want to enable  http_application_routing, please follow [HTTP application routing](https://docs.microsoft.com/azure/aks/http-application-routing).

Run the following commands to create the AKS cluster instance.

```bash
# Change these parameters as needed for your own environment
AKS_CLUSTER_NAME=WLSSimpleCluster
AKS_PERS_RESOURCE_GROUP=wls-simple-cluster
AKS_PERS_LOCATION=eastus
SP_APP_ID=<service-principal-app-id>
SP_CLIENT_SECRET=<service-principal-client-secret>

az group create --name $AKS_PERS_RESOURCE_GROUP --location $AKS_PERS_LOCATION
az aks create \
   --resource-group $AKS_PERS_RESOURCE_GROUP \
   --name $AKS_CLUSTER_NAME \
   --node-count 3 \
   --generate-ssh-keys \
   --nodepool-name nodepool1 \
   --node-vm-size Standard_D4s_v3 \
   --location $AKS_PERS_LOCATION \
   --service-principal $SP_APP_ID \
   --client-secret $SP_CLIENT_SECRET
```

After the deployment finishes, run the following command to connect to the AKS cluster. This command updates your local `~/.kube/config` so that subsequent `kubectl` commands interact with the named AKS cluster.

```bash
az aks get-credentials --resource-group $AKS_PERS_RESOURCE_GROUP --name $AKS_CLUSTER_NAME
```

To verify the connection to your cluster, use the `kubectl get` command to return a list of the cluster nodes.

```bash
kubectl get nodes
```

Example output:

```bash
NAME                                STATUS   ROLES   AGE     VERSION
aks-nodepool1-15992006-vmss000000   Ready    agent   7m49s   v1.15.11
aks-nodepool1-15992006-vmss000001   Ready    agent   7m32s   v1.15.11
aks-nodepool1-15992006-vmss000002   Ready    agent   7m52s   v1.15.11
```

#### Create storage and set up file share

Our usage pattern for the operator involves creating Kubernetes "persistent volumes" to allow the WebLogic Server to persist its configuration and data separately from the Kubernetes pods that run WebLogic Server workloads.

We will create an external data volume to access and persist data. There are several options for data sharing as described in [Storage options for applications in Azure Kubernetes Service (AKS)](https://docs.microsoft.com/azure/aks/concepts-storage).

We will use Azure Files as a Kubernetes volume. Consult the [Azure Files Documentation](https://docs.microsoft.com/azure/aks/azure-files-volume) for details about this full featured cloud storage solution.

Create a storage account first, please note that the storage account name can contain only lowercase letters and numbers, between 3 and 24 characters:

```bash
# Change the value as needed for your own environment
AKS_PERS_STORAGE_ACCOUNT_NAME=wlssimplestorageacct

az storage account create \
   -n $AKS_PERS_STORAGE_ACCOUNT_NAME \
   -g $AKS_PERS_RESOURCE_GROUP \
   -l $AKS_PERS_LOCATION \
   --sku Standard_LRS
```

Now we need to create a file share. We need a storage connection string to create the file share. Run the `show-connection-string` command to get connection string, then create the share with `az storage share create`, as shown here.

```bash
# Change value as needed for your own environment
AKS_PERS_SHARE_NAME=weblogic

export AZURE_STORAGE_CONNECTION_STRING=$(az storage account show-connection-string -n $AKS_PERS_STORAGE_ACCOUNT_NAME -g $AKS_PERS_RESOURCE_GROUP -o tsv)

az storage share create -n $AKS_PERS_SHARE_NAME --connection-string $AZURE_STORAGE_CONNECTION_STRING
```

The operator uses Kubernetes secrets.  We need a storage key for the secret. These commands query the storage account to obtain the key, and then stores the storage account key as a Kubernetes secret.

```bash
STORAGE_KEY=$(az storage account keys list --resource-group $AKS_PERS_RESOURCE_GROUP --account-name $AKS_PERS_STORAGE_ACCOUNT_NAME --query "[0].value" -o tsv)
```

We will use the `kubernetes/samples/scripts/create-kuberetes-secrets/create-azure-storage-credentials-secret.sh` script to create the storage account key as a Kubernetes secret, leaving the secret name with the default value `azure-secret`. For example:

```bash
./create-azure-storage-credentials-secret.sh -a $AKS_PERS_STORAGE_ACCOUNT_NAME -k $STORAGE_KEY
```

##### Generate configuration files

This sample uses Kubernetes Persistent Volume Claims (PVC) and load balancing to bring WLS to AKS.  These features are expressed to Kubernetes using yaml files.  The script `kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service/create-domain-on-aks.sh` generates the required configuration files automatically, given an input file containing the parameters.  A parameters file is provided at `kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service/create-domain-on-aks-inputs.yaml`.  Copy and customize this file for your needs.

For example, given the service principal created above, the following values must be substituted in your copy of the input file.

| Name in yaml file | Example value | Notes |
|-------------------|---------------|-------|
| `azureServicePrincipalAppId` | `nr086o75-pn59-4782-no5n-nq2op0rsr1q6` | `appId` |
| `azureServicePrincipalClientSecret` | `8693089o-q190-45ps-9319-or36252s3s90` | `password` |
| `azureServicePrincipalTenantId` | `72s988os-86s1-cafe-babe-2q7pq011qo47` | `tenant` |
| `dockerEmail` | `yourDockerEmail` | The email address corresponding to the docker user name |
| `dockerPassword` | `yourDockerPassword`| Your docker password in clear text |
| `dockerUserName` | `yourDockerId` ||

Use the following command to generate configuration files, assuming the output directory is `~/azure`

```bash
#cd kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service
mkdir ~/azure
cp create-domain-on-aks-inputs.yaml my-create-domain-on-aks-inputs.yaml
./create-domain-on-aks.sh -i my-create-domain-on-aks-inputs.yaml -o ~/azure
```

After running the command, all needed configuration files are generated and output to `~/azure/weblogic-on-aks`:

```bash
The following files were generated:
  /home/username/azure/weblogic-on-aks/pv.yaml
  /home/username/azure/weblogic-on-aks/pvc.yaml
  /home/username/azure/weblogic-on-aks/admin-lb.yaml
  /home/username/azure/weblogic-on-aks/cluster-lb.yaml
  /home/username/azure/weblogic-on-aks/domain1.yaml

Completed
```

##### Apply generated configuration files

In order to mount the file share as a persistent volume, we have provided a configuration file `pv.yaml`. You can find it in your output directory. The following content is an example that uses the default value `weblogic` as "shareName", `azure-secret` as "secretName", and the default persistent volume claim name `azurefile`.

```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: azurefile
spec:
  capacity:
    storage: 5Gi
  accessModes:
    - ReadWriteMany
  storageClassName: azurefile
  azureFile:
    secretName: azure-secret
    shareName: weblogic
    readOnly: false
  mountOptions:
  - dir_mode=0777
  - file_mode=0777
  - uid=1000
  - gid=1000
  - mfsymlinks
  - nobrl
```

We have provided another configuration file `pvc.yaml` for the PersistentVolumeClaim.  Both `pv.yaml` and `pvc.yaml` have exactly the same content in the `metadata` and `storageClassName` attributes. This is required. The following content is an example that uses the default persistent volume claim name `azurefile` for "storageClassName".

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: azurefile
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: azurefile
  resources:
    requests:
      storage: 5Gi
```

Use the `kubectl` command to create the persistent volume and persistent volume claim.

```bash
kubectl apply -f ~/azure/weblogic-on-aks/pv.yaml
kubectl apply -f ~/azure/weblogic-on-aks/pvc.yaml
```

You should see the following output after each command, respectively.

```bash
persistentvolume/azurefile created
persistentvolumeclaim/azurefile created
```

Use the following command to verify:

```bash
kubectl get pv,pvc
```

Example output:

```bash
NAME        CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS   CLAIM               STORAGECLASS   REASON   AGE
azurefile   5Gi        RWX            Retain           Bound    default/azurefile   azurefile               2d21h

NAME        STATUS   VOLUME      CAPACITY   ACCESS MODES   STORAGECLASS   AGE
azurefile   Bound    azurefile   5Gi        RWX            azurefile      2d21h
```

> **Note**: Carefully inspect the output and verify it matches the above. `ACCESS MODES`, `CLAIM`, and `STORAGECLASS` are vital.

#### Install WebLogic Operator

The Oracle WebLogic Server Kubernetes Operator (the operator) is an adapter to integrate WebLogic Server and Kubernetes, allowing Kubernetes to serve as a container infrastructure hosting WLS instances.  The operator runs as a Kubernetes pod and stands ready to perform actions related to running WLS on Kubernetes.

Kubernetes Operators use [Helm](https://helm.sh/) to manage Kubernetes applications. You have to grant the Helm service account with the `cluster-admin` role with the following command.

```bash
cat <<EOF | kubectl apply -f -
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: helm-user-cluster-admin-role
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: default
  namespace: kube-system
EOF
```

You should see this output:

```bash
clusterrolebinding.rbac.authorization.k8s.io/helm-user-cluster-admin-role created
```

Install the WebLogic Server Operator. The operator’s Helm chart is located in the `kubernetes/charts/weblogic-operator` directory. Please check the Helm version first if you are using the Azure Cloud Shell, and run the corresponding command.

```bash
# Check the helm version
helm version

# For Helm 3.x, run the following
helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts
helm repo update
helm install weblogic-operator weblogic-operator/weblogic-operator

# For helm 2.x, run the following
helm init
helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts
helm repo update
helm install weblogic-operator/weblogic-operator --name weblogic-operator
```

The output should show something similar to the following.

```bash
NAME: weblogic-operator
LAST DEPLOYED: Wed Jul  1 23:47:44 2020
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None
```

Verify the operator with the following command, the status should be running.

```bash
kubectl get pods -w
```

Example output:

```bash
NAME                                              READY   STATUS      RESTARTS   AGE
weblogic-operator-6655cdc949-x58ts                1/1     Running     0          2d21h
```

{{% notice tip %}} You will have to press Ctrl-C to exit this command due to the `-w` flag.
{{% /notice %}}

#### Create WebLogic domain

Now that we have created the AKS cluster, installed the WLS operator, and verified the operator is ready to go, we can ask the operator to create a WLS domain.

1. We will use the `kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh` script to create the domain credentials as a Kubernetes secret.

   ```bash
   #cd weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain-credentials
   ./create-weblogic-credentials.sh -u weblogic -p welcome1 -d domain1
   ```

   The successful output should look similar to the following:

   ```bash
   secret/domain1-weblogic-credentials created
   secret/domain1-weblogic-credentials labeled
   The secret domain1-weblogic-credentials has been successfully created in the default namespace.
    ```

2. We will use the `kubernetes/samples/scripts/create-kuberetes-secrets/create-docker-credentials-secret.sh` script to create the docker credentials as a Kubernetes secret.  For example:

   ```bash
   ./create-docker-credentials-secret.sh -e foo@bar.com -p myDockerPassword -u myDockerUserId
   ```

   Verify secrets with the following command:

   ```bash
   kubectl get secret
   ```

   Example output:

   ```text
   NAME                                      TYPE                                  DATA   AGE
   azure-secret                              Opaque                                2      2d21h
   default-token-mwdj8                       kubernetes.io/service-account-token   3      2d22h
   domain1-weblogic-credentials              Opaque                                2      2d21h
   regcred                                   kubernetes.io/dockerconfigjson        1      2d20h
   sh.helm.release.v1.weblogic-operator.v1   helm.sh/release.v1                    1      2d21h
   weblogic-operator-secrets                 Opaque                                1      2d21h
   ```

   > **Note**: In the `NAME` column in your output is missing any of the values shown above, please reexamine your execution of the preceding steps in this sample to ensure you correctly followed all of them.  The `default-token-mwdj8` shown above will have a different ending in your output.

3. We will use `kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain.sh` script to create the WLS domain in the persistent volume we created previously.

   First, we need to set up domain configuration for the WebLogic domain.  This step uses the configuration generated previously.

   ```bash
   #cd weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv
   ./create-domain.sh -i ~/azure/weblogic-on-aks/domain1.yaml -o ~/azure -e -v
   ```

   You may observe error related output during the creation of the domain.  This is due to timing issues during domain creation.  The script accounts for this with a series of retries.  The error output looks similar to the following.

   ```text
   Waiting for the job to complete...
   Waiting for the job to complete...
   Error from server (BadRequest): container "create-weblogic-sample-domain-job" in pod domain1-create-weblogic-sample-domain-job-9pnxj" is waiting to start: PodInitializing
   status on iteration 1 of 20
   pod domain1-create-weblogic-sample-domain-job-9pnxj status is Init:0/1
   status on iteration 2 of 20
   ```

   The following example output shows the WebLogic domain was created successfully.

   ```bash
   NAME: weblogic-operator
   LAST DEPLOYED: Mon Mar 30 10:29:58 2020
   NAMESPACE: default
   STATUS: deployed
   REVISION: 1
   TEST SUITE: None
   fatal: destination path 'weblogic-kubernetes-operator' already exists and is not an empty directory.
   secret/domain1-weblogic-credentials created
   secret/domain1-weblogic-credentials labeled
   The secret domain1-weblogic-credentials has been successfully created in the default namespace.
   secret/regcred created
   Input parameters being used
   export version="create-weblogic-sample-domain-inputs-v1"
   export adminPort="7001"
   export adminServerName="admin-server"
   export domainUID="domain1"
   export domainHome="/shared/domains/domain1"
   export serverStartPolicy="IF_NEEDED"
   export clusterName="cluster-1"
   export configuredManagedServerCount="5"
   export initialManagedServerReplicas="2"
   export managedServerNameBase="managed-server"
   export managedServerPort="8001"
   export image="store/oracle/weblogic:12.2.1.3"
   export imagePullPolicy="IfNotPresent"
   export imagePullSecretName="regcred"
   export productionModeEnabled="true"
   export weblogicCredentialsSecretName="domain1-weblogic-credentials"
   export includeServerOutInPodLog="true"
   export logHome="/shared/logs/domain1"
   export t3ChannelPort="30012"
   export exposeAdminT3Channel="false"
   export adminNodePort="30701"
   export exposeAdminNodePort="true"
   export namespace="default"
   javaOptions=-Dweblogic.StdoutDebugEnabled=false
   export persistentVolumeClaimName="azurefile"
   export domainPVMountPath="/shared"
   export createDomainScriptsMountPath="/u01/weblogic"
   export createDomainScriptName="create-domain-job.sh"
   export createDomainFilesDir="wlst"
   export istioEnabled="false"
   export istioReadinessPort="8888"

   Generating /home/username/azure/weblogic-domains/domain1/create-domain-job.yaml
   Generating /home/username/azure/weblogic-domains/domain1/delete-domain-job.yaml
   Generating /home/username/azure/weblogic-domains/domain1/domain.yaml
   Checking to see if the secret domain1-weblogic-credentials exists in namespace default
   Checking if the persistent volume claim azurefile in NameSpace default exists
   The persistent volume claim azurefile already exists in NameSpace default
   configmap/domain1-create-weblogic-sample-domain-job-cm created
   Checking the configmap domain1-create-weblogic-sample-domain-job-cm was created
   configmap/domain1-create-weblogic-sample-domain-job-cm labeled
   Checking if object type job with name domain1-create-weblogic-sample-domain-job exists
   No resources found in default namespace.
   Creating the domain by creating the job /home/username/azure/weblogic-domains/domain1/create-domain-job.yaml
   job.batch/domain1-create-weblogic-sample-domain-job created
   Waiting for the job to complete...
   Error from server (BadRequest): container "create-weblogic-sample-domain-job" in pod "domain1-create-weblogic-sample-domain-job-p5htr" is waiting to start: PodInitializing
   status on iteration 1 of 20
   pod domain1-create-weblogic-sample-domain-job-p5htr status is Init:0/1
   Error from server (BadRequest): container "create-weblogic-sample-domain-job" in pod "domain1-create-weblogic-sample-domain-job-p5htr" is waiting to start: PodInitializing
   status on iteration 2 of 20
   pod domain1-create-weblogic-sample-domain-job-p5htr status is Init:0/1
   status on iteration 3 of 20
   pod domain1-create-weblogic-sample-domain-job-p5htr status is Completed
   domain.weblogic.oracle/domain1 created

   Domain domain1 was created and will be started by the WebLogic Kubernetes Operator

   Administration console access is available at http://jyffvzcyrp-jyf-nxf-fvzcyr-p-685on0-35nns494.upc.rnfghf.nmzx8f.io:30701/console
   The following files were generated:
     /home/username/azure/weblogic-domains/domain1/create-domain-inputs.yaml
     /home/username/azure/weblogic-domains/domain1/create-domain-job.yaml
     /home/username/azure/weblogic-domains/domain1/domain.yaml

   Completed
   ```

   > **Note**: If your output does not show a successful completion, you must
   troubleshoot the reason and resolve it before proceeding to the next
   step.

4. You must create `LoadBalancer` service for the Administration Server and the WLS cluster.  This enables WLS to service requests from outside the AKS cluster.

   Use the configuration file in `~/azure/weblogic-on-aks/admin-lb.yaml` to create a load balancer service for the Administration Server. The following content is an example of `admin-lb.yaml`, with default domain uid  `domain1`, server name `admin-server`, and default port `7001`.

   ```yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: domain1-admin-server-external-lb
     namespace: default
   spec:
     ports:
     - name: default
       port: 7001
       protocol: TCP
       targetPort: 7001
     selector:
       weblogic.domainUID: domain1
       weblogic.serverName: admin-server
     sessionAffinity: None
     type: LoadBalancer
   ```

   Create the load balancer service using the following command.

   ```bash
   kubectl apply -f ~/azure/weblogic-on-aks/admin-lb.yaml
   ```

   You should see the following output:

   ```text
   service/domain1-admin-server-external-lb created
   ```

   Use the configuration file in `~/azure/weblogic-on-aks/cluster-lb.yaml` to create a load balancer service for the managed servers. The following content is an example of `cluster-lb.yaml`, with default domain uid `domain1`, cluster name `cluster-1`, and default managed server port `8001`.

   ```yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: domain1-cluster-1-lb
     namespace: default
   spec:
     ports:
     - name: default
       port: 8001
       protocol: TCP
       targetPort: 8001
     selector:
       weblogic.domainUID: domain1
       weblogic.clusterName: cluster-1
     sessionAffinity: None
     type: LoadBalancer
   ```

   Create the load balancer service using the following command:

   ```bash
   kubectl  apply -f ~/azure/weblogic-on-aks/cluster-lb.yaml
   ```

   You should see the following output:

   ```text
   service/domain1-cluster-1-external-lb created
   ```

   After a short time, you will see the Administration Server and Managed Servers running. 

   Use the following command to check server pod status:

   ```bash
   kubectl get pods --watch
   ```

   It may take you up to 20 minutes to deploy all pods, please wait and make sure everything is ready. The final example of pod output is as following:

   ```bash
   NAME                                              READY   STATUS      RESTARTS   AGE
   domain1-admin-server                              1/1     Running     0          9m42s
   domain1-create-weblogic-sample-domain-job-qqmkn   0/1     Completed   0          11m
   domain1-managed-server1                           1/1     Running     0          3m1s
   domain1-managed-server2                           1/1     Running     0          3m1s
   weblogic-operator-754447455c-kbmnm                1/1     Running     0          12m
   ```

   Get the addresses of the Administration Server and Managed Servers (please wait for the external IP addresses to be assigned):

   ```bash
   kubectl get svc --watch
   ```

   The final example of servcie output is as following:

   ```bash
   NAME                               TYPE           CLUSTER-IP    EXTERNAL-IP      PORT(S)              AGE
   domain1-admin-server               ClusterIP      None          <none>           30012/TCP,7001/TCP   2d20h
   domain1-admin-server-external      NodePort       10.0.182.50   <none>           7001:30701/TCP       2d20h
   domain1-admin-server-external-lb   LoadBalancer   10.0.67.79    52.188.176.103   7001:32227/TCP       2d20h
   domain1-cluster-1-lb               LoadBalancer   10.0.112.43   104.45.176.215   8001:30874/TCP       2d17h
   domain1-cluster-cluster-1          ClusterIP      10.0.162.19   <none>           8001/TCP             2d20h
   domain1-managed-server1            ClusterIP      None          <none>           8001/TCP             2d20h
   domain1-managed-server2            ClusterIP      None          <none>           8001/TCP             2d20h
   internal-weblogic-operator-svc     ClusterIP      10.0.192.13   <none>           8082/TCP             2d22h
   kubernetes                         ClusterIP      10.0.0.1      <none>           443/TCP              2d22h
   ```

   In the example, the URL to access the Administration Server is: `http://52.188.176.103:7001/console`.  The default user name for the Administration Console is `weblogic` and the default password is `welcome1`.  Please change this for production deployments.

   If the Administration Console is still not available, use `kubectl describe domain` to check domain status.

   ```bash
   kubectl describe domain domain1
   ```

   Make sure the status of cluster-1 is `ServersReady` and `Available`.

   ```yaml
   Status:
    Clusters:
      Cluster Name:      cluster-1
      Maximum Replicas:  5
      Minimum Replicas:  1
      Ready Replicas:    2
      Replicas:          2
      Replicas Goal:     2
    Conditions:
      Last Transition Time:  2020-07-06T05:39:32.539Z
      Reason:                ServersReady
      Status:                True
      Type:                  Available
    Replicas:                2
    Servers:
      Desired State:  RUNNING
      Node Name:      aks-nodepool1-11471722-vmss000001
      Server Name:    admin-server
      State:          RUNNING
      Cluster Name:   cluster-1
      Desired State:  RUNNING
      Node Name:      aks-nodepool1-11471722-vmss000001
      Server Name:    managed-server1
      State:          RUNNING
      Cluster Name:   cluster-1
      Desired State:  RUNNING
      Node Name:      aks-nodepool1-11471722-vmss000001
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
   ```

#### Automation

If you want to automate all the above steps, please use the `kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service/create-domain-on-aks.sh`.

For input values, you can edit `kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service/create-domain-on-aks-inputs.yaml` directly, or copy the file and edit in your copy. The following values must be specified:

| Name in yaml file | Example value | Notes |
|-------------------|---------------|-------|
| `azureServicePrincipalAppId` | `nr086o75-pn59-4782-no5n-nq2op0rsr1q6` | Application id of your service principal, refer to the application id in [Create Service Principal](#create-service-principal-for-aks) section. |
| `azureServicePrincipalClientSecret` | `8693089o-q190-45ps-9319-or36252s3s90` | A client secret of your service principal, refer to the client secret in [Create Service Principal](#create-service-principal-for-aks) section. |
| `azureServicePrincipalTenantId` | `72s988os-86s1-cafe-babe-2q7pq011qo47` | Tenant (Directory ) id of your service principal, refer to the client secret in [Create Service Principal](#create-service-principal-for-aks) section. |
| `dockerEmail` | `yourDockerEmail` | Your docker email, refer to [Docker Hub](#docker-hub) section. |
| `dockerPassword` | `yourDockerPassword`| Your docker password in clear text, refer to [Docker Hub](#docker-hub) section. |
| `dockerUserName` | `yourDockerId` |Your docker user name, refer to [Docker Hub](#docker-hub) section. ||

If you don't want to change the other parameters, you can use the default value.  Please make sure no extra whitespaces are added!

```bash
# Use ~/azure as output directory, please change it according to your requirement.

# Use create-domain-on-aks-inputs.yaml as input file
# cd kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service
bash create-domain-on-aks.sh -i create-domain-on-aks-inputs.yaml -o ~/azure -e

# Use your own input file.
# cd kubernetes/samples/scripts/create-weblogic-domain-on-azure-kubernetes-service
bash create-domain-on-aks.sh -i <your-input>.yaml -o ~/azure -e

```

The script will print the Administration Server address after a successful deployment.  The default user name for the Administration Console is `weblogic` and the default password is `welcome1`.  Please change this for production deployments.  To interact with the cluster using `kubectl`, use `az aks get-credentials` as shown in the script output.

{{% notice info %}} You now have created an AKS cluster with `PersistentVolumeClaim` and `PersistentVolume` to contain the WLS domain configuration files.  Using those artifacts, you have used the operator to create a WLS domain.
{{% /notice %}}

#### Deploy sample application

Now that you have WLS running in AKS, you can test the cluster by deploying the simple sample application included in the repository:

1. Go to the WebLogic Server Administration Console, Select "Lock & Edit".
2. Select Deployments.
3. Select Install.
4. Select Upload your file(s).
5. For the Deployment Archive, Select "Choose File".
6. Select the file `kubernetes/samples/charts/application/testwebapp.war`.
7. Select Next. Choose 'Install this deployment as an application'.
8. Select Next. Select cluster-1 and All servers in the cluster.
9. Accept the defaults in the next screens and Select Finish.
10. Select Activate Changes.

![Deploy Application](screenshot-deploy-test-app.png)

Next you will need to start the application:

1. Go to Deplyments
1. Select Control
1. Select testwebapp
1. Select Start
1. Select Servicing all requests
1. Select Yes.

After the successful deployment, go to the application through the domain1-cluster-1-lb external IP.

```bash
kubectl  get svc domain1-cluster-1-external-lb

NAME                            TYPE           CLUSTER-IP     EXTERNAL-IP     PORT(S)          AGE
domain1-cluster-1-external-lb   LoadBalancer   10.0.108.249   52.224.248.40   8001:32695/TCP   30m
```

In the example, the application address is: `http://52.224.248.40:8001/testwebapp`.

The test application will list the server host and server IP on the page.

#### Access WebLogic Server logs

The logs are stored in the Azure file share. Follow these steps to access the log:

1. Go to the [Azure Portal](https://ms.portal.azure.com).
1. Go to your resource group.
1. Open the storage account.
1. In the "File service" section of the left panel, select File shares.
1. Select the file share name (e.g. weblogic in this example).
1. Select logs.
1. Select domain1.
1. WebLogic Server logs are listed in the folder.

   ![WebLogic Logs](screenshot-logs.png)

#### Troubleshooting

1. **Get pod error details**

   You may get the following message while creating the WebLogic domain: "the job status is not Completed!"

   ```bash
   status on iteration 20 of 20
   pod domain1-create-weblogic-sample-domain-job-nj7wl status is Init:0/1
   The create domain job is not showing status completed after waiting 300 seconds.
   Check the log output for errors.
   Error from server (BadRequest): container "create-weblogic-sample-domain-job" in pod "domain1-create-weblogic-sample-domain-job-nj7wl" is waiting to start: PodInitializing
   [ERROR] Exiting due to failure - the job status is not Completed!
   ```

   You can get further error details by running `kubectl describe pod`, as shown here:

   ```bash
   # replace domain1-create-weblogic-sample-domain-job-nj7wl with your pod name
   kubectl describe pod domain1-create-weblogic-sample-domain-job-nj7wl
   ```

   Error example:

   ```bash
   Events:
     Type     Reason       Age                  From                                        Message
     ----     ------       ----                 ----                                        -------
     Normal   Scheduled    4m2s                 default-scheduler                           Successfully assigned default/domain1-create-weblogic-sample-domain-job-qqv6k to aks-nodepool1-58449474-vmss000001
     Warning  FailedMount  119s                 kubelet, aks-nodepool1-58449474-vmss000001  Unable to mount volumes for pod "domain1-create-weblogic-sample-domain-job-qqv6k_default(15706980-73cb-11ea-b804-b2c91b494b00)": timeout expired waiting for volumes to attach or mount for pod "default"/"domain1-create-weblogic-sample-domain-job-qqv6k". list of unmounted volumes=[weblogic-sample-domain-storage-volume]. list of unattached volumes=[create-weblogic-sample-domain-job-cm-volume weblogic-sample-domain-storage-volume weblogic-credentials-volume default-token-zr7bq]
     Warning  FailedMount  114s (x9 over 4m2s)  kubelet, aks-nodepool1-58449474-vmss000001  MountVolume.SetUp failed for volume "azurefile" : Couldn't get secret default/azure-secrea
     ```

2. **Fail to access Administration Console**

   Here are some common reasons for this failure, along with some tips to help you investigate.

   * **Create WebLogic domain job fails**

   Check the deploy log and find the failure details with `kubectl describe pod podname`.
   Please go to 1. Getting pod error details.

   * **Process of starting the Administration Server is still running**

   Check with `kubectl get svc` and if domain1-admin-server is not listed,
   we need to wait some more for the Administration Server to start.

   The following output is an example of when the Administration Server has started.

   ```bash
   NAME                               TYPE           CLUSTER-IP    EXTERNAL-IP     PORT(S)              AGE
   domain1-admin-server               ClusterIP      None          <none>          30012/TCP,7001/TCP   7m3s
   domain1-admin-server-external      NodePort       10.0.78.211   <none>          7001:30701/TCP       7m3s
   domain1-admin-server-external-lb   LoadBalancer   10.0.6.144    40.71.233.81    7001:32758/TCP       7m32s
   domain1-cluster-1-lb               LoadBalancer   10.0.29.231   52.142.39.152   8001:31022/TCP       7m30s
   domain1-cluster-cluster-1          ClusterIP      10.0.80.134   <none>          8001/TCP             1s
   domain1-managed-server1            ClusterIP      None          <none>          8001/TCP             1s
   domain1-managed-server2            ClusterIP      None          <none>          8001/TCP             1s
   internal-weblogic-operator-svc     ClusterIP      10.0.1.23     <none>          8082/TCP             9m59s
   kubernetes                         ClusterIP      10.0.0.1      <none>          443/TCP              16m
   ```

#### Useful links

* [Quickstart: Deploy an Azure Kubernetes Service cluster using the Azure CLI](https://docs.microsoft.com/azure/aks/kubernetes-walkthrough)
* [WebLogic Server Kubernetes Operator](https://oracle.github.io/weblogic-kubernetes-operator/userguide/introduction/introduction/)
* [Manually create and use a volume with Azure Files share in Azure Kubernetes Service (AKS)](https://docs.microsoft.com/azure/aks/azure-files-volume)
* [Create a Secret by providing credentials on the command line](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/#create-a-secret-by-providing-credentials-on-the-command-line)

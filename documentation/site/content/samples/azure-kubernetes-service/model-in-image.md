---
title: "Model in Image"
date: 2020-11-24T18:22:31-05:00
weight: 3
description: "Sample for creating a WebLogic cluster on the Azure Kubernetes Service with model in image domain home source type."
---

This sample demonstrates how to use the [WebLogic Kubernetes Operator](https://oracle.github.io/weblogic-kubernetes-operator) (hereafter "the operator") to set up a WebLogic Server (WLS) cluster on the Azure Kubernetes Service (AKS) using the model in image domain home source type. After going through the steps, your WLS domain runs on an AKS cluster instance and you can manage your WLS domain by interacting with the operator.

#### Contents

 - [Prerequisites](#prerequisites)
 - [Create an AKS cluster](#create-the-aks-cluster)
 - [Install WebLogic Kubernetes Operator](#install-weblogic-kubernetes-operator)
 - [Create Docker image](#create-docker-image)
 - [Create WebLogic domain](#create-weblogic-domain)
 - [Invoke the web application](#invoke-the-web-application)
 - [Rolling updates](#rolling-updates)
 - [Clean up resource](#clean-up-resources)
 - [Troubleshooting](#troubleshooting)
 - [Useful links](#useful-links)

{{< readfile file="/samples/azure-kubernetes-service/includes/prerequisites-02.txt" >}}

##### Prepare parameters

Set required parameters by running the following commands.

```shell
# Change these parameters as needed for your own environment
export ORACLE_SSO_EMAIL=<replace with your oracle account email>
export ORACLE_SSO_PASSWORD="<replace with your oracle password.>"

export BASE_DIR=~
export NAME_PREFIX=wls

# Used to generate resource names.
export TIMESTAMP=`date +%s`
export ACR_NAME="acr${TIMESTAMP}"
export AKS_CLUSTER_NAME="aks${TIMESTAMP}"
export AKS_PERS_RESOURCE_GROUP="resourcegroup${TIMESTAMP}"
export AKS_PERS_LOCATION=eastus

export SECRET_NAME_DOCKER="${NAME_PREFIX}regcred"
export WEBLOGIC_USERNAME=weblogic
export WEBLOGIC_PASSWORD=Secret123456
export WEBLOGIC_WDT_PASSWORD=Secret123456
```

{{< readfile file="/samples/azure-kubernetes-service/includes/create-aks-cluster-body-01.txt" >}}

{{< readfile file="/samples/azure-kubernetes-service/includes/sign-in-azure.txt" >}}

{{< readfile file="/samples/azure-kubernetes-service/includes/download-samples-zip.txt" >}}

{{< readfile file="/samples/azure-kubernetes-service/includes/create-resource-group.txt" >}}

{{< readfile file="/samples/azure-kubernetes-service/includes/create-aks-cluster-body-02.txt" >}}

**NOTE**: If you run into VM size failure, see [Troubleshooting - Virtual Machine size is not supported]({{< relref "/samples/azure-kubernetes-service/troubleshooting#virtual-machine-size-is-not-supported" >}}).


#### Install WebLogic Kubernetes Operator

The WebLogic Kubernetes Operator is an adapter to integrate WebLogic Server and Kubernetes, allowing Kubernetes to serve as container infrastructure hosting WLS instances. The operator runs as a Kubernetes Pod and stands ready to perform actions related to running WLS on Kubernetes.

Create a namespace and service account for the operator.

```shell
$ kubectl create namespace sample-weblogic-operator-ns
```

The output will show something similar to the following:

```
namespace/sample-weblogic-operator-ns created
```

```shell
$ kubectl create serviceaccount -n sample-weblogic-operator-ns sample-weblogic-operator-sa
```

The output will show something similar to the following:

```
serviceaccount/sample-weblogic-operator-sa created
```

Validate the service account was created with this command.

```shell
$ kubectl -n sample-weblogic-operator-ns get serviceaccount
```

The output will show something similar to the following:

```
NAME                          SECRETS   AGE
default                       1         9m24s
sample-weblogic-operator-sa   1         9m5s
```

Install the operator. The operator’s Helm chart is located in the kubernetes/charts/weblogic-operator directory. This sample installs the operator using Helm charts from GitHub. It may take you several minutes to install the operator.

```shell
$ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update
```

Update the repo to get the latest Helm charts. It is a best practice to do this every time before installing a new operator version. In this example, we are using a pinned version, but you may also find success if you use the latest version. In this case, you can omit the `--version` argument. Be warned that these instructions have only been tested with the exact version shown.


```shell
$ helm repo update
$ helm install weblogic-operator weblogic-operator/weblogic-operator \
  --namespace sample-weblogic-operator-ns \
  --version 4.2.8 \
  --set serviceAccount=sample-weblogic-operator-sa \
  --wait
```

The output will show something similar to the following:

```
NAME: weblogic-operator
LAST DEPLOYED: Fri Aug 12 14:28:47 2022
NAMESPACE: sample-weblogic-operator-ns
STATUS: deployed
REVISION: 1
TEST SUITE: None
```

{{% notice tip %}} If you wish to use a more recent version of the operator, replace the `4.2.8` in the preceding command with the other version number. To see the list of versions, visit the [GitHub releases page](https://github.com/oracle/weblogic-kubernetes-operator/releases).
{{% /notice %}}


Verify the operator with the following commands; the status will be `Running`.

```shell
$ helm list -A
```

The output will show something similar to the following:

```
NAME                    NAMESPACE                       REVISION        UPDATED                                 STATUS CHART                    APP VERSION
weblogic-operator       sample-weblogic-operator-ns     1               2023-05-15 10:31:05.1890341 +0800 CST   deployeweblogic-operator-4.2.8  4.2.8
```

```shell
$ kubectl get pods -n sample-weblogic-operator-ns
```

The output will show something similar to the following:

```
NAME                                         READY   STATUS    RESTARTS   AGE
weblogic-operator-54b5c8df46-g4rcm           1/1     Running   0          86s
weblogic-operator-webhook-6c5885f69f-pd8qw   1/1     Running   0          86s
```

{{% notice note %}}
You can specify the operator image by changing value of `--set image`. If you run into failures, see [Troubleshooting - WebLogic Kubernetes Operator installation failure]({{< relref "/samples/azure-kubernetes-service/troubleshooting#weblogic-kubernetes-operator-installation-failure" >}}).
{{% /notice %}}

{{% notice info %}}
If you have an image built with domain models following [Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}}), you can go to [Create WebLogic domain](#create-weblogic-domain) directly.
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

- The `JAVA_HOME` environment variable must be set and must reference a valid JDK 8 or 11 installation.
- Copy the sample to a new directory; for example, use the directory `/tmp/mii-sample`. In the directory name, `mii` is short for "model in image". Model in image is one of three domain home source types supported by the operator. To learn more, see [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).

   ```shell
   $ rm /tmp/mii-sample -f -r
   $ mkdir /tmp/mii-sample
   ```

   ```shell
   $ cp -r $BASE_DIR/sample-scripts/create-weblogic-domain/wdt-artifacts/* /tmp/mii-sample
   ```

   Save the model file directory.

   ```shell
   $ export WDT_MODEL_FILES_PATH=/tmp/mii-sample/wdt-model-files
   ```

   **NOTE**: We will refer to this working copy of the sample as `/tmp/mii-sample`; however, you can use a different location.

{{< readfile file="/samples/azure-kubernetes-service/includes/download-wls-tools.txt" >}}


##### Image creation - Introduction

{{< readfile file="/samples/azure-kubernetes-service/includes/auxiliary-image-directory.txt" >}}

##### Understanding your first archive

See [Understanding your first archive]({{< relref "/samples/domains/model-in-image/auxiliary-image-creation#understand-your-first-archive" >}}).


##### Staging a ZIP file of the archive

When you create the image, you will use the files in the staging directory, `${WDT_MODEL_FILES_PATH}/WLS-v1`. In preparation, you need it to contain a ZIP file of the WDT application archive.

Run the following commands to create your application archive ZIP file and put it in the expected directory:

```shell
# Delete existing archive.zip in case we have an old leftover version
$ rm -f ${WDT_MODEL_FILES_PATH}/WLS-v1/archive.zip
```

Create a ZIP file of the archive in the location that we will use when we run the WebLogic Image Tool.

```shell
$ cd /tmp/mii-sample/archives/archive-v1
$ zip -r ${WDT_MODEL_FILES_PATH}/WLS-v1/archive.zip wlsdeploy
```

##### Staging model files

{{< readfile file="/samples/azure-kubernetes-service/includes/staging-model-files.txt" >}}

A Model in Image image can contain multiple properties files, archive ZIP files, and YAML files but in this sample you use just one of each. For a complete description of Model in Images model file naming conventions, file loading order, and macro syntax, see [Model files]({{< relref "/managing-domains/model-in-image/model-files.md" >}}) in the Model in Image user documentation.

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

Ensure Docker is running on your local machine.  Run the following commands to tag and push the image to your ACR.

```shell
$ docker tag wdt-domain-image:WLS-v1 $LOGIN_SERVER/mii-aks-auxiliary-image:1.0
```
```shell
$ docker push $LOGIN_SERVER/mii-aks-auxiliary-image:1.0
```

The output will show something similar to the following:

```
The push refers to repository [contosorgresourcegroup1610068510.azurecr.io/mii-aks-auxiliary-image]
1.0: digest: sha256:208217afe336053e4c524caeea1a415ccc9cc73b206ee58175d0acc5a3eeddd9 size: 2415
```

{{< readfile file="/samples/azure-kubernetes-service/includes/aks-connect-acr.txt" >}}

If you see an error that seems related to you not being an **Owner on this subscription**, please refer to the troubleshooting section [Cannot attach ACR due to not being Owner of subscription]({{< relref "/samples/azure-kubernetes-service/troubleshooting#cannot-attach-acr-due-to-not-being-owner-of-subscription" >}}).

#### Create WebLogic domain

  - [Namespace](#namespace)
  - [Kubernetes Secrets for WebLogic image](#kubernetes-secrets-for-weblogic-image)
  - [Kubernetes Secrets for WebLogic](#kubernetes-secrets-for-weblogic)
  - [Domain resource](##domain-resource)

In this section, you will deploy the new image to the namespace `sample-domain1-ns`, including the following steps:

- Create a namespace for the WebLogic domain.
- Upgrade the operator to manage the WebLogic domain namespace.
- Create a secret containing your WebLogic administrator user name and password.
- Create a secret containing your Model in Image runtime encryption password:
    - All Model in Image domains must supply a runtime encryption Secret with a `password` value.
    - The runtime encryption password is used to encrypt configuration that is passed around internally by the operator.
    - The value must be kept private but can be arbitrary; you can optionally supply a different secret value every time you restart the domain.
- Deploy a domain YAML file that references the new image.
- Wait for the domain’s pods to start and reach their ready state.

##### Namespace

Create a namespace that can host one or more domains:

```shell
$ kubectl create namespace sample-domain1-ns
```

Label the domain namespace so that the operator can autodetect and create WebLogic Server pods. Without this step, the operator cannot see the namespace.

```shell
$ kubectl label namespace sample-domain1-ns weblogic-operator=enabled
```

##### Kubernetes Secrets for WebLogic image

You will use the `kubernetes/samples/scripts/create-kubernetes-secrets/create-docker-credentials-secret.sh` script to create the Docker credentials as a Kubernetes secret to pull image from OCR. Please run:

``` shell
$ $BASE_DIR/sample-scripts/create-kubernetes-secrets/create-docker-credentials-secret.sh \
  -n sample-domain1-ns \
  -s ${SECRET_NAME_DOCKER} \
  -e ${ORACLE_SSO_EMAIL} \
  -p ${ORACLE_SSO_PASSWORD} \
  -u ${ORACLE_SSO_EMAIL}
```

The output will show something similar to the following:

```
secret/wlsregcred created
The secret wlsregcred has been successfully created in the sample-domain1-ns namespace.
```

##### Kubernetes Secrets for WebLogic

First, create the secrets needed by the WLS type model domain. For more on secrets in the context of running domains, see [Prepare to run a domain]({{< relref "/managing-domains/prepare" >}}). In this case, you have two secrets.

Run the following `kubectl` commands to deploy the required secrets:

```shell
$ kubectl -n sample-domain1-ns create secret generic \
  sample-domain1-weblogic-credentials \
   --from-literal=username="${WEBLOGIC_USERNAME}" \
   --from-literal=password="${WEBLOGIC_PASSWORD}"
```
```shell
$ kubectl -n sample-domain1-ns label  secret \
  sample-domain1-weblogic-credentials \
  weblogic.domainUID=sample-domain1
```
```shell
$ kubectl -n sample-domain1-ns create secret generic \
  sample-domain1-runtime-encryption-secret \
   --from-literal=password="${WEBLOGIC_WDT_PASSWORD}"
```
```shell
$ kubectl -n sample-domain1-ns label  secret \
  sample-domain1-runtime-encryption-secret \
  weblogic.domainUID=sample-domain1
```

   Some important details about these secrets:

   - Make sure to enclose your values in double quotes and perform the necessary escaping to prevent the shell from modifying the values before the secret values are set.
   - Choosing passwords and user names:
      - Set the variables `WEBLOGIC_USERNAME` and `WEBLOGIC_PASSWORD` with a user name and password of your choice.
        The password should be at least eight characters long and include at least one digit.
        Remember what you specified. These credentials may be needed again later.
      - Set the variable `WEBLOGIC_WDT_PASSWORD` with a password of your choice.

   - The WebLogic credentials secret:
      - It is required and must contain `username` and `password` fields.
      - It must be referenced by the `spec.webLogicCredentialsSecret` field in your Domain resource YAML file.  For complete details about the `Domain` resource, see the [Domain resource reference](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md#domain-spec).
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

Now, you can verify the secrets with command:

```shell
kubectl get secrets -n sample-domain1-ns
```

The output looks similar to the following content.

```txt
NAME                                       TYPE                             DATA   AGE
sample-domain1-runtime-encryption-secret   Opaque                           1      19s
sample-domain1-weblogic-credentials        Opaque                           2      28s
wlsregcred                                 kubernetes.io/dockerconfigjson   1      47s
```

##### Domain resource

Now, you create a domain YAML file. Think of the domain YAML file as the way to configure some aspects of your WebLogic domain using Kubernetes.  The operator uses the Kubernetes "custom resource" feature to define a Kubernetes resource type called `Domain`.  For more on the `Domain` Kubernetes resource, see [Domain Resource]({{< relref "/managing-domains/domain-resource" >}}). For more on custom resources see [the Kubernetes documentation](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/).

We provide a script at `$BASE_DIR/sample-scripts/create-weblogic-domain-on-azure-kubernetes-service/create-domain-on-aks-mii-generate-yaml.sh` to generate a domain resource description.

Run the following command to generate resource files.

```shell
export Domain_Creation_Image_tag="$LOGIN_SERVER/mii-aks-auxiliary-image:1.0"
```

```shell
$ cd $BASE_DIR
$ bash $BASE_DIR/sample-scripts/create-weblogic-domain-on-azure-kubernetes-service/create-domain-on-aks-mii-generate-yaml.sh
```

After running above commands, you will get three files: `mii-initial.yaml`, `admin-lb.yaml` and `cluster-lb.yaml`.

Run the following command to create the domain custom resource:

```shell
$ kubectl apply -f mii-initial.yaml
```

Successful output will look like:

```
domain.weblogic.oracle/sample-domain1 created
cluster.weblogic.oracle/sample-domain1-cluster-1 created
```

Verify the WebLogic Server pods are all running:

```shell
$ kubectl get pods -n sample-domain1-ns --watch
```

Output will look similar to the following.

```
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
```

When the system stabilizes with the following state, it is safe to proceed.

```
NAME                             READY   STATUS    RESTARTS   AGE
sample-domain1-admin-server      1/1     Running   0          2m
sample-domain1-managed-server1   1/1     Running   0          83s
sample-domain1-managed-server2   1/1     Running   0          83s
```

It may take you up to 10 minutes to deploy all pods, please wait and make sure everything is ready.

If the system does not reach this state, troubleshoot and resolve the problem before continuing. See [Troubleshooting](#troubleshooting) for hints.

#### Invoke the web application

##### Create Azure load balancer

Create an Azure public standard load balancer to access the WebLogic Server Administration Console and applications deployed to the cluster.

Use the file `admin-lb.yaml` to create a load balancer service for the Administration Server. If you are choosing not to use the predefined YAML file and instead created a new one with customized values, then substitute the following content with your domain values.

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

Use the file `cluster-lb.yaml` to create a load balancer service for the managed servers. If you are choosing not to use the predefined YAML file and instead created new one with customized values, then substitute the following content with your domain values.

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

Create the load balancer services using the following commands:

```shell
$ kubectl apply -f admin-lb.yaml
```

Successful output will look like:

```
service/sample-domain1-admin-server-external-lb created
```

```shell
$ kubectl  apply -f cluster-lb.yaml
```

Successful output will look like:

```
service/sample-domain1-cluster-1-external-lb created
```

Get the external IP addresses of the Administration Server and cluster load balancers (please wait for the external IP addresses to be assigned):

```shell
$ kubectl get svc -n sample-domain1-ns --watch
```

Successful output will look like:

```
NAME                                      TYPE           CLUSTER-IP     EXTERNAL-IP      PORT(S)          AGE
sample-domain1-admin-server               ClusterIP      None           <none>           7001/TCP         8m33s
sample-domain1-admin-server-external-lb   LoadBalancer   10.0.184.118   52.191.234.149   7001:30655/TCP   2m30s
sample-domain1-cluster-1-lb               LoadBalancer   10.0.76.7      52.191.235.71    8001:30439/TCP   2m25s
sample-domain1-cluster-cluster-1          ClusterIP      10.0.118.225   <none>           8001/TCP         7m53s
sample-domain1-managed-server1            ClusterIP      None           <none>           8001/TCP         7m53s
sample-domain1-managed-server2            ClusterIP      None           <none>           8001/TCP         7m52s
```

In the example, the URL to access the Administration Server is: `http://52.191.234.149:7001/console`.
The expected username and password must match the values that you chose during the [Kubernetes Secrets for WebLogic](#kubernetes-secrets-for-weblogic) step.

**IMPORTANT:** You must ensure that any Network Security Group rules that govern access to the console allow inbound traffic on port 7001.

If the WLS Administration Console is still not available, use `kubectl describe domain` to check domain status.

```shell
$ kubectl describe domain domain1
```

Make sure the status of cluster-1 is `ServersReady` and `Available`.

{{%expand "Click here to view example domain status." %}}
```yaml
Name:         sample-domain1
Namespace:    sample-domain1-ns
Labels:       weblogic.domainUID=sample-domain1
Annotations:  <none>
API Version:  weblogic.oracle/v9
Kind:         Domain
Metadata:
  Creation Timestamp:  2020-11-30T05:40:11Z
  Generation:          1
  Resource Version:    9346
  Self Link:           /apis/weblogic.oracle/v9/namespaces/sample-domain1-ns/domains/sample-domain1
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
  Server Start Policy:  IfNeeded
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

Access the Administration Console using the admin load balancer IP address.

```shell
$ ADMIN_SERVER_IP=$(kubectl -n sample-domain1-ns get svc sample-domain1-admin-server-external-lb -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')
$ echo "Administration Console Address: http://${ADMIN_SERVER_IP}:7001/console/"
```

Access the sample application using the cluster load balancer IP address.

```shell
## Access the sample application using the cluster load balancer IP.
$ CLUSTER_IP=$(kubectl -n sample-domain1-ns get svc sample-domain1-cluster-1-lb -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')
```

```shell
$ curl http://${CLUSTER_IP}:8001/myapp_war/index.jsp
```

Successful output will look like:

```
<html><body><pre>
*****************************************************************

Hello World! This is version 'v1' of the sample JSP web-app.

Welcome to WebLogic Server 'managed-server1'!

  domain UID  = 'sample-domain1'
  domain name = 'domain1'

Found 1 local cluster runtime:
  Cluster 'cluster-1'

Found min threads constraint runtime named 'SampleMinThreads' with configured count: 1

Found max threads constraint runtime named 'SampleMaxThreads' with configured count: 10

Found 0 local data sources:

*****************************************************************
</pre></body></html>
```

#### Rolling updates

Naturally, you will want to deploy newer versions of the EAR application, located in the WDT archive ZIP file at `wlsdeploy/applications/myapp-v1`. To learn how to do this, follow the steps in [Update 3]({{< relref "/samples/domains/model-in-image/update3" >}}).

#### Database connection

For guidance on how to connect a database to your AKS with WebLogic Server application, see [Deploy a Java application with WebLogic Server on an Azure Kubernetes Service (AKS) cluster](https://learn.microsoft.com/en-us/azure/aks/howto-deploy-java-wls-app).

#### Clean up resources

Run the following commands to clean up resources.

{{< readfile file="/samples/azure-kubernetes-service/includes/clean-up-resources-body-02.txt" >}}

#### Troubleshooting

For troubleshooting advice, see [Troubleshooting]({{< relref "/samples/azure-kubernetes-service/troubleshooting.md" >}}).

#### Useful links

- [Model in Image]({{< relref "/managing-domains/model-in-image/_index.md" >}}) user documentation
- [Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}}) sample
- [Deploy a Java application with WebLogic Server on an Azure Kubernetes Service (AKS) cluster](https://learn.microsoft.com/en-us/azure/aks/howto-deploy-java-wls-app)

---
title: "Tanzu Kubernetes Service"
date: 2020-11-16T13:34:31-05:00
weight: 9
description: "Sample for using the operator to set up a WLS cluster on the Tanzu Kubernetes Service."
---


This sample demonstrates how to use the Oracle [WebLogic Server Kubernetes Operator](/weblogic-kubernetes-operator/) (hereafter “the operator”) to set up a WebLogic Server (WLS) cluster on the Tanzu Kubernetes Grid (TKG).
After performing the sample steps, your WLS domain with a Model in Image domain source type runs on a TKG Kubernetes cluster instance. After the domain has been provisioned, you can monitor it using the WebLogic Server Administration console.

TKG is a managed Kubernetes Service that lets you quickly deploy and manage Kubernetes clusters. To learn more, see the [Tanzu Kubernetes Grid (TKG)](https://docs.vmware.com/en/VMware-Tanzu-Kubernetes-Grid/1.2/vmware-tanzu-kubernetes-grid-12/GUID-index.html) overview page.

#### Contents

 - [Prerequisites](#prerequisites)
   - [Create a Tanzu Kubernetes cluster](#create-a-tanzu-kubernetes-cluster)
   - [Oracle Container Registry](#oracle-container-registry)
 - [Install WebLogic Server Kubernetes Operator](#install-weblogic-server-kubernetes-operator)
 - [Create an image](#create-an-image)
 - [Create WebLogic domain](#create-weblogic-domain)
 - [Invoke the web application](#invoke-the-web-application)

#### Prerequisites

This sample assumes the following prerequisite environment setup:

* WebLogic Server Kubernetes Operator: This document was tested with version v3.1.0.
* Operating System: GNU/Linux.
* [Git](https://git-scm.com/downloads); use `git --version` to test if `git` works.  This document was tested with version 2.17.1.
* TKG CLI; use `tkg version` to test if TKG works. This document was tested with version v1.1.3.
* [kubectl](https://kubernetes-io-vnext-staging.netlify.com/docs/tasks/tools/install-kubectl/); use `kubectl version` to test if `kubectl` works.  This document was tested with version v1.18.6.
* [Helm](https://helm.sh/docs/intro/install/) version 3.1 or later; use `helm version` to check the `helm` version.  This document was tested with version v3.2.1.

##### Create a Tanzu Kubernetes cluster

Create the Kubernetes cluster using the TKG CLI. See the [Tanzu documentation](https://docs.vmware.com/en/VMware-Tanzu-Kubernetes-Grid/1.2/vmware-tanzu-kubernetes-grid-12/GUID-index.html) to set up your Kubernetes cluster.
After your Kubernetes cluster is up and running, run the following commands to make sure `kubectl` can access the Kubernetes cluster:

```shell
$ kubectl get nodes -o wide
```
```
NAME                                    STATUS     ROLES    AGE     VERSION            INTERNAL-IP       EXTERNAL-IP       OS-IMAGE                 KERNEL-VERSION   CONTAINER-RUNTIME
k8s-cluster-101-control-plane-8nj7t     NotReady   master   2d20h   v1.18.6+vmware.1   192.168.100.147   192.168.100.147   VMware Photon OS/Linux   4.19.132-1.ph3   containerd://1.3.4
k8s-cluster-101-md-0-577b7dc766-552hn   Ready      <none>   2d20h   v1.18.6+vmware.1   192.168.100.148   192.168.100.148   VMware Photon OS/Linux   4.19.132-1.ph3   containerd://1.3.4
k8s-cluster-101-md-0-577b7dc766-m8wrc   Ready      <none>   2d20h   v1.18.6+vmware.1   192.168.100.149   192.168.100.149   VMware Photon OS/Linux   4.19.132-1.ph3   containerd://1.3.4
k8s-cluster-101-md-0-577b7dc766-p2gkz   Ready      <none>   2d20h   v1.18.6+vmware.1   192.168.100.150   192.168.100.150   VMware Photon OS/Linux   4.19.132-1.ph3   containerd://1.3.4
```

##### Oracle Container Registry

You will need an Oracle Container Registry account. The following steps will direct you to accept the Oracle Standard Terms and Restrictions to pull the WebLogic Server images.  Make note of your Oracle Account password and email.  This sample pertains to 12.2.1.4, but other versions may work as well.

#### Install WebLogic Server Kubernetes Operator

The Oracle WebLogic Server Kubernetes Operator is an adapter to integrate WebLogic Server and Kubernetes, allowing Kubernetes to serve as a container infrastructure hosting WLS instances.
The operator runs as a Kubernetes Pod and stands ready to perform actions related to running WLS on Kubernetes.

Clone the Oracle WebLogic Server Kubernetes Operator repository to your machine. We will use several scripts in this repository to create a WebLogic domain.
Kubernetes Operators use [Helm](https://helm.sh/) to manage Kubernetes applications. The operator’s Helm chart is located in the `kubernetes/charts/weblogic-operator` directory. Install the operator by running the following commands.

Clone the repository.

```shell
$ git clone --branch v3.2.1 https://github.com/oracle/weblogic-kubernetes-operator.git
```
```shell
$ cd weblogic-kubernetes-operator
```
Grant the Helm service account the cluster-admin role.

```shell
$ cat <<EOF | kubectl apply -f -
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

Create a namespace and service account for the operator.

```shell
$ kubectl create namespace sample-weblogic-operator-ns
```
```
namespace/sample-weblogic-operator-ns created
```
```shell
$ kubectl create serviceaccount -n sample-weblogic-operator-ns sample-weblogic-operator-sa
```
```
serviceaccount/sample-weblogic-operator-sa created
```

Install the operator.

```shell
$ helm install weblogic-operator kubernetes/charts/weblogic-operator \
  --namespace sample-weblogic-operator-ns \
  --set serviceAccount=sample-weblogic-operator-sa \
  --set "enableClusterRoleBinding=true" \
  --set "domainNamespaceSelectionStrategy=LabelSelector" \
  --set "domainNamespaceLabelSelector=weblogic-operator\=enabled" \
  --wait
```
```
NAME: weblogic-operator
LAST DEPLOYED: Tue Nov 17 09:33:58 2020
NAMESPACE: sample-weblogic-operator-ns
STATUS: deployed
REVISION: 1
TEST SUITE: None
```

Verify the operator with the following commands; the status will be running.

```shell
$ helm list -A
```
```
NAME                        NAMESPACE                     REVISION   UPDATED                                 STATUS       CHART                   APP VERSION
sample-weblogic-operator    sample-weblogic-operator-ns   1          2020-11-17 09:33:58.584239273 -0700 PDT deployed     weblogic-operator-3.1
```
```shell
$ kubectl get pods -n sample-weblogic-operator-ns
```
```
NAME                                 READY   STATUS    RESTARTS   AGE
weblogic-operator-775b668c8f-nwwnn   1/1     Running   0          32s
```


#### Create an image

  - [Image creation prerequisites](#image-creation-prerequisites)
  - [Image creation - Introduction](#image-creation---introduction)
  - [Understanding your first archive](#understanding-your-first-archive)
  - [Staging a ZIP file of the archive](#staging-a-zip-file-of-the-archive)
  - [Staging model files](#staging-model-files)
  - [Creating the image with WIT](#creating-the-image-with-wit)

##### Image creation prerequisites
1. The `JAVA_HOME` environment variable must be set and must reference a valid JDK 8 or 11 installation.
2. Copy the sample to a new directory; for example, use the directory `/tmp/mii-sample`.

```shell
$ mkdir /tmp/mii-sample
```
```shell
$ cp -r /root/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/* /tmp/mii-sample
```


**Note**: We will refer to this working copy of the sample as `/tmp/mii-sample`; however, you can use a different location.

Download the latest WebLogic Deploying Tooling (WDT) and WebLogic Image Tool (WIT) installer ZIP files to your `/tmp/mii-sample/model-images` directory. Both WDT and WIT are required to create your Model in Image container images.

```shell
$ cd /tmp/mii-sample/model-images
```
```shell
$ curl -m 120 -fL https://github.com/oracle/weblogic-deploy-tooling/releases/latest/download/weblogic-deploy.zip \
  -o /tmp/mii-sample/model-images/weblogic-deploy.zip
```
```shell
$ curl -m 120 -fL https://github.com/oracle/weblogic-image-tool/releases/latest/download/imagetool.zip \
  -o /tmp/mii-sample/model-images/imagetool.zip
```


To set up the WebLogic Image Tool, run the following commands:
```shell
$ cd /tmp/mii-sample/model-images
```
```shell
$ unzip imagetool.zip
```
```shell
$ ./imagetool/bin/imagetool.sh cache addInstaller \
  --type wdt \
  --version latest \
  --path /tmp/mii-sample/model-images/weblogic-deploy.zip
```

These steps will install WIT to the `/tmp/mii-sample/model-images/imagetool` directory, plus put a `wdt_latest` entry in the tool’s cache which points to the WDT ZIP file installer. You will use WIT later in the sample for creating model images.


##### Image creation - Introduction

The goal of image creation is to demonstrate using the WebLogic Image Tool to create an image named `model-in-image:WLS-v1` from files that you will stage to `/tmp/mii-sample/model-images/model-in-image:WLS-v1/`.
The staged files will contain a web application in a WDT archive, and WDT model configuration for a WebLogic Administration Server called `admin-server` and a WebLogic cluster called `cluster-1`.

Overall, a Model in Image image must contain a WebLogic Server installation and a WebLogic Deploy Tooling installation in its `/u01/wdt/weblogic-deploy` directory.
In addition, if you have WDT model archive files, then the image must also contain these files in its `/u01/wdt/models` directory.
Finally, an image optionally may also contain your WDT model YAML file and properties files in the same `/u01/wdt/models` directory.
If you do not specify a WDT model YAML file in your `/u01/wdt/models` directory, then the model YAML file must be supplied dynamically using a Kubernetes ConfigMap that is referenced by your Domain `spec.model.configMap` field.
We provide an example of using a model ConfigMap later in this sample.

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

```
# Delete existing archive.zip in case we have an old leftover version
```
```shell
$ rm -f /tmp/mii-sample/model-images/model-in-image__WLS-v1/archive.zip
```
```
# Move to the directory which contains the source files for our archive
```
```shell
$ cd /tmp/mii-sample/archives/archive-v1
```
```
# Zip the archive to the location will later use when we run the WebLogic Image Tool
```
```shell
$ zip -r /tmp/mii-sample/model-images/model-in-image__WLS-v1/archive.zip wlsdeploy
```

##### Staging model files

In this step, you explore the staged WDT model YAML file and properties in the `/tmp/mii-sample/model-in-image__WLS-v1` directory. The model in this directory references the web application in your archive,
configures a WebLogic Server Administration Server, and configures a WebLogic cluster. It consists of only two files, `model.10.properties`, a file with a single property, and, `model.10.yaml`, a YAML file with your WebLogic configuration `model.10.yaml`.

```
CLUSTER_SIZE=5
```

Here is the WLS `model.10.yaml`:

```yaml
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

The model files:

- Define a WebLogic domain with:
    - Cluster `cluster-1`
    - Administration Server `admin-server`
    - A `cluster-1` targeted EAR application that’s located in the WDT archive ZIP file at `wlsdeploy/applications/myapp-v1`

- Leverage macros to inject external values:
    - The property file `CLUSTER_SIZE` property is referenced in the model YAML file `DynamicClusterSize` and `MaxDynamicClusterSize` fields using a PROP macro.
    - The model file domain name is injected using a custom environment variable named `CUSTOM_DOMAIN_NAME` using an ENV macro.
        - You set this environment variable later in this sample using an env field in its Domain.
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

Now, you use the Image Tool to create an image named `model-in-image:WLS-v1` that’s layered on a base WebLogic image. You’ve already set up this tool during the prerequisite steps.

Run the following commands to create the model image and verify that it worked:

```shell
$ cd /tmp/mii-sample/model-images
```
```shell
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

This command runs the WebLogic Image Tool in its Model in Image mode, and does the following:

  - Builds the final image as a layer on the `container-registry.oracle.com/middleware/weblogic:12.2.1.4` base image.
  - Copies the WDT ZIP file that’s referenced in the WIT cache into the image.
      - Note that you cached WDT in WIT using the keyword `latest` when you set up the cache during the sample prerequisites steps.
      - This lets WIT implicitly assume it’s the desired WDT version and removes the need to pass a `-wdtVersion` flag.
  - Copies the specified WDT model, properties, and application archives to image location `/u01/wdt/models`.

When the command succeeds, it should end with output like the following:

```
[INFO   ] Build successful. Build time=36s. Image tag=model-in-image:WLS-v1
```

Also, if you run the `docker images` command, then you will see an image named `model-in-image:WLS-v1`.

> Note: If you have Kubernetes cluster worker nodes that are remote to your local machine, then you need to put the image in a location that these nodes can access. See [Ensuring your Kubernetes cluster can access images](#ensuring-your-kubernetes-cluster-can-access-images).


#### Create WebLogic domain

In this section, you will deploy the new image to namespace `sample-domain1-ns`, including the following steps:

- Create a namespace for the WebLogic domain.
- Upgrade the operator to manage the WebLogic domain namespace.   
- Create a Secret containing your WebLogic administrator user name and password.
- Create a Secret containing your Model in Image runtime encryption password:
    - All Model in Image domains must supply a runtime encryption Secret with a `password` value.
    - It is used to encrypt configuration that is passed around internally by the operator.
    - The value must be kept private but can be arbitrary; you can optionally supply a different secret value every time you restart the domain.
- Deploy a Domain YAML file that references the new image.
- Wait for the domain’s Pods to start and reach their ready state.

##### Namespace

Create a namespace that can host one or more domains:

```shell
$ kubectl create namespace sample-domain1-ns
```
```
## label the domain namespace so that the operator can autodetect and create WebLogic Server pods.
```
```shell
$ kubectl label namespace sample-domain1-ns weblogic-operator=enabled
```

##### Secrets

First, create the secrets needed by the WLS type model domain. In this case, you have two secrets.

Run the following `kubectl` commands to deploy the required secrets:

```shell
$ kubectl -n sample-domain1-ns create secret generic \
  sample-domain1-weblogic-credentials \
   --from-literal=username=weblogic --from-literal=password=welcome1
```
```shell
$ kubectl -n sample-domain1-ns label  secret \
  sample-domain1-weblogic-credentials \
  weblogic.domainUID=sample-domain1
```
```shell
$ kubectl -n sample-domain1-ns create secret generic \
  sample-domain1-runtime-encryption-secret \
   --from-literal=password=my_runtime_password
```
```shell
$ kubectl -n sample-domain1-ns label  secret \
  sample-domain1-runtime-encryption-secret \
  weblogic.domainUID=sample-domain1
```

  Some important details about these secrets:

  - The WebLogic credentials secret:
    - It is required and must contain `username` and `password` fields.
    - It must be referenced by the `spec.webLogicCredentialsSecret` field in your Domain.
    - It also must be referenced by macros in the `domainInfo.AdminUserName` and `domainInfo.AdminPassWord` fields in your model YAML file.

  - The Model WDT runtime secret:
    - This is a special secret required by Model in Image.
    - It must contain a `password` field.
    - It must be referenced using the `spec.model.runtimeEncryptionSecret` field in its Domain.
    - It must remain the same for as long as the domain is deployed to Kubernetes but can be changed between deployments.
    - It is used to encrypt data as it's internally passed using log files from the domain's introspector job and on to its WebLogic Server pods.

  - Deleting and recreating the secrets:
    - You delete a secret before creating it, otherwise the create command will fail if the secret already exists.
    - This allows you to change the secret when using the `kubectl create secret` command.

  - You name and label secrets using their associated domain UID for two reasons:
    - To make it obvious which secrets belong to which domains.
    - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all resources associated with a domain.

##### Domain resource

Now, you create a Domain YAML file. A Domain is the key resource that tells the operator how to deploy a WebLogic domain.

Copy the following to a file called `/tmp/mii-sample/mii-initial.yaml` or similar, or use the file `/tmp/mii-sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml` that is included in the sample source.


  {{%expand "Click here to view the WLS Domain YAML file." %}}
  ```yaml
    #
    # This is an example of how to define a Domain resource.
    #
    apiVersion: "weblogic.oracle/v8"
    kind: Domain
    metadata:
      name: sample-domain1
      namespace: sample-domain1-ns
      labels:
        weblogic.domainUID: sample-domain1

    spec:
      # Set to 'FromModel' to indicate 'Model in Image'.
      domainHomeSourceType: FromModel

      # The WebLogic Domain Home, this must be a location within
      # the image for 'Model in Image' domains.
      domainHome: /u01/domains/sample-domain1

      # The WebLogic Server image that the Operator uses to start the domain
      image: "model-in-image:WLS-v1"

      # Defaults to "Always" if image tag (version) is ':latest'
      imagePullPolicy: "IfNotPresent"

      # Identify which Secret contains the credentials for pulling an image
      #imagePullSecrets:
      #- name: regsecret

      # Identify which Secret contains the WebLogic Admin credentials,
      # the secret must contain 'username' and 'password' fields.
      webLogicCredentialsSecret:
        name: sample-domain1-weblogic-credentials

      # Whether to include the WebLogic Server stdout in the pod's stdout, default is true
      includeServerOutInPodLog: true

      # Whether to enable overriding your log file location, see also 'logHome'
      #logHomeEnabled: false

      # The location for domain log, server logs, server out, introspector out, and Node Manager log files
      # see also 'logHomeEnabled', 'volumes', and 'volumeMounts'.
      #logHome: /shared/logs/sample-domain1

      # Set which WebLogic Servers the Operator will start
      # - "NEVER" will not start any server in the domain
      # - "ADMIN_ONLY" will start up only the administration server (no managed servers will be started)
      # - "IF_NEEDED" will start all non-clustered servers, including the administration server, and clustered servers up to their replica count.
      serverStartPolicy: "IF_NEEDED"

      # Settings for all server pods in the domain including the introspector job pod
      serverPod:
        # Optional new or overridden environment variables for the domain's pods
        # - This sample uses CUSTOM_DOMAIN_NAME in its image model file
        #   to set the Weblogic domain name
        env:
        - name: CUSTOM_DOMAIN_NAME
          value: "domain1"
        - name: JAVA_OPTIONS
          value: "-Dweblogic.StdoutDebugEnabled=false"
        - name: USER_MEM_ARGS
          value: "-XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom "

        # Optional volumes and mounts for the domain's pods. See also 'logHome'.
        #volumes:
        #- name: weblogic-domain-storage-volume
        #  persistentVolumeClaim:
        #    claimName: sample-domain1-weblogic-sample-pvc
        #volumeMounts:
        #- mountPath: /shared
        #  name: weblogic-domain-storage-volume

      # The desired behavior for starting the domain's administration server.
      adminServer:
        # The serverStartState legal values are "RUNNING" or "ADMIN"
        # "RUNNING" means the listed server will be started up to "RUNNING" mode
        # "ADMIN" means the listed server will be start up to "ADMIN" mode
        serverStartState: "RUNNING"
        # Setup a Kubernetes node port for the administration server default channel
        #adminService:
        #  channels:
        #  - channelName: default
        #    nodePort: 30701

      # The number of managed servers to start for unlisted clusters
      replicas: 1

      # The desired behavior for starting a specific cluster's member servers
      clusters:
      - clusterName: cluster-1
        serverStartState: "RUNNING"
        replicas: 2

      # Change the `restartVersion` to force the introspector job to rerun
      # and apply any new model configuration, to also force a subsequent
      # roll of your domain's WebLogic Server pods.
      restartVersion: '1'

      configuration:

        # Settings for domainHomeSourceType 'FromModel'
        model:
          # Valid model domain types are 'WLS', 'JRF', and 'RestrictedJRF', default is 'WLS'
          domainType: "WLS"

          # Optional configmap for additional models and variable files
          #configMap: sample-domain1-wdt-config-map

          # All 'FromModel' domains require a runtimeEncryptionSecret with a 'password' field
          runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret

        # Secrets that are referenced by model yaml macros
        # (the model yaml in the optional configMap or in the image)
        #secrets:
        #- sample-domain1-datasource-secret
  ```
  {{% /expand %}}


  > **Note**: Before you deploy the domain custom resource, determine if you have Kubernetes cluster worker nodes that are remote to your local machine. If so, you need to put the Domain's image in a location that these nodes can access and you may also need to modify your Domain YAML file to reference the new location. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/simple/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

Run the following command to create the domain custom resource:

```shell
$ kubectl apply -f /tmp/mii-sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml
```
> **Note**: If you are choosing not to use the predefined Domain YAML file and instead created your own Domain YAML file earlier, then substitute your custom file name in the above command. Previously, we suggested naming it `/tmp/mii-sample/mii-initial.yaml`.

Verify the WebLogic Server pods are all running:

```shell
$ kubectl get all -n sample-domain1-ns
```
```
NAME                                 READY   STATUS    RESTARTS   AGE
pod/sample-domain1-admin-server      1/1     Running   0          41m
pod/sample-domain1-managed-server1   1/1     Running   0          40m
pod/sample-domain1-managed-server2   1/1     Running   0          40m

NAME                                       TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)    AGE
service/sample-domain1-admin-server        ClusterIP   None           <none>        7001/TCP   41m
service/sample-domain1-cluster-cluster-1   ClusterIP   100.66.99.27   <none>        8001/TCP   40m
service/sample-domain1-managed-server1     ClusterIP   None           <none>        8001/TCP   40m
service/sample-domain1-managed-server2     ClusterIP   None           <none>        8001/TCP   40m

```

#### Invoke the web application

Create a load balancer to access the WebLogic Server Administration Console and applications deployed in the cluster.
Tanzu supports the MetalLB load balancer and NGINX ingress for routing.


Install the MetalLB load balancer by running following commands:

```
## create namespace metallb-system
```
```shell
$ kubectl create ns metallb-system
```
```
## deploy MetalLB load balancer
```
```shell
$ kubectl apply -f https://raw.githubusercontent.com/google/metallb/v0.9.2/manifests/metallb.yaml -n metallb-system
```
```
## create secret
```
```shell
$ kubectl create secret generic -n metallb-system memberlist --from-literal=secretkey="$(openssl rand -base64 128)"
```
```shell
$ cat metallb-configmap.yaml
```
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  namespace: metallb-system
  name: config
data:
  config: |
    address-pools:
    - name: default
      protocol: layer2
      addresses:
      - 192.168.100.50-192.168.100.65
```
```shell
$ kubectl apply -f metallb-configmap.yaml
```
```
configmap/config created
```
```shell
$ kubectl get all -n metallb-system
```
```
NAME                              READY   STATUS    RESTARTS   AGE
pod/controller-684f5d9b49-jkzfk   1/1     Running   0          2m14s
pod/speaker-b457r                 1/1     Running   0          2m14s
pod/speaker-bzmmj                 1/1     Running   0          2m14s
pod/speaker-gphh5                 1/1     Running   0          2m14s
pod/speaker-lktgc                 1/1     Running   0          2m14s

NAME                     DESIRED   CURRENT   READY   UP-TO-DATE   AVAILABLE   NODE SELECTOR                 AGE
daemonset.apps/speaker   4         4         4       4            4           beta.kubernetes.io/os=linux   2m14s

NAME                         READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/controller   1/1     1            1           2m14s

NAME                                    DESIRED   CURRENT   READY   AGE
replicaset.apps/controller-684f5d9b49   1         1         1       2m14s
```

Install NGINX.

```shell
$ helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx --force-update
```
```shell
$ helm install ingress-nginx ingress-nginx/ingress-nginx
```


Create ingress for accessing the application deployed in the cluster and to access the Administration console.

```shell
$ cat ingress.yaml
```
```yaml
apiVersion: networking.k8s.io/v1beta1
kind: Ingress
metadata:
  name: sample-nginx-ingress-pathrouting
  namespace: sample-domain1-ns
  annotations:
    kubernetes.io/ingress.class: nginx
spec:
  rules:
  - host:
    http:
      paths:
      - path: /
        backend:
          serviceName: sample-domain1-cluster-cluster-1
          servicePort: 8001
      - path: /console
        backend:
          serviceName: sample-domain1-admin-server
          servicePort: 7001
```
```shell
$ kubectl apply -f ingress.yaml
```

Verify ingress is running.

```shell
$ kubectl get ingresses -n sample-domain1-ns
```
```
NAME                               CLASS    HOSTS   ADDRESS          PORTS   AGE
sample-nginx-ingress-pathrouting   <none>   *       192.168.100.50   80      7m18s
```

Access the Administration Console using the load balancer IP address, `http://192.168.100.50/console`

Access the sample application.

```
# Access the sample application using the load balancer IP (192.168.100.50)
```
```shell
$ curl http://192.168.100.50/myapp_war/index.jsp
```
```html
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
```
```shell
$ curl http://192.168.100.50/myapp_war/index.jsp
```
```html
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

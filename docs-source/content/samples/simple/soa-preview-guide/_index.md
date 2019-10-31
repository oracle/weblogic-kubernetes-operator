---
title: "SOA preview guide"
date: 2019-10-14T11:21:31-05:00
weight: 7
description: "End-to-end guide for SOA Suite preview testers."
---

### End-to-end guide for Oracle SOA Suite preview testers

This document provides detailed instructions for testing the Oracle SOA Suite preview.
This guide uses the WebLogic Kubernetes operator version 2.3.0 and SOA Suite 12.2.1.3.0.
SOA Suite has also been tested using the WebLogic Kubernetes operator version 2.2.1.
SOA Suite is currently a *preview*, meaning that everything is tested and should work,
but official support is not available yet.
You can, however, come to [our public Slack](https://weblogic-slack-inviter.herokuapp.com/) channel to ask questions
and provide feedback.
At Oracle OpenWorld 2019, we announced our *intention* to provide official
support for SOA Suite running on Kubernetes in 2020 (subject to the standard Safe Harbor statement).
For planning purposes, it would be reasonable to assume that the production support would
likely be for Oracle SOA Suite 12.2.1.4.0.

{{% notice warning %}}
Oracle SOA Suite is currently only supported for non-production use in Docker and Kubernetes.  The information provided
in this document is a *preview* for early adopters who wish to experiment with Oracle SOA Suite in Kubernetes before
it is supported for production use.
{{% /notice %}}

#### Overview

This guide will help you to test the Oracle SOA Suite preview in Kubernetes.  The guide presents
a complete end-to-end example of setting up and using SOA Suite in Kubernetes including:

* [Preparing your Kubernetes cluster](#preparing-your-kubernetes-cluster).
* [Obtaining the necessary Docker images](#obtaining-the-necessary-docker-images).
* [Installing the WebLogic Kubernetes operator](#installing-the-weblogic-kubernetes-operator).
* [Preparing your database for the SOAINFRA schemas](#preparing-your-database-for-the-soainfra-schemas).
* [Running the Repository Creation Utility to populate the database](#running-the-repository-creation-utility-to-populate-the-database).
* [Creating a SOA domain](#creating-a-soa-domain).
* [Starting the SOA domain in Kubernetes](#starting-the-soa-domain-in-kubernetes).
* [Setting up a load balancer to access various SOA endpoints](#setting-up-a-load-balancer-to-access-various-soa-endpoints).
* [Configuring the SOA cluster for access through a load balancer](#configuring-the-soa-cluster-for-access-through-a-load-balancer).
* [Deploying a SCA composite to the domain](#deploying-a-sca-composite-to-the-domain).
* [Accessing the SCA composite and various SOA web interfaces](#accessing-the-sca-composite-and-various-soa-web-interfaces).
* [Configuring the domain to send logs to Elasticsearch](#configuring-the-domain-to-send-logs-to-elasticsearch).
* [Using Kibana to view logs for the domain](#using-kibana-to-view-logs-for-the-domain).
* [Configuring the domain to send metrics to Prometheus](#configuring-the-domain-to-send-metrics-to-prometheus).
* [Using the Grafana dashboards to view metrics for the domain](#using-the-grafana-dashboards-to-view-metrics-for-the-domain).

{{% notice note %}}
**Feedback**  
If you find any issues with this guide, please [open an issue in our GitHub repository](https://github.com/oracle/weblogic-kubernetes-operator/issues/new)
or report it on [our public Slack](https://weblogic-slack-inviter.herokuapp.com/) channel.  Thanks!
{{% /notice %}}

#### Preparing your Kubernetes cluster

To follow the instructions in this guide, you will need a Kubernetes cluster.
In this guide, the examples are shown using Oracle Container Engine for Kubernetes,
Oracle's managed Kubernetes service.  For detailed information, see
[the documentation](https://docs.cloud.oracle.com/iaas/Content/ContEng/Concepts/contengoverview.htm).
If you do not have your own Kubernetes cluster, you can [try Oracle Cloud for free](https://www.oracle.com/cloud/free/)
and get a cluster using the free credits, which will provide enough time to work through this
whole guide. You can also use any of the other [supported Kubernetes distributions]({{< relref "/userguide/introduction/introduction" >}}).

##### A current version of Kubernetes

To confirm that your Kubernetes cluster is suitable for SOA Suite, you should confirm
you have a reasonably recent version of Kubernetes, 1.13 or later is recommended.
You can check the version of Kubernetes with this command:

```bash
$ kubectl version
Client Version: version.Info{Major:"1", Minor:"15", GitVersion:"v1.15.3", GitCommit:"2d3c76f9091b6bec110a5e63777c332469e0cba2", GitTreeState:"clean", BuildDate:"2019-08-19T11:13:54Z", GoVersion:"go1.12.9", Compiler:"gc", Platform:"linux/amd64"}
Server Version: version.Info{Major:"1", Minor:"13+", GitVersion:"v1.13.5-6+d6ea2e3ed7815b", GitCommit:"d6ea2e3ed7815b9b53d854038041f43b0a98555e", GitTreeState:"clean", BuildDate:"2019-09-19T23:10:35Z", GoVersion:"go1.11.5", Compiler:"gc", Platform:"linux/amd64"}
```

This output shows that the Kubernetes cluster (the "Server Version" section) is running version 1.13.5.

##### Adequate CPU and RAM

Make sure that your worker nodes have enough memory and CPU resources.  If you plan to run a SOA
domain with two Managed Servers and an Administration Server, plus a database, then a good
rule of thumb would be to have at least 12GB of available RAM between your worker nodes.
We came up with that number by allowing 4GB each for the database, and each of the three
WebLogic Servers.

You can use the following commands to check how many worker nodes you have, and to check
the available CPU and memory for each:

```bash
$ kubectl get nodes
NAME        STATUS   ROLES   AGE   VERSION
10.0.10.2   Ready    node    54m   v1.13.5
10.0.10.3   Ready    node    54m   v1.13.5
10.0.10.4   Ready    node    54m   v1.13.5

$ kubectl get nodes -o jsonpath='{.items[*].status.capacity}'
map[cpu:16 ephemeral-storage:40223552Ki hugepages-1Gi:0 hugepages-2Mi:0 memory:123485928Ki pods:110] map[cpu:16 ephemeral-storage:40223552Ki hugepages-1Gi:0 hugepages-2Mi:0 memory:123485928Ki pods:110] map[cpu:16 ephemeral-storage:40223552Ki hugepages-1Gi:0 hugepages-2Mi:0 memory:123485928Ki pods:110]
2019-10-30 09:39:21:~
```

From the output shown, you can see that this cluster has three worker nodes, and each one has 16 cores and about 120GB of RAM.

##### Helm installed

You will need to have Helm installed on your client machine (the machine where you run `kubectl` commands) and the "Tiller"
component installed in your cluster.

You can obtain Helm from their [releases page](https://github.com/helm/helm/releases/tag/v2.14.3).
The examples in this guide use version 2.14.3.  You must ensure that the version you choose is
compatible with the version of Kubernetes that you are running.

To install the "Tiller" component on your Kubernetes cluster, use this command:

```bash
$ helm init
```

Typically, it will take about 30-60 seconds for Tiller to be deployed and to start.
To confirm that Tiller is running, use this command:

```bash
$ kubectl -n kube-system get pods  | grep tiller
tiller-deploy-5545b55857-rq8gp          1/1     Running   0          81m
```

The output should show the status `Running`.

**Note**: More information about the Helm requirement can be found [here]({{< relref "/userguide/managing-operators" >}}).

{{% notice note %}}
All Kubernetes distributions and managed services have small differences.  In particular,
the way that persistent storage and load balancers are managed varies significantly.  
You may need to adjust the instructions in this guide to suit your particular flavor of Kubernetes.
{{% /notice %}}


#### Obtaining the necessary Docker images

You will need the Docker images to run SOA Suite, the Oracle database,
and the WebLogic Kubernetes operator.

##### Accept license agreements

These Docker images are
available in the [Oracle Container Registry](https://container-registry.oracle.com).
Before you can pull the images, you will need to log on to the
web interface and accept the license agreements.

From the [Home page](https://container-registry.oracle.com), select the
**Middleware** category, and then select the **soasuite** repository.

![Oracle Container Registry - Oracle SOA Suite page](/weblogic-kubernetes-operator/images/ocr-sign-in-page.png)

In the right pane, click **Sign In** and use your Oracle Account to authenticate.
The license agreement will be displayed; you must accept the terms
and conditions.  After you have accepted, you will be able to pull this
image.

Repeat these steps to also select the license for the **enterprise**
repository in the **Database** category.

You do not need to accept a license for the WebLogic Kubernetes operator
Docker image.

##### Confirm access to the images

To confirm that you have access to the images, you can log in to the Oracle
Container Registry and pull the images using these commands:

```bash
$ docker login container-registry.oracle.com
$ docker pull container-registry.oracle.com/database/enterprise:12.2.0.1
$ docker pull container-registry.oracle.com/middleware/soasuite:12.2.1.3
```

{{% notice note %}}
If you are not running these commands on one of your Kubernetes worker nodes,
then strictly speaking, you do not need to pull the images onto your
client machine.  This step is just to confirm that you have successfully
completed the license acceptance and have access to the images.
{{% /notice %}}

In order for your Kubernetes cluster to access these images, you will need
to create Docker registry secrets and attach these to Service Accounts in the
Kubernetes Namespaces where they are needed.
This will be covered later in this document, when it is needed.

#### Installing the WebLogic Kubernetes operator

You will use the WebLogic Kubernetes operator to manage the SOA domain.

##### Grant Tiller the cluster-admin role

To install the WebLogic Kubernetes operator, you must first give the Tiller
Service Account the `cluster-admin` role using this command:

```bash
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

output:
clusterrolebinding "helm-user-cluster-admin-role" configured
```

This will allow Tiller (the Helm server component) to be able to perform
the necessary operations in the Kubernetes cluster that are required to
install an operator.

##### Create a namespace

You can optionally install the WebLogic Kubernetes operator in its
own namespace.  If you prefer, you can just install it in the `default`
namespace.

To create a namespace, use this command:

```bash
$ kubectl create ns operator
namespace/operator created
```

You can change `operator` to your preferred name.  If you chose a different
name, you will need to adjust the commands in the following sections to
use the name you chose.

##### Clone the operator GitHub repository

Make a clone of the WebLogic Kubernetes operator GitHub repository on your
client machine and change into that directory using these commands:

```bash
$ git clone https://github.com/oracle/weblogic-kubernetes-operator
Cloning into 'weblogic-kubernetes-operator'...
remote: Enumerating objects: 461, done.
remote: Counting objects: 100% (461/461), done.
remote: Compressing objects: 100% (272/272), done.
remote: Total 99543 (delta 191), reused 303 (delta 103), pack-reused 99082
Receiving objects: 100% (99543/99543), 71.16 MiB | 5.08 MiB/s, done.
Resolving deltas: 100% (59255/59255), done.
Updating files: 100% (6481/6481), done.

$ cd weblogic-kubernetes-operator
```

You will use several samples from this repository during this guide.

##### Install the operator

To install the operator, use the following command:

```bash
$ helm install \
       kubernetes/charts/weblogic-operator \
       --name weblogic-operator \
       --namespace operator \
       --set image=oracle/weblogic-kubernetes-operator:2.3.0 \
       --set "domainNamespaces={}"

NAME:   weblogic-operator
LAST DEPLOYED: Wed Oct 30 11:01:20 2019
NAMESPACE: operator
STATUS: DEPLOYED

RESOURCES:
==> v1/ClusterRole
NAME                                                   AGE
operator-weblogic-operator-clusterrole-domain-admin    2s
operator-weblogic-operator-clusterrole-general         2s
operator-weblogic-operator-clusterrole-namespace       2s
operator-weblogic-operator-clusterrole-nonresource     2s
operator-weblogic-operator-clusterrole-operator-admin  2s

==> v1/ClusterRoleBinding
NAME                                                          AGE
operator-weblogic-operator-clusterrolebinding-auth-delegator  2s
operator-weblogic-operator-clusterrolebinding-discovery       2s
operator-weblogic-operator-clusterrolebinding-general         2s
operator-weblogic-operator-clusterrolebinding-nonresource     2s

==> v1/ConfigMap
NAME                  DATA  AGE
weblogic-operator-cm  2     2s

==> v1/Pod(related)
NAME                                READY  STATUS             RESTARTS  AGE
weblogic-operator-7c95fd48cf-w427t  0/1    ContainerCreating  0         1s

==> v1/Role
NAME                    AGE
weblogic-operator-role  2s

==> v1/RoleBinding
NAME                                     AGE
weblogic-operator-rolebinding            2s
weblogic-operator-rolebinding-namespace  2s

==> v1/Secret
NAME                       TYPE    DATA  AGE
weblogic-operator-secrets  Opaque  0     2s

==> v1/Service
NAME                            TYPE       CLUSTER-IP    EXTERNAL-IP  PORT(S)   AGE
internal-weblogic-operator-svc  ClusterIP  10.96.169.15  <none>       8082/TCP  2s

==> v1beta1/Deployment
NAME               READY  UP-TO-DATE  AVAILABLE  AGE
weblogic-operator  0/1    1           0          1s
```

Sample output is shown above, yours may look slightly different.  The operator will take
a short time to start up (normally less than 30 seconds).  Confirm that it has reached
the `Running` state with this command:

```bash
$ kubectl get pods -n operator
NAME                                 READY   STATUS    RESTARTS   AGE
weblogic-operator-7c95fd48cf-w427t   1/1     Running   0          2m41s
```

If your operator pod is not in the `Running` state, you will need to fix that
issue before proceeding.  The most common issue is not being able to pull
the Docker image.  You can check on the issues using the described command:

```bash
$ kubectl -n operator describe pod weblogic-operator-7c95fd48cf-w427t
```

The output of this command will tell you what issue is preventing the operator
from starting successfully.


#### Preparing your database for the SOAINFRA schemas

SOA Suite requires a database where it stores its configuration and runtime
data.  You can run the database inside Kubernetes for testing and development
purposes.  For a production deployment, you should run the database outside
Kubernetes.  In this example, you will run the database inside the same
Kubernetes cluster that SOA Suite is running in.

{{% notice warning %}}
The Oracle Database Docker images are supported only for non-production use.
For more details, see My Oracle Support note:
Oracle Support for Database Running on Docker (Doc ID 2216342.1)
{{% /notice %}}

**Note**: More detailed information about options for configuring access
to your database can be found [here]({{< relref "/userguide/managing-fmw-domains/soa-suite#configuring-access-to-your-database" >}}),
but this document contains all of the important information.

##### Create a namespace for SOA Suite and the database

Create a Kubernetes namespace to run SOA Suite and the database using this command:

```bash
$ kubectl create ns soans
namespace/soans created
```

**Note**: You can choose to run the database in a different namespace than SOA, or in the
`default` namespace.  If you choose a different namespace, you will need
to adjust the commands in the following sections.


##### Create persistent volumes for the database files

Create some persistent storage for the database files.  

{{% notice note %}}
The mechanism for creating persistent storage varies significantly across different variants
of Kubernetes and different managed Kubernetes services.  You will need
to consult the documentation for your particular variant to learn how to
allocate persistent storage.  Note that you are running a single node
database in this example, so you can use `ReadWriteOnce` storage - which can only be
mounted read/write by a single pod at any given time.  
{{% /notice %}}

The following example demonstrates how to allocate persistent storage in
the Oracle Container Engine for Kubernetes, which allows you to request
storage from the Block Storage service.  Detailed documentation is
available [here](https://docs.cloud.oracle.com/iaas/Content/ContEng/Tasks/contengcreatingpersistentvolumeclaim.htm).

To allocate 50GB of storage, you create the following Kubernetes YAML file:

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: soadb-pvc
  namespace: soans
spec:
  storageClassName: "oci"
  selector:
    matchLabels:
      failure-domain.beta.kubernetes.io/zone: "EU-FRANKFURT-1-AD-1"
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 50Gi

```

You will have to update this file with the correct zone (from the list [here](https://docs.cloud.oracle.com/iaas/Content/ContEng/Concepts/contengprerequisites.htm#Availab)).  If you chose a different namespace, you will need to specify it in the `metadata` section.

Apply this YAML file to your cluster using this command:

```bash
$ kubectl apply -f soadb-pvc.yaml
persistentvolumeclaim/soadb-pvc created
```

It will take a short time (typically less that a minute) to provision the storage
and create a file system on it.  During this time, the persistent volume
claim will display the state as `Pending`.  After the storage is
provisioned, the status will display `Bound`.  You can check the status with
this command:

```bash
$ kubectl get pvc -n soans
NAME        STATUS   VOLUME                                                    CAPACITY   ACCESS MODES   STORAGECLASS   AGE
soadb-pvc   Bound    ocid1.volume.oc1.eu-frankfurt-1.abtheljspyxxxxxx4zfgcsa   50Gi       RWO            oci            16s
```

**Note**: The output is shortened to fit on the screen.

##### Create secrets to allow the database image to be pulled

As mentioned earlier, you need to create a Docker registry secret in this
namespace and attach that to the Service Account so that the Oracle Database
images can be pulled from the Oracle Container Registry.

To create the Docker registry secret, use a command like this:

```bash
$ kubectl create secret \
          docker-registry \
          oracle-container-reg \
          --docker-server=container-registry.oracle.com \
          --docker-username=your.name@wherever.com \
          --docker-password=your-password \
          --docker-email=your.name@wherever.com \
          --namespace=soans
```

You will need to provide the correct user name, password, and email address in this command.
Note that `oracle-container-reg` is the name of the secret in this example, and
`docker-registry` is the type of secret to create.  You can choose a different name,
but you must use this type.

Now update the Service Account to use this secret, using this command:

```bash
$ kubectl patch serviceaccount default \
          -p '{"imagePullSecrets": [{"name": "oracle-container-reg"}]}' \
          -n soans
```

This example uses the `default` Service Account in the `soans`  namespace.

You can confirm that the Service Account was updated with this command:

```bash
$ kubectl -n soans get sa default -o yaml
apiVersion: v1
imagePullSecrets:
- name: oracle-container-reg
kind: ServiceAccount
metadata:
  creationTimestamp: "2019-10-30T16:51:34Z"
  name: default
  namespace: soans
  resourceVersion: "41031"
  selfLink: /api/v1/namespaces/soans/serviceaccounts/default
  uid: 87c3dcd4-xxxx-11e9-xxxx-0a580aed58e1
secrets:
- name: default-token-hsjjp
```

In the example output, you can see that the `oracle-container-reg` secret
has been added to the `default` Service Account's `imagePullSecrets` list.


##### Create the database

To create the database pod and service, you need to create a Kubernetes YAML
file similar to the one shown below.  This example is provided in the WebLogic
Kubernetes operator repository in this location:

`kubernetes/samples/scripts/create-soa-domain/create-database/db-with-pv.yaml`

{{% notice warning %}}
**TODO FOR MARK**  
In `develop` branch, this file is moved to:
`kubernetes/samples/scripts/create-soa-domain/domain-home-on-pv/create-database/db-with-pv.yaml`  
Need to update this after that change is merged to `master`
{{% /notice %}}

Here are the contents of the example:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: soadb
  labels:
    app: soadb
  namespace: soans
spec:
  ports:
  - port: 1521
    name: server-port
  - port: 5500
    name: em-port
  clusterIP: None
  selector:
    app: soadb
---
apiVersion: apps/v1beta1
kind: StatefulSet
metadata:
  name: soadb
  namespace: soans
spec:
  serviceName: "soadb"
  replicas: 1
  template:
    metadata:
      labels:
        app: soadb
    spec:
      terminationGracePeriodSeconds: 30
      containers:
      - name: soadb
        image: coantiner-registry.oracle.com/database/enterprise:12.2.0.1
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 1521
          name: server-port
        - containerPort: 5500
          name: em-port
        env:
        - name: DB_SID
          value: soadb
        - name: DB_PDB
          value: soapdb
        - name: DB_DOMAIN
          value: my.domain.com
        - name: DB_BUNDLE
          value: basic
        readinessProbe:
          exec:
            command:
            - grep
            - "Done ! The database is ready for use ."
            - "/home/oracle/setup/log/setupDB.log"
          initialDelaySeconds: 300
          periodSeconds: 5
        volumeMounts:
        - mountPath: /ORCL
          name: soadb-storage
      volumes:
      - name: soadb-storage
        persistentVolumeClaim:
          claimName: soadb-pvc
```

If you are using the Oracle Container Engine for Kubernetes and you created the persistent
volume claim as shown above, you will need to add an `initContainer` to change the
owner of the file system to the correct `oracle` user that the Oracle Database image
uses (which is uid 54321).  You can do this by adding the lines shown below:

```yaml
    ... lines omitted ...
    spec:
      terminationGracePeriodSeconds: 30
      # add the lines after this line
      initContainers:
      - name: fix-pvc-owner
        image:  busybox
        command: ["sh", "-c", "chown -R 54321:54321 /ORCL"]
        volumeMounts:
        - name: soadb-storage
          mountPath: /ORCL
      # add the lines before this line
      containers:
      - name: soadb
        image: coantiner-registry.oracle.com/database/enterprise:12.2.0.1
    ... lines omitted ...
```        

After updating this file, if you chose a different namespace, persistent volume claim name,
etc., apply this file to your cluster using this command:

```bash
$ kubectl apply -f kubernetes/samples/scripts/create-soa-domain/create-database/db-with-pv.yaml
service/soadb created
statefulset.apps/soadb created
```

You can confirm the pod is being created with this command:

```bash
$ kubectl get pods  -n soans
NAME      READY   STATUS              RESTARTS   AGE
soadb-0   0/1     ContainerCreating   0          22s
```

It will take a short period of time to pull the image (might be a few minutes, depending on
where your cluster is running) and then the pod will start.  During this time, you can use
this command to check on progress:

```bash
$ kubectl -n soans describe pod soadb-0
// (lines omitted)
Events:
  Type    Reason                  Age    From                     Message
  ----    ------                  ----   ----                     -------
  Normal  Scheduled               3m14s  default-scheduler        Successfully assigned soans/soadb-0 to 10.0.10.4
  Normal  SuccessfulAttachVolume  2m58s  attachdetach-controller  AttachVolume.Attach succeeded for volume "ocid1.volume.oc1.eu-frankfurt-1.abtheljspyxxxxxx4zfgcsa"
  Normal  Pulling                 2m37s  kubelet, 10.0.10.4       pulling image "container-registry.oracle.com/database/enterprise:12.2.0.1"
```

After the pod starts, you can watch its output with this command:

```bash
$ kubectl -n soans logs soadb-0 -f
```

You will see it setting up the database, and after a few minutes, it will display
this message indicating that the database is ready to use.  Here is an example of the
output:

```text
Setup Oracle Database
ls: cannot access /ORCL//u01/app/oracle/product/12.2.0/dbhome_1/dbs: No such file or directory
Oracle Database 12.2.0.1 Setup
Wed Oct 30 22:08:49 UTC 2019

Check parameters ......
log file is : /home/oracle/setup/log/paramChk.log
paramChk.sh is done at 0 sec

untar DB bits ......
log file is : /home/oracle/setup/log/untarDB.log
untarDB.sh is done at 47 sec

config DB ......
log file is : /home/oracle/setup/log/configDB.log

DBNEWID: Release 12.2.0.1.0 - Production on Wed Oct 30 22:11:00 2019

Copyright (c) 1982, 2017, Oracle and/or its affiliates.  All rights reserved.

Connected to database ORCLCDB (DBID=2722566360)

Connected to server version 12.2.0

Control Files in database:
    /u02/app/oracle/oradata/ORCLCDB/cntrlORCLCDB.dbf
    /u03/app/oracle/fast_recovery_area/ORCLCDB/cntrlORCLCDB2.dbf

Change database ID and database name ORCLCDB to SOADB? (Y/[N]) =>
Proceeding with operation
Changing database ID from 2722566360 to 1896944501
Changing database name from ORCLCDB to SOADB
    Control File /u02/app/oracle/oradata/ORCLCDB/cntrlORCLCDB.dbf - modified
    Control File /u03/app/oracle/fast_recovery_area/ORCLCDB/cntrlORCLCDB2.dbf - modified
    Datafile /u02/app/oracle/oradata/ORCL/system01.db - dbid changed, wrote new name
    Datafile /u02/app/oracle/oradata/ORCL/sysaux01.db - dbid changed, wrote new name
    Datafile /u02/app/oracle/oradata/ORCL/pdbseed/system01.db - dbid changed, wrote new name
    Datafile /u02/app/oracle/oradata/ORCL/pdbseed/sysaux01.db - dbid changed, wrote new name
    Datafile /u02/app/oracle/oradata/ORCL/users01.db - dbid changed, wrote new name
    Datafile /u02/app/oracle/oradata/ORCL/pdbseed/undotbs01.db - dbid changed, wrote new name
    Datafile /u02/app/oracle/oradata/ORCL/undotbs01.db - dbid changed, wrote new name
    Datafile /u02/app/oracle/oradata/ORCL/temp01.db - dbid changed, wrote new name
    Datafile /u02/app/oracle/oradata/ORCL/pdbseed/temp012017-03-02_07-54-38-075-AM.db - dbid changed, wrote new name
    Control File /u02/app/oracle/oradata/ORCLCDB/cntrlORCLCDB.dbf - dbid changed, wrote new name
    Control File /u03/app/oracle/fast_recovery_area/ORCLCDB/cntrlORCLCDB2.dbf - dbid changed, wrote new name
    Instance shut down

Database name changed to SOADB.
Modify parameter file and generate a new password file before restarting.
Database ID for database SOADB changed to 1896944501.
All previous backups and archived redo logs for this database are unusable.
Database is not aware of previous backups and archived logs in Recovery Area.
Database has been shutdown, open database with RESETLOGS option.
Successfully changed database name and ID.
DBNEWID - Completed successfully.

Wed Oct 30 22:09:36 UTC 2019
Start Docker DB configuration
Call configDBora.sh to configure database
Wed Oct 30 22:09:37 UTC 2019
Configure DB as oracle user
Setup Database directories ...

SQL*Plus: Release 12.2.0.1.0 Production on Wed Oct 30 22:09:37 2019

Copyright (c) 1982, 2016, Oracle.  All rights reserved.

Connected to an idle instance.

SQL> ORACLE instance started.

Total System Global Area 1342177280 bytes
Fixed Size		    8792536 bytes
Variable Size		  570426920 bytes
Database Buffers	  754974720 bytes
Redo Buffers		    7983104 bytes
Database mounted.
SQL> Disconnected from Oracle Database 12c Enterprise Edition Release 12.2.0.1.0 - 64bit Production
NID change db name

SQL*Plus: Release 12.2.0.1.0 Production on Wed Oct 30 22:11:16 2019

Copyright (c) 1982, 2016, Oracle.  All rights reserved.

Connected to an idle instance.

SQL>
File created.

SQL> ORACLE instance started.

Total System Global Area 1342177280 bytes
Fixed Size		    8792536 bytes
Variable Size		  570426920 bytes
Database Buffers	  754974720 bytes
Redo Buffers		    7983104 bytes
Database mounted.
SQL>
Database altered.

SQL>
Database altered.

SQL>
NAME				     TYPE	 VALUE
------------------------------------ ----------- ------------------------------
spfile				     string	 /u01/app/oracle/product/12.2.0
						 /dbhome_1/dbs/spfilesoadb.ora
SQL>
NAME				     TYPE	 VALUE
------------------------------------ ----------- ------------------------------
encrypt_new_tablespaces 	     string	 CLOUD_ONLY
SQL>
User altered.

SQL>
User altered.

SQL> Disconnected from Oracle Database 12c Enterprise Edition Release 12.2.0.1.0 - 64bit Production
update password

Enter password for SYS:
create pdb : soapdb

SQL*Plus: Release 12.2.0.1.0 Production on Wed Oct 30 22:11:30 2019

Copyright (c) 1982, 2016, Oracle.  All rights reserved.


Connected to:
Oracle Database 12c Enterprise Edition Release 12.2.0.1.0 - 64bit Production

SQL>   2    3    4    5  
Pluggable database created.

SQL>
Pluggable database altered.

SQL>
Pluggable database altered.

SQL> Disconnected from Oracle Database 12c Enterprise Edition Release 12.2.0.1.0 - 64bit Production
Reset Database parameters

SQL*Plus: Release 12.2.0.1.0 Production on Wed Oct 30 22:13:04 2019

Copyright (c) 1982, 2016, Oracle.  All rights reserved.


Connected to:
Oracle Database 12c Enterprise Edition Release 12.2.0.1.0 - 64bit Production

SQL>
System altered.

SQL> Disconnected from Oracle Database 12c Enterprise Edition Release 12.2.0.1.0 - 64bit Production

LSNRCTL for Linux: Version 12.2.0.1.0 - Production on 30-OCT-2019 22:13:04

Copyright (c) 1991, 2016, Oracle.  All rights reserved.

Starting /u01/app/oracle/product/12.2.0/dbhome_1/bin/tnslsnr: please wait...

TNSLSNR for Linux: Version 12.2.0.1.0 - Production
System parameter file is /u01/app/oracle/product/12.2.0/dbhome_1/admin/soadb/listener.ora
Log messages written to /u01/app/oracle/diag/tnslsnr/soadb-0/listener/alert/log.xml
Listening on: (DESCRIPTION=(ADDRESS=(PROTOCOL=tcp)(HOST=0.0.0.0)(PORT=1521)))
Listening on: (DESCRIPTION=(ADDRESS=(PROTOCOL=ipc)(KEY=EXTPROC1521)))

Connecting to (DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=0.0.0.0)(PORT=1521)))
STATUS of the LISTENER
------------------------
Alias                     LISTENER
Version                   TNSLSNR for Linux: Version 12.2.0.1.0 - Production
Start Date                30-OCT-2019 22:13:04
Uptime                    0 days 0 hr. 0 min. 0 sec
Trace Level               off
Security                  ON: Local OS Authentication
SNMP                      OFF
Listener Parameter File   /u01/app/oracle/product/12.2.0/dbhome_1/admin/soadb/listener.ora
Listener Log File         /u01/app/oracle/diag/tnslsnr/soadb-0/listener/alert/log.xml
Listening Endpoints Summary...
  (DESCRIPTION=(ADDRESS=(PROTOCOL=tcp)(HOST=0.0.0.0)(PORT=1521)))
  (DESCRIPTION=(ADDRESS=(PROTOCOL=ipc)(KEY=EXTPROC1521)))
The listener supports no services
The command completed successfully

DONE!
Remove password info
Docker DB configuration is complete !
configDB.sh is done at 255 sec

Done ! The database is ready for use .
# ===========================================================================  
# == Add below entries to your tnsnames.ora to access this database server ==  
# ====================== from external host =================================  
soadb=(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=<ip-address>)(PORT=<port>))
    (CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=soadb.localdomain)))     
soapdb=(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=<ip-address>)(PORT=<port>))
    (CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=soapdb.localdomain)))     
#                                                                              
#ip-address : IP address of the host where the container is running.           
#port       : Host Port that is mapped to the port 1521 of the container.      
#                                                                              
# The mapped port can be obtained from running "docker port <container-id>"  
# ===========================================================================  
SOAPDB(3):Database Characterset for SOAPDB is AL32UTF8
SOAPDB(3):Opatch validation is skipped for PDB SOAPDB (con_id=0)
2019-10-30T22:13:03.813639+00:00
SOAPDB(3):Opening pdb with no Resource Manager plan active
Pluggable database SOAPDB opened read write
Completed:     alter pluggable database soapdb open
    alter pluggable database all save state
Completed:     alter pluggable database all save state
2019-10-30T22:13:04.220268+00:00
ALTER SYSTEM SET encrypt_new_tablespaces='DDL' SCOPE=BOTH;
```

After you see this line, you can proceed:

```text
Done ! The database is ready for use .
```

The next step is to use the Repository Creation Utility (RCU) to create
the SOA database schemas.


#### Running the Repository Creation Utility to populate the database

Now that the database is running, you need to create the SOA schemas
in the database using the Repository Creation Utility (RCU).  To do
this, you can start up a "utility" pod that can just be used for 
running interactive commands. 

Start up a "utility" pod using this command:

```bash
$ kubectl run rcu \
          --generator=run-pod/v1 \
          --image container-registry.oracle.com/middleware/soasuite:12.2.1.3 \
          -n soans \
          -- sleep infinity
```

This will start a new pod named `rcu` using the SOA Suite Docker image
which will not run any command, just go into an infinite sleep.
You can check that the pod has started using this command, you may
need to wait a few minutes for it to pull the Docker image before
it starts:

```bash
$ kubectl get pods  -n soans
NAME      READY   STATUS              RESTARTS   AGE
rcu       1/1     Running             0          4m
soadb-0   1/1     Running             0          19h
```

When the pod is running, you can open a shell session inside the pod
using this command:

```bash
$ kubectl exec -n soans -ti rcu /bin/bash
[oracle@rcu ~]
```

To run RCU to create the schemas in the database, you can use
commands like those below.  The connection string needs to match
the Kubernetes service name, port and Oracle Database service name
that you chose earlier.  If you followed the example as-is, then 
the connection string shown here will be correct.  If you put the
database in a different namespace, you will need to add the
namespace after the Kubernetes service name.  For example, if you
put the database in a namespace called `dbns` then your connection
string would be `soadb.dbns:1521/soapdb.my.domain.com`.
You samples that we will use later to create the domain assume
that the RCU prefix is `SOA1`.  If you change the prefix, you will
also need to change the scripts used later to create the SOA
domain.

```bash
$ export CONNECTION_STRING=soadb:1521/soapdb.my.domain.com
$ export RCUPREFIX=SOA1
$ echo -e Oradoc_db1"\n"Welcome1 > /tmp/pwd.txt
$ /u01/oracle/oracle_common/bin/rcu \
  -silent \
  -createRepository \
  -databaseType ORACLE \
  -connectString $CONNECTION_STRING \
  -dbUser sys \
  -dbRole sysdba \
  -useSamePasswordForAllSchemaUsers true \
  -selectDependentsForComponents true \
  -variables SOA_PROFILE_TYPE=SMALL,HEALTHCARE_INTEGRATION=NO \
  -schemaPrefix $RCUPREFIX \
  -component MDS \
  -component IAU \
  -component IAU_APPEND \
  -component IAU_VIEWER \
  -component OPSS \
  -component WLS \
  -component STB \
  -component SOAINFRA \
  -f < /tmp/pwd.txt
```

Here is an example of the output from RCU:

```text
	RCU Logfile: /tmp/RCU2019-10-31_18-17_468416136/logs/rcu.log

Processing command line ....
Repository Creation Utility - Checking Prerequisites
Checking Global Prerequisites

Repository Creation Utility - Checking Prerequisites
Checking Component Prerequisites
Repository Creation Utility - Creating Tablespaces
Validating and Creating Tablespaces
Repository Creation Utility - Create
Repository Create in progress.
Percent Complete: 11
Percent Complete: 27
Percent Complete: 27
Percent Complete: 28
... (lines omitted) ...
Percent Complete: 94
Percent Complete: 98
Percent Complete: 98
Percent Complete: 100
```

Just like in a normal on-premises installation of SOA Suite, you need to
make sure you maintain the one to one relationship between this set of
SOA Schemas and the domain that you create with them.  During domain
creation, various information that is specific to the domain will be
written into these schemas (especially by Oracle Platform Security 
Services).


##### Dropping schemas

Just like in a normal on-premises installation of SOA Suite, if the domain
creation fails, you will need to use RCU to drop the schemas and create
new ones before retrying domain creation.
If you need to drop the RCU schemas for any reason, you can use the following 
commands to drop the schemas, with the same caveats as above about setting the
correct connection string and so on:

{{% notice warning %}}
The following commands are not part of the normal process.
These are given for recovery/retry purposes only!
{{% /notice %}}

```bash
$ kubectl exec -n soans -ti rcu /bin/bash

$ ### THESE COMMANDS ARE EXECUTED INSIDE THE CONTAINER  ###
$ export CONNECTION_STRING=soadb:1521/soapdb.my.domain.com
$ export RCUPREFIX=SOA1
$ echo -e Oradoc_db1"\n"Welcome1 > /tmp/pwd.txt
$ /u01/oracle/oracle_common/bin/rcu \
  -silent \
  -dropRepository \
  -databaseType ORACLE \
  -connectString $CONNECTION_STRING \
  -dbUser sys \
  -dbRole sysdba \
  -selectDependentsForComponents true \
  -schemaPrefix $RCUPREFIX \
  -component MDS \
  -component IAU \
  -component IAU_APPEND \
  -component IAU_VIEWER \
  -component OPSS \
  -component WLS \
  -component STB \
  -component SOAINFRA \
  -f < /tmp/pwd.txt
```

After the schemas are successfully dropped, you can recreate them and retry
domain creation.

#### Creating a SOA domain

Now that the database is ready, the next step is to create the SOA domain.


#### Starting the SOA domain in Kubernetes


#### Setting up a load balancer to access various SOA endpoints


#### Configuring the SOA cluster for access through a load balancer


#### Deploying a SCA composite to the domain


#### Accessing the SCA composite and various SOA web interfaces


#### Configuring the domain to send logs to Elasticsearch


#### Using Kibana to view logs for the domain


#### Configuring the domain to send metrics to Prometheus


#### Using the Grafana dashboards to view metrics for the domain

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
Before you can pull the images, you will need to log on the
web interface and accept the license agreements.

From the [Home page](https://container-registry.oracle.com), select the
"Middleware" category, and then select the "soasuite" repository.

![Oracle Container Registry - Oracle SOA Suite page](/weblogic-kubernetes-operator/images/ocr-sign-in-page.png)

In the right pane, click "Sign In" and use your Oracle Account to authenticate.
The license agreement will be displayed; you must accept the terms
and conditions.  After you have accepted, you will be able to pull this
image.

Repeat these steps to also select the license for the "enterprise"
repository in the "Database" category.

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

We will use the WebLogic Kubernetes operator to manage the SOA domain.

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

You can optionally install the WebLogic Kubernetes operator into its
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
Kubernetes.  In this example, we will run the database inside the same 
Kubernetes cluster that SOA Suite is running in.

{{% notice warning %}}
The Oracle Database Docker images are supported only for non-production use.
For more details, see My Oracle Support note:
Oracle Support for Database Running on Docker (Doc ID 2216342.1)
{{% /notice %}}

**Note** More detailed information about options for [configuring access
to your database can be found here]({{< relref "/userguide/managing-fmw-domains/soa-suite#configuring-access-to-your-database" >}}),
but this document contains all of the important information.




#### Running the Repository Creation Utility to populate the database


#### Creating a SOA domain


#### Starting the SOA domain in Kubernetes


#### Setting up a load balancer to access various SOA endpoints


#### Configuring the SOA cluster for access through a load balancer


#### Deploying a SCA composite to the domain


#### Accessing the SCA composite and various SOA web interfaces


#### Configuring the domain to send logs to Elasticsearch


#### Using Kibana to view logs for the domain


#### Configuring the domain to send metrics to Prometheus


#### Using the Grafana dashboards to view metrics for the domain

---
title: "Manage SOA domains"
date: 2019-04-18T06:46:23-05:00
weight: 2
description: "SOA domains include the deployment of various Oracle Service-Oriented Architecture (SOA) Suite components, such as SOA, Oracle Service Bus (OSB), and Oracle Enterprise Scheduler (ESS)."
---

#### Contents

* [Limitations](#limitations)
* [Obtaining the SOA Suite Docker image](#obtaining-the-soa-suite-docker-image)
* [Creating a SOA Suite Docker image](#creating-a-soa-suite-docker-image)
* [Configuring access to your database](#configuring-access-to-your-database)
* [Running the Repository Creation Utility to set up your database schema](#running-the-repository-creation-utility-to-set-up-your-database-schema)
* [Create a Kubernetes secret with the RCU credentials](#create-a-kubernetes-secret-with-the-rcu-credentials)
* [Creating a SOA domain](#creating-a-soa-domain)


Starting with the 2.2.1 release, the operator supports deployment of SOA Suite components such as Oracle Service-Oriented Architecture (SOA), Oracle Service Bus (OSB), and Oracle Enterprise Scheduler (ESS). Currently the operator supports these different domain types:

* SOA
* OSB
* SOAESS
* SOAOSB
* SOAESSOSB

This document provides details about the special considerations for running SOA Suite domains with the operator.
Other than those considerations listed here, SOA Suite domains work in the same way as FMW Infrastructure domains and WebLogic Server domains.
That is, the remainder of the documentation applies equally to SOA Suite domains.

In this release, SOA Suite domains are supported using the “domain on a persistent volume”
[model]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}) only, where the domain home is located in a persistent volume (PV).


#### Limitations

Compared to running a WebLogic Server domain in Kubernetes using the operator, the
following limitations currently exist for SOA Suite domains:

* Domain in image is not supported in this version of the operator.
* Only configured clusters are supported.  Dynamic clusters are not supported for
  SOA Suite domains.  Note that you can still use all of the scaling features,
  you just need to define the maximum size of your cluster at domain creation time.
  The maximum server size currently supported is 5.
* SOA Suite domains are not supported with any version of the operator
  before version 2.2.1.
* The [WebLogic Logging Exporter](https://github.com/oracle/weblogic-logging-exporter)
  currently supports WebLogic Server logs only.  Other logs will not be sent to
  Elasticsearch.  Note, however, that you can use a sidecar with a log handling tool
  like Logstash or fluentd to get logs.
* The [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter)
  currently supports the WebLogic MBean trees only.  Support for JRF MBeans has not
  been added yet.


#### Obtaining the SOA Suite Docker Image

The Oracle WebLogic Server Kubernetes Operator requires patch 29135930.
The standard pre-built SOA Suite image, `container-registry.oracle.com/middleware/soasuite:12.2.1.3`, already has this patch applied. For detailed instructions on how to log in to the Oracle Container Registry and accept license agreement, see this [document]({{< relref "/userguide/managing-domains/domain-in-image/base-images/_index.md#obtaining-standard-images-from-the-oracle-container-registry" >}}).

To pull an image from the Oracle Container Registry, in a web browser, navigate to https://container-registry.oracle.com and log in
using the Oracle Single Sign-On authentication service. If you do not already have SSO credentials, at the top of the page, click the Sign In link to create them.  

Use the web interface to accept the Oracle Standard Terms and Restrictions for the Oracle software images that you intend to deploy.
Your acceptance of these terms are stored in a database that links the software images to your Oracle Single Sign-On login credentials.


First, you will need to log into the Oracle Container Registry:

```
$ docker login container-registry.oracle.com
```

Then, you can pull the image with these commands:

```
$ docker pull container-registry.oracle.com/middleware/soasuite:12.2.1.3
$ docker tag  container-registry.oracle.com/middleware/soasuite:12.2.1.3 oracle/soa:12.2.1.3
```

Additional information about using this image is available in the
[Oracle Container Registry](https://container-registry.oracle.com).


#### Creating a SOA Suite Docker image

You can also create a Docker image containing the SOA Suite binaries.
A [sample](https://github.com/oracle/docker-images/tree/master/OracleSOASuite)
is provided in the Oracle GitHub account that demonstrates how to create a Docker image
to run SOA Suite.  

Please consult the [README](https://github.com/oracle/docker-images/blob/master/OracleSOASuite/dockerfiles/README.md) file associated with this sample for important prerequisite steps,
such as building or pulling the Server JRE Docker image, Oracle FMW Infrastructure Docker image, and downloading the SOA Suite installer binary.


You must also install the [required patch]({{< relref "/userguide/introduction/introduction/_index.md#prerequisites" >}})
to use this image with the operator.  A [sample](https://github.com/oracle/docker-images/tree/master/OracleFMWInfrastructure/samples/12213-patch-fmw-for-k8s)
is provided that demonstrates how to create a Docker image with the necessary patch installed. Use this patched image for building the SOA Suite image.

Follow these steps to build the patched SOA image:

After downloading the patch from My Oracle Support, you create the patched image
by running the provided script:

```bash
cd docker-images/OracleFMWInfrastructure/samples/12213-patch-fmw-for-k8s
./build.sh
```

This will produce an image named `oracle/fmw-infrastructure:12213-update-k8s`.

All samples and instructions reference the pre-built and already patched image, `container-registry.oracle.com/middleware/fmw_infrastructure:12.2.1.3`. When building your own image, you will need to rename `oracle/fmw-infrastructure:12213-update-k8s` to `container-registry.oracle.com/middleware/fmw_infrastructure:12.2.1.3`.

```
$ docker tag oracle/fmw-infrastructure:12213-update-k8s container-registry.oracle.com/middleware/fmw_infrastructure:12.2.1.3
```

After cloning the repository and downloading the installer from Oracle Technology Network
or e-delivery, you create your image by running the provided script:

```bash
cd docker-images/OracleFMWInfrastructure/dockerfiles
./buildDockerImage.sh -v 12.2.1.3 -s
```

The image produced will be named `middleware/soasuite/oracle/soasuite:12.2.1.3`.

The Oracle SOA Suite image created through the above step needs to be retagged
from `middleware/soasuite/oracle/soasuite:12.2.1.3` to `oracle/soa:12.2.1.3` before continuing with the next steps.

```bash
$ docker tag middleware/soasuite/oracle/soasuite:12.2.1.3 oracle/soa:12.2.1.3
```

You can use this image to run the Repository Creation Utility and to run your domain using the “domain on a persistent volume” model.

Before creating a domain, you will need to set up the necessary schemas in your database.

#### Configuring access to your database

SOA Suite domains require a database with the necessary schemas installed in them.
A utility called Repository Creation Utility (RCU) is provided which allows you to create
those schemas.  You must set up the database before you create your domain.
There are no additional requirements added by running SOA in Kubernetes; the
same existing requirements apply.

For development or test environments, you may use the container-based database,
whereas for production environments, it is recommended that you use the standalone database.

The following documentation and samples are for creating a container-based database on a persistent volume.

##### Running the database inside Kubernetes

Follow these instructions for a basic database setup inside Kubernetes that uses PV (persistent volume) and PVC (persistent volume claim) to persist the data. For more details about database setup and configuration, refer this [page](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-fmw-domains/fmw-infra/#running-the-database-inside-kubernetes).

Pull the database image:

```bash
$ docker pull container-registry.oracle.com/database/enterprise:12.2.0.1
$ docker tag  container-registry.oracle.com/database/enterprise:12.2.0.1  oracle/database:12.2.0.1
```
Create the PV and PVC for the database:

```bash
$ mkdir /scratch/DockerVolume/DB
$ chmod 777 /scratch/DockerVolume/DB
```
Update the appropriate host/storage path in `db-pv.yaml`.

Create the PV/PVC using these commands:

```bash
$ cd  weblogic-kubernetes-operator/kubernetes/samples/scripts/create-database-pv-pvc
$ kubectl create -f db-pv.yaml
$ kubectl create -f db-pvc.yaml
```
Bring up the database and database service using the following commands:

```bash
$ cd soa-kubernetes-operator/kubernetes/samples/scripts/create-soa-domain
$ kubectl create -f db-with-pv.yaml
```
Verify that the database service status is Ready.

```bash
$ kubectl get pods,svc -n soans |grep soadb
po/soadb-0   1/1       Running   0          6m

svc/soadb   ClusterIP   None         <none>        1521/TCP,5500/TCP   7m
$
```

#### Running the Repository Creation Utility to set up your database schema

If you want to run RCU from a pod inside the Kubernetes cluster, you can use the Docker
image that you built earlier as a "service" pod to run RCU.  To do this, start up a
pod using that image as follows:

```bash
kubectl run rcu --generator=run-pod/v1 --image oracle/soa:12.2.1.3 -n soans  -- sleep infinity
```

This will create a Kubernetes deployment called `rcu` containing a pod running a container
created from the `oracle/soa:12.2.1.3` image which will just run
`sleep infinity`, which essentially creates a pod that we can "exec" into and use to run whatever
commands we need to run.

To get inside this container and run commands, use this command:

```bash
kubectl exec -n soans -ti rcu /bin/bash
```

When you are finished with this pod, you can remove it with this command:

```bash
kubectl delete pod rcu
```

{{% notice note %}}
You can use the same approach to get a temporary pod to run other utilities
like WLST.
{{% /notice %}}

##### Creating schemas

Inside this pod, you can use the following command to run RCU in command-line (no GUI) mode
to create your schemas required for SOA domains.  You will need to provide the right prefix and connect string.
You will be prompted to enter the password for the `sys` user, and then the password to use
for the regular schema users.

{{% notice note %}}If an ESS application is being deployed to the SOA domain cluster
(for example, for domain types, `soaess` and `soaessosb`), you must append the components
list with `-component ESS` for the `createRepository` and `dropRepository` RCU commands.
{{% /notice %}}

```bash
$export CONNECTION_STRING=soadb:1521/soapdb.my.domain.com
$export RCUPREFIX=SOA1

$echo -e Oradoc_db1"\n"Welcome1 > /tmp/pwd.txt

$/u01/oracle/oracle_common/bin/rcu \
-silent \
-createRepository \
-databaseType ORACLE \
-connectString $CONNECTION_STRING \
-dbUser sys \
-dbRole sysdba   \
-useSamePasswordForAllSchemaUsers true \
-selectDependentsForComponents true \
-variables SOA_PROFILE_TYPE=SMALL,HEALTHCARE_INTEGRATION=NO \
-schemaPrefix $RCUPREFIX  \
-component MDS \
-component IAU \
-component IAU_APPEND \
-component IAU_VIEWER \
-component OPSS \
-component WLS \
-component STB \
-component SOAINFRA < /tmp/pwd.txt
```

You need to make sure that you maintain the association between the database schemas and the
matching domain just like you did in a non-Kubernetes environment.  There is no specific
functionality provided to help with this.  

##### Dropping schemas

If you want to drop the schema, you can use a command like this:

```bash
$/u01/oracle/oracle_common/bin/rcu \
-silent \
-dropRepository \
-databaseType ORACLE \
-connectString $CONNECTION_STRING \
-dbUser sys \
-dbRole sysdba  \
-selectDependentsForComponents true \
-schemaPrefix $RCUPREFIX \
-component MDS \
-component IAU \
-component IAU_APPEND \
-component IAU_VIEWER \
-component OPSS \
-component WLS \
-component STB \
-component SOAINFRA < /tmp/pwd.txt
```

Again, you will need to set the right prefix and connection string, and you will be prompted
to enter the `sys` user password.

#### Create a Kubernetes secret with the RCU credentials

You also need to create a Kubernetes secret containing the credentials for the database schemas.
When you create your domain using the sample provided below, it will obtain the RCU credentials
from this secret.

Follow these steps to create the secret. The schema owner user name required will be the `schemaPrefix` value followed by an underscore and a component name, such as `SOA1_SOAINFRA`. The schema owner password will be the password you provided for regular schema users during RCU creation.

```bash
$ cd  kubernetes/samples/scripts/create-rcu-credentials
$ ./create-rcu-credentials.sh -u SOA1 -p Welcome1 -a sys -q Oradoc_db1 -d soainfra -n soans -s soainfra-rcu-credentials
```
You can check the secret with the `kubectl get secret` command. An example is shown below, including the output:

```bash
$ kubectl get secret soainfra-rcu-credentials -o yaml -n soans
apiVersion: v1
data:
  password: V2VsY29tZTE=
  sys_password: T3JhZG9jX2RiMQ==
  sys_username: c3lz
  username: U09BMQ==
kind: Secret
metadata:
  creationTimestamp: 2019-06-02T07:15:31Z
  labels:
    weblogic.domainName: soainfra
    weblogic.domainUID: soainfra
  name: soainfra-rcu-credentials
  namespace: soans
  resourceVersion: "11562794"
  selfLink: /api/v1/namespaces/soans/secrets/soainfra-rcu-credentials
  uid: 1230385e-6caa-11e9-8143-fa163efa261a
type: Opaque
$
```

#### Creating a SOA domain

Now that you have your Docker images and you have created your RCU schemas, you are ready
to create your domain.  A [sample]({{< relref "/samples/simple/domains/soa-domain/_index.md" >}})
is provided that demonstrates how to create a SOA Suite domain.

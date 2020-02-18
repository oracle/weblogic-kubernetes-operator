---
title: "Manage SOA domains"
date: 2019-04-18T06:46:23-05:00
weight: 2
description: "SOA domains include the deployment of various Oracle Service-Oriented Architecture (SOA) Suite components, such as SOA, Oracle Service Bus (OSB), and Oracle Enterprise Scheduler (ESS)."
---

{{% notice warning %}}
Oracle SOA Suite is currently supported only for non-production use in Docker and Kubernetes.  The information provided
in this document is a *preview* for early adopters who wish to experiment with Oracle SOA Suite in Kubernetes before
it is supported for production use.
{{% /notice %}}


#### Contents

* [Introduction](#introduction)
* [Limitations](#limitations)
* [Obtaining the SOA Suite Docker image](#obtaining-the-soa-suite-docker-image)
* [Creating a SOA Suite Docker image](#creating-a-soa-suite-docker-image)
* [Configuring access to your database](#configuring-access-to-your-database)
* [Running the Repository Creation Utility to set up your database schemas](#running-the-repository-creation-utility-to-set-up-your-database-schemas)
* [Create a Kubernetes secret with the RCU credentials](#create-a-kubernetes-secret-with-the-rcu-credentials)
* [Creating a SOA domain](#creating-a-soa-domain)
* [Monitoring a SOA domain](#monitoring-a-soa-domain)


#### Introduction

The operator supports deployment of SOA Suite components such as Oracle Service-Oriented Architecture (SOA), Oracle Service Bus (OSB), and Oracle Enterprise Scheduler (ESS). Currently the operator supports these different domain types:

* `soa`: Deploys a SOA domain
* `osb`: Deploys an OSB (Oracle Service Bus) domain
* `soaess`: Deploys a SOA domain with Enterprise Scheduler (ESS)
* `soaosb`: Deploys a domain with SOA and OSB
* `soaessosb`: Deploys a domain with SOA, OSB, and ESS

This document provides details about the special considerations for deploying and running SOA Suite domains with the operator.
Other than those considerations listed here, SOA Suite domains work in the same way as FMW Infrastructure domains and WebLogic Server domains.

In this release, SOA Suite domains are supported using the “domain on a persistent volume”
[model]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}) only, where the domain home is located in a persistent volume (PV).


#### Limitations

Compared to running a WebLogic Server domain in Kubernetes using the operator, the
following limitations currently exist for SOA Suite domains:

* The "domain in image" model is not supported.
* Only configured clusters are supported.  Dynamic clusters are not supported for
  SOA Suite domains.  Note that you can still use all of the scaling features,
  you just need to define the maximum size of your cluster at domain creation time.
* Deploying and running SOA Suite domains is supported only in operator versions 2.2.1 and later.
* The [WebLogic Logging Exporter](https://github.com/oracle/weblogic-logging-exporter)
  currently supports WebLogic Server logs only.  Other logs will not be sent to
  Elasticsearch.  Note, however, that you can use a sidecar with a log handling tool
  like Logstash or fluentd to get logs.
* The [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter)
  currently supports the WebLogic MBean trees only.  Support for JRF MBeans has not
  been added yet.


#### Obtaining the SOA Suite Docker Image

The Oracle WebLogic Server Kubernetes Operator requires a SOA Suite 12.2.1.3.0 image with patch 29135930 applied.
The standard pre-built SOA Suite image, `container-registry.oracle.com/middleware/soasuite:12.2.1.3`, already has this patch applied. For detailed instructions on how to log in to the Oracle Container Registry and accept the license agreement, see this [document]({{< relref "/userguide/managing-domains/domain-in-image/base-images/_index.md#obtaining-standard-images-from-the-oracle-container-registry" >}}).

To pull an image from the Oracle Container Registry, in a web browser, navigate to https://container-registry.oracle.com and log in
using the Oracle Single Sign-On authentication service. If you do not already have SSO credentials, at the top of the page, click the Sign In link to create them.  

Use the web interface to accept the Oracle Standard Terms and Restrictions for the Oracle software images that you intend to deploy.
Your acceptance of these terms are stored in a database that links the software images to your Oracle Single Sign-On login credentials.

To obtain the image, log in to the Oracle Container Registry:

```
$ docker login container-registry.oracle.com
```

Then, you can pull the image with this command:

```
$ docker pull container-registry.oracle.com/middleware/soasuite:12.2.1.3
```

Additional information about using this image is available in the
[Oracle Container Registry](https://container-registry.oracle.com).

#### Creating a SOA Suite Docker image

If you prefer, you can also create a Docker image containing the SOA Suite binaries yourself.
A [sample](https://github.com/oracle/docker-images/tree/master/OracleSOASuite)
is provided in the Oracle GitHub account that demonstrates how to create a Docker image
to run SOA Suite.  

Please consult the [README](https://github.com/oracle/docker-images/blob/master/OracleSOASuite/dockerfiles/README.md) file associated with this sample for important prerequisite steps,
such as building or pulling the Server JRE Docker image, Oracle FMW Infrastructure Docker image, and downloading the SOA Suite installer binary.

You must also install the [required patch]({{< relref "/userguide/introduction/introduction/_index.md#prerequisites" >}})
to use this image with the operator.  A [sample](https://github.com/oracle/docker-images/tree/master/OracleFMWInfrastructure/samples/12213-patch-fmw-for-k8s)
is provided that demonstrates how to create a Docker image with the necessary patch installed. Use this patched image for building the SOA Suite image.

Follow these steps to build the necessary images - a patched Fusion Middleware
Infrastructure image, and then the SOA Suite image as a layer on top of that:

* Make a local clone of the sample repsitory.

    ```bash
    $ git clone https://github.com/oracle/docker-images
    ```

* Download the required patch (29135930) from My Oracle Support.
* Create a Docker image containing the Fusion Middleware Infrastructure binaries with
the patch applied by running the provided script:

    ```bash
    $ cd docker-images/OracleFMWInfrastructure/samples/12213-patch-fmw-for-k8s
    $ ./build.sh
    ```

    This will produce an image named `oracle/fmw-infrastructure:12213-update-k8s`.
    The samples and instructions in this document assume you are using the
    pre-built (and already patched) image, `container-registry.oracle.com/middleware/fmw_infrastructure:12.2.1.3`.
    When building your own image, you will need to rename your image, for example from `oracle/fmw-infrastructure:12213-update-k8s`
    to `oracle/fmw-infrastructure:12.2.1.3`, or update the samples to refer to the
    image you created.

    ```
    $ docker tag oracle/fmw-infrastructure:12213-update-k8s oracle/fmw-infrastructure:12.2.1.3
    ```
* Download the Oracle SOA Suite installer from Oracle Technology Network
    or e-delivery.

* Create the SOA Suite image by running the provided script:

    ```bash
    $ cd docker-images/OracleSOASuite/dockerfiles
    $ ./buildDockerImage.sh -v 12.2.1.3 -s
    ```

    The image produced will be named `oracle/soa:12.2.1.3`.  The samples and
    instructions assume the Oracle SOA Suite image is named
    `container-registry.oracle.com/middleware/soasuite:12.2.1.3`.
    You will need to rename your image to match this name,
     or update the samples to refer to the image you created.

    ```bash
    $ docker tag oracle/soa:12.2.1.3 container-registry.oracle.com/middleware/soasuite:12.2.1.3
    ```

    You can use this image to run the Repository Creation Utility and to run your domain using the “domain on a persistent volume” model.

Before creating a domain, you will need to set up the necessary schemas in your database.

#### Configuring access to your database

SOA Suite domains require a database with the necessary schemas installed in them.
The Repository Creation Utility (RCU) allows you to create
those schemas.  You must set up the database before you create your domain.
There are no additional requirements added by running SOA in Kubernetes; the
same existing requirements apply.

For testing and development, you may choose to run your database inside Kubernetes or outside of Kubernetes.

{{% notice warning %}}
The Oracle Database Docker images are supported for non-production use only.
For more details, see My Oracle Support note:
Oracle Support for Database Running on Docker (Doc ID 2216342.1).
{{% /notice %}}

##### Running the database inside Kubernetes

Follow these instructions to perform a basic deployment of the Oracle
database in Kubernetes. For more details about database setup and configuration, refer to this [page]({{< relref "/userguide/managing-fmw-domains/fmw-infra/_index.md#running-the-database-inside-kubernetes" >}}).

When running the Oracle database in Kubernetes, you have an option
to attach persistent volumes (PV) so that the database storage will
be persisted across database restarts.
If you prefer not to persist the database storage, follow the instructions in this
[document](https://github.com/oracle/weblogic-kubernetes-operator/tree/master/kubernetes/samples/scripts/create-rcu-schema#create-the-rcu-schema-in-the-oracle-database)
to set up a database in a container with no persistent volume (PV) attached.

>NOTE: `start-db-service.sh` creates the database in the `default` namespace. If you
>want to create the database in a different namespace, you need to manually update
>the value for all the occurrences of the namespace field in the provided
>sample file `create-rcu-schema/common/oracle.db.yaml`.

These instructions will set up the database in a container with the persistent volume (PV) attached.
If you chose not to use persistent storage, please go to the [RCU creation step](#running-the-repository-creation-utility-to-set-up-your-database-schemas).

* Create the persistent volume and persistent volume claim for the database
using the [create-pv-pvc.sh]({{< relref "/samples/simple/storage/_index.md" >}}) sample.
Refer to the instructions provided in that sample.

{{% notice note %}}
When creating the PV and PVC for the database, make sure that you use a different name
and storage class for the PV and PVC for the domain.
The name is set using the value of the `baseName` field in `create-pv-pvc-inputs.yaml`.
{{% /notice %}}

* Start the database and database service using the following commands:

>NOTE: Make sure you update the `kubernetes/samples/scripts/create-soa-domain/domain-home-on-pv/create-database/db-with-pv.yaml`
>file with the name of the PVC created in the previous step. Also update the value for all the occurrences of the namespace field
>to the namespace where the database PVC was created in the previous step.

    ```bash
    $ cd weblogic-kubernetes-operator/kubernetes/samples/scripts/create-soa-domain/domain-home-on-pv/create-database
    $ kubectl create -f db-with-pv.yaml
    ```

The database will take several minutes to start the first time, while it
performs setup operations.  You can watch the log to see its progress using
this command:

```bash
$ kubectl logs -f oracle-db -n soans
```

A log message will indicate when the database is ready.  Also, you can
verify the database service status using this command:

```bash
$ kubectl get pods,svc -n soans |grep oracle-db
po/oracle-db   1/1       Running   0          6m
svc/oracle-db   ClusterIP   None         <none>        1521/TCP,5500/TCP   7m
```

#### Running the Repository Creation Utility to set up your database schemas

##### Creating schemas

To create the database schemas for Oracle SOA Suite, run the `create-rcu-schema.sh` script as described
[here](https://github.com/oracle/weblogic-kubernetes-operator/tree/master/kubernetes/samples/scripts/create-rcu-schema#create-the-rcu-schema-in-the-oracle-database).

The following example shows commands you might use to execute `create-rcu-schema.sh`:

```bash
$ cd weblogic-kubernetes-operator/kubernetes/samples/scripts/create-rcu-schema
$ ./create-rcu-schema.sh \
  -s SOA1 \
  -t soaessosb \
  -d oracle-db.soans.svc.cluster.local:1521/devpdb.k8s \
  -i container-registry.oracle.com/middleware/soasuite:12.2.1.3
```

For SOA domains, the `create-rcu-schema.sh` script supports the following domain types `soa,osb,soaosb,soaess,soaessosb`.
You must specify one of these using the `-t` flag.

You need to make sure that you maintain the association between the database schemas and the
matching domain just like you did in a non-Kubernetes environment.  There is no specific
functionality provided to help with this.

##### Dropping schemas

If you want to drop the schema, you can use the `drop-rcu-schema.sh` script as described
[here](https://github.com/oracle/weblogic-kubernetes-operator/tree/master/kubernetes/samples/scripts/create-rcu-schema#drop-the-rcu-schema-from-the-oracle-database).

The following example shows commands you might use to execute `drop-rcu-schema.sh`:

```bash
$ cd weblogic-kubernetes-operator/kubernetes/samples/scripts/create-rcu-schema
$ ./drop-rcu-schema.sh \
  -s SOA1 \
  -t soaessosb \
  -d oracle-db.soans.svc.cluster.local:1521/devpdb.k8s
```

For SOA domains, the `drop-rcu-schema.sh` script supports the domain types `soa,osb,soaosb,soaess,soaessosb`.
You must specify one of these using the `-t` flag.

#### Create a Kubernetes secret with the RCU credentials

You also need to create a Kubernetes secret containing the credentials for the database schemas.
When you create your domain using the sample provided below, it will obtain the RCU credentials
from this secret.

Use the provided sample script to create the secret as shown below:

```bash
$ cd kubernetes/samples/scripts/create-rcu-credentials
$ ./create-rcu-credentials.sh \
  -u SOA1_SOAINFRA \
  -p Welcome1 \
  -a sys \
  -q Oradoc_db1 \
  -d soainfra \
  -n soans \
  -s soainfra-rcu-credentials
```

The parameter values are as follows:

* The schema owner user name (`-u`) must be the `schemaPrefix`
value followed by an underscore and a component name, for example `SOA1_SOAINFRA`.
* The schema owner password (`-p`) will be the password you provided for regular schema users during RCU creation.
* The database administration user (`-u`) and password (`-q`).
* The `domainUID` for the domain (`-d`).
* The namespace the domain is in (`-n`), if omitted, then `default` is assumed.
* The name of the secret (`-s`).

You can confirm the secret was created as expected with the `kubectl get secret` command.
An example is shown below, including the output:

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
```

#### Creating a SOA domain

Now that you have your Docker images and you have created your RCU schemas, you are ready
to create your domain.  To continue, follow the instructions in the [SOA Domain sample]({{< relref "/samples/simple/domains/soa-domain/_index.md" >}}).

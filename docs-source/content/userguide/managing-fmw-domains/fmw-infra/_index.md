---
title: "Manage FMW Infrastructure domains"
date: 2019-04-18T06:46:23-05:00
weight: 1
description: "FMW Infrastructure domains contain the Java Required Files (JRF) feature and are
the prerequisite for upper stack products like Oracle SOA Suite."
---

#### Contents

* [Limitations](#limitations)
* [Obtaining the FMW Infrastructure Docker image](#obtaining-the-fmw-infrastructure-docker-image)
* [Creating an FMW Infrastructure Docker image](#creating-an-fmw-infrastructure-docker-image)
* [Configuring access to your database](#configuring-access-to-your-database)
* [Running the Repository Creation Utility to set up your database schema](#running-the-repository-creation-utility-to-set-up-your-database-schema)
* [Create a Kubernetes Secret with the RCU credentials](#create-a-kubernetes-secret-with-the-rcu-credentials)
* [Creating an FMW Infrastructure domain](#creating-an-fmw-infrastructure-domain)
* [Patching the FMW Infrastructure image](#patching-the-fmw-infrastructure-image)
* [Additional considerations for Coherence](#additional-considerations-for-coherence)
* [Additional considerations for Model in Image](#additional-considerations-for-model-in-image)


Starting with the 2.2.0 release, the operator supports FMW Infrastructure domains, that is,
domains that are created with the FMW Infrastructure installer rather than the WebLogic
Server installer.  These domains contain the Java Required Files (JRF) feature and are
the prerequisite for "upper stack" products like Oracle SOA Suite, for example.
These domains also require a database and the use of the Repository
Creation Utility (RCU).

This document provides details about the special considerations for running
FMW Infrastructure domains with the operator.  Other than those considerations
listed here, FMW Infrastructure domains work in the same way as WebLogic Server domains.
The remainder of the documentation in this site applies equally to FMW
Infrastructure domains and WebLogic Server domains.

FMW Infrastructure domains are supported using the Domain in PV,
Domain in Image, or Model in Image [domain home source types]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).
If you plan to experiment with upper stack products (which are not officially supported
by the operator yet), we strongly recommend using the domain on a persistent
volume approach.

#### Limitations

Compared to running a WebLogic Server domain in Kubernetes using the operator, the
following limitations currently exist for FMW Infrastructure domains:

* The [WebLogic Logging Exporter](https://github.com/oracle/weblogic-logging-exporter)
  currently supports WebLogic Server logs only.  Other logs will not be sent to
  Elasticsearch.  Note, however, that you can use a sidecar with a log handling tool
  like Logstash or Fluentd to get logs.
* The [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter)
  currently supports the WebLogic MBean trees only.  Support for JRF MBeans has not
  been added yet.
* Only configured clusters are supported.  Dynamic clusters are not supported for
  FMW Infrastructure domains.  Note that you can still use all of the scaling features,
  you just need to define the maximum size of your cluster at domain creation time.
* FMW Infrastructure domains are not supported with any version of the operator
  before version 2.2.0.


#### Obtaining the FMW Infrastructure Docker Image

The Oracle WebLogic Server Kubernetes Operator requires patch 29135930.
The standard pre-built FMW Infrastructure image, `container-registry.oracle.com/middleware/fmw-infrastrucutre:12.2.1.3`, already has this patch applied. For detailed instructions on how to log in to the Oracle Container Registry and accept license agreement, see this [document]({{< relref "/userguide/managing-domains/domain-in-image/base-images/_index.md#obtaining-standard-images-from-the-oracle-container-registry" >}}).
The FMW Infrastructure 12.2.1.4.0 images do not require patches.

To pull an image from the Oracle Container Registry, in a web browser, navigate to https://container-registry.oracle.com and log in
using the Oracle Single Sign-On authentication service. If you do not already have SSO credentials, at the top of the page, click the Sign In link to create them.  

Use the web interface to accept the Oracle Standard Terms and Restrictions for the Oracle software images that you intend to deploy.
Your acceptance of these terms is stored in a database that links the software images to your Oracle Single Sign-On login credentials.


First, you will need to log in to the Oracle Container Registry:

```
$ docker login container-registry.oracle.com
```

Then, you can pull the image with this command:

```
$ docker pull container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4
```
If desired, you can:

* Check the WLS version with `docker run container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4 sh -c` `'source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh > /dev/null 2>&1 && java weblogic.version'`

* Check the WLS patches with `docker run container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4 sh -c` `'$ORACLE_HOME/OPatch/opatch lspatches'`

Additional information about using this image is available in the
[Oracle Container Registry](https://container-registry.oracle.com).


#### Creating an FMW Infrastructure Docker image

You can also create a Docker image containing the FMW Infrastructure binaries.
We provide a [sample](https://github.com/oracle/docker-images/tree/master/OracleFMWInfrastructure)
 in the Oracle GitHub account that demonstrates how to create a Docker image
to run the FMW Infrastructure. Please consult the [README](https://github.com/oracle/docker-images/blob/master/OracleFMWInfrastructure/dockerfiles/12.2.1.4/README.md)
file associated with this sample for important prerequisite steps,
such as building or pulling the Server JRE Docker image and downloading the Fusion Middleware
Infrastructure installer binary.

After cloning the repository and downloading the installer from Oracle Technology Network
or e-delivery, you create your image by running the provided script:

```bash
cd docker-images/OracleFMWInfrastructure/dockerfiles
./buildDockerImage.sh -v 12.2.1.4 -s
```

The image produced will be named `oracle/fmw-infrastructure:12.2.1.4`.

You must also install the [required patch]({{< relref "/userguide/introduction/introduction/_index.md#prerequisites" >}})
to use this image with the operator.  We provide a [sample](https://github.com/oracle/docker-images/tree/master/OracleFMWInfrastructure/samples/12213-patch-fmw-for-k8s)
 that demonstrates how to create a Docker image with the necessary patch installed.

After downloading the patch from My Oracle Support, you create the patched image
by running the provided script:

```bash
cd docker-images/OracleFMWInfrastructure/samples/12213-patch-fmw-for-k8s
./build.sh
```

This will produce an image named `oracle/fmw-infrastructure:12213-update-k8s`.

All samples and instructions reference the pre-built image, `container-registry.oracle.com/middleware/fmw_infrastructure:12.2.1.4`. Because these samples build an image based on WebLogic Server 12.2.1.3 and use the tag, `oracle/fmw-infrastructure:12213-update-k8s`, be sure to update your sample inputs to use this `image` value.

These samples allow you to create a Docker image containing the FMW Infrastructure
binaries and the necessary patch.  You can use this image to run the Repository Creation Utility
and to run your domain using the "domain on a persistent volume" model. If you want to use
the "domain in a Docker image" model, you will need to go one step further and add another
layer with your domain in it.  You can use WLST or WDT to create your domain.

Before creating a domain, you will need to set up the necessary schemas in your database.

#### Configuring access to your database

FMW Infrastructure domains require a database with the necessary schemas installed in them.
We provide a utility, called the Repository Creation Utility (RCU), which allows you to create
those schemas.  You must set up the database before you create your domain.
There are no additional requirements added by running FMW Infrastructure in Kubernetes; the
same existing requirements apply.

For testing and development, you may choose to run your database inside Kubernetes or outside of Kubernetes.

{{% notice warning %}}
The Oracle Database Docker images are only supported for non-production use.
For more details, see My Oracle Support note:
Oracle Support for Database Running on Docker (Doc ID 2216342.1)
{{% /notice %}}

##### Running the database inside Kubernetes

If you wish to run the database inside Kubernetes, you can use the official Docker image
[from Docker Hub](https://hub.docker.com/_/oracle-database-enterprise-edition) or
the [Oracle Container Registry](https://container-registry.oracle.com/pls/apex/f?p=113:1:10859199204803::NO:1:P1_BUSINESS_AREA:3).
Please note that there is a Slim Variant (`12.2.0.1-slim` tag) of EE that has reduced
disk space (4GB) requirements and a quicker container startup.

Running the database inside the Kubernetes cluster is possibly more relevant or
desirable in test/development or CI/CD scenarios.

Here is an example of a Kubernetes YAML file to define a deployment of the Oracle
database:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: oracle-db
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/instance: dev
      app.kubernetes.io/name: oracle-db
  strategy:
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 1
    type: RollingUpdate
  template:
    metadata:
      creationTimestamp: null
      labels:
        app.kubernetes.io/instance: dev
        app.kubernetes.io/name: oracle-db
    spec:
      containers:
      - env:
        - name: DB_SID
          value: devcdb
        - name: DB_PDB
          value: devpdb
        - name: DB_DOMAIN
          value: k8s
        image: container-registry.oracle.com/database/enterprise:12.2.0.1-slim
        imagePullPolicy: IfNotPresent
        name: oracle-db
        ports:
        - containerPort: 1521
          name: tns
          protocol: TCP
        resources:
          limits:
            cpu: "1"
            memory: 2Gi
          requests:
            cpu: 200m
        terminationMessagePath: /dev/termination-log
        terminationMessagePolicy: File
      dnsPolicy: ClusterFirst
      restartPolicy: Always
      schedulerName: default-scheduler
      securityContext: {}
      terminationGracePeriodSeconds: 30
```

Notice that you can pass in environment variables to set the SID, the name of the PDB, and
so on.  The documentation describes the other variables that are available.  The `sys` password
defaults to `Oradoc_db1`.  Follow the instructions in the documentation to reset this password.

You should also create a service to make the database available within the Kubernetes cluster with
a well known name.  Here is an example:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: oracle-db
  namespace: default
spec:
  ports:
  - name: tns
    port: 1521
    protocol: TCP
    targetPort: 1521
  selector:
    app.kubernetes.io/instance: dev
    app.kubernetes.io/name: oracle-db
  sessionAffinity: None
  type: ClusterIP
```

In the example above, the database would be visible in the cluster using the address
`oracle-db.default.svc.cluster.local:1521/devpdb.k8s`.

When you run the database in the Kubernetes cluster, you will probably want to also
run RCU from a pod inside your network, though this
is not strictly necessary.  You could create a `NodePort` to expose your database outside
the Kubernetes cluster and run RCU on another machine with access to the cluster.


##### Running the database outside Kubernetes

If you wish to run the database outside Kubernetes, you need to create a way for containers
running in pods in Kubernetes to see the database.  This can be done by defining a
Kubernetes Service with no selector and associating it with an endpoint definition, as shown
in the example below:

```yaml
kind: Service
apiVersion: v1
metadata:
 name: database
spec:
 type: ClusterIP
 ports:
 - port: 1521
   targetPort: 1521
---
kind: Endpoints
apiVersion: v1
metadata:
 name: database
subsets:
 - addresses:
     - ip: 129.123.1.4
   ports:
     - port: 1521
```

This creates a DNS name `database` in the current namespace, or`default` if no namespace is
specified, as in the example above. In this example, the fully qualified name would be
`database.default.svc.cluster.local`.  The second part is the namespace.
If you looked up the `ClusterIP` for such a service, it would have an IP address on the overlay
network, that is the network inside the Kubernetes cluster.  If you are using flannel,
for example, the address might be something like `10.0.1.25`.  Note that this is usually a
non-routed address.

From a container in a pod running in Kubernetes, you can make a connection to that address
and port `1521`.  Kubernetes will route the connection to the address provided in the
endpoint definition, in this example, `129.123.1.4:1521`.  This IP address (or name) is
resolved from the point of view of the Kubernetes Node's IP stack, not the overlay network
inside the Kubernetes cluster.  Note that this is a "real" routed IP address.

When you create your data sources, you would use the internal address, for example,
`database:1521/some.service`.

Because your database is externally accessible, you can run RCU in the normal way, from any
machine on your network.

#### Running the Repository Creation Utility to set up your database schema

If you want to run RCU from a pod inside the Kubernetes cluster, you can use the Docker
image that you built earlier as a "service" pod to run RCU.  To do this, start up a
pod using that image as follows:

```bash
kubectl run rcu --generator=run-pod/v1 --image container-registry.oracle.com/middleware/fmw_infrastructure:12.2.1.4 -- sleep infinity

```

This will create a Kubernetes Deployment called `rcu` containing a pod running a container
created from the `container-registry.oracle.com/middleware/fmw_infrastructure:12.2.1.4` image which will just run
`sleep infinity`, which essentially creates a pod that we can "exec" into and use to run whatever
commands we need to run.

To get inside this container and run commands, use this command:

```bash
kubectl exec -ti rcu /bin/bash
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
to create your FMW schemas.  You will need to provide the right prefix and connect string.
You will be prompted to enter the password for the `sys` user, and then the password to use
for the regular schema users:

```bash
/u01/oracle/oracle_common/bin/rcu \
  -silent \
  -createRepository \
  -databaseType ORACLE \
  -connectString oracle-db.default:1521/devpdb.k8s \
  -dbUser sys \
  -dbRole sysdba \
  -useSamePasswordForAllSchemaUsers true \
  -selectDependentsForComponents true \
  -schemaPrefix FMW1 \
  -component MDS \
  -component IAU \
  -component IAU_APPEND \
  -component IAU_VIEWER \
  -component OPSS  \
  -component WLS  \
  -component STB
```

You need to make sure that you maintain the association between the database schemas and the
matching domain just like you did in a non-Kubernetes environment.  There is no specific
functionality provided to help with this.  We recommend that you consider making the RCU
prefix (value of the `schemaPrefix` argument) the same as your `domainUID` to help maintain this association.

##### Dropping schemas

If you want to drop the schema, you can use a command like this:

```bash
/u01/oracle/oracle_common/bin/rcu \
  -silent \
  -dropRepository \
  -databaseType ORACLE \
  -connectString oracle-db.default:1521/devpdb.k8s \
  -dbUser sys \
  -dbRole sysdba \
  -selectDependentsForComponents true \
  -schemaPrefix FMW1 \
  -component MDS \
  -component IAU \
  -component IAU_APPEND \
  -component IAU_VIEWER \
  -component OPSS \
  -component WLS \
  -component STB
```

Again, you will need to set the right prefix and connection string, and you will be prompted
to enter the `sys` user password.

#### Create a Kubernetes Secret with the RCU credentials

You also need to create a Kubernetes Secret containing the credentials for the database schemas.
When you create your domain using the sample provided below, it will obtain the RCU credentials
from this secret.

We provide a [sample](https://github.com/oracle/weblogic-kubernetes-operator/tree/master/kubernetes/samples/scripts/create-rcu-credentials/README.md)
that demonstrates how to create the secret.  The schema owner user name required will be the
`schemaPrefix` value followed by an underscore and a component name, such as `FMW1_STB`.  The schema owner
password will be the password you provided for regular schema users during RCU creation.

#### Creating an FMW Infrastructure domain

Now that you have your Docker images and you have created your RCU schemas, you are ready
to create your domain.  We provide a [sample]({{< relref "/samples/simple/domains/fmw-domain/_index.md" >}})
that demonstrates how to create an FMW Infrastructure domain.

#### Patching the FMW Infrastructure image

There are two kinds of patches that can be applied to the FMW Infrastructure binaries:

* Patches which are eligible for Zero Downtime Patching (ZDP), meaning that
  they can be applied with a rolling restart.
* Non-ZDP eligible compliant patches, meaning that the domain must be shut down
  and restarted.

You can find out whether or not a patch is eligible for Zero Downtime Patching
by consulting the README file that accompanies the patch.

If you wish to apply a ZDP compliant patch which can be applied with a rolling
restart, after you have patched the FMW Infrastructure image as shown in this
[sample](https://github.com/oracle/docker-images/tree/master/OracleFMWInfrastructure/samples/12213-patch),
you can edit the domain custom resource with the name of the new image and
the operator will initiate a rolling restart of the domain.

If you wish to apply a non-ZDP compliant patch to the FMW Infrastructure binary image,
you must shut down the entire domain before applying the patch. Please see the documentation on
[domain lifecycle operations]({{< relref "/userguide/managing-domains/domain-lifecycle/_index.md" >}})
for more information.

An example of a non-ZDP compliant patch is one that includes a schema change
that can not be applied dynamically.

#### Additional considerations for Coherence

If you are running a domain which contains Coherence, please refer to
[Coherence requirements]({{< relref "/faq/coherence-requirements.md" >}})
for more information.

#### Additional considerations for Model in Image

If you are using Model in Image, then see the [Model in Image sample]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}}), which demonstrates a JRF model and its RCU schema setup, and see [Model in Image requirements for JRF domain types]({{< relref "/userguide/managing-domains/model-in-image/usage/_index.md#requirements-for-jrf-domain-types" >}}).

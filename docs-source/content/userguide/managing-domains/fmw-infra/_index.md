+++
title = "FMW domains"
date = 2019-04-18T06:46:23-05:00
weight = 5
pre = "<b> </b>"
+++

Starting with release 2.2, the operator supports FMW Infrastructure domains.
This means domains that are created with the FMW Infrastructure installer rather than the WebLogic
installer.  These domains contain the Java Required Files (JRF) feature, and are 
the pre-requisite for "upper stack" products like Oracle SOA Suite for example.
These domains also require a database and the use of the Repository
Creation Utility (RCU). 

This section provides details about the special considerations for running
FMW Infrastructure domains with the operator.  Other than those considerations
listed here, FMW Infrastructure domains work in the same way as WebLogic domains.
That is, the remainder of the documentation in this site applies equally to FMW 
Infrastructure domains and WebLogic domains. 

FMW Infrastructure domains are supported using both the "domain on a persistent volume" 
and the "domain in a Docker image" [models]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).
If you plan to experiment with upper stack products (which are not officially supported
by the operator yet) we strongly recommend using the domain on a persistent
volume approach. 

#### Limitations

Compared to running a WebLogic Server domain in Kubernetes using the operator, the
following limitations currently exist for FMW Infrastructure domains: 

* The [WebLogic Logging Exporter](https://github.com/oracle/weblogic-logging-exporter)
  currently only supports WebLogic server logs.  Other logs will not be sent to
  Elasticsearch.  Note however that you can use a sidecar with a log handling tool
  like Logstash or fluentd to get logs. 
* The [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter)
  currently only supports the WebLogic MBean trees.  Support for JRF MBeans has not
  been added yet.
* Only configured clusters are supported.  Dynamic clusters are not supported for
  FMW Infrastructure domains.  Note that you can still use all of the scaling features,
  you just need to define the maximum size of your cluster at domain creation time.
* FMW Infrastructure domains are not supported with any version of the operator
  before version 2.2.

#### Creating a FMW Infrastructure Docker image

You will need to create a Docker image containing the FMW Infrastructure binaries.
A [sample](https://github.com/oracle/docker-images/tree/master/OracleFMWInfrastructure)
is provided in the Oracle GitHub account that demonstrates how to create a Docker image
to run FMW Infrastructure.  

After cloning the repository and downloading the installer from Oracle Technology Network
or e-delivery, you would create your image by running the provided script:

```bash
cd docker-images/OracleFMWInfrastructure/dockerfiles
./buildDockerImage.sh -v 12.2.1.3 -g 
```

The image produced will be named `oracle/fmw-infrastructure:12.2.1.3`.

You must also install the [required patch]({{< relref "/userguide/introduction/introduction/_index.md#prerequisites" >}})
to use this image with the operator.  A [sample](https://github.com/oracle/docker-images/tree/master/OracleFMWInfrastructure/samples/12213-patch-fmw-for-k8s)
is provided that demonstrates how to create a Docker image with the necessary patch installed.

After downloading the patch from My Oracle Support, you can create the patched image
by running the provided script:

TODO TODO TODO check with monica's sample to ensure this is correct TODO TODO TODO

```bash
cd docker-images/OracleFMWInfrastructure/samples/12213-patch-fmw-for-k8s
./buildDockerImage.sh 
```

This will produce an image named `TODO TODO TODO monica what is it called TODO TODO TODO`.

These sample will allow you to create a Docker image containing the FMW Infrastructure
binaries and the necessary patch.  You can use this image to run the Repository Creation Utility
and to run your domain using the "domain on a persistent volume" model. If you want to use 
the "domain in a Docker image" model, you will need to go one step further and add another
layer with your domain in it.  You can use WLST or WDT to create your domain.

Before creating a domain you will need to set up the necessary schemas in your database. 

#### Configuring access to your database

FMW Infrastructure domains require a database with the necessary schemas installed in them.
A utility called Repository Creation Utility (RCU) is provided which allows you to create
those schemas.  You must set up the database before you create your domain.
There are no additional requirements added by running FMW Infrastructure in Kubernetes, the
same existing requirements apply.

You may choose to run your database inside Kubernetes or outside of Kubernetes. 

##### Running the database inside Kubernetes

If you wish to run the database inside Kubernetes, you can use the official Docker image
[from Docker Hub](https://hub.docker.com/_/oracle-database-enterprise-edition) or
[Oracle Container Registry](https://container-registry.oracle.com/pls/apex/f?p=113:1:10859199204803::NO:1:P1_BUSINESS_AREA:3).
Please note that there is a Slim Variant (`12.2.0.1-slim` tag) of EE that has reduced
disk space (4GB) requirements and a quicker container startup.

Running the database inside the Kubernetes cluster is possibly more relevant or
desirable in test/development or CI/CD scenarios.

Here is an example of a Kubernetes YAML file to define a deployment of the Oracle
database:

```yaml
apiVersion: extensions/v1beta1
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

Notice that you can pass in environment variables to set the SID, the name of the PDB and
so on.  The documentation describes the other variables that are available.  You should
also create a service to make the database available within the Kubernetes cluster with
a well known name.  Here is an example: 

```yaml
apiVersion: v1
kind: Service
metadata:
  name: oracle-db
  namespace: default
spec:
  clusterIP: 10.97.236.215
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
`oracle-db.default.svc.cluster.local:1521/devpdc.k8s`.

When you run the database in the Kubernetes cluster, you will probably want to also
run RCU from a pod inside your network, though this
is not strictly necessary.  You could create a `NodePort` to expose your database outside
the Kubernetes cluster and run RCU on another machine with access to the cluster.


##### Running the database outside Kubernetes

If you wish to run the database outside Kubernetes, you need to create a way for containers
running in pods in Kubernetes to see the database.  This can be done by defining a
Kubernetes service with no selector and associating it with an endpoint definition, as shown
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
specified, as in the example above.  The fully qualified name would be
`database.default.svc.cluster.local` in this example.  The second part is the namespace.
If you looked up the `ClusterIP` for such a service, it would have an IP address on the overlay
network, that is the network inside the Kubernetes cluster.  If you are using flannel
for example, the address might be something like `10.0.1.25`.  Note that this is usually a
non-routed address.

From a container in a pod running in Kubernetes, you can make a connection to that address
and port `1521`.  Kubernetes will route the connection to the address provided in the
endpoint definition, in this example `129.123.1.4:1521`.  This IP address (or name) is
resolved from the point of view of the Kubernetes node's IP stack, not the overlay network
inside the Kubernetes cluster.  Note that this is a "real" routed IP address. 

When you create your data sources, you would use the internal address, for example
`database:1521/some.service`.

Because your database is externally accessible, you can run RCU in the normal way, from any
machine on your network.

#### Running the Repository Creation Utility to set up your database schema

If you want to run RCU from a pod inside the Kubernetes cluster, you can use the Docker
image that you built earlier as a "service" pod to run RCU.  To do this, start up a
pod using that image as follows: 

```bash
kubectl run rcu -ti --image oracle/fmw-infrastructure:12.2.1.3 -- sleep 100000

```

This will create a Kubernetes deployment called `rcu` containing a pod running a container
created from the `oracle/fmw-infrastructure:12.2.1.3` image which will just run
`sleep 100000`, which essentially creates a pod that we can "exec" into and use to run whatever
commands we need to run. 

To get inside this container and run commands, use this command: 

```bash
kubectl exec -ti rcu /bin/bash
```

When you are finished with this pod, you can remove it with this command: 

```bash
kubectl delete deploy rcu 
```

{{% notice note %}}
You can use the same approach to get a temporary "service" pod to run other utilities
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
  -connectString oracle-db:1521/devpdb.k8s \
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
prefix the same as your `domainUID` to help maintain this association. 

##### Dropping schemas

If you want to drop the schema, you can use a command like this:

```bash
/u01/oracle/oracle_common/bin/rcu \
  -silent \
  -dropRepository \
  -databaseType ORACLE \
  -connectString oracle-db:1521/devpdb.k8s \
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

#### Create a Kubernetes secret with the RCU credentials

You also need to create a Kubernetes secret containing the credentials for the database schemas.
When you create your domain using the sample provided below, it will obtain the RCU credentials
from this secret. 

A [sample](/weblogic-kubernetes-operator/blob/master/kubernetes/samples/scripts/create-rcu-credentials/README.md)
is provided that demonstrates how to create the secret. 


#### Creating a FMW Infrastructure domain

Now that you have your Docker images and you have created your RCU schemas, you are ready
to create your domain.  A [sample]({{< relref "/samples/simple/domains/fmw-domain/_index.md" >}})
is provided that demonstrates how to create a FMW Infrastructure domain. 


---
title: "Run a database"
date: 2019-02-23T16:43:10-05:00
weight: 2
description: "Run an ephemeral database in Kubernetes that is suitable for sample or basic testing purposes."

---

{{< table_of_contents >}}

### Overview

This section describes how to run an
ephemeral [Oracle database](#oracle-database-in-kubernetes)
or [MySQL database](#mysql-database-in-kubernetes)
in your Kubernetes cluster
using approaches suitable for sample or basic testing purposes.

**NOTES**:

- The databases are configured with ephemeral storage, which means all
  information will be lost on any shutdown or pod failure.

- The Oracle Database images are supported for non-production use _only_.
  For more details, see My Oracle Support note: Oracle Support for Database Running on Docker [Doc ID 2216342.1](https://support.oracle.com/epmos/faces/DocumentDisplay?_afrLoop=208317433106215&id=2216342.1&_afrWindowMode=0&_adf.ctrl-state=c2nhai8p3_4).

### Oracle database in Kubernetes

The following example shows how to set up an ephemeral Oracle database with the following attributes:

| Attribute | Value |
| --------- | ----- |
| Kubernetes namespace | `default` |
| Kubernetes pod | `oracle-db` |
| Kubernetes service name | `oracle-db` |
| Kubernetes service port | `1521` |
| Kubernetes node port | `30011` |
| Image | `container-registry.oracle.com/database/enterprise:12.2.0.1-slim` |
| DBA user (with full privileges) | `sys as sysdba` |
| DBA password | `<password placeholder>` |
| Database Domain (not the same as a WebLogic Domain) | k8s |
| Database PDB | devpdb |
| Database URL inside Kubernetes cluster (from any namespace) | `oracle-db.default.svc.cluster.local:1521/devpdb.k8s` |
| Database URL outside Kubernetes cluster | `dns-name-that-resolves-to-node-location:30011/devpdb.k8s` |

1. Get the operator source and put it in `/tmp/weblogic-kubernetes-operator`.

   For example:

   ```shell
   $ cd /tmp
   ```
   ```shell
   $ git clone --branch v{{< latestVersion >}} https://github.com/oracle/weblogic-kubernetes-operator.git
   ```

   **NOTE**: We will refer to the top directory of the operator source tree as `/tmp/weblogic-kubernetes-operator`; however, you can use a different location.

   For additional information about obtaining the operator source, see the [Developer Guide Requirements](https://oracle.github.io/weblogic-kubernetes-operator/developerguide/requirements#obtaining-the-operator-source-code).

1. Ensure that you have access to the database image:

   - Use a browser to log in to `https://container-registry.oracle.com`, select `Database -> enterprise`, and accept the license agreement.

   - Get the database image:
     - In the local shell, `docker login container-registry.oracle.com`.
     - In the local shell, `docker pull container-registry.oracle.com/database/enterprise:12.2.0.1-slim`.

   - If your Kubernetes cluster nodes do not all have access to the database image in a local cache, then:
     - Deploy a Kubernetes `docker secret` to the default namespace with login credentials for `container-registry.oracle.com`:
       ```shell
       kubectl create secret docker-registry docker-secret \
               --docker-server=container-registry.oracle.com \
               --docker-username=your.email@some.com \
               --docker-password=your-password \
               --docker-email=your.email@some.com \
               -n default
       ```
       Pass the name of this secret as a parameter to the `start-db-service.sh`
       in the following step using `-s your-image-pull-secret`.
     - Alternatively, copy the database image to each local Docker cache in the cluster.
     - For more information, see the FAQ, [Cannot pull image]({{<relref "/faq/cannot-pull-image">}}).


      **WARNING**: The Oracle Database images are supported only for non-production use. For more details, see My Oracle Support note: Oracle Support for Database Running on Docker [Doc ID 2216342.1](https://support.oracle.com/epmos/faces/DocumentDisplay?_afrLoop=208317433106215&id=2216342.1&_afrWindowMode=0&_adf.ctrl-state=c2nhai8p3_4).

1. Create a secret named `oracle-db-secret` in the default namespace
   with your desired Oracle SYS DBA password in its `password` key.

   - For example:
     ```
     $ kubectl -n default create secret generic oracle-db-secret \
       --from-literal='password=<password placeholder>'
     ```
     (Replace `<password placeholder>` with your desired password.)
   - Oracle Database passwords can contain upper case, lower case, digits, and special characters.
     Use only "_" and "#" as special characters to eliminate potential parsing errors
     for Oracle Database connection strings.

1. Create a deployment using the database image:

   Use the sample script in `/tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-oracle-db-service`
   to create an Oracle database running in the deployment, `oracle-db`.

   ```shell
   $ cd /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-oracle-db-service
   ```
   ```shell
   $ start-db-service.sh
   ```

   Notes:
   - Call `start-db-service.sh -h` to see how to customize the namespace, node port, secret name, etc.
   - Call `stop-db-service.sh` to shutdown and cleanup the `oracle-db` deployment.
   - To troubleshoot, use the `kubectl describe pod DB_POD_NAME` and `kubectl logs DB_POD_NAME` commands on the database pod.

### MySQL database in Kubernetes

The following example shows how to set up an ephemeral MySQL database with the following attributes:

| Attribute | Value |
| --------- | ----- |
| Kubernetes namespace | `default` |
| Kubernetes pod | `mysql-db` |
| Kubernetes service name | `mysql-db` |
| Kubernetes service port | `3306` |
| Image | `mysql:5.6` |
| Root user (with full privileges) | `<user name placeholder>` |
| Root password | `<password placeholder>` |
| Database URL inside Kubernetes cluster (from any namespace) | `jdbc:mysql://mysql-db.default.svc.cluster.local:3306/mysql` |

Copy the following YAML into a file named `mysql.yaml`:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: mysql-db
  namespace: default
  labels:
    app: mysql-db
spec:
  terminationGracePeriodSeconds: 5
  containers:
  - image: mysql:5.6
    name: mysql
    env:
    - name: MYSQL_ROOT_USER
      valueFrom:
        secretKeyRef:
          name: mysql-secret
          key: root-user
    - name: MYSQL_ROOT_PASSWORD
      valueFrom:
        secretKeyRef:
          name: mysql-secret
          key: root-password
    ports:
    - containerPort: 3306
      name: mysql
---
apiVersion: v1
kind: Service
metadata:
  name: mysql-db
  namespace: default
spec:
  ports:
  - port: 3306
  selector:
    app: mysql-db
  clusterIP: None
---
apiVersion: v1
kind: Secret
metadata:
  name: mysql-secret
  namespace: default
data:
  root-user: <user name placeholder>
  root-password: <password placeholder>
```

In file `mysql.yaml`, replace `<user name placeholder>` and `<password placeholder>`, respectively, with the output from piping the
root user name and password through base64:
```
echo -n <user name placeholder> | base64
echo -n <password placeholder> | base64
```
Deploy MySQL using the command `kubectl create -f mysql.yaml`.

To shut down and clean up the resources, use `kubectl delete -f mysql.yaml`.

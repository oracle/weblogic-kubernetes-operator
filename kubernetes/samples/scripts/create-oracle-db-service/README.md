# Managing Oracle Database Service for Fusion Middleware domain

The sample scripts in this directory demonstrate how to:
* Start an Oracle Database (DB) service in a Kubernetes cluster.
* Stop an Oracle DB service in a Kubernetes cluster.

## Start an Oracle Database service in a Kubernetes cluster

Use the `start-db-service.sh` script to create an Oracle Database service in a Kubernetes Namespace with the Oracle Database Slim image.

By default, the script assumes that either the image, `container-registry.oracle.com/database/enterprise:12.2.0.1-slim`, is available in the Docker repository, or an `ImagePullSecret` is created for `container-registry.oracle.com`. To create a secret for accessing `container-registry.oracle.com`, see the script `create-image-pull-secret.sh`. To specify a different image, use the script's `-i` option.

The script also assumes a Kubernetes secret with the Oracle `sys` account password in the secret's `password` field. To create this secret:
- Create a secret with your desired password. For example, call
`kubectl -n MYNAMESPACE create secret generic MYSECRETNAME --from-literal='password=MYSYSPASSWORD'`
and replace MYNAMESPACE, MYSECRETNAME, and MYPASSWORD with your desired values.
- Specify the '-a MYSECRETNAME' command line option.
- Oracle passwords can contain upper case, lower case, digits, and special characters.
  Use only "_" and "#" as special characters to eliminate potential parsing errors in Oracle connection strings.

Usage example:

```shell
$ ./start-db-service.sh -h
usage: ./start-db-service.sh [-a <dbasecret>] [-p <nodeport>] [-i <image>] [-s <pullsecret>] [-n <namespace>] [-h]
  -a DB Sys Account Password Secret Name (optional)
      (default: oracle-db-secret, secret must include a key named 'password')
      If this secret is not deployed, then the database will not successfully deploy.
  -p DB Service NodePort (optional)
      (default: 30011, set to 'none' to deploy service without a NodePort)
  -i Oracle DB Image (optional)
      (default: container-registry.oracle.com/database/enterprise:12.2.0.1-slim)
  -s DB Image Pull Secret (optional)
      (default: docker-store)
      If this secret is not deployed, then Kubernetes will try pull anonymously.
  -n Configurable Kubernetes NameSpace for Oracle DB Service (optional)
      (default: default)
  -h Help
```
```shell
$ kubectl -n MYNAMESPACE create secret generic MYSECRETNAME --from-literal='password=MYSYSPASSWORD'
```
```shell
$ ./start-db-service.sh -n MYNAMESPACE -a MYSECRETNAME
```
```shell
$ ./start-db-service.sh     
NodePort[30011] ImagePullSecret[docker-store] Image[container-registry.oracle.com/database/enterprise:12.2.0.1-slim]
deployment.extensions/oracle-db created
service/oracle-db created
[oracle-db-54667dfd5f-76sxf] already initialized ..
Checking Pod READY column for State [1/1]
Pod [oracle-db-54667dfd5f-76sxf] Status is Ready Iter [1/60]
NAME                         READY   STATUS    RESTARTS   AGE
oracle-db-54667dfd5f-76sxf   1/1     Running   0          8s
NAME                         READY   STATUS    RESTARTS   AGE
oracle-db-54667dfd5f-76sxf   1/1     Running   0          8s
NAME         TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)          AGE
kubernetes   ClusterIP   10.96.0.1      <none>        443/TCP          27d
oracle-db    NodePort    10.99.58.137   <none>        1521:30011/TCP   9s
Oracle DB service is RUNNING with NodePort [30011]
```

Notes:
- For samples that use an `input.yaml` file and that run in the same Kubernetes cluster as the database, you can use the database connection string, `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`, as the `rcuDatabaseURL` parameter. Change `default` to match the namespace that matches the namespace
- If you have not specified `-p none` on the command line when calling `start-db-service.sh`, then you can access the database through the NodePort outside of the Kubernetes cluster. For example, using the database connection string `<hostmachine>:30011/devpdb.k8s`.

## Stop an Oracle Database service in a Kubernetes cluster

Use this script to stop the Oracle Database service you created using the `start-db-service.sh` script.

```shell
$ ./stop-db-service.sh -h 
usage: stop-db-service.sh -n namespace  [-h]
 -n Kubernetes NameSpace for Oracle DB Service to be Stopped (optional)
     (default: default) 
 -h Help
```

Note: Here the NameSpace refers to the NameSpace used in start-db-service.sh

```
$ ./stop-db-service.sh  
deployment.extensions "oracle-db" deleted
service "oracle-db" deleted
Checking Status for Pod [oracle-db-756f9b99fd-gvv46] in namesapce [default]
Pod [oracle-db-756f9b99fd-gvv46] Status [Terminating]
Pod [oracle-db-756f9b99fd-gvv46] Status [Terminating]
Pod [oracle-db-756f9b99fd-gvv46] Status [Terminating]
Error from server (NotFound): pods "oracle-db-756f9b99fd-gvv46" not found
Pod [oracle-db-756f9b99fd-gvv46] removed from nameSpace [default]
```

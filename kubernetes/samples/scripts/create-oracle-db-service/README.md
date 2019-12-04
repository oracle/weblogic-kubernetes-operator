# Managing Oracle Database Service for Fusion Middleware domain

The sample scripts in this directory demonstrate how to:
* Start an Oracle Database (DB) service in a Kubernetes cluster.
* Stop an Oracle DB service in a Kubernetes cluster.

## Start an Oracle Database service in a Kubernetes cluster

Use this script to create an Oracle Database service in a Kubernetes Namespace with the default credentials, in the Oracle Database Slim image.

The script assumes that either the image, `container-registry.oracle.com/database/enterprise:12.2.0.1-slim`, is available in the Docker repository, or an `ImagePullSecret` is created for `container-registry.oracle.com`. To create a secret for accessing `container-registry.oracle.com`, see the script `create-image-pull-secret.sh`.

```

$ ./start-db-service.sh -h    
usage: ./start-db-service.sh -p <nodeport> -i <image> -s <pullsecret> -n <namespace>  [-h]
 -i  Oracle DB Image (optional)
    (default: container-registry.oracle.com/database/enterprise:12.2.0.1-slim)
  -p DB Service NodePort (optional)
    (default: 30011)
  -s DB Image PullSecret (optional)
    (default: docker-store)
  -n Configurable Kubernetes NameSpace for Oracle DB Service (optional)"
    (default: default)
  -h Help

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

For creating a Fusion Middleware domain, you can use the database connection string, `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`,as `rcuDatabaseURL` parameter in the `domain.input.yaml` file.

Note: oracle-db.default.svc.cluster.local:1521/devpdb.k8s can be used as rcuDatabaseURL if the Oracle DB Service is started in `default` NameSpace. For custom NameSpace the URL need to be modified accrodingly e.g. oracle-db.[namespace].svc.cluster.local:1521/devpdb.k8s 

You can access the database through the NodePort outside of the Kubernetes cluster, using the URL  `<hostmachine>:30011/devpdb.k8s`.

**Note**: To create a Fusion Middleware domain image, the domain-in-image model needs a public database URL as an `rcuDatabaseURL` parameter.

## Stop an Oracle Database service in a Kubernetes cluster

Use this script to stop the Oracle Database service you created using the `start-db-service.sh` script.

```
$ ./stop-db-service.sh -h 
usage: stop-db-service.sh -n namespace  [-h]
 -n Kubernetes NameSpace for Oracle DB Service to be Stopped (optional)
     (default: default) 
 -h Help

Note: Here the NameSpace refers to the NameSpace used in start-db-service.sh

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

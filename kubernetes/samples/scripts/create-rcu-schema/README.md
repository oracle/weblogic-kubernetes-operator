# Creating Oracle DB service and RCU Schema for a Fusion Middleware domain

The sample scripts in this directory demonstrates how to:
* Start an Oracle DB service in a Kubernetes cluster.
* Stop an Oracle DB service in a Kubernetes cluster.
* Create the RCU schema in the Oracle DB that will be used by a Fusion Middleware domain.
* Delete the RCU schema in the Oracle DB used by a Fusion Middleware domain.

## Start an Oracle DB service in a Kubernetes cluster
```
The script creates an Oracle DB Service in default namespace with default credential that comes with the Oracle Database Slim image.
$ ./start-db-service.sh -h    
usage: start-db-service.sh -p <nodeport> [-h]
  -p DB Service NodePort (optional)
      (default: 30011) 
  -h Help

$ ./start-db-service.sh     
Trying to pull repository container-registry.oracle.com/database/enterprise ... 
12.2.0.1-slim: Pulling from container-registry.oracle.com/database/enterprise
f07cd347d7cc: Pull complete 
7f4b317ad325: Pull complete 
Digest: sha256:25b0ec7cc3987f86b1e754fc214e7f06761c57bc11910d4be87b0d42ee12d254
Status: Downloaded newer image for container-registry.oracle.com/database/enterprise:12.2.0.1-slim
deployment.extensions/oracle-db created
service/oracle-db created
[oracle-db-756f9b99fd-k4t8b] already initialized .. 
Checking Pod READY column for State [1/1]
Pod [oracle-db-756f9b99fd-k4t8b] Status is Ready Iter [1/60]
NAME                         READY   STATUS    RESTARTS   AGE
oracle-db-756f9b99fd-k4t8b   1/1     Running   0          8s
NAME                         READY   STATUS    RESTARTS   AGE
oracle-db-756f9b99fd-k4t8b   1/1     Running   0          9s
NAME         TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
kubernetes   ClusterIP   10.96.0.1        <none>        443/TCP          23d
oracle-db    NodePort    10.107.245.233   <none>        1521:30011/TCP   8s
Oracle DB service is RUNNING with NodePort [30011]

The DB Connection String is oracle-db.default.svc.cluster.local:1521/devpdb.k8s which can be used as  rcuDatabaseURL parameter to domain.input.yaml file while creating a Fusion Middleware domain in Operator Environment 

The Database can be accessed through NodePort outside of Kubernates cluster using the URL String <hostmachine>:30011/devpdb.k8s

Note: Domain-in-Image model need public DB url as rcuDatabaseURL parameter to configure a Fusion Middleware domain.

```

## Create the RCU schema in the Oracle DB 
```
The script generates the RCU schema based schemaPrefix and dburl
$ ./create-rcu-schema.sh -h
usage: create-rcu-schema.sh -s <schemaPrefix> -d <dburl>  [-h]
  -s RCU Schema Prefix (needed)
  -d RCU Oracle Database URL (optional) 
      (default: oracle-db.default.svc.cluster.local:1521/devpdb.k8s) 
  -h Help

$ ./create-rcu-schema.sh -s domain1
Trying to pull repository container-registry.oracle.com/middleware/fmw-infrastructure ... 
12.2.1.3: Pulling from container-registry.oracle.com/middleware/fmw-infrastructure
35defbf6c365: Pull complete 
e406c26b7097: Pull complete 
51d87468764e: Pull complete 
8bcb1774cc5f: Pull complete 
42439840a3ce: Pull complete 
ebb1c8ed0ed5: Pull complete 
Digest: sha256:215d05d7543cc5d500eb213fa661753ae420d53e704baabeab89600827a61131
Status: Downloaded newer image for container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3
[oracle-db-756f9b99fd-k4t8b] already initialized .. 
Checking Pod READY column for State [1/1]
NAME                         READY   STATUS    RESTARTS   AGE
oracle-db-756f9b99fd-k4t8b   1/1     Running   0          34m
pod/rcu created
[rcu] already initialized .. 
Checking Pod READY column for State [1/1]
Pod [rcu] Status is Ready Iter [1/60]
NAME   READY   STATUS    RESTARTS   AGE
rcu    1/1     Running   0          11s
CLASSPATH=/usr/java/jdk1.8.0_211/lib/tools.jar:/u01/oracle/wlserver/modules/features/wlst.wls.classpath.jar:

Your environment has been set.
Check if the DB Service is ready to accept request 
DB Connection URL [oracle-db.default.svc.cluster.local:1521/devpdb.k8s] and schemaPrefix is [doman1]
[1/20] Retrying the DB Connection ...
[2/20] Retrying the DB Connection ...
[3/20] Retrying the DB Connection ...

**** Success!!! ****
You can connect to the database in your app using:
  java.util.Properties props = new java.util.Properties();
  props.put("user", "scott");
  props.put("password", "****");
  java.sql.Driver d =
    Class.forName("oracle.jdbc.OracleDriver").newInstance();
  java.sql.Connection conn =
    Driver.connect("scott", props);
	RCU Logfile: /tmp/RCU2019-08-30_21-21_1875987468/logs/rcu.log
Enter the database password(User:sys):
Processing command line ....
Repository Creation Utility - Checking Prerequisites
Checking Global Prerequisites
The selected Oracle database is not configured to use the AL32UTF8 character set. Oracle strongly recommends using the AL32UTF8 character set for databases that support Oracle Fusion Middleware.
Enter the schema password. This password will be used for all schema users of following components:MDS,IAU,IAU_APPEND,IAU_VIEWER,OPSS,WLS,STB.
 
Repository Creation Utility - Checking Prerequisites
Checking Component Prerequisites
Repository Creation Utility - Creating Tablespaces
Validating and Creating Tablespaces
Repository Creation Utility - Create
Repository Create in progress.
Percent Complete: 12
Percent Complete: 30
.....
Percent Complete: 97
Percent Complete: 100

Repository Creation Utility: Create - Completion Summary

Database details:
--------------------
Host Name                         : oracle-db.default.svc.cluster.local
Port                              : 1521
Service Name                      : DEVPDB.K8S
Connected As                      : sys
Prefix for (prefixable) Schema Owners        : DOMAIN1
RCU Logfile                : /tmp/RCU2019-08-30_21-21_1875987468/logs/rcu.log

Component schemas created:
-----------------------------
Component                                    Status         Logfile		

Common Infrastructure Services               Success        /tmp/RCU2019-08-30_21-21_1875987468/logs/stb.log 
Oracle Platform Security Services            Success        /tmp/RCU2019-08-30_21-21_1875987468/logs/opss.log 
Audit Services                               Success        /tmp/RCU2019-08-30_21-21_1875987468/logs/iau.log 
Audit Services Append                        Success        /tmp/RCU2019-08-30_21-21_1875987468/logs/iau_append.log 
Audit Services Viewer                        Success        /tmp/RCU2019-08-30_21-21_1875987468/logs/iau_viewer.log 
Metadata Services                            Success        /tmp/RCU2019-08-30_21-21_1875987468/logs/mds.log 
WebLogic Services                            Success        /tmp/RCU2019-08-30_21-21_1875987468/logs/wls.log 

Repository Creation Utility - Create : Operation Completed
[INFO] Modify the domain.input.yaml to use [oracle-db.default.svc.cluster.local:1521/devpdb.k8s] as rcuDatabaseURL and [domain1] as rcuSchemaPrefix
```

## Drop the RCU schema in the Oracle DB 
```
The script drops the RCU schema based schemaPrefix and dburl.
$ ./drop-rcu-schema.sh -h 
usage: ./drop-rcu-schema.sh -s <schemaPrefix> -d <dburl>  [-h]
  -s RCU Schema Prefix (needed)
  -d Oracle Database URL
      (default: oracle-db.default.svc.cluster.local:1521/devpdb.k8s) 
  -h Help

$ ./drop-rcu-schema.sh -s domain1
CLASSPATH=/usr/java/jdk1.8.0_211/lib/tools.jar:/u01/oracle/wlserver/modules/features/wlst.wls.classpath.jar:

Your environment has been set.
Check if the DB Service is ready to accept request 
DB Connection URL [oracle-db.default.svc.cluster.local:1521/devpdb.k8s] and schemaPrefix is [domain1]

**** Success!!! ****
You can connect to the database in your app using:
  java.util.Properties props = new java.util.Properties();
  props.put("user", "scott");
  props.put("password", "****");
  java.sql.Driver d =
    Class.forName("oracle.jdbc.OracleDriver").newInstance();
  java.sql.Connection conn =
    Driver.connect("scott", props);

	RCU Logfile: /tmp/RCU2019-08-30_21-29_1679536190/logs/rcu.log

Enter the database password(User:sys):
Processing command line ....
Repository Creation Utility - Checking Prerequisites
Checking Global Prerequisites
Repository Creation Utility - Checking Prerequisites
Checking Component Prerequisites
Repository Creation Utility - Drop
Repository Drop in progress.
Percent Complete: 2
Percent Complete: 14
....
Percent Complete: 99
Percent Complete: 100

Repository Creation Utility: Drop - Completion Summary

Database details:
----------------------------
Host Name                              : oracle-db.default.svc.cluster.local
Port                                   : 1521
Service Name                           : DEVPDB.K8S
Connected As                           : sys
Prefix for (prefixable) Schema Owners        : DOMAIN1
RCU Logfile                                  : /tmp/RCU2019-08-30_21-29_1679536190/logs/rcu.log

Component schemas dropped:
-----------------------------
Component                                    Status         Logfile		

Common Infrastructure Services               Success        /tmp/RCU2019-08-30_21-29_1679536190/logs/stb.log 
Oracle Platform Security Services            Success        /tmp/RCU2019-08-30_21-29_1679536190/logs/opss.log 
Audit Services                               Success        /tmp/RCU2019-08-30_21-29_1679536190/logs/iau.log 
Audit Services Append                        Success        /tmp/RCU2019-08-30_21-29_1679536190/logs/iau_append.log 
Audit Services Viewer                        Success        /tmp/RCU2019-08-30_21-29_1679536190/logs/iau_viewer.log 
Metadata Services                            Success        /tmp/RCU2019-08-30_21-29_1679536190/logs/mds.log 
WebLogic Services                            Success        /tmp/RCU2019-08-30_21-29_1679536190/logs/wls.log 

Repository Creation Utility - Drop : Operation Completed
pod "rcu" deleted
Checking Status for Pod [rcu] in namesapce [default]
Error from server (NotFound): pods "rcu" not found
Pod [rcu] removed from nameSpace [default]
```

## Stop an Oracle DB service in a Kubernetes cluster
```
The script stop the Oracle DB service created thru start-db-service.sh
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

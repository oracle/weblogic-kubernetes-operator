# Creating Oracle DB service and RCU Schema for a Fusion Middleware domain

This sample demonstrates how to create an Oracle DB service on Kubernetes cluster and create RCU schema on the Oracle DB being used by a Fusion Middleware domain.  

The directory contains the following sample scripts to start an Oracle DB service in default namespace, stop the Oracle DB service, create an RCU schema and drop the RCU schema

```
$ ./start-db-service.sh   
Trying to pull repository container-registry.oracle.com/database/enterprise ... 
12.2.0.1-slim: Pulling from container-registry.oracle.com/database/enterprise
Digest: sha256:25b0ec7cc3987f86b1e754fc214e7f06761c57bc11910d4be87b0d42ee12d254
Status: Image is up to date for container-registry.oracle.com/database/enterprise:12.2.0.1-slim
deployment.extensions/oracle-db created
service/oracle-db created
[oracle-db-756f9b99fd-r6ghb] already initialized .. 
Checking Pod READY column for State [1/1]
NAME                         READY   STATUS    RESTARTS   AGE
oracle-db-756f9b99fd-r6ghb   1/1     Running   0          3s
NAME                         READY   STATUS    RESTARTS   AGE
oracle-db-756f9b99fd-r6ghb   1/1     Running   0          4s
NAME         TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
kubernetes   ClusterIP   10.96.0.1       <none>        443/TCP          16d
oracle-db    NodePort    10.100.148.17   <none>        1521:30011/TCP   3s
Oracle DB service is RUNNING with external NodePort [30011]

```
The parameters are as follows:

```  
  -p external NodePort for the Service (default is 30011) 
```
```
The script create a Oracle DB Service in default Namesapce with default Credential that comes with the Oracle Database Slim image.
$ kubectl get po
NAME                         READY   STATUS    RESTARTS   AGE
oracle-db-756f9b99fd-ch7xt   1/1     Running   0          33m
$ kubectl get svc
NAME         TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)          AGE
oracle-db    NodePort    10.99.94.157   <none>        1521:30011/TCP   32m

The DB Connection String is oracle-db.default.svc.cluster.local:1521/devpdb.k8s which can be used as  rcuDatabaseURL parameter to domain.input.yaml file while creating a Fusion Middleware domain in Operator Environment 

The Database can be accessed thru external NodePort outside of Kubernates cluster using the URL String <hostmachine>:30011/devpdb.k8s

Note : Domain-in-Image model need public DB url as rcuDatabaseURL parameter to configure Fusion Middleware domain in Operator Environment

```

```
$ ./create-rcu-schema.sh -s <schemaPrefix> -d <dburl> 


```
The parameters are as follows:

```  
  -s  RCU Schema Prefix, Must be specified
  -d  RCU Oracle Database URL (default oracle-db.default.svc.cluster.local:1521/devpdb.k8s) 
```
```
The script Generates the RCU schema based Schema Prefix and RCU DB URL
$ ./create-rcu-schema.sh -s domain1
[oracle-db-756f9b99fd-r6ghb] already initialized .. 
Checking Pod READY column for State [1/1]
NAME                         READY   STATUS    RESTARTS   AGE
oracle-db-756f9b99fd-r6ghb   1/1     Running   0          109s
Trying to pull repository container-registry.oracle.com/middleware/fmw-infrastructure ... 
12.2.1.3: Pulling from container-registry.oracle.com/middleware/fmw-infrastructure
Digest: sha256:215d05d7543cc5d500eb213fa661753ae420d53e704baabeab89600827a61131
Status: Image is up to date for container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3
pod/rcu created
[rcu] already initialized .. 
Checking Pod READY column for State [1/1]
Pod [rcu] Status is Ready Iter [1/60]
NAME   READY   STATUS    RESTARTS   AGE
rcu    1/1     Running   0          7s
NAME                         READY   STATUS    RESTARTS   AGE
oracle-db-756f9b99fd-r6ghb   1/1     Running   0          2m2s
rcu                          1/1     Running   0          12s
CLASSPATH=/usr/java/jdk1.8.0_211/lib/tools.jar:/u01/oracle/wlserver/modules/features/wlst.wls.classpath.jar:

PATH=/u01/oracle/wlserver/server/bin:/u01/oracle/wlserver/../oracle_common/modules/thirdparty/org.apache.ant/1.9.8.0.0/apache-ant-1.9.8/bin:/usr/java/jdk1.8.0_211/jre/bin:/usr/java/jdk1.8.0_211/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/java/default/bin:/u01/oracle/oracle_common/common/bin:/u01/oracle/wlserver/common/bin:/u01/oracle/container-scripts:/u01/oracle/wlserver/../oracle_common/modules/org.apache.maven_3.2.5/bin

Your environment has been set.
Check if the DB Service is Ready to accept Connection ?
DB Connection URL [oracle-db.default.svc.cluster.local:1521/devpdb.k8s] and schemaPrefix is [domain1]
[1/20] Retrying the DB Connection ...
[2/20] Retrying the DB Connection ...
[3/20] Retrying the DB Connection ...

**** Success!!! ****

You can connect to the database in your app using:

  java.util.Properties props = new java.util.Properties();
  props.put("user", "scott");
  props.put("password", "tiger");
  java.sql.Driver d =
    Class.forName("oracle.jdbc.OracleDriver").newInstance();
  java.sql.Connection conn =
    Driver.connect("scott", props);

	RCU Logfile: /tmp/RCU2019-08-23_21-13_1202784079/logs/rcu.log

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
Percent Complete: 30
Percent Complete: 32
Percent Complete: 34
Percent Complete: 36
Percent Complete: 36
Percent Complete: 36
Percent Complete: 45
Percent Complete: 45
Percent Complete: 55
Percent Complete: 55
Percent Complete: 55
Percent Complete: 63
Percent Complete: 63
Percent Complete: 73
Percent Complete: 73
Percent Complete: 73
Percent Complete: 81
Percent Complete: 81
Percent Complete: 83
Percent Complete: 83
Percent Complete: 85
Percent Complete: 85
Percent Complete: 94
Percent Complete: 94
Percent Complete: 94
Percent Complete: 95
Percent Complete: 96
Percent Complete: 97
Percent Complete: 97
Percent Complete: 100

Repository Creation Utility: Create - Completion Summary

Database details:
-----------------------------
Host Name                                    : oracle-db.default.svc.cluster.local
Port                                         : 1521
Service Name                                 : DEVPDB.K8S
Connected As                                 : sys
Prefix for (prefixable) Schema Owners        : DOMAIN1
RCU Logfile                                  : /tmp/RCU2019-08-23_21-13_1202784079/logs/rcu.log

Component schemas created:
-----------------------------
Component                                    Status         Logfile		

Common Infrastructure Services               Success        /tmp/RCU2019-08-23_21-13_1202784079/logs/stb.log 
Oracle Platform Security Services            Success        /tmp/RCU2019-08-23_21-13_1202784079/logs/opss.log 
Audit Services                               Success        /tmp/RCU2019-08-23_21-13_1202784079/logs/iau.log 
Audit Services Append                        Success        /tmp/RCU2019-08-23_21-13_1202784079/logs/iau_append.log 
Audit Services Viewer                        Success        /tmp/RCU2019-08-23_21-13_1202784079/logs/iau_viewer.log 
Metadata Services                            Success        /tmp/RCU2019-08-23_21-13_1202784079/logs/mds.log 
WebLogic Services                            Success        /tmp/RCU2019-08-23_21-13_1202784079/logs/wls.log 

Repository Creation Utility - Create : Operation Completed
[INFO] Modify the domain.input.yaml to use [oracle-db.default.svc.cluster.local:1521/devpdb.k8s] as rcuDatabaseURL and [domain1] as rcuSchemaPrefix

```

```
$ ./drop-rcu-schema.sh -s <schemaPrefix> -d <dburl> 
```
The parameters are as follows:

```  
  -s  RCU Schema Prefix, Must be specified
  -d  RCU Oracle Database URL (default oracle-db.default.svc.cluster.local:1521/devpdb.k8s) 
```
```
The script drop RCU schema based Schema Prefix and RCU DB URL
```

```
$ ./stop-db-service.sh  
```
```
The script stop the DB service created thru start-db-service.sh
```

# Managing RCU schema for a Fusion Middleware domain

The sample scripts in this directory demonstrate how to:
* Create an RCU schema in the Oracle DB that will be used by a Fusion Middleware domain.
* Delete the RCU schema in the Oracle DB used by a Fusion Middleware domain.

## Start an Oracle Database service in a Kubernetes cluster

Use the script ``samples/scripts/create-oracle-db-service/start-db-service.sh``

For creating a Fusion Middleware domain, you can use the Database connection string, `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`, as an `rcuDatabaseURL` parameter in the `domain.input.yaml` file.

You can access the Database through the NodePort outside of the Kubernetes cluster, using the URL  `<hostmachine>:30011/devpdb.k8s`.

**Note**: To create a Fusion Middleware domain image, the domain-in-image model needs a public Database URL as an `rcuDatabaseURL` parameter.


## Create the RCU schema in the Oracle Database

This script generates the RCU schema based `schemaPrefix` and `dburl`.

The script assumes that either the image, `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3`, is available in the Docker image repository or an `ImagePullSecret` is created for `container-registry.oracle.com`. To create a secret for accessing `container-registry.oracle.com`, see the script `create-image-pull-secret.sh`.

```
$ ./create-rcu-schema.sh -h
usage: ./create-rcu-schema.sh -s <schemaPrefix> -t <rcuType> -d <dburl> -i <image> -s <dockerstore> [-h]
  -s RCU Schema Prefix (required)
  -t RCU Schema Type (optional)
      Supported values: fmw(default),soa,osb,soaosb,soaess,soaessosb
  -d RCU Oracle Database URL (optional)
      (default: oracle-db.default.svc.cluster.local:1521/devpdb.k8s)
  -p Fmw Infrastructure ImagePull Secret (optional)
      (default: docker-store)
  -i Fmw Infrastructure Image (optional)
      (default: container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3)
  -h Help

$ ./create-rcu-schema.sh -s domain1

ImagePullSecret[docker-store] Image[container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3] dburl[oracle-db.default.svc.cluster.local:1521/devpdb.k8s]
[oracle-db-54667dfd5f-76sxf] already initialized ..
Checking Pod READY column for State [1/1]
NAME                         READY   STATUS    RESTARTS   AGE
oracle-db-54667dfd5f-76sxf   1/1     Running   0          5m1s
pod/rcu created
[rcu] already initialized ..
Checking Pod READY column for State [1/1]
Pod [rcu] Status is Ready Iter [1/60]
NAME   READY   STATUS    RESTARTS   AGE
rcu    1/1     Running   0          6s
NAME   READY   STATUS    RESTARTS   AGE
rcu    1/1     Running   0          11s
CLASSPATH=/usr/java/jdk1.8.0_211/lib/tools.jar:/u01/oracle/wlserver/modules/features/wlst.wls.classpath.jar:

PATH=/u01/oracle/wlserver/server/bin:/u01/oracle/wlserver/../oracle_common/modules/thirdparty/org.apache.ant/1.9.8.0.0/apache-ant-1.9.8/bin:/usr/java/jdk1.8.0_211/jre/bin:/usr/java/jdk1.8.0_211/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/java/default/bin:/u01/oracle/oracle_common/common/bin:/u01/oracle/wlserver/common/bin:/u01/oracle/container-scripts:/u01/oracle/wlserver/../oracle_common/modules/org.apache.maven_3.2.5/bin

Your environment has been set.
Check if the DB service is ready to accept requests
DB Connection URL [oracle-db.default.svc.cluster.local:1521/devpdb.k8s] and schemaPrefix is [domain1] rcuType [fmw]"

**** Success!!! ****
You can connect to the database in your app using:
  java.util.Properties props = new java.util.Properties();
  props.put("user", "scott")
  props.put("password", "*****");
  java.sql.Driver d =
    Class.forName("oracle.jdbc.OracleDriver").newInstance();
  java.sql.Connection conn =
    Driver.connect("scott", props);

Creating RCU Schema for FMW Domain ..
	RCU Logfile: /tmp/RCU2019-09-03_23-13_1113614917/logs/rcu.log
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

## Drop the RCU schema from the Oracle Database

Use this script to drop the RCU schema based `schemaPrefix` and `dburl`.

```
$ ./drop-rcu-schema.sh -h
usage: ./drop-rcu-schema.sh -s <schemaPrefix> -d <dburl>  [-h]
  -s RCU Schema Prefix (required)
  -d Oracle Database URL
      (default: oracle-db.default.svc.cluster.local:1521/devpdb.k8s)
  -t RCU Schema Type (optional)
      Supported values: fmw(default),soa,osb,soaosb,soaess,soaessosb
  -h Help

$ ./drop-rcu-schema.sh -s domain1
CLASSPATH=/usr/java/jdk1.8.0_211/lib/tools.jar:/u01/oracle/wlserver/modules/features/wlst.wls.classpath.jar:

Your environment has been set.
Check if the DB service is ready to accept requests
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

## Stop an Oracle Database service in a Kubernetes cluster

Use the script ``samples/scripts/create-oracle-db-service/stop-db-service.sh``

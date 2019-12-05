---
title: "Persisting SOA Adapters Customizations"
date: 2019-12-05T06:46:23-05:00
weight: 2
description: "The adapters (such as DB Adapter, File Adapter etc) in a SOA domain can be customized based on users requirement"
---

The lifetime for any customization done in a file on a server pod is upto the lifetime of that pod, the changes are not persisted once the pod goes down or restarted.

For example: Below configuration updates `DbAdapter.rar` to create a new connection instance and creates DatasSource CoffeeShop on Administration Console for the same with jdbc/CoffeeShopDS.

file location: /u01/oracle/soa/soa/connectors/DbAdapter.rar
``` 
<connection-instance>
  <jndi-name>eis/DB/CoffeeShop</jndi-name>
  <connection-properties>
    <properties>
      <property>
        <name>XADataSourceName</name>
        <value>jdbc/CoffeeShopDS</value>
      </property>
      <property>
        <name>DataSourceName</name>
	    <value></value>
      </property>
      <property>
        <name>PlatformClassName</name> 
	    <value>org.eclipse.persistence.platform.database.Oracle10Platform</value>
      </property>
    </properties>
   </connection-properties>
</connection-instance>
``` 
If you need to persist the customizations for any of the adpater files under SOA oracle home in the server pod, you need to follow one of the below methods. 

## Method 1: Customize the Adapter file using the Administration console:

* Login to WebLogic Administration console : Deployments -> ABC.rar -> Configuration -> Outbound Connection Pools
* Create a new connection that is required : New -> provide connection name -> finish
* Go back to this new connection : update the required properties under it and save
* Go back to deployments : select the ABC.rar -> Update 
  This step asks for `Plan.xml` location. This location by default will be in `${MW_HOME}/soa/soa` which is not under Persistent Volume.   
  Hence when you specify above location, provide the domains PV location such as `{DOMAIN_HOME}/soainfra/servers` etc.  
  Now the `Plan.xml` will be persisted under this location for each Managed Servers.

## Method 2: Customize the Adapter file on the Worker Node:
    
* Copy the `ABC.rar` from the server pod to a PV path:
  ```
  command:
  $ kubectl cp <namespace>/<SOA Managed Server pod name>:<full path of .rar file>  <destination path inside PV>
  ```
  ```
  Sample command:
  $ kubectl cp soans/soainfra-soa-server1:/u01/oracle/soa/soa/connectors/ABC.rar ${DockerVolume}/domains/soainfra/servers/ABC.rar
  ```
  or 
  You can do a normal file copy between these locations after entering (kubectl exec) in to the Managed Server pod.
* Unrar the ABC.rar.
* Update the new connection details in `weblogic-ra.xml` file under META_INF.
* In WebLogic Administration console, Under Deployments -> select `ABC.rar` and click update.
* Here, select the `ABC.rar` path as the new location which is: `${DOMAIN_HOME}/user_projects/domains/soainfra/servers/ABC.rar` and update
* Verify that the `plan.xml` or updated .rar should be persisted in PV.


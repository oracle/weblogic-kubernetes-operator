---
title: "Disabling Fast Application Notifications"
date:
draft: false
---

To support Fast Application Notifications (FAN), Oracle databases configure GRID.
GRID is typically associated with (and required by) Oracle RAC databases but can
also be used in other configurations.  ATP-S does not provide GRID.

When connecting to a database that does not have GRID, the only type of WebLogic Server
supported data source is Generic Data Sources. Multi Data Sources and Active GridLink
data sources cannot be used because they work with RAC.


When connecting with a 12.2 driver to a database that does not have GRID, you will
encounter the following exception (however, the 18.3 driver does not have this problem):

```
oracle.simplefan.impl.FanManager configure
SEVERE: attempt to configure ONS in FanManager failed with oracle.ons.NoServersAvailable: Subscription time out
```

To correct the problem, you must disable FAN, in one of two places:

1)	Through a system property at the domain, cluster, or server level.  

To do this, edit the Domain Custom Resource to set the system property `oracle.jdbc.fanEnabled`
to false as shown in the following example:

```
  serverPod:
    # an (optional) list of environment variable to be set on the servers
    env:
    - name: JAVA_OPTIONS
      value: "-Dweblogic.StdoutDebugEnabled=false -Doracle.jdbc.fanEnabled=false"
```

2) Configure the data source connection pool properties.  

The following WLST script creates a data source with the connection property set.

```
       fmwDb = 'jdbc:oracle:thin:@' + db
       print 'fmwDatabase  ' + fmwDb
       cd('/JDBCSystemResource/LocalSvcTblDataSource/JdbcResource/LocalSvcTblDataSource')
       cd('JDBCDriverParams/NO_NAME_0')
       set('DriverName', 'oracle.jdbc.OracleDriver')
       set('URL', fmwDb)
       set('PasswordEncrypted', dbPassword)

       stbUser = dbPrefix + '_STB'
       cd('Properties/NO_NAME_0/Property/user')
       set('Value', stbUser)
       ls()
       cd('../..')
       ls()
       create('oracle.jdbc.fanEnabled','Property')
       ls()
       cd('Property/oracle.jdbc.fanEnabled')
       set('Value', 'false')
       ls()
```

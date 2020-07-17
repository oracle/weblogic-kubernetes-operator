# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

connect('weblogic', 'welcome1', 't3://DOMAINNAME-admin-server:7001')

# get all JDBC Properties
dsCounter = 0
allJDBCResources = cmo.getJDBCSystemResources()
for jdbcResource in allJDBCResources:
    dsCounter = dsCounter + 1
    dsname = jdbcResource.getName()
    dsResource = jdbcResource.getJDBCResource()
    dsJNDIname = dsResource.getJDBCDataSourceParams().getJNDINames()#[0]
    dsDriver = dsResource.getJDBCDriverParams().getDriverName()
    conn =  dsResource.getJDBCDriverParams().getUrl()
    dsInitialCap = dsResource.getJDBCConnectionPoolParams().getInitialCapacity()
    dsMaxCap = dsResource.getJDBCConnectionPoolParams().getMaxCapacity()
    dsParams = dsResource.getJDBCDataSourceParams()
    dsProps = dsResource.getJDBCDriverParams().getProperties()
    dsParams = dsResource.getJDBCConnectionPoolParams()

    user = get("/JDBCSystemResources/"+ dsname +"/Resource/" + dsname + "/JDBCDriverParams/" + dsname + "/Properties/" + dsname + "/Properties/user/Value")
    readTimeOut = get("/JDBCSystemResources/"+ dsname +"/Resource/" + dsname + "/JDBCDriverParams/" + dsname + "/Properties/" + dsname + "/Properties/oracle.jdbc.ReadTimeout/Value")
    connTimeOut = get("/JDBCSystemResources/"+ dsname +"/Resource/" + dsname + "/JDBCDriverParams/" + dsname + "/Properties/" + dsname + "/Properties/oracle.net.CONNECT_TIMEOUT/Value")

    print 'datasource.name.' + str(dsCounter) +'=' + str(dsname)
    print 'datasource.jndiname.' + str(dsCounter) + '=' + str(dsJNDIname)
    print 'datasource.driver.class.' + str(dsCounter) + '=' + dsDriver
    print 'datasource.url.' + str(dsCounter) + '=' + conn
    print 'datasource.initialCapacity.' + str(dsCounter) + '=' + str(dsInitialCap)
    print 'datasource.maxCapacity.' + str(dsCounter) + '=' + str(dsMaxCap)
    print 'datasource.readTimeout.' + str(dsCounter) + '=' + readTimeOut
    print 'datasource.connectionTimeout.' + str(dsCounter) + '=' + connTimeOut
    print 'datasource.username.' + str(dsCounter) + '=' + str(user)
    print 'datasource.dsProps.' + str(dsCounter) + '=' + str(dsProps)
    print 'datasource.dsParams.' + str(dsCounter) + '=' + str(dsParams)
disconnect()
exit()
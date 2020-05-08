# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

connect('weblogic', 'welcome1', 't3://itmiiconfigupdatesecret-domain-3-admin-server:7001')

# get all JDBC Properties
dsCounter = 0
allJDBCResources = cmo.getJDBCSystemResources()
for jdbcResource in allJDBCResources:
    dsCounter = dsCounter + 1
    dsname = jdbcResource.getName()
    dsResource = jdbcResource.getJDBCResource()
    dsJNDIname = dsResource.getJDBCDataSourceParams().getJNDINames()#[0]
    jdbcurl =  dsResource.getJDBCDriverParams().getUrl()
    user = get("/JDBCSystemResources/"+ dsname +"/Resource/" + dsname + "/JDBCDriverParams/" + dsname + "/Properties/" + dsname + "/Properties/user/Value")

    print 'datasource.name.' + str(dsCounter) +'=' + str(dsname)
    print 'datasource.jndiname.' + str(dsCounter) + '=' + str(dsJNDIname)
    print 'datasource.jdbcurl.' + str(dsCounter) + '=' + jdbcurl
    print 'datasource.username.' + str(dsCounter) + '=' + str(user)
disconnect()
exit()
# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

hostname=sys.argv[1]
connect('weblogic','welcome1','t3://'+hostname+':7001')
serverRuntime()
cd('JMSRuntime/admin-server.jms/JMSServers/TestAdminJmsServer/Destinations/TestAdminJmsModule!testAccountingQueue')
ls()
count=cmo.getMessagesCurrentCount()
print 'messagesgot='+str(count)
disconnect()
exit()


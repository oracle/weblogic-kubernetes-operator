# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import time as systime

# to give enough time for the DNS entries to be updated
systime.sleep(15)

connect(sys.argv[1],sys.argv[2],sys.argv[3])

appName=sys.argv[4]
appPath=sys.argv[5]
clusterName=sys.argv[6]

print 'Deploying application[%s] to the Cluster[%s] located @ [%s] ' %(appName,clusterName,appPath)
print 'Checking the server status on the Cluster[%s] ' %(clusterName)
domainRuntime()
state(clusterName,'Cluster')

deploy(appName=appName, path=appPath, targets=clusterName, upload='false', remote='false')

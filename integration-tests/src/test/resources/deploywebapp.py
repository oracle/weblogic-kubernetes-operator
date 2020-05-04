# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
connect(sys.argv[1],sys.argv[2],sys.argv[3])

appName=sys.argv[4]
appPath=sys.argv[5]
clusterName=sys.argv[6]

print 'Deploying application[%s] to the Cluster[%s] located @ [%s] ' %(appName,clusterName,appPath)
print 'Checking the server status on the Cluster[%s] ' %(clusterName)
domainRuntime()
state(clusterName,'Cluster')

#deploy(sys.argv[4],sys.argv[5],sys.argv[6],upload='false',remote='false')
deploy(appName=appName, path=appPath, targets=clusterName, upload='false', remote='false', timeout=600000)

#cd ('AppDeployments')
#myapps=cmo.getAppDeployments()
 
#for appName in myapps:
#       domainConfig()
#       cd ('/AppDeployments/'+appName.getName()+'/Targets')
#       mytargets = ls(returnMap='true')
#       domainRuntime()
#       cd('AppRuntimeStateRuntime')
#       cd('AppRuntimeStateRuntime')
#       for targetinst in mytargets:
#             curstate4=cmo.getCurrentState(appName.getName(),targetinst)
#             print '-----------', curstate4, '-----------', appName.getName()
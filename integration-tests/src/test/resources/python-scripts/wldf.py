# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

from java.util import Properties
import sys, socket
import os
import time as systime

k8s_master_host=os.environ.get('KUBERNETES_SERVICE_HOST')
k8s_master_port=os.environ.get('KUBERNETES_SERVICE_PORT')
k8s_master="https://"+k8s_master_host+":"+k8s_master_port
operator_cert_data=os.environ.get('INTERNAL_OPERATOR_CERT')
domain_home=os.environ.get('DOMAIN_HOME')

adminUsername=sys.argv[1]
adminPassword=sys.argv[2]
adminUrl=sys.argv[3]
scaleAction=sys.argv[4]
domainUid=sys.argv[5]
clusterName=sys.argv[6]
domainNamespace=sys.argv[7]
opNamespace=sys.argv[8]
operator_service_account=sys.argv[9]
scaling_size=sys.argv[10]
myAppName=sys.argv[11]

wldfname = 'Scaling' + clusterName
scriptAction = 'ScriptAction' + scaleAction
connect(adminUsername,adminPassword,adminUrl)
print "Looking up WLDF System Resource: " + wldfname

edit()
startEdit()

wldfSysResource=cmo.lookupWLDFSystemResource(wldfname)
if(wldfSysResource != None):
  print "Deleting the existing WLDFSystemResource ..."
  cmo.destroyWLDFSystemResource(getMBean('WLDFSystemResources/' + wldfname))
  save()
  activate(block='true')

startEdit()
as_bean=getMBean('/Servers/admin-server')
wldfSysResource=cmo.createWLDFSystemResource(wldfname)
wldfSysResource.addTarget(as_bean)
wldfResource = wldfSysResource.getWLDFResource()
print('Configuring New WLDF System Resource');

wn1 = wldfResource.getWatchNotification()
scriptAct = wn1.createScriptAction(scriptAction)
scriptAct.setEnabled(true)
scriptAct.setTimeout(0)
scriptAct.setWorkingDirectory(domain_home + '/bin/scripts')
scriptAct.setPathToScript(domain_home + '/bin/scripts/scalingAction.sh')
props = Properties()
props.setProperty("INTERNAL_OPERATOR_CERT",  operator_cert_data);
scriptAct.setEnvironment(props)

params=['--action=%s' %scaleAction]
params.append('--domain_uid=%s' %domainUid)
params.append('--cluster_name=%s' %clusterName)
params.append('--wls_domain_namespace=%s' %domainNamespace)
params.append('--operator_namespace=%s' %opNamespace)
params.append('--operator_service_account=%s' %operator_service_account)
params.append('--scaling_size=%s' %scaling_size)
params.append('--kubernetes_master=%s' %k8s_master)
scriptAct.setParameters(params)

wh1 = wn1.createWatch('myScalePolicy')
wh1.setRuleType('Harvester')
wh1.setExpressionLanguage('EL')
wh1.setEnabled(true)
wh1.setRuleExpression("wls:ClusterGenericMetricRule('" + clusterName + "','com.bea:Type=WebAppComponentRuntime,ApplicationRuntime=" + myAppName + ",*','OpenSessionsCurrentCount','>=',0.01,5,'1 seconds','10 seconds')")
wh1.getSchedule().setMinute("*");
wh1.getSchedule().setSecond("*/15");
wh1.setAlarmType('ManualReset');
wh1.addNotification(scriptAct)

save()
activate(block='true')

print "verify the policy and script action is enabled"
cd("WLDFSystemResources/" + wldfname + "/WLDFResource/" + wldfname + "/WatchNotification/" + wldfname + "/Watches/myScalePolicy")
maxwait=300
isEnabled=cmo.isEnabled()

while(isEnabled != 1) and (maxwait > 0):
  print isEnabled
  maxwait -= 1
  systime.sleep(1)
  isEnabled=cmo.isEnabled()

print "myScalePolicy is enabled"

cd("/WLDFSystemResources/" + wldfname + "/WLDFResource/" + wldfname + "/WatchNotification/" + wldfname + "/ScriptActions/" + scriptAction)
isEnabled = cmo.isEnabled()

while(isEnabled != 1) and (maxwait > 0):
  print isEnabled
  maxwait -= 1
  systime.sleep(1)
  isEnabled=cmo.isEnabled()

print "scriptAction " + scriptAction + " is enabled"

print "wait for harvester watch to become active"

domainRuntime()

cd('ServerRuntimes/admin-server/WLDFRuntime/WLDFRuntime/WLDFWatchNotificationRuntime/WatchNotification')

numWatchEval=cmo.getTotalHarvesterWatchEvaluations()

while(numWatchEval < 1) and (maxwait > 0):
  print numWatchEval
  maxwait -= 1
  systime.sleep(1)
  numWatchEval=cmo.getTotalHarvesterWatchEvaluations()

print "wldf.py done numWatchEval is ", numWatchEval, " maxwait is ", maxwait


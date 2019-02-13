import sys, traceback
from java.util import Properties
from java.io import File
import sys, socket
import os
import time as systime
hostname = socket.gethostname()

k8s_master_host=os.environ.get('KUBERNETES_SERVICE_HOST')
k8s_master_port=os.environ.get('KUBERNETES_SERVICE_PORT')
k8s_master="https://"+k8s_master_host+":"+k8s_master_port
operator_cert_data=os.environ.get('INTERNAL_OPERATOR_CERT')

wldfname = 'Scaling'

username=sys.argv[1]
password=sys.argv[2]
aurl=sys.argv[3]

connect(username,password,aurl)
print "Looking up WLDF System Resource: " + wldfname

edit()
startEdit()

wldfSysResource=cmo.lookupWLDFSystemResource(wldfname)
if(wldfSysResource != None):
  print "Deleting the existing WLDFSystemResource ..."
  cmo.destroyWLDFSystemResource(getMBean('WLDFSystemResources/' + wldfname))

as_bean=getMBean('/Servers/admin-server')
wldfSysResource=cmo.createWLDFSystemResource(wldfname)
wldfSysResource.addTarget(as_bean)
wldfResource = wldfSysResource.getWLDFResource()
print('Configuring New WLDF System Resource');

wn1 = wldfResource.getWatchNotification()
scriptAct = wn1.createScriptAction('ScriptActionScaleUp')
scriptAct.setEnabled(true)
scriptAct.setTimeout(0)
scriptAct.setWorkingDirectory('/shared/domains/domainonpvwdt/bin/scripts/')
scriptAct.setPathToScript('/shared/domains/domainonpvwdt/bin/scripts/scalingAction.sh')
props = Properties()
props.setProperty("INTERNAL_OPERATOR_CERT",  operator_cert_data);
#props.setProperty("KUBERNETES_SERVICE_HOST", k8s_master_host);
#props.setProperty("KUBERNETES_SERVICE_PORT", k8s_master_port);
scriptAct.setEnvironment(props)

params=['--action=scaleUp', '--domain_uid=domainonpvwdt', '--cluster_name=cluster-1', '--wls_domain_namespace=test2', '--operator_namespace=weblogic-operator2']
k8s_master_url='--kubernetes_master=%s' %(k8s_master)
params.append(k8s_master_url)
scriptAct.setParameters(params)


wh1 = wn1.createWatch('myScaleUpPolicy')
wh1.setRuleType('Harvester')
wh1.setExpressionLanguage('EL')
wh1.setEnabled(true)
wh1.setRuleExpression("wls:ClusterGenericMetricRule('cluster-1','com.bea:Type=WebAppComponentRuntime,ApplicationRuntime=opensessionapp,*','OpenSessionsCurrentCount','>=',0.01,5,'1 seconds','10 seconds')")
wh1.getSchedule().setMinute("*");
wh1.getSchedule().setSecond("*/15");
wh1.setAlarmType('AutomaticReset');
wh1.setAlarmResetPeriod(60000)
wh1.addNotification(scriptAct)

save()
activate(block='true')

print "wait for harvester watch to become active"

domainRuntime()

cd('ServerRuntimes/admin-server/WLDFRuntime/WLDFRuntime/WLDFWatchNotificationRuntime/WatchNotification')

numWatchEval=cmo.getTotalHarvesterWatchEvaluations()
maxwait=300

while(numWatchEval < 1) and (maxwait > 0):
  print numWatchEval
  maxwait -= 1
  systime.sleep(1)
  numWatchEval=cmo.getTotalHarvesterWatchEvaluations()

print "wldf.py done numWatchEval is ", numWatchEval, " maxwait is ", maxwait


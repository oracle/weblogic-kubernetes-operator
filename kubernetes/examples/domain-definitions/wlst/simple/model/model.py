import os
from weblogic.management.configuration import TargetMBean

wl_home                  = os.environ.get("WL_HOME")
domain_path              = os.environ.get("DOMAIN_HOME")
domain_name              = "domain1"
admin_server_name        = "admin-server"
admin_port               = 7001
t3_channel_port          = 30012
t3_public_address        = "kubernetes"
cluster_name             = "cluster-1"
number_of_ms             = 5
managed_server_name_base = "managed-server"
managed_server_port      = 8001

file = open('/weblogic-credentials/username', 'r')
username = file.read()
file.close()

file = open('/weblogic-credentials/password', 'r')
password = file.read()
file.close()

readTemplate(wl_home + "/common/templates/wls/wls.jar")

cmo.setName(domain_name)
setOption("DomainName", domain_name)
setOption("OverwriteDomain", "true")

cd("/Security/" + domain_name + "/User/weblogic")
cmo.setName(username)
cmo.setPassword(password)

cd("/Servers/AdminServer")
cmo.setListenPort(admin_port)
cmo.setName(admin_server_name)
nap=create("T3Channel", 'NetworkAccessPoint')
nap.setPublicPort(t3_channel_port)
nap.setPublicAddress(t3_public_address)
nap.setListenPort(t3_channel_port)

cd("/")
cl=create(cluster_name, 'Cluster')
templateName = cluster_name + "-template"
st=create(templateName, "ServerTemplate")
st.setListenPort(managed_server_port)
st.setCluster(cl)
cd("/Clusters/" + cluster_name)
ds=create(cluster_name, "DynamicServers")
ds.setServerTemplate(st)
ds.setServerNamePrefix(managed_server_name_base)
ds.setDynamicClusterSize(number_of_ms)
ds.setMaxDynamicClusterSize(number_of_ms)
ds.setCalculatedListenPorts(false)

cd("/")
dep=create("testwebapp", "AppDeployment")
dep.setTargets(jarray.array([cl],TargetMBean))
dep.setModuleType("war")
dep.setSourcePath("wlsdeploy/applications/testwebapp.war")

writeDomain(domain_path)
closeTemplate()

readDomain(domain_path)
cmo.setProductionModeEnabled(true)
updateDomain()
closeDomain()

exit()

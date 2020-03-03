# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

def getEnvVar(var):
    val = os.environ.get(var)
    if val == None:
        print "ERROR: Env var ", var, " not set."
        sys.exit(1)
    return val

def createWLDFSystemResource(sysName, sysTarget):
    cd('/')
    create(sysName, 'WLDFSystemResource')
    assign('WLDFSystemResource', sysName, 'Target', sysTarget)
    print 'WLDFSystemResource[%s] Created/Targeted to [%s]' % (sysName, sysTarget)

def createJMSSystemResource(sysResTarget):
    filestore = 'ClusterFileStore'
    jmsserver = 'ClusterJmsServer'
  
    jmsmodule = 'ClusterJmsSystemResource'
    subdeployment = 'ClusterSubDeployment'

    cf = 'ClusterConnectionFactory'
    urt = 'UniformReplicatedTestTopic'
  
    cd('/')
    create(filestore, 'FileStore')
    assign('FileStore', filestore, 'Target', sysResTarget)
  
    cd('/')
    create(jmsserver, 'JMSServer')
    assign('JMSServer', jmsserver, 'Target', sysResTarget)
    cd('/JMSServers/' + jmsserver)
    set('PersistentStore', filestore)
    print('JMS Server [%s] Created/Targeted ...' % jmsserver)
  
    cd('/')
    create(jmsmodule, 'JMSSystemResource')
    assign('JMSSystemResource', jmsmodule, 'Target', sysResTarget)
    print('JMSystemResource[%s] Created/Targeted to [%s]' % (jmsmodule, sysResTarget))
  
    cd('/JMSSystemResource/' + jmsmodule)
    create(subdeployment, 'SubDeployment')
    print('SubDeployment %s Created ...' % subdeployment)
    SubDep = jmsmodule + "." + subdeployment
    assign('JMSSystemResource.SubDeployment', SubDep, 'Target', sysResTarget)
    print 'SubDeployment [%s] Created/Targeted ...' % SubDep
  
    cd('/JMSSystemResource/' + jmsmodule + '/JmsResource/NO_NAME_0')
    myt = create(urt, 'UniformDistributedTopic')
    myt.setJNDIName('jms/' + urt)
    myt.setDefaultTargetingEnabled(true)
  
    cd('UniformDistributedTopic/' + urt)
    create('testparams', 'DeliveryFailureParams')
    cd('DeliveryFailureParams/NO_NAME_0')
    cmo.setExpirationPolicy('Log')
   
    cd('/JMSSystemResource/' + jmsmodule + '/JmsResource/NO_NAME_0')
    myc = create(cf, 'ConnectionFactory')
    myc.setJNDIName('jms/ClusterConnectionFactory')
    myc.setDefaultTargetingEnabled(true)
  
def createDataSource(dsName, dsJNDI, dsUrl, dsUser, dsPassword, dsTarget):
    cd('/')
    print 'create JDBCSystemResource'
    create(dsName, 'JDBCSystemResource')
    cd('/JDBCSystemResource/' + dsName)
    set('Target', dsTarget)
    cd('/JDBCSystemResource/' + dsName + '/JdbcResource/' + dsName)
    cmo.setName(dsName)
    cmo.setDatasourceType('GENERIC')

    print 'create JDBCDataSourceParams'
    cd('/JDBCSystemResource/' + dsName + '/JdbcResource/' + dsName)
    create('testDataSourceParams', 'JDBCDataSourceParams')
    cd('JDBCDataSourceParams/NO_NAME_0')
    set('JNDIName', java.lang.String(dsJNDI))
    set('GlobalTransactionsProtocol', java.lang.String('TwoPhaseCommit'))

    print 'create JDBCDriverParams'
    cd('/JDBCSystemResource/' + dsName + '/JdbcResource/' + dsName)
    create('testDriverParams', 'JDBCDriverParams')
    cd('JDBCDriverParams/NO_NAME_0')
    set('DriverName', 'com.mysql.cj.jdbc.Driver')
    set('URL', dsUrl)
    set('PasswordEncrypted', dsPassword)

    print 'create JDBCDriverParams Properties'
    create('testProperties', 'Properties')
    cd('Properties/NO_NAME_0')
    create('user', 'Property')
    cd('Property')
    cd('user')
    set('Value', dsUser)

    print 'create JDBCConnectionPoolParams'
    cd('/JDBCSystemResource/' + dsName + '/JdbcResource/' + dsName)
    create('testJdbcConnectionPoolParams', 'JDBCConnectionPoolParams')

    cd('JDBCConnectionPoolParams/NO_NAME_0')
    cmo.setTestTableName('SQL SELECT 1\r\n\r\n\r\n')
    set('InitialCapacity', 0)
    set('MinCapacity', 0)

# This python script is used to create a WebLogic domain

domain_uid                   = getEnvVar("DOMAIN_UID")
server_port                  = int(getEnvVar("MANAGED_SERVER_PORT"))
domain_path                  = getEnvVar("DOMAIN_HOME")
cluster_name                 = getEnvVar("CLUSTER_NAME")
admin_server_name            = getEnvVar("ADMIN_SERVER_NAME")
admin_server_name_svc        = getEnvVar("ADMIN_SERVER_NAME_SVC")
admin_port                   = int(getEnvVar("ADMIN_PORT"))
domain_name                  = getEnvVar("DOMAIN_NAME")
t3_channel_port              = int(getEnvVar("T3_CHANNEL_PORT"))
t3_public_address            = getEnvVar("T3_PUBLIC_ADDRESS")
number_of_ms                 = int(getEnvVar("CONFIGURED_MANAGED_SERVER_COUNT"))
cluster_type                 = getEnvVar("CLUSTER_TYPE")
managed_server_name_base     = getEnvVar("MANAGED_SERVER_NAME_BASE")
managed_server_name_base_svc = getEnvVar("MANAGED_SERVER_NAME_BASE_SVC")
domain_logs                  = getEnvVar("DOMAIN_LOGS_DIR")
script_dir                   = getEnvVar("CREATE_DOMAIN_SCRIPT_DIR")
production_mode_enabled      = getEnvVar("PRODUCTION_MODE_ENABLED")
jdbc_url                     = 'jdbc:mysql://HOSTNAME:NOPORT/'
jdbc_user                    = 'user'
jdbc_password                = 'password'

# Read the domain secrets from the common python file
execfile('%s/read-domain-secret.py' % script_dir)

print('domain_path        : [%s]' % domain_path);
print('domain_name        : [%s]' % domain_name);
print('admin_server_name  : [%s]' % admin_server_name);
print('admin_username     : [%s]' % admin_username);
print('admin_port         : [%s]' % admin_port);
print('cluster_name       : [%s]' % cluster_name);
print('server_port        : [%s]' % server_port);
# Open default domain template
# ============================
readTemplate("/u01/oracle/wlserver/common/templates/wls/wls.jar")

set('Name', domain_name)
setOption('DomainName', domain_name)

# Configure the Administration Server
# ===================================
cd('/Servers/AdminServer')
# Dont set listenaddress, introspector overrides automatically with config override
set('ListenPort', admin_port)
set('Name', admin_server_name)

create('T3Channel', 'NetworkAccessPoint')
cd('/Servers/%s/NetworkAccessPoints/T3Channel' % admin_server_name)
set('PublicPort', t3_channel_port)
set('PublicAddress', 'junkvalue')
# Dont set listenaddress, introspector overrides automatically with config override
set('ListenPort', t3_channel_port)

cd('/Servers/%s' % admin_server_name)
create(admin_server_name, 'Log')
cd('/Servers/%s/Log/%s' % (admin_server_name, admin_server_name))
# Give incorrect filelog, introspector overrides with config override
set('FileName', 'dirdoesnotexist')



# Set the admin user's username and password
# ==========================================
cd('/Security/%s/User/weblogic' % domain_name)
cmo.setName(admin_username)
cmo.setPassword(admin_password)

# Write the domain and close the domain template
# ==============================================
setOption('OverwriteDomain', 'true')

# Create a cluster/
# ======================
cd('/')
cl = create(cluster_name, 'Cluster')

if cluster_type == "CONFIGURED":

    # Create managed servers
    for index in range(0, number_of_ms):
        cd('/')

        msIndex = index + 1
        name = '%s%s' % (managed_server_name_base, msIndex)
        name_svc = '%s%s' % (managed_server_name_base_svc, msIndex)

        create(name, 'Server')
        cd('/Servers/%s/' % name)
        print('managed server name is %s' % name);
        set('ListenAddress', '%s-%s' % (domain_uid, name_svc))
        set('ListenPort', server_port)
        set('NumOfRetriesBeforeMSIMode', 0)
        set('RetryIntervalBeforeMSIMode', 1)
        set('Cluster', cluster_name)

else:
    print('Configuring Dynamic Cluster %s' % cluster_name)

    templateName = cluster_name + "-template"
    print('Creating Server Template: %s' % templateName)
    st1 = create(templateName, 'ServerTemplate')
    print('Done creating Server Template: %s' % templateName)
    cd('/ServerTemplates/%s' % templateName)
    cmo.setListenPort(server_port)
    cmo.setListenAddress('%s-%s${id}' % (domain_uid, managed_server_name_base_svc))
    cmo.setCluster(cl)
    print('Done setting attributes for Server Template: %s' % templateName);


    cd('/Clusters/%s' % cluster_name)
    create(cluster_name, 'DynamicServers')
    cd('DynamicServers/%s' % cluster_name)
    set('ServerTemplate', st1)
    set('ServerNamePrefix', managed_server_name_base)
    set('DynamicClusterSize', number_of_ms)
    set('MaxDynamicClusterSize', number_of_ms)
    set('CalculatedListenPorts', false)

    print('Done setting attributes for Dynamic Cluster: %s' % cluster_name);

createDataSource('JdbcTestDataSource-0', 'jdbc/JdbcTestDataSource-0', jdbc_url, jdbc_user, jdbc_password, admin_server_name)
createJMSSystemResource(cluster_name)
createWLDFSystemResource("WLDF-MODULE-0", admin_server_name)

# Write Domain
writeDomain(domain_path)
closeTemplate()
print 'Domain Created'

# Update Domain
readDomain(domain_path)
cd('/')
cmo.setProductionModeEnabled(false)
updateDomain()
closeDomain()
print 'Domain Updated'
print 'Done'

# Exit WLST
exit()

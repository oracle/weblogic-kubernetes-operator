# Copyright (c) 2020, 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import sys
import traceback

script_name = 'wlst-create-domain-onpv.py'

def usage():
  print 'Call script as: '
  print 'wlst.sh ' + script_name + ' -skipWLSModuleScanning -loadProperties domain.properties'

def createWLDFSystemResource(sysResName, sysResTarget):
    cd('/')
    create(sysResName, 'WLDFSystemResource')
    assign('WLDFSystemResource', sysResName, 'Target', sysResTarget)
    print 'WLDFSystemResource[%s] Targeted to [%s]' % (sysResName, sysResTarget)

def createJDBCSystemResource():
    cd('/')

    DSnam = 'TestDataSource'
    sysResTarget='cluster-1'

    print 'Creating DataSource[%s] on [%s]' % (DSnam, sysResTarget)
    create(DSnam, 'JDBCSystemResource')

    RESOURCE='/JDBCSystemResources/'+DSnam+'/JdbcResource/'+DSnam
    cd(RESOURCE)
    set('Name',DSnam)
    print 'DataSource[%s] created'  %(DSnam)
 
    #Setting JNDI name
    cd('/JDBCSystemResource/TestDataSource/JdbcResource/TestDataSource')
    create('dataSourceParams','JDBCDataSourceParams')
    cd('JDBCDataSourceParams/NO_NAME_0')
    set('JNDIName', ['jdbc/TestDataSource'])
    set('GlobalTransactionsProtocol','None')
    print 'JNDI name assigned ...'

    #Set Connection Pool specific parameters
    cd('/JDBCSystemResource/TestDataSource/JdbcResource/TestDataSource')
    create('connectionPoolParams','JDBCConnectionPoolParams')
    cd('JDBCConnectionPoolParams/NO_NAME_0')
    set('InitialCapacity',0)
    set('TestTableName','SQL SELECT 1 FROM DUAL')
    print 'JDBCConnectionPoolParams created ...'

    DBURL='jdbc:oracle:thin:@//localhost:1521/dummydb'
    DBDRV='oracle.jdbc.OracleDriver'
    DBUSR='scott'
    DBPASS='tiger'

    cd('/JDBCSystemResource/TestDataSource/JdbcResource/TestDataSource')
    create('driverParams', 'JDBCDriverParams')
    cd('JDBCDriverParams/NO_NAME_0')
    set('DriverName','oracle.jdbc.OracleDriver')
    set('URL','jdbc:oracle:thin:@//localhost:1521/dummydb')
    set('PasswordEncrypted','tiger')
    set('UseXADataSourceInterface', 'false')
    print 'JDBCDriverParams created ...'

    create('myProps','Properties')
    cd('Properties/NO_NAME_0')
    create('user', 'Property')
    cd('Property/user')
    set('Value', 'scott')
    print 'JDBCDriverParams Properties created'

    cd('/JDBCSystemResources/'+DSnam)
    #assign('JDBCSystemResource', sysResName, 'Target', sysResTarget)
    set('Target', sysResTarget)
    print 'JDBCSystemResource[%s] Targeted to [%s]' % (DSnam, sysResTarget)

def createJMSSystemResource(sysResTarget):
    filestore = 'ClusterFileStore'
    jmsserver = 'ClusterJmsServer'
  
    jmsmodule = 'ClusterJmsSystemResource'
    subdeployment = 'ClusterSubDeployment'

    cf = 'ClusterConnectionFactory'
    urt = 'UniformReplicatedTestTopic'
    udq = 'UniformDistributedTestQueue'
  
    cd('/')
    create(filestore, 'FileStore')
    cd('/FileStores/'+filestore)
    set('Directoy','JmsFileStores')
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

    myq = create(udq, 'UniformDistributedQueue')
    myq.setJNDIName('jms/' + udq)
    myq.setDefaultTargetingEnabled(true)
  
    cd('UniformDistributedTopic/' + urt)
    create('testparams', 'DeliveryFailureParams')
    cd('DeliveryFailureParams/NO_NAME_0')
    cmo.setExpirationPolicy('Log')

    cd('/JMSSystemResource/' + jmsmodule + '/JmsResource/NO_NAME_0')
    myc = create(cf, 'ConnectionFactory')
    myc.setJNDIName('jms/ClusterConnectionFactory')
    myc.setDefaultTargetingEnabled(true)

def create_domain():
  # Open default domain template
  # ============================
  print('Reading default domain template')
  readTemplate("/u01/oracle/wlserver/common/templates/wls/wls.jar")

  print('Set domain name')
  set('Name', domain_name)
  setOption('DomainName', domain_name)

  print('Configuring the Administration Server')
  cd('/Servers/AdminServer')
  set('ListenPort', int(admin_server_port))
  set('Name', admin_server_name)

  print('Configuring network access point')
  create('T3Channel', 'NetworkAccessPoint')
  cd('/Servers/%s/NetworkAccessPoints/T3Channel' % admin_server_name)
  set('PublicPort', int(admin_t3_channel_port))
  set('PublicAddress', admin_t3_public_address)
  set('ListenPort', int(admin_t3_channel_port))

  print('Set the admin user username and password')  
  cd('/Security/%s/User/weblogic' % domain_name)
  cmo.setName(admin_username)
  cmo.setPassword(admin_password)

  print('Set option to overwrite the domain  ')
  setOption('OverwriteDomain', 'true')


  print('Configuring a dynamic cluster %s ' % cluster_name)
  cd('/')
  cl=create(cluster_name, 'Cluster') 

  template_name = cluster_name + "-template"
  print('Creating server template: %s ' % template_name)
  st=create(template_name, 'ServerTemplate')
  print('Done creating server template: %s' % template_name)
  cd('/ServerTemplates/%s' % template_name)
  print('Set managed server port in template')
  cmo.setListenPort(int(managed_server_port))
  cmo.setCluster(cl)
  cmo.setResolveDNSName(true)

  template_channel_name = "ms-nap"
  print('Creating server template NAP: %s' % cluster_name + "-NAP")
  create(template_channel_name, 'NetworkAccessPoint')
  cd('NetworkAccessPoints/%s' % template_channel_name)
  #set('PublicPort', int(managed_server_port) + 10)
  set('ListenPort', int(managed_server_port) + 10)
  print('Done creating server template NAP: %s' % cluster_name + "-NAP")
  print('Done setting attributes for server template: %s' % template_name);

  cd('/Clusters/%s' % cluster_name)
  create(cluster_name, 'DynamicServers')
  cd('DynamicServers/%s' % cluster_name)
  set('ServerTemplate', st)
  set('ServerNamePrefix', managed_server_name_base)
  set('DynamicClusterSize', int(number_of_ms))
  set('MaxDynamicClusterSize', int(number_of_ms))
  set('CalculatedListenPorts', false)

  print('Done setting attributes for Dynamic Cluster: %s' % cluster_name);
   
  #create JMS and WLDF resources
  createJMSSystemResource(cluster_name)
  createWLDFSystemResource("WLDF-MODULE-0", cluster_name)
  #createJDBCSystemResource()

  print('Writing domain in disk %s' % domain_path + os.path.sep + domain_name)
  writeDomain(domain_path + os.path.sep + domain_name)
  closeTemplate()
  print('Domain Created')

  print('Update domain to enable production mode')
  readDomain(domain_path + os.path.sep + domain_name)
  cd('/')
  if production_mode_enabled == "true":
    cmo.setProductionModeEnabled(true)
    cd('/SecurityConfiguration/'+domain_name)
    secm=create('mySecureMode','SecureMode')
    cd('SecureMode/mySecureMode')
    set('SecureModeEnabled','false')
    #setOption('ServerStartMode', 'prod')
  else: 
    cmo.setProductionModeEnabled(false)
  updateDomain()
  closeDomain()
  print 'Domain Updated'


def main():
  try:
    #Create domain offline    
    create_domain()
  except NameError, e:
    print('Apparently properties not set.')
    print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])
    usage()
    exit(exitcode=1)
  except:
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

#call main()
main()
exit()

# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import os
import sys

import com.oracle.cie.domain.script.jython.WLSTException as WLSTException

class SOA12213Provisioner:

    jrfDone = 0;
    MACHINES = {
        'machine1' : {
            'NMType': 'SSL',
            'ListenAddress': 'localhost',
            'ListenPort': 5658
        }
    }


    MANAGED_SERVERS = []
    ADDL_MANAGED_SERVERS = []
    ADDL_CLUSTER = 'osb_cluster'
    ADDL_MANAGED_SERVER_BASENAME = 'osb_server'
    ADDL_MANAGED_SERVER_PORT = 9001


    JRF_12213_TEMPLATES = {
        'baseTemplate' : '@@ORACLE_HOME@@/wlserver/common/templates/wls/wls.jar',
        'extensionTemplates' : [
            '@@ORACLE_HOME@@/oracle_common/common/templates/wls/oracle.jrf_template.jar',
            '@@ORACLE_HOME@@/oracle_common/common/templates/wls/oracle.jrf.ws.async_template.jar',
            '@@ORACLE_HOME@@/oracle_common/common/templates/wls/oracle.wsmpm_template.jar',
            '@@ORACLE_HOME@@/oracle_common/common/templates/wls/oracle.ums_template.jar',
            '@@ORACLE_HOME@@/em/common/templates/wls/oracle.em_wls_template.jar'
        ],
        'serverGroupsToTarget' : [ 'JRF-MAN-SVR', 'WSMPM-MAN-SVR' ]
    }

    SOA_12213_TEMPLATES = {
        'extensionTemplates' : [
            '@@ORACLE_HOME@@/soa/common/templates/wls/oracle.soa_template.jar'
        ],
        'serverGroupsToTarget' : [ 'SOA-MGD-SVRS-ONLY' ]
    }

    SOA_ESS_12213_TEMPLATES = {
        'extensionTemplates' : [
            '@@ORACLE_HOME@@/soa/common/templates/wls/oracle.soa_template.jar',
            '@@ORACLE_HOME@@/oracle_common/common/templates/wls/oracle.ess.basic_template.jar',
            '@@ORACLE_HOME@@/em/common/templates/wls/oracle.em_ess_template.jar'
        ],
        'serverGroupsToTarget' : [ 'SOA-MGD-SVRS', 'ESS-MGD-SVRS' ]
    }

    OSB_12213_TEMPLATES = {
        'extensionTemplates' : [
            '@@ORACLE_HOME@@/osb/common/templates/wls/oracle.osb_template.jar'
        ],
        'serverGroupsToTarget' : [ 'OSB-MGD-SVRS-ONLY' ]
    }

    BPM_12213_TEMPLATES = {
        'extensionTemplates' : [
            '@@ORACLE_HOME@@/soa/common/templates/wls/oracle.bpm_template.jar'
        ],
        'serverGroupsToTarget' : [ 'SOA-MGD-SVRS-ONLY' ]
    }

    def __init__(self, oracleHome, javaHome, domainParentDir, adminListenPort, adminName, managedNameBase, managedServerPort, prodMode, managedCount, clusterName):
        self.oracleHome = self.validateDirectory(oracleHome)
        self.javaHome = self.validateDirectory(javaHome)
        self.domainParentDir = self.validateDirectory(domainParentDir, create=True)
        return



    def createSOADomain(self, domainName, user, password, db, dbPrefix, dbPassword, adminListenPort, adminName, managedNameBase, managedServerPort, prodMode, managedCount, clusterName, domainType,
                          exposeAdminT3Channel=None, t3ChannelPublicAddress=None, t3ChannelPort=None):
        domainHome = self.createBaseDomain(domainName, user, password, adminListenPort, adminName, managedNameBase, managedServerPort, prodMode, managedCount, clusterName, domainType)

        if domainType == "soa" or domainType == "soaosb":
                self.extendSoaDomain(domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort)

        if domainType == "soaess" or domainType == "soaessosb" :
                self.extendSoaEssDomain(domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort)

        if domainType == "osb" or domainType == "soaosb" or domainType == "soaessosb" :
                self.extendOsbDomain(domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort, domainType)

        if domainType == "bpm":
                self.extendBpmDomain(domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort)

    def createManagedServers(self, ms_count, managedNameBase, ms_port, cluster_name, ms_servers):
        # Create managed servers
        for index in range(0, ms_count):
            cd('/')
            msIndex = index+1
            cd('/')
            name = '%s%s' % (managedNameBase, msIndex)
            create(name, 'Server')
            cd('/Servers/%s/' % name )
            print('managed server name is %s' % name);
            set('ListenPort', ms_port)
            set('NumOfRetriesBeforeMSIMode', 0)
            set('RetryIntervalBeforeMSIMode', 1)
            set('Cluster', cluster_name)
            ms_servers.append(name)
        print ms_servers
        return ms_servers

    def createBaseDomain(self, domainName, user, password, adminListenPort, adminName, managedNameBase, managedServerPort, prodMode, managedCount, clusterName, domainType):
        baseTemplate = self.replaceTokens(self.JRF_12213_TEMPLATES['baseTemplate'])

        readTemplate(baseTemplate)
        setOption('DomainName', domainName)
        setOption('JavaHome', self.javaHome)
        if (prodMode == 'true'):
            setOption('ServerStartMode', 'prod')
        else:
            setOption('ServerStartMode', 'dev')
        set('Name', domainName)

        admin_port = int(adminListenPort)
        ms_port    = int(managedServerPort)
        ms_count   = int(managedCount)

        # Create Admin Server
        # =======================
        print 'Creating Admin Server...'
        cd('/Servers/AdminServer')
        #set('ListenAddress', '%s-%s' % (domain_uid, admin_server_name_svc))
        set('ListenPort', admin_port)
        set('Name', adminName)

        # Define the user password for weblogic
        # =====================================
        cd('/Security/' + domainName + '/User/weblogic')
        set('Name', user)
        set('Password', password)

        # Create a cluster
        # ======================
        print 'Creating cluster...'
        cd('/')
        cl=create(clusterName, 'Cluster')

        # Create managed servers
        self.MANAGED_SERVERS = self.createManagedServers(ms_count, managedNameBase, ms_port, clusterName, self.MANAGED_SERVERS)
        
        # Creating additional managed servers
        if  domainType == "soaosb" or domainType == "soaessosb":
            print 'Creating additional cluster... ' + self.ADDL_CLUSTER
            cd('/')
            cl=create(self.ADDL_CLUSTER, 'Cluster')

            # Creating  managed servers for additional cluster
            self.ADDL_MANAGED_SERVERS = self.createManagedServers(ms_count, self.ADDL_MANAGED_SERVER_BASENAME, self.ADDL_MANAGED_SERVER_PORT, self.ADDL_CLUSTER, self.ADDL_MANAGED_SERVERS)
            print 'Created managed Servers for additional cluster..... ' + self.ADDL_CLUSTER

        # Create Node Manager
        # =======================
        print 'Creating Node Managers...'
        for machine in self.MACHINES:
            cd('/')
            create(machine, 'Machine')
            cd('Machine/' + machine)
            create(machine, 'NodeManager')
            cd('NodeManager/' + machine)
            for param in self.MACHINES[machine]:
                set(param, self.MACHINES[machine][param])

        setOption('OverwriteDomain', 'true')
        domainHome = self.domainParentDir + '/' + domainName
        print 'Will create Base domain at ' + domainHome

        print 'Writing base domain...'
        writeDomain(domainHome)
        closeTemplate()
        print 'Base domain created at ' + domainHome
        return domainHome

    def readAndApplyJRFTemplates(self, domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort):
        print 'Extending domain at ' + domainHome
        print 'Database  ' + db
        readDomain(domainHome)
        setOption('AppDir', self.domainParentDir + '/applications')

        if self.jrfDone == 1:
            print 'JRF templates already applied '
            return

        print 'ExposeAdminT3Channel %s with %s:%s ' % (exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort)
        if 'true' == exposeAdminT3Channel:
            self.enable_admin_channel(t3ChannelPublicAddress, t3ChannelPort)

        self.applyJRFTemplates()
        print 'Extension Templates added'
        self.jrfDone = 1
        return

    def applyJRFTemplates(self):
        print 'Applying JRF templates...'
        for extensionTemplate in self.JRF_12213_TEMPLATES['extensionTemplates']:
            addTemplate(self.replaceTokens(extensionTemplate))
        return

    def applySOATemplates(self):
        print 'Applying SOA templates...'
        for extensionTemplate in self.SOA_12213_TEMPLATES['extensionTemplates']:
            addTemplate(self.replaceTokens(extensionTemplate))
        return

    def applySOAESSTemplates(self):
        print 'Applying SOA+ESS templates...'
        for extensionTemplate in self.SOA_ESS_12213_TEMPLATES['extensionTemplates']:
            addTemplate(self.replaceTokens(extensionTemplate))
        return

    def applyOSBTemplates(self):
        print 'Applying OSB templates...'
        for extensionTemplate in self.OSB_12213_TEMPLATES['extensionTemplates']:
            addTemplate(self.replaceTokens(extensionTemplate))
        return

    def applyBPMTemplates(self):
        print 'Applying BPM templates...'
        for extensionTemplate in self.BPM_12213_TEMPLATES['extensionTemplates']:
            addTemplate(self.replaceTokens(extensionTemplate))
        return

    def configureJDBCTemplates(self,db,dbPrefix,dbPassword):
        print 'Configuring the Service Table DataSource...'
        fmwDb = 'jdbc:oracle:thin:@' + db
        print 'fmwDatabase  ' + fmwDb
        cd('/JDBCSystemResource/LocalSvcTblDataSource/JdbcResource/LocalSvcTblDataSource')
        cd('JDBCDriverParams/NO_NAME_0')
        set('DriverName', 'oracle.jdbc.OracleDriver')
        set('URL', fmwDb)
        set('PasswordEncrypted', dbPassword)

        stbUser = dbPrefix + '_STB'
        cd('Properties/NO_NAME_0/Property/user')
        set('Value', stbUser)

        print 'Getting Database Defaults...'
        getDatabaseDefaults()
        return

    def configureXADataSources(self):
        cd('/JDBCSystemResources/SOADataSource/JdbcResource/SOADataSource')
        cd('JDBCDriverParams/NO_NAME_0')
        set('DriverName', 'oracle.jdbc.xa.client.OracleXADataSource')
        cd('/JDBCSystemResources/EDNDataSource/JdbcResource/EDNDataSource')
        cd('JDBCDriverParams/NO_NAME_0')
        set('DriverName', 'oracle.jdbc.xa.client.OracleXADataSource')
        cd('/JDBCSystemResources/OraSDPMDataSource/JdbcResource/OraSDPMDataSource')
        cd('JDBCDriverParams/NO_NAME_0')
        set('DriverName', 'oracle.jdbc.xa.client.OracleXADataSource')
        return

    def targetSOAServers(self,serverGroupsToTarget):
        print 'Targeting Server Groups...'
        cd('/')
        for managedName in self.MANAGED_SERVERS:
            setServerGroups(managedName, serverGroupsToTarget)
            print "Set CoherenceClusterSystemResource to defaultCoherenceCluster for server:" + managedName
            cd('/Servers/' + managedName)
            set('CoherenceClusterSystemResource', 'defaultCoherenceCluster')
        return

    def targetSOACluster(self):
        print 'Targeting Cluster ...'
        cd('/')
        print "Set CoherenceClusterSystemResource to defaultCoherenceCluster for cluster:" + clusterName
        cd('/Cluster/' + clusterName)
        set('CoherenceClusterSystemResource', 'defaultCoherenceCluster')
        return

    def targetSOAESSServers(self,serverGroupsToTarget):
        cd('/')
        for managedName in self.MANAGED_SERVERS:
            if not managedName == 'AdminServer':
                setServerGroups(managedName, serverGroupsToTarget)
                print "Set CoherenceClusterSystemResource to defaultCoherenceCluster for server:" + managedName
                cd('/Servers/' + managedName)
                set('CoherenceClusterSystemResource', 'defaultCoherenceCluster')
        return

    def targetSOAESSCluster(self):
        print 'Targeting Cluster ...'
        cd('/')
        print "Set CoherenceClusterSystemResource to defaultCoherenceCluster for cluster:" + clusterName
        cd('/Cluster/' + clusterName)
        set('CoherenceClusterSystemResource', 'defaultCoherenceCluster')
        return

    def targetOSBServers(self,serverGroupsToTarget, managedServers):
        print 'Targeting Server Groups...'
        cd('/')
        for managedName in managedServers:
            setServerGroups(managedName, serverGroupsToTarget)
            print "Set CoherenceClusterSystemResource to defaultCoherenceCluster for server:" + managedName
            cd('/Servers/' + managedName)
            set('CoherenceClusterSystemResource', 'defaultCoherenceCluster')
        return

    def targetOSBCluster(self, cluster_name):
        print 'Targeting Cluster ...'
        cd('/')
        print "Set CoherenceClusterSystemResource to defaultCoherenceCluster for cluster:" + cluster_name
        cd('/Cluster/' + cluster_name)
        set('CoherenceClusterSystemResource', 'defaultCoherenceCluster')
        return

    def extendSoaDomain(self, domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort):
        self.readAndApplyJRFTemplates(domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort)
        self.applySOATemplates()
        print 'Extension Templates added'
        
        if 'soa_server1' not in self.MANAGED_SERVERS:
            print 'INFO: deleting soa_server1'
            cd('/')
            delete('soa_server1','Server')
            print 'INFO: deleted soa_server1'

        self.configureJDBCTemplates(db,dbPrefix,dbPassword)
        self.configureXADataSources()

        print 'Targeting Server Groups...'
        serverGroupsToTarget = list(self.JRF_12213_TEMPLATES['serverGroupsToTarget'])
        serverGroupsToTarget.extend(self.SOA_12213_TEMPLATES['serverGroupsToTarget'])
        cd('/')
        self.targetSOAServers(serverGroupsToTarget)

        cd('/')
        self.targetSOACluster()

        print "Set WLS clusters as target of defaultCoherenceCluster:[" + clusterName + "]"
        cd('/CoherenceClusterSystemResource/defaultCoherenceCluster')
        set('Target', clusterName)

        print 'Preparing to update domain...'
        updateDomain()
        print 'Domain updated successfully'
        closeDomain()
        return

    def extendSoaEssDomain(self, domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort):
        self.readAndApplyJRFTemplates(domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort)
        self.applySOAESSTemplates()

        print 'Extension Templates added'
        
        if 'soa_server1' not in self.MANAGED_SERVERS:
            print 'INFO: deleting soa_server1'
            cd('/')
            delete('soa_server1','Server')
            print 'INFO: deleted soa_server1'

        print 'Deleting ess_server1'
        cd('/')
        delete('ess_server1', 'Server')
        print 'ess_server1 deleted'

        self.configureJDBCTemplates(db,dbPrefix,dbPassword)
        self.configureXADataSources()

        print 'Targeting Server Groups...'
        serverGroupsToTarget = list(self.JRF_12213_TEMPLATES['serverGroupsToTarget'])
        serverGroupsToTarget.extend(self.SOA_ESS_12213_TEMPLATES['serverGroupsToTarget'])
        cd('/')
        self.targetSOAESSServers(serverGroupsToTarget)

        cd('/')
        self.targetSOAESSCluster()

        print "Set WLS clusters as target of defaultCoherenceCluster:[" + clusterName + "]"
        cd('/CoherenceClusterSystemResource/defaultCoherenceCluster')
        set('Target', clusterName)

        print 'Preparing to update domain...'
        updateDomain()
        print 'Domain updated successfully'
        closeDomain()
        return

    def extendOsbDomain(self, domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort, domainType):
        self.readAndApplyJRFTemplates(domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort)
        self.applyOSBTemplates()
        print 'Extension Templates added'

        if 'osb_server1' not in self.MANAGED_SERVERS and 'osb_server1' not in self.ADDL_MANAGED_SERVERS:
            print 'INFO: deleting osb_server1'
            cd('/')
            delete('osb_server1','Server')
            print 'INFO: deleted osb_server1'

        self.configureJDBCTemplates(db,dbPrefix,dbPassword)
        cd('/JDBCSystemResources/SOADataSource/JdbcResource/SOADataSource')
        cd('JDBCDriverParams/NO_NAME_0')
        set('DriverName', 'oracle.jdbc.xa.client.OracleXADataSource')
        cd('/JDBCSystemResources/OraSDPMDataSource/JdbcResource/OraSDPMDataSource')
        cd('JDBCDriverParams/NO_NAME_0')
        set('DriverName', 'oracle.jdbc.xa.client.OracleXADataSource')

        print 'Targeting Server Groups...'
        serverGroupsToTarget = list(self.JRF_12213_TEMPLATES['serverGroupsToTarget'])
        serverGroupsToTarget.extend(self.OSB_12213_TEMPLATES['serverGroupsToTarget'])
        if domainType == "osb" :
            cd('/')
            self.targetOSBServers(serverGroupsToTarget, self.MANAGED_SERVERS)

            cd('/')
            self.targetOSBCluster(clusterName)

            print "Set WLS clusters as target of defaultCoherenceCluster:[" + clusterName + "]"
            cd('/CoherenceClusterSystemResource/defaultCoherenceCluster')
            set('Target', clusterName)

        if domainType == "soaosb" or domainType == "soaessosb" :
            cd('/')
            self.targetOSBServers(serverGroupsToTarget, self.ADDL_MANAGED_SERVERS)

            cd('/')
            self.targetOSBCluster(self.ADDL_CLUSTER)

            print "Set WLS clusters as target of defaultCoherenceCluster:[" + self.ADDL_CLUSTER + "]"
            cd('/CoherenceClusterSystemResource/defaultCoherenceCluster')
            set('Target', self.ADDL_CLUSTER)

        print 'Preparing to update domain...'
        updateDomain()
        print 'Domain updated successfully'
        closeDomain()
        return


    def extendBpmDomain(self, domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort):
        self.readAndApplyJRFTemplates(domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort)
        self.applyBPMTemplates()
        print 'Extension Templates added'

        self.configureJDBCTemplates(db,dbPrefix,dbPassword)
        self.configureXADataSources()

        print 'Targeting Server Groups...'
        serverGroupsToTarget = list(self.JRF_12213_TEMPLATES['serverGroupsToTarget'])
        serverGroupsToTarget.extend(self.BPM_12213_TEMPLATES['serverGroupsToTarget'])
        cd('/')
        self.targetSOAServers(serverGroupsToTarget)

        cd('/')
        self.targetSOACluster()

        print "Set WLS clusters as target of defaultCoherenceCluster:[" + clusterName + "]"
        cd('/CoherenceClusterSystemResource/defaultCoherenceCluster')
        set('Target', clusterName)
        print 'Preparing to update domain...'
        updateDomain()
        print 'Domain updated successfully'
        closeDomain()
        return


    ###########################################################################
    # Helper Methods                                                          #
    ###########################################################################

    def validateDirectory(self, dirName, create=False):
        directory = os.path.realpath(dirName)
        if not os.path.exists(directory):
            if create:
                os.makedirs(directory)
            else:
                message = 'Directory ' + directory + ' does not exist'
                raise WLSTException(message)
        elif not os.path.isdir(directory):
            message = 'Directory ' + directory + ' is not a directory'
            raise WLSTException(message)
        return self.fixupPath(directory)


    def fixupPath(self, path):
        result = path
        if path is not None:
            result = path.replace('\\', '/')
        return result


    def replaceTokens(self, path):
        result = path
        if path is not None:
            result = path.replace('@@ORACLE_HOME@@', oracleHome)
        return result

    def enable_admin_channel(self, admin_channel_address, admin_channel_port):
        if admin_channel_address == None or admin_channel_port == 'None':
            return
        cd('/')
        admin_server_name = get('AdminServerName')
        print('setting admin server t3channel for ' + admin_server_name)
        cd('/Servers/' + admin_server_name)
        create('T3Channel', 'NetworkAccessPoint')
        cd('/Servers/' + admin_server_name + '/NetworkAccessPoint/T3Channel')
        set('ListenPort', int(admin_channel_port))
        set('PublicPort', int(admin_channel_port))
        set('PublicAddress', admin_channel_address)


#############################
# Entry point to the script #
#############################

def usage():
    print sys.argv[0] + ' -oh <oracle_home> -jh <java_home> -parent <domain_parent_dir> -name <domain-name> ' + \
          '-user <domain-user> -password <domain-password> ' + \
          '-rcuDb <rcu-database> -rcuPrefix <rcu-prefix> -rcuSchemaPwd <rcu-schema-password> ' \
          '-adminListenPort <adminListenPort> -adminName <adminName> ' \
          '-managedNameBase <managedNameBase> -managedServerPort <managedServerPort> -prodMode <prodMode> ' \
          '-managedServerCount <managedCount> -clusterName <clusterName>' \
          '-domainType <soa|osb|bpm|soaosb|soaess|soaessosb>' \
          '-exposeAdminT3Channel <quoted true or false> -t3ChannelPublicAddress <address of the cluster> ' \
          '-t3ChannelPort <t3 channel port> '
    sys.exit(0)

# Uncomment for Debug only
#print str(sys.argv[0]) + " called with the following sys.argv array:"
#for index, arg in enumerate(sys.argv):
#    print "sys.argv[" + str(index) + "] = " + str(sys.argv[index])

if len(sys.argv) < 17:
    usage()

#oracleHome will be passed by command line parameter -oh.
oracleHome = None
#javaHome will be passed by command line parameter -jh.
javaHome = None
#domainParentDir will be passed by command line parameter -parent.
domainParentDir = None
#domainUser is hard-coded to weblogic. You can change to other name of your choice. Command line paramter -user.
domainUser = 'weblogic'
#domainPassword will be passed by Command line parameter -password.
domainPassword = None
#rcuDb will be passed by command line parameter -rcuDb.
rcuDb = None
#change rcuSchemaPrefix to your infra schema prefix. Command line parameter -rcuPrefix.
rcuSchemaPrefix = 'SOA1'
#change rcuSchemaPassword to your infra schema password. Command line parameter -rcuSchemaPwd.
rcuSchemaPassword = None
exposeAdminT3Channel = None
t3ChannelPort = None
t3ChannelPublicAddress = None

i = 1
while i < len(sys.argv):
    if sys.argv[i] == '-oh':
        oracleHome = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-jh':
        javaHome = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-parent':
        domainParentDir = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-name':
        domainName = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-user':
        domainUser = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-password':
        domainPassword = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-rcuDb':
        rcuDb = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-rcuPrefix':
        rcuSchemaPrefix = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-rcuSchemaPwd':
        rcuSchemaPassword = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-adminListenPort':
        adminListenPort = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-adminName':
        adminName = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-managedNameBase':
        managedNameBase = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-managedServerPort':
        managedServerPort = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-prodMode':
        prodMode = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-managedServerCount':
        managedCount = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-clusterName':
        clusterName = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-domainType':
        domainType = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-t3ChannelPublicAddress':
        t3ChannelPublicAddress = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-t3ChannelPort':
        t3ChannelPort = sys.argv[i + 1]
        i += 2
    elif sys.argv[i] == '-exposeAdminT3Channel':
        exposeAdminT3Channel = sys.argv[i + 1]
        i += 2
    else:
        print 'Unexpected argument switch at position ' + str(i) + ': ' + str(sys.argv[i])
        usage()
        sys.exit(1)

provisioner = SOA12213Provisioner(oracleHome, javaHome, domainParentDir, adminListenPort, adminName, managedNameBase, managedServerPort, prodMode, managedCount, clusterName)
provisioner.createSOADomain(domainName, domainUser, domainPassword, rcuDb, rcuSchemaPrefix, rcuSchemaPassword, adminListenPort, adminName, managedNameBase, managedServerPort, prodMode, managedCount, clusterName, domainType, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort)

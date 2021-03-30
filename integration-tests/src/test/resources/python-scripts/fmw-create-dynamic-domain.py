# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import os
import sys
import com.oracle.cie.domain.script.jython.WLSTException as WLSTException

class Infra12213Provisioner:

    MACHINES = {
        'machine1' : {
            'NMType': 'SSL',
            'ListenAddress': 'localhost',
            'ListenPort': 5658
        }
    }

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

    def __init__(self, oracleHome, javaHome, domainParentDir, adminListenPort, adminName, managedNameBase, managedServerPort, prodMode, managedCount, clusterName):
        self.oracleHome = self.validateDirectory(oracleHome)
        self.javaHome = self.validateDirectory(javaHome)
        self.domainParentDir = self.validateDirectory(domainParentDir, create=True)
        return

    def createInfraDomain(self, domainName, user, password, db, dbPrefix, dbPassword, adminListenPort, adminName,
                          managedNameBase, managedServerPort, prodMode, managedCount, clusterName,
                          exposeAdminT3Channel=None, t3ChannelPublicAddress=None, t3ChannelPort=None):
        domainHome = self.createBaseDomain(domainName, user, password, adminListenPort, adminName, managedNameBase,
                                           managedServerPort, prodMode, managedCount, clusterName)
        self.extendDomain(domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort, managedCount)

    def createBaseDomain(self, domainName, user, password, adminListenPort, adminName, managedNameBase, managedServerPort, prodMode, managedCount, clusterName):
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
        set('ListenPort', admin_port)
        set('Name', adminName)
        set('WeblogicPluginEnabled', true)

        # Define the user password for weblogic
        # =====================================
        cd('/Security/' + domainName + '/User/weblogic')
        set('Name', user)
        set('Password', password)

        print('Set the admin user username and password')
        cd('/Security/%s/User/weblogic' % domainName)
        cmo.setName(user)
        cmo.setPassword(password)

        print('Set option to overwrite the domain  ')
        setOption('OverwriteDomain', 'true')

        # Create a dynamic cluster
        # ==========================
        print 'Creating a dynamic cluster...'
        cd('/')
        cl=create(clusterName, 'Cluster')

        template_name = clusterName + "-template"
        print('Creating server template: %s ' % template_name)
        st=create(template_name, 'ServerTemplate')
        print('Done creating server template: %s' % template_name)
        cd('/ServerTemplates/%s' % template_name)
        print('Set managed server port in template')
        cmo.setListenPort(ms_port)
        cmo.setCluster(cl)
        cmo.setResolveDNSName(true)

        template_channel_name = "ms-nap"
        print('Creating server template NAP: %s' % clusterName + "-NAP")
        create(template_channel_name, 'NetworkAccessPoint')
        cd('NetworkAccessPoints/%s' % template_channel_name)
        set('ListenPort', ms_port + 10)
        print('Done creating server template NAP: %s' % clusterName + "-NAP")
        print('Done setting attributes for server template: %s' % template_name);

        cd('/Clusters/%s' % clusterName)
        set('WeblogicPluginEnabled', true)
        set('FrontendHost', clusterName)
        create(clusterName, 'DynamicServers')
        cd('DynamicServers/%s' % clusterName)
        set('ServerTemplate', st)
        set('ServerNamePrefix', managedNameBase)
        set('DynamicClusterSize', ms_count)
        set('MaxDynamicClusterSize', ms_count)
        set('CalculatedListenPorts', false)

        print('Done setting attributes for Dynamic Cluster: %s' % clusterName);

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

    def extendDomain(self, domainHome, db, dbPrefix, dbPassword, exposeAdminT3Channel, t3ChannelPublicAddress,
                     t3ChannelPort, managedCount):
        print 'Extending domain at ' + domainHome
        print 'Database  ' + db
        readDomain(domainHome)
        setOption('AppDir', self.domainParentDir + '/applications')

        print 'ExposeAdminT3Channel %s with %s:%s ' % (exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort)
        if 'true' == exposeAdminT3Channel:
            self.enable_admin_channel(t3ChannelPublicAddress, t3ChannelPort)

        print 'Applying JRF templates...'
        for extensionTemplate in self.JRF_12213_TEMPLATES['extensionTemplates']:
            addTemplate(self.replaceTokens(extensionTemplate))

        print 'Extension Templates added'

        print 'Configuring the Service Table DataSource...'
        fmwDb = 'jdbc:oracle:thin:@' + db
        print 'fmwDatabase  ' + fmwDb
        cd('/JDBCSystemResource/LocalSvcTblDataSource/JdbcResource/LocalSvcTblDataSource')
        cd('JDBCDriverParams/NO_NAME_0')
        set('DriverName', 'oracle.jdbc.OracleDriver')
        set('URL', fmwDb)
        set('PasswordEncrypted', dbPassword)
        print 'dbPassword  ' + dbPassword

        stbUser = dbPrefix + '_STB'
        cd('Properties/NO_NAME_0/Property/user')
        set('Value', stbUser)
        print 'dbPrefix  ' + dbPrefix

        print 'Getting Database Defaults...'
        getDatabaseDefaults()

        # Set server group
        # =======================
        cd('/')
        serverGroup = ["WSMPM-DYN-CLUSTER", "WSM-CACHE-DYN-CLUSTER"]
        setServerGroups(clusterName, serverGroup)

        # reset DynamicClusterSize since WSM erase the setting belore
        ms_count   = int(managedCount)
        cd('/Clusters/%s' % clusterName)
        cd('DynamicServers/%s' % clusterName)
        set('DynamicClusterSize', ms_count)

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

script_name = 'jrf-create-dynamic-domain.py'

def usage():
  print 'Call script as: '
  print 'wlst.sh ' + script_name + ' -skipWLSModuleScanning -loadProperties domain.properties'

provisioner = Infra12213Provisioner(oracleHome, javaHome, domainParentDir, adminListenPort, adminName, managedNameBase, managedServerPort, prodMode, managedCount, clusterName)
provisioner.createInfraDomain(domainName, domainUser, domainPassword, rcuDb, rcuSchemaPrefix, rcuSchemaPassword,
                              adminListenPort, adminName, managedNameBase, managedServerPort, prodMode, managedCount,
                              clusterName, exposeAdminT3Channel, t3ChannelPublicAddress, t3ChannelPort)

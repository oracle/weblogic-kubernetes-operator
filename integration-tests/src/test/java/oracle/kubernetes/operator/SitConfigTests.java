/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package oracle.kubernetes.operator;

import java.util.*;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.net.MalformedURLException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import weblogic.diagnostics.descriptor.WLDFHarvestedTypeBean;
import weblogic.diagnostics.descriptor.WLDFInstrumentationMonitorBean;
import weblogic.diagnostics.descriptor.WLDFResourceBean;
import weblogic.j2ee.descriptor.wl.JDBCConnectionPoolParamsBean;
import weblogic.j2ee.descriptor.wl.JDBCDataSourceBean;
import weblogic.j2ee.descriptor.wl.JDBCDriverParamsBean;
import weblogic.j2ee.descriptor.wl.JMSBean;
import weblogic.j2ee.descriptor.wl.UniformDistributedTopicBean;

import javax.management.MalformedObjectNameException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.sql.DataSource;

import weblogic.management.configuration.*;
import weblogic.management.jmx.MBeanServerInvocationHandler;
import weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean;
import weblogic.management.mbeanservers.runtime.RuntimeServiceMBean;
import weblogic.management.mbeanservers.edit.ConfigurationManagerMBean;
import weblogic.management.mbeanservers.edit.EditServiceMBean;
import weblogic.management.runtime.DomainRuntimeMBean;
import weblogic.management.runtime.ServerRuntimeMBean;

public class SitConfigTests {

    private MBeanServerConnection domainRuntimeMbs, editMbs, runtimeMbs;
    private JMXConnector jmxConnector;
    private static ObjectName service;
    private DomainRuntimeMBean domainRuntimeMBean;
    private DomainRuntimeServiceMBean domainServiceMBean;
    private RuntimeServiceMBean runtimeServiceMBean;
    private ServerRuntimeMBean serverRuntime;
    private EditServiceMBean editServiceMBean;

    private DomainMBean editdomain, editTree;
    private ConfigurationManagerMBean cfgMgr;
    private static final String JNDI = "/jndi/";

    private final String adminHost;
    private final String adminPort;
    private final String adminUser;
    private final String adminPassword;

    public static void main(String args[]) throws Exception {
        String adminHost = args[0];
        String adminPort = args[1];
        String adminUser = args[2];
        String adminPassword = args[3];
        String testName = args[4];

        SitConfigTests test = new SitConfigTests(adminHost, adminPort, adminUser, adminPassword);
        //SitConfigTests test = new SitConfigTests("slc11vjg", "30051", "weblogic", "welcome1");

        ServerRuntimeMBean runtimeMBean = test.runtimeServiceMBean.getServerRuntime();
        println("Sitconfig State:" + runtimeMBean.isInSitConfigState());

        if (testName.equals("testCustomSitConfigOverridesForDomain")) {
            test.verifyDebugFlagJMXCore(true);
            test.verifyDebugFlagServerLifeCycle(true);
            test.verifyMaxMessageSize(78787878);
            test.verifyConnectTimeout(12);
            test.verifyRestartMax(22);
            test.verifyT3ChannelPublicAddress(adminHost);
            test.verifyT3ChannelPublicPort(30051);
        }
        if (testName.equals("testCustomSitConfigOverridesForJdbc")) {
            test.testSystemResourcesJDBCAttributeChange("JdbcTestDataSource-0");
        }

        if (testName.equals("testSystemResourcesJDBCAttributeChangeSecret")) {
            test.testSystemResourcesJDBCAttributeChangeSecret("JdbcTestDataSource-1");
        }
        if (testName.equals("testCustomSitConfigOverridesForJms")) {
            test.testSystemResourcesJMSAttributeChange();
        }
        if (testName.equals("testCustomSitConfigOverridesForWldf")) {
            test.testSystemResourcesWLDFAttributeAdd();
        }
    }

    /*    public SitConfigTests(){
    
    }*/
    public SitConfigTests(String adminHost, String adminPort, String adminUser, String adminPassword) throws Exception {
        this.adminHost = adminHost;
        this.adminPort = adminPort;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
        createConnections();
    }

    private void createConnections() throws Exception {
        try {
            runtimeMbs = lookupMBeanServerConnection(adminHost, adminPort, adminUser, adminPassword, RuntimeServiceMBean.MBEANSERVER_JNDI_NAME);
            domainRuntimeMbs = lookupMBeanServerConnection(adminHost, adminPort, adminUser, adminPassword, DomainRuntimeServiceMBean.MBEANSERVER_JNDI_NAME);
            editMbs = lookupMBeanServerConnection(adminHost, adminPort, adminUser, adminPassword, EditServiceMBean.MBEANSERVER_JNDI_NAME);

            ObjectName runtimeserviceObjectName = new ObjectName(RuntimeServiceMBean.OBJECT_NAME);
            runtimeServiceMBean = (RuntimeServiceMBean) MBeanServerInvocationHandler.newProxyInstance(runtimeMbs, runtimeserviceObjectName);

            ObjectName domainServiceObjectName = new ObjectName(DomainRuntimeServiceMBean.OBJECT_NAME);
            domainServiceMBean = (DomainRuntimeServiceMBean) MBeanServerInvocationHandler.newProxyInstance(domainRuntimeMbs, domainServiceObjectName);

            ObjectName editserviceObjectName = new ObjectName(EditServiceMBean.OBJECT_NAME);
            editServiceMBean = (EditServiceMBean) MBeanServerInvocationHandler.newProxyInstance(editMbs, editserviceObjectName);
        } catch (MalformedObjectNameException ex) {
            Logger.getLogger(SitConfigTests.class.getName()).log(Level.SEVERE, null, ex);
            throw new AssertionError(ex.getMessage());
        }
    }

    private MBeanServerConnection lookupMBeanServerConnection(String host, String adminPort, String user, String adminPassword, String jndiName) throws MalformedURLException, IOException, Exception {
        JMXServiceURL serviceURL;
        MBeanServerConnection mBeanServerConnection;

        println("Host: " + adminHost + " Port: " + adminPort + " adminUser: " + adminUser + " adminPassword :" + adminPassword);
        String protocol = "t3";
        Integer portInteger = Integer.valueOf(adminPort);
        int port = portInteger;
        HashMap h = new HashMap();
        h.put(Context.SECURITY_PRINCIPAL, adminUser);
        h.put(Context.SECURITY_CREDENTIALS, adminPassword);
        h.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");
        h.put("jmx.remote.x.request.waiting.timeout", new Long(10000));
        serviceURL = new JMXServiceURL(protocol, adminHost, port, "/jndi/" + jndiName);
        println("Making mbean server connection with url");
        println(serviceURL.toString());
        jmxConnector = JMXConnectorFactory.connect(serviceURL, h);
        mBeanServerConnection = jmxConnector.getMBeanServerConnection();
        if (mBeanServerConnection == null) {
            throw new Exception("MBean server connection is null");
        }
        return mBeanServerConnection;
    }

    protected void verifyDebugFlagJMXCore(boolean expectedValue) {
        ServerMBean serverMBean = getServerMBean();
        ServerDebugMBean serverDebugMBean = serverMBean.getServerDebug();
        boolean debugFlag = serverDebugMBean.getDebugJMXCore();
        assert expectedValue == debugFlag : "Didn't get the expected value " + expectedValue + " for JMX core debug flag";
    }

    protected void verifyDebugFlagServerLifeCycle(boolean expectedValue) {
        ServerMBean serverMBean = getServerMBean();
        ServerDebugMBean serverDebugMBean = serverMBean.getServerDebug();
        boolean debugFlag = serverDebugMBean.getDebugServerLifeCycle();
        assert expectedValue == debugFlag : "Didn't get the expected value " + expectedValue + " for server life cycle debug flag";
    }

    protected void verifyConnectTimeout(int expectedValue) {
        ServerMBean serverMBean = getServerMBean();
        int got = serverMBean.getConnectTimeout();
        assert expectedValue == got : "Didn't get the expected value " + expectedValue + " for ConnectTimeout";
    }

    protected void verifyRestartMax(int expectedValue) {
        ServerMBean serverMBean = getServerMBean();
        int got = serverMBean.getRestartMax();
        assert expectedValue == got : "Didn't get the expected value " + expectedValue + " for RestartMax";
    }

    protected void verifyMaxMessageSize(int expectedValue) {
        ServerMBean serverMBean = getServerMBean();
        int got = serverMBean.getMaxMessageSize();
        assert expectedValue == got : "Didn't get the expected value " + expectedValue + " for MaxMessageSize";
    }

    protected void verifyT3ChannelPublicAddress(String expectedValue) {
        boolean got = false;
        ServerMBean serverMBean = getServerMBean();
        NetworkAccessPointMBean[] networkAccessPoints = serverMBean.getNetworkAccessPoints();
        for (NetworkAccessPointMBean networkAccessPoint : networkAccessPoints) {
            if (networkAccessPoint.getName().equals("T3Channel")) {
                assert expectedValue.equals(networkAccessPoint.getPublicAddress()) : "Didn't get the expected value " + expectedValue + " for T3Channel public address";
            }
        }
    }

    protected void verifyT3ChannelPublicPort(int expectedValue) {
        boolean got = false;
        ServerMBean serverMBean = getServerMBean();
        NetworkAccessPointMBean[] networkAccessPoints = serverMBean.getNetworkAccessPoints();
        for (NetworkAccessPointMBean networkAccessPoint : networkAccessPoints) {
            if (networkAccessPoint.getName().equals("T3Channel")) {
                assert expectedValue == networkAccessPoint.getPublicPort() : "Didn't get the expected value " + expectedValue + " for T3Channel public address";
            }
        }
    }

    protected void verifyT3ChannelMaxMessageSize(int expectedValue) {
        boolean got = false;
        ServerMBean serverMBean = getServerMBean();
        NetworkAccessPointMBean[] networkAccessPoints = serverMBean.getNetworkAccessPoints();
        for (NetworkAccessPointMBean networkAccessPoint : networkAccessPoints) {
            if (networkAccessPoint.getName().equals("T3Channel")) {
                assert expectedValue == networkAccessPoint.getMaxMessageSize() : "Didn't get the expected value " + expectedValue + " for MaxMessageSize";
            }
        }
    }

    private ServerMBean getServerMBean() {
        ServerMBean serverMBean = runtimeServiceMBean.getServerConfiguration();
        println("ServerMBean: " + serverMBean);

        return serverMBean;
    }

    public void testSystemResourcesJDBCAttributeChange(String jdbcResourceName) {
        int initialCapacity = 2;
        int maxCapacity = 12;
        boolean testConnectionsonReserve = true;
        int harvestMaxCount = 7;
        int inactiveConnectionTimeoutSeconds = 120;
        String dsUrl = "jdbc:oracle:thin:@//slc11smq.us.oracle.com:1583/w18ys12c.us.oracle.com";
        String dbInstanceName = "w18ys12c";
        String dbHostName = "slc11smq";

        println("Verifying the configuration changes made by sit config file");

        JDBCSystemResourceMBean jdbcSystemResource = getJDBCSystemResource(jdbcResourceName);
        JDBCDataSourceBean jdbcDataSourceBean = jdbcSystemResource.getJDBCResource();

        // Assert the connection pool properties
        JDBCConnectionPoolParamsBean jcpb = jdbcDataSourceBean.getJDBCConnectionPoolParams();
        assert initialCapacity == jcpb.getInitialCapacity() : "Didn't get the expected value " + initialCapacity + " for initialCapacity";
        assert maxCapacity == jcpb.getMaxCapacity() : "Didn't get the expected value " + maxCapacity + " for maxCapacity";
        assert testConnectionsonReserve == jcpb.isTestConnectionsOnReserve() : "Didn't get the expected value " + testConnectionsonReserve + " for testConnectionsonReserve";
        assert inactiveConnectionTimeoutSeconds == jcpb.getInactiveConnectionTimeoutSeconds() : "Didn't get the expected value " + inactiveConnectionTimeoutSeconds + " for inactiveConnectionTimeoutSeconds";
        //testMaxPoolSize(resourceName, jcpb.getMaxCapacity());

        // Assert the jdbc driver param properties
        JDBCDriverParamsBean jdbcDriverParams = jdbcDataSourceBean.getJDBCDriverParams();
        //assert dsUrl.equals(jdbcDriverParams.getUrl()) : "Didn't get the expected url for datasource " + dsUrl;

        //println("jdbcDriverParams.getPasswordEncrypted()" + jdbcDriverParams.getPasswordEncrypted()[0]);
        // Assert datasource user property
        /*
        JDBCPropertiesBean properties = jdbcDriverParams.getProperties();
        JDBCPropertyBean[] properties1 = properties.getProperties();
        for (JDBCPropertyBean jDBCPropertyBean : properties1) {
            println(jDBCPropertyBean.getName());
        }
         */
        // Assert datasource is working with overiridden value
        DataSource dataSource = getDataSource("jdbc/" + jdbcResourceName);

        try {
            Connection connection = dataSource.getConnection();
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM v$instance");
            while (rs.next()) {
                println("INSTANCE_NAME:" + rs.getString("INSTANCE_NAME"));
                println("HOST_NAME:" + rs.getString("HOST_NAME"));
            }
            /*
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            println(colCount + "");
            for (int col = 1; col <= colCount; col++) {
                System.out.print(meta.getColumnName(col) + " ");
            }
            println("");
            while (rs.next()) {
                println("INSTANCE_NAME:" + rs.getString("INSTANCE_NAME"));
                println("HOST_NAME:" + rs.getString("HOST_NAME"));
                for (int col = 1; col <= colCount; col++) {
                    Object value = rs.getObject(col);
                    if (value != null) {
                        System.out.print("@" + value.toString() + "@ ");
                    }
                }
                println("");
            }
             */
        } catch (SQLException ex) {
            Logger.getLogger(SitConfigTests.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void testSystemResourcesJDBCAttributeChangeSecret(String jdbcResourceName) {
        String dsUrl = "jdbc:oracle:thin:@//slc11smq.us.oracle.com:1583/w18ys12c.us.oracle.com";
        String dbInstanceName = "w18ys12c";
        String dbHostName = "slc11smq";

        println("Verifying the configuration changes made by sit config file");

        JDBCSystemResourceMBean jdbcSystemResource = getJDBCSystemResource(jdbcResourceName);
        JDBCDataSourceBean jdbcDataSourceBean = jdbcSystemResource.getJDBCResource();

        // Assert the jdbc driver param properties
        JDBCDriverParamsBean jdbcDriverParams = jdbcDataSourceBean.getJDBCDriverParams();
        assert dsUrl.equals(jdbcDriverParams.getUrl()) : "Didn't get the expected url for datasource " + dsUrl;

        // Assert datasource is working with overiridden value
        DataSource dataSource = getDataSource("jdbc/" + jdbcResourceName);

        try {
            Connection connection = dataSource.getConnection();
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM v$instance");
            while (rs.next()) {
                println("INSTANCE_NAME:" + rs.getString("INSTANCE_NAME"));
                println("HOST_NAME:" + rs.getString("HOST_NAME"));
            }
        } catch (SQLException ex) {
            Logger.getLogger(SitConfigTests.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    protected JDBCSystemResourceMBean getJDBCSystemResource(String resourceName) {
        println("Looking up the jdbc system module..." + resourceName);
        DomainMBean domain = runtimeServiceMBean.getDomainConfiguration();
        JDBCSystemResourceMBean jdbcSysRes = domain.lookupJDBCSystemResource(resourceName);
        assert jdbcSysRes != null : " JDBC resource is null";
        return jdbcSysRes;
    }

    protected DataSource getDataSource(String dataSourceName) {
        DataSource ds = null;
        try {
            Hashtable h = new Hashtable();
            h.put(Context.SECURITY_PRINCIPAL, adminUser);
            h.put(Context.SECURITY_CREDENTIALS, adminPassword);
            h.put(Context.INITIAL_CONTEXT_FACTORY, "weblogic.jndi.WLInitialContextFactory");
            h.put(Context.PROVIDER_URL, "t3://" + adminHost + ":" + Integer.valueOf(adminPort));
            Context ctx = new InitialContext(h);
            System.out.println("Looking up datasource :" + dataSourceName);
            ds = (javax.sql.DataSource) ctx.lookup(dataSourceName);
        } catch (NamingException ex) {
            Logger.getLogger(SitConfigTests.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ds;
    }

    public void testSystemResourcesJMSAttributeChange() {
        String jmsModuleName = "ClusterJmsSystemResource";
        String topicName = "UniformReplicatedTestTopic";
        String expirationPolicy_exp = "Discard";
        int redeliveryLimit_exp = 20;

        JMSSystemResourceMBean jmsModule = getJMSSystemModule(jmsModuleName);
        JMSBean jmsResource = jmsModule.getJMSResource();
        UniformDistributedTopicBean uniformDistributedTopic = jmsResource.lookupUniformDistributedTopic(topicName);

        println("Verifying the configuration changes made by sit config file");
        assert expirationPolicy_exp.equals(uniformDistributedTopic.getDeliveryFailureParams().getExpirationPolicy()) : " Didn't get the expected Expiration Policy" + expirationPolicy_exp;
        assert redeliveryLimit_exp == uniformDistributedTopic.getDeliveryFailureParams().getRedeliveryLimit() : " Didn't get the expected redelivery limit " + redeliveryLimit_exp;
    }

    protected JMSSystemResourceMBean getJMSSystemModule(String resourceName) {
        println("Looking up the jms system module..." + resourceName);
        DomainMBean domain = runtimeServiceMBean.getDomainConfiguration();
        JMSSystemResourceMBean jmsSysRes = domain.lookupJMSSystemResource(resourceName);
        assert jmsSysRes != null : " JMS resource is null";
        return jmsSysRes;
    }

    public void testSystemResourcesWLDFAttributeAdd() {
        String sitconfig_file = "testSystemResourcesWLDFAttributeAdd.xml";
        final String WLDF_MOD_NAME_0 = "WLDF-MODULE-0";

        //verify the changed properties
        println("Verifying the configuration changes made by sit config file");
        String resourceName = WLDF_MOD_NAME_0;
        WLDFSystemResourceMBean wldfSystemModule = getWLDFSystemModule(resourceName);
        WLDFResourceBean wldfRes = wldfSystemModule.getWLDFResource();
        String monitors_exp[] = {"Connector_After_Inbound", "Connector_Around_Outbound", "Connector_Around_Tx", "Connector_Around_Work", "Connector_Before_Inbound"};

        WLDFInstrumentationMonitorBean[] wldfInstrumentationMonitors = wldfRes.getInstrumentation().getWLDFInstrumentationMonitors();
        String monitors_got[] = new String[wldfInstrumentationMonitors.length];
        for (int i = 0; i < wldfInstrumentationMonitors.length; i++) {
            monitors_got[i] = wldfInstrumentationMonitors[i].getName();
            println("Monitor got :" + wldfInstrumentationMonitors[i].getName());
        }
        Arrays.sort(monitors_exp);
        Arrays.sort(monitors_got);
        assert Arrays.equals(monitors_exp, monitors_got) : "Didn't get all the configured monitors, expected " + Arrays.toString(monitors_exp) + " but got " + Arrays.toString(monitors_got);

        String harvested_types_exp[] = {"weblogic.management.runtime.JDBCServiceRuntimeMBean", "weblogic.management.runtime.ServerRuntimeMBean"};
        WLDFHarvestedTypeBean[] harvestedTypes = wldfRes.getHarvester().getHarvestedTypes();
        String harvested_types_got[] = new String[harvestedTypes.length];
        for (int i = 0; i < harvested_types_got.length; i++) {
            harvested_types_got[i] = harvestedTypes[i].getName();
            println("Harvester type :" + harvestedTypes[i].getName());
        }
        Arrays.sort(harvested_types_exp);
        Arrays.sort(harvested_types_got);
        assert Arrays.equals(harvested_types_exp, harvested_types_got) == true : "Didn't get all the configured harvesters, expected " + Arrays.toString(harvested_types_exp) + " but got " + Arrays.toString(harvested_types_got);

        WLDFHarvestedTypeBean lookupHarvestedType1 = wldfRes.getHarvester().lookupHarvestedType(harvested_types_exp[0]);
        assert lookupHarvestedType1.getName().equals(harvested_types_exp[0]) : "Harvested type name doesn't match";
        assert lookupHarvestedType1.getNamespace().equals("DomainRuntime") : "Harvested type name space doesn't match";
        WLDFHarvestedTypeBean lookupHarvestedType2 = wldfRes.getHarvester().lookupHarvestedType(harvested_types_exp[1]);
        assert lookupHarvestedType2.getName().equals(harvested_types_exp[1]) : "Harvested type name doesn't match";
        assert lookupHarvestedType2.getNamespace().equals("DomainRuntime") : "Harvested type name space doesn't match";
    }

    protected WLDFSystemResourceMBean getWLDFSystemModule(String resourceName) {
        println("Looking up the wldf system module..." + resourceName);
        DomainMBean domain = runtimeServiceMBean.getDomainConfiguration();
        WLDFSystemResourceMBean wldfResource = domain.lookupWLDFSystemResource(resourceName);
        assert wldfResource != null : "WLDF resource is null";
        return wldfResource;
    }

    protected static void println(String msg) {
        System.out.println("> " + msg);
    }

}

// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator;

import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import weblogic.diagnostics.descriptor.WLDFHarvestedTypeBean;
import weblogic.diagnostics.descriptor.WLDFInstrumentationMonitorBean;
import weblogic.diagnostics.descriptor.WLDFResourceBean;
import weblogic.j2ee.descriptor.wl.JDBCConnectionPoolParamsBean;
import weblogic.j2ee.descriptor.wl.JDBCDataSourceBean;
import weblogic.j2ee.descriptor.wl.JDBCDriverParamsBean;
import weblogic.j2ee.descriptor.wl.JMSBean;
import weblogic.j2ee.descriptor.wl.UniformDistributedTopicBean;
import weblogic.management.configuration.DomainMBean;
import weblogic.management.configuration.JDBCSystemResourceMBean;
import weblogic.management.configuration.JMSSystemResourceMBean;
import weblogic.management.configuration.NetworkAccessPointMBean;
import weblogic.management.configuration.ServerDebugMBean;
import weblogic.management.configuration.ServerMBean;
import weblogic.management.configuration.WLDFSystemResourceMBean;
import weblogic.management.jmx.MBeanServerInvocationHandler;
import weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean;
import weblogic.management.mbeanservers.runtime.RuntimeServiceMBean;
import weblogic.management.runtime.ServerRuntimeMBean;

/**
 * The configuration overrides tests are implemented in this class. Since the
 * weblogic-kubernetes-operator doesn't have access to weblogic.jar, this test class is transferred
 * to administration server pod running in the kubernetes cluster, built, run and returns with exit
 * status.
 *
 * <p>This test class implements a main method since it is run outside of the JUnit tests in
 * weblogic-kubernetes-operator integration test suite.
 *
 * <p>The JUnit wrapper test class oracle.kubernetes.operator.ITSitConfig running in functional
 * integration test suite calls this Main class to run individual override verification tests. The
 * class exits with exception if any of the asserts fail and exit status 1, or gracefully exits with
 * status 0 when all of the asserts pass.
 *
 * <p>The class takes a minimum of four arguments - administration server host, administration
 * server port, administration server user name and administration server password. When
 * testCustomSitConfigOverridesForJdbc test is run it expects an additional argument JDBC URL.
 */
public class SitConfigTests {

  private MBeanServerConnection runtimeMbs;
  private JMXConnector jmxConnector;
  private static ObjectName service;
  private RuntimeServiceMBean runtimeServiceMBean;
  private ServerRuntimeMBean serverRuntime;
  private static final String JNDI = "/jndi/";

  private final String adminHost;
  private final String adminPort;
  private final String adminUser;
  private final String adminPassword;

  /**
   * Main method to create the SitConfigTests object and run the configuration override tests. To
   * run the configuration override tests pass the parameters described below with test method name
   * to run a particular test.
   *
   * @param args should include a minimum of these values - administration server host,
   *     administration server port, administration server user name and administration server
   *     password. When testCustomSitConfigOverridesForJdbc test is run it expects an additional
   *     argument JDBC URL.
   * @throws Exception when the test assertions fail
   */
  public static void main(String args[]) throws Exception {

    String adminHost = args[0];
    String adminPort = args[1];
    String adminUser = args[2];
    String adminPassword = args[3];
    String testName = args[4];

    SitConfigTests test = new SitConfigTests(adminHost, adminPort, adminUser, adminPassword);

    ServerRuntimeMBean runtimeMBean = test.runtimeServiceMBean.getServerRuntime();
    println("Sitconfig State:" + runtimeMBean.isInSitConfigState());

    if (testName.equals("testCustomSitConfigOverridesForDomain")) {
      // the values passed to these verify methods are the attribute values overrrideen in the
      // config.xml. These are just randomly chosen attributes and values to override
      test.verifyDebugFlagJMXCore(true);
      test.verifyDebugFlagServerLifeCycle(true);
      test.verifyMaxMessageSize(78787878);
      test.verifyConnectTimeout(120);
      test.verifyRestartMax(5);
      test.verifyT3ChannelPublicAddress(adminHost);
      test.verifyT3ChannelPublicPort(30091);
    }

    if (testName.equals("testCustomSitConfigOverridesForJdbc")) {
      String JDBC_URL = args[5];
      test.testSystemResourcesJDBCAttributeChange("JdbcTestDataSource-0", JDBC_URL);
    }

    if (testName.equals("testCustomSitConfigOverridesForJms")) {
      test.testSystemResourcesJMSAttributeChange();
    }

    if (testName.equals("testCustomSitConfigOverridesForWldf")) {
      test.testSystemResourcesWLDFAttributeAdd();
    }
  }

  /**
   * Create connections to the MBean servers when the class is instantiated.
   *
   * @param adminHost - administration server t3 public address
   * @param adminPort - administration server t3 public port
   * @param adminUser - administration server user name
   * @param adminPassword - - administration server password
   * @throws Exception when connection cannot be created for reasons like incorrect administration
   *     server name, port , user name , password or administration server not running
   */
  public SitConfigTests(String adminHost, String adminPort, String adminUser, String adminPassword)
      throws Exception {
    this.adminHost = adminHost;
    this.adminPort = adminPort;
    this.adminUser = adminUser;
    this.adminPassword = adminPassword;
    createConnections();
  }

  /**
   * This method creates connection to the RuntimeMBean server, looks up the RuntimeService needed
   * by the tests.
   *
   * @throws Exception when it cannot connect to the administration server, obtain references to
   *     RuntimeService MBean
   */
  private void createConnections() throws Exception {
    runtimeMbs =
        lookupMBeanServerConnection(
            adminHost,
            adminPort,
            adminUser,
            adminPassword,
            RuntimeServiceMBean.MBEANSERVER_JNDI_NAME);
    ObjectName runtimeserviceObjectName = new ObjectName(RuntimeServiceMBean.OBJECT_NAME);
    runtimeServiceMBean =
        (RuntimeServiceMBean)
            MBeanServerInvocationHandler.newProxyInstance(runtimeMbs, runtimeserviceObjectName);
    ObjectName domainServiceObjectName = new ObjectName(DomainRuntimeServiceMBean.OBJECT_NAME);
  }

  /**
   * Method for creating a connection to a specific Mbean Server. Accepts the following parameters
   * and creates a connection using T3 protocol.
   *
   * @param host - administration server hostname
   * @param adminPort - administration server T3 channel port
   * @param user - - administration server user name
   * @param adminPassword - - administration server password
   * @param jndiName - jndi name of the MBean server
   * @return MBeanServerConnection - MBean server connection created
   * @throws MalformedURLException - throws MalformedURLException when T3 URL is wrong
   * @throws IOException - throws IOException when it cannot connect the MBean server
   * @throws Exception - throws Exception when created MBeanserver connection is null
   */
  private MBeanServerConnection lookupMBeanServerConnection(
      String host, String adminPort, String user, String adminPassword, String jndiName)
      throws MalformedURLException, IOException, Exception {
    JMXServiceURL serviceURL;
    MBeanServerConnection mBeanServerConnection;
    println(
        "Host: "
            + adminHost
            + " Port: "
            + adminPort
            + " adminUser: "
            + adminUser
            + " adminPassword :"
            + adminPassword);
    String protocol = "t3";
    Integer portInteger = Integer.valueOf(adminPort);
    int port = portInteger;
    HashMap h = new HashMap();
    h.put(Context.SECURITY_PRINCIPAL, adminUser);
    h.put(Context.SECURITY_CREDENTIALS, adminPassword);
    h.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");
    // Add a timeout of 10 secs if the server doesnot return after a method invocation, this is not
    // needed by the tests but to exit in case of a hang.
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

  /**
   * A utility method to check if the Debug JMX Core flag in the ServerConfig tree matches with the
   * expected value, a boolean value set in the configuration override file config.xml. Uses Java
   * assertions to verify if both the values match.
   *
   * @param expectedValue - boolean value to be checked in the debug-jmx-core attribute in
   *     ServerMBean in ServerConfig tree.
   */
  protected void verifyDebugFlagJMXCore(boolean expectedValue) {
    ServerMBean serverMBean = getServerMBean();
    ServerDebugMBean serverDebugMBean = serverMBean.getServerDebug();
    boolean debugFlag = serverDebugMBean.getDebugJMXCore();
    assert expectedValue == debugFlag
        : "Didn't get the expected value " + expectedValue + " for JMX core debug flag";
  }

  /**
   * A utility method to check if the Debug Server Life Cycle flag in the ServerConfig tree matches
   * with the expected value, a boolean value set in the configuration override file config.xml.Uses
   * Java assertions to verify if both the values match.
   *
   * @param expectedValue - boolean value to be checked in the debug-server-life-cycle attribute in
   *     ServerMBean in ServerConfig MBean tree
   */
  protected void verifyDebugFlagServerLifeCycle(boolean expectedValue) {
    ServerMBean serverMBean = getServerMBean();
    ServerDebugMBean serverDebugMBean = serverMBean.getServerDebug();
    boolean debugFlag = serverDebugMBean.getDebugServerLifeCycle();
    assert expectedValue == debugFlag
        : "Didn't get the expected value " + expectedValue + " for server life cycle debug flag";
  }

  /**
   * A utility method to check if the connect-timeout in the ServerConfig tree matches with the
   * expected value, a integer value set in the configuration override file config.xml. Uses Java
   * assertions to verify if both the values match.
   *
   * @param expectedValue - integer value to be checked in the connect-timeout attribute in
   *     ServerMBean in ServerConfig tree.
   */
  protected void verifyConnectTimeout(int expectedValue) {
    ServerMBean serverMBean = getServerMBean();
    int got = serverMBean.getConnectTimeout();
    assert expectedValue == got
        : "Didn't get the expected value " + expectedValue + " for ConnectTimeout";
  }

  /**
   * A utility method to check if the restart-max in the ServerConfig tree matches with the expected
   * value, a integer value set in the configuration override file config.xml.Uses Java assertions
   * to verify if both the values match.
   *
   * @param expectedValue - integer value to be checked in the restart-max attribute in ServerMBean
   *     in ServerConfig tree.
   */
  protected void verifyRestartMax(int expectedValue) {
    ServerMBean serverMBean = getServerMBean();
    int got = serverMBean.getRestartMax();
    assert expectedValue == got
        : "Didn't get the expected value " + expectedValue + " for RestartMax";
  }

  /**
   * A utility method to check if the max-message-size in the ServerConfig tree matches with the
   * expected value, a integer value set in the configuration override file config.xml. Uses Java
   * assertions to verify if both the values match.
   *
   * @param expectedValue - integer value to be checked in the max-message-size attribute in
   *     ServerMBean in ServerConfig tree
   */
  protected void verifyMaxMessageSize(int expectedValue) {
    ServerMBean serverMBean = getServerMBean();
    int got = serverMBean.getMaxMessageSize();
    assert expectedValue == got
        : "Didn't get the expected value " + expectedValue + " for MaxMessageSize";
  }

  /**
   * A utility method to check if the Network Access Point public-address in the ServerConfig tree
   * matches with the expected value, a string value set in the configuration override file
   * config.xml. Uses Java assertions to verify if both the values match.
   *
   * @param expectedValue - string value to be checked in the public-address attribute in
   *     ServerMBean in ServerConfig tree.
   */
  protected void verifyT3ChannelPublicAddress(String expectedValue) {
    boolean got = false;
    ServerMBean serverMBean = getServerMBean();
    NetworkAccessPointMBean[] networkAccessPoints = serverMBean.getNetworkAccessPoints();

    for (NetworkAccessPointMBean networkAccessPoint : networkAccessPoints) {
      if (networkAccessPoint.getName().equals("T3Channel")) {
        assert expectedValue.equals(networkAccessPoint.getPublicAddress())
            : "Didn't get the expected value " + expectedValue + " for T3Channel public address";
      }
    }
  }

  /**
   * A utility method to check if the Network Access Point public-port in the ServerConfig tree
   * matches with the expected value. Uses Java assertions to verify if both the values match.
   *
   * @param expectedValue - integer value to be checked in the public-port attribute in ServerMBean
   *     in ServerConfig tree
   */
  protected void verifyT3ChannelPublicPort(int expectedValue) {
    boolean got = false;
    ServerMBean serverMBean = getServerMBean();
    NetworkAccessPointMBean[] networkAccessPoints = serverMBean.getNetworkAccessPoints();

    for (NetworkAccessPointMBean networkAccessPoint : networkAccessPoints) {
      if (networkAccessPoint.getName().equals("T3Channel")) {
        assert expectedValue == networkAccessPoint.getPublicPort()
            : "Didn't get the expected value " + expectedValue + " for T3Channel public address";
      }
    }
  }

  /**
   * Looks up the ServerMBean from RuntimeServiceMBean.
   *
   * @return the ServerMBean reference
   */
  private ServerMBean getServerMBean() {
    ServerMBean serverMBean = runtimeServiceMBean.getServerConfiguration();
    println("ServerMBean: " + serverMBean);

    return serverMBean;
  }

  /**
   * Test that verifies the initialCapacity, maxCapacity, testConnectionsonReserve, harvestMaxCount
   * and inactiveConnectionTimeoutSeconds on the given JDBC resource with the overridden values used
   * in the jdbc-JdbcTestDataSource-0.xml JDBC resource. The values for these attributes are
   * randomly chosen and used in the jdbc-JdbcTestDataSource-0.xml. The test expects the values
   * overridden in jdbc-JdbcTestDataSource-0.xml to match with attributes from ServerConfig MBean
   * JDBCSystemResourceMBean to match.
   *
   * @param jdbcResourceName - name of the JDBC resource overridden in jdbc-JdbcTestDataSource-0.xml
   * @param dsUrl - data source URL of the MySQL database overridden in
   *     jdbc-JdbcTestDataSource-0.xml
   */
  public void testSystemResourcesJDBCAttributeChange(String jdbcResourceName, String dsUrl) {
    int initialCapacity = 2;
    int maxCapacity = 12;
    boolean testConnectionsonReserve = true;
    int harvestMaxCount = 7;
    int inactiveConnectionTimeoutSeconds = 120;

    println("Verifying the configuration changes made by sit config file");

    JDBCSystemResourceMBean jdbcSystemResource = getJDBCSystemResource(jdbcResourceName);
    JDBCDataSourceBean jdbcDataSourceBean = jdbcSystemResource.getJDBCResource();

    // Assert the connection pool properties
    JDBCConnectionPoolParamsBean jcpb = jdbcDataSourceBean.getJDBCConnectionPoolParams();
    println("initialCapacity:" + jcpb.getInitialCapacity());
    assert initialCapacity == jcpb.getInitialCapacity()
        : "Didn't get the expected value " + initialCapacity + " for initialCapacity";
    println("maxCapacity:" + jcpb.getMaxCapacity());
    assert maxCapacity == jcpb.getMaxCapacity()
        : "Didn't get the expected value " + maxCapacity + " for maxCapacity";
    println("testConnectionsonReserve:" + jcpb.isTestConnectionsOnReserve());
    assert testConnectionsonReserve == jcpb.isTestConnectionsOnReserve()
        : "Didn't get the expected value "
            + testConnectionsonReserve
            + " for testConnectionsonReserve";
    println("inactiveConnectionTimeoutSeconds:" + jcpb.getInactiveConnectionTimeoutSeconds());
    assert inactiveConnectionTimeoutSeconds == jcpb.getInactiveConnectionTimeoutSeconds()
        : "Didn't get the expected value "
            + inactiveConnectionTimeoutSeconds
            + " for inactiveConnectionTimeoutSeconds";

    // Assert the jdbc driver param properties
    JDBCDriverParamsBean jdbcDriverParams = jdbcDataSourceBean.getJDBCDriverParams();
    println("Data Source URL:" + jdbcDriverParams.getUrl());
    assert dsUrl.equals(jdbcDriverParams.getUrl())
        : "Didn't get the expected url for datasource " + dsUrl;

    // Assert datasource is working with overiridden JDBC URL value
    DataSource dataSource = getDataSource("jdbc/" + jdbcResourceName);

    // Create DDL statement and execute it to verify the datasource actually works.
    try {
      Connection connection = dataSource.getConnection();
      Statement stmt = connection.createStatement();
      int createSchema = stmt.executeUpdate("CREATE SCHEMA `mysqldb` ;");
      println("create schema returned " + createSchema);
      int createTable =
          stmt.executeUpdate(
              "CREATE TABLE IF NOT EXISTS mysqldb.testtable (title VARCHAR(255) NOT NULL,description TEXT)ENGINE=INNODB;");
      println("create table returned " + createTable);
      assert createSchema == 1 : "create schema failed";
      assert createTable == 0 : "create table failed";
    } catch (SQLException ex) {
      Logger.getLogger(SitConfigTests.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Returns the JDBCSystemResourceMBean from the domain configuration matching with JDBC resource
   * name.
   *
   * @param resourceName - name of the JDBC data source to lookup in domain configuration
   * @return - the JDBC data source mbean
   */
  protected JDBCSystemResourceMBean getJDBCSystemResource(String resourceName) {
    println("Looking up the jdbc system module..." + resourceName);
    DomainMBean domain = runtimeServiceMBean.getDomainConfiguration();
    JDBCSystemResourceMBean jdbcSysRes = domain.lookupJDBCSystemResource(resourceName);
    assert jdbcSysRes != null : " JDBC resource is null";
    return jdbcSysRes;
  }

  /**
   * Returns the JDBC datasource matching the JDBC data source name.
   *
   * @param dataSourceName - JDBC datasource to lookup
   * @return JDBC DataSource from the domain configuration
   */
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
    } catch (Exception ex) {
      Logger.getLogger(SitConfigTests.class.getName()).log(Level.SEVERE, null, ex);
    }
    return ds;
  }

  /**
   * The testSystemResourcesJMSAttributeChange verifies the ClusterJmsSystemResource system module
   * resource UniformReplicatedTestTopic Delivery Failure Parameters overridden in
   * jms-ClusterJmsSystemResource.xml attributes redelivery-limit and expiration-policy. The test
   * expects the overridden values to match against the domain configuration.
   */
  public void testSystemResourcesJMSAttributeChange() {
    String jmsModuleName = "ClusterJmsSystemResource";
    String topicName = "UniformReplicatedTestTopic";
    String expirationPolicy_exp = "Discard";
    int redeliveryLimit_exp = 20;

    JMSSystemResourceMBean jmsModule = getJMSSystemModule(jmsModuleName);
    JMSBean jmsResource = jmsModule.getJMSResource();
    UniformDistributedTopicBean uniformDistributedTopic =
        jmsResource.lookupUniformDistributedTopic(topicName);

    println("Verifying the configuration changes made by sit config file");
    assert expirationPolicy_exp.equals(
            uniformDistributedTopic.getDeliveryFailureParams().getExpirationPolicy())
        : " Didn't get the expected Expiration Policy" + expirationPolicy_exp;
    assert redeliveryLimit_exp
            == uniformDistributedTopic.getDeliveryFailureParams().getRedeliveryLimit()
        : " Didn't get the expected redelivery limit " + redeliveryLimit_exp;
  }

  /**
   * Returns the JMSSystemResourceMBean from domain configuration matching the JMS resource name.
   *
   * @param resourceName - name of the JMS system module to lookup
   * @return JMSSystemResourceMBean of the JMS module
   */
  protected JMSSystemResourceMBean getJMSSystemModule(String resourceName) {
    println("Looking up the jms system module..." + resourceName);
    DomainMBean domain = runtimeServiceMBean.getDomainConfiguration();
    JMSSystemResourceMBean jmsSysRes = domain.lookupJMSSystemResource(resourceName);
    assert jmsSysRes != null : " JMS resource is null";
    return jmsSysRes;
  }

  /**
   * The testSystemResourcesWLDFAttributeAdd test verifies the WLDF-MODULE-0 WLDF system module
   * overridden in diagnostics-WLDF-MODULE-0.xml. The added elements wldf-instrumentation-monitor
   * harvested-type are expected to show up in the domain configuration.
   */
  public void testSystemResourcesWLDFAttributeAdd() {
    String sitconfig_file = "testSystemResourcesWLDFAttributeAdd.xml";
    final String WLDF_MOD_NAME_0 = "WLDF-MODULE-0";

    // verify the changed properties
    println("Verifying the configuration changes made by sit config file");
    String resourceName = WLDF_MOD_NAME_0;
    WLDFSystemResourceMBean wldfSystemModule = getWLDFSystemModule(resourceName);
    WLDFResourceBean wldfRes = wldfSystemModule.getWLDFResource();
    String monitors_exp[] = {
      "Connector_After_Inbound",
      "Connector_Around_Outbound",
      "Connector_Around_Tx",
      "Connector_Around_Work",
      "Connector_Before_Inbound"
    };

    WLDFInstrumentationMonitorBean[] wldfInstrumentationMonitors =
        wldfRes.getInstrumentation().getWLDFInstrumentationMonitors();
    String monitors_got[] = new String[wldfInstrumentationMonitors.length];
    for (int i = 0; i < wldfInstrumentationMonitors.length; i++) {
      monitors_got[i] = wldfInstrumentationMonitors[i].getName();
      println("Monitor got :" + wldfInstrumentationMonitors[i].getName());
    }
    Arrays.sort(monitors_exp);
    Arrays.sort(monitors_got);
    assert Arrays.equals(monitors_exp, monitors_got)
        : "Didn't get all the configured monitors, expected "
            + Arrays.toString(monitors_exp)
            + " but got "
            + Arrays.toString(monitors_got);

    String harvested_types_exp[] = {
      "weblogic.management.runtime.JDBCServiceRuntimeMBean",
      "weblogic.management.runtime.ServerRuntimeMBean"
    };
    WLDFHarvestedTypeBean[] harvestedTypes = wldfRes.getHarvester().getHarvestedTypes();
    String harvested_types_got[] = new String[harvestedTypes.length];
    for (int i = 0; i < harvested_types_got.length; i++) {
      harvested_types_got[i] = harvestedTypes[i].getName();
      println("Harvester type :" + harvestedTypes[i].getName());
    }
    Arrays.sort(harvested_types_exp);
    Arrays.sort(harvested_types_got);
    assert Arrays.equals(harvested_types_exp, harvested_types_got) == true
        : "Didn't get all the configured harvesters, expected "
            + Arrays.toString(harvested_types_exp)
            + " but got "
            + Arrays.toString(harvested_types_got);

    WLDFHarvestedTypeBean lookupHarvestedType1 =
        wldfRes.getHarvester().lookupHarvestedType(harvested_types_exp[0]);
    assert lookupHarvestedType1.getName().equals(harvested_types_exp[0])
        : "Harvested type name doesn't match";
    assert lookupHarvestedType1.getNamespace().equals("DomainRuntime")
        : "Harvested type name space doesn't match";
    WLDFHarvestedTypeBean lookupHarvestedType2 =
        wldfRes.getHarvester().lookupHarvestedType(harvested_types_exp[1]);
    assert lookupHarvestedType2.getName().equals(harvested_types_exp[1])
        : "Harvested type name doesn't match";
    assert lookupHarvestedType2.getNamespace().equals("DomainRuntime")
        : "Harvested type name space doesn't match";
  }

  /**
   * Returns the WLDFSystemResourceMBean from domain configuration matching the WLDF resource name.
   *
   * @param resourceName - name of the WLDF system module to lookup
   * @return the WLDFSystemResourceMBean
   */
  protected WLDFSystemResourceMBean getWLDFSystemModule(String resourceName) {
    println("Looking up the wldf system module..." + resourceName);
    DomainMBean domain = runtimeServiceMBean.getDomainConfiguration();
    WLDFSystemResourceMBean wldfResource = domain.lookupWLDFSystemResource(resourceName);
    assert wldfResource != null : "WLDF resource is null";
    return wldfResource;
  }

  /**
   * Prints message in standard out. Short name method to System.out.println.
   *
   * @param msg - message to print
   */
  protected static void println(String msg) {
    System.out.println("> " + msg);
  }
}

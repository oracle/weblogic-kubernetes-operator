// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Random;
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
import weblogic.j2ee.descriptor.wl.JDBCDataSourceParamsBean;
import weblogic.j2ee.descriptor.wl.JDBCDriverParamsBean;
import weblogic.j2ee.descriptor.wl.JMSBean;
import weblogic.j2ee.descriptor.wl.UniformDistributedTopicBean;
import weblogic.management.configuration.DomainMBean;
import weblogic.management.configuration.JDBCSystemResourceMBean;
import weblogic.management.configuration.JMSSystemResourceMBean;
import weblogic.management.configuration.NetworkAccessPointMBean;
import weblogic.management.configuration.ServerDebugMBean;
import weblogic.management.configuration.ServerMBean;
import weblogic.management.configuration.ShutdownClassMBean;
import weblogic.management.configuration.StartupClassMBean;
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
 * <p>The JUnit wrapper test class oracle.kubernetes.operator.ItSitConfig running in functional
 * integration test suite calls this Main class to run individual override verification tests. The
 * class exits with exception if any of the asserts fail and exit status 1, or gracefully exits with
 * status 0 when all of the asserts pass.
 *
 * <p>The class takes a minimum of four arguments - administration server host, administration
 * server port, administration server user name and administration server password. When
 * testCustomSitConfigOverridesForJdbc test is run it expects an additional argument JDBC URL.
 */
public class SitConfigTests {

  private static final String JNDI = "/jndi/";
  private static ObjectName service;
  private final String adminUser;
  private final String adminPassword;
  private MBeanServerConnection runtimeMbs;
  private JMXConnector jmxConnector;
  private RuntimeServiceMBean runtimeServiceMBean;
  private ServerRuntimeMBean serverRuntime;
  private String adminHost;
  private String adminPort;

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
  public static void main(String[] args) throws Exception {

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
      test.verifyDebugFlagJmxCore(true);
      test.verifyDebugFlagServerLifeCycle(true);
      test.verifyMaxMessageSize(78787878);
      test.verifyConnectTimeout(120);
      test.verifyRestartMax(5);
      test.verifyT3ChannelPublicAddress(adminHost);
      test.verifyT3ChannelPublicPort(30091);
    }

    if (testName.equals("testCustomSitConfigOverridesForDomainMS")) {
      // the values passed to these verify methods are the attribute values overrrideen in the
      // config.xml. These are just randomly chosen attributes and values to override
      String serverName = args[5];
      test.connectToManagedServer(serverName);
      test.verifyMaxMessageSize(77777777);
    }

    if (testName.equals("testCustomSitConfigOverridesForJdbc")) {
      String jdbcUrl = args[5];
      test.testSystemResourcesJdbcAttributeChange("JdbcTestDataSource-0", jdbcUrl);
    }

    if (testName.equals("testCustomSitConfigOverridesForJms")) {
      test.testSystemResourcesJmsAttributeChange();
    }

    if (testName.equals("testCustomSitConfigOverridesForWldf")) {
      test.testSystemResourcesWldfAttributeAdd();
    }

    if (testName.equals("testOverrideJDBCResourceAfterDomainStart")) {
      test.testOverrideJdbcResourceAfterDomainStart("JdbcTestDataSource-1");
    }

    if (testName.equals("testConfigOverrideAfterDomainStartup")) {
      test.testConfigOverrideAfterDomainStartup();
    }

    if (testName.equals("testOverrideJDBCResourceWithNewSecret")) {
      String jdbcUrl = args[5];
      test.testSystemResourcesJdbcAttributeChange("JdbcTestDataSource-0", jdbcUrl);
    }
  }

  /**
   * Prints message in standard out. Short name method to System.out.println.
   *
   * @param msg - message to print
   */
  protected static void println(String msg) {
    System.out.println("> " + msg);
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
    println(
        "Host: "
            + adminHost
            + " Port: "
            + adminPort
            + " adminUser: "
            + adminUser
            + " adminPassword :"
            + adminPassword);
    final String protocol = "t3";
    Integer portInteger = Integer.valueOf(adminPort);
    int port = portInteger;
    HashMap h = new HashMap();
    h.put(Context.SECURITY_PRINCIPAL, adminUser);
    h.put(Context.SECURITY_CREDENTIALS, adminPassword);
    h.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");
    // Add a timeout of 10 secs if the server doesnot return after a method invocation, this is not
    // needed by the tests but to exit in case of a hang.
    h.put("jmx.remote.x.request.waiting.timeout", new Long(10000));
    JMXServiceURL serviceUrl = new JMXServiceURL(protocol, adminHost, port, "/jndi/" + jndiName);
    println("Making mbean server connection with url");
    println(serviceUrl.toString());
    jmxConnector = JMXConnectorFactory.connect(serviceUrl, h);
    MBeanServerConnection mbeanServerConnection = jmxConnector.getMBeanServerConnection();
    if (mbeanServerConnection == null) {
      throw new Exception("MBean server connection is null");
    }

    return mbeanServerConnection;
  }

  /**
   * A utility method to check if the Debug JMX Core flag in the ServerConfig tree matches with the
   * expected value, a boolean value set in the configuration override file config.xml. Uses Java
   * assertions to verify if both the values match.
   *
   * @param expectedValue - boolean value to be checked in the debug-jmx-core attribute in
   *     ServerMBean in ServerConfig tree.
   */
  protected void verifyDebugFlagJmxCore(boolean expectedValue) {
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

  /** Test to verify the startup and shutdown class names after domain startup. */
  private void testConfigOverrideAfterDomainStartup() {
    String startupClassName = "AddedStartupClassOne";
    StartupClassMBean[] startupClasses =
        runtimeServiceMBean.getDomainConfiguration().getStartupClasses();
    assert startupClasses.length > 0;
    for (StartupClassMBean startupClasse : startupClasses) {
      assert startupClasse.getName().equals("StartupClass-0")
          : "Startup class name is not StartupClass-0";
      assert startupClasse.getClassName().equals(startupClassName)
          : "Startup class name is not AddedStartupClassOne";
      assert startupClasse.getDeploymentOrder() == 5 : "Startup class deployment order is not 5";
      assert !startupClasse.getFailureIsFatal() : "FailureIsFatal is not false";
      assert startupClasse.getLoadBeforeAppDeployments() : "LoadBeforeAppDeployments is not true";
    }

    ShutdownClassMBean[] shutdownClasses =
        runtimeServiceMBean.getDomainConfiguration().getShutdownClasses();
    assert shutdownClasses.length > 0;
    for (ShutdownClassMBean shutdownClasse : shutdownClasses) {
      assert shutdownClasse.getName().equals("ShutdownClass-0")
          : "Shutdown class name is not ShutdownClass-0";
      assert shutdownClasse.getClassName().equals("AddedShutdownClassOne")
          : "Shutdownclassname is not AddedShutdownClassOne";
      assert shutdownClasse.getDeploymentOrder() == 6 : "Deployment order is not 6";
    }
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
  public void testSystemResourcesJdbcAttributeChange(String jdbcResourceName, String dsUrl) {
    int initialCapacity = 2;
    final int maxCapacity = 12;
    final boolean testConnectionsonReserve = true;
    final int harvestMaxCount = 7;
    final int inactiveConnectionTimeoutSeconds = 120;

    println("Verifying the configuration changes made by sit config file");

    JDBCSystemResourceMBean jdbcSystemResource = getJdbcSystemResource(jdbcResourceName);
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

    createDatabase("mysqldb1", jdbcResourceName);
  }

  /**
   * Test that verifies a new JDBC data source is created after domain start can be overridden with
   * configmap change verifies the JDBC resource overriden values initialCapacity, maxCapacity,
   * LoginDelaySeconds, IgnoreInUseConnectionsEnabled, StatementCacheType,
   * GlobalTransactionsProtocol.
   *
   * @param jdbcResourceName - name of the JDBC resource overridden in jdbc-JdbcTestDataSource-0.xml
   */
  public void testOverrideJdbcResourceAfterDomainStart(String jdbcResourceName) {

    createDatabase("mysqldb2", jdbcResourceName);

    final int initialCapacity = 5;
    final int maxCapacity = 10;

    println("Verifying the configuration changes made by sit config file");

    JDBCSystemResourceMBean jdbcSystemResource = getJdbcSystemResource(jdbcResourceName);
    JDBCDataSourceBean jdbcDataSourceBean = jdbcSystemResource.getJDBCResource();

    // Assert the connection pool properties
    JDBCConnectionPoolParamsBean jcpb = jdbcDataSourceBean.getJDBCConnectionPoolParams();
    println("initialCapacity:" + jcpb.getInitialCapacity());
    assert initialCapacity == jcpb.getInitialCapacity()
        : "Didn't get the expected value " + initialCapacity + " for initialCapacity";
    println("maxCapacity:" + jcpb.getMaxCapacity());
    assert maxCapacity == jcpb.getMaxCapacity()
        : "Didn't get the expected value " + maxCapacity + " for maxCapacity";
    assert jcpb.getMinCapacity() == 6 : "Didn't get the expected value 6 for minCapacity";
    assert jcpb.getLoginDelaySeconds() == 10
        : "Didn't get the expected value 10 for LoginDelaySeconds";
    assert jcpb.isIgnoreInUseConnectionsEnabled()
        : "Didn't get the expected value of true for IgnoreInUseConnectionsEnabled";
    assert jcpb.getStatementCacheType().equals("FIXED")
        : "Didn't get the expected value of FIXED for StatementCacheType";

    JDBCDataSourceParamsBean jdbcDataSourceParams = jdbcDataSourceBean.getJDBCDataSourceParams();
    assert jdbcDataSourceParams.getGlobalTransactionsProtocol().equals("EmulateTwoPhaseCommit")
        : "Didn't get the expected value of EmulateTwoPhaseCommit for GlobalTransactionsProtocol";
  }

  /**
   * Utility method to create a database schema using the data source name.
   *
   * @param dbName name of the database schema to create
   * @param jdbcResourceName name of the data source to lookup
   */
  private void createDatabase(String dbName, String jdbcResourceName) {
    // Assert datasource is working with overiridden JDBC URL value
    Random rand = new Random();
    dbName = dbName + rand.nextInt(100);
    DataSource dataSource = getDataSource("jdbc/" + jdbcResourceName);
    try {
      Connection connection = dataSource.getConnection();
      Statement stmt = connection.createStatement();
      int createSchema = stmt.executeUpdate("CREATE SCHEMA `" + dbName + "` ;");
      println("create schema returned " + createSchema);
      int createTable =
          stmt.executeUpdate(
              "CREATE TABLE IF NOT EXISTS "
                  + dbName
                  + ".testtable (title VARCHAR(255) "
                  + "NOT NULL,description TEXT)ENGINE=INNODB;");
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
   * @return the JDBC data source mbean
   */
  protected JDBCSystemResourceMBean getJdbcSystemResource(String resourceName) {
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
   * The testSystemResourcesJmsAttributeChange verifies the ClusterJmsSystemResource system module
   * resource UniformReplicatedTestTopic Delivery Failure Parameters overridden in
   * jms-ClusterJmsSystemResource.xml attributes redelivery-limit and expiration-policy. The test
   * expects the overridden values to match against the domain configuration.
   */
  public void testSystemResourcesJmsAttributeChange() {
    String jmsModuleName = "ClusterJmsSystemResource";
    String topicName = "UniformReplicatedTestTopic";
    String expirationPolicyExp = "Discard";
    int redeliveryLimitExp = 20;

    JMSSystemResourceMBean jmsModule = getJmsSystemModule(jmsModuleName);
    JMSBean jmsResource = jmsModule.getJMSResource();
    UniformDistributedTopicBean uniformDistributedTopic =
        jmsResource.lookupUniformDistributedTopic(topicName);

    println("Verifying the configuration changes made by sit config file");
    assert expirationPolicyExp.equals(
            uniformDistributedTopic.getDeliveryFailureParams().getExpirationPolicy())
        : " Didn't get the expected Expiration Policy" + expirationPolicyExp;
    assert redeliveryLimitExp
            == uniformDistributedTopic.getDeliveryFailureParams().getRedeliveryLimit()
        : " Didn't get the expected redelivery limit " + redeliveryLimitExp;
  }

  /**
   * Returns the JMSSystemResourceMBean from domain configuration matching the JMS resource name.
   *
   * @param resourceName - name of the JMS system module to lookup
   * @return JMSSystemResourceMBean of the JMS module
   */
  protected JMSSystemResourceMBean getJmsSystemModule(String resourceName) {
    println("Looking up the jms system module..." + resourceName);
    DomainMBean domain = runtimeServiceMBean.getDomainConfiguration();
    JMSSystemResourceMBean jmsSysRes = domain.lookupJMSSystemResource(resourceName);
    assert jmsSysRes != null : " JMS resource is null";
    return jmsSysRes;
  }

  /**
   * The testSystemResourcesWldfAttributeAdd test verifies the WLDF-MODULE-0 WLDF system module
   * overridden in diagnostics-WLDF-MODULE-0.xml. The added elements wldf-instrumentation-monitor
   * harvested-type are expected to show up in the domain configuration.
   */
  public void testSystemResourcesWldfAttributeAdd() {
    String sitconfigFile = "testSystemResourcesWldfAttributeAdd.xml";
    final String wldfModName0 = "WLDF-MODULE-0";

    // verify the changed properties
    println("Verifying the configuration changes made by sit config file");
    String resourceName = wldfModName0;
    WLDFSystemResourceMBean wldfSystemModule = getWldfSystemModule(resourceName);
    WLDFResourceBean wldfRes = wldfSystemModule.getWLDFResource();
    String[] monitorsExp = {
      "Connector_After_Inbound",
      "Connector_Around_Outbound",
      "Connector_Around_Tx",
      "Connector_Around_Work",
      "Connector_Before_Inbound"
    };

    WLDFInstrumentationMonitorBean[] wldfInstrumentationMonitors =
        wldfRes.getInstrumentation().getWLDFInstrumentationMonitors();
    String[] monitorsGot = new String[wldfInstrumentationMonitors.length];
    for (int i = 0; i < wldfInstrumentationMonitors.length; i++) {
      monitorsGot[i] = wldfInstrumentationMonitors[i].getName();
      println("Monitor got :" + wldfInstrumentationMonitors[i].getName());
    }
    Arrays.sort(monitorsExp);
    Arrays.sort(monitorsGot);
    assert Arrays.equals(monitorsExp, monitorsGot)
        : "Didn't get all the configured monitors, expected "
            + Arrays.toString(monitorsExp)
            + " but got "
            + Arrays.toString(monitorsGot);

    String[] harvestedTypesExp = {
      "weblogic.management.runtime.JDBCServiceRuntimeMBean",
      "weblogic.management.runtime.ServerRuntimeMBean"
    };
    WLDFHarvestedTypeBean[] harvestedTypes = wldfRes.getHarvester().getHarvestedTypes();
    String[] harvestedTypesGot = new String[harvestedTypes.length];
    for (int i = 0; i < harvestedTypesGot.length; i++) {
      harvestedTypesGot[i] = harvestedTypes[i].getName();
      println("Harvester type :" + harvestedTypes[i].getName());
    }
    Arrays.sort(harvestedTypesExp);
    Arrays.sort(harvestedTypesGot);
    assert Arrays.equals(harvestedTypesExp, harvestedTypesGot) == true
        : "Didn't get all the configured harvesters, expected "
            + Arrays.toString(harvestedTypesExp)
            + " but got "
            + Arrays.toString(harvestedTypesGot);

    WLDFHarvestedTypeBean lookupHarvestedType1 =
        wldfRes.getHarvester().lookupHarvestedType(harvestedTypesExp[0]);
    assert lookupHarvestedType1.getName().equals(harvestedTypesExp[0])
        : "Harvested type name doesn't match";
    assert lookupHarvestedType1.getNamespace().equals("DomainRuntime")
        : "Harvested type name space doesn't match";
    WLDFHarvestedTypeBean lookupHarvestedType2 =
        wldfRes.getHarvester().lookupHarvestedType(harvestedTypesExp[1]);
    assert lookupHarvestedType2.getName().equals(harvestedTypesExp[1])
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
  protected WLDFSystemResourceMBean getWldfSystemModule(String resourceName) {
    println("Looking up the wldf system module..." + resourceName);
    DomainMBean domain = runtimeServiceMBean.getDomainConfiguration();
    WLDFSystemResourceMBean wldfResource = domain.lookupWLDFSystemResource(resourceName);
    assert wldfResource != null : "WLDF resource is null";
    return wldfResource;
  }

  /**
   * Looks up the managed server public listen address and port, closes the existing MBeanServer
   * connection to admin server and creates MBeanServer connection to the given managed server.
   *
   * @param serverName name of the managed server to which to create MBeanServerConnection
   * @throws Exception when connection close fails or when new connection to managed server fails.
   */
  private void connectToManagedServer(String serverName) throws Exception {
    ServerMBean[] servers = runtimeServiceMBean.getDomainConfiguration().getServers();
    try {
      for (ServerMBean server : servers) {
        if (server.getName().equals(serverName)) {
          adminHost = server.getListenAddress();
          adminPort = String.valueOf(server.getListenPort());
        }
      }
    } finally {
      jmxConnector.close();
      createConnections();
    }
  }
}

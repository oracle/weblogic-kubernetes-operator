// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * JUnit test class used for testing configuration override use cases for Domain In Image.
 */
public class ItSitConfigDomainInImage extends SitConfig {

  private static String testClassName;
  private static int testNumber;
  private static Operator operator1;
  private static Domain domain;
  private static String mysqlYamlFile = "";
  private static String domainNS;
  private static String testprefix = "sitconfigdomaininimage";
  private static String mysqldbport;
  private static String JDBC_URL;
  private static StringBuffer namespaceList;
  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties.
   * @throws Exception when the initialization fails.
   */

  @BeforeAll
  public static void staticPrepare() throws Exception {
    if (FULLTEST) {
      namespaceList = new StringBuffer();
      testClassName = new Object() {
      }.getClass().getEnclosingClass().getSimpleName();
      initialize(APP_PROPS_FILE, testClassName);
      TEST_RES_DIR = getProjectRoot() + "/integration-tests/src/test/resources/";
      testNumber = getNewSuffixCount();
    }
  }

  /**
   * This method gets called before every test. It creates the resultRoot, pvRoot directories, creates operator and
   * domain if not running.
   *
   * @throws Exception if results/pv directory or operator or domain creation fails.
   */

  @BeforeEach
  public void prepare() throws Exception {
    // initialize test properties and create the directories
    if (FULLTEST) {
      // create operator1
      if (operator1 == null) {
        createResultAndPvDirs(testClassName);
        Map<String, Object> operatorMap = createOperatorMap(testNumber, true, testprefix);
        operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator1);
        domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList.append((String) operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS);
        mysqldbport = String.valueOf(31306 + testNumber);
        domain = prepareDomainAndDB(true, domainNS, mysqldbport);
        JDBC_URL = "jdbc:mysql://" + fqdn + ":" + mysqldbport + "/";
        mysqlYamlFile = getResultDir() + "/sitconfigtemp" + testprefix + "/mysql/mysql-dbservices.yml";
        Assertions.assertNotNull(domain);
      }
    }
  }

  /**
   * Destroy domain, delete the MySQL DB container and teardown.
   *
   * @throws Exception when domain destruction or MySQL container destruction fails
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      ExecResult result = TestUtils.exec("kubectl delete -f " + mysqlYamlFile);
      destroySitConfigDomain(domain);
      if (operator1 != null) {
        LoggerHelper.getLocal().log(Level.INFO, "Destroying operator...");
        operator1.destroy();
        operator1 = null;
      }
      tearDown(new Object() {}.getClass()
          .getEnclosingClass().getSimpleName(), namespaceList.toString());
    }
  }

  /**
   * This test covers custom configuration override use cases for config.xml for administration
   * server for domain in image WLS servers.
   *
   * <p>The test checks the overridden config.xml attributes connect-timeout, max-message-size,
   * restart-max, JMXCore and ServerLifeCycle debug flags, the T3Channel public address. The
   * overridden values are verified against the ServerConfig MBean tree. It does not verifies
   * whether the overridden values are applied to the runtime.
   *
   * @throws Exception when the assertion fails due to unmatched values
   */
  @Test
  public void testCustomSitConfigOverridesForDomainInImage() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    testCustomSitConfigOverridesForDomain(testMethod,domain);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethod);
  }

  /**
   * This test covers custom configuration override use cases for config.xml for managed server for
   * domain in image WLS servers
   *
   * <p>The test checks the overridden config.xml server template attribute max-message-size. The
   * overridden values are verified against the ServerConfig MBean tree. It does not verifies
   * whether the overridden values are applied to the runtime.
   *
   * @throws Exception when the assertion fails due to unmatched values
   */
  @Test
  public void testCustomSitConfigOverridesForDomainMsInImage() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    testCustomSitConfigOverridesForDomainMS(testMethod,domain);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethod);
  }

  /**
   * This test covers custom resource override use cases for JDBC resource.
   *
   * <p>The resource override sets the following connection pool properties. initialCapacity,
   * maxCapacity, test-connections-on-reserve, connection-harvest-max-count,
   * inactive-connection-timeout-seconds in the JDBC resource override file. It also overrides the
   * jdbc driver parameters like data source url, db user and password using kubernetes secret.
   *
   * <p>The overridden values are verified against the ServerConfig MBean tree. It does not verifies
   * whether the overridden values are applied to the runtime except the JDBC URL which is verified
   * at runtime by making a connection to the MySql database and executing a DDL statement.
   *
   * @throws Exception when the assertion fails due to unmatched values
   */
  @Test
  public void testCustomSitConfigOverridesForJdbcInImage() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    testCustomSitConfigOverridesForJdbc(testMethod,domain, JDBC_URL);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethod);
  }

  /**
   * This test covers custom resource use cases for JMS resource. The JMS resource override file
   * sets the following Delivery Failure Parameters re delivery limit and expiration policy for a
   * uniform-distributed-topic JMS resource.
   *
   * <p>The overridden values are verified against the ServerConfig MBean tree. It does not verifies
   * whether the overridden values are applied to the runtime.
   *
   * @throws Exception when the assertion fails due to unmatched values
   */
  @Test
  public void testCustomSitConfigOverridesForJmsInImage() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    testCustomSitConfigOverridesForJms(testMethod,domain);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethod);
  }

  /**
   * This test covers custom resource override use cases for diagnostics resource. It adds the
   * following instrumentation monitors. Connector_After_Inbound, Connector_Around_Outbound,
   * Connector_Around_Tx, Connector_Around_Work, Connector_Before_Inbound, and harvesters for
   * weblogic.management.runtime.JDBCServiceRuntimeMBean,
   * weblogic.management.runtime.ServerRuntimeMBean.
   *
   * <p>The overridden values are verified against the ServerConfig MBean tree. It does not verifies
   * whether the overridden values are applied to the runtime.
   *
   * @throws Exception when the assertion fails due to unmatched values
   */
  @Test
  public void testCustomSitConfigOverridesForWldfInImage() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    testCustomSitConfigOverridesForWldf(testMethod,domain);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethod);
  }
}

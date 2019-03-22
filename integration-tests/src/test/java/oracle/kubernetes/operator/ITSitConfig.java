// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.BaseTest.initialize;
import static oracle.kubernetes.operator.BaseTest.logger;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public class ITSitConfig extends BaseTest {

  private static String TESTSCRIPTDIR;
  private static String ADMINPODNAME;
  private static final String DOMAINUID = "customsitconfigdomain";
  private static final String ADMINPORT = "30710";
  private static final int T3CHANNELPORT = 30091;
  private static final String MYSQL_DB_PORT = "31306";
  private static String fqdn;
  private static String JDBC_URL;
  private static String KUBE_EXEC_CMD;

  private static Domain domain;
  private static Operator operator1;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    // initialize test properties and create the directories
    if (!QUICKTEST) {
      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE);
      if (operator1 == null) {
        operator1 = TestUtils.createOperator(OPERATOR1_YAML);
      }
      TESTSCRIPTDIR = BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/";
      // Create the MySql db container
      ExecResult result =
          TestUtils.exec(
              "kubectl create -f " + TESTSCRIPTDIR + "/sitconfig/mysql/mysql-dbservices.yml");
      Assert.assertEquals(0, result.exitValue());

      fqdn = TestUtils.getHostName();
      JDBC_URL = "jdbc:mysql://" + fqdn + ":" + MYSQL_DB_PORT + "/";
      // copy the configuration override files to replacing the JDBC_URL token
      copySitConfigFiles();
      // create weblogic domain with configOverrides
      domain = createSitConfigDomain();
      Assert.assertNotNull(domain);
      // copy the jmx test client file the administratioin server weblogic server pod
      ADMINPODNAME = domain.getDomainUid() + "-" + domain.getAdminServerName();
      TestUtils.copyFileViaCat(
          TESTSCRIPTDIR + "sitconfig/java/SitConfigTests.java",
          "SitConfigTests.java",
          ADMINPODNAME,
          domain.getDomainNS());
      TestUtils.copyFileViaCat(
          TESTSCRIPTDIR + "sitconfig/scripts/runSitConfigTests.sh",
          "runSitConfigTests.sh",
          ADMINPODNAME,
          domain.getDomainNS());
      KUBE_EXEC_CMD =
          "kubectl -n " + domain.getDomainNS() + "  exec -it " + ADMINPODNAME + "  -- bash -c";
    }
  }

  /**
   * Destroy domain, delete the MySql DB container and teardown
   *
   * @throws Exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (!QUICKTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");

      destroySitConfigDomain();
      tearDown();
      ExecResult result =
          TestUtils.exec(
              "kubectl delete -f " + TESTSCRIPTDIR + "/sitconfig/mysql/mysql-dbservices.yml");
      logger.info("SUCCESS");
    }
  }

  /**
   * This test covers custom configuration override use cases for config.xml.
   *
   * <p>The test checks the overridden config.xml attributes connect-timeout, max-message-size,
   * restart-max, JMXCore and ServerLifeCycle debug flags, the T3Channel public address. The
   * overridden are verified against the ServerConfig MBean tree. It does not verifies whether the
   * overridden values are applied to the runtime
   *
   * @throws Exception
   */
  @Test
  public void testCustomSitConfigOverridesForDomain() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    boolean testCompletedSuccessfully = false;
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    ExecResult result =
        TestUtils.exec(
            KUBE_EXEC_CMD
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + T3CHANNELPORT
                + " weblogic welcome1 "
                + testMethod
                + "'");
    assertResult(result);
    testCompletedSuccessfully = true;
    logger.log(Level.INFO, "SUCCESS - {0}", testMethod);
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
   * @throws Exception
   */
  @Test
  public void testCustomSitConfigOverridesForJdbc() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    boolean testCompletedSuccessfully = false;
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    ExecResult result =
        TestUtils.exec(
            KUBE_EXEC_CMD
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + T3CHANNELPORT
                + " weblogic welcome1 "
                + testMethod
                + " "
                + JDBC_URL
                + "'");
    assertResult(result);
    testCompletedSuccessfully = true;
    logger.log(Level.INFO, "SUCCESS - {0}", testMethod);
  }

  /**
   * This test covers custom resource use cases for JMS resource The JMS resource override file sets
   * the following Delivery Failure Parameters. Redelivery limit and Expiration policy for a
   * uniform-distributed-topic JMS resource
   *
   * <p>The overridden values are verified against the ServerConfig MBean tree. It does not verifies
   * whether the overridden values are applied to the runtime
   *
   * @throws Exception
   */
  @Test
  public void testCustomSitConfigOverridesForJms() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    boolean testCompletedSuccessfully = false;
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    ExecResult result =
        TestUtils.exec(
            KUBE_EXEC_CMD
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + T3CHANNELPORT
                + " weblogic welcome1 "
                + testMethod
                + "'");
    assertResult(result);
    testCompletedSuccessfully = true;
    logger.log(Level.INFO, "SUCCESS - {0}", testMethod);
  }

  /**
   * This test covers custom resource override use cases for diagnostics resource. It adds the
   * following instrumentation monitors Connector_After_Inbound Connector_Around_Outbound
   * Connector_Around_Tx Connector_Around_Work Connector_Before_Inbound and harvesters for
   * weblogic.management.runtime.JDBCServiceRuntimeMBean
   * weblogic.management.runtime.ServerRuntimeMBean
   *
   * <p>The overridden values are verified against the ServerConfig MBean tree. It does not verifies
   * whether the overridden values are applied to the runtime
   *
   * @throws Exception
   */
  @Test
  public void testCustomSitConfigOverridesForWldf() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    boolean testCompletedSuccessfully = false;
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    ExecResult result =
        TestUtils.exec(
            KUBE_EXEC_CMD
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + T3CHANNELPORT
                + " weblogic welcome1 "
                + testMethod
                + "'");
    assertResult(result);
    testCompletedSuccessfully = true;
    logger.log(Level.INFO, "SUCCESS - {0}", testMethod);
  }

  private static Domain createSitConfigDomain() throws Exception {
    String createDomainScript = TESTSCRIPTDIR + "/domain-home-on-pv/create-domain.py";
    // load input yaml to map and add configOverrides
    Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
    domainMap.put("configOverrides", "sitconfigcm");
    domainMap.put("configOverridesFile", sitconfigDir);
    domainMap.put("domainUID", DOMAINUID);
    domainMap.put("adminNodePort", new Integer(ADMINPORT));
    domainMap.put("t3ChannelPort", new Integer(T3CHANNELPORT));
    domainMap.put(
        "createDomainPyScript",
        "integration-tests/src/test/resources/sitconfig/scripts/create-domain-auto-custom-sit-config20.py");
    domainMap.put(
        "javaOptions",
        "-Dweblogic.debug.DebugSituationalConfig=true -Dweblogic.debug.DebugSituationalConfigDumpXml=true");
    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();
    return domain;
  }

  private static void destroySitConfigDomain() throws Exception {
    if (domain != null) {
      domain.destroy();
    }
  }

  private static void copySitConfigFiles() throws IOException {
    String src_dir = TESTSCRIPTDIR + "/sitconfig/configoverrides";
    String dst_dir = sitconfigDir;
    String files[] = {
      "config.xml",
      "jdbc-JdbcTestDataSource-0.xml",
      "diagnostics-WLDF-MODULE-0.xml",
      "jms-ClusterJmsSystemResource.xml",
      "version.txt"
    };
    for (String file : files) {
      Path path = Paths.get(src_dir, file);
      logger.log(Level.INFO, "Copying {0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      String content = new String(Files.readAllBytes(path), charset);
      content = content.replaceAll("JDBC_URL", JDBC_URL);
      path = Paths.get(dst_dir, file);
      logger.log(Level.INFO, "to {0}", path.toString());
      Files.write(path, content.getBytes(charset));
    }
  }

  private void assertResult(ExecResult result) {
    logger.log(Level.INFO, result.stdout().trim());
    Assert.assertFalse(result.stdout().toLowerCase().contains("error"));
    Assert.assertFalse(result.stderr().toLowerCase().contains("error"));
    Assert.assertEquals(0, result.exitValue());
  }
}

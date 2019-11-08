// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;



/**
 * JUnit test class used for testing configuration override use cases for Domain In Image.
 */
public class ItSitConfigDomainInImage extends SitConfig {

  private static String testClassName;
  private static int testNumber;
  private static Operator operator1;
  private static Domain domain;
  private static String sitconfigTmpDir = "";
  private static String mysqltmpDir = "";
  private static String configOverrideDir = "";
  private static String mysqlYamlFile = "";
  private static String domainNS;
  private static String testprefix = "sitconfigdomaininimage";
  private static String mysqldbport;
  private static String adminpodname;
  private static String domainYaml;
  private static String JDBC_URL;
  private static StringBuffer namespaceList;
  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception when the initialization, creating directories , copying files and domain
   *                   creation fails.
   */

  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (FULLTEST) {
      testClassName = new Object() {
      }.getClass().getEnclosingClass().getSimpleName();
      initialize(APP_PROPS_FILE, testClassName);
    }
  }

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception when the initialization, creating directories , copying files and domain
   *                   creation fails.
   */
  @Before
  public void prepare() throws Exception {
    // initialize test properties and create the directories
    if (FULLTEST) {
      createResultAndPvDirs(testClassName);
      testNumber = getNewSuffixCount();
      // create operator1
      if (operator1 == null) {
        Map<String, Object> operatorMap = createOperatorMap(testNumber, true, testprefix);
        operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assert.assertNotNull(operator1);
        domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList = new StringBuffer((String)operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS);
      }
      TEST_RES_DIR = BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/";

      sitconfigTmpDir = getResultDir() + "/sitconfigtemp" + testprefix;
      mysqltmpDir = sitconfigTmpDir + "/mysql";
      configOverrideDir = sitconfigTmpDir + "/configoverridefiles";
      mysqlYamlFile = mysqltmpDir + "/mysql-dbservices.yml";

      Files.createDirectories(Paths.get(sitconfigTmpDir));
      Files.createDirectories(Paths.get(configOverrideDir));
      Files.createDirectories(Paths.get(mysqltmpDir));
      mysqldbport = String.valueOf(31306 + testNumber);
      // Create the MySql db container
      copyMySqlFile(domainNS, mysqlYamlFile, mysqldbport, testprefix);

      if (!OPENSHIFT) {
        fqdn = TestUtils.getHostName();
      } else {
        ExecResult result = TestUtils.exec("hostname -i");
        fqdn = result.stdout().trim();
      }
      JDBC_URL = "jdbc:mysql://" + fqdn + ":" + mysqldbport + "/";
      // copy the configuration override files to replacing the JDBC_URL token
      String[] files = {
          "config.xml",
          "jdbc-JdbcTestDataSource-0.xml",
          "diagnostics-WLDF-MODULE-0.xml",
          "jms-ClusterJmsSystemResource.xml",
          "version.txt"
      };
      copySitConfigFiles(files, oldSecret, configOverrideDir, testprefix, JDBC_URL);
      // create weblogic domain with configOverrides
      String domainScript = "integration-tests/src/test/resources/sitconfig/scripts/"
          + "create-domain-auto-custom-sit-config-inimage.py";
      domain = createSitConfigDomain(true, domainScript, domainNS);
      Assert.assertNotNull(domain);
      domainYaml =
          getUserProjectsDir()
              + "/weblogic-domains/"
              + domain.getDomainUid()
              + "/domain.yaml";
      // copy the jmx test client file the administratioin server weblogic server pod
      adminpodname = domain.getDomainUid() + "-" + domain.getAdminServerName();
      TestUtils.copyFileViaCat(
          TEST_RES_DIR + "sitconfig/java/SitConfigTests.java",
          "SitConfigTests.java",
          adminpodname,
          domain.getDomainNs());
      Files.copy(
          Paths.get(TEST_RES_DIR + "sitconfig/scripts","runSitConfigTests.sh"),
          Paths.get(sitconfigTmpDir, "runSitConfigTests.sh"),
          StandardCopyOption.REPLACE_EXISTING);
      Path path = Paths.get(sitconfigTmpDir, "runSitConfigTests.sh");
      String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      content = content.replaceAll("customsitconfigdomain", testprefix);
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, content.getBytes(charset));

      TestUtils.copyFileViaCat(
          sitconfigTmpDir + "/runSitConfigTests.sh",
          "runSitConfigTests.sh",
          adminpodname,
          domain.getDomainNs());

    }
  }

  /**
   * Destroy domain, delete the MySQL DB container and teardown.
   *
   * @throws Exception when domain destruction or MySQL container destruction fails
   */
  @AfterClass
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
    Assume.assumeTrue(FULLTEST);
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
    Assume.assumeTrue(FULLTEST);
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
    Assume.assumeTrue(FULLTEST);
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
    Assume.assumeTrue(FULLTEST);
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
    Assume.assumeTrue(FULLTEST);
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    testCustomSitConfigOverridesForWldf(testMethod,domain);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethod);
  }
}

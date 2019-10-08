// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.Assert;

/** JUnit test class used for testing configuration override use cases. */
public class SitConfig extends BaseTest {

  private static final String DOMAINUID = "customsitconfigdomain";
  private static final String ADMINPORT = "30710";
  private static final int T3CHANNELPORT = 30091;
  private static final String MYSQL_DB_PORT = "31306";
  private static String TEST_RES_DIR;
  private static String ADMINPODNAME;
  private static String fqdn;
  private static String JDBC_URL;
  private static final String JDBC_DRIVER_NEW = "com.mysql.cj.jdbc.Driver";
  private static final String JDBC_DRIVER_OLD = "com.mysql.jdbc.Driver";
  private static final String PS3_TAG = "12.2.1.3";
  private static String KUBE_EXEC_CMD;
  private static Domain domain;
  private static Operator operator1;
  private static String sitconfigTmpDir = "";
  private static String mysqltmpDir = "";
  private static String configOverrideDir = "";
  private static String mysqlYamlFile = "";
  private static String domainYaml;
  private static String JDBC_RES_SCRIPT;
  private static final String oldSecret = "test-secrets";
  private static final String newSecret = "test-secrets-new";

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception when the initialization, creating directories , copying files and domain
   *     creation fails.
   */
  protected static void staticPrepare(String domainInputYaml, String domainScript)
      throws Exception {
    // initialize test properties and create the directories
    if (FULLTEST) {
      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE);
      if (operator1 == null) {
        operator1 = TestUtils.createOperator(OPERATOR1_YAML);
      }
      TEST_RES_DIR = BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/";
      sitconfigTmpDir = BaseTest.getResultDir() + "/sitconfigtemp";
      mysqltmpDir = sitconfigTmpDir + "/mysql";
      configOverrideDir = sitconfigTmpDir + "/configoverridefiles";
      mysqlYamlFile = mysqltmpDir + "/mysql-dbservices.yml";
      Files.createDirectories(Paths.get(sitconfigTmpDir));
      Files.createDirectories(Paths.get(configOverrideDir));
      Files.createDirectories(Paths.get(mysqltmpDir));
      // Create the MySql db container
      copyMySqlFile();
      ExecResult result = TestUtils.exec("kubectl create -f " + mysqlYamlFile);
      Assert.assertEquals(0, result.exitValue());

      if (!OPENSHIFT) {
        fqdn = TestUtils.getHostName();
      } else {
        result = TestUtils.exec("hostname -i");
        fqdn = result.stdout().trim();
      }
      JDBC_URL = "jdbc:mysql://" + fqdn + ":" + MYSQL_DB_PORT + "/";
      // copy the configuration override files to replacing the JDBC_URL token
      String[] files = {
        "config.xml",
        "jdbc-JdbcTestDataSource-0.xml",
        "diagnostics-WLDF-MODULE-0.xml",
        "jms-ClusterJmsSystemResource.xml",
        "version.txt"
      };
      copySitConfigFiles(files, oldSecret);
      // create weblogic domain with configOverrides
      domain = createSitConfigDomain(domainInputYaml, domainScript);
      Assert.assertNotNull(domain);
      domainYaml =
          BaseTest.getUserProjectsDir()
              + "/weblogic-domains/"
              + domain.getDomainUid()
              + "/domain.yaml";
      // copy the jmx test client file the administratioin server weblogic server pod
      ADMINPODNAME = domain.getDomainUid() + "-" + domain.getAdminServerName();
      TestUtils.copyFileViaCat(
          TEST_RES_DIR + "sitconfig/java/SitConfigTests.java",
          "SitConfigTests.java",
          ADMINPODNAME,
          domain.getDomainNs());
      TestUtils.copyFileViaCat(
          TEST_RES_DIR + "sitconfig/scripts/runSitConfigTests.sh",
          "runSitConfigTests.sh",
          ADMINPODNAME,
          domain.getDomainNs());
      KUBE_EXEC_CMD =
          "kubectl -n " + domain.getDomainNs() + "  exec -it " + ADMINPODNAME + "  -- bash -c";
      JDBC_RES_SCRIPT = TEST_RES_DIR + "/sitconfig/scripts/create-jdbc-resource.py";
    }
  }

  /**
   * Destroy domain, delete the MySQL DB container and teardown.
   *
   * @throws Exception when domain destruction or MySQL container destruction fails
   */
  protected static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      ExecResult result = TestUtils.exec("kubectl delete -f " + mysqlYamlFile);
      destroySitConfigDomain();
      if (operator1 != null) {
        logger.log(Level.INFO, "Destroying operator...");
        operator1.destroy();
        operator1 = null;
      }
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
    }
  }

  /**
   * Create Domain using the custom domain script create-domain-auto-custom-sit-config20.py
   * Customizes the following attributes of the domain map configOverrides, configOverridesFile
   * domain uid , admin node port and t3 channel port.
   *
   * @return - created domain
   * @throws Exception - if it cannot create the domain
   */
  private static Domain createSitConfigDomain(String domainInputYaml, String domainScript)
      throws Exception {
    // load input yaml to map and add configOverrides
    Map<String, Object> domainMap = TestUtils.loadYaml(domainInputYaml);
    domainMap.put("configOverrides", "sitconfigcm");
    domainMap.put("configOverridesFile", configOverrideDir);
    domainMap.put("domainUID", DOMAINUID);
    domainMap.put("adminNodePort", new Integer(ADMINPORT));
    domainMap.put("t3ChannelPort", new Integer(T3CHANNELPORT));
    domainMap.put("createDomainPyScript", domainScript);
    domainMap.put(
        "javaOptions",
        "-Dweblogic.debug.DebugSituationalConfig=true -Dweblogic.debug.DebugSituationalConfigDumpXml=true");
    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();
    return domain;
  }

  /**
   * Destroys the domain.
   *
   * @throws Exception when domain destruction fails
   */
  private static void destroySitConfigDomain() throws Exception {
    if (domain != null) {
      logger.log(Level.INFO, "Destroying domain...");
      domain.destroy();
    }
  }

  /**
   * Copy the configuration override files to a staging area after replacing the JDBC_URL token in
   * jdbc-JdbcTestDataSource-0.xml.
   *
   * @throws IOException when copying files from source location to staging area fails
   */
  private static void copySitConfigFiles(String[] files, String secretName) throws IOException {
    String srcDir = TEST_RES_DIR + "/sitconfig/configoverrides";
    String dstDir = configOverrideDir;

    Charset charset = StandardCharsets.UTF_8;
    for (String file : files) {
      Path path = Paths.get(srcDir, file);
      logger.log(Level.INFO, "Copying {0}", path.toString());
      String content = new String(Files.readAllBytes(path), charset);
      content = content.replaceAll("JDBC_URL", JDBC_URL);
      content = content.replaceAll(oldSecret, secretName);
      if (getWeblogicImageTag().contains(PS3_TAG)) {
        content = content.replaceAll(JDBC_DRIVER_NEW, JDBC_DRIVER_OLD);
      }
      path = Paths.get(dstDir, file);
      logger.log(Level.INFO, "to {0}", path.toString());
      if (path.toFile().exists()) {
        Files.write(path, content.getBytes(charset), StandardOpenOption.TRUNCATE_EXISTING);
      } else {
        Files.write(path, content.getBytes(charset));
      }
      // display(dstDir);
    }
  }

  private static void display(String dir) throws IOException {
    for (File file : new File(dir).listFiles()) {
      logger.log(Level.INFO, file.getAbsolutePath());
      logger.log(Level.INFO, new String(Files.readAllBytes(file.toPath())));
    }
  }

  /**
   * A utility method to copy MySQL yaml template file replacing the NAMESPACE and DOMAINUID.
   *
   * @throws IOException when copying files from source location to staging area fails
   */
  private static void copyMySqlFile() throws IOException {
    final Path src = Paths.get(TEST_RES_DIR + "/mysql/mysql-dbservices.ymlt");
    final Path dst = Paths.get(mysqlYamlFile);
    logger.log(Level.INFO, "Copying {0}", src.toString());
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(src), charset);
    content = content.replaceAll("@NAMESPACE@", "default");
    content = content.replaceAll("@DOMAIN_UID@", DOMAINUID);
    logger.log(Level.INFO, "to {0}", dst.toString());
    Files.write(dst, content.getBytes(charset));
  }

  /**
   * This test covers custom configuration override use cases for config.xml for administration
   * server.
   *
   * <p>The test checks the overridden config.xml attributes connect-timeout, max-message-size,
   * restart-max, JMXCore and ServerLifeCycle debug flags, the T3Channel public address. The
   * overridden values are verified against the ServerConfig MBean tree. It does not verifies
   * whether the overridden values are applied to the runtime.
   *
   * @param testMethod - Name of the test
   * @throws Exception when the assertion fails due to unmatched values
   */
  protected void testCustomSitConfigOverridesForDomain(String testMethod) throws Exception {
    transferTests();
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
  }

  /**
   * This test covers custom configuration override use cases for config.xml for managed server.
   *
   * <p>The test checks the overridden config.xml server template attribute max-message-size. The
   * overridden values are verified against the ServerConfig MBean tree. It does not verifies
   * whether the overridden values are applied to the runtime.
   *
   * @param testMethod - Name of the test
   * @throws Exception when the assertion fails due to unmatched values
   */
  protected void testCustomSitConfigOverridesForDomainMS(String testMethod) throws Exception {
    transferTests();
    ExecResult result =
        TestUtils.exec(
            KUBE_EXEC_CMD
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + T3CHANNELPORT
                + " weblogic welcome1 "
                + testMethod
                + " managed-server1'");
    assertResult(result);
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
   * @param testMethod - Name of the test
   * @throws Exception when the assertion fails due to unmatched values
   */
  protected void testCustomSitConfigOverridesForJdbc(String testMethod) throws Exception {
    transferTests();
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
  }

  /**
   * This test covers custom resource use cases for JMS resource. The JMS resource override file
   * sets the following Delivery Failure Parameters re delivery limit and expiration policy for a
   * uniform-distributed-topic JMS resource.
   *
   * <p>The overridden values are verified against the ServerConfig MBean tree. It does not verifies
   * whether the overridden values are applied to the runtime.
   *
   * @param testMethod - Name of the test
   * @throws Exception when the assertion fails due to unmatched values
   */
  protected void testCustomSitConfigOverridesForJms(String testMethod) throws Exception {
    transferTests();
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
   * @param testMethod - Name of the test
   * @throws Exception when the assertion fails due to unmatched values
   */
  protected void testCustomSitConfigOverridesForWldf(String testMethod) throws Exception {
    transferTests();
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
  }

  /**
   * Test to verify the configuration override after a domain is up and running. Modifies the
   * existing config.xml entries to add startup and shutdown classes verifies those are overridden
   * when domain is restarted.
   *
   * @param testMethod - Name of the test
   * @throws Exception when assertions fail.
   */
  protected void testConfigOverrideAfterDomainStartup(String testMethod) throws Exception {
    // recreate the map with new situational config files
    String srcDir = TEST_RES_DIR + "/sitconfig/configoverrides";
    String dstDir = configOverrideDir;
    Files.copy(
        Paths.get(srcDir, "config_1.xml"),
        Paths.get(dstDir, "config.xml"),
        StandardCopyOption.REPLACE_EXISTING);
    recreateConfigMapandRestart(oldSecret, oldSecret);
    transferTests();
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
  }

  /**
   * This test covers the overriding of JDBC system resource after a domain is up and running. It
   * creates a datasource , recreates the K8S configmap with updated JDBC descriptor and verifies
   * the new overridden values with restart of the WLS pods
   *
   * @param testMethod - Name of the test
   * @throws Exception when assertions fail.
   */
  protected void testOverrideJdbcResourceAfterDomainStart(String testMethod) throws Exception {
    createJdbcResource();
    // recreate the map with new situational config files
    String srcDir = TEST_RES_DIR + "/sitconfig/configoverrides";
    String dstDir = configOverrideDir;
    Files.copy(
        Paths.get(srcDir, "jdbc-JdbcTestDataSource-1.xml"),
        Paths.get(dstDir, "jdbc-JdbcTestDataSource-1.xml"),
        StandardCopyOption.REPLACE_EXISTING);
    recreateConfigMapandRestart(oldSecret, oldSecret);
    transferTests();
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
  }

  /**
   * This test covers the overriding of JDBC system resource with new kubernetes secret name for
   * dbusername and dbpassword.
   *
   * @param testMethod - Name of the test
   * @throws Exception when assertions fail.
   */
  protected void testOverrideJdbcResourceWithNewSecret(String testMethod) throws Exception {
    // recreate the map with new situational config files
    String[] files = {"config.xml", "jdbc-JdbcTestDataSource-0.xml"};
    try {
      copySitConfigFiles(files, newSecret);
      recreateConfigMapandRestart(oldSecret, newSecret);
      transferTests();
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
    } finally {
      copySitConfigFiles(files, "test-secrets");
      recreateConfigMapandRestart("test-secrets-new", "test-secrets");
    }
  }

  /**
   * Create a JDBC system resource in domain.
   *
   * @throws Exception JDBC resource creation fails
   */
  private void createJdbcResource() throws Exception {
    Path path = Paths.get(JDBC_RES_SCRIPT);
    String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    content = content.replaceAll("JDBC_URL", JDBC_URL);
    content = content.replaceAll("DOMAINUID", DOMAINUID);
    if (getWeblogicImageTag().contains(PS3_TAG)) {
      content = content.replaceAll(JDBC_DRIVER_NEW, JDBC_DRIVER_OLD);
    }
    Files.write(
        Paths.get(sitconfigTmpDir, "create-jdbc-resource.py"),
        content.getBytes(StandardCharsets.UTF_8));
    TestUtils.copyFileViaCat(
        Paths.get(sitconfigTmpDir, "create-jdbc-resource.py").toString(),
        "create-jdbc-resource.py",
        ADMINPODNAME,
        domain.getDomainNs());
    TestUtils.exec(KUBE_EXEC_CMD + " 'wlst.sh create-jdbc-resource.py'", true);
  }

  /**
   * Update the configOverrides configmap and restart WLS pods.
   *
   * @throws Exception when pods restart fail
   */
  private void recreateConfigMapandRestart(String oldSecret, String newSecret) throws Exception {
    // modify the domain.yaml if the secret name is changed
    if (!oldSecret.equals(newSecret)) {
      String content =
          new String(Files.readAllBytes(Paths.get(domainYaml)), StandardCharsets.UTF_8);
      content = content.replaceAll(oldSecret, newSecret);
      Files.write(
          Paths.get(domainYaml),
          content.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.TRUNCATE_EXISTING);

      // delete the old secret and add new secret to domain.yaml
      TestUtils.exec("kubectl delete secret " + domain.getDomainUid() + "-" + oldSecret, true);
      String cmd =
          "kubectl -n "
              + domain.getDomainNs()
              + " create secret generic "
              + domain.getDomainUid()
              + "-"
              + newSecret
              + " --from-literal=hostname="
              + TestUtils.getHostName()
              + " --from-literal=dbusername=root"
              + " --from-literal=dbpassword=root123";
      TestUtils.exec(cmd, true);
      TestUtils.exec("kubectl apply -f " + domainYaml, true);
    }

    int clusterReplicas =
        TestUtils.getClusterReplicas(DOMAINUID, domain.getClusterName(), domain.getDomainNs());

    // restart the pods so that introspector can run and replace files with new secret if changed
    // and with new config override files
    String patchStr = "'{\"spec\":{\"serverStartPolicy\":\"NEVER\"}}'";
    TestUtils.kubectlpatch(DOMAINUID, domain.getDomainNs(), patchStr);
    domain.verifyServerPodsDeleted(clusterReplicas);

    String cmd =
        "kubectl create configmap "
            + DOMAINUID
            + "-sitconfigcm --from-file="
            + configOverrideDir
            + " -o yaml --dry-run | kubectl replace -f -";
    TestUtils.exec(cmd, true);
    cmd = "kubectl describe cm -n " + domain.getDomainNs() + " customsitconfigdomain-sitconfigcm";
    TestUtils.exec(cmd, true);

    patchStr = "'{\"spec\":{\"serverStartPolicy\":\"IF_NEEDED\"}}'";
    TestUtils.kubectlpatch(DOMAINUID, domain.getDomainNs(), patchStr);
    domain.verifyDomainCreated();
  }

  /**
   * Transfer the tests to run in WLS pods.
   *
   * @throws Exception exception
   */
  private void transferTests() throws Exception {
    TestUtils.copyFileViaCat(
        TEST_RES_DIR + "sitconfig/java/SitConfigTests.java",
        "SitConfigTests.java",
        ADMINPODNAME,
        domain.getDomainNs());
    TestUtils.copyFileViaCat(
        TEST_RES_DIR + "sitconfig/scripts/runSitConfigTests.sh",
        "runSitConfigTests.sh",
        ADMINPODNAME,
        domain.getDomainNs());
  }

  /**
   * Verifies the test run result doesn't contain any errors and exit status is 0.
   *
   * @param result - ExecResult object containing result of the test run
   */
  private void assertResult(ExecResult result) {
    logger.log(Level.INFO, result.stdout().trim());
    Assert.assertFalse(result.stdout().toLowerCase().contains("error"));
    Assert.assertFalse(result.stderr().toLowerCase().contains("error"));
    Assert.assertEquals(0, result.exitValue());
  }
}

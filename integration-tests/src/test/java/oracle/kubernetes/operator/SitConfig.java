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
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.Assert;

/**
 * JUnit test class used for testing configuration override use cases.
 */
public class SitConfig extends BaseTest {

  /*
  protected static String ADMINPORT = String.valueOf(30800 + getNewSuffixCount());
  protected static int T3CHANNELPORT = 31000 + getNewSuffixCount();
  protected static String MYSQL_DB_PORT = String.valueOf(31306 + getNewSuffixCount());
  */
  protected static String ADMINPORT;
  protected static int T3CHANNELPORT;
  protected static String MYSQL_DB_PORT;
  protected static String TEST_RES_DIR;
  protected static String ADMINPODNAME;
  protected static String fqdn;
  protected static String JDBC_URL;
  protected static final String JDBC_DRIVER_NEW = "com.mysql.cj.jdbc.Driver";
  protected static final String JDBC_DRIVER_OLD = "com.mysql.jdbc.Driver";
  protected static final String PS3_TAG = "12.2.1.3";
  protected static String KUBE_EXEC_CMD;
  protected static Domain domain;
  protected static Operator operator1;
  protected static String sitconfigTmpDir = "";
  protected static String mysqltmpDir = "";
  protected static String configOverrideDir = "";
  protected static String mysqlYamlFile = "";
  protected static String domainYaml;
  protected static String JDBC_RES_SCRIPT;
  protected static final String oldSecret = "test-secrets";
  protected static final String newSecret = "test-secrets-new";
  protected static String domainNS;
  /*
  protected static String testprefix = "sitconfigdomaininpv";
  //private static String DOMAINUID = "customsitconfigdomain";
  protected static String DOMAINUID = "customsitconfigdomain";
  */

  //protected static String DOMAINUID = "customsitconfigdomain";
  protected static String testprefix;
  //private static String DOMAINUID = "customsitconfigdomain";
  protected static String DOMAINUID;


  /**
   * Create Domain using the custom domain script create-domain-auto-custom-sit-config20.py
   * Customizes the following attributes of the domain map configOverrides, configOverridesFile
   * domain uid , admin node port and t3 channel port.
   *
   * @return - created domain
   * @throws Exception - if it cannot create the domain
   */
  protected static Domain createSitConfigDomain(boolean domainInImage, String domainScript, String domainNS)
      throws Exception {
    // load input yaml to map and add configOverrides
    Map<String, Object> domainMap = null;
    if (domainInImage) {
      domainMap = TestUtils.createDomainInImageMap(getNewSuffixCount(), false, "sitconfigdomaininimage");
      testprefix = "sitconfigdomaininimage";
    } else {
      domainMap = TestUtils.createDomainMap(getNewSuffixCount(), "sitconfigdomaininpv");
      testprefix = "sitconfigdomaininpv";
    }

    configOverrideDir = BaseTest.getResultDir() + "/sitconfigtemp" + testprefix;
    domainMap.put("configOverrides", "sitconfigcm");
    domainMap.put("configOverridesFile", configOverrideDir);
    domainMap.put("domainUID", testprefix);
    //domainMap.put("adminNodePort", new Integer(ADMINPORT));
    //domainMap.put("t3ChannelPort", new Integer(T3CHANNELPORT));
    domainMap.put("createDomainPyScript", domainScript);
    domainMap.put("namespace", domainNS);
    domainMap.put(
        "javaOptions",
        "-Dweblogic.debug.DebugSituationalConfig=true -Dweblogic.debug.DebugSituationalConfigDumpXml=true");
    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();
    DOMAINUID = testprefix;
    T3CHANNELPORT = (Integer)domain.getDomainMap().get("t3ChannelPort");
    return domain;
  }

  /**
   * Destroys the domain.
   *
   * @throws Exception when domain destruction fails
   */
  protected static void destroySitConfigDomain() throws Exception {
    if (domain != null) {
      LoggerHelper.getLocal().log(Level.INFO, "Destroying domain...");
      domain.destroy();
    }
  }

  /**
   * Copy the configuration override files to a staging area after replacing the JDBC_URL token in
   * jdbc-JdbcTestDataSource-0.xml.
   *
   * @throws IOException when copying files from source location to staging area fails
   */
  protected static void copySitConfigFiles(String[] files, String secretName) throws IOException {
    String srcDir = TEST_RES_DIR + "/sitconfig/configoverrides";
    String dstDir = configOverrideDir;

    Charset charset = StandardCharsets.UTF_8;
    for (String file : files) {
      Path path = Paths.get(srcDir, file);
      LoggerHelper.getLocal().log(Level.INFO, "Copying {0}", path.toString());
      String content = new String(Files.readAllBytes(path), charset);
      content = content.replaceAll("JDBC_URL", JDBC_URL);
      content = content.replaceAll(oldSecret, secretName);
      content = content.replaceAll("customsitconfigdomain", DOMAINUID);
      if (getWeblogicImageTag().contains(PS3_TAG)) {
        content = content.replaceAll(JDBC_DRIVER_NEW, JDBC_DRIVER_OLD);
      }
      path = Paths.get(dstDir, file);
      LoggerHelper.getLocal().log(Level.INFO, "to {0}", path.toString());
      if (path.toFile().exists()) {
        Files.write(path, content.getBytes(charset), StandardOpenOption.TRUNCATE_EXISTING);
      } else {
        Files.write(path, content.getBytes(charset));
      }
      // display(dstDir);
    }
  }

  protected static void display(String dir) throws IOException {
    for (File file : new File(dir).listFiles()) {
      LoggerHelper.getLocal().log(Level.INFO, file.getAbsolutePath());
      LoggerHelper.getLocal().log(Level.INFO, new String(Files.readAllBytes(file.toPath())));
    }
  }

  /**
   * A utility method to copy MySQL yaml template file replacing the NAMESPACE and DOMAINUID.
   *
   * @throws IOException when copying files from source location to staging area fails
   */
  protected static void copyMySqlFile(boolean domainInImage) throws Exception {
    setParamsForTest(domainInImage);
    Files.createDirectories(Paths.get(sitconfigTmpDir));
    Files.createDirectories(Paths.get(configOverrideDir));
    Files.createDirectories(Paths.get(mysqltmpDir));
    final Path src = Paths.get(TEST_RES_DIR + "/mysql/mysql-dbservices.ymlt");
    final Path dst = Paths.get(mysqlYamlFile);
    LoggerHelper.getLocal().log(Level.INFO, "Copying {0}", src.toString());
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(src), charset);
    LoggerHelper.getLocal().log(Level.INFO, "to {0}", dst.toString());
    Files.write(dst, content.getBytes(charset));
    content = new String(Files.readAllBytes(dst), charset);
    content = content.replaceAll("@NAMESPACE@", "default");
    content = content.replaceAll("@DOMAIN_UID@", DOMAINUID);
    content = content.replaceAll("@MYSQLPORT@", MYSQL_DB_PORT);
    Files.write(dst, content.getBytes(charset));
    ExecResult result = TestUtils.exec("kubectl create -f " + mysqlYamlFile);
    Assert.assertEquals(0, result.exitValue());
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
   * whether the overridden values are applied to the
   * runtime.
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
    Path path = Paths.get(dstDir, "config.xml");
    String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    content = content.replaceAll("customsitconfigdomain", DOMAINUID);
    //attention
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, content.getBytes(charset));

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
  protected void createJdbcResource() throws Exception {
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
  protected void recreateConfigMapandRestart(String oldSecret, String newSecret) throws Exception {
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
      TestUtils.exec("kubectl delete secret -n "
          + domain.getDomainNs() + " "
          + domain.getDomainUid() + "-" + oldSecret, true);
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
        "kubectl create configmap -n " + domain.getDomainNs()
            + " "
            + DOMAINUID
            + "-sitconfigcm --from-file="
            + configOverrideDir
            + " -o yaml --dry-run | kubectl replace -f -";
    TestUtils.exec(cmd, true);
    cmd = "kubectl describe cm -n " + domain.getDomainNs() + " " + DOMAINUID + "-sitconfigcm";
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
  protected void transferTests() throws Exception {
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
  protected void assertResult(ExecResult result) {
    LoggerHelper.getLocal().log(Level.INFO, result.stdout().trim());
    Assert.assertFalse(result.stdout().toLowerCase().contains("error"));
    Assert.assertFalse(result.stderr().toLowerCase().contains("error"));
    Assert.assertEquals(0, result.exitValue());
  }

  public static void setParamsForTest(boolean domainInImage) {
    int testNumber = getNewSuffixCount();
    //testprefix = "customsitconfigdomain";copy
    testprefix = "sitconfigdomaininpv";
    if (domainInImage) {
      testprefix = "sitconfigdomaininimage";
      testNumber++;
    }
    ADMINPORT = String.valueOf(30800 + testNumber);
    MYSQL_DB_PORT = String.valueOf(31306 + testNumber);
    T3CHANNELPORT = 31000 + testNumber;
    DOMAINUID = testprefix;
    sitconfigTmpDir = BaseTest.getResultDir() + "/sitconfigtemp" + testprefix;
    mysqltmpDir = sitconfigTmpDir + "/mysql";
    configOverrideDir = sitconfigTmpDir + "/configoverridefiles";
    mysqlYamlFile = mysqltmpDir + "/mysql-dbservices.yml";
  }

}

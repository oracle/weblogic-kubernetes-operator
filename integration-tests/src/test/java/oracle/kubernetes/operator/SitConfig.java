// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
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
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.Assertions;

/**
 * JUnit test class used for testing configuration override use cases.
 */
public class SitConfig extends BaseTest {

  protected static String TEST_RES_DIR;
  protected static String fqdn;
  //protected static String jdbcUrl;
  protected static final String JDBC_DRIVER_NEW = "com.mysql.cj.jdbc.Driver";
  protected static final String JDBC_DRIVER_OLD = "com.mysql.jdbc.Driver";
  protected static final String PS3_TAG = "12.2.1.3";
  protected static String JDBC_RES_SCRIPT;
  protected static final String oldSecret = "test-secrets";
  protected static final String newSecret = "test-secrets-new";

  /**
   * Create Domain using the custom domain script create-domain-auto-custom-sit-config20.py
   * Customizes the following attributes of the domain map configOverrides, configOverridesFile
   * domain uid , admin node port and t3 channel port.
   *
   * @return - created domain
   * @throws Exception - if it cannot create the domain
   */
  protected  Domain createSitConfigDomain(boolean domainInImage, String domainScript, String domainNS)
      throws Exception {
    // load input yaml to map and add configOverrides
    Map<String, Object> domainMap = null;
    String testprefix = "sitconfigdomaininpv";
    if (domainInImage) {
      testprefix = "sitconfigdomaininimage";
      domainMap = createDomainInImageMap(getNewSuffixCount(), false, testprefix);
    } else {
      domainMap = createDomainMap(getNewSuffixCount(), testprefix);
    }
    String resultDir = (String)domainMap.get("resultDir");
    JDBC_RES_SCRIPT = TEST_RES_DIR + "/sitconfig/scripts/create-jdbc-resource.py";
    String configOverrideDir = resultDir + "/sitconfigtemp" + testprefix + "/configoverridefiles";
    domainMap.put("configOverrides", "sitconfigcm");
    domainMap.put("configOverridesFile", configOverrideDir);
    domainMap.put("domainUID", testprefix);
    domainMap.put("createDomainPyScript", domainScript);
    domainMap.put("namespace", domainNS);
    domainMap.put(
        "javaOptions",
        "-Dweblogic.debug.DebugSituationalConfig=true -Dweblogic.debug.DebugSituationalConfigDumpXml=true");
    Domain domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();
    return domain;
  }

  /**
   * Destroys the domain.
   *
   * @throws Exception when domain destruction fails
   */
  protected static void destroySitConfigDomain(Domain domain) throws Exception {
    if (domain != null) {
      LoggerHelper.getLocal().log(Level.INFO, "Destroying domain...");
      domain.destroy();
      domain.deleteImage();
    }
  }

  /**
   * create domain, mysql database, test directories.
   * @param domainInImage - select domain in image or domain in pv
   * @param domainNS - namespace to create domain
   * @param mysqldbport - unique port number for mysql db port
   * @throws Exception when domain destruction fails
   */

  protected Domain prepareDomainAndDB(boolean domainInImage,String domainNS, String mysqldbport) throws Exception {
    String testprefix = "sitconfigdomaininpv";
    if (domainInImage) {
      testprefix = "sitconfigdomaininimage";
    }
    String sitconfigTmpDir = getResultDir() + "/sitconfigtemp" + testprefix;
    String mysqltmpDir = sitconfigTmpDir + "/mysql";
    String configOverrideDir = sitconfigTmpDir + "/configoverridefiles";

    Files.createDirectories(Paths.get(sitconfigTmpDir));
    Files.createDirectories(Paths.get(configOverrideDir));
    Files.createDirectories(Paths.get(mysqltmpDir));
    String mysqlYamlFile = mysqltmpDir + "/mysql-dbservices.yml";
    // Create the MySql db container
    copyMySqlFile(domainNS, mysqlYamlFile, mysqldbport, testprefix);

    if (!OPENSHIFT) {
      fqdn = TestUtils.getHostName();
    } else {
      ExecResult result = TestUtils.exec("hostname -i");
      fqdn = result.stdout().trim();
    }
    String jdbcUrl = "jdbc:mysql://" + fqdn + ":" + mysqldbport + "/";
    // copy the configuration override files to replacing the JDBC_URL token
    String[] files = {
        "config.xml",
        "jdbc-JdbcTestDataSource-0.xml",
        "diagnostics-WLDF-MODULE-0.xml",
        "jms-ClusterJmsSystemResource.xml",
        "version.txt"
    };
    copySitConfigFiles(files, oldSecret, configOverrideDir, testprefix, jdbcUrl);
    // create weblogic domain with configOverrides
    String domainScript = "integration-tests/src/test/resources/sitconfig/"
        + "scripts/create-domain-auto-custom-sit-config20.py";
    if (domainInImage) {
      domainScript =
          "integration-tests/src/test/resources/sitconfig/scripts/"
              + "create-domain-auto-custom-sit-config-inimage.py";
    }
    Domain domain = createSitConfigDomain(domainInImage, domainScript, domainNS);
    Assertions.assertNotNull(domain);
    // copy the jmx test client file the administratioin server weblogic server pod
    String adminpodName = domain.getDomainUid() + "-" + domain.getAdminServerName();
    TestUtils.copyFileViaCat(
        TEST_RES_DIR + "sitconfig/java/SitConfigTests.java",
        "SitConfigTests.java",
        adminpodName,
        domain.getDomainNs());
    Files.copy(
        Paths.get(TEST_RES_DIR + "sitconfig/scripts", "runSitConfigTests.sh"),
        Paths.get(sitconfigTmpDir, "runSitConfigTests.sh"),
        StandardCopyOption.REPLACE_EXISTING);
    Path path = Paths.get(sitconfigTmpDir, "runSitConfigTests.sh");
    String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    content = content.replaceAll("customsitconfigdomain", testprefix);
    //attention
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, content.getBytes(charset));

    TestUtils.copyFileViaCat(
        sitconfigTmpDir + "/runSitConfigTests.sh",
        "runSitConfigTests.sh",
        adminpodName,
        domain.getDomainNs());
    return domain;
  }

  /**
   * Copy the configuration override files to a staging area after replacing the jdbcUrl token in
   * jdbc-JdbcTestDataSource-0.xml.
   *
   * @throws IOException when copying files from source location to staging area fails
   */
  protected static void copySitConfigFiles(String[] files, String secretName,
                                           String dstDir,
                                           String domainNS, String jdbcUrl) throws IOException {
    String srcDir = TEST_RES_DIR + "/sitconfig/configoverrides";
    Charset charset = StandardCharsets.UTF_8;
    for (String file : files) {
      Path path = Paths.get(srcDir, file);
      LoggerHelper.getLocal().log(Level.INFO, "Copying {0}", path.toString());
      String content = new String(Files.readAllBytes(path), charset);
      content = content.replaceAll("JDBC_URL", jdbcUrl);
      content = content.replaceAll(oldSecret, secretName);
      content = content.replaceAll("customsitconfigdomain", domainNS);
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
  protected static void copyMySqlFile(String domainNS,
                                      String mysqlYamlFile,
                                      String mysqldbport, String domainUid) throws Exception {

    final Path src = Paths.get(TEST_RES_DIR + "/mysql/mysql-dbservices.ymlt");
    final Path dst = Paths.get(mysqlYamlFile);
    LoggerHelper.getLocal().log(Level.INFO, "Copying {0}", src.toString());
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(src), charset);
    LoggerHelper.getLocal().log(Level.INFO, "to {0}", dst.toString());
    Files.write(dst, content.getBytes(charset));
    content = new String(Files.readAllBytes(dst), charset);
    content = content.replaceAll("@NAMESPACE@", domainNS);
    content = content.replaceAll("@DOMAIN_UID@", domainUid);
    content = content.replaceAll("@MYSQLPORT@", mysqldbport);
    Files.write(dst, content.getBytes(charset));
    ExecResult result = TestUtils.exec("kubectl create -f " + mysqlYamlFile);
    Assertions.assertEquals(0, result.exitValue());
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
   * @param domain - Domain object
   * @throws Exception when the assertion fails due to unmatched values
   */
  protected void testCustomSitConfigOverridesForDomain(String testMethod, Domain domain) throws Exception {
    transferTests(domain);
    String adminpodname = domain.getDomainUid() + "-" + domain.getAdminServerName();
    String kubeExecCmd =
        "kubectl -n " + domain.getDomainNs() + "  exec -it " + adminpodname + "  -- bash -c";
    ExecResult result =
        TestUtils.exec(
            kubeExecCmd
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + ((Integer) domain.getDomainMap().get("t3ChannelPort")).intValue()
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
   * @param domain - Domain object
   * @throws Exception when the assertion fails due to unmatched values
   */
  protected void testCustomSitConfigOverridesForDomainMS(String testMethod, Domain domain) throws Exception {
    transferTests(domain);
    String adminpodname = domain.getDomainUid() + "-" + domain.getAdminServerName();
    String kubeExecCmd =
        "kubectl -n " + domain.getDomainNs() + "  exec -it " + adminpodname + "  -- bash -c";
    ExecResult result =
        TestUtils.exec(
            kubeExecCmd
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + ((Integer) domain.getDomainMap().get("t3ChannelPort")).intValue()
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
   * @param domain - Domain object
   * @throws Exception when the assertion fails due to unmatched values
   */
  protected void testCustomSitConfigOverridesForJdbc(String testMethod,
                                                     Domain domain, String jdbcUrl) throws Exception {
    transferTests(domain);
    String adminpodname = domain.getDomainUid() + "-" + domain.getAdminServerName();
    String kubeExecCmd =
        "kubectl -n " + domain.getDomainNs() + "  exec -it " + adminpodname + "  -- bash -c";
    ExecResult result =
        TestUtils.exec(
            kubeExecCmd
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + ((Integer) domain.getDomainMap().get("t3ChannelPort")).intValue()
                + " weblogic welcome1 "
                + testMethod
                + " "
                + jdbcUrl
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
   * @param domain - Domain object
   * @throws Exception when the assertion fails due to unmatched values
   */
  protected void testCustomSitConfigOverridesForJms(String testMethod, Domain domain) throws Exception {
    transferTests(domain);
    String adminpodname = domain.getDomainUid() + "-" + domain.getAdminServerName();
    String kubeExecCmd =
        "kubectl -n " + domain.getDomainNs() + "  exec -it " + adminpodname + "  -- bash -c";
    ExecResult result =
        TestUtils.exec(
            kubeExecCmd
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + ((Integer) domain.getDomainMap().get("t3ChannelPort")).intValue()
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
   * @param domain - Domain object   *
   * @throws Exception when the assertion fails due to unmatched values
   */
  protected void testCustomSitConfigOverridesForWldf(String testMethod, Domain domain) throws Exception {
    transferTests(domain);
    String adminpodname = domain.getDomainUid() + "-" + domain.getAdminServerName();
    String kubeExecCmd =
        "kubectl -n " + domain.getDomainNs() + "  exec -it " + adminpodname + "  -- bash -c";
    ExecResult result =
        TestUtils.exec(
            kubeExecCmd
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + ((Integer) domain.getDomainMap().get("t3ChannelPort")).intValue()
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
   * @param domain - Domain object
   * @throws Exception when assertions fail.
   */
  protected void testConfigOverrideAfterDomainStartup(String testMethod, Domain domain) throws Exception {
    // recreate the map with new situational config files
    String srcDir = TEST_RES_DIR + "/sitconfig/configoverrides";
    String dstDir = (String)domain.getDomainMap().get("configOverridesFile");
    Files.copy(
        Paths.get(srcDir, "config_1.xml"),
        Paths.get(dstDir, "config.xml"),
        StandardCopyOption.REPLACE_EXISTING);
    Path path = Paths.get(dstDir, "config.xml");
    String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    content = content.replaceAll("customsitconfigdomain", domain.getDomainUid());
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, content.getBytes(charset));

    recreateConfigMapandRestart(oldSecret, oldSecret, domain);
    transferTests(domain);
    String adminpodname = domain.getDomainUid() + "-" + domain.getAdminServerName();
    String kubeExecCmd =
        "kubectl -n " + domain.getDomainNs() + "  exec -it " + adminpodname + "  -- bash -c";
    ExecResult result =
        TestUtils.exec(
            kubeExecCmd
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + ((Integer) domain.getDomainMap().get("t3ChannelPort")).intValue()
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
   * @param domain - Domain object   *
   * @throws Exception when assertions fail.
   */
  protected void testOverrideJdbcResourceAfterDomainStart(String testMethod,
                                                          Domain domain,
                                                          String jdbcUrl) throws Exception {
    createJdbcResource(domain, jdbcUrl);
    // recreate the map with new situational config files
    String srcDir = TEST_RES_DIR + "/sitconfig/configoverrides";
    String dstDir = (String)domain.getDomainMap().get("configOverridesFile");
    Files.copy(
        Paths.get(srcDir, "jdbc-JdbcTestDataSource-1.xml"),
        Paths.get(dstDir, "jdbc-JdbcTestDataSource-1.xml"),
        StandardCopyOption.REPLACE_EXISTING);
    recreateConfigMapandRestart(oldSecret, oldSecret, domain);
    transferTests(domain);
    String adminpodname = domain.getDomainUid() + "-" + domain.getAdminServerName();
    String kubeExecCmd =
        "kubectl -n " + domain.getDomainNs() + "  exec -it " + adminpodname + "  -- bash -c";
    ExecResult result =
        TestUtils.exec(
            kubeExecCmd
                + " 'sh runSitConfigTests.sh "
                + fqdn
                + " "
                + ((Integer) domain.getDomainMap().get("t3ChannelPort")).intValue()
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
   * @param domain - Domain object   *
   * @throws Exception when assertions fail.
   */
  protected void testOverrideJdbcResourceWithNewSecret(String testMethod,
                                                       Domain domain,
                                                       String jdbcUrl) throws Exception {
    // recreate the map with new situational config files
    String[] files = {"config.xml", "jdbc-JdbcTestDataSource-0.xml"};
    String configOverrideDir = (String)domain.getDomainMap().get("configOverridesFile");
    try {
      copySitConfigFiles(files, newSecret, configOverrideDir, domain.getDomainUid(), jdbcUrl);
      recreateConfigMapandRestart(oldSecret, newSecret, domain);
      transferTests(domain);
      String adminpodname = domain.getDomainUid() + "-" + domain.getAdminServerName();
      String kubeExecCmd =
          "kubectl -n " + domain.getDomainNs() + "  exec -it " + adminpodname + "  -- bash -c";
      ExecResult result =
          TestUtils.exec(
              kubeExecCmd
                  + " 'sh runSitConfigTests.sh "
                  + fqdn
                  + " "
                  + ((Integer) domain.getDomainMap().get("t3ChannelPort")).intValue()
                  + " weblogic welcome1 "
                  + testMethod
                  + " "
                  + jdbcUrl
                  + "'");
      assertResult(result);
    } finally {
      copySitConfigFiles(files, "test-secrets", configOverrideDir, domain.getDomainUid(), jdbcUrl);
      recreateConfigMapandRestart("test-secrets-new", "test-secrets", domain);
    }
  }

  /**
   * Create a JDBC system resource in domain.
   *
   * @param domain - Domain object
   * @throws Exception JDBC resource creation fails
   */
  protected void createJdbcResource(Domain domain, String jdbcUrl) throws Exception {
    Path path = Paths.get(JDBC_RES_SCRIPT);
    String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

    content = content.replaceAll("JDBC_URL", jdbcUrl);
    content = content.replaceAll("DOMAINUID", domain.getDomainUid());
    if (getWeblogicImageTag().contains(PS3_TAG)) {
      content = content.replaceAll(JDBC_DRIVER_NEW, JDBC_DRIVER_OLD);
    }
    String testresultDir = (String)domain.getDomainMap().get("resultDir");
    String sitconfigTmpDir = testresultDir + "/sitconfigtemp" + domain.getDomainUid();
    Files.write(
        Paths.get(sitconfigTmpDir, "create-jdbc-resource.py"),
        content.getBytes(StandardCharsets.UTF_8));
    String adminpodname = domain.getDomainUid() + "-" + domain.getAdminServerName();
    TestUtils.copyFileViaCat(
        Paths.get(sitconfigTmpDir, "create-jdbc-resource.py").toString(),
        "create-jdbc-resource.py",
        adminpodname,
        domain.getDomainNs());
    String kubeExecCmd =
        "kubectl -n " + domain.getDomainNs() + "  exec -it " + adminpodname + "  -- bash -c";
    TestUtils.exec(kubeExecCmd + " 'wlst.sh create-jdbc-resource.py'", true);
  }

  /**
   * Update the configOverrides configmap and restart WLS pods.
   *
   * @throws Exception when pods restart fail
   */
  protected void recreateConfigMapandRestart(String oldSecret, String newSecret, Domain domain) throws Exception {
    // modify the domain.yaml if the secret name is changed
    String domainYaml =
        (String)domain.getDomainMap().get("userProjectsDir")
            + "/weblogic-domains/"
            + domain.getDomainUid()
            + "/domain.yaml";
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
        TestUtils.getClusterReplicas(domain.getDomainUid(), domain.getClusterName(), domain.getDomainNs());

    // restart the pods so that introspector can run and replace files with new secret if changed
    // and with new config override files
    String patchStr = "'{\"spec\":{\"serverStartPolicy\":\"NEVER\"}}'";
    TestUtils.kubectlpatch(domain.getDomainUid(), domain.getDomainNs(), patchStr);
    domain.verifyServerPodsDeleted(clusterReplicas);

    String cmd =
        "kubectl create configmap -n " + domain.getDomainNs()
            + " "
            + domain.getDomainUid()
            + "-sitconfigcm --from-file="
            + (String)domain.getDomainMap().get("configOverridesFile")
            + " -o yaml --dry-run | kubectl replace -f -";
    TestUtils.exec(cmd, true);
    cmd = "kubectl describe cm -n " + domain.getDomainNs() + " " + domain.getDomainUid() + "-sitconfigcm";
    TestUtils.exec(cmd, true);

    patchStr = "'{\"spec\":{\"serverStartPolicy\":\"IF_NEEDED\"}}'";
    TestUtils.kubectlpatch(domain.getDomainUid(), domain.getDomainNs(), patchStr);
    domain.verifyDomainCreated();
  }

  /**
   * Transfer the tests to run in WLS pods.
   * @param domain - Domain object
   * @throws Exception exception
   */
  protected void transferTests(Domain domain) throws Exception {
    String adminpodname = domain.getDomainUid() + "-" + domain.getAdminServerName();
    TestUtils.copyFileViaCat(
        TEST_RES_DIR + "sitconfig/java/SitConfigTests.java",
        "SitConfigTests.java",
        adminpodname,
        domain.getDomainNs());
    TestUtils.copyFileViaCat(
        TEST_RES_DIR + "sitconfig/scripts/runSitConfigTests.sh",
        "runSitConfigTests.sh",
        adminpodname,
        domain.getDomainNs());
  }

  /**
   * Verifies the test run result doesn't contain any errors and exit status is 0.
   *
   * @param result - ExecResult object containing result of the test run
   */
  protected void assertResult(ExecResult result) {
    LoggerHelper.getLocal().log(Level.INFO, result.stdout().trim());
    Assertions.assertFalse(result.stdout().toLowerCase().contains("error"));
    Assertions.assertFalse(result.stderr().toLowerCase().contains("error"));
    Assertions.assertEquals(0, result.exitValue());
  }
}

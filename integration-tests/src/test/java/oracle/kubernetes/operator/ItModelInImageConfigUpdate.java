// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.Operator.RestCertType;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Wdt Config Update with Model File(s) to existing MII domain
 *
 * <p>This test is used for creating domain using model in image.
 */

public class ItModelInImageConfigUpdate extends MiiBaseTest {
  private static Operator operator;
  private static Domain domain;
  private static String domainNS;
  private static String testClassName;
  private static StringBuffer namespaceList;
  private static final String configMapSuffix = "-mii-config-map";
  private static final String dsName = "MyDataSource";
  private static final String appName = "myear";
  private static final String readTimeout_1 = "30001";
  private static final String readTimeout_2 = "30002";

  /**
   * This method does the initialization of the integration test
   * properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception if initializing the application properties
   *         and creates directories for results fails.
   */
  @BeforeAll
  public static void staticPrepare() throws Exception {
    namespaceList = new StringBuffer();
    testClassName = new Object() {
    }.getClass().getEnclosingClass().getSimpleName();
    // initialize test properties and create the directories
    initialize(APP_PROPS_FILE, testClassName);
  }

  /**
   * This method creates the result/pv root directories for the test.
   * Creates the operator if its not running.
   *
   * @throws Exception if Operator creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    createResultAndPvDirs(testClassName);

    // create operator
    if (operator == null) {
      Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(),
          true, testClassName);
      operator = TestUtils.createOperator(operatorMap, RestCertType.SELF_SIGNED);
      Assertions.assertNotNull(operator);
      domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
      namespaceList.append((String)operatorMap.get("namespace"));
      namespaceList.append(" ").append(domainNS);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception when errors while running statedump.sh or cleanup.sh
   *         scripts or while renewing the lease for shared cluster run
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    tearDown(new Object() {
    }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

    LoggerHelper.getLocal().info("SUCCESS");
  }

  /**
   * Create a domain without a JDBC DS using model in image and having configmap
   * in the domain.yaml before deploying the domain. After deploying the domain crd,
   * re-create the configmap with model files that define a JDBC DataSource
   * and update the domain crd to change domain restartVersion to reload the model,
   * generate new config and initiate a rolling restart.
   *
   * @throws Exception if domain creation, config update or test veriofication fails
   */
  @Test
  public void testMiiConfigUpdateNonJdbcCm() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    boolean testCompletedSuccessfully = false;
    try {
      // create Domain w/o JDBC DS using the image created by MII
      boolean createDS = false;
      createDomainUsingMii(createDS);

      // copy model files that contains JDBC DS to a dir to re-create cm
      String destDir = copyTestModelFiles();

      // re-create cm to update config
      wdtConfigUpdate(destDir);

      // update domain yaml with restartVersion and
      // apply the domain yaml, verify domain restarted
      modifyDomainYamlWithRestartVersion(domain, domainNS);

      // verify the test result by checking updated config file on server pod
      verifyJdbcUpdate();

      // verify that JDBC DS is created by checking JDBC DS name and read timeout
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      Set<String> jdbcResourcesToVerify = new HashSet<String>();
      jdbcResourcesToVerify.add("datasource.name.1=" + dsName);
      jdbcResourcesToVerify.add("datasource.readTimeout.1=" + readTimeout_2);

      verifyJdbcResources(jdbcResourcesToVerify, destDir);

      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create a domain without a JDBC DS using model in image and having configmap
   * in the domain.yaml before deploying the domain. After deploying the domain crd,
   * create a new image with diff tag name and model files that define a JDBC DataSource
   * and update the domain crd to change image name to reload the model,
   * generate new config and initiate a rolling restart.
   *
   * @throws Exception if domain creation, config update or test veriofication fails
   */
  @Test
  public void testMiiConfigUpdateNonJdbcImage() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    boolean testCompletedSuccessfully = false;
    try {
      // create Domain w/o JDBC DS using the image created by MII
      boolean createDS = false;
      createDomainUsingMii(createDS);

      // create image with a new tag to update config
      Map<String, Object> domainMap = domain.getDomainMap();
      final String imageName = (String) domainMap.get("image") + "_nonjdbc";
      final String wdtModelFile = "model.jdbc.yaml";
      final String wdtModelPropFile = "model.jdbc.properties";
      createDomainImage(domainMap, imageName, wdtModelFile, wdtModelPropFile);

      // update domain yaml with new image tag and
      // apply the domain yaml, verify domain rolling-restarted
      modifyDomainYamlWithImageName(domain, domainNS, imageName);

      // verify the test result by checking updated config file on server pod
      verifyJdbcUpdate();

      // verify that JDBC DS is created by checking JDBC DS name and read timeout
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      Set<String> jdbcResourcesToVerify = new HashSet<String>();
      jdbcResourcesToVerify.add("datasource.name.1=" + dsName);
      jdbcResourcesToVerify.add("datasource.readTimeout.1=" + readTimeout_2);

      final String destDir = getResultDir() + "/samples/model-in-image-update";
      verifyJdbcResources(jdbcResourcesToVerify, destDir);

      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create a domain with a JDBC DS using model in image and having configmap
   * in the domain.yaml before deploying the domain. After deploying the domain crd,
   * re-create the configmap with a model file that define a JDBC DataSource
   * and update the domain crd to change domain restartVersion to reload the model,
   * generate new config and initiate a rolling restart.
   *
   * @throws Exception if domain creation, config update or test veriofication fails
   */
  @Test
  public void testMiiConfigUpdateJdbcCm() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    boolean testCompletedSuccessfully = false;
    final String connTimeout = "5200";
    try {
      // create Domain w JDBC DS using the image created by MII
      boolean createDS = true;
      createDomainUsingMii(createDS);

      // verify that JDBC DS is created by checking JDBC DS name and read timeout
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      Set<String> jdbcResourcesToVerify = new HashSet<String>();
      jdbcResourcesToVerify.add("datasource.name.1=" + dsName);
      jdbcResourcesToVerify.add("datasource.readTimeout.1=" + readTimeout_1);

      // Get dest dir and
      // copy another model files that contain JDBC DS to a dir to re-create cm
      String destDir = copyTestModelFiles();
      verifyJdbcResources(jdbcResourcesToVerify, destDir);

      // re-create cm to update config
      wdtConfigUpdate(destDir);

      // update domain yaml with restartVersion and
      // apply the domain yaml, verify domain restarted
      modifyDomainYamlWithRestartVersion(domain, domainNS);

      // verify the test result by checking updated config file on server pod
      verifyJdbcUpdate();

      // verify that JDBC DS is created by checking JDBC DS name,
      // connection timeout and read timeout via WLST on server pod
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is overridden");
      jdbcResourcesToVerify.add("datasource.name.1=" + dsName);
      jdbcResourcesToVerify.remove("datasource.readTimeout.1=" + readTimeout_1);
      jdbcResourcesToVerify.add("datasource.readTimeout.1=" + readTimeout_2);
      jdbcResourcesToVerify.add("datasource.connectionTimeout.1=" + connTimeout);

      verifyJdbcResources(jdbcResourcesToVerify, destDir);

      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create a domain with a JDBC DS using model in image and having configmap
   * in the domain.yaml before deploying the domain. After deploying the domain crd,
   * create a new image with diff tag name and model files that define a JDBC DataSource
   * and update the domain crd to change image name to reload the model,
   * generate new config and initiate a rolling restart.
   *
   * @throws Exception if domain creation, config update or test veriofication fails
   */
  @Test
  public void testMiiConfigUpdateJdbcImage() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    boolean testCompletedSuccessfully = false;
    try {
      // create Domain w/o JDBC DS using the image created by MII
      boolean createDS = true;
      createDomainUsingMii(createDS);

      // create image with a new tag to update config
      Map<String, Object> domainMap = domain.getDomainMap();
      final String imageName = (String) domainMap.get("image") + "_jdbc";
      final String wdtModelFile = "model.jdbc.yaml";
      final String wdtModelPropFile = "model.jdbc.properties";
      createDomainImage(domainMap, imageName, wdtModelFile, wdtModelPropFile);

      // update domain yaml with new image tag and
      // apply the domain yaml, verify domain rolling-restarted
      modifyDomainYamlWithImageName(domain, domainNS, imageName);

      // verify the test result by checking updated config file on server pod
      verifyJdbcUpdate();

      // verify that JDBC DS is created by checking JDBC DS name and read timeout
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      Set<String> jdbcResourcesToVerify = new HashSet<String>();
      jdbcResourcesToVerify.add("datasource.name.1=" + dsName);
      jdbcResourcesToVerify.add("datasource.readTimeout.1=" + readTimeout_2);

      final String destDir = getResultDir() + "/samples/model-in-image-update";
      verifyJdbcResources(jdbcResourcesToVerify, destDir);

      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create a domain using model in image and having configmap in the domain.yaml
   * The model file has predeployed application and a JDBC DataSource. After deploying
   * the domain crd, re-create the configmap with a model file that removes the JDBC
   * and application through the ! notation and update the domain crd to change domain
   * restartVersion to reload the model, generate new config and initiate a rolling restart.
   * Verify the JDBC DataSource and application no longer exists in the WebLogic domain.
   *
   * @throws Exception when test fails
   */
  @Test
  public void testMiiConfigAppDelete() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    try {
      // create Domain with JDBC DataSource and application using the image created by MII
      LoggerHelper.getLocal().log(Level.INFO,
          "Creating Domain & waiting for the script to complete execution");
      boolean createDS = true;
      createDomainUsingMii(createDS);

      // verify that JDBC DS is created by checking JDBC DS name and read timeout
      // verify the test result by checking override config file on server pod
      // verify that application is accessible from inside the managed server pod
      verifyJdbcUpdate();
      Assertions.assertTrue(verifyApp().contains("Hello"), "Application is not found");

      // delete config and application using new model file
      wdtConfigDeleteOverride();

      // update domain yaml with restartVersion and
      // apply the domain yaml, verify domain restarted
      modifyDomainYamlWithRestartVersion(domain, domainNS);

      // verify the test result by getting JDBC DS via WLST on server pod
      String destDir = getResultDir() + "/samples/model-in-image-override";
      String jdbcResources = getJdbcResources(destDir);
      Assertions.assertFalse(jdbcResources.contains(dsName), dsName + " is found");

      // verify the app access returns 404
      Assertions.assertFalse(verifyApp().contains("Hello"), "Application is found");

      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  private String copyTestModelFiles() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Creating configMap");
    String origDir = BaseTest.getProjectRoot()
        + "/integration-tests/src/test/resources/model-in-image";
    String destDir = getResultDir() + "/samples/model-in-image-update";
    final String modelFile = "model.jdbc.yaml";
    final String propFile = "model.jdbc.properties";
    Files.deleteIfExists(Paths.get(destDir));
    Files.createDirectories(Paths.get(destDir));

    TestUtils.copyFile(origDir + "/" + modelFile, destDir + "/" + modelFile);
    TestUtils.copyFile(origDir + "/" + propFile, destDir + "/" + propFile);

    return destDir;
  }

  private void createDomainUsingMii(boolean createDS) throws Exception {
    final String cmFile = "model.empty.properties";
    String wdtModelFile = "model.wls.yaml";
    String wdtModelPropFile = "model.properties";

    if (createDS) {
      wdtModelFile = "model.jdbc.image.yaml";
      wdtModelPropFile = "model.jdbc.image.properties";
    }

    StringBuffer paramBuff = new StringBuffer("Creating a Domain with: ");
    paramBuff
        .append("testClassName=")
        .append(testClassName)
        .append(", domainNS=")
        .append(domainNS)
        .append(", wdtModelFile=")
        .append(wdtModelFile)
        .append(", wdtModelPropFile=")
        .append(wdtModelPropFile)
        .append(", cmFile=")
        .append(cmFile)
        .append(", WdtDomainType=")
        .append(WdtDomainType.WLS.geWdtDomainType());

    LoggerHelper.getLocal().log(Level.INFO, "Params used to create domain: " + paramBuff);

    domain = createMiiDomainWithConfigMap(
        testClassName,
        domainNS,
        wdtModelFile,
        wdtModelPropFile,
        cmFile,
        WdtDomainType.WLS.geWdtDomainType());

    Assertions.assertNotNull(domain, "Failed to create a domain");
  }

  private void wdtConfigUpdate(String destDir) throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Creating configMap...");

    // Re-create config map after deploying domain crd
    final String domainUid = domain.getDomainUid();
    final String cmName = domainUid + configMapSuffix;
    final String label = "weblogic.domainUID=" + domainUid;

    TestUtils.createConfigMap(cmName, destDir, domainNS, label);
  }

  private void verifyJdbcUpdate() throws Exception {
    final String jndiName = "jdbc/generic1";
    // get domain name
    StringBuffer cmdStrBuff = new StringBuffer("kubectl get domain -n ");
    cmdStrBuff
        .append(domainNS)
        .append(" -o=jsonpath='{.items[0].metadata.name}'");

    LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdStrBuff);
    ExecResult result = TestUtils.exec(cmdStrBuff.toString());
    String domainName = result.stdout();
    LoggerHelper.getLocal().log(Level.INFO, "Domain name is: " + domainName);

    // check JDBC DS update
    cmdStrBuff = new StringBuffer("kubectl -n ");
    cmdStrBuff
        .append(domainNS)
        .append(" exec -it ")
        .append(domain.getDomainUid())
        .append("-")
        .append(domain.getAdminServerName())
        .append(" -- bash -c 'cd /u01/oracle/user_projects/domains/")
        .append(domainName)
        .append("/config/jdbc/")
        .append(" && grep -R ")
        .append(jndiName)
        .append("'");

    LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdStrBuff);
    result = TestUtils.exec(cmdStrBuff.toString());
    LoggerHelper.getLocal().log(Level.INFO, "JDBC DS info from server pod: " + result.stdout());

    Assertions.assertTrue(result.stdout().contains("<jndi-name>" + jndiName + "</jndi-name>"),
        "JDBC DS doesn't update");
  }

  private void verifyJdbcResources(Set<String> jdbcResourcesSet, String destDir) throws Exception {
    // verify JDBC DS props via WLST on server pod
    String jdbcResources = getJdbcResources(destDir);

    Iterator<String> iterator = jdbcResourcesSet.iterator();
    while (iterator.hasNext()) {
      String prop = iterator.next();
      Assertions.assertTrue(jdbcResources.contains(prop), prop + " is not found");
      LoggerHelper.getLocal().log(Level.INFO, prop + " exists");
    }
  }

  private String getJdbcResources(String destDir) throws Exception {
    // get domain name
    StringBuffer cmdStrBuff = new StringBuffer("kubectl get domain -n ");
    cmdStrBuff
        .append(domainNS)
        .append(" -o=jsonpath='{.items[0].metadata.name}'");
    LoggerHelper.getLocal().log(Level.INFO, "Command to domain name: " + cmdStrBuff);
    ExecResult result = TestUtils.exec(cmdStrBuff.toString());
    String domainName = result.stdout();
    LoggerHelper.getLocal().log(Level.INFO, "Domain name is: " + domainName);

    // copy verification file to test dir
    String origDir = BaseTest.getProjectRoot()
        + "/integration-tests/src/test/resources/model-in-image/scripts";
    String pyFileName = "verify-jdbc-resource.py";
    Files.createDirectories(Paths.get(destDir));
    TestUtils.copyFile(origDir + "/" + pyFileName, destDir + "/" + pyFileName);

    // replace var in verification file
    String tempDir = getResultDir() + "/configupdatetemp-" + domainNS;
    Files.createDirectories(Paths.get(tempDir));
    String content =
        new String(Files.readAllBytes(Paths.get(destDir + "/" + pyFileName)), StandardCharsets.UTF_8);
    content = content.replaceAll("DOMAINNAME", domainName);
    Files.write(
        Paths.get(tempDir, pyFileName),
        content.getBytes(StandardCharsets.UTF_8));

    // get server pod name
    cmdStrBuff = new StringBuffer("kubectl get pod -n ");
    cmdStrBuff
        .append(domainNS)
        .append(" -o=jsonpath='{.items[0].metadata.name}' | grep admin-server");
    LoggerHelper.getLocal().log(Level.INFO, "Command to get pod name: " + cmdStrBuff);
    result = TestUtils.exec(cmdStrBuff.toString());
    String adminPodName = result.stdout();
    LoggerHelper.getLocal().log(Level.INFO, "pod name is: " + adminPodName);

    // copy verification file to the pod
    cmdStrBuff = new StringBuffer("kubectl -n ");
    cmdStrBuff
        .append(domainNS)
        .append(" exec -it ")
        .append(adminPodName)
        .append(" -- bash -c 'mkdir -p ")
        .append(BaseTest.getAppLocationInPod())
        .append("'");
    LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdStrBuff);
    TestUtils.exec(cmdStrBuff.toString(), true);

    TestUtils.copyFileViaCat(
        Paths.get(tempDir, pyFileName).toString(),
        BaseTest.getAppLocationInPod() + "/" + pyFileName,
        adminPodName,
        domainNS);

    cmdStrBuff = new StringBuffer("kubectl -n ");
    cmdStrBuff
        .append(domainNS)
        .append(" exec -it ")
        .append(adminPodName)
        .append(" -- bash -c 'wlst.sh ")
        .append(BaseTest.getAppLocationInPod())
        .append("/")
        .append(pyFileName)
        .append("'");
    LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdStrBuff);
    result = TestUtils.exec(cmdStrBuff.toString(), true);

    return result.stdout();
  }

  private void wdtConfigDeleteOverride() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Creating configMap");
    String origDir = BaseTest.getProjectRoot()
        + "/integration-tests/src/test/resources/model-in-image";
    final String origModelFile = origDir + "/model.jdbc.yaml";
    final String origPropFile = origDir + "/model.jdbc.properties";
    final String destDir = getResultDir() + "/samples/model-in-image-override";;
    final String destModelFile = destDir + "/model.jdbc_2.yaml";
    final String destPropFile = destDir + "/model.jdbc_2.properties";
    Files.createDirectories(Paths.get(destDir));

    Path path = Paths.get(origModelFile);
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(path), charset);
    // prefix the JDBC DataSource and application name with !
    content = content.replaceAll(dsName, "!" + dsName);
    content = content.replaceAll(appName, "!" + appName);
    Files.write(Paths.get(destModelFile), content.getBytes(charset));
    TestUtils.copyFile(origPropFile, destPropFile);

    // Re-create config map after deploying domain crd
    final String domainUid = domain.getDomainUid();
    final String cmName = domainUid + configMapSuffix;
    final String label = "weblogic.domainUID=" + domainUid;

    TestUtils.createConfigMap(cmName, destDir, domainNS, label);
  }

  private String verifyApp() throws Exception {
    // get managed server pod name
    StringBuffer cmdStrBuff = new StringBuffer();
    cmdStrBuff
        .append("kubectl get pod -n ")
        .append(domainNS)
        .append(" -o=jsonpath='{.items[1].metadata.name}' | grep managed-server1");
    String msPodName = TestUtils.exec(cmdStrBuff.toString()).stdout();

    // access the application deployed in managed-server1
    cmdStrBuff = new StringBuffer();
    cmdStrBuff
        .append("kubectl -n ")
        .append(domainNS)
        .append(" exec -it ")
        .append(msPodName)
        .append(" -- bash -c ")
        .append("'curl http://" + msPodName + ":8001/sample_war/")
        .append("'");
    ExecResult exec = TestUtils.exec(cmdStrBuff.toString(), true);
    return exec.stdout();
  }
}

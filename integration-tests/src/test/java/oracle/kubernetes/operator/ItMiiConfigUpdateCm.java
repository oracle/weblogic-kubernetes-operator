// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.Operator.RestCertType;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Wdt Config Update with Model File(s) to existing MII domain
 *
 * <p>This test is used for domain config update by re-creating
 *    config map using model in image.
 */
public class ItMiiConfigUpdateCm extends MiiConfigUpdateBaseTest {
  private static Operator operator;
  private static String domainNS;
  private static String testClassName;
  private static StringBuffer namespaceList;

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
      assertNotNull(operator);
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
   * Create a domain without a JDBC DS using model in image and having config map
   * in the domain.yaml before deploying the domain. After the domain resources
   * is created, re-create the config map with model files that define a JDBC DataSource
   * and patch to change domain-level restart version to reload the model files,
   * generate new config and initiate a rolling restart.
   */
  @Test
  public void testMiiConfigUpdateNonJdbcCm() {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    boolean testCompletedSuccessfully = false;
    Domain domain = null;

    try {
      logTestBegin(testMethodName);
      LoggerHelper.getLocal().log(Level.INFO,
          "Creating Domain & waiting for the script to complete execution");

      // create domain w/o JDBC DS using the image created by MII
      boolean createDS = false;
      domain = createDomainUsingMii(createDS, domainNS, testClassName);
      assertNotNull(domain, "Failed to create a domain");

      // copy model files that contains JDBC DS to a dir, re-create cm to update config,
      // patch domain to change domain-level restart version and verify domain restarted successfully
      final String destDir = getResultDir() + "/samples/model-in-image-update";
      final String[] modelFiles = {"model.jdbc.yaml", "model.jdbc.properties"};
      createCmAndPatchDomain(domain, destDir, modelFiles);

      // get JDBC DS prop values via WLST on server pod
      String jdbcResourceData = getJdbcResources(destDir, domain);

      // verify that JDBC DS is created by checking DS name, JNDI name and read timeout
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      final String[] jdbcResourcesToVerify =
          {"datasource.name.1=" + dsName,
              "datasource.jndiname.1=array(java.lang.String,['" + jndiName + "'])",
              "datasource.readTimeout.1=" + readTimeout_2};
      for (String prop : jdbcResourcesToVerify) {
        assertTrue(jdbcResourceData.contains(prop), prop + " is not found");
        LoggerHelper.getLocal().log(Level.INFO, prop + " exists");
      }

      testCompletedSuccessfully = true;
    } catch (Exception ex) {
      fail("FAILED - " + testMethodName);
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        try {
          TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        } catch (Exception ex) {
          LoggerHelper.getLocal().log(Level.INFO, "Failed to delete domain\n " + ex.getMessage());
        }
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create a domain with a JDBC DS using model in image and having config map
   * in the domain.yaml before deploying the domain. After the domain resources
   * is created, re-create the config map with a model file that define a JDBC DataSource
   * and patch to change domain-level restart version to reload the model file,
   * generate new config and initiate a rolling restart.
   */
  @Test
  public void testMiiConfigUpdateJdbcCm() {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    boolean testCompletedSuccessfully = false;
    // The value for property oracle.net.CONNECT_TIMEOUT set in model.jdbc.yaml
    // for a client to establish an Oracle Net connection to the database instance.
    final String connTimeout = "5200";
    Domain domain = null;

    try {
      logTestBegin(testMethodName);
      LoggerHelper.getLocal().log(Level.INFO,
          "Creating Domain & waiting for the script to complete execution");

      // create domain w JDBC DS using the image created by MII
      boolean createDS = true;
      domain = createDomainUsingMii(createDS, domainNS, testClassName);
      assertNotNull(domain, "Failed to create a domain");

      // get JDBC DS prop values via WLST on server pod
      final String destDir = getResultDir() + "/samples/model-in-image-update";
      String jdbcResourceData = getJdbcResources(destDir, domain);

      // verify that JDBC DS is created by checking DS name, JNDI name and read timeout
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      final String[] jdbcResourcesToVerify1 =
          {"datasource.name.1=" + dsName,
              "datasource.jndiname.1=array(java.lang.String,['" + jndiName + "'])",
              "datasource.readTimeout.1=" + readTimeout_1};
      for (String prop : jdbcResourcesToVerify1) {
        assertTrue(jdbcResourceData.contains(prop), prop + " is not found");
        LoggerHelper.getLocal().log(Level.INFO, prop + " exists");
      }

      // copy model files that contains JDBC DS to a dir, re-create cm to update config,
      // patch domain to change domain-level restart version and verify domain restarted successfully
      final String[] modelFiles = {"model.jdbc.yaml", "model.jdbc.properties"};
      createCmAndPatchDomain(domain, destDir, modelFiles);

      // get JDBC DS props via WLST on server pod
      jdbcResourceData = getJdbcResources(destDir, domain);

      // verify that JDBC DS is updated by checking JDBC DS name,
      // connection timeout and read timeout via WLST on server pod
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      final String[] jdbcResourcesToVerify2 =
          {"datasource.name.1=" + dsName,
              "datasource.readTimeout.1=" + readTimeout_2,
              "datasource.connectionTimeout.1=" + connTimeout};
      for (String prop : jdbcResourcesToVerify2) {
        assertTrue(jdbcResourceData.contains(prop), prop + " is not found");
        LoggerHelper.getLocal().log(Level.INFO, prop + " exists");
      }

      testCompletedSuccessfully = true;
    } catch (Exception ex) {
      fail("FAILED - " + testMethodName);
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        try {
          TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        } catch (Exception ex) {
          LoggerHelper.getLocal().log(Level.INFO, "Failed to delete domain\n " + ex.getMessage());
        }
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create a domain using model in image and having config map in the domain.yaml
   * The model file has predeployed application and a JDBC DataSource. After the domain
   * resources is created, re-create the config map with a model file that removes the JDBC
   * and application through the ! notation and patch the domain to change domain
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
    boolean testCompletedSuccessfully = false;
    Domain domain = null;

    try {
      logTestBegin(testMethodName);

      // create Domain with JDBC DataSource and application using the image created by MII
      LoggerHelper.getLocal().log(Level.INFO,
          "Creating Domain & waiting for the script to complete execution");
      boolean createDS = true;
      domain = createDomainUsingMii(createDS, domainNS, testClassName);
      assertNotNull(domain, "Failed to create a domain");

      // verify that JDBC DS is created by checking JDBC DS name and read timeout
      // verify the test result by checking override config file on server pod
      // verify that application is accessible from inside the managed server pod
      String jdbcDsStr = getJndiName(domain);
      assertTrue(verifyApp().contains("Hello"), "Application is not found");

      // delete config and application using new model file
      wdtConfigDeleteOverride(domain);

      // patch to change domain-level restart version and verify domain restarted successfully
      modifyDomainYamlWithRestartVersion(domain);

      // verify the test result by getting JDBC DS via WLST on server pod
      String destDir = getResultDir() + "/samples/model-in-image-override";
      String jdbcResources = getJdbcResources(destDir, domain);
      assertFalse(jdbcResources.contains(dsName), dsName + " is found");

      // verify the app access returns 404
      assertFalse(verifyApp().contains("Hello"), "Application is found");

      testCompletedSuccessfully = true;
    } catch (Exception ex) {
      fail("FAILED - " + testMethodName);
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        try {
          TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        } catch (Exception ex) {
          LoggerHelper.getLocal().log(Level.INFO, "Failed to delete domain.\n " + ex.getMessage());
        }
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  private void wdtConfigDeleteOverride(Domain domain) throws Exception {
    final String appName = "myear";
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

    try {
      TestUtils.createConfigMap(cmName, destDir, domainNS, label);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail("Failed to create cm.\n", ex.getCause());
    }
  }

  private String verifyApp() throws Exception {
    String appStr = "";
    // get managed server pod name
    StringBuffer cmdStrBuff = new StringBuffer();
    cmdStrBuff
        .append("kubectl get pod -n ")
        .append(domainNS)
        .append(" -o=jsonpath='{.items[1].metadata.name}' | grep managed-server1");
    String msPodName = TestUtils.execOrAbortProcess(cmdStrBuff.toString()).stdout();

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

    try {
      ExecResult exec = TestUtils.execOrAbortProcess(cmdStrBuff.toString(), true);
      appStr = exec.stdout();
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Varify app failed:\n " + ex.getMessage());
      ex.printStackTrace();
    }

    return appStr;
  }
}

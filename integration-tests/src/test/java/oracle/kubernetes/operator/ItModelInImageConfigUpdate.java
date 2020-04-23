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
  //private static Domain domain;
  private static String domainNS;
  private static String testClassName;
  private static StringBuffer namespaceList;
  private static final String configMapSuffix = "-mii-config-map";
  private static final String dsName = "MyDataSource";
  private static String jndiName = "jdbc/generic1";
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
      domain = createDomainUsingMii(createDS);
      Assertions.assertNotNull(domain, "Failed to create a domain");

      // copy model files that contains JDBC DS to a dir to re-create cm
      final String destDir = getResultDir() + "/samples/model-in-image-update";
      copyTestModelFiles(destDir);

      // re-create cm to update config and verify cm is created successfylly
      wdtConfigUpdate(destDir, domain);

      // update domain yaml with restartVersion,
      // apply the domain yaml and verify domain restarted successfully
      modifyDomainYamlWithRestartVersion(domain, domainNS);

      // verify that JNDI name exists by checking updated config file on server pod
      String jdbcDsStr = getJndiName(domain);
      Assertions.assertTrue(jdbcDsStr.contains("<jndi-name>" + jndiName + "</jndi-name>"),
          "JDBC DS wasn't updated");
      LoggerHelper.getLocal().log(Level.INFO, jndiName + " found");

      // get JDBC DS prop values via WLST on server pod
      String jdbcResourceData = getJdbcResources(destDir, domain);

      // verify that JDBC DS is created by checking JDBC DS name and read timeout
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      final String[] jdbcResourcesToVerify =
          {"datasource.name.1=" + dsName,
              "datasource.readTimeout.1=" + readTimeout_2};
      for (String prop : jdbcResourcesToVerify) {
        Assertions.assertTrue(jdbcResourceData.contains(prop), prop + " is not found");
        LoggerHelper.getLocal().log(Level.INFO, prop + " exists");
      }

      testCompletedSuccessfully = true;
    } catch (Exception ex) {
      Assertions.fail("FAILED - " + testMethodName);
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
   * Create a domain without a JDBC DS using model in image and having configmap
   * in the domain.yaml before deploying the domain. After deploying the domain crd,
   * create a new image with diff tag name and model files that define a JDBC DataSource
   * and update the domain crd to change image name to reload the model,
   * generate new config and initiate a rolling restart.
   */
  @Test
  public void testMiiConfigUpdateNonJdbcImage() {
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
      domain = createDomainUsingMii(createDS);
      Assertions.assertNotNull(domain, "Failed to create a domain");

      // create image with a new tag to update config and verify image created successfully
      Map<String, Object> domainMap = domain.getDomainMap();
      final String imageName = (String) domainMap.get("image") + "_nonjdbc";
      final String wdtModelFile = "model.jdbc.yaml";
      final String wdtModelPropFile = "model.jdbc.properties";
      createDomainImage(domainMap, imageName, wdtModelFile, wdtModelPropFile);

      // push the image to docker repository
      if (BaseTest.SHARED_CLUSTER) {
        TestUtils.loginAndPushImageToOcir(imageName);
      }

      // update domain yaml with new image tag and
      // apply the domain yaml, verify domain rolling-restarted successfully
      modifyDomainYamlWithImageName(domain, domainNS, imageName);

      // verify that JNDI name exists by checking updated config file on server pod
      String jdbcDsStr = getJndiName(domain);
      Assertions.assertTrue(jdbcDsStr.contains("<jndi-name>" + jndiName + "</jndi-name>"),
          "JDBC DS wasn't updated");
      LoggerHelper.getLocal().log(Level.INFO, jndiName + " found");

      // get JDBC DS prop values via WLST on server pod
      final String destDir = getResultDir() + "/samples/model-in-image-update";
      String jdbcResourceData = getJdbcResources(destDir, domain);

      // verify that JDBC DS is created by checking JDBC DS name and read timeout
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      final String[] jdbcResourcesToVerify =
          {"datasource.name.1=" + dsName,
              "datasource.readTimeout.1=" + readTimeout_2};
      for (String prop : jdbcResourcesToVerify) {
        Assertions.assertTrue(jdbcResourceData.contains(prop), prop + " is not found");
        LoggerHelper.getLocal().log(Level.INFO, prop + " exists");
      }

      testCompletedSuccessfully = true;
    } catch (Exception ex) {
      Assertions.fail("FAILED - " + testMethodName);
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
   * Create a domain with a JDBC DS using model in image and having configmap
   * in the domain.yaml before deploying the domain. After deploying the domain crd,
   * re-create the configmap with a model file that define a JDBC DataSource
   * and update the domain crd to change domain restartVersion to reload the model,
   * generate new config and initiate a rolling restart.
   */
  @Test
  public void testMiiConfigUpdateJdbcCm() {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    boolean testCompletedSuccessfully = false;
    final String connTimeout = "5200";
    Domain domain = null;

    try {
      logTestBegin(testMethodName);
      LoggerHelper.getLocal().log(Level.INFO,
          "Creating Domain & waiting for the script to complete execution");

      // create domain w JDBC DS using the image created by MII
      boolean createDS = true;
      domain = createDomainUsingMii(createDS);
      Assertions.assertNotNull(domain, "Failed to create a domain");

      // verify that JNDI name exists by checking updated config file on server pod
      String jdbcDsStr = getJndiName(domain);
      Assertions.assertTrue(jdbcDsStr.contains("<jndi-name>" + jndiName + "</jndi-name>"),
          "JDBC DS wasn't updated");
      LoggerHelper.getLocal().log(Level.INFO, jndiName + " found");

      // get JDBC DS prop values via WLST on server pod
      final String destDir = getResultDir() + "/samples/model-in-image-update";
      String jdbcResourceData = getJdbcResources(destDir, domain);

      // verify that JDBC DS is created by checking JDBC DS name and read timeout
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      String[] jdbcResourcesToVerify1 =
          {"datasource.name.1=" + dsName,
              "datasource.readTimeout.1=" + readTimeout_1};
      for (String prop : jdbcResourcesToVerify1) {
        Assertions.assertTrue(jdbcResourceData.contains(prop), prop + " is not found");
        LoggerHelper.getLocal().log(Level.INFO, prop + " exists");
      }

      // copy model files that contain JDBC DS to a dir to re-create cm
      copyTestModelFiles(destDir);

      // re-create cm to update config and verify cm is created successfylly
      wdtConfigUpdate(destDir, domain);

      // update domain yaml with restartVersion and
      // apply the domain yaml, verify domain restarted successfully
      modifyDomainYamlWithRestartVersion(domain, domainNS);

      // get JDBC DS props via WLST on server pod
      jdbcResourceData = getJdbcResources(destDir, domain);

      // verify that JDBC DS is updated by checking JDBC DS name,
      // connection timeout and read timeout via WLST on server pod
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      String[] jdbcResourcesToVerify2 =
          {"datasource.name.1=" + dsName,
              "datasource.readTimeout.1=" + readTimeout_2,
              "datasource.connectionTimeout.1=" + connTimeout};
      for (String prop : jdbcResourcesToVerify2) {
        Assertions.assertTrue(jdbcResourceData.contains(prop), prop + " is not found");
        LoggerHelper.getLocal().log(Level.INFO, prop + " exists");
      }

      testCompletedSuccessfully = true;
    } catch (Exception ex) {
      Assertions.fail("FAILED - " + testMethodName);
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
   * Create a domain with a JDBC DS using model in image and having configmap
   * in the domain.yaml before deploying the domain. After deploying the domain crd,
   * create a new image with diff tag name and model files that define a JDBC DataSource
   * and update the domain crd to change image name to reload the model,
   * generate new config and initiate a rolling restart.
   */
  @Test
  public void testMiiConfigUpdateJdbcImage() {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    boolean testCompletedSuccessfully = false;
    final String connTimeout = "5200";
    Domain domain = null;

    try {
      logTestBegin(testMethodName);
      LoggerHelper.getLocal().log(Level.INFO,
          "Creating Domain & waiting for the script to complete execution");

      // create domain w JDBC DS using the image created by MII
      boolean createDS = true;
      domain = createDomainUsingMii(createDS);
      Assertions.assertNotNull(domain, "Failed to create a domain");

      // verify that JNDI name exists by checking updated config file on server pod
      String jdbcDsStr = getJndiName(domain);
      Assertions.assertTrue(jdbcDsStr.contains("<jndi-name>" + jndiName + "</jndi-name>"),
          "JDBC DS wasn't updated");
      LoggerHelper.getLocal().log(Level.INFO, jndiName + " found");

      // get JDBC DS prop values via WLST on server pod
      final String destDir = getResultDir() + "/samples/model-in-image-update";
      String jdbcResourceData = getJdbcResources(destDir, domain);

      // verify that JDBC DS is created by checking JDBC DS name and read timeout
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      String[] jdbcResourcesToVerify1 =
          {"datasource.name.1=" + dsName,
              "datasource.readTimeout.1=" + readTimeout_1};
      for (String prop : jdbcResourcesToVerify1) {
        Assertions.assertTrue(jdbcResourceData.contains(prop), prop + " is not found");
        LoggerHelper.getLocal().log(Level.INFO, prop + " exists");
      }

      // create image with a new tag to update config and verify image is created successfylly
      Map<String, Object> domainMap = domain.getDomainMap();
      final String imageName = (String) domainMap.get("image") + "_jdbc";
      final String wdtModelFile = "model.jdbc.yaml";
      final String wdtModelPropFile = "model.jdbc.properties";
      createDomainImage(domainMap, imageName, wdtModelFile, wdtModelPropFile);

      // push the image to docker repository
      if (BaseTest.SHARED_CLUSTER) {
        TestUtils.loginAndPushImageToOcir(imageName);
      }

      // update domain yaml with new image tag and
      // apply the domain yaml, verify domain rolling-restarted
      modifyDomainYamlWithImageName(domain, domainNS, imageName);

      // get JDBC DS prop values via WLST on server pod
      jdbcResourceData = getJdbcResources(destDir, domain);

      // verify that JDBC DS is updated by checking JDBC DS name,
      // connection timeout and read timeout via WLST on server pod
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      String[] jdbcResourcesToVerify2 =
          {"datasource.name.1=" + dsName,
              "datasource.readTimeout.1=" + readTimeout_2,
              "datasource.connectionTimeout.1=" + connTimeout};
      for (String prop : jdbcResourcesToVerify2) {
        Assertions.assertTrue(jdbcResourceData.contains(prop), prop + " is not found");
        LoggerHelper.getLocal().log(Level.INFO, prop + " exists");
      }

      testCompletedSuccessfully = true;
    } catch (Exception ex) {
      Assertions.fail("FAILED - " + testMethodName);
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
    boolean testCompletedSuccessfully = false;
    Domain domain = null;

    try {
      logTestBegin(testMethodName);

      // create Domain with JDBC DataSource and application using the image created by MII
      LoggerHelper.getLocal().log(Level.INFO,
          "Creating Domain & waiting for the script to complete execution");
      boolean createDS = true;
      domain = createDomainUsingMii(createDS);
      Assertions.assertNotNull(domain, "Failed to create a domain");

      // verify that JDBC DS is created by checking JDBC DS name and read timeout
      // verify the test result by checking override config file on server pod
      // verify that application is accessible from inside the managed server pod
      String jdbcDsStr = getJndiName(domain);
      Assertions.assertTrue(verifyApp().contains("Hello"), "Application is not found");

      // delete config and application using new model file
      wdtConfigDeleteOverride(domain);

      // update domain yaml with restartVersion and
      // apply the domain yaml, verify domain restarted successfully
      modifyDomainYamlWithRestartVersion(domain, domainNS);

      // verify the test result by getting JDBC DS via WLST on server pod
      String destDir = getResultDir() + "/samples/model-in-image-override";
      String jdbcResources = getJdbcResources(destDir, domain);
      Assertions.assertFalse(jdbcResources.contains(dsName), dsName + " is found");

      // verify the app access returns 404
      Assertions.assertFalse(verifyApp().contains("Hello"), "Application is found");

      testCompletedSuccessfully = true;
    } catch (Exception ex) {
      Assertions.fail("FAILED - " + testMethodName);
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

  private void copyTestModelFiles(String destDir) {
    LoggerHelper.getLocal().log(Level.INFO, "Creating configMap");
    String origDir = BaseTest.getProjectRoot()
        + "/integration-tests/src/test/resources/model-in-image";
    final String modelFile = "model.jdbc.yaml";
    final String propFile = "model.jdbc.properties";

    try {
      Files.deleteIfExists(Paths.get(destDir));
      Files.createDirectories(Paths.get(destDir));

      TestUtils.copyFile(origDir + "/" + modelFile, destDir + "/" + modelFile);
      TestUtils.copyFile(origDir + "/" + propFile, destDir + "/" + propFile);
    } catch (Exception ex) {
      ex.printStackTrace();
      Assertions.fail("Failed to copy model files", ex.getCause());
    }
  }

  private Domain createDomainUsingMii(boolean createDS) {
    final String cmFile = "model.empty.properties";
    String wdtModelFile = "model.wls.yaml";
    String wdtModelPropFile = "model.properties";
    Domain domain = null;

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

    try {
      domain = createMiiDomainWithConfigMap(
        testClassName,
        domainNS,
        wdtModelFile,
        wdtModelPropFile,
        cmFile,
        WdtDomainType.WLS.geWdtDomainType());
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "FAILURE: command: "
          + paramBuff
          + " failed \n"
          + ex.getMessage());

      ex.printStackTrace();
    }

    return domain;
  }

  private void wdtConfigUpdate(String destDir, Domain domain) {
    LoggerHelper.getLocal().log(Level.INFO, "Creating configMap...");

    // Re-create config map after deploying domain crd
    final String domainUid = domain.getDomainUid();
    final String cmName = domainUid + configMapSuffix;
    final String label = "weblogic.domainUID=" + domainUid;

    try {
      TestUtils.createConfigMap(cmName, destDir, domainNS, label);
    } catch (Exception ex) {
      ex.printStackTrace();
      Assertions.fail("Failed to create cm.\n", ex.getCause());
    }
  }

  private String getJndiName(Domain domain) {
    ExecResult result = null;
    String jdbcDsStr = "";
    // get domain name
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainName = (String) domainMap.get("domainName");

    // check JDBC DS update
    StringBuffer cmdStrBuff = new StringBuffer("kubectl -n ");
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

    try {
      LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdStrBuff);
      result = TestUtils.exec(cmdStrBuff.toString());
      LoggerHelper.getLocal().log(Level.INFO, "JDBC DS info from server pod: " + result.stdout());
      jdbcDsStr  = result.stdout();
    } catch (Exception ex) {
      StringBuffer errorMsg = new StringBuffer("FAILURE: command: ");
      errorMsg
          .append(cmdStrBuff)
          .append(" failed, returned ")
          .append(result.stdout())
          .append("\n")
          .append(result.stderr());

      LoggerHelper.getLocal().log(Level.INFO, errorMsg + "\n" + ex.getMessage());
      ex.printStackTrace();
    }

    return jdbcDsStr;
  }

  private String getJdbcResources(String destDir, Domain domain) {
    ExecResult result = null;
    String jdbcDsStr = "";
    // get domain name
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainName = (String) domainMap.get("domainName");

    try {
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
      StringBuffer cmdStrBuff = new StringBuffer("kubectl get pod -n ");
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
      jdbcDsStr  = result.stdout();
      //clean up
      LoggerHelper.getLocal().log(Level.INFO, "Deleting: " + destDir + "/" + pyFileName);
      Files.deleteIfExists(Paths.get(destDir + "/" + pyFileName));
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Failed to get DS prop values.\n" + ex.getMessage());
      ex.printStackTrace();
    }

    return jdbcDsStr;
  }

  private void wdtConfigDeleteOverride(Domain domain) throws Exception {
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
      Assertions.fail("Failed to create cm.\n", ex.getCause());
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

    try {
      ExecResult exec = TestUtils.exec(cmdStrBuff.toString(), true);
      appStr = exec.stdout();
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Varify app failed:\n " + ex.getMessage());
      ex.printStackTrace();
    }

    return appStr;
  }
}

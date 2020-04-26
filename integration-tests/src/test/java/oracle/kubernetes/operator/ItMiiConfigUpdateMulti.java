// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.Operator.RestCertType;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Wdt Config Update with Model File(s) to existing MII domain
 *
 * <p>This test is used for domain config update with multiple model files
 *    using model in image.
 */

public class ItMiiConfigUpdateMulti extends MiiConfigUpdateBaseTest {
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
   * in the domain.yaml before deploying the domain. After the domain resources is created,
   * re-create the config map with multiple model files that define a JDBC DataSource.
   * Each model file overlaps the value of JDBC DS property oracle.net.CONNECT_TIMEOUT.
   * Patch to change domain-level restart version to reload the model file, generate
   * new config and initiate a rolling restart. The test verifies that loading order
   * of model files follows WDT rules and the model file loaded last take precedence
   */
  @Test
  public void testMiiMultiModelFilesLoadingOrderName() {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    boolean testCompletedSuccessfully = false;
    // The value for property oracle.net.CONNECT_TIMEOUT set in jdbc.20.yaml
    // for a client to establish an Oracle Net connection to the database instance.
    final String connTimeout = "5203";
    Domain domain = null;

    try {
      logTestBegin(testMethodName);
      LoggerHelper.getLocal().log(Level.INFO,
          "Creating Domain & waiting for the script to complete execution");

      // create domain w/o JDBC DS using the image created by MII
      boolean createDS = false;
      domain = createDomainUsingMii(createDS, domainNS, testClassName);
      assertNotNull(domain, "Failed to create a domain");

      // copy model files that contains JDBC DS to a dir to re-create cm
      final String destDir = getResultDir() + "/samples/model-in-image-update-multi1";
      String[] modelFiles =
          {"now.jdbc.10.yaml",
              "jdbc.20.yaml",
              "new.jdbc.10.yaml",
              "model.jdbc.yaml",
              "model.jdbc.properties",
              "cm.jdbc.yaml"};
      copyTestModelFiles(destDir, modelFiles);

      // re-create cm to update config and verify cm is created successfully
      wdtConfigUpdateCm(destDir, domain);

      // patch to change domain-level restart version and verify domain restarted successfully
      modifyDomainYamlWithRestartVersion(domain, domainNS);

      // get JDBC DS prop values via WLST on server pod
      String jdbcResourceData = getJdbcResources(destDir, domain);

      // verify that JDBC DS is updated, loading order of model files
      // follows WDT rules and overlapped connection timeout value is
      // from the last loaded model file, jdbc.20.yaml
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      final String[] jdbcResourcesToVerify =
          {"datasource.name.1=" + dsName,
              "datasource.jndiname.1=array(java.lang.String,['" + jndiName + "'])",
              "datasource.connectionTimeout.1=" + connTimeout};
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
   * Create a domain without a JDBC DS using model in image and having config map
   * in the domain.yaml before deploying the domain. After the domain resources is created,
   * re-create the image and config map with multiple model files that define a JDBC DataSource.
   * Each model file overlaps the value of JDBC DS property oracle.net.CONNECT_TIMEOUT.
   * Patch to change domain-level restart version to reload the model file,
   * generate new config and initiate a rolling restart. The test verifies
   * that the last model file loaded by config map take precedence
   */
  @Test
  public void testMiiMultiModelFilesLoadingOrderCm() {
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

      // create domain w/o JDBC DS using the image created by MII
      boolean createDS = false;
      domain = createDomainUsingMii(createDS, domainNS, testClassName);
      assertNotNull(domain, "Failed to create a domain");

      // update image by defining model and property files with JDBC DS
      // in the image and verify image created successfully
      Map<String, Object> domainMap = domain.getDomainMap();
      final String imageName = (String) domainMap.get("image");
      final String wdtModelFile = "model.jdbc.image.yaml";
      final String wdtModelPropFile = "model.jdbc.image.properties";
      wdtConfigUpdateImage(domainMap, imageName, wdtModelFile, wdtModelPropFile);

      // copy model files that contains JDBC DS to a dir to re-create cm
      final String destDir = getResultDir() + "/samples/model-in-image-update-multi2";
      String[] modelFiles =
          {"cm.jdbc.yaml",
              "model.jdbc.yaml",
              "model.jdbc.properties"};
      copyTestModelFiles(destDir, modelFiles);

      // re-create cm by defining model and property files with overlapped JDBC DS
      // and verify cm is created successfully
      wdtConfigUpdateCm(destDir, domain);

      // patch to change domain-level restart version and verify domain restarted successfully
      modifyDomainYamlWithRestartVersion(domain, domainNS);

      // get JDBC DS prop values via WLST on server pod
      String jdbcResourceData = getJdbcResources(destDir, domain);

      // verify that JDBC DS is updated, loading order of model files
      // follows WDT rules and overlapped connection timeout value is
      // from the last loaded model file, model.jdbc.yaml
      LoggerHelper.getLocal().log(Level.INFO, "Verify that JDBC DS is created");
      final String[] jdbcResourcesToVerify =
          {"datasource.name.1=" + dsName,
              "datasource.jndiname.1=array(java.lang.String,['" + jndiName + "'])",
              "datasource.connectionTimeout.1=" + connTimeout};
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
}

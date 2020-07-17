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
 * <p>This test is used for domain config update by updating
 *    the image tag using model in image.
 */
public class ItMiiConfigUpdateImage extends MiiConfigUpdateBaseTest {
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
   * is created, create a new image with diff tag name and model files that
   * define a JDBC DataSource and patch the domain to change image tag to reload
   * the model file, generate new config and initiate a rolling restart.
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
      domain = createDomainUsingMii(createDS, domainNS, testClassName);
      assertNotNull(domain, "Failed to create a domain");

      // create image with a new tag to update config and verify image created successfully
      // patch domain to change image tag and verify domain restarted successfully
      Map<String, Object> domainMap = domain.getDomainMap();
      final String imageName = (String) domainMap.get("image") + "_nonjdbc";
      final String wdtModelFile = "model.jdbc.yaml";
      final String wdtModelPropFile = "model.jdbc.properties";
      createImageAndPatchDomain(domain, imageName, wdtModelFile, wdtModelPropFile);

      // get JDBC DS prop values via WLST on server pod
      final String destDir = getResultDir() + "/samples/model-in-image-update";
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
   * is created, create a new image with diff tag name and model files that
   * define a JDBC DataSource and patch to change image tage to reload
   * the model file, generate new config and initiate a rolling restart.
   */
  @Test
  public void testMiiConfigUpdateJdbcImage() {
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
      String[] jdbcResourcesToVerify1 =
          {"datasource.name.1=" + dsName,
              "datasource.jndiname.1=array(java.lang.String,['" + jndiName + "'])",
              "datasource.readTimeout.1=" + readTimeout_1};
      for (String prop : jdbcResourcesToVerify1) {
        assertTrue(jdbcResourceData.contains(prop), prop + " is not found");
        LoggerHelper.getLocal().log(Level.INFO, prop + " exists");
      }

      // create image with a new tag to update config and verify image created successfully
      // patch domain to change image tag and verify domain restarted successfully
      Map<String, Object> domainMap = domain.getDomainMap();
      final String imageName = (String) domainMap.get("image") + "_jdbc";
      final String wdtModelFile = "model.jdbc.yaml";
      final String wdtModelPropFile = "model.jdbc.properties";
      createImageAndPatchDomain(domain, imageName, wdtModelFile, wdtModelPropFile);

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

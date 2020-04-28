// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.Operator.RestCertType;
import oracle.kubernetes.operator.utils.Secret;
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
 * <p>This test is used for domain config update by changing
 *    the secret to modify JDBC connection attributes using model in image.
 */
public class ItMiiConfigUpdateSecret extends MiiConfigUpdateBaseTest {
  private static Operator operator;
  private static String domainNS;
  private static String testClassName;
  private static StringBuffer namespaceList;
  private static int testNumber;

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
    testNumber = getNewSuffixCount();
  }

  /**
   * This method creates the result/pv root directories for the test.
   * Creates the operator if it's not running.
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
   * Create a domain without a JDBC DS using model in image and having configmap
   * in the domain.yaml before deploying the domain. After the domain resources
   * is created, create a secret containing username, encrypted password and
   * JDBC URL values for accessing MySQL, re-create the domain image with model files
   * that define a JDBC DS. Patch domain to change domain-level restart version
   * to reload the model, generate new config and initiate a rolling restart.
   * Verify that MySQL connection attributes defined in the secret are updated correctly
   * and a connection to MySQL is established
   */
  @Test
  public void testMiiConfigUpdatUsingSecret() {
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

      String destDir = getResultDir() + "/model-in-image-update-secret/mysql";
      String mysqldbport = String.valueOf(31306 + testNumber);
      createMySql(domain, destDir, mysqldbport);

      // build a webapp to deploy to the cluster
      buildApp(domain, "build-app.sh");

      // create a secret with JDBC URL, username and password
      final String hostName = TestUtils.getHostName();
      final String jdbcUrl = "jdbc:mysql://" + hostName + ":" + mysqldbport + "/";
      final String wdtModelFile = "model.mysql.yaml";
      final String wdtModelPropFile = "model.jdbc.properties";
      destDir = getResultDir() + "/model-in-image-update-secret/modelFile";
      Secret secret =
          createSecretImageAndPatchDomain(domain, jdbcUrl,
              mysqldbport, wdtModelFile, wdtModelPropFile);

      // verify that a connection to MySQL is established
      verifyConnToMySql(domain);

      // verify that MySQL username and JDBC URL are same as ones stored in the secret
      verifyUsernameAndJdbcUrl(domain, secret, destDir);

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

  private void verifyConnToMySql(Domain domain) {
    // access the application deployed on managed-server1
    Map<String, Object> domainMap = domain.getDomainMap();
    String managedServerNameBase = (String) domainMap.get("managedServerNameBase");
    String msPodName = domain.getDomainUid() + "-" + managedServerNameBase + "1";
    ExecResult result = null;

    StringBuffer accessAppCmd = new StringBuffer();
    accessAppCmd
        .append("kubectl -n ")
        .append(domainNS)
        .append(" exec -it ")
        .append(msPodName)
        .append(" -- bash -c ")
        .append("'curl http://" + msPodName + ":8001/ds_war/")
        .append("'");
    LoggerHelper.getLocal().log(Level.INFO, "Command to access the webapp: " + accessAppCmd);

    try {
      result = TestUtils.exec(accessAppCmd.toString());
    } catch (Exception ex) {
      ex.printStackTrace();
      StringBuffer errorMsg = new StringBuffer("FAILURE: command: ");
      errorMsg
          .append(accessAppCmd)
          .append(" failed, returned ")
          .append(result.stdout())
          .append("\n")
          .append(result.stderr());
      fail(errorMsg.toString());
    }

    String appReturn = result.stdout();
    LoggerHelper.getLocal().log(Level.INFO, "webapp returns: \n" + appReturn);

    // verify that a connection is able to be established to MySQL
    final String[] dbConnToVerify =
        {"you have reached server: " + managedServerNameBase,
            "Successfully looked up and got a connection to " + jndiName};
    for (String attrib : dbConnToVerify) {
      assertTrue(appReturn.contains(attrib), attrib + "> is not found");
      LoggerHelper.getLocal().log(Level.INFO, attrib + "> exists");
    }
  }

  private void verifyUsernameAndJdbcUrl(Domain domain, Secret secret, String destDir) {
    // get values of MYSQL username and JDBC URL via WLST on server pod
    String connAttrib = getUsernameAndJdbcUrlViaWlst(destDir, domain);
    LoggerHelper.getLocal().log(Level.INFO, "Got conn attrib values: " + connAttrib);

    // verify that the JDBC DS is created and username and JDBC Url are from the Secret
    LoggerHelper.getLocal().log(Level.INFO, "Verify the values of connection  attributes");
    final String[] connAttribToVerify =
        {"datasource.name.1=" + dsName,
            "datasource.jndiname.1=array(java.lang.String,['" + jndiName + "'])",
            "datasource.jdbcurl.1=" + secret.getJdbcUrl(),
            "datasource.username.1=" + secret.getUsername()};
    for (String attrib : connAttribToVerify) {
      assertTrue(connAttrib.contains(attrib), attrib + " is not found");
      LoggerHelper.getLocal().log(Level.INFO, attrib + " exists");
    }
  }

  private String getUsernameAndJdbcUrlViaWlst(String destDir, Domain domain) {
    // get domain namespace name and domain name
    Map<String, Object> domainMap = domain.getDomainMap();
    final String domainNS = domainMap.get("namespace").toString();
    final String domainName = (String) domainMap.get("domainName");
    final String adminPodName =
        domain.getDomainUid() + "-" + domain.getAdminServerName();
    ExecResult result = null;
    String jdbcDsStr = "";

    try {
      // copy verification file to test dir
      String origDir = BaseTest.getProjectRoot()
          + "/integration-tests/src/test/resources/model-in-image/scripts";
      String pyFileName = "verify-ds-secret.py";
      Files.createDirectories(Paths.get(destDir));
      TestUtils.copyFile(origDir + "/" + pyFileName, destDir + "/" + pyFileName);

      // replace var in verification file
      String tempDir = getResultDir() + "/configupdatetemp-" + domainNS;
      Files.createDirectories(Paths.get(tempDir));
      String content =
          new String(Files.readAllBytes(Paths.get(destDir + "/" + pyFileName)),
                                        StandardCharsets.UTF_8);
      content = content.replaceAll("DOMAINNAME", domainName);
      Files.write(
          Paths.get(tempDir, pyFileName),
          content.getBytes(StandardCharsets.UTF_8));

      // copy verification file to the pod
      StringBuffer cmdStrBuff = new StringBuffer("kubectl -n ");
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

      // run WLST to get connection attributes
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
      LoggerHelper.getLocal().log(Level.INFO, "Deleting: " + tempDir + "/" + pyFileName);
      Files.deleteIfExists(Paths.get(tempDir + "/" + pyFileName));
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Failed to get values of conn attrib.\n" + ex.getMessage());
      ex.printStackTrace();
    }

    return jdbcDsStr;
  }
}

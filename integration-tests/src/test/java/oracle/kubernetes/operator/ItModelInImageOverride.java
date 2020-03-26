// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.DomainCrd;
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
 * This JUnit test is used for testing
 * Wdt Config Override with Model File(s) to existing MII domain
 *
 * <p>This test is used for creating domain using model in image.
 */

public class ItModelInImageOverride extends MiiBaseTest {
  private static Operator operator;
  private static Domain domain;
  private static String domainNS;
  private static String testClassName;
  private static StringBuffer namespaceList;
  private static final String configMapSuffix = "-mii-config-map";
  private static final String dsName = "MyDataSource";
  private static final String readTimeout_1 = "30001";
  private static final String readTimeout_2 = "30002";

  /**
   * This method does the initialization of the integration test
   * properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception if initializing the application properties
   *          and creates directories for results fails.
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
   * @throws Exception exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    createResultAndPvDirs(testClassName);

    // create operator1
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
   * @throws Exception exception
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    tearDown(new Object() {
    }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

    LoggerHelper.getLocal().info("SUCCESS");
  }

  /**
   * Create a domain using model in image and having configmap in the domain.yaml
   * before deploying the domain. After deploying the domain crd,
   * re-create the configmap with a model file that define a JDBC DataSource
   * and update the domain crd to change domain restartVersion
   * to reload the model, generate new config and initiate a rolling restart.
   *
   * @throws Exception exception
   */
  @Test
  public void testMiiOverrideNonExistJdbc() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    boolean testCompletedSuccessfully = false;
    try {
      // create Domain using the image created by MII
      createDomainUsingMii();

      // override config
      wdtConfigOverride();

      // update domain yaml with restartVersion and
      // apply the domain yaml, verify domain restarted
      modifyDomainYamlWithRestartVersion();

      // verify the test result
      verifyJdbcOverride();
      String jdbcDsValues = getJdbcResourceValues();

      // verify JDBC DS name
      Assumptions.assumeTrue(jdbcDsValues.contains(dsName), dsName + " not found");
      LoggerHelper.getLocal().log(Level.INFO, dsName + " is found from WLST return values");
      // verify value of read timeout
      Assumptions.assumeTrue(jdbcDsValues.contains(readTimeout_1), "readTimeout not found");
      LoggerHelper.getLocal().log(Level.INFO, dsName
            + "oracle.jdbc.ReadTimeout=" + readTimeout_1 + " is found from WLST return values");

      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  private void createDomainUsingMii() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    // config map before deploying domain crd
    Map<String, Object> domainMap =
        createModelInImageMap(getNewSuffixCount(), testClassName);
    final String cmName = domainMap.get("domainUID") + configMapSuffix;
    domainMap.put("namespace", domainNS);
    // params passed to mii
    domainMap.put("wdtModelFile", "./model.wls.yaml");
    domainMap.put("wdtModelPropertiesFile", "./model.empty.properties");
    // params to create cm
    String cmModelFile = "./model.properties";
    domainMap.put("miiConfigMap", cmName);
    domainMap.put("miiConfigMapFileOrDir", cmModelFile);

    // create domain and verify
    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();
  }

  private void wdtConfigOverride() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Creating configMap");
    String origDir = BaseTest.getProjectRoot()
        + "/integration-tests/src/test/resources/model-in-image";
    String origModelFile = origDir + "/model.jdbc.yaml";
    String origPropFile = origDir + "/model.jdbc.properties";
    String destDir = getResultDir() + "/samples/model-in-image-override";;
    String destModelFile = destDir + "/model.jdbc_2.yaml";
    String destPropFile = destDir + "/model.jdbc_2.properties";
    Files.createDirectories(Paths.get(destDir));

    TestUtils.copyFile(origModelFile, destModelFile);
    TestUtils.copyFile(origPropFile, destPropFile);

    // Re-create config map after deploying domain crd
    final String domainUid = domain.getDomainUid();
    final String cmName = domainUid + configMapSuffix;
    final String label = "weblogic.domainUID=" + domainUid;

    TestUtils.createConfigMap(cmName, destDir, domainNS, label);
  }

  private void modifyDomainYamlWithRestartVersion()
      throws Exception {
    String originalYaml =
        getUserProjectsDir()
            + "/weblogic-domains/"
            + domain.getDomainUid()
            + "/domain.yaml";

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    Map<String, String> objectNode = new HashMap();
    objectNode.put("restartVersion", "v1.1");
    crd.addObjectNodeToDomain(objectNode);
    String modYaml = crd.getYamlTree();
    LoggerHelper.getLocal().log(Level.INFO, modYaml);

    // Write the modified yaml to a new file
    Path path = Paths.get(getUserProjectsDir()
        + "/weblogic-domains/"
        + domain.getDomainUid(), "modified.domain.yaml");
    LoggerHelper.getLocal().log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, modYaml.getBytes(charset));

    // Apply the new yaml to update the domain crd
    LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f {0}", path.toString());
    ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
    LoggerHelper.getLocal().log(Level.INFO, exec.stdout());
    LoggerHelper.getLocal().log(Level.INFO, "Verifying if the domain is restarted");
    domain.verifyDomainRestarted();
  }

  private void verifyJdbcOverride() throws Exception {
    // get domain name
    StringBuffer cmdStrBuff = new StringBuffer("kubectl get domain -n ");
    cmdStrBuff
        .append(domainNS)
        .append(" -o=jsonpath='{.items[0].metadata.name}'");

    LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdStrBuff);
    ExecResult result = TestUtils.exec(cmdStrBuff.toString());
    String domainName = result.stdout();
    LoggerHelper.getLocal().log(Level.INFO, "Domain name is: " + domainName);

    // check JDBC DS override
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
        .append(" && grep -R jdbc/generic1")
        .append("'");

    LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdStrBuff);
    result = TestUtils.exec(cmdStrBuff.toString());
    LoggerHelper.getLocal().log(Level.INFO, "JDBC DS info from server pod: " + result.stdout());

    Assumptions.assumeTrue(result.stdout().contains("<jndi-name>jdbc/generic1</jndi-name>"),
        "JDBC DS doesn't override");
  }

  /**
   * Create a JDBC system resource in domain.
   *
   * @param domain - Domain object
   * @throws Exception JDBC resource creation fails
   */
  private String getJdbcResourceValues() throws Exception {
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
        + "/integration-tests/src/test/resources/model-in-image/scripts/";
    String pyFileName = "verify-jdbc-resource.py";
    String destDir = getResultDir() + "/samples/model-in-image/scripts/";;
    Files.createDirectories(Paths.get(destDir));
    TestUtils.copyFile(origDir + pyFileName, destDir + pyFileName);

    // replace var in verification file
    String tempDir = getResultDir() + "/jdbcoverridetemp-" + domainNS;
    Files.createDirectories(Paths.get(tempDir));
    String content =
        new String(Files.readAllBytes(Paths.get(destDir + pyFileName)), StandardCharsets.UTF_8);
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
        .append(" -- bash -c 'mkdir ")
        .append(BaseTest.getAppLocationInPod())
        .append("'");
    LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdStrBuff);
    TestUtils.exec(cmdStrBuff.toString(), true);

    LoggerHelper.getLocal().log(Level.INFO, "====AppLoc: " + BaseTest.getAppLocationInPod());
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

    LoggerHelper.getLocal().log(Level.INFO, "WLST returns: " + result.stdout());

    return result.stdout();
  }
}

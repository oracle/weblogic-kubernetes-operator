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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
 * Simple JUnit test file used for testing Model in Image.
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

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
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
   * This method gets called before every test. It creates the result/pv root directories
   * for the test. Creates the operator and domain if its not running.
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
   * and update the domain crd to new config map and change domain
   * restartVersion to reload the model, generate new config and initiate a
   * rolling restart.
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
    String destDir = getResultDir() + "/samples/model-in-image";;
    String destModelFile = destDir + "/model.jdbc_2.yaml";
    String destPropFile = destDir + "/model.jdbc_2.properties";

    TestUtils.copyFile(origModelFile, destModelFile);
    TestUtils.copyFile(origPropFile, destPropFile);
    Set<String> cmModelFileSet = new HashSet<>();

    // Re-create config map after deploying domain crd
    final String domainUid = domain.getDomainUid();
    final String cmName = domainUid + configMapSuffix;
    cmModelFileSet.add(destModelFile);
    cmModelFileSet.add(destPropFile);
    TestUtils.createConfigMapWMultiModels(cmName, cmModelFileSet, domainUid, domainNS);
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
    StringBuffer cmdKubectlSh = new StringBuffer("kubectl get domain -n ");
    cmdKubectlSh
        .append(domainNS)
        .append(" -o=jsonpath='{.items[0].metadata.name}'");

    LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdKubectlSh);
    ExecResult result = TestUtils.exec(cmdKubectlSh.toString());
    String domainName = result.stdout();
    LoggerHelper.getLocal().log(Level.INFO, "Domain name is: " + domainName);

    // check JDBC DS override
    cmdKubectlSh = new StringBuffer("kubectl -n ");
    cmdKubectlSh
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

    LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdKubectlSh);
    result = TestUtils.exec(cmdKubectlSh.toString());
    LoggerHelper.getLocal().log(Level.INFO, "JDBC DS info from server pod: " + result.stdout());

    Assumptions.assumeTrue(result.stdout().contains("<jndi-name>jdbc/generic1</jndi-name>"),
        "JDBC DS doesn't override");
  }
}

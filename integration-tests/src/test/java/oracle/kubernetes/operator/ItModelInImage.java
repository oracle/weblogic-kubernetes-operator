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
 * Simple JUnit test file used for testing Model in Image.
 *
 * <p>This test is used for creating domain using model in image.
 */

public class ItModelInImage extends BaseTest {
  private static Operator operator;
  private static String domainNS;
  private static String testClassName;
  private static StringBuffer namespaceList;

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
      /* ArrayList<String> targetDomainsNS = new ArrayList<String>();
      targetDomainsNS.add("default");
      operatorMap.put("domainNamespaces", targetDomainsNS); */
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
   * Create a domain using model in image with model yaml and model properties
   * file in the image. Deploy the domain, verify the running domain has
   * the correct configuration as given in the image.
   *
   * @throws Exception exception
   */
  @Test
  public void testMiiWithNoConfigMap() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap =
          createModelInImageMap(getNewSuffixCount(), testClassName);
      domainMap.put("namespace", domainNS);
      domainMap.put("wdtModelFile", "./model.wls.yaml");
      domainMap.put("wdtModelPropertiesFile", "./model.properties");

      domain = TestUtils.createDomain(domainMap);
      // domain = new Domain(domainMap, true, false);
      domain.verifyDomainCreated();

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
   * before deploying the domain. Verify the running domain has configuration given
   * in configmap
   * @throws Exception exception
   */
  @Test
  public void testMiiWithConfigMapBeforeDeployingDomain() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap =
          createModelInImageMap(getNewSuffixCount(), testClassName);
      // config map before deploying domain crd
      String cmName = domainMap.get("domainUID") + "-mii-config-map";
      String cmFile = "./model.cm.properties";

      domainMap.put("namespace", domainNS);
      domainMap.put("wdtModelFile", "./model.wls.yaml");
      domainMap.put("wdtModelPropertiesFile", "./model.empty.properties");

      domainMap.put("miiConfigMap", cmName);
      domainMap.put("miiConfigMapFileOrDir", cmFile);

      domain = TestUtils.createDomain(domainMap);
      //domain = new Domain(domainMap, true, false);
      domain.verifyDomainCreated();

      //ToDo: access MS using port given in configmap props
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
   * before deploying the domain. After deploying the domain crd, create a new
   * config map and update the domain crd to new config map and change domain
   * restartVersion to reload the model, generate new config and initiate a
   * rolling restart.
   * @throws Exception exception
   */
  @Test
  public void testMiiWithNewConfigMapAfterDeployingDomain() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap =
          createModelInImageMap(getNewSuffixCount(), testClassName);
      // config map before deploying domain crd
      String cmName = domainMap.get("domainUID") + "-mii-config-map1";
      String cmFile = "./model.properties";
      domainMap.put("namespace", domainNS);
      domainMap.put("wdtModelFile", "./model.wls.yaml");
      domainMap.put("wdtModelPropertiesFile", "./model.empty.properties");

      domainMap.put("miiConfigMap", cmName);
      domainMap.put("miiConfigMapFileOrDir", cmFile);

      domain = TestUtils.createDomain(domainMap);
      // domain = new Domain(domainMap, true, false);
      domain.verifyDomainCreated();
      //ToDo: access MS using port given in the configmap props

      // config map after deploying domain crd
      cmName = domainMap.get("domainUID") + "-mii-config-map2";
      cmFile = getResultDir() + "/samples/model-in-image/model.cm.properties";
      TestUtils.createConfigMap(cmName, cmFile, domainNS);

      // update domain yaml with new config map, restartVersion and
      // apply the domain yaml, verify domain restarted
      modifyDomainYamlWithNewConfigMapAndDomainRestartVersion(
          cmName, domain);
      //ToDo: access MS using port given in the new configmap props
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Negative test. overridesConfigMap should fail with model in image domain.
   * Create a domain using model in image and deploy the domain.
   * After deploying the domain, update domain crd with overrides configmap and
   * apply the crd.
   * Verify that the domain deployment fails with proper error in the operator log.
   *
   * @throws Exception exception
   */
  //@Test
  public void testModelInImageNegativeOverridesUseCase() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap =
          createModelInImageMap(getNewSuffixCount(), testClassName);
      domainMap.put("namespace", domainNS);
      String domainUid = (String)domainMap.get("domainUID");
      String overridesConfigMap = domainUid + "-mii-overrides-config-map";
      String overridesConfigMapFileOrDir = "./model.cm.properties";
      // the below override attributes are just place holders,
      // they are not used while creating the domain first time
      domainMap.put("overridesConfigMap", overridesConfigMap);
      domainMap.put("overridesConfigMapFileOrDir", overridesConfigMapFileOrDir);

      // domain = TestUtils.createDomain(domainMap);
      domain = new Domain(domainMap, true, false);
      domain.verifyDomainCreated();

      domain.createMIIConfigMap("overridesConfigMap",
          "overridesConfigMapFileOrDir");

      //append overridesConfigMap to domain.yaml
      domain.appendOverridesConfigMapAndApply();


      //ToDo: access MS using port given in configmap props

      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  private void modifyDomainYamlWithNewConfigMapAndDomainRestartVersion(
      String cmName, Domain domain)
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

    //change config map name to new config map
    modYaml.replaceAll((String)domain.getDomainMap().get("miiConfigMap"), cmName);

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

}

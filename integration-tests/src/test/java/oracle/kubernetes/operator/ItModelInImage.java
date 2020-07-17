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
import oracle.kubernetes.operator.utils.Secret;
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

public class ItModelInImage extends MiiBaseTest {
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
   * before deploying the domain. After deploying the domain crd, create a new
   * config map and update the domain crd to new config map and change domain
   * restartVersion to reload the model, generate new config and initiate a
   * rolling restart.
   * @throws Exception exception
   */
  @Test
  public void testMiiWithConfigMapBothBeforeAndAfterDeployingDomain() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {

      domain = createMiiDomainWithConfigMap(testClassName, domainNS, "./model.wls.yaml",
          "./model.empty.properties", "./model.properties", "WLS");
      Map<String, Object> domainMap = domain.getDomainMap();
      //ToDo: access MS using port given in the configmap props

      // config map after deploying domain crd
      String cmName = domainMap.get("domainUID") + "-mii-config-map2";
      String cmFile = getResultDir() + "/samples/model-in-image/model.cm.properties";
      TestUtils.createConfigMap(cmName, cmFile, domainNS,
          " weblogic.domainUID=" + domainMap.get("domainUID"));

      // update domain yaml with new config map, restartVersion and
      // apply the domain yaml, verify domain restarted
      modifyDomainYamlWithNewConfigMapAndDomainRestartVersion(
          cmName, domain);
      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the domain is restarted");
      domain.verifyDomainRestarted();
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
   * Create a domain using model in image with model yaml and model properties file in the image.
   * Change the weblogic credentials and verify the pods can restart with changed password
   *
   * @throws Exception exception
   */
  @Test
  public void testCredentialsChange() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap
          = createModelInImageMap(getNewSuffixCount(), testClassName);
      domainMap.put("namespace", domainNS);
      domainMap.put("wdtModelFile", "./model.wls.yaml");
      domainMap.put("wdtModelPropertiesFile", "./model.properties");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();

      // delete and recreate the weblogic-credentials secret
      Secret secret = new Secret(domain.getDomainNs(), domain.getDomainUid()
          + "-weblogic-credentials", "system", "gumby1234");
      // use a different secret name for runtimeEncryptionSecret with original credentials
      secret = new Secret(domain.getDomainNs(), domain.getDomainUid()
          + "-model-secret", getUsername(), getPassword());

      // Modify the original domain yaml to include restartVersion and change runtimeEncryptionSecret
      String originalYaml = getUserProjectsDir() + "/weblogic-domains/" + domain.getDomainUid()
          + "/domain.yaml";
      DomainCrd crd = new DomainCrd(originalYaml);
      Map<String, String> objectNode = new HashMap();
      objectNode.put("restartVersion", "v1.1");
      crd.addObjectNodeToDomain(objectNode);
      crd.changeRuntimeEncryptionSecret(secret.getSecretName());
      String modYaml = crd.getYamlTree();
      LoggerHelper.getLocal().log(Level.INFO, modYaml);
      // Write the modified yaml to a new file
      Path path = Paths.get(getUserProjectsDir() + "/weblogic-domains/" + domain.getDomainUid(),
          "modified.domain.yaml");
      LoggerHelper.getLocal().log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, modYaml.getBytes(charset));

      // Apply the new yaml to update the domain crd
      LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.execOrAbortProcess("kubectl apply -f " + path.toString());
      LoggerHelper.getLocal().log(Level.INFO, exec.stdout());
      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the domain is restarted");
      domain.verifyAdminServerRestarted();
      domain.verifyManagedServersRestarted();
      domain.verifyDomainCreated();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }
}

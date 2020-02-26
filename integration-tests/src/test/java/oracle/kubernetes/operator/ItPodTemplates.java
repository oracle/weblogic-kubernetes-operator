// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
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
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Simple JUnit test file used for testing domain pod templates.
 *
 * <p>This test is used for creating Operator(s) and domain which uses pod templates.
 */
@TestMethodOrder(Alphanumeric.class)
public class ItPodTemplates extends BaseTest {

  private static Operator operator1;
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
    // initialize test properties and create the directories
    if (QUICKTEST) {
      createResultAndPvDirs(testClassName);
      // create operator1
      if (operator1 == null) {
        Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, testClassName);
        operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator1);
        domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList.append((String)operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS);
      }
    }


  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    if (QUICKTEST) {
      tearDown(new Object() {
      }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

      LoggerHelper.getLocal().info("SUCCESS");
    }
  }

  /**
   * Test pod templates using all the variables $(SERVER_NAME), $(DOMAIN_NAME), $(DOMAIN_UID),
   * $(DOMAIN_HOME), $(LOG_HOME) and $(CLUSTER_NAME) in serverPod. Make sure the domain comes up
   * successfully.
   *
   * @throws Exception when the domain crd creation fails or when updating the serverPod with
   *                   variables
   */
  @Test
  public void testPodTemplateUsingVariables() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, "Creating Operator & waiting for the script to complete execution");
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), testClassName);
      // domainMap.put("domainUID", "podtemplatedomain");
      domainMap.put("namespace", domainNS);
      // just create domain yaml, dont apply
      domain = TestUtils.createDomain(domainMap, false);
      String originalYaml =
          getUserProjectsDir()
              + "/weblogic-domains/"
              + domain.getDomainUid()
              + "/domain.yaml";

      // Modify the original domain yaml to include labels in serverPod
      // node
      final DomainCrd crd = new DomainCrd(originalYaml);
      // add labels to serverPod
      Map<String, String> labelKeyValue = new HashMap();
      labelKeyValue.put("servername", "$(SERVER_NAME)");
      labelKeyValue.put("domainname", "$(DOMAIN_NAME)");
      labelKeyValue.put("domainuid", "$(DOMAIN_UID)");
      crd.addObjectNodeToServerPod("labels", labelKeyValue);

      // add annotations to serverPod as DOMAIN_HOME and LOG_HOME contains "/" which is not allowed
      // in labels
      Map<String, String> envKeyValue = new HashMap();
      envKeyValue.put("domainhome", "$(DOMAIN_HOME)");
      envKeyValue.put("loghome", "$(LOG_HOME)");
      crd.addObjectNodeToServerPod("annotations", envKeyValue);

      // add label to cluster serverPod for CLUSTER_NAME
      Map<String, String> clusterLabelKeyValue = new HashMap();
      clusterLabelKeyValue.put("clustername", "$(CLUSTER_NAME)");
      crd.addObjectNodeToClusterServerPod(domain.getClusterName(), "labels", clusterLabelKeyValue);

      String modYaml = crd.getYamlTree();
      LoggerHelper.getLocal().log(Level.INFO, modYaml);

      // Write the modified yaml to a new file
      Path path =
          Paths.get(
              getUserProjectsDir() + "/weblogic-domains/" + domain.getDomainUid(),
              "domain.modified.yaml");
      LoggerHelper.getLocal().log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, modYaml.getBytes(charset));

      // Apply the new yaml to update the domain
      LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
      LoggerHelper.getLocal().log(Level.INFO, exec.stdout());

      domain.verifyDomainCreated();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        domain.shutdownUsingServerStartPolicy();
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        TestUtils.verifyAfterDeletion(domain);
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }
}

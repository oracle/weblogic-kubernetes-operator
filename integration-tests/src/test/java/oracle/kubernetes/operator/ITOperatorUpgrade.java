// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.BaseTest.logger;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for testing Helm install for Operator(s)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITOperatorUpgrade extends BaseTest {

  private static final String OP_REL = "2.0";
  private static final String OP_NS = "weblogic-operator";
  private static final String OP_DEP = "operator";
  private static final String OP_SA = "operator-sa";
  private static final String DOM_NS = "weblogic-domain";
  private static final String DUID = "domain1";
  private static final String managedServerNameBase = "managed-server";
  private static final String adminServerName = "admin-server";
  private static final int initialManagedServerReplicas = 2;
  private static final String API_VER = "v2";
  private static String opUpgradeTmpDir;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (!QUICKTEST) {
      initialize(APP_PROPS_FILE);
      pullImages();
      opUpgradeTmpDir = BaseTest.getResultDir() + "/operatorupgrade";
      Files.createDirectories(Paths.get(opUpgradeTmpDir));
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories
   *
   * @throws Exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (!QUICKTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");

      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());

      logger.info("SUCCESS");
    }
  }

  @Test
  public void testOperatorUpgradeTo2_1() throws Exception {
    // checkout weblogic operator image 2.0
    // pull traefik , wls and operator images
    // create service account, etc.,
    // create traefik loadbalancer
    // create operator
    // create domain

    // pull operator 2.1 image
    // helm upgrade to operator 2.1
    // verify the domain is not restarted but the operator image running is 2.1
    createOperator();
    verifyDomainCreated();
  }

  private void createOperator() throws Exception {
    TestUtils.ExecAndPrintLog("kubectl create namespace " + OP_NS);
    TestUtils.ExecAndPrintLog("kubectl create serviceaccount -n " + OP_NS + " " + OP_SA);
    TestUtils.ExecAndPrintLog("kubectl create namespace " + DOM_NS);

    TestUtils.ExecAndPrintLog(
        "cd "
            + opUpgradeTmpDir
            + " && git clone -b "
            + OP_REL
            + " https://github.com/oracle/weblogic-kubernetes-operator");
    TestUtils.ExecAndPrintLog(
        "cd "
            + opUpgradeTmpDir
            + " && helm install stable/traefik --name traefik-operator --namespace traefik --values weblogic-kubernetes-operator/kubernetes/samples/charts/traefik/values.yaml --set 'kubernetes.namespaces={traefik}' --wait --timeout 60");
    TestUtils.ExecAndPrintLog(
        "cd "
            + opUpgradeTmpDir
            + " && helm install weblogic-kubernetes-operator/kubernetes/charts/weblogic-operator --name operator --namespace "
            + OP_NS
            + " --set serviceAccount="
            + OP_SA
            + " --set 'domainNamespaces={}' --wait");
    Thread.sleep(10 * 1000);
    TestUtils.ExecAndPrintLog("helm list");
    TestUtils.ExecAndPrintLog("kubectl get pods -n " + OP_NS);
    TestUtils.ExecAndPrintLog(
        "cd "
            + opUpgradeTmpDir
            + " && helm upgrade --reuse-values --set 'domainNamespaces={"
            + DOM_NS
            + "}' --wait operator weblogic-kubernetes-operator/kubernetes/charts/weblogic-operator");
    TestUtils.ExecAndPrintLog(
        "helm upgrade --reuse-values --set 'kubernetes.namespaces={traefik,"
            + DOM_NS
            + "}' --wait traefik-operator stable/traefik");
    TestUtils.ExecAndPrintLog(
        "cd "
            + opUpgradeTmpDir
            + " && weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh -u weblogic -p welcome1 -n "
            + DOM_NS
            + " -d "
            + DUID);
    TestUtils.ExecAndPrintLog(
        "cd "
            + opUpgradeTmpDir
            + " && cp weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain-inputs.yaml .");

    Path src = Paths.get(opUpgradeTmpDir + "/create-domain-inputs.yaml");
    logger.log(Level.INFO, "Copying {0}", src.toString());
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(src), charset);
    content = content.replaceAll("namespace:default", "namespace: " + DOM_NS);
    logger.log(Level.INFO, "to {0}", src.toString());
    Files.write(src, content.getBytes(charset), StandardOpenOption.TRUNCATE_EXISTING);

    TestUtils.ExecAndPrintLog(
        "cd "
            + opUpgradeTmpDir
            + " && weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain.sh -i "
            + "create-domain-inputs.yaml -o "
            + opUpgradeTmpDir
            + " -u weblogic -p welcome1 -e");
  }

  private static void pullImages() throws Exception {
    TestUtils.ExecAndPrintLog("docker pull oracle/weblogic-kubernetes-operator:" + OP_REL);
    TestUtils.ExecAndPrintLog("docker pull traefik:1.7.6");
    TestUtils.ExecAndPrintLog(
        "docker pull " + BaseTest.getWeblogicImageName() + ":" + BaseTest.getWeblogicImageTag());
  }

  private void verifyDomainCreated() throws Exception {
    StringBuffer command = new StringBuffer();
    command.append("kubectl get domain ").append(DUID).append(" -n ").append(DOM_NS);
    ExecResult result = TestUtils.exec(command.toString());
    if (!result.stdout().contains(DUID)) {
      throw new RuntimeException("FAILURE: domain not found, exiting!");
    }

    // verify pods created
    logger.info("Checking if admin pod(" + DUID + "-" + adminServerName + ") is Running");
    TestUtils.checkPodCreated(DUID + "-" + adminServerName, DOM_NS);

    // check managed server pods
    for (int i = 1; i <= initialManagedServerReplicas; i++) {
      logger.info(
          "Checking if managed pod(" + DUID + "-" + managedServerNameBase + i + ") is Running");
      TestUtils.checkPodCreated(DUID + "-" + managedServerNameBase + i, DOM_NS);
    }

    // check services created
    for (int i = 1; i <= initialManagedServerReplicas; i++) {
      logger.info(
          "Checking if managed service(" + DUID + "-" + managedServerNameBase + i + ") is created");
      TestUtils.checkServiceCreated(DUID + "-" + managedServerNameBase + i, DOM_NS);
    }

    // check pods are ready
    TestUtils.checkPodReady(DUID + "-" + adminServerName, DOM_NS);
    for (int i = 1; i <= initialManagedServerReplicas; i++) {
      logger.info("Checking if managed server (" + managedServerNameBase + i + ") is Running");
      TestUtils.checkPodReady(DUID + "-" + managedServerNameBase + i, DOM_NS);
    }
  }
}

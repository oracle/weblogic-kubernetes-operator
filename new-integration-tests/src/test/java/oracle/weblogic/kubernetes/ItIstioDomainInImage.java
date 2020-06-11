// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Istio;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create domain in image domain using wdt and start the domain")
@IntegrationTest
class ItIstioDomainInImage implements LoggedTest {

  private static HelmParams opHelmParams = null;
  private static String opNamespace = null;
  private static String operatorImage = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static String dockerConfigJson = "";
  private String domainUid = "istio-dii";
  private String clusterName = "cluster-1"; // do not modify 
  private String adminServerName = "admin-server"; // do not modify
  private final String adminServerPodName = domainUid + "-" + adminServerName;

  private static Map<String, Object> secretNameMap;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

  }

  /**
   * Create a domain in domain-home-in-image model.
   * Add istio Configuration 
   * Label domain namespace and operator namespace with istio-injection=enabled 
   * Deploy istio gateways and virtualservices 
   * Verify domain pods runs in ready state and services are created.
   * Verify login to WebLogic console is successful thru ISTIO ingress Port.
   */
  @Test
  @DisplayName("Create WebLogic domainhome-in-image with istio")
  @Slow
  @MustNotRunInParallel
  public void testIstioCreateDomaininImage() {
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    createDockerRegistrySecret(domainNamespace);

    // Label the operator/domain namespace with istio-injection=enabled
    boolean k8res = labelNamespace(opNamespace);
    assertTrue(k8res, "Could not label the Operator namespace");
    k8res = labelNamespace(domainNamespace);
    assertTrue(k8res, "Could not label the WebLogic domain namespace");

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create the domain CR
    createDomainResource(domainUid, domainNamespace, adminSecretName, REPO_SECRET_NAME,
        replicaCount);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));

    // check admin server pod exists
    logger.info("Check for adminserver pod {0} existence in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods exist
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managedserver pod {0} existence in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managedserver service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    boolean deployRes = deployHttpIstioGatewayAndVirtualservice();
    assertTrue(deployRes, "Could not deploy Http Istio Gateway/VirtualService");

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);

    try {
      Thread.sleep(2 * 1000);
    } catch (InterruptedException ie) {
      //
    }

    logger.info("Validating WebLogic admin server access by login to console");
    boolean loginSuccessful = assertDoesNotThrow(() -> {
      return adminNodePortAccessible(istioIngressPort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    }, "Access to admin server node port failed");
    assertTrue(loginSuccessful, "Console login validation failed");
  }

  /**
   * TODO: remove when infra for Istio Gateway and Virtual Service is ready.
   * Replace a file with a String Replacement.
   * @param in  input file
   * @param out output file
   * @param oldString the old String to be replaced 
   * @param newString the new String 
   */
  public void updateFileWithStringReplacement(String in, String out, String oldString, String newString) {

    File fileToBeModified = new File(in);
    File fileToBeReplaced = new File(out);
    String oldContent = "";
    BufferedReader reader = null;
    FileWriter writer = null;
    try {
      reader = new BufferedReader(new FileReader(fileToBeModified));
      //Reading all the lines of input text file into oldContent
      String line = reader.readLine();
      while (line != null) {
        oldContent = oldContent + line + System.lineSeparator();
        line = reader.readLine();
      }
      //Replacing oldString with newString in the oldContent
      String newContent = oldContent.replaceAll(oldString, newString);
      //Rewriting the input text file with newContent
      writer = new FileWriter(fileToBeReplaced);
      writer.write(newContent);
      reader.close();
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /*
   * TODO: move to CommonTestUtils
   * Deploy the Http Istio Gateway and Istio Virtualservice.
   * @returns true if deployment is success otherwise false
   **/

  private boolean deployHttpIstioGatewayAndVirtualservice() {
    String input = RESOURCE_DIR + "/istio/istio-http-template.service.yaml";
    String output = RESOURCE_DIR + "/istio/istio-http-service.yaml";
    String clusterService = domainUid + "-cluster-" + clusterName + ".svc.cluster.local";
    updateFileWithStringReplacement(input, output, "NAMESPACE", domainNamespace); 
    updateFileWithStringReplacement(output, output, "ADMIN_SERVICE", adminServerPodName); 
    updateFileWithStringReplacement(output, output, "CLUSTER_SERVICE", clusterService); 
    ExecResult result = null;
    StringBuffer deployIstioGateway = null;
    deployIstioGateway = new StringBuffer("kubectl apply -f ");
    deployIstioGateway.append(RESOURCE_DIR)
        .append("/istio/istio-http-service.yaml");
    logger.info("deployIstioGateway: kubectl command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioGateway() {0}", ex);
      return false;
    }
    logger.info("deployIstioGateway: kubectl returned {0}", result.toString());
    if (result.stdout().contains("istio-http-gateway created")) {
      return true;
    } else {
      return false;
    }
  }
  
  /*
   * TODO: move to CommonTestUtils
   * Deploy the tcp Istio Gateway and Istio Virtualservice.
   * @returns true if deployment is success otherwise false
   **/

  private boolean deployTcpIstioGatewayAndVirtualservice() {

    String input = RESOURCE_DIR + "/istio/istio-tcp-template.service.yaml";
    String output = RESOURCE_DIR + "/istio/istio-tcp-service.yaml";
    String adminService = adminServerPodName + ".svc.cluster.local";
    updateFileWithStringReplacement(input, output, "NAMESPACE", domainNamespace); 
    updateFileWithStringReplacement(output, output, "ADMIN_SERVICE", adminService); 
    ExecResult result = null;
    StringBuffer deployIstioGateway = null;
    deployIstioGateway = new StringBuffer("kubectl apply -f ");
    deployIstioGateway.append(RESOURCE_DIR)
        .append("/istio/istio-tcp-service.yaml");
    logger.info("deployIstioGateway: kubectl command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioGateway() {0}", ex);
      return false;
    }
    logger.info("deployIstioGateway: kubectl returned {0}", result.toString());
    if (result.stdout().contains("istio-tcp-gateway created")) {
      return true;
    } else {
      return false;
    }
  }


  /*
   * TODO: move to CommonTestUtils
   * @returns ingress port for istio-ingressgateway
   **/
  private int getIstioHttpIngressPort() {
    ExecResult result = null;
    StringBuffer getIngressPort = null;
    getIngressPort = new StringBuffer("kubectl -n istio-system get service istio-ingressgateway ");
    getIngressPort.append("-o jsonpath='{.spec.ports[?(@.name==\"http2\")].nodePort}'");
    logger.info("getIngressPort: kubectl command {0}", new String(getIngressPort));
    try {
      result = exec(new String(getIngressPort), true);
    } catch (Exception ex) {
      logger.info("Exception in getIngressPort() {0}", ex);
      return 0;
    }
    logger.info("getIngressPort: kubectl returned {0}", result.toString());
    if (result.stdout() == null) {
      return 0;
    } else {
      return new Integer(result.stdout());
    }
  }

  /*
   * TODO: move to CommonTestUtils
   * @returns secure ingress https port for istio-ingressgateway
   **/
  private int getIstioSecureIngressPort() {
    ExecResult result = null;
    StringBuffer getSecureIngressPort = null;
    getSecureIngressPort = new StringBuffer("kubectl -n istio-system get service istio-ingressgateway ");
    getSecureIngressPort.append("-o jsonpath='{.spec.ports[?(@.name==\"https\")].nodePort}'");
    logger.info("getSecureIngressPort: kubectl command {0}", new String(getSecureIngressPort));
    try {
      result = exec(new String(getSecureIngressPort), true);
    } catch (Exception ex) {
      logger.info("Exception in getSecureIngressPort() {0}", ex);
      return 0;
    }
    logger.info("getSecureIngressPort: kubectl returned {0}", result.toString());
    if (result.stdout() == null) {
      return 0;
    } else {
      return new Integer(result.stdout());
    }
  }

  /*
   * TODO: move to CommonTestUtils
   * @returns tcp ingress port for istio-ingressgateway
   **/
  private int getIstioTcpIngressPort() {
    ExecResult result = null;
    StringBuffer getTcpIngressPort = null;
    getTcpIngressPort = new StringBuffer("kubectl -n istio-system get service istio-ingressgateway ");
    getTcpIngressPort.append("-o jsonpath='{.spec.ports[?(@.name==\"tcp\")].nodePort}'");
    logger.info("getTcpIngressPort: kubectl command {0}", new String(getTcpIngressPort));
    try {
      result = exec(new String(getTcpIngressPort), true);
    } catch (Exception ex) {
      logger.info("Exception in getTcpIngressPort() {0}", ex);
      return 0;
    }
    logger.info("getTcpIngressPort: kubectl returned {0}", result.toString());
    if (result.stdout() == null) {
      return 0;
    } else {
      return new Integer(result.stdout());
    }
  }

  /*
   * TODO: move to CommonTestUtils
   * Label a namespace with Istio Injection
   **/
  private boolean labelNamespace(String namespace) {
    ExecResult result = null;
    StringBuffer labelNamespace = null;
    labelNamespace = new StringBuffer("kubectl label namespace ");
    labelNamespace.append(namespace)
        .append(" istio-injection=enabled --overwrite");
    logger.info("labelNamespace: kubectl command {0}", new String(labelNamespace));
    try {
      result = exec(new String(labelNamespace), true);
    } catch (Exception ex) {
      logger.info("labelNamespace: kubectl returned {0}", result.stdout());
      logger.info("Exception in labelNamespace() {0}", ex);
      return false;
    }
    logger.info("labelNamespace: kubectl returned {0}", result.stdout());
    return true;
  }

  private void createDomainResource(String domainUid, String domNamespace, String adminSecretName,
                                    String repoSecretName, int replicaCount) {
    // create the domain CR
    Domain domain = new Domain()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUid)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .domainUid(domainUid)
                    .domainHomeSourceType("Image")
                    .image(WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG)
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(repoSecretName))
                    .webLogicCredentialsSecret(new V1SecretReference()
                            .name(adminSecretName)
                            .namespace(domNamespace))
                    .includeServerOutInPodLog(true)
                    .serverStartPolicy("IF_NEEDED")
                    .serverPod(new ServerPod()
                            .addEnvItem(new V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom ")))
                    .adminServer(new AdminServer()
                            .serverStartState("RUNNING")
                            .adminService(new AdminService()
                                    .addChannelsItem(new Channel()
                                            .channelName("istio-default")
                                            .nodePort(0))))
                    .addClustersItem(new Cluster()
                            .clusterName(clusterName)
                            .replicas(replicaCount)
                            .serverStartState("RUNNING"))
                    .configuration(new Configuration()
                            .istio(new Istio()
                                 .enabled(Boolean.TRUE)
                                 .envoyPort(31111)
                                 .readinessPort(8888))
                            .model(new Model()
                                    .domainType("WLS"))
                        .introspectorJobActiveDeadlineSeconds(300L)));

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
            domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
                    + "for %s in namespace %s", domainUid, domNamespace));
  }

}

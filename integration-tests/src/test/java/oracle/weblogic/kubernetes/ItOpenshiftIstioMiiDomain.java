// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Istio;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.OnlineUpdate;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.IstioUtils.createAdminServer;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployIstioDestinationRule;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * A sample mii domain tests running in Openshift service mesh. The test assumes that the service mesh istio operator is
 * running in istio-system namespace. All the service mesh related operators are installed and running as per this
 * documentation. https://docs.openshift.com/container-platform/4.10/service_mesh/v2x/installing-ossm.html
 */
@DisplayName("Test Openshift servce mesh istio enabled WebLogic Domain in mii model")
@Tag("openshift")
@IntegrationTest
class ItOpenshiftIstioMiiDomain {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private String domainUid = "openshift-istio-mii";
  private String configMapName = "dynamicupdate-istio-configmap";
  private final String clusterName = "cluster-1"; // do not modify
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private final String workManagerName = "newWM";
  private final int replicaCount = 2;

  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher
  */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assign unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Assign unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);
    
    // edit service member roll to include operator and domain namespace so that 
    // Openshift service mesh can add istio side cars to the operator and WebLogic pods.
    Path smrYaml = Paths.get(WORK_DIR, "openshift", "servicememberroll.yaml");
    assertDoesNotThrow(() -> {
      deleteQuietly(smrYaml.getParent().toFile());
      Files.createDirectories(smrYaml.getParent());
    });
    assertDoesNotThrow(() -> {
      FileUtils.copy(Paths.get(RESOURCE_DIR, "openshift", "servicememberroll.yaml"),
          smrYaml);
      FileUtils.replaceStringInFile(smrYaml.toString(), "OPERATOR_NAMESPACE", opNamespace);
      FileUtils.replaceStringInFile(smrYaml.toString(), "DOMAIN_NAMESPACE", domainNamespace);
    });
    logger.info("Run " + KUBERNETES_CLI + " to create the service member roll");
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " apply -f " + smrYaml.toString());
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create service member roll");

    // install and verify operator with istio side car injection set to true
    String opSa = opNamespace + "-sa";
    HelmParams opHelmParams
        = new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    
    installAndVerifyOperator(opNamespace,
        opSa, // operator service account
        false, // with REST api enabled
        0, // externalRestHttpPort
        opHelmParams, // operator helm parameters
        false, // ElkintegrationEnabled
        null, // domainspaceSelectionStrategy
        null, // domainspaceSelector
        false, // enableClusterRolebinding
        "INFO", // operator pod log level
        -1, // domainPresenceFailureRetryMaxCount
        -1, // domainPresenceFailureRetrySeconds
        true, // openshift istio injection
        domainNamespace // domainNamespace
    );
  }

  /**
   * Create a domain using model-in-image model.
   * Inject sidecar.istio.io in domain resource under global serverPod object.
   * Set localhostBindingsEnabled to true in istio configuration. 
   * Add istio configuration with default readinessPort.
   * Do not add any AdminService under AdminServer configuration.
   * Deploy istio gateways and virtual service.
   * Verify server pods are in ready state and services are created.
   * Verify login to WebLogic console is successful thru istio ingress port.
   * Deploy a web application thru istio http ingress port using REST api.  
   * Access web application thru istio http ingress port using curl.
   */
  @Test
  @DisplayName("Create WebLogic Domain with mii model with openshift service mesh")  
  void testIstioModelInImageDomain() {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
                                    adminSecretName,
                                    domainNamespace,
                                    ADMIN_USERNAME_DEFAULT,
                                    ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
                                      encryptionSecretName,
                                      domainNamespace,
                            "weblogicenc",
                            "weblogicenc"),
                    String.format("createSecret failed for %s", encryptionSecretName));

    // create WDT config map without any files
    createConfigMapAndVerify(configMapName, domainUid, domainNamespace, Collections.emptyList());
    // create the domain object
    DomainResource domain = createDomainResource(domainUid,
                                      domainNamespace,
                                      adminSecretName,
                                      TEST_IMAGES_REPO_SECRET_NAME,
                                      encryptionSecretName,
                                      replicaCount,
                              MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
                              configMapName);
    logger.info(Yaml.dump(domain));

    domain = createClusterResourceAndAddReferenceToDomain(
        domainUid + "-" + clusterName, clusterName, domainNamespace, domain, replicaCount);

    // create model in image domain
    createDomainAndVerify(domain, domainNamespace);

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    String clusterService = domainUid + "-cluster-" + clusterName + "." + domainNamespace + ".svc.cluster.local";

    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("NAMESPACE", domainNamespace);
    templateMap.put("DUID", domainUid);
    templateMap.put("ADMIN_SERVICE",adminServerPodName);
    templateMap.put("CLUSTER_SERVICE", clusterService);
    templateMap.put("testwebapp", "sample-war");
    templateMap.put("MANAGED_SERVER_PORT", "7001");

    Path srcHttpFile = Paths.get(RESOURCE_DIR, "istio", "istio-http-template.yaml");
    Path targetHttpFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcHttpFile.toString(), "openshift-istio-http.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0}", targetHttpFile);

    boolean deployRes = assertDoesNotThrow(
        () -> deployHttpIstioGatewayAndVirtualservice(targetHttpFile));
    assertTrue(deployRes, "Failed to deploy Http Istio Gateway/VirtualService");

    Path srcDrFile = Paths.get(RESOURCE_DIR, "istio", "istio-dr-template.yaml");
    Path targetDrFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDrFile.toString(), "openshift-istio-dr.yaml", templateMap));
    logger.info("Generated DestinationRule file path is {0}", targetDrFile);

    deployRes = assertDoesNotThrow(
        () -> deployIstioDestinationRule(targetDrFile));
    assertTrue(deployRes, "Failed to deploy Istio DestinationRule");
    
    // get gateway url
    // oc -n istio-system get route istio-ingressgateway -o jsonpath='{.spec.host}'
    logger.info("Run " + KUBERNETES_CLI + " to get ingress gateway route");
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " get route -n istio-system istio-ingressgateway -o jsonpath='{.spec.host}'");
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertEquals(0, result.exitValue());
    assertNotNull(result.stdout());
    String gatewayUrl = result.stdout();

    String readyAppUrl = gatewayUrl + "/weblogic/ready";
    boolean checlReadyApp = checkAppUsingHostHeader(readyAppUrl, domainNamespace + ".org");
    assertTrue(checlReadyApp, "Failed to access ready app");
    logger.info("ready app is accessible");

    String url = "http://" + gatewayUrl + "/sample-war/index.jsp";
    logger.info("Application Access URL {0}", url);
    boolean checkApp = checkAppUsingHostHeader(url, domainNamespace + ".org");
    assertTrue(checkApp, "Failed to access WebLogic application");
  }

  private DomainResource createDomainResource(String domainUid, String domNamespace,
           String adminSecretName, String repoSecretName,
           String encryptionSecretName, int replicaCount,
           String miiImage, String configmapName) {

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy("IfNotPresent")
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .putAnnotationsItem("sidecar.istio.io/inject", "true")
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false -Dweblogic.rjvm.enableprotocolswitch=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(createAdminServer())
            .configuration(new Configuration()
                .istio(new Istio()
                    .localhostBindingsEnabled(Boolean.FALSE))
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configmapName)
                    .onlineUpdate(new OnlineUpdate().enabled(true))
                    .runtimeEncryptionSecret(encryptionSecretName))
            .introspectorJobActiveDeadlineSeconds(3000L)));
    setPodAntiAffinity(domain);
    return domain;
  }
}

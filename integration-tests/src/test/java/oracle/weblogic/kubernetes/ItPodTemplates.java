// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_DOMAINHOME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;



/**
 * This test is used for creating Operator(s) and domain which uses pod templates.
 */
@DisplayName("Test to verify domain pod templates.")
@IntegrationTest
class ItPodTemplates {


  // domain constants
  private static final int replicaCount = 1;
  private static String domain1Namespace = null;
  private static String domain1Uid = "itpodtemplates-domain-1";
  private static String clusterName = "cluster-1";
  private static LoggingFacade logger = null;

  /**
   * Install operator.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll

  public static void initAll(@Namespaces(2) List<String> namespaces) {

    logger = getLogger();
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);

    logger.info("Get a unique namespace for WebLogic domain1");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain1Namespace = namespaces.get(1);

    logger.info("install and verify operator");
    installAndVerifyOperator(opNamespace, domain1Namespace);
  }

  /**
   * Test pod templates using all the variables $(SERVER_NAME), $(DOMAIN_NAME), $(DOMAIN_UID),
   * $(DOMAIN_HOME), $(LOG_HOME) and $(CLUSTER_NAME) in serverPod for Domain In Image. Make sure the domain comes up
   * successfully.
   *
   * @throws Exception when the domain crd creation fails or when updating the serverPod with
   *                   variables
   */
  @Test
  @DisplayName("Test pod templates using all the variables for domain in image.")
  public void testPodTemplateUsingVariablesDomainInImage() throws Exception {
    try {
      logger.info("Add annotations to serverPod as $(DOMAIN_HOME) and $(LOG_HOME)");
      logger.info("Add labels to serverPod as $(DOMAIN_NAME), $(DOMAIN_UID), $(SERVER_NAME)");
      logger.info("Add label to cluster serverPod for $(CLUSTER_NAME)");
      logger.info("Create domain in image using pod template and verify that it's running");
      String wdtImage = WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG;
      createAndVerifyPodFromTemplate(wdtImage,
          domain1Uid,
          domain1Namespace,
          "Image",
          "domain1",
          WDT_BASIC_IMAGE_DOMAINHOME);

    } finally {
      logger.info("Shutting down domain");
      shutdownDomain(domain1Uid, domain1Namespace);
    }
  }

  /**
   * Create domain CRD with added labels and annotations to the serverPod using variables :
   * $(SERVER_NAME), $(DOMAIN_NAME), $(DOMAIN_UID),
   * $(DOMAIN_HOME), $(LOG_HOME) and $(CLUSTER_NAME).
   * Create and verify domain and check that managed server pod labels and annotations are added
   * and initialized for $(SERVER_NAME), $(DOMAIN_NAME), $(DOMAIN_UID), $(DOMAIN_HOME), $(CLUSTER_NAME).
   * and not initialized for $(LOG_HOME) since logHomeEnable option set to false.
   */
  private void createAndVerifyPodFromTemplate(String imageName, String domainUid, String domainNS,
                                              String domainHomeSource,
                                              String domainName,
                                              String domainHome) throws io.kubernetes.client.openapi.ApiException {
    createAndVerifyDomain(imageName, domainUid, domainNS, domainHomeSource, replicaCount);
    String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + "1";
    V1Pod managedServerPod = Kubernetes.getPod(domainNS, null, managedServerPodName);

    //check that managed server pod is up and all applicable variable values are initialized.
    assertNotNull(managedServerPod,"The managed server pod does not exist in namespace " + domainNS);
    V1ObjectMeta managedServerMetadata = managedServerPod.getMetadata();
    String serverName = managedServerMetadata.getLabels().get("servername");

    logger.info("Checking that variables used in the labels and annotations "
        + "in the serverPod for servername, domainname, clustername are initialized");
    //check that label contains servername in the managed server pod
    assertNotNull(serverName, "Can't find label servername");
    assertTrue(serverName.equalsIgnoreCase("managed-server1"),
        "Can't find or match label servername, real value is " + serverName);

    String domainname = managedServerMetadata.getLabels()
        .get("domainname");

    //check that label contains domainname in the managed server pod
    assertNotNull(domainname, "Can't find label domainname");
    assertTrue(domainName.equalsIgnoreCase(domainName),
        "Can't find expected value for  label domainname, real value is " + domainname);

    String myclusterName = managedServerMetadata.getLabels()
        .get("clustername");

    //check that label contains clustername in the managed server pod
    assertNotNull(myclusterName, "Can't find label clustername");
    assertTrue(myclusterName.equalsIgnoreCase(clusterName),
        "Can't find expected value for label clustername, real value is " + myclusterName);

    String domainuid = managedServerMetadata.getLabels()
        .get("domainuid");

    //check that label contains domainuid in the managed server pod
    assertNotNull(domainuid, "Can't find label domainuid");
    assertTrue(domainuid.equalsIgnoreCase(domainUid),
        "Can't find expected value for label domainuid, , real value is " + domainuid);

    logger.info("Checking that applicable variables used "
        + "in the annotations for domainhome and loghome are initialized");
    String loghome = managedServerMetadata.getAnnotations()
        .get("loghome");
    //check that annotation contains loghome in the pod
    assertNotNull(loghome, "Can't find annotation loghome");
    //value is not initialized since logHomeEnable = false in CRD
    assertTrue(loghome.equalsIgnoreCase("$(LOG_HOME)"),
        "Can't find expected value for annotation loghome, real value is " + loghome);

    String domainhome = managedServerMetadata.getAnnotations()
        .get("domainhome");
    //check that annotation contains domainhome in the managed server pod
    assertNotNull(domainhome, "Can't find annotation domainhome");
    assertTrue(domainhome.equalsIgnoreCase(domainHome),
        "Can't find expected value for annotation domainhome, retrieved value is :" + domainhome);
  }

  //create domain from provided image and verify it's start
  private static void createAndVerifyDomain(String imageName,
                                            String domainUid,
                                            String namespace,
                                            String domainHomeSource,
                                            int replicaCount) {
    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create docker registry secret in namespace {0}", namespace);
    createOcirRepoSecret(namespace);
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, namespace,
        "weblogic", "welcome1"),
        String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, namespace,
        "weblogicenc", "weblogicenc"),
        String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create domain {0} in namespace {1} using docker image {2}",
        domainUid, namespace, imageName);
    createDomainCrAndVerify(adminSecretName, OCIR_SECRET_NAME, encryptionSecretName, imageName,domainUid,
        namespace, domainHomeSource, replicaCount);
    String adminServerPodName = domainUid + "-admin-server";

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, namespace);
    checkServiceExists(adminServerPodName, namespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, namespace);
    checkPodReady(adminServerPodName, domainUid, namespace);

    String managedServerPrefix = domainUid + "-managed-server";
    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server pod exists
      logger.info("Checking that managed server pod {0} exists in namespace {1}",
          managedServerPodName, namespace);
      checkPodExists(managedServerPodName, domainUid, namespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, namespace);
      checkPodReady(managedServerPodName, domainUid, namespace);

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, namespace);
      checkServiceExists(managedServerPodName, namespace);
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName,
                                              String repoSecretName,
                                              String encryptionSecretName,
                                              String imageName,
                                              String domainUid,
                                              String namespace,
                                              String domainHomeSource,
                                              int replicaCount) {
    // add labels to serverPod
    Map<String, String> labelKeyValues = new HashMap();
    labelKeyValues.put("servername", "$(SERVER_NAME)");
    labelKeyValues.put("domainname", "$(DOMAIN_NAME)");
    labelKeyValues.put("domainuid", "$(DOMAIN_UID)");

    // add annotations to serverPod as DOMAIN_HOME and LOG_HOME contains "/" which is not allowed
    // in labels
    Map<String, String> annotationKeyValues = new HashMap();
    annotationKeyValues.put("domainhome", "$(DOMAIN_HOME)");
    annotationKeyValues.put("loghome", "$(LOG_HOME)");

    // add label to cluster serverPod for CLUSTER_NAME
    Map<String, String> clusterLabelKeyValues = new HashMap();
    clusterLabelKeyValues.put("clustername", "$(CLUSTER_NAME)");
    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .uid(domainUid)
            .namespace(namespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(WDT_BASIC_IMAGE_DOMAINHOME)
            .domainHomeSourceType(domainHomeSource)
            .image(imageName)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(namespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .annotations(annotationKeyValues)
                .labels(labelKeyValues)
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.security.SSL.ignoreHostnameVerification=true "))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")
                )
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    domain.getSpec().getClusters()
        .stream()
        .forEach(
            cluster -> {
                if (cluster.getClusterName().equals(clusterName)) {
                  cluster.getServerPod().labels(clusterLabelKeyValues);
                }
            }
    );
    // create domain using model in image
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, namespace, imageName);
    createDomainAndVerify(domain, namespace);
  }

}

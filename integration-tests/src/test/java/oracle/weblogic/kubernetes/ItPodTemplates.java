// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.ClusterSpec;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_DOMAINHOME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test is used for creating Operator(s) and domain which uses pod templates.
 */
@DisplayName("Test to verify domain pod templates.")
@IntegrationTest
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@Tag("oke-arm")
@Tag("oke-parallel")
class ItPodTemplates {

  // domain constants
  private static final int replicaCount = 1;
  private static String domainNamespace = null;
  private static String domainUid = "itpodtemplates-domain";
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
    domainNamespace = namespaces.get(1);

    logger.info("install and verify operator");
    installAndVerifyOperator(opNamespace, domainNamespace);
  }

  /**
   * Test pod templates using supported variables.
   * $(SERVER_NAME), $(DOMAIN_NAME), $(DOMAIN_UID), $(CLUSTER_NAME) 
   * $(DOMAIN_HOME), $(LOG_HOME) in serverPod section of Domain Spec
   * and Cluster Spec. Make sure the domain comes up successfully.
   */
  @Test
  @DisplayName("Test pod templates using all supported variables in serverPod")
  void testPodTemplateUsingVariablesDomainInImage() throws Exception {
    String wdtImage = WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG;
    logger.info("Add annotations to serverPod in Domain Spec as $(DOMAIN_HOME) and $(LOG_HOME)");
    logger.info("Add labels to serverPod in Domain Spec as $(DOMAIN_NAME), $(DOMAIN_UID), $(SERVER_NAME)");
    logger.info("Add label to serverPod in Cluster Spec for $(CLUSTER_NAME)");
    
    assertDoesNotThrow(() -> 
        createAndVerifyPodFromTemplate(wdtImage,
          "Image",
          "domain1",
          WDT_BASIC_IMAGE_DOMAINHOME),
        "Creating Domain resource with serverPod template failed");
  }

  /**
   * Create Domain resource with added labels and annotations to the 
   * serverPod section using following variables ...
   * $(SERVER_NAME), $(DOMAIN_NAME), $(DOMAIN_UID),
   * $(DOMAIN_HOME), $(LOG_HOME) and $(CLUSTER_NAME).
   * Create and verify domain and check that managed server pod labels and 
   * annotations are added for $(SERVER_NAME), $(DOMAIN_NAME), $(DOMAIN_UID), 
   * $(DOMAIN_HOME), $(CLUSTER_NAME) and not added for $(LOG_HOME) since 
   * logHomeEnable option set to false.
   */
  private void createAndVerifyPodFromTemplate(String imageName,
                                              String domainHomeSource,
                                              String domainName,
                                              String domainHome) throws ApiException {
    createAndVerifyDomain(imageName,  domainHomeSource, replicaCount);
    String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + "1";
    V1Pod managedServerPod = Kubernetes.getPod(domainNamespace, null, managedServerPodName);

    //check that managed server pod is up and all applicable variable values are initialized.
    assertNotNull(managedServerPod,"The managed server pod does not exist in namespace " + domainNamespace);
    V1ObjectMeta managedServerMetadata = managedServerPod.getMetadata();
    assertNotNull(managedServerMetadata, "managed server pod metadata is null");
    assertNotNull(managedServerMetadata.getLabels(), "managed server metadata label is null");
    String serverName = managedServerMetadata.getLabels().get("servername");
    logger.info("Checking that variables used in the labels and annotations "
        + "in the serverPod for servername, domainname, clustername are initialized");
    //check that label contains servername in the managed server pod
    assertNotNull(serverName, "Can't find label servername");
    assertTrue(serverName.equalsIgnoreCase("managed-server1"),
        "Can't find or match label servername, real value is " + serverName);

    String domainname = managedServerMetadata.getLabels().get("domainname");

    //check that label contains domainname in the managed server pod
    assertNotNull(domainname, "Can't find label domainname");
    assertTrue(domainname.equalsIgnoreCase(domainName),
        "Can't find expected value for  label domainname, real value is " + domainname);

    String myclusterName = managedServerMetadata.getLabels().get("clustername");

    //check that label contains clustername in the managed server pod
    assertNotNull(myclusterName, "Can't find label clustername");
    assertTrue(myclusterName.equalsIgnoreCase(clusterName),
        "Can't find expected value for label clustername, real value is " + myclusterName);

    String domainuid = managedServerMetadata.getLabels().get("domainuid");

    //check that label contains domainuid in the managed server pod
    assertNotNull(domainuid, "Can't find label domainuid");
    assertTrue(domainuid.equalsIgnoreCase(domainUid),
        "Can't find expected value for label domainuid, , real value is " + domainuid);

    logger.info("Checking that applicable variables used "
        + "in the annotations for domainhome and loghome are initialized");
    assertNotNull(managedServerMetadata.getAnnotations(), "managed server metadata annotation is null");
    String loghome = managedServerMetadata.getAnnotations().get("loghome");
    //check that annotation contains loghome in the pod
    assertNotNull(loghome, "Can't find annotation loghome");
    //value is not initialized since logHomeEnable = false in CRD
    assertTrue(loghome.equalsIgnoreCase("$(LOG_HOME)"),
        "Can't find expected value for annotation loghome, real value is " + loghome);

    String domainhome = managedServerMetadata.getAnnotations().get("domainhome");
    //check that annotation contains domainhome in the managed server pod
    assertNotNull(domainhome, "Can't find annotation domainhome");
    assertTrue(domainhome.equalsIgnoreCase(domainHome),
        "Can't find expected value for annotation domainhome, retrieved value is :" + domainhome);
  }

  //create domain from provided image and verify all servers are started
  private static void createAndVerifyDomain(String imageName,
                                            String domainHomeSource,
                                            int replicaCount) {
    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc"),
        String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create domain {0} in domainNamespace {1} using image {2}",
        domainUid, domainNamespace, imageName);
    createDomainCrAndVerify(adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, 
         encryptionSecretName, imageName, 
         domainHomeSource, replicaCount);
    String adminServerPodName = domainUid + "-admin-server";

    // check that admin server pod is ready
    logger.info("Wait for admin server pod {0} is ready in domainNamespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    logger.info("Wait for managed pods are ready in namespace {0}",domainNamespace);
    String managedServerPrefix = domainUid + "-managed-server";
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName,
                                              String repoSecretName,
                                              String encryptionSecretName,
                                              String imageName,
                                              String domainHomeSource,
                                              int replicaCount) {
    // add labels to serverPod
    Map<String, String> labelKeyValues = new HashMap<>();
    labelKeyValues.put("servername", "$(SERVER_NAME)");
    labelKeyValues.put("domainname", "$(DOMAIN_NAME)");
    labelKeyValues.put("domainuid", "$(DOMAIN_UID)");

    // add annotations to serverPod as DOMAIN_HOME and LOG_HOME 
    // contains "/" which is not allowed in labels
    Map<String, String> annotationKeyValues = new HashMap<>();
    annotationKeyValues.put("domainhome", "$(DOMAIN_HOME)");
    annotationKeyValues.put("loghome", "$(LOG_HOME)");

    // add label to cluster serverPod for CLUSTER_NAME
    Map<String, String> clusterLabelKeyValues = new HashMap<>();
    clusterLabelKeyValues.put("clustername", "$(CLUSTER_NAME)");

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .uid(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(WDT_BASIC_IMAGE_DOMAINHOME)
            .domainHomeSourceType(domainHomeSource)
            .image(imageName)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
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
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(3000L)));
    setPodAntiAffinity(domain);
    
    logger.info(Yaml.dump(domain));

    ClusterSpec clusterSpec = new ClusterSpec()
            .withClusterName(clusterName)
            .replicas(replicaCount)
            .serverPod(new ServerPod()
                .labels(clusterLabelKeyValues));
    logger.info(Yaml.dump(clusterSpec));
    ClusterResource cluster = 
         createClusterResource(clusterName, domainNamespace, clusterSpec);
    logger.info("Creating cluster resource {0} in namespace {1}", clusterName, domainNamespace);
    logger.info(Yaml.dump(cluster));
    createClusterAndVerify(cluster);
    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterName));
    // create domain resource
    logger.info("Create domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, imageName);
    createDomainAndVerify(domain, domainNamespace);
  }

}

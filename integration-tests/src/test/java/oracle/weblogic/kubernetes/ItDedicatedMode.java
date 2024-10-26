// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.IT_DEDICATEDMODE_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_DEDICATEDMODE_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.impl.Domain.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyClusterAfterScaling;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The current class verifies various use cases related to Dedicated
 * domainNamespaceSelectionStrategy applicable to Operator Helm Chart.
 * For more detail regarding the feature, please refer to
 * https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-operators/using-helm/#weblogic-domain-management
 */
@DisplayName("Test Operator and WebLogic domain with Dedicated set to true")
@Tag("kind-sequential")
@Tag("oke-weekly-sequential")
@Tag("okd-wls-mrg")
@IntegrationTest
class ItDedicatedMode {
  // namespace constants
  private static String opNamespace = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;

  private static final String CRD_V16 = "domain-crd.yaml";

  // domain constants
  private final String domainUid = "dedicated-domain1";
  private final String clusterName = "cluster-1";
  private final String clusterResName = domainUid + "-" + clusterName;
  private final int replicaCount = 2;
  private final String adminServerPodName =
       domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private final String managedServerPodPrefix =
       domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static int externalRestHttpsPort = IT_DEDICATEDMODE_NODEPORT;

  // operator constants
  private static HelmParams opHelmParams;
  private static String opServiceAccount;
  private static final String domainNamespaceSelectionStrategy = "Dedicated";

  private static LoggingFacade logger = null;

  /**
   * Get namespaces for operator and domain.
   * Create CRD based on the k8s version.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism.
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // in the dedicated mode, the operator only manages domains in the
    // operator's own namespace
    domain1Namespace = opNamespace;

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain2Namespace = namespaces.get(1);

    // Variables for Operator
    opServiceAccount = opNamespace + "-sa";
    opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);

    // delete existing CRD
    Command
        .withParams(new CommandParams()
            .command(KUBERNETES_CLI + " delete crd domains.weblogic.oracle --ignore-not-found"))
        .execute();

    // install CRD
    String createCrdCommand = KUBERNETES_CLI + " create -f " + ITTESTS_DIR + "/../kubernetes/crd/" + CRD_V16;
    logger.info("Creating CRD with command {0}", createCrdCommand);
    Command
        .withParams(new CommandParams().command(createCrdCommand))
        .execute();

    // Install the Operator in a ns (say op) with helm parameter
    // domainNamespaceSelectionStrategy set to Dedicated and set
    // domainNamespaces parameter to something other than Operator ns (say wls)
    logger.info("Installing and verifying operator");
    installAndVerifyOperator(opNamespace, opNamespace + "-sa",
        true, externalRestHttpsPort, opHelmParams, domainNamespaceSelectionStrategy,
        false, domain2Namespace);
    logger.info("Operator installed on namespace {0} and domainNamespaces set to {1} ", opNamespace, domain2Namespace);

  }

  /**
   * Create WebLogic Domain in a namespace (say wls) that is different
   * from the Operator's namespace. Verify that the domain does not come up.
   */
  @Test
  @DisplayName("Verify in Dedicated NamespaceSelectionStrategy domain on non-operator namespace does not started")
  void testDedicatedModeDiffNamespace() {
    // create and verify the domain
    logger.info("Creating a domain in non-operator namespace {1}", domain2Namespace);
    createDomain(domain2Namespace);
    verifyDomainNotRunning(domain2Namespace);
    logger.info("WebLogic domain is not managed in non-operator namespace");
  }

  /**
   * Create WebLogic Domain in a namespace (say op) that is same as
   * Operator's namespace. Verify that the domain does come up and can be
   * scaled up using Operator
   */
  @Test
  @DisplayName("Verify in Dedicated NamespaceSelectionStrategy domain on operator namespace gets started")
  void testDedicatedModeSameNamespace() {

    // This test uses the operator restAPI to scale the doamin.
    // To do this in OKD cluster, we need to expose the external service as
    // route and set tls termination to  passthrough
    String opExternalSvc =
        createRouteForOKD("external-weblogic-operator-svc", opNamespace);
    // Patch the route just created to set tls termination to passthrough
    setTlsTerminationForRoute("external-weblogic-operator-svc", opNamespace);

    logger.info("Creating a domain in perator namespace {1}", domain1Namespace);
    createDomain(domain1Namespace);
    verifyDomainRunning(domain1Namespace);
    logger.info("WebLogic domain is managed in operator namespace Only");

    List<OffsetDateTime> listOfPodCreationTimestamp = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodPrefix + i;
      OffsetDateTime originalCreationTimestamp
          = assertDoesNotThrow(() -> getPodCreationTimestamp(domain1Namespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
              managedServerPodName, domain1Namespace));
      listOfPodCreationTimestamp.add(originalCreationTimestamp);
    }
    // Scale up cluster-1 in domain1Namespace and verify it succeeds
    String externalRestHttpshost;
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      externalRestHttpshost = "localhost";
      externalRestHttpsPort = IT_DEDICATEDMODE_HOSTPORT;
      logger.info("Running in podman using Operator hostport {0}:{1}", externalRestHttpshost, externalRestHttpsPort);
    } else {
      logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);
      externalRestHttpshost = null;
    }

    logger.info("scaling the cluster from {0} servers to {1} servers", replicaCount, replicaCount + 1);
    if (OKE_CLUSTER) {
      scaleAndVerifyCluster(clusterResName, domainUid, domain1Namespace, managedServerPodPrefix,
          replicaCount, replicaCount + 1, null, null);
    } else {
      scaleClusterWithRestApi(domainUid, clusterName, replicaCount + 1,
          externalRestHttpshost, externalRestHttpsPort, opNamespace, opServiceAccount);
    }

    verifyClusterAfterScaling(domainUid, domain1Namespace, managedServerPodPrefix,
        replicaCount, replicaCount + 1, null, null, listOfPodCreationTimestamp);
  }

  private void createDomain(String domainNamespace) {
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
    logger.info("Create model in image domain {0} in namespace {1} using image {2}",
        domainNamespace, domainUid, domainNamespace);
    createDomainCrAndVerify(domainNamespace, TEST_IMAGES_REPO_SECRET_NAME, adminSecretName, encryptionSecretName);
  }

  private void createDomainCrAndVerify(String domainNamespace,
                                       String repoSecretName,
                                       String adminSecretName,
                                       String encryptionSecretName) {
    // get the pre-built image created by IntegrationTestWatcher
    String miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    domain = createClusterResourceAndAddReferenceToDomain(
        clusterResName, clusterName, domainNamespace, domain, replicaCount);

    setPodAntiAffinity(domain);
    // create model in image domain
    logger.info("Creating mii domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
  }

  private void verifyDomainRunning(String domainNamespace) {
    // check that admin server pod is ready and the service exists in the domain namespace
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodPrefix + i;

      // check that the managed server pod is ready and the service exists in the domain namespace
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }
  }

  private void verifyDomainNotRunning(String domainNamespace) {
    // check that admin server pod doesn't exists in the domain namespace
    logger.info("Checking that admin server pod {0} doesn't exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodPrefix + i;

      // check that managed server pod doesn't exists in the domain namespace
      logger.info("Checking that managed server pod {0} doesn't exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodDoesNotExist(managedServerPodName, domainUid, domainNamespace);
    }
  }

}

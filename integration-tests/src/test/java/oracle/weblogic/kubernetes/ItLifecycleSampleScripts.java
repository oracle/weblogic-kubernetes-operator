// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkClusterReplicaCountMatches;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to domain lifecycle sample scripts.
 */
@DisplayName("Verify the domain lifecycle sample scripts")
@IntegrationTest
public class ItLifecycleSampleScripts {

  public static final String SERVER_LIFECYCLE = "Server";
  public static final String CLUSTER_LIFECYCLE = "Cluster";
  public static final String DOMAIN = "DOMAIN";
  public static final String STOP_SERVER_SCRIPT = "stopServer.sh";
  public static final String START_SERVER_SCRIPT = "startServer.sh";
  public static final String STOP_CLUSTER_SCRIPT = "stopCluster.sh";
  public static final String START_CLUSTER_SCRIPT = "startCluster.sh";
  public static final String STOP_DOMAIN_SCRIPT = "stopDomain.sh";
  public static final String START_DOMAIN_SCRIPT = "startDomain.sh";
  private static String domainNamespace = null;
  private static final String domainName = "domain1";
  private final int replicaCount = 2;
  private final String clusterName = "cluster-1";
  private final String adminServerName = "admin-server";
  private final String managedServerNameBase = "managed-server";

  private final String adminServerPodName = domainName + "-" + adminServerName;
  private final String managedServerPodNamePrefix = domainName + "-" + managedServerNameBase;
  private final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private final Path domainLifecycleSamplePath = Paths.get(samplePath + "/scripts/domain-lifecycle");
  private final Path tempSamplePath = Paths.get(WORK_DIR, "lifecycle-scripts-testing");
  private final Path sampleBase =
          Paths.get(tempSamplePath.toString(), "scripts/create-weblogic-domain/domain-home-on-pv");

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(10, MINUTES).await();

  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator and domains and installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public void initAll(@Namespaces(2) List<String> namespaces) {
    String opNamespace = namespaces.get(0);

    logger = getLogger();

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);

    //create and start WebLogic domain using domain-home-on-pv sample scripts
    createDomain(sampleBase);
  }

  /**
   * Test scripts for stopping and starting a managed server.
   */
  @Test
  @DisplayName("Test server lifecycle samples scripts")
  public void testServerLifecycleScripts() {

    // Verify that stopServer script execution shuts down server pod and replica count is decremented
    String serverName = managedServerNameBase + "1";
    executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    checkPodDoesNotExist(managedServerPodNamePrefix + "1", domainName, domainNamespace);
    assertDoesNotThrow(() -> {
      checkClusterReplicaCountMatches(clusterName, domainName, domainNamespace, 1);
    });

    // Verify that startServer script execution starts server pod and replica count is incremented
    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    checkPodExists(managedServerPodNamePrefix + "1", domainName, domainNamespace);
    assertDoesNotThrow(() -> {
      checkClusterReplicaCountMatches(clusterName, domainName, domainNamespace, 2);
    });
  }

  /**
   * Test scripts for stopping and starting a managed server while keeping replica count constant.
   */
  @Test
  @DisplayName("Test server lifecycle samples scripts with constant replica count")
  public void testServerLifecycleScriptsWithConstantReplicaCount() {
    String serverName = managedServerNameBase + "1";
    String keepReplicaCountConstantParameter = "-k";
    // Verify that replica count is not changed when using "-k" parameter and a replacement server is started
    executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicaCountConstantParameter);
    checkPodDoesNotExist(managedServerPodNamePrefix + "1", domainName, domainNamespace);
    checkPodExists(managedServerPodNamePrefix + "3", domainName, domainNamespace);
    assertDoesNotThrow(() -> {
      checkClusterReplicaCountMatches(clusterName, domainName, domainNamespace, 2);
    });

    // Verify that replica count is not changed when using "-k" parameter and replacement server is shutdown
    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicaCountConstantParameter);
    checkPodExists(managedServerPodNamePrefix + "1", domainName, domainNamespace);
    checkPodDoesNotExist(managedServerPodNamePrefix + "3", domainName, domainNamespace);
    assertDoesNotThrow(() -> {
      checkClusterReplicaCountMatches(clusterName, domainName, domainNamespace, 2);
    });
  }

  /**
   * Test scripts for stopping and starting a cluster.
   */
  @Test
  @DisplayName("Test cluster lifecycle scripts")
  public void testClusterLifecycleScripts() {

    // Verify all clustered server pods are shut down after stopCluster script execution
    executeLifecycleScript(STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, clusterName);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainName, domainNamespace);
    }

    // Verify all clustered server pods are started after startCluster script execution
    executeLifecycleScript(START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, clusterName);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodExists(managedServerPodNamePrefix + i, domainName, domainNamespace);
    }
  }

  /**
   * Test scripts for stopping and starting a domain.
   */
  @Test
  @DisplayName("Test domain lifecycle scripts")
  public void testDomainLifecycleScripts() {
    // Verify all WebLogic server instance pods are shut down after stopDomain script execution
    executeLifecycleScript(STOP_DOMAIN_SCRIPT, DOMAIN, null);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainName, domainNamespace);
    }
    checkPodDoesNotExist(adminServerPodName, domainName, domainNamespace);

    // Verify all WebLogic server instance pods are started after startDomain script execution
    executeLifecycleScript(START_DOMAIN_SCRIPT, DOMAIN, null);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodExists(managedServerPodNamePrefix + i, domainName, domainNamespace);
    }
    checkPodExists(adminServerPodName, domainName, domainNamespace);
  }

  // Function to execute domain lifecyle scripts
  private void executeLifecycleScript(String script, String scriptType, String entityName) {
    executeLifecycleScript(script, scriptType, entityName, "");
  }

  // Function to execute domain lifecyle scripts
  private void executeLifecycleScript(String script, String scriptType, String entityName, String extraParams) {
    CommandParams params;
    boolean result;
    String commonParameters = " -d " + domainName + " -n " + domainNamespace;
    params = new CommandParams().defaults();
    if (scriptType.equals(SERVER_LIFECYCLE)) {
      params.command("sh "
              + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
              + commonParameters + " -s " + entityName + " " + extraParams);
    } else if (scriptType.equals(CLUSTER_LIFECYCLE)) {
      params.command("sh "
              + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
              + commonParameters + " -c " + entityName);
    } else {
      params.command("sh "
              + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
              + commonParameters);
    }
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to execute script " + script);
  }

  // Create domain using doamain-home-on-pv sample script and verify admin/managed pods are ready
  private void createDomain(Path sampleBase) {
    //copy the samples directory to a temporary location
    setupSample();
    //create PV and PVC used by the domain
    createPvPvc();

    //create WebLogic secrets for the domain
    createSecretWithUsernamePassword(domainName + "-weblogic-credentials", domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // change namespace from default to custom, set wlst or wdt, domain name, and t3PublicAddress
    assertDoesNotThrow(() -> {
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "namespace: default", "namespace: " + domainNamespace);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "#t3PublicAddress:", "t3PublicAddress: " + K8S_NODEPORT_HOST);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "image: container-registry.oracle.com/middleware/weblogic:12.2.1.4",
              "image: " + WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "#imagePullSecretName:", "imagePullSecretName: " + BASE_IMAGES_REPO_SECRET);
    });

    // run create-domain.sh to create domain.yaml file
    CommandParams params = new CommandParams().defaults();
    params.command("sh "
            + Paths.get(sampleBase.toString(), "create-domain.sh").toString()
            + " -i " + Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString()
            + " -o "
            + Paths.get(sampleBase.toString()));

    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain.yaml");

    // run kubectl to create the domain
    params = new CommandParams().defaults();
    params.command("kubectl apply -f "
            + Paths.get(sampleBase.toString(), "weblogic-domains/" + domainName + "/domain.yaml").toString());

    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain custom resource");

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
              .conditionEvaluationListener(
                  condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                                  + "(elapsed time {2}ms, remaining time {3}ms)",
                            domainName,
                            domainNamespace,
                            condition.getElapsedTimeInMS(),
                            condition.getRemainingTimeInMS()))
              .until(domainExists(domainName, DOMAIN_VERSION, domainNamespace));

    // verify the admin server service and pod is created
    checkPodReadyAndServiceExists(adminServerPodName, domainName, domainNamespace);

    // verify managed server services created and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainName, domainNamespace);
    }

  }

  // copy samples directory to a temporary location
  private void setupSample() {
    assertDoesNotThrow(() -> {
      // copy ITTESTS_DIR + "../kubernates/samples" to WORK_DIR + "/sample-testing"
      logger.info("Deleting and recreating {0}", tempSamplePath);
      Files.createDirectories(tempSamplePath);
      deleteDirectory(tempSamplePath.toFile());
      Files.createDirectories(tempSamplePath);

      logger.info("Copying {0} to {1}", samplePath, tempSamplePath);
      copyDirectory(samplePath.toFile(), tempSamplePath.toFile());
    });
  }

  // create persistent volume and persistent volume claims used by the samples
  private void createPvPvc() {

    String pvName = ItLifecycleSampleScripts.domainName + "-weblogic-sample-pv";
    String pvcName = ItLifecycleSampleScripts.domainName + "-weblogic-sample-pvc";

    Path pvpvcBase = Paths.get(tempSamplePath.toString(),
        "scripts/create-weblogic-domain-pv-pvc");

    // create pv and pvc
    assertDoesNotThrow(() -> {
      // when tests are running in local box the PV directories need to exist
      Path pvHostPath;
      pvHostPath = Files.createDirectories(Paths.get(PV_ROOT, this.getClass().getSimpleName(), pvName));

      logger.info("Creating PV directory host path {0}", pvHostPath);
      deleteDirectory(pvHostPath.toFile());
      Files.createDirectories(pvHostPath);

      // set the pvHostPath in create-pv-pvc-inputs.yaml
      replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "#weblogicDomainStoragePath: /scratch/k8s_dir", "weblogicDomainStoragePath: " + pvHostPath);
      // set the namespace in create-pv-pvc-inputs.yaml
      replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "namespace: default", "namespace: " + domainNamespace);
      // set the baseName to domain name in create-pv-pvc-inputs.yaml
      replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "baseName: weblogic-sample", "baseName: " + ItLifecycleSampleScripts.domainName + "-weblogic-sample");
      // set the pv storage policy to Recycle in create-pv-pvc-inputs.yaml
      replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "weblogicDomainStorageReclaimPolicy: Retain", "weblogicDomainStorageReclaimPolicy: Recycle");
    });

    // generate the create-pv-pvc-inputs.yaml
    CommandParams params = new CommandParams().defaults();
    params.command("sh "
        + Paths.get(pvpvcBase.toString(), "create-pv-pvc.sh").toString()
        + " -i " + Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString()
        + " -o "
        + Paths.get(pvpvcBase.toString()));

    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create create-pv-pvc-inputs.yaml");

    //create pv and pvc
    params = new CommandParams().defaults();
    params.command("kubectl create -f " + Paths.get(pvpvcBase.toString(),
        "pv-pvcs/" + ItLifecycleSampleScripts.domainName + "-weblogic-sample-pv.yaml").toString());
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create pv");

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pv {0} to be ready, "
                + "(elapsed time {1}ms, remaining time {2}ms)",
                pvName,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> pvExists(pvName, null),
            String.format("pvExists failed with ApiException for pv %s",
                pvName)));

    params = new CommandParams().defaults();
    params.command("kubectl create -f " + Paths.get(pvpvcBase.toString(),
        "pv-pvcs/" + ItLifecycleSampleScripts.domainName + "-weblogic-sample-pvc.yaml").toString());
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create pvc");

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pv {0} to be ready in namespace {1} "
                + "(elapsed time {2}ms, remaining time {3}ms)",
                pvcName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> pvcExists(pvcName, domainNamespace),
            String.format("pvcExists failed with ApiException for pvc %s",
                pvcName)));

  }

  /**
   * Delete the persistent volumes since the pv is not decorated with label.
   */
  @AfterAll
  public void tearDownAll() {
    deleteDomain();
    deletePersistentVolume(domainName + "-weblogic-sample-pv");
  }

  // Delete domain and verify admin/managed pods are shut down
  private void deleteDomain() {
    //delete the domain resource
    CommandParams params = new CommandParams().defaults();
    params.command("kubectl delete -f "
            + Paths.get(sampleBase.toString(), "weblogic-domains/" + domainName + "/domain.yaml").toString());
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to delete domain custom resource");
    // verify managed servers pods are shut down
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainName, domainNamespace);
    }

    withStandardRetryPolicy
              .conditionEvaluationListener(
                  condition -> logger.info("Waiting for domain {0} to be deleted in namespace {1} "
                                    + "(elapsed time {2}ms, remaining time {3}ms)",
                            domainName,
                            domainNamespace,
                            condition.getElapsedTimeInMS(),
                            condition.getRemainingTimeInMS()))
              .until(domainDoesNotExist(domainName, DOMAIN_VERSION, domainNamespace));
  }

}
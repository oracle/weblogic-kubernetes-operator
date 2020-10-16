// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import oracle.weblogic.kubernetes.actions.TestActions;
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
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
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
 * Tests related to introspectVersion attribute.
 */
@DisplayName("Verify the samples")
@IntegrationTest
public class ItSamples {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private static String domainName = "domain1";
  private static String weblogicCredentialsSecretName = "domain1-weblogic-credentials";

  private Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private Path tempSamplePath = Paths.get(WORK_DIR, "sample-testing");

  String pvName = domainName + "-weblogic-sample-pv";
  String pvcName = domainName + "-weblogic-sample-pvc";

  private static String[] params = {"wlst:domain1", "wdt:domain2"};

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
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for Introspect Version WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);
    createSecretWithUsernamePassword(weblogicCredentialsSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
  }

  // copy samples directory to a temporary location
  private void setupSample() {
    assertDoesNotThrow(() -> {
      // copy ITTESTS_DIR + "/kubernates/samples" to WORK_DIR + "/sample-testing"
      logger.info("Deleting and recreating {0}", tempSamplePath);
      Files.createDirectories(tempSamplePath);
      deleteDirectory(tempSamplePath.toFile());
      Files.createDirectories(tempSamplePath);

      logger.info("Copying {0} to {1}", samplePath, tempSamplePath);
      copyDirectory(samplePath.toFile(), tempSamplePath.toFile());
    });
  }

  // create persistent volume and persistent volume claims used by the samples
  private void createPvPvc(String domainName) {

    Path pvpvcBase = Paths.get(tempSamplePath.toString(),
        "scripts/create-weblogic-domain-pv-pvc");

    // create pv and pvc
    assertDoesNotThrow(() -> {
      // when tests are running in local box the PV directories need to exist
      Path pvHostPath = null;
      pvHostPath = Files.createDirectories(Paths.get(PV_ROOT, this.getClass().getSimpleName(), pvName));

      logger.info("Creating PV directory host path {0}", pvHostPath);
      deleteDirectory(pvHostPath.toFile());
      Files.createDirectories(pvHostPath);

      // set the pvHostPath in create-pv-pvc-inputs.yaml
      replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "#weblogicDomainStoragePath: /scratch/k8s_dir", "weblogicDomainStoragePath: " + pvHostPath);
      replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "namespace: default", "namespace: " + domainNamespace);
      replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "baseName: weblogic-sample", "baseName: " + domainName + "-weblogic-sample");
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
        "pv-pvcs/" + domainName + "-weblogic-sample-pv.yaml").toString());
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
        "pv-pvcs/" + domainName + "-weblogic-sample-pvc.yaml").toString());
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

  @Test
  @MethodSource("paramProvider")
  @DisplayName("Test sample domain in pv")
  public void testSampleDomainInPv(String model) {

    String domainName = model.split(":")[1];
    String script = model.split(":")[0];

    setupSample();
    createPvPvc(domainName);

    Path sampleBase = Paths.get(tempSamplePath.toString(), "scripts/create-weblogic-domain/domain-home-on-pv");

    // change namespace from default to custom
    assertDoesNotThrow(() -> {
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
          "namespace: default", "namespace: " + domainNamespace);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
          "createDomainFilesDir: wlst", "createDomainFilesDir: " + script);
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
    assertTrue(result, "Failed to create create domain");

    final String clusterName = "cluster-1";

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainName + "-" + adminServerName;

    final String managedServerNameBase = "managed-server";
    String managedServerPodNamePrefix = domainName + "-" + managedServerNameBase;
    int replicaCount = 2;

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

    // verify the admin server service created
    checkServiceExists(adminServerPodName, domainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainName, domainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace);
    }

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainName, domainNamespace);
    }
  }
  // generates the stream of objects used by parametrized test.

  private static Stream<String> paramProvider() {
    return Arrays.stream(params);
  }

  /**
   * Uninstall Nginx. The cleanup framework does not uninstall Nginx release. Do it here for now.
   */
  @AfterAll
  public void tearDownAll() {
    TestActions.deletePersistentVolumeClaim(pvcName, domainNamespace);
    TestActions.deletePersistentVolume(pvName);
  }
}

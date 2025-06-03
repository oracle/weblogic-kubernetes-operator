// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.METRICS_SERVER_YAML;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test Horizontal Pod Autoscaler using Kubernetes Metrics Server
 * by increasing the CPU utilization.
 * This test is not run on OKE as the CPU utilization is not
 * going up intermittently after increasing the load.
 */
@DisplayName("Test to a create MII domain and test autoscaling using HPA")
@IntegrationTest
@Tag("kind-parallel")
@Tag("gate")
public class ItHorizontalPodAutoscaler {
  private static String domainNamespace = null;
  static int replicaCount = 2;
  static String wlClusterName = "cluster-1";
  static String clusterResName = "hpacluster";

  private static String adminSecretName;
  private static String encryptionSecretName;
  private static final String domainUid = "hpadomain";
  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  static DomainResource domain = null;

  private static String opServiceAccount = null;
  private static String opNamespace = null;

  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);

    // set the service account name for the operator
    opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);
    createBaseRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");

    domain = createDomainResource(
        domainUid,
        domainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName,
        new String[]{TEST_IMAGES_REPO_SECRET_NAME},
        encryptionSecretName
    );

    // create cluster resouce with limits and requests in serverPod
    ClusterResource clusterResource =
        createClusterResource(clusterResName, wlClusterName, domainNamespace, replicaCount);
    clusterResource.getSpec()
        .serverPod(new ServerPod().resources(
            new V1ResourceRequirements()
                .putLimitsItem("cpu", Quantity.fromString("2"))
                .putLimitsItem("memory", Quantity.fromString("2Gi"))
                .putRequestsItem("cpu", Quantity.fromString("250m"))
                .putRequestsItem("memory", Quantity.fromString("768Mi"))));
    logger.info("Creating cluster {0} in namespace {1}", clusterResName, domainNamespace);
    createClusterAndVerify(clusterResource);

    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));

    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace);

    // check admin server is up and running for domain1
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check managed servers are up and running for domain1
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }


  /**
   * Test autoscaling using HPA by increasing the CPU usage.
   */
  @Test
  void testHPAWithMetricsServer() {
    // install metrics server
    installMetricsServer();

    // create hpa with autoscale
    createHPA();

    // create load on cpu and verify scale up/scale down
    createLoadOnCpuAndVerifyAutoscaling();
  }

  /**
   * Delete metrics server.
   */
  @AfterAll
  public static void cleanUp() {
    if (!SKIP_CLEANUP) {
      getLogger().info("After All cleanUp() method called");
      // delete metrics server
      CommandParams params = new CommandParams().defaults();
      params.command(KUBERNETES_CLI + " delete -f " + METRICS_SERVER_YAML);
      ExecResult result = Command.withParams(params).executeAndReturnResult();
      if (result.exitValue() != 0) {
        getLogger().info(
            "Failed to uninstall metrics server, result " + result);
      } else {
        getLogger().info("uninstalled metrics server");
      }
    }
  }

  /**
   * Install metrics server to collect container resource metrics.
   */
  private void installMetricsServer() {
    // install metrics server
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " apply -f " + METRICS_SERVER_YAML);
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertTrue(result.exitValue() == 0,
        "Failed to install metrics server, result " + result);

    // patch metrics server for fix this error
    // x509: cannot validate certificate for 192.168.65.4 because it doesn't contain any IP SANs
    String patchCmd = KUBERNETES_CLI + " patch deployment metrics-server -n kube-system --type 'json' "
        + "-p '[{\"op\": \"add\", \"path\": \"/spec/template/spec/containers/0/args/-\","
        + "\"value\": \"--kubelet-insecure-tls\"}]'";
    new CommandParams().defaults();
    params.command(patchCmd);
    result = Command.withParams(params).executeAndReturnResult();
    assertTrue(result.exitValue() == 0,
        "Failed to patch metrics server, result " + result);

    // check pods are in ready status
    testUntil(
        podReady("metrics-server", null, "kube-system"),
        logger,
        "{0} to be ready in namespace {1}",
        "metrics-server",
        "kube-system");
  }

  /**
   * Create hpa on the cluster to autoscale with cpu usage over 50%
   * maintaining min replicas 2 and max replicas 4.
   */
  private void createHPA() {
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " autoscale cluster " + clusterResName
        + " --cpu-percent=50 --min=2 --max=4 -n " + domainNamespace);
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertTrue(result.exitValue() == 0,
        "Failed to create hpa or autoscale, result " + result);
    // wait till autoscaler could get the current cpu utilization to make sure it is ready
    testUntil(withStandardRetryPolicy,
        () -> verifyHPA(domainNamespace, clusterResName),
        logger,
        "Get current cpu utilization from hpa {0} in namespace {1}",
        clusterResName,
        domainNamespace);
  }

  private void createLoadOnCpuAndVerifyAutoscaling() {
    // execute command to increase cpu usage
    int duration = (OKE_CLUSTER == true) ? 60 : 30;
    String cmd = KUBERNETES_CLI + " exec -t " + managedServerPrefix + "1 -n "
        + domainNamespace + "  -- timeout --foreground -s 2 "
        + duration + " dd if=/dev/zero of=/dev/null";
    CommandParams params = new CommandParams().defaults();
    params.command(cmd);
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertTrue(result.exitValue() == 124,
        "Command failed to increase cpu usage, result " + result);

    // check cluster is scaled up
    for (int i = 1; i <= 4; i++) {
      final int j = i;
      testUntil(
          withLongRetryPolicy,
          assertDoesNotThrow(() -> checkHPAAndServiceExists(managedServerPrefix + j,
                  domainNamespace),
              String.format("serviceExists failed with ApiException for service %s in namespace %s",
                  managedServerPrefix + j, domainNamespace)),
          logger,
          "service {0} to exist in namespace {1}",
          managedServerPrefix + j,
          managedServerPrefix + j);

      logger.info("Waiting for pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // the command to increase cpu load is ran for 30 sec, after that
    // it takes some time to autoscale down the cluster
    for (int i = 4; i <= 3; i--) {
      final int j = i;
      testUntil(withLongRetryPolicy,
          assertDoesNotThrow(() -> checkHPAAndpodDoesNotExist(
              managedServerPrefix + j, domainUid, domainNamespace),
              String.format("podDoesNotExist failed with ApiException for pod %s in namespace %s",
                  managedServerPrefix + i, domainNamespace)),
          logger,
          "pod {0} to be deleted in namespace {1}",
          managedServerPrefix + i,
          domainNamespace);
    }
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  // verify hpa is getting the metrics
  private boolean verifyHPA(String namespace, String hpaName) {
    CommandParams params = new CommandParams().defaults();
    params.saveResults(true);
    params.command(KUBERNETES_CLI + " get hpa " + hpaName + " -n " + namespace);

    ExecResult result = Command.withParams(params).executeAndReturnResult();
    logger.info("Get HPA result " + result);
    /* check if hpa output contains something like 7%/50%
     * kubectl get hpa --all-namespaces
     * NAMESPACE   NAME         REFERENCE            TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
     * ns-qsjlcw   hpacluster   Cluster/hpacluster   4%/50%    2         4         2          18m
     * when its not ready, it looks
     * NAMESPACE   NAME         REFERENCE            TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
     * ns-qsjlcw   hpacluster   Cluster/hpacluster   <unknown>/50%    2         4         2          18m
     */
    return result.stdout().contains("%/");
  }

  private Callable<Boolean> checkHPAAndpodDoesNotExist(String podName, String domainUid, String namespace) {
    verifyHPA(namespace, clusterResName);
    return podDoesNotExist(podName, domainUid, namespace);
  }

  private Callable<Boolean> checkHPAAndServiceExists(String serviceName, String namespace) {
    verifyHPA(namespace, clusterResName);
    return serviceExists(serviceName, null, namespace);
  }
}

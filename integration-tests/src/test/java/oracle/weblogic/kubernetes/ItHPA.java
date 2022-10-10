// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;

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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
public class ItHPA {
  private static String domainNamespace = null;
  static int replicaCount = 2;
  static String wlClusterName = "cluster-1";
  static String clusterResName = "cluster-1";

  private static String adminSecretName;
  private static String encryptionSecretName;
  private static final String domainUid = "hpadomain";
  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  static DomainResource domain = null;

  private static String opServiceAccount = null;
  private static String opNamespace = null;

  private static LoggingFacade logger = null;
  private static final String METRICS_SERVER_YAML =
      "https://github.com/kubernetes-sigs/metrics-server/releases/download/metrics-server-helm-chart-3.8.2/components.yaml";

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
  }

  @BeforeEach
  public void beforeEach() {
    // check admin server is up and running for domain1
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check managed servers are up and running for domain1
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

  }

  @Test
  void testHPAWithMetricsServer() {
    // install metrics server
    installMetricsServer();

    // create hpa with autoscale
    createHPA();

    // create load on cpu and verify scale up/scale down
    createLoadOnCpuAndVerifyAutoscaling();
  }

  private void installMetricsServer() {
    // install metrics server
    CommandParams params = new CommandParams().defaults();
    params.command("kubectl apply -f " + METRICS_SERVER_YAML);
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertTrue(result.exitValue() == 0,
        "Failed to install metrics server, result " + result);

    // patch metrics server for fix this error
    // x509: cannot validate certificate for 192.168.65.4 because it doesn't contain any IP SANs
    String patchCmd = "kubectl patch deployment metrics-server -n kube-system --type 'json' "
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

  private void createHPA() {
    CommandParams params = new CommandParams().defaults();
    params.command("kubectl autoscale cluster " + clusterResName
        + " --cpu-percent=50 --min=2 --max=4 -n " + domainNamespace);
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertTrue(result.exitValue() == 0,
        "Failed to create hpa or autoscale, result " + result);

  }

  private void createLoadOnCpuAndVerifyAutoscaling() {
    // execute command to increase cpu usage
    String cmd = "kubectl exec -t " + managedServerPrefix + "1 -n "
        + domainNamespace + "  -- timeout --foreground -s 2 30 dd if=/dev/zero of=/dev/null";
    CommandParams params = new CommandParams().defaults();
    params.command(cmd);
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertTrue(result.exitValue() == 124,
        "Command failed to increase cpu usage, result " + result);

    // check cluster is scaled up
    for (int i = 1; i <= 4; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // the command to increase cpu load is ran for 30 sec, after that
    // it takes some time to autoscale down the cluster
    for (int i = 3; i <= 4; i++) {
      final int j = i;
      testUntil(withLongRetryPolicy,
          assertDoesNotThrow(() -> podDoesNotExist(managedServerPrefix + j, domainUid, domainNamespace),
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
}

// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressRule;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressTLS;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LabelSelectorRequirement;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1NFSVolumeSource;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodAffinityTerm;
import io.kubernetes.client.openapi.models.V1PodAntiAffinity;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1WeightedPodAffinityTerm;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.Exec;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readString;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FSS_DIR;
import static oracle.weblogic.kubernetes.TestConstants.GEN_EXTERNAL_REST_IDENTITY_FILE;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.NFS_SERVER;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespacedJob;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithWLDF;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.checkHelmReleaseRevision;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.credentialsNotValid;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.credentialsValid;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusReasonMatches;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.jobCompleted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podInitializing;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEvent;
import static oracle.weblogic.kubernetes.utils.K8sEvents.getEvent;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The common utility class for tests.
 */
public class CommonTestUtils {

  public static ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  public static ConditionFactory withQuickRetryPolicy = with().pollDelay(0, SECONDS)
      .and().with().pollInterval(3, SECONDS)
      .atMost(120, SECONDS).await();

  /**
   * Install WebLogic Remote Console.
   *
   * @return true if WebLogic Remote Console is successfully installed, false otherwise.
   */
  public static boolean installAndVerifyWlsRemoteConsole() {

    assertThat(TestActions.installWlsRemoteConsole())
        .as("WebLogic Remote Console installation succeeds")
        .withFailMessage("WebLogic Remote Console installation failed")
        .isTrue();

    return true;
  }

  /**
   * Shutdown WebLogic Remote Console.
   *
   * @return true if WebLogic Remote Console is successfully shutdown, false otherwise.
   */
  public static boolean shutdownWlsRemoteConsole() {

    assertThat(TestActions.shutdownWlsRemoteConsole())
        .as("WebLogic Remote Console shutdown succeeds")
        .withFailMessage("WebLogic Remote Console shutdown failed")
        .isTrue();

    return true;
  }

  /**
   * Create a domain in the specified namespace and wait up to five minutes until the domain exists.
   *
   * @param domain the oracle.weblogic.domain.Domain object to create domain custom resource
   * @param domainNamespace namespace in which the domain will be created
   * @param domVersion custom resource's version
   */
  public static void createDomainAndVerify(Domain domain,
                                           String domainNamespace,
                                           String... domVersion) {
    String domainVersion = (domVersion.length == 0) ? DOMAIN_VERSION : domVersion[0];

    LoggingFacade logger = getLogger();
    // create the domain CR
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getSpec(), "domain spec is null");
    String domainUid = domain.getSpec().getDomainUid();

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain, domainVersion),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, domainVersion, domainNamespace));

  }

  /**
   * Create a domain in the specified namespace, wait up to five minutes until the domain exists and
   * verify the servers are running.
   *
   * @param domainUid domain
   * @param domain the oracle.weblogic.domain.Domain object to create domain custom resource
   * @param domainNamespace namespace in which the domain will be created
   * @param adminServerPodName admin server pod name
   * @param managedServerPodNamePrefix managed server pod prefix
   * @param replicaCount replica count
   */
  public static void createDomainAndVerify(String domainUid, Domain domain,
                                           String domainNamespace, String adminServerPodName,
                                           String managedServerPodNamePrefix, int replicaCount) {
    LoggingFacade logger = getLogger();

    // create domain and verify
    createDomainAndVerify(domain, domainNamespace);

    // check that admin service/pod exists in the domain namespace
    logger.info("Checking that admin service/pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodNamePrefix + i;

      // check that ms service/pod exists in the domain namespace
      logger.info("Checking that clustered ms service/pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }

  }

  /**
   * add security context constraints to the service account of db namespace.
   * @param serviceAccount - service account to add to scc
   * @param namespace - namespace to which the service account belongs
   */
  public static void addSccToDBSvcAccount(String serviceAccount, String namespace) {
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command("oc adm policy add-scc-to-user anyuid -z " + serviceAccount + " -n " + namespace))
        .execute(), "oc expose service failed");
  }

  /**
   * Execute command inside a pod and assert the execution.
   *
   * @param pod V1Pod object
   * @param containerName name of the container inside the pod
   * @param redirectToStdout if true redirect to stdout and stderr
   * @param command the command to execute inside the pod
   */
  public static void execInPod(V1Pod pod, String containerName, boolean redirectToStdout, String command) {
    LoggingFacade logger = getLogger();
    ExecResult exec = null;
    try {
      logger.info("Executing command {0}", command);
      exec = Exec.exec(pod, containerName, redirectToStdout, "/bin/sh", "-c", command);
      // checking for exitValue 0 for success fails sometimes as k8s exec api returns non-zero
      // exit value even on success, so checking for exitValue non-zero and stderr not empty for failure,
      // otherwise its success
      assertFalse(exec.exitValue() != 0 && exec.stderr() != null && !exec.stderr().isEmpty(),
          String.format("Command %s failed with exit value %s, stderr %s, stdout %s",
              command, exec.exitValue(), exec.stderr(), exec.stdout()));
    } catch (IOException | ApiException | InterruptedException ex) {
      logger.warning(ex.getMessage());
    }
  }

  /**
   * Check pod exists in the specified namespace.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodExists(String podName, String domainUid, String domainNamespace) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podExists(podName, domainUid, domainNamespace),
            String.format("podExists failed with ApiException for pod %s in namespace %s",
                podName, domainNamespace)));
  }

  /**
   * Check pod is ready.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodReady(String podName, String domainUid, String domainNamespace) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podReady(podName, domainUid, domainNamespace),
            String.format("podReady failed with ApiException for pod %s in namespace %s",
                podName, domainNamespace)));
  }

  /**
   * Checks that pod is initializing.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodInitializing(String podName, String domainUid, String domainNamespace) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be initializing in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podInitializing(podName, domainUid, domainNamespace),
            String.format("podReady failed with ApiException for pod %s in namespace %s",
                podName, domainNamespace)));
  }

  /**
   * Check pod is restarted by comparing the pod's creation timestamp with the last timestamp.
   *
   * @param domainUid the label the pod is decorated with
   * @param podName pod name to check
   * @param domNamespace the Kubernetes namespace in which the domain exists
   * @param lastCreationTime the previous creation time
   */
  public static void checkPodRestarted(
      String domainUid,
      String domNamespace,
      String podName,
      OffsetDateTime lastCreationTime
  ) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be restarted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isPodRestarted(podName, domNamespace, lastCreationTime),
            String.format(
                "pod %s has not been restarted in namespace %s", podName, domNamespace)));
  }

  /**
   * Check pod is restarted by comparing the pod's creation timestamp with the last timestamp.
   *
   * @param podName pod name to check
   * @param domNamespace the Kubernetes namespace in which the domain exists
   * @param lastCreationTime the previous creation time
   */
  public static Callable<Boolean> checkIsPodRestarted(String domNamespace,
                                                      String podName,
                                                      OffsetDateTime lastCreationTime) {
    return isPodRestarted(podName, domNamespace, lastCreationTime);
  }

  /**
   * Check service exists in the specified namespace.
   *
   * @param serviceName service name to check
   * @param namespace the namespace in which to check for the service
   */
  public static void checkServiceExists(String serviceName, String namespace) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for service {0} to exist in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                serviceName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> serviceExists(serviceName, null, namespace),
            String.format("serviceExists failed with ApiException for service %s in namespace %s",
                serviceName, namespace)));
  }

  /**
   * Check pod is ready and service exists in the specified namespace.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param namespace the namespace in which the pod exists
   */
  public static void checkPodReadyAndServiceExists(String podName, String domainUid, String namespace) {
    LoggingFacade logger = getLogger();

    logger.info("Check service {0} exists in namespace {1}", podName, namespace);
    checkServiceExists(podName, namespace);

    logger.info("Waiting for pod {0} to be ready in namespace {1}", podName, namespace);
    checkPodReady(podName, domainUid, namespace);
  }

  /**
   * Check pod does not exist in the specified namespace.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param namespace the namespace in which to check whether the pod exists
   */
  public static void checkPodDoesNotExist(String podName, String domainUid, String namespace) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, namespace),
            String.format("podDoesNotExist failed with ApiException for pod %s in namespace %s",
                podName, namespace)));
  }

  /**
   * Check service does not exist in the specified namespace.
   *
   * @param serviceName service name to check
   * @param namespace the namespace in which to check the service does not exist
   */
  public static void checkServiceDoesNotExist(String serviceName, String namespace) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for service {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                serviceName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> serviceDoesNotExist(serviceName, null, namespace),
            String.format("serviceDoesNotExist failed with ApiException for service %s in namespace %s",
                serviceName, namespace)));
  }

  /**
   * Check the status reason of the domain matches the given reason.
   *
   * @param domain  oracle.weblogic.domain.Domain object
   * @param namespace the namespace in which the domain exists
   * @param statusReason the expected status reason of the domain
   */
  public static void checkDomainStatusReasonMatches(Domain domain, String namespace, String statusReason) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for the status reason of the domain {0} in namespace {1} "
                    + "is {2} (elapsed time {3}ms, remaining time {4}ms)",
                domain,
                namespace,
                statusReason,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> domainStatusReasonMatches(domain, statusReason)));
  }

  /**
   * Check whether the cluster's replica count matches with input parameter value.
   *
   * @param clusterName Name of cluster to check
   * @param domainName Name of domain to which cluster belongs
   * @param namespace cluster's namespace
   * @param replicaCount replica count value to match
   * @return true, if the cluster replica count is matched
   */
  public static boolean checkClusterReplicaCountMatches(String clusterName, String domainName,
                                                        String namespace, Integer replicaCount) throws ApiException {
    Cluster cluster = TestActions.getDomainCustomResource(domainName, namespace).getSpec().getClusters()
            .stream().filter(c -> c.clusterName().equals(clusterName)).findAny().orElse(null);
    return Optional.ofNullable(cluster).get().replicas() == replicaCount;
  }

  /**
   * Create a secret with TLS certificate and key in the specified namespace.
   *
   * @param secretName secret name to create
   * @param namespace namespace in which the secret will be created
   * @param keyFile key file containing key for the secret
   * @param certFile certificate file containing certificate for secret
   * @throws java.io.IOException when reading key/cert files fails
   */
  public static void createSecretWithTLSCertKey(
      String secretName, String namespace, Path keyFile, Path certFile) throws IOException {

    LoggingFacade logger = getLogger();
    logger.info("Creating TLS secret {0} in namespace {1} with certfile {2} and keyfile {3}",
        secretName, namespace, certFile, keyFile);

    Map<String, String> data = new HashMap<>();
    data.put("tls.crt", Base64.getEncoder().encodeToString(Files.readAllBytes(certFile)));
    data.put("tls.key", Base64.getEncoder().encodeToString(Files.readAllBytes(keyFile)));

    V1Secret secret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))
        .type("kubernetes.io/tls")
        .stringData(data);

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(secret),
        "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
  }

  /**
   * Create a secret with username and password in the specified namespace.
   *
   * @param secretName secret name to create
   * @param namespace namespace in which the secret will be created
   * @param username username in the secret
   * @param password passowrd in the secret
   */
  public static void createSecretWithUsernamePassword(String secretName,
                                                      String namespace,
                                                      String username,
                                                      String password) {
    Map<String, String> secretMap = new HashMap<>();
    secretMap.put("username", username);
    secretMap.put("password", password);

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))
        .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
  }

  /**
   * Create a RCU secret with username, password and sys_username, sys_password in the specified namespace.
   *
   * @param secretName secret name to create
   * @param namespace namespace in which the secret will be created
   * @param username RCU schema username
   * @param password RCU schema passowrd
   * @param sysUsername DB sys username
   * @param sysPassword DB sys password
   */
  public static void createRcuSecretWithUsernamePassword(String secretName, String namespace,
      String username, String password, String sysUsername, String sysPassword) {
    Map<String, String> secretMap = new HashMap<>();
    secretMap.put("username", username);
    secretMap.put("password", password);
    secretMap.put("sys_username", sysUsername);
    secretMap.put("sys_password", sysPassword);

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))
        .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
  }

  /**
   * Create a RcuAccess secret with RCU schema prefix, RCU schema password and RCU database connection string in the
   * specified namespace.
   *
   * @param secretName secret name to create
   * @param namespace namespace in which the secret will be created
   * @param rcuPrefix  RCU schema prefix
   * @param password RCU schema passoword
   * @param rcuDbConnString RCU database connection string
   */
  public static void createRcuAccessSecret(String secretName, String namespace,
      String rcuPrefix, String password, String rcuDbConnString) {
    Map<String, String> secretMap = new HashMap<>();
    secretMap.put("rcu_db_conn_string", rcuDbConnString);
    secretMap.put("rcu_prefix", rcuPrefix);
    secretMap.put("rcu_schema_password", password);

    getLogger().info("Create RcuAccessSecret: {0} in namespace: {1}, with rcuPrefix {2}, password {3}, "
        + "rcuDbConnString {4} ", secretName, namespace, rcuPrefix, password, rcuDbConnString);
    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))
        .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
  }

  /**
   * Create a RcuAccess secret with RCU schema prefix, RCU schema password and RCU database connection string
   * in the specified namespace.
   *
   * @param secretName secret name to create
   * @param namespace namespace in which the secret will be created
   * @param opsswalletpassword  OPSS wallet password
   */
  public static void createOpsswalletpasswordSecret(String secretName, String namespace,
      String opsswalletpassword) {
    Map<String, String> secretMap = new HashMap<>();
    secretMap.put("walletPassword", opsswalletpassword);

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))
        .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
  }

  /**
   * Update a RcuAccess secret with RCU schema prefix, RCU schema password and RCU database connection string in the
   * specified namespace.
   *
   * @param secretName secret name to update
   * @param namespace namespace in which the secret will be created
   * @param rcuPrefix  RCU schema prefix
   * @param password RCU schema passoword that is being changed to
   * @param rcuDbConnString RCU database connection string
   */
  public static void updateRcuAccessSecret(String secretName, String namespace,
      String rcuPrefix, String password, String rcuDbConnString) {

    assertTrue(Kubernetes.deleteSecret(secretName, namespace),
        String.format("create secret failed for %s", secretName));
    createRcuAccessSecret(secretName, namespace, rcuPrefix, password, rcuDbConnString);

  }

  /**
   * Create a secret with username and password and Elasticsearch host and port in the specified namespace.
   *
   * @param secretName secret name to create
   * @param namespace namespace in which the secret will be created
   * @param username username in the secret
   * @param password passowrd in the secret
   * @param elasticsearchhost Elasticsearch host in the secret
   * @param elasticsearchport Elasticsearch port in the secret
   */
  public static void createSecretWithUsernamePasswordElk(String secretName,
                                                         String namespace,
                                                         String username,
                                                         String password,
                                                         String elasticsearchhost,
                                                         String elasticsearchport) {
    Map<String, String> secretMap = new HashMap<>();
    secretMap.put("username", username);
    secretMap.put("password", password);
    secretMap.put("elasticsearchhost", elasticsearchhost);
    secretMap.put("elasticsearchport", elasticsearchport);

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))
        .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
  }

  /** Scale the WebLogic cluster to specified number of servers.
   *  Verify the sample app can be accessed through NGINX if curlCmd is not null.
   *
   * @param clusterName the WebLogic cluster name in the domain to be scaled
   * @param domainUid the domain to which the cluster belongs
   * @param domainNamespace the namespace in which the domain exists
   * @param manageServerPodNamePrefix managed server pod name prefix
   * @param replicasBeforeScale the replicas of the WebLogic cluster before the scale
   * @param replicasAfterScale the replicas of the WebLogic cluster after the scale
   * @param curlCmd the curl command to verify ingress controller can access the sample apps from all managed servers
   *                in the cluster, if curlCmd is null, the method will not verify the accessibility of the sample app
   *                through ingress controller
   * @param expectedServerNames list of managed servers in the cluster before scale, if curlCmd is null,
   *                            set expectedServerNames to null too
   */
  public static void scaleAndVerifyCluster(String clusterName,
                                           String domainUid,
                                           String domainNamespace,
                                           String manageServerPodNamePrefix,
                                           int replicasBeforeScale,
                                           int replicasAfterScale,
                                           String curlCmd,
                                           List<String> expectedServerNames) {

    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, manageServerPodNamePrefix, replicasBeforeScale,
        replicasAfterScale, false, 0, "", "",
        false, "", "", 0, "", "", curlCmd, expectedServerNames);
  }

  /**
   * Scale the WebLogic cluster to specified number of servers.
   * Verify the sample app can be accessed through NGINX if curlCmd is not null.
   *
   * @param clusterName the WebLogic cluster name in the domain to be scaled
   * @param domainUid the domain to which the cluster belongs
   * @param domainNamespace the namespace in which the domain exists
   * @param manageServerPodNamePrefix managed server pod name prefix
   * @param replicasBeforeScale the replicas of the WebLogic cluster before the scale
   * @param replicasAfterScale the replicas of the WebLogic cluster after the scale
   * @param withRestApi whether to use REST API to scale the cluster
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opNamespace the namespace of WebLogic operator
   * @param opServiceAccount the service account for operator
   * @param withWLDF whether to use WLDF to scale cluster
   * @param domainHomeLocation the domain home location of the domain
   * @param scalingAction scaling action, accepted value: scaleUp or scaleDown
   * @param scalingSize the number of servers to scale up or scale down
   * @param myWebAppName the web app name deployed to the domain
   * @param curlCmdForWLDFApp the curl command to call the web app used in the WLDF script
   * @param curlCmd the curl command to verify ingress controller can access the sample apps from all managed servers
   *                in the cluster, if curlCmd is null, the method will not verify the accessibility of the sample app
   *                through ingress controller
   * @param expectedServerNames list of managed servers in the cluster before scale, if curlCmd is null,
   *                            set expectedServerNames to null too
   */
  public static void scaleAndVerifyCluster(String clusterName,
                                           String domainUid,
                                           String domainNamespace,
                                           String manageServerPodNamePrefix,
                                           int replicasBeforeScale,
                                           int replicasAfterScale,
                                           boolean withRestApi,
                                           int externalRestHttpsPort,
                                           String opNamespace,
                                           String opServiceAccount,
                                           boolean withWLDF,
                                           String domainHomeLocation,
                                           String scalingAction,
                                           int scalingSize,
                                           String myWebAppName,
                                           String curlCmdForWLDFApp,
                                           String curlCmd,
                                           List<String> expectedServerNames) {
    LoggingFacade logger = getLogger();
    // get the original managed server pod creation timestamp before scale
    List<OffsetDateTime> listOfPodCreationTimestamp = new ArrayList<>();
    for (int i = 1; i <= replicasBeforeScale; i++) {
      String managedServerPodName = manageServerPodNamePrefix + i;
      OffsetDateTime originalCreationTimestamp =
          assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                  managedServerPodName, domainNamespace));
      listOfPodCreationTimestamp.add(originalCreationTimestamp);
    }

    // scale the cluster in the domain
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers",
        clusterName, domainUid, domainNamespace, replicasAfterScale);
    if (withRestApi) {
      assertThat(assertDoesNotThrow(() -> scaleClusterWithRestApi(domainUid, clusterName,
          replicasAfterScale, externalRestHttpsPort, opNamespace, opServiceAccount)))
          .as(String.format("Verify scaling cluster %s of domain %s in namespace %s with REST API succeeds",
              clusterName, domainUid, domainNamespace))
          .withFailMessage(String.format("Scaling cluster %s of domain %s in namespace %s with REST API failed",
              clusterName, domainUid, domainNamespace))
          .isTrue();
    } else if (withWLDF) {
      // scale the cluster using WLDF policy
      assertThat(assertDoesNotThrow(() -> scaleClusterWithWLDF(clusterName, domainUid, domainNamespace,
          domainHomeLocation, scalingAction, scalingSize, opNamespace, opServiceAccount, myWebAppName,
          curlCmdForWLDFApp)))
          .as(String.format("Verify scaling cluster %s of domain %s in namespace %s with WLDF policy succeeds",
              clusterName, domainUid, domainNamespace))
          .withFailMessage(String.format("Scaling cluster %s of domain %s in namespace %s with WLDF policy failed",
              clusterName, domainUid, domainNamespace))
          .isTrue();
    } else {
      assertThat(assertDoesNotThrow(() -> scaleCluster(domainUid, domainNamespace, clusterName, replicasAfterScale)))
          .as(String.format("Verify scaling cluster %s of domain %s in namespace %s succeeds",
              clusterName, domainUid, domainNamespace))
          .withFailMessage(String.format("Scaling cluster %s of domain %s in namespace %s failed",
              clusterName, domainUid, domainNamespace))
          .isTrue();
    }

    if (replicasBeforeScale <= replicasAfterScale) {

      // scale up
      // check that the original managed server pod state is not changed during scaling the cluster
      for (int i = 1; i <= replicasBeforeScale; i++) {
        String manageServerPodName = manageServerPodNamePrefix + i;

        // check the original managed server pod state is not changed
        logger.info("Checking that the state of manged server pod {0} is not changed in namespace {1}",
            manageServerPodName, domainNamespace);
        podStateNotChanged(manageServerPodName, domainUid, domainNamespace, listOfPodCreationTimestamp.get(i - 1));
      }

      if (curlCmd != null && expectedServerNames != null) {
        // check that NGINX can access the sample apps from the original managed servers in the domain
        logger.info("Checking that NGINX can access the sample app from the original managed servers in the domain "
            + "while the domain is scaling up.");
        logger.info("expected server name list which should be in the sample app response: {0} before scale",
            expectedServerNames);

        assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNames, 50))
            .as("Verify NGINX can access the sample app from the original managed servers in the domain")
            .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
            .isTrue();
      }

      // check that new managed server pods were created and wait for them to be ready
      for (int i = replicasBeforeScale + 1; i <= replicasAfterScale; i++) {
        String manageServerPodName = manageServerPodNamePrefix + i;

        // check new managed server pod exists in the namespace
        logger.info("Checking that the new managed server pod {0} exists in namespace {1}",
            manageServerPodName, domainNamespace);
        checkPodExists(manageServerPodName, domainUid, domainNamespace);

        // check new managed server pod is ready
        logger.info("Checking that the new managed server pod {0} is ready in namespace {1}",
            manageServerPodName, domainNamespace);
        checkPodReady(manageServerPodName, domainUid, domainNamespace);

        // check new managed server service exists in the namespace
        logger.info("Checking that the new managed server service {0} exists in namespace {1}",
            manageServerPodName, domainNamespace);
        checkServiceExists(manageServerPodName, domainNamespace);

        if (expectedServerNames != null) {
          // add the new managed server to the list
          expectedServerNames.add(manageServerPodName.substring(domainUid.length() + 1));
        }
      }

      if (curlCmd != null && expectedServerNames != null) {
        // check that NGINX can access the sample apps from new and original managed servers
        logger.info("Checking that NGINX can access the sample app from the new and original managed servers "
            + "in the domain after the cluster is scaled up. Expected server names: {0}", expectedServerNames);
        assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNames, 50))
            .as("Verify NGINX can access the sample app from all managed servers in the domain")
            .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
            .isTrue();
      }
    } else {
      // scale down
      // wait and check the pods are deleted
      for (int i = replicasBeforeScale; i > replicasAfterScale; i--) {
        String managedServerPodName = manageServerPodNamePrefix + i;
        logger.info("Checking that managed server pod {0} was deleted from namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodDoesNotExist(managedServerPodName, domainUid, domainNamespace);
        if (expectedServerNames != null) {
          expectedServerNames.remove(managedServerPodName.substring(domainUid.length() + 1));
        }
      }

      if (curlCmd != null && expectedServerNames != null) {
        // check that NGINX can access the app from the remaining managed servers in the domain
        logger.info("Checking that NGINX can access the sample app from the remaining managed servers in the domain "
            + "after the cluster is scaled down. Expected server name: {0}", expectedServerNames);
        assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNames, 50))
            .as("Verify NGINX can access the sample app from the remaining managed server in the domain")
            .withFailMessage("NGINX can not access the sample app from the remaining managed server")
            .isTrue();
      }
    }
  }

  /**
   * Create a persistent volume and persistent volume claim.
   *
   * @param v1pv V1PersistentVolume object to create the persistent volume
   * @param v1pvc V1PersistentVolumeClaim object to create the persistent volume claim
   * @param labelSelector String containing the labels the PV is decorated with
   * @param namespace the namespace in which the persistence volume claim to be created
   *
   **/
  public static void createPVPVCAndVerify(V1PersistentVolume v1pv,
                                          V1PersistentVolumeClaim v1pvc,
                                          String labelSelector,
                                          String namespace) {
    LoggingFacade logger = getLogger();
    assertNotNull(v1pv, "v1pv is null");
    assertNotNull(v1pvc, "v1pvc is null");

    String pvName = v1pv.getMetadata().getName();
    String pvcName = v1pvc.getMetadata().getName();

    logger.info("Creating persistent volume {0}", pvName);
    assertTrue(assertDoesNotThrow(() -> createPersistentVolume(v1pv),
        "Persistent volume creation failed with ApiException "),
        "PersistentVolume creation failed");

    logger.info("Creating persistent volume claim {0}", pvcName);
    assertTrue(assertDoesNotThrow(() -> createPersistentVolumeClaim(v1pvc),
        "Persistent volume claim creation failed with ApiException"),
        "PersistentVolumeClaim creation failed");

    // check the persistent volume and persistent volume claim exist
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for persistent volume {0} exists "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                pvName,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> pvExists(pvName, labelSelector),
            String.format("pvExists failed with ApiException when checking pv %s", pvName)));

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for persistent volume claim {0} exists in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                pvcName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> pvcExists(pvcName, namespace),
            String.format("pvcExists failed with ApiException when checking pvc %s in namespace %s",
                pvcName, namespace)));
  }

  /**
   * Create a persistent volume and persistent volume claim.
   *
   * @param v1pv V1PersistentVolume object to create the persistent volume
   * @param v1pvc V1PersistentVolumeClaim object to create the persistent volume claim
   * @param labelSelector String containing the labels the PV is decorated with
   * @param namespace the namespace in which the persistence volume claim to be created
   * @param storageClassName the name for storage class
   * @param pvHostPath path to pv dir if hostpath is used, ignored if nfs
   *
   **/
  public static void createPVPVCAndVerify(V1PersistentVolume v1pv,
                                          V1PersistentVolumeClaim v1pvc,
                                          String labelSelector,
                                          String namespace, String storageClassName, Path pvHostPath) {
    LoggingFacade logger = getLogger();
    if (!OKE_CLUSTER) {
      logger.info("Creating PV directory {0}", pvHostPath);
      assertDoesNotThrow(() -> deleteDirectory(pvHostPath.toFile()), "deleteDirectory failed with IOException");
      assertDoesNotThrow(() -> createDirectories(pvHostPath), "createDirectories failed with IOException");
    }
    if (OKE_CLUSTER) {
      v1pv.getSpec()
          .storageClassName("oci-fss")
          .nfs(new V1NFSVolumeSource()
              .path(FSS_DIR)
              .server(NFS_SERVER)
              .readOnly(false));
    } else {
      v1pv.getSpec()
          .storageClassName(storageClassName)
          .hostPath(new V1HostPathVolumeSource()
              .path(pvHostPath.toString()));
    }
    if (OKE_CLUSTER) {
      v1pvc.getSpec()
          .storageClassName("oci-fss");
    } else {
      v1pvc.getSpec()
          .storageClassName(storageClassName);
    }
    createPVPVCAndVerify(v1pv, v1pvc, labelSelector, namespace);
  }

  /**
   * Create ConfigMap from the specified files.
   * @param configMapName name of the ConfigMap to create
   * @param files files to be added in ConfigMap
   * @param namespace the namespace in which the ConfigMap to be created
   */
  public static void createConfigMapFromFiles(String configMapName,
                                              List<Path> files,
                                              String namespace) {

    // create a ConfigMap of the domain
    Map<String, String> data = new HashMap<>();
    for (Path file : files) {
      data.put(file.getFileName().toString(),
          assertDoesNotThrow(() -> readString(file), "readString failed with IOException"));
    }

    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(new V1ObjectMeta()
            .name(configMapName)
            .namespace(namespace));

    assertTrue(assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("createConfigMap failed with ApiException for ConfigMap %s with files %s in namespace %s",
            configMapName, files, namespace)),
        String.format("createConfigMap failed while creating ConfigMap %s in namespace %s", configMapName, namespace));
  }

  /**
   * Create a job in the specified namespace and wait until it completes.
   *
   * @param jobBody V1Job object to create in the specified namespace
   * @param namespace the namespace in which the job will be created
   */
  public static String createJobAndWaitUntilComplete(V1Job jobBody, String namespace) {
    LoggingFacade logger = getLogger();
    String jobName = assertDoesNotThrow(() -> createNamespacedJob(jobBody), "createNamespacedJob failed");

    logger.info("Checking if the job {0} completed in namespace {1}", jobName, namespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for job {0} to be completed in namespace {1} "
                    + "(elapsed time {2} ms, remaining time {3} ms)",
                jobName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(jobCompleted(jobName, null, namespace));

    return jobName;
  }

  /**
   * Get the PodCreationTimestamp of a pod in a namespace.
   *
   * @param namespace Kubernetes namespace that the domain is hosted
   * @param podName name of the pod
   * @return PodCreationTimestamp of the pod
   */
  public static OffsetDateTime getPodCreationTime(String namespace, String podName) {
    LoggingFacade logger = getLogger();
    OffsetDateTime podCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(namespace, "", podName),
            String.format("Couldn't get PodCreationTimestamp for pod %s", podName));
    assertNotNull(podCreationTime, "Got null PodCreationTimestamp");
    logger.info("PodCreationTimestamp for pod {0} in namespace {1} is {2}",
        podName,
        namespace,
        podCreationTime);
    return podCreationTime;
  }

  /**
   * Create a Kubernetes ConfigMap with the given parameters and verify that the operation succeeds.
   *
   * @param configMapName the name of the Kubernetes ConfigMap to be created
   * @param domainUid the domain to which the cluster belongs
   * @param namespace Kubernetes namespace that the domain is hosted
   * @param modelFiles list of the file names along with path for the WDT model files in the ConfigMap
   */
  public static void createConfigMapAndVerify(
      String configMapName,
      String domainUid,
      String namespace,
      List<String> modelFiles) {
    LoggingFacade logger = getLogger();
    assertNotNull(configMapName, "ConfigMap name cannot be null");

    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);

    assertNotNull(configMapName, "ConfigMap name cannot be null");

    logger.info("Create ConfigMap {0} that contains model files {1}",
        configMapName, modelFiles);

    Map<String, String> data = new HashMap<>();

    for (String modelFile : modelFiles) {
      addModelFile(data, modelFile);
    }

    V1ObjectMeta meta = new V1ObjectMeta()
        .labels(labels)
        .name(configMapName)
        .namespace(namespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    assertTrue(assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Create ConfigMap %s failed due to Kubernetes client  ApiException", configMapName)),
        String.format("Failed to create ConfigMap %s", configMapName));
  }

  /**
   * Read the content of a model file as a String and add it to a map.
   */
  private static void addModelFile(Map<String, String> data, String modelFile) {
    LoggingFacade logger = getLogger();
    logger.info("Add model file {0}", modelFile);

    String cmData = assertDoesNotThrow(() -> Files.readString(Paths.get(modelFile)),
        String.format("Failed to read model file %s", modelFile));
    assertNotNull(cmData,
        String.format("Failed to read model file %s", modelFile));

    data.put(modelFile.substring(modelFile.lastIndexOf("/") + 1), cmData);
  }

  /**
   * Create an external REST Identity secret in the specified namespace.
   *
   * @param namespace the namespace in which the secret to be created
   * @param secretName name of the secret to be created
   * @return true if the command to create secret succeeds, false otherwise
   */
  public static boolean createExternalRestIdentitySecret(String namespace, String secretName) {

    StringBuffer command = new StringBuffer()
        .append(GEN_EXTERNAL_REST_IDENTITY_FILE);

    if (Character.isDigit(K8S_NODEPORT_HOST.charAt(0))) {
      command.append(" -a \"IP:");
    } else {
      command.append(" -a \"DNS:");
    }

    command.append(K8S_NODEPORT_HOST)
        .append(",DNS:localhost,IP:127.0.0.1\"")
        .append(" -n ")
        .append(namespace)
        .append(" -s ")
        .append(secretName);

    CommandParams params = Command
        .defaultCommandParams()
        .command(command.toString())
        .saveResults(true)
        .redirect(true);

    return Command.withParams(params).execute();
  }

  /**
   * Check that the given credentials are valid to access the WebLogic domain.
   *
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @param expectValid true if the check expects a successful result
   */
  public static void verifyCredentials(
      String podName,
      String namespace,
      String username,
      String password,
      boolean expectValid) {

    verifyCredentials(null, podName, namespace, username, password, expectValid);
  }

  /**
   * Check that the given credentials are valid to access the WebLogic domain.
   *
   * @param host this is only for OKD - ingress host to access the service
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @param expectValid true if the check expects a successful result
   */
  public static void verifyCredentials(
      String host,
      String podName,
      String namespace,
      String username,
      String password,
      boolean expectValid,
      String... args) {
    LoggingFacade logger = getLogger();
    String msg = expectValid ? "valid" : "invalid";
    logger.info("Check if the given WebLogic admin credentials are {0}", msg);
    String finalHost = host != null ? host : K8S_NODEPORT_HOST;
    logger.info("finalHost = {0}", finalHost);
    withQuickRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Checking that credentials {0}/{1} are {2}"
                    + "(elapsed time {3}ms, remaining time {4}ms)",
                username,
                password,
                msg,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(
            expectValid
                ?
            () -> credentialsValid(finalHost, podName, namespace, username, password, args)
                :
            () -> credentialsNotValid(finalHost, podName, namespace, username, password, args),
            String.format(
                "Failed to validate credentials %s/%s on pod %s in namespace %s",
                username, password, podName, namespace)));
  }


  /**
   * Generate a text file in RESULTS_ROOT directory by replacing template value.
   * @param inputTemplateFile input template file
   * @param outputFile output file to be generated. This file will be copied to RESULTS_ROOT. If outputFile contains
   *                   a directory, then the directory will created if it does not exist.
   *                   example - crossdomxaction/istio-cdt-http-srvice.yaml
   * @param templateMap map containing template variable(s) to be replaced
   * @return path of the generated file - will be under RESULTS_ROOT
  */
  public static Path generateFileFromTemplate(
       String inputTemplateFile, String outputFile,
       Map<String, String> templateMap) throws IOException {

    LoggingFacade logger = getLogger();

    Path targetFileParent = Paths.get(outputFile).getParent();
    if (targetFileParent != null) {
      checkDirectory(targetFileParent.toString());
    }
    Path srcFile = Paths.get(inputTemplateFile);
    Path targetFile = Paths.get(RESULTS_ROOT, outputFile);
    logger.info("Copying  source file {0} to target file {1}", inputTemplateFile, targetFile.toString());

    // Add the parent directory for the target file
    Path parentDir = targetFile.getParent();
    Files.createDirectories(parentDir);
    Files.copy(srcFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
    String out = targetFile.toString();
    for (Map.Entry<String, String> entry : templateMap.entrySet()) {
      logger.info("Replacing String {0} with the value {1}", entry.getKey(), entry.getValue());
      FileUtils.replaceStringInFile(out, entry.getKey(), entry.getValue());
    }
    return targetFile;
  }

  /**
   * Check the application running in WebLogic server using host information in the header.
   * @param url url to access the application
   * @param hostHeader host information to be passed as http header
   * @return true if curl command returns HTTP code 200 otherwise false
  */
  public static boolean checkAppUsingHostHeader(String url, String hostHeader) {
    LoggingFacade logger = getLogger();
    StringBuffer curlString = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    StringBuffer headerString = null;
    if (hostHeader != null) {
      headerString = new StringBuffer("-H 'host: ");
      headerString.append(hostHeader)
                  .append(" ' ");
    } else {
      headerString = new StringBuffer("");
    }
    curlString.append(" --noproxy '*' ")
         .append(" --silent --show-error ")
         .append(headerString.toString())
         .append(url)
         .append(" -o /dev/null")
         .append(" -w %{http_code});")
         .append("echo ${status}");
    logger.info("checkAppUsingHostInfo: curl command {0}", new String(curlString));
    withQuickRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for appliation to be ready {0} "
                + "(elapsed time {1} ms, remaining time {2} ms)",
                url,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> {
          return () -> {
            return exec(new String(curlString), true).stdout().contains("200");
          };
        }));
    return true;
  }

  /**
   * Check if the the application is accessible inside the WebLogic server pod.
   * @param conditionFactory condition factory
   * @param namespace namespace of the domain
   * @param podName name of the pod
   * @param internalPort internal port of the managed server running in the pod
   * @param appPath path to access the application
   * @param expectedStr expected response from the app
   */
  public static void checkAppIsRunning(
      ConditionFactory conditionFactory,
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {

    // check if the application is accessible inside of a server pod
    conditionFactory
        .conditionEvaluationListener(
            condition -> getLogger().info("Waiting for application {0} is running on pod {1} in namespace {2} "
                    + "(elapsed time {3}ms, remaining time {4}ms)",
                appPath,
                podName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(() -> appAccessibleInPod(
            namespace,
            podName,
            internalPort,
            appPath,
            expectedStr));

  }

  /**
   * Check if the the application is active for a given weblogic target.
   * @param host hostname to construct the REST url
   * @param port the port construct the REST url
   * @param headers extra header info to pass to the REST url
   * @param application name of the application
   * @param target the weblogic target for the application
   * @param username username to log into the system 
   * @param password password for the username
   */
  public static boolean checkAppIsActive(
      String host,
      int    port,
      String headers,
      String application,
      String target,
      String username,
      String password
  ) {

    LoggingFacade logger = getLogger();
    String curlString = String.format("curl -v --show-error --noproxy '*' "
           + "--user " + username + ":" + password + " " + headers  
           + " -H X-Requested-By:MyClient -H Accept:application/json "
           + "-H Content-Type:application/json " 
           + " -d \"{ target: '" + target + "' }\" "
           + " -X POST "
           + "http://%s:%s/management/weblogic/latest/domainRuntime/deploymentManager/appDeploymentRuntimes/"
           + application + "/getState", host, port);

    logger.info("curl command {0}", curlString);
    withStandardRetryPolicy 
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for Application {0} to be active "
                + "(elapsed time {1} ms, remaining time {2} ms)",
                application,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> {
          return () -> {
            return exec(new String(curlString), true).stdout().contains("STATE_ACTIVE");
          };
        }));
    return true;
  }

  /** Create a persistent volume.
   * @param pvName name of the persistent volume to create
   * @param domainUid domain UID
   * @param className name of the class to call this method
  */
  public static void createPV(String pvName, String domainUid, String className) {

    LoggingFacade logger = getLogger();
    logger.info("creating persistent volume for pvName {0}, domainUid: {1}, className: {2}",
        pvName, domainUid, className);
    Path pvHostPath = null;
    // when tests are running in local box the PV directories need to exist
    if (!OKE_CLUSTER && !OKD) {
      try {
        pvHostPath = Files.createDirectories(Paths.get(
            PV_ROOT, className, pvName));
        logger.info("Creating PV directory host path {0}", pvHostPath);
        deleteDirectory(pvHostPath.toFile());
        createDirectories(pvHostPath);
      } catch (IOException ioex) {
        logger.severe(ioex.getMessage());
        fail("Create persistent volume host path failed");
      }
    }

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("5Gi"))
            .persistentVolumeReclaimPolicy("Recycle")
            .accessModes(Arrays.asList("ReadWriteMany")))
        .metadata(new V1ObjectMeta()
            .name(pvName)
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));
    if (OKE_CLUSTER) {
      v1pv.getSpec()
          .storageClassName("oci-fss")
          .nfs(new V1NFSVolumeSource()
          .path(FSS_DIR)
          .server(NFS_SERVER)
          .readOnly(false));
    } else if (OKD) {
      v1pv.getSpec()
          .storageClassName("okd-nfsmnt")
          .nfs(new V1NFSVolumeSource()
          .path(PV_ROOT)
          .server(NFS_SERVER)
          .readOnly(false));
    } else {
      v1pv.getSpec()
          .storageClassName("weblogic-domain-storage-class")
          .hostPath(new V1HostPathVolumeSource()
          .path(pvHostPath.toString()));
    }
    boolean success = assertDoesNotThrow(() -> createPersistentVolume(v1pv),
        "Failed to create persistent volume");
    assertTrue(success, "PersistentVolume creation failed");
  }

  /**
   * Create a persistent volume claim.
   *
   * @param pvName name of the persistent volume
   * @param pvcName name of the persistent volume claim to create
   * @param domainUid UID of the WebLogic domain
   * @param namespace name of the namespace in which to create the persistent volume claim
   */
  public static void createPVC(String pvName, String pvcName, String domainUid, String namespace) {

    LoggingFacade logger = getLogger();
    logger.info("creating persistent volume claim for pvName {0}, pvcName {1}, "
        + "domainUid: {2}, namespace: {3}", pvName, pvcName, domainUid, namespace);
    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeName(pvName)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("5Gi"))))
        .metadata(new V1ObjectMeta()
            .name(pvcName)
            .namespace(namespace)
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));

    if (OKE_CLUSTER) {
      v1pvc.getSpec()
          .storageClassName("oci-fss");
    } else if (OKD) {
      v1pvc.getSpec()
          .storageClassName("okd-nfsmnt");
    } else {
      v1pvc.getSpec()
          .storageClassName("weblogic-domain-storage-class");
    }
    boolean success = assertDoesNotThrow(() -> createPersistentVolumeClaim(v1pvc),
        "Failed to create persistent volume claim");
    assertTrue(success, "PersistentVolumeClaim creation failed");
  }

  /**
   * Create configmap containing domain creation scripts.
   *
   * @param configMapName name of the configmap to create
   * @param files files to add in configmap
   * @param namespace name of the namespace in which to create configmap
   * @param className name of the class to call this method
   * @throws IOException when reading the domain script files fail
   * @throws ApiException if create configmap fails
   */
  public static void createConfigMapForDomainCreation(String configMapName, List<Path> files,
      String namespace, String className)
      throws ApiException, IOException {

    LoggingFacade logger = getLogger();
    logger.info("Creating configmap {0}, namespace {1}, className {2}", configMapName, namespace, className);

    Path domainScriptsDir = Files.createDirectories(
        Paths.get(TestConstants.LOGS_DIR, className, namespace));

    // add domain creation scripts and properties files to the configmap
    Map<String, String> data = new HashMap<>();
    for (Path file : files) {
      logger.info("Adding file {0} in configmap", file);
      data.put(file.getFileName().toString(), Files.readString(file));
      logger.info("Making a copy of file {0} to {1} for diagnostic purposes", file,
          domainScriptsDir.resolve(file.getFileName()));
      Files.copy(file, domainScriptsDir.resolve(file.getFileName()));
    }
    V1ObjectMeta meta = new V1ObjectMeta()
        .name(configMapName)
        .namespace(namespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Failed to create configmap %s with files %s", configMapName, files));
    assertTrue(cmCreated, String.format("Failed while creating ConfigMap %s", configMapName));
  }


  /**
   * Create a job to create a domain in persistent volume.
   *
   * @param image image name used to create the domain
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param domainScriptCM configmap holding domain creation script files
   * @param namespace name of the domain namespace in which the job is created
   * @param jobContainer V1Container with job commands to create domain
   */
  public static void createDomainJob(String image, String pvName,
                               String pvcName, String domainScriptCM, String namespace, V1Container jobContainer) {
    createDomainJob(image, pvName, pvcName, domainScriptCM, namespace, jobContainer, null);
  }

  /**
   * Create a job to create a domain in persistent volume.
   *
   * @param image             image name used to create the domain
   * @param pvName            name of the persistent volume to create domain in
   * @param pvcName           name of the persistent volume claim
   * @param domainScriptCM    configmap holding domain creation script files
   * @param namespace         name of the domain namespace in which the job is created
   * @param jobContainer      V1Container with job commands to create domain
   * @param podAnnotationsMap annotations for the job pod
   */
  public static void createDomainJob(String image, String pvName, String pvcName, String domainScriptCM,
                                     String namespace, V1Container jobContainer, Map podAnnotationsMap) {

    LoggingFacade logger = getLogger();
    logger.info("Running Kubernetes job to create domain for image: {0}"
        + " pvName: {1}, pvcName: {2}, domainScriptCM: {3}, namespace: {4}", image,
        pvName, pvcName, domainScriptCM, namespace);

    V1PodSpec podSpec = new V1PodSpec()
                    .restartPolicy("Never")
                    .containers(Arrays.asList(jobContainer  // container containing WLST or WDT details
                        .name("create-weblogic-domain-onpv-container")
                        .image(image)
                        .imagePullPolicy("IfNotPresent")
                        .ports(Arrays.asList(new V1ContainerPort()
                            .containerPort(7001)))
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("create-weblogic-domain-job-cm-volume") // domain creation scripts volume
                                .mountPath("/u01/weblogic"), // available under /u01/weblogic inside pod
                            new V1VolumeMount()
                                .name(pvName) // location to write domain
                                .mountPath("/shared"))))) // mounted under /shared inside pod
                    .volumes(Arrays.asList(
                        new V1Volume()
                            .name(pvName)
                            .persistentVolumeClaim(
                                new V1PersistentVolumeClaimVolumeSource()
                                    .claimName(pvcName)),
                        new V1Volume()
                            .name("create-weblogic-domain-job-cm-volume")
                            .configMap(
                                new V1ConfigMapVolumeSource()
                                    .name(domainScriptCM)))) //config map containing domain scripts
                    .imagePullSecrets(Arrays.asList(
                        new V1LocalObjectReference()
                            .name(BASE_IMAGES_REPO_SECRET)));
    if (!OKD) {
      podSpec.initContainers(Arrays.asList(createfixPVCOwnerContainer(pvName, "/shared")));
    }

    V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec();
    if (podAnnotationsMap != null) {
      podTemplateSpec.metadata(new V1ObjectMeta()
          .annotations(podAnnotationsMap));
    }
    podTemplateSpec.spec(podSpec);

    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("create-domain-onpv-job-" + pvName) // name of the create domain job
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(podTemplateSpec));
    String jobName = assertDoesNotThrow(()
        -> createNamespacedJob(jobBody), "Failed to create Job");

    logger.info("Checking if the domain creation job {0} completed in namespace {1}",
        jobName, namespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for job {0} to be completed in namespace {1} "
                    + "(elapsed time {2} ms, remaining time {3} ms)",
                jobName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(jobCompleted(jobName, null, namespace));

    // check job status and fail test if the job failed to create domain
    V1Job job = assertDoesNotThrow(() -> getJob(jobName, namespace),
        "Getting the job failed");
    if (job != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equalsIgnoreCase(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        logger.severe("Job {0} failed to create domain", jobName);
        List<V1Pod> pods = assertDoesNotThrow(() -> listPods(
            namespace, "job-name=" + jobName).getItems(),
            "Listing pods failed");
        if (!pods.isEmpty()) {
          String podLog = assertDoesNotThrow(() -> getPodLog(pods.get(0).getMetadata().getName(), namespace),
              "Failed to get pod log");
          logger.severe(podLog);
          fail("Domain create job failed");
        }
      }
    }
  }

  /**
   * Verify the default secret exists for the default service account.
   *
   */
  public static void verifyDefaultTokenExists() {
    final LoggingFacade logger = getLogger();

    ConditionFactory withStandardRetryPolicy
        = with().pollDelay(0, SECONDS)
        .and().with().pollInterval(5, SECONDS)
        .atMost(5, MINUTES).await();

    withStandardRetryPolicy.conditionEvaluationListener(
        condition -> logger.info("Waiting for the default token to be available in default service account, "
                + "elapsed time {0}, remaining time {1}",
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(() -> {
          V1ServiceAccountList sas = Kubernetes.listServiceAccounts("default");
          for (V1ServiceAccount sa : sas.getItems()) {
            if (sa.getMetadata().getName().equals("default")) {
              List<V1ObjectReference> secrets = sa.getSecrets();
              return !secrets.isEmpty();
            }
          }
          return false;
        });
  }

  /**
   * Get the creationTimestamp for the domain admin server pod and managed server pods.
   *
   * @param domainNamespace namespace where the domain is
   * @param adminServerPodName the pod name of the admin server
   * @param managedServerPrefix prefix of the managed server pod name
   * @param replicaCount replica count of the managed servers
   * @return map of domain admin server pod and managed server pods with their corresponding creationTimestamps
   */
  public static Map getPodsWithTimeStamps(String domainNamespace, String adminServerPodName,
       String managedServerPrefix, int replicaCount) {

    // create the map with server pods and their original creation timestamps
    Map<String, OffsetDateTime> podsWithTimeStamps = new LinkedHashMap<>();
    podsWithTimeStamps.put(adminServerPodName,
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
            String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                adminServerPodName, domainNamespace)));

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;
      podsWithTimeStamps.put(managedServerPodName,
          assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                  managedServerPodName, domainNamespace)));
    }
    return podsWithTimeStamps;
  }

  public static String getExternalServicePodName(String adminServerPodName) {
    return getExternalServicePodName(adminServerPodName, TestConstants.DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  public static String getExternalServicePodName(String adminServerPodName, String suffix) {
    return adminServerPodName + suffix;
  }

  public static String getIntrospectJobName(String domainUid) {
    return domainUid + TestConstants.DEFAULT_INTROSPECTOR_JOB_NAME_SUFFIX;
  }

  /**
   * Get the introspector pod name.
   * @param domainUid domain uid of the domain
   * @param domainNamespace domain namespace in which introspector runs
   * @return the introspector pod name
   * @throws ApiException if Kubernetes API calls fail
   */
  public static String getIntrospectorPodName(String domainUid, String domainNamespace) throws ApiException {
    checkPodExists(getIntrospectJobName(domainUid), domainUid, domainNamespace);

    String labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);

    V1Pod introspectorPod = getPod(domainNamespace, labelSelector, getIntrospectJobName(domainUid));

    if (introspectorPod != null && introspectorPod.getMetadata() != null) {
      return introspectorPod.getMetadata().getName();
    } else {
      return "";
    }
  }

  /**
   * Set the inter-pod anti-affinity  for the domain custom resource
   * so that server instances spread over the available Nodes.
   *
   * @param domain custom resource object
   */
  public static synchronized void setPodAntiAffinity(Domain domain) {
    domain.getSpec()
        .getClusters()
        .stream()
        .forEach(
            cluster -> {
              cluster
                  .serverPod(new ServerPod()
                      .affinity(new V1Affinity().podAntiAffinity(
                          new V1PodAntiAffinity()
                              .addPreferredDuringSchedulingIgnoredDuringExecutionItem(
                                  new V1WeightedPodAffinityTerm()
                                      .weight(100)
                                      .podAffinityTerm(new V1PodAffinityTerm()
                                          .topologyKey("kubernetes.io/hostname")
                                          .labelSelector(new V1LabelSelector()
                                              .addMatchExpressionsItem(new V1LabelSelectorRequirement()
                                                  .key("weblogic.clusterName")
                                                  .operator("In")

                                                  .addValuesItem("$(CLUSTER_NAME)")))
                                      )))));

            }
        );

  }

  /**
   * Create container to fix pvc owner for pod.
   *
   * @param pvName name of pv
   * @param mountPath mounting path for pv
   * @return container object with required ownership based on OKE_CLUSTER variable value.
   */
  public static synchronized V1Container createfixPVCOwnerContainer(String pvName, String mountPath) {
    String argCommand = "chown -R 1000:0 " + mountPath;
    if (OKE_CLUSTER) {
      argCommand = "chown 1000:0 " + mountPath
          + "/. && find "
          + mountPath
          + "/. -maxdepth 1 ! -name '.snapshot' ! -name '.' -print0 | xargs -r -0  chown -R 1000:0";
    }
    V1Container container = new V1Container()
            .name("fix-pvc-owner") // change the ownership of the pv to opc:opc
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .addCommandItem("/bin/sh")
            .addArgsItem("-c")
            .addArgsItem(argCommand)
            .volumeMounts(Arrays.asList(
                new V1VolumeMount()
                    .name(pvName)
                    .mountPath(mountPath)))
            .securityContext(new V1SecurityContext()
                .runAsGroup(0L)
                .runAsUser(0L));
    return container;
  }

  /**
   * Patch the domain with server start policy.
   *
   * @param patchPath JSON path of the patch
   * @param policy server start policy
   * @param domainNamespace namespace where domain exists
   * @param domainUid unique id of domain
   */
  public static void patchServerStartPolicy(String patchPath, String policy, String domainNamespace,
      String domainUid) {
    final LoggingFacade logger = getLogger();
    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"")
        .append(patchPath)
        .append("\",")
        .append(" \"value\":  \"")
        .append(policy)
        .append("\"")
        .append(" }]");

    logger.info("The domain resource patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource failed");
  }

  /**
   * Check if the pods are deleted.
   * @param podName pod name
   * @param domainUid unique id of the domain
   * @param domNamespace namespace where domain exists
   */
  public static void checkPodDeleted(String podName, String domainUid, String domNamespace) {
    final LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, domNamespace),
            String.format("podDoesNotExist failed with ApiException for %s in namespace in %s",
                podName, domNamespace)));
  }

  /**
   * Check the system resource configuration using REST API.
   * @param nodePort admin node port
   * @param resourcesType type of the resource
   * @param resourcesName name of the resource
   * @param expectedStatusCode expected status code
   * @return true if the REST API results matches expected status code
   */
  public static boolean checkSystemResourceConfiguration(int nodePort, String resourcesType,
                                                   String resourcesName, String expectedStatusCode) {
    final LoggingFacade logger = getLogger();
    StringBuffer curlString = new StringBuffer("status=$(curl --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + K8S_NODEPORT_HOST + ":" + nodePort)
        .append("/management/weblogic/latest/domainConfig")
        .append("/")
        .append(resourcesType)
        .append("/")
        .append(resourcesName)
        .append("/")
        .append(" --silent --show-error ")
        .append(" -o /dev/null ")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkSystemResource: curl command {0}", new String(curlString));
    return new Command()
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedStatusCode);
  }

  /**
   * Check the system resource configuration using REST API.
   * @param nodePort admin node port
   * @param resourcesPath path of the resource
   * @param expectedValue expected value returned in the REST call
   * @return true if the REST API results matches expected status code
   */
  public static boolean checkSystemResourceConfig(int nodePort, String resourcesPath, String expectedValue) {
    final LoggingFacade logger = getLogger();
    StringBuffer curlString = new StringBuffer("curl --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + K8S_NODEPORT_HOST + ":" + nodePort)
        .append("/management/weblogic/latest/domainConfig")
        .append("/")
        .append(resourcesPath)
        .append("/");

    logger.info("checkSystemResource: curl command {0}", new String(curlString));
    return new Command()
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedValue);
  }

  /**
   * Check the system resource runtime using REST API.
   * @param nodePort admin node port
   * @param resourcesUrl url of the resource
   * @param expectedValue expected value returned in the REST call
   * @return true if the REST API results matches expected value
   */
  public static boolean checkSystemResourceRuntime(int nodePort, String resourcesUrl, String expectedValue) {
    final LoggingFacade logger = getLogger();
    StringBuffer curlString = new StringBuffer("curl --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + K8S_NODEPORT_HOST + ":" + nodePort)
        .append("/management/weblogic/latest/domainRuntime")
        .append("/")
        .append(resourcesUrl)
        .append("/");

    logger.info("checkSystemResource: curl command {0} expectedValue {1}", new String(curlString), expectedValue);
    return new Command()
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedValue);
  }

  /**
   * Deploy application and access the application once to make sure the app is accessible.
   * @param domainNamespace namespace where domain exists
   * @param domainUid the domain to which the cluster belongs
   * @param clusterName the WebLogic cluster name that the app deploys to
   * @param adminServerName the WebLogic admin server name that the app deploys to
   * @param adminServerPodName WebLogic admin pod prefix
   * @param managedServerPodNamePrefix WebLogic managed server pod prefix
   * @param replicaCount replica count of the cluster
   * @param adminInternalPort admin server's internal port
   * @param msInternalPort managed server's internal port
   */
  public static void deployAndAccessApplication(String domainNamespace,
                                                String domainUid,
                                                String clusterName,
                                                String adminServerName,
                                                String adminServerPodName,
                                                String managedServerPodNamePrefix,
                                                int replicaCount,
                                                String adminInternalPort,
                                                String msInternalPort) {
    final LoggingFacade logger = getLogger();

    Path archivePath = Paths.get(ITTESTS_DIR, "../operator/integration-tests/apps/testwebapp.war");
    logger.info("Deploying application {0} to domain {1} cluster target cluster-1 in namespace {2}",
        archivePath, domainUid, domainNamespace);
    logger.info("Deploying webapp {0} to admin server and cluster", archivePath);
    DeployUtil.deployUsingWlst(adminServerPodName,
                               adminInternalPort,
                               ADMIN_USERNAME_DEFAULT,
                               ADMIN_PASSWORD_DEFAULT,
                               clusterName + "," + adminServerName,
                               archivePath,
                               domainNamespace);

    // check if the application is accessible inside of a server pod using quick retry policy
    logger.info("Check and wait for the application to become ready");
    for (int i = 1; i <= replicaCount; i++) {
      checkAppIsRunning(withQuickRetryPolicy, domainNamespace, managedServerPodNamePrefix + i,
          msInternalPort, "testwebapp/index.jsp", managedServerPodNamePrefix + i);
    }
    checkAppIsRunning(withQuickRetryPolicy, domainNamespace, adminServerPodName,
        adminInternalPort, "testwebapp/index.jsp", adminServerPodName);
  }

  /**
   * Check application availability while the operator upgrade is happening and once the ugprade is complete
   * by accessing the application inside the managed server pods.
   * @param domainNamespace namespace where domain exists
   * @param operatorNamespace namespace where operator exists
   * @param appAvailability application's availability
   * @param adminPodName WebLogic admin server pod name
   * @param managedServerPodNamePrefix WebLogic managed server pod prefix
   * @param replicaCount replica count of the cluster
   * @param adminInternalPort admin server's internal port
   * @param msInternalPort managed server's internal port
   * @param appPath application path
   */
  public static void collectAppAvailability(String domainNamespace,
                                            String operatorNamespace,
                                            List<Integer> appAvailability,
                                            String adminPodName,
                                            String managedServerPodNamePrefix,
                                            int replicaCount,
                                            String adminInternalPort,
                                            String msInternalPort,
                                            String appPath) {
    final LoggingFacade logger = getLogger();

    // Access the pod periodically to check application's availability during
    // upgrade and after upgrade is complete.
    // appAccessedAfterUpgrade is used to access the app once after upgrade is complete
    boolean appAccessedAfterUpgrade = false;
    while (!appAccessedAfterUpgrade) {
      boolean isUpgradeComplete = checkHelmReleaseRevision(OPERATOR_RELEASE_NAME, operatorNamespace, "2");
      // upgrade is not complete or app is not accessed after upgrade
      if (!isUpgradeComplete || !appAccessedAfterUpgrade) {
        // Check application accessibility on admin server
        if (appAccessibleInPod(domainNamespace,
                               adminPodName,
                               adminInternalPort,
                               appPath,
                               adminPodName)) {
          appAvailability.add(1);
          logger.info("application accessible in admin pod " + adminPodName);
        } else {
          appAvailability.add(0);
          logger.info("application not accessible in admin pod " + adminPodName);
        }

        // Check application accessibility on managed servers
        for (int i = 1; i <= replicaCount; i++) {
          if (appAccessibleInPod(domainNamespace,
                       managedServerPodNamePrefix + i,
                                 msInternalPort,
                                 appPath,
                                managedServerPodNamePrefix + i)) {
            appAvailability.add(1);
            logger.info("application is accessible in pod " + managedServerPodNamePrefix + i);
          } else {
            appAvailability.add(0);
            logger.info("application is not accessible in pod " + managedServerPodNamePrefix + i);
          }
        }
      }
      if (isUpgradeComplete) {
        logger.info("Upgrade is complete and app is accessed after upgrade");
        appAccessedAfterUpgrade = true;
      }
    }
  }


  /**
   * Compile java class inside the pod.
   * @param podName name of the pod
   * @param namespace name of namespace
   * @param destLocation location of java class
   */
  public static void runJavacInsidePod(String podName, String namespace, String destLocation) {
    final LoggingFacade logger = getLogger();

    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer javacCmd = new StringBuffer("kubectl exec -n ");
    javacCmd.append(namespace);
    javacCmd.append(" -it ");
    javacCmd.append(podName);
    javacCmd.append(" -- /bin/bash -c \"");
    javacCmd.append("javac -cp ");
    javacCmd.append(jarLocation);
    javacCmd.append(" ");
    javacCmd.append(destLocation);
    javacCmd.append(" \"");
    logger.info("javac command {0}", javacCmd.toString());
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(javacCmd), true));
    logger.info("javac returned {0}", result.toString());
    logger.info("javac returned EXIT value {0}", result.exitValue());
    assertTrue(result.exitValue() == 0, "Client compilation fails");
  }

  /**
   * Run java client inside the pod using weblogic.jar.
   *
   * @param podName    name of the pod
   * @param namespace  name of the namespace
   * @param javaClientLocation location(path) of java class
   * @param javaClientClass java class name
   * @param args       arguments to the java command
   * @return true if the client ran successfully
   */
  public static Callable<Boolean> runClientInsidePod(String podName, String namespace, String javaClientLocation,
                                                     String javaClientClass, String... args) {
    final LoggingFacade logger = getLogger();

    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer javapCmd = new StringBuffer("kubectl exec -n ");
    javapCmd.append(namespace);
    javapCmd.append(" -it ");
    javapCmd.append(podName);
    javapCmd.append(" -- /bin/bash -c \"");
    javapCmd.append("java -cp ");
    javapCmd.append(jarLocation);
    javapCmd.append(":");
    javapCmd.append(javaClientLocation);
    javapCmd.append(" ");
    javapCmd.append(javaClientClass);
    javapCmd.append(" ");
    for (String arg:args) {
      javapCmd.append(arg).append(" ");
    }
    javapCmd.append(" \"");
    logger.info("java command to be run {0}", javapCmd.toString());

    return (() -> {
      ExecResult result = assertDoesNotThrow(() -> exec(javapCmd.toString(), true));
      logger.info("java returned {0}", result.toString());
      logger.info("java returned EXIT value {0}", result.exitValue());
      return ((result.exitValue() == 0));
    });
  }

  /**
   * Create an ingress in specified namespace and retry up to maxRetries times if fail.
   * @param maxRetries max number of retries
   * @param isTLS whether the ingress uses TLS
   * @param ingressName ingress name
   * @param namespace namespace in which the ingress will be created
   * @param annotations annotations of the ingress
   * @param ingressRules a list of ingress rules
   * @param tlsList list of ingress tls
   */
  public static void createIngressAndRetryIfFail(int maxRetries,
                                                 boolean isTLS,
                                                 String ingressName,
                                                 String namespace,
                                                 Map<String, String> annotations,
                                                 List<NetworkingV1beta1IngressRule> ingressRules,
                                                 List<NetworkingV1beta1IngressTLS> tlsList) {
    for (int i = 0; i < maxRetries; i++) {
      try {
        if (isTLS) {
          createIngress(ingressName, namespace, annotations, ingressRules, tlsList);
        } else {
          createIngress(ingressName, namespace, annotations, ingressRules, null);
        }
        break;
      } catch (ApiException apiEx) {
        try {
          Thread.sleep(5000);
        } catch (InterruptedException ignore) {
          //ignore
        }
      }
    }
  }

  /**
   * Adds proxy extra arguments for docker command.
   **/
  public static String getDockerExtraArgs() {
    StringBuffer extraArgs = new StringBuffer("");

    String httpsproxy = Optional.ofNullable(System.getenv("HTTPS_PROXY")).orElse(System.getenv("https_proxy"));
    String httpproxy = Optional.ofNullable(System.getenv("HTTP_PROXY")).orElse(System.getenv("http_proxy"));
    String noproxy = Optional.ofNullable(System.getenv("NO_PROXY")).orElse(System.getenv("no_proxy"));
    LoggingFacade logger = getLogger();
    logger.info(" httpsproxy : " + httpsproxy);
    String proxyHost = "";
    StringBuffer mvnArgs = new StringBuffer("");
    if (httpsproxy != null) {
      logger.info(" httpsproxy : " + httpsproxy);
      proxyHost = httpsproxy.substring(httpsproxy.lastIndexOf("www"), httpsproxy.lastIndexOf(":"));
      logger.info(" proxyHost: " + proxyHost);
      mvnArgs.append(String.format(" -Dhttps.proxyHost=%s -Dhttps.proxyPort=80 ",
          proxyHost));
      extraArgs.append(String.format(" --build-arg https_proxy=%s", httpsproxy));
    }
    if (httpproxy != null) {
      logger.info(" httpproxy : " + httpproxy);
      proxyHost = httpproxy.substring(httpproxy.lastIndexOf("www"), httpproxy.lastIndexOf(":"));
      logger.info(" proxyHost: " + proxyHost);
      mvnArgs.append(String.format(" -Dhttp.proxyHost=%s -Dhttp.proxyPort=80 ",
          proxyHost));
      extraArgs.append(String.format(" --build-arg http_proxy=%s", httpproxy));
    }
    if (noproxy != null) {
      logger.info(" noproxy : " + noproxy);
      extraArgs.append(String.format(" --build-arg no_proxy=%s",noproxy));
    }
    if (!mvnArgs.equals("")) {
      extraArgs.append(" --build-arg MAVEN_OPTS=\" " + mvnArgs.toString() + "\"");
    }
    return extraArgs.toString();
  }

  /**
   * Wait until a given event is logged by the operator.
   *
   * @param opNamespace namespace in which the operator is running
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid UID of the domain
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param type type of event, Normal of Warning
   * @param timestamp the timestamp after which to see events
   */
  public static void checkEvent(
      String opNamespace, String domainNamespace, String domainUid,
      String reason, String type, OffsetDateTime timestamp) {
    withStandardRetryPolicy
        .conditionEvaluationListener(condition ->
            getLogger().info("Waiting for domain event {0} to be logged in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                reason,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(checkDomainEvent(opNamespace, domainNamespace, domainUid, reason, type, timestamp));
  }

  /**
   * Delete a domain in the specified namespace.
   * @param domainNS the namespace in which the domain exists
   * @param domainUid domain uid
   */
  public static void deleteDomainResource(String domainNS, String domainUid) {
    //clean up domain resources in namespace and set namespace to label , managed by operator
    getLogger().info("deleting domain custom resource {0}", domainUid);
    assertTrue(deleteDomainCustomResource(domainUid, domainNS));

    // wait until domain was deleted
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> getLogger().info("Waiting for domain {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNS,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainDoesNotExist(domainUid, DOMAIN_VERSION, domainNS));
  }

  private static Callable<Boolean> podLogContainsString(String namespace, String podName, String expectedString) {
    return () -> {
      String podLog;
      try {
        podLog = getPodLog(podName, namespace);
        getLogger().info("pod log for pod {0} in namespace {1} : {2}", podName, namespace, podLog);
      } catch (ApiException apiEx) {
        getLogger().severe("got ApiException while getting pod log: ", apiEx);
        return false;
      }

      return podLog.contains(expectedString);
    };
  }

  /**
   * Wait and check the pod log contains the expected string.
   * @param namespace the namespace in which the pod exists
   * @param podName the pod to get the log
   * @param expectedString the expected string to check in the pod log
   */
  public static  void checkPodLogContainsString(String namespace, String podName, String expectedString) {

    getLogger().info("Wait for string {0} existing in pod {1} in namespace {2}", expectedString, podName, namespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> getLogger().info("Waiting for string {0} existing in pod {1} in namespace {2} "
                    + "(elapsed time {3}ms, remaining time {4}ms)",
                expectedString,
                podName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podLogContainsString(namespace, podName, expectedString),
            "podLogContainsString failed with IOException, ApiException or InterruptedException"));
  }

  /**
   * Check the domain event contains the expected error msg.
   *
   * @param opNamespace namespace in which the operator is running
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid UID of the domain
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param type type of event, Normal of Warning
   * @param timestamp the timestamp after which to see events
   * @param expectedMsg the expected message in the domain event message
   */
  public static void checkDomainEventContainsExpectedMsg(String opNamespace,
                                                         String domainNamespace,
                                                         String domainUid,
                                                         String reason,
                                                         String type,
                                                         OffsetDateTime timestamp,
                                                         String expectedMsg) {
    checkEvent(opNamespace, domainNamespace, domainUid, reason, type, timestamp);
    CoreV1Event event =
        getEvent(opNamespace, domainNamespace, domainUid, reason, type, timestamp);
    if (event != null && event.getMessage() != null) {
      assertTrue(event.getMessage().contains(expectedMsg),
          String.format("The event message does not contain the expected msg %s", expectedMsg));
    } else {
      fail("event is null or event message is null");
    }
  }
}

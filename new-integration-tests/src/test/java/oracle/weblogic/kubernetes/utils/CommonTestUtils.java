// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.actions.impl.GrafanaParams;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.PrometheusParams;
import oracle.weblogic.kubernetes.actions.impl.VoyagerParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;

import static java.nio.file.Files.readString;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.APPSCODE_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.APPSCODE_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.GEN_EXTERNAL_REST_IDENTITY_FILE;
import static oracle.weblogic.kubernetes.TestConstants.GOOGLE_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.STABLE_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_IMAGE_DOMAINHOME_BASE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.archiveApp;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createDockerConfigJson;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespacedJob;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.installGrafana;
import static oracle.weblogic.kubernetes.actions.TestActions.installNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.installPrometheus;
import static oracle.weblogic.kubernetes.actions.TestActions.installVoyager;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithWLDF;
import static oracle.weblogic.kubernetes.actions.TestActions.upgradeOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isGrafanaReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isNginxReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPrometheusReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isVoyagerReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.jobCompleted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndWaitTillReady;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The common utility class for tests.
 */
public class CommonTestUtils {

  private static ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static HelmParams installAndVerifyOperator(String opNamespace,
                                                    String... domainNamespace) {

    return installAndVerifyOperator(opNamespace, opNamespace + "-sa", false, 0, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static HelmParams installAndVerifyOperator(String opNamespace,
                                                    String opServiceAccount,
                                                    boolean withRestAPI,
                                                    int externalRestHttpsPort,
                                                    String... domainNamespace) {

    // Create a service account for the unique opNamespace
    logger.info("Creating service account");
    assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
        .metadata(new V1ObjectMeta()
            .namespace(opNamespace)
            .name(opServiceAccount))));
    logger.info("Created service account: {0}", opServiceAccount);

    // get operator image name
    String operatorImage = getOperatorImageName();
    assertFalse(operatorImage.isEmpty(), "operator image name can not be empty");
    logger.info("operator image name {0}", operatorImage);

    // Create Docker registry secret in the operator namespace to pull the image from repository
    logger.info("Creating Docker registry secret in namespace {0}", opNamespace);
    createDockerRegistrySecret(opNamespace);

    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", REPO_SECRET_NAME);

    // Helm install parameters
    HelmParams opHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // operator chart values to override
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .image(operatorImage)
        .imagePullSecrets(secretNameMap)
        .domainNamespaces(Arrays.asList(domainNamespace))
        .serviceAccount(opServiceAccount);

    if (withRestAPI) {
      // create externalRestIdentitySecret
      assertTrue(createExternalRestIdentitySecret(opNamespace, DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME),
          "failed to create external REST identity secret");
      opParams
          .externalRestEnabled(true)
          .externalRestHttpsPort(externalRestHttpsPort)
          .externalRestIdentitySecret(DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME);
    }

    // install operator
    logger.info("Installing operator in namespace {0}", opNamespace);
    assertTrue(installOperator(opParams),
        String.format("Failed to install operator in namespace %s", opNamespace));
    logger.info("Operator installed in namespace {0}", opNamespace);

    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking operator release {0} status in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);
    assertTrue(isHelmReleaseDeployed(OPERATOR_RELEASE_NAME, opNamespace),
        String.format("Operator release %s is not in deployed status in namespace %s",
            OPERATOR_RELEASE_NAME, opNamespace));
    logger.info("Operator release {0} status is deployed in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);

    // wait for the operator to be ready
    logger.info("Wait for the operator pod is ready in namespace {0}", opNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for operator to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                opNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> operatorIsReady(opNamespace),
            "operatorIsReady failed with ApiException"));

    return opHelmParams;
  }

  /**
   * Upgrade WebLogic operator to manage the given domain namespaces.
   *
   * @param opNamespace the operator namespace in which the operator will be upgraded
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return true if successful
   */
  public static boolean upgradeAndVerifyOperator(String opNamespace,
                                                    String... domainNamespace) {
    // Helm upgrade parameters
    HelmParams opHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // operator chart values
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .domainNamespaces(Arrays.asList(domainNamespace));

    // upgrade operator
    logger.info("Upgrading operator in namespace {0}", opNamespace);
    if (!upgradeOperator(opParams)) {
      logger.info("Failed to upgrade operator in namespace {0}", opNamespace);
      return false;
    }
    logger.info("Operator upgraded in namespace {0}", opNamespace);

    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking operator release {0} status in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);
    if (!isHelmReleaseDeployed(OPERATOR_RELEASE_NAME, opNamespace)) {
      logger.info("Operator release {0} is not in deployed status in namespace {1}",
          OPERATOR_RELEASE_NAME, opNamespace);
      return false;
    }
    logger.info("Operator release {0} status is deployed in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);

    return true;
  }

  /**
   * Install NGINX and wait up to five minutes until the NGINX pod is ready.
   *
   * @param nginxNamespace the namespace in which the NGINX will be installed
   * @param nodeportshttp the http nodeport of NGINX
   * @param nodeportshttps the https nodeport of NGINX
   * @return the NGINX Helm installation parameters
   */
  public static HelmParams installAndVerifyNginx(String nginxNamespace,
                                                 int nodeportshttp,
                                                 int nodeportshttps) {

    // Helm install parameters
    HelmParams nginxHelmParams = new HelmParams()
        .releaseName(NGINX_RELEASE_NAME)
        .namespace(nginxNamespace)
        .repoUrl(GOOGLE_REPO_URL)
        .repoName(STABLE_REPO_NAME)
        .chartName(NGINX_CHART_NAME);

    // NGINX chart values to override
    NginxParams nginxParams = new NginxParams()
        .helmParams(nginxHelmParams)
        .nodePortsHttp(nodeportshttp)
        .nodePortsHttps(nodeportshttps);

    // install NGINX
    assertThat(installNginx(nginxParams))
        .as("Test NGINX installation succeeds")
        .withFailMessage("NGINX installation is failed")
        .isTrue();

    // verify that NGINX is installed
    logger.info("Checking NGINX release {0} status in namespace {1}",
        NGINX_RELEASE_NAME, nginxNamespace);
    assertTrue(isHelmReleaseDeployed(NGINX_RELEASE_NAME, nginxNamespace),
        String.format("NGINX release %s is not in deployed status in namespace %s",
            NGINX_RELEASE_NAME, nginxNamespace));
    logger.info("NGINX release {0} status is deployed in namespace {1}",
        NGINX_RELEASE_NAME, nginxNamespace);

    // wait until the NGINX pod is ready.
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for NGINX to be ready in namespace {0} (elapsed time {1}ms, remaining time {2}ms)",
                nginxNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isNginxReady(nginxNamespace), "isNginxReady failed with ApiException"));

    return nginxHelmParams;
  }

  /**
   * Install Voyager and wait up to five minutes until the Voyager pod is ready.
   *
   * @param voyagerNamespace the namespace in which the Voyager will be installed
   * @param cloudProvider the name of bare metal Kubernetes cluster
   * @param enableValidatingWebhook whehter to enable validating webhook or not
   * @return the Voyager Helm installation parameters
   */
  public static HelmParams installAndVerifyVoyager(String voyagerNamespace,
                                                   String cloudProvider,
                                                   boolean enableValidatingWebhook) {

    final String voyagerPodNamePrefix = VOYAGER_CHART_NAME +  "-release-";

    // Helm install parameters
    HelmParams voyagerHelmParams = new HelmParams()
        .releaseName(VOYAGER_RELEASE_NAME)
        .namespace(voyagerNamespace)
        .repoUrl(APPSCODE_REPO_URL)
        .repoName(APPSCODE_REPO_NAME)
        .chartName(VOYAGER_CHART_NAME)
        .chartVersion(VOYAGER_CHART_VERSION);

    // Voyager chart values to override
    VoyagerParams voyagerParams = new VoyagerParams()
        .helmParams(voyagerHelmParams)
        .cloudProvider(cloudProvider)
        .enableValidatingWebhook(enableValidatingWebhook);

    // install Voyager
    assertThat(installVoyager(voyagerParams))
        .as("Test Voyager installation succeeds")
        .withFailMessage("Voyager installation is failed")
        .isTrue();

    // verify that Voyager is installed
    logger.info("Checking Voyager release {0} status in namespace {1}",
        VOYAGER_RELEASE_NAME, voyagerNamespace);
    assertTrue(isHelmReleaseDeployed(VOYAGER_RELEASE_NAME, voyagerNamespace),
        String.format("Voyager release %s is not in deployed status in namespace %s",
            VOYAGER_RELEASE_NAME, voyagerNamespace));
    logger.info("Voyager release {0} status is deployed in namespace {1}",
        VOYAGER_RELEASE_NAME, voyagerNamespace);

    // wait until the Voyager pod is ready.
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info(
              "Waiting for Voyager to be ready in namespace {0} (elapsed time {1}ms, remaining time {2}ms)",
              voyagerNamespace,
              condition.getElapsedTimeInMS(),
              condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isVoyagerReady(voyagerNamespace, voyagerPodNamePrefix),
            "isVoyagerReady failed with ApiException"));

    return voyagerHelmParams;
  }

  /**
   * Create a domain in the specified namespace and wait up to five minutes until the domain exists.
   *
   * @param domain the oracle.weblogic.domain.Domain object to create domain custom resource
   * @param domainNamespace namespace in which the domain will be created
   */
  public static void createDomainAndVerify(Domain domain, String domainNamespace) {

    // create the domain CR
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getSpec(), "domain spec is null");
    String domainUid = domain.getSpec().getDomainUid();

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain),
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
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));
  }

  /**
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @return list of ingress hosts
   */
  public static List<String> createIngressForDomainAndVerify(String domainUid,
                                                             String domainNamespace,
                                                             Map<String, Integer> clusterNameMSPortMap) {

    return createIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMSPortMap);
  }

  /**
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param nodeport node port of the ingress controller
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @return list of ingress hosts
   */
  public static List<String> createIngressForDomainAndVerify(String domainUid,
                                                             String domainNamespace,
                                                             int nodeport,
                                                             Map<String, Integer> clusterNameMSPortMap) {

    // create an ingress in domain namespace
    final String ingressNginxClass = "nginx";
    String ingressName = domainUid + "-" + ingressNginxClass;

    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("kubernetes.io/ingress.class", ingressNginxClass);

    List<String> ingressHostList =
        createIngress(ingressName, domainNamespace, domainUid, clusterNameMSPortMap, annotations);

    assertNotNull(ingressHostList,
        String.format("Ingress creation failed for domain %s in namespace %s", domainUid, domainNamespace));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as("Test ingress {0} was found in namespace {1}", ingressName, domainNamespace)
        .withFailMessage("Ingress {0} was not found in namespace {1}", ingressName, domainNamespace)
        .contains(ingressName);

    logger.info("ingress {0} for domain {1} was created in namespace {2}",
        ingressName, domainUid, domainNamespace);

    // check the ingress is ready to route the app to the server pod
    if (nodeport != 0) {
      for (String ingressHost : ingressHostList) {
        String curlCmd = "curl --silent --show-error --noproxy '*' -H 'host: " + ingressHost
            + "' http://" + K8S_NODEPORT_HOST + ":" + nodeport
            + "/weblogic/ready --write-out %{http_code} -o /dev/null";

        logger.info("Executing curl command {0}", curlCmd);
        assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));
      }
    }

    return ingressHostList;
  }

  /**
   * Create an ingress for the domain with domainUid in a given namespace and verify.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param ingressName name of ingress to be created in a given domain
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @return list of ingress hosts
   */
  public static List<String> installVoyagerIngressAndVerify(String domainUid,
                                                            String domainNamespace,
                                                            String ingressName,
                                                            Map<String, Integer> clusterNameMSPortMap) {

    final String voyagerIngressName = VOYAGER_CHART_NAME + "-" + ingressName;
    final String channelName = "tcp-80";
    final String ingressType = "NodePort";
    final String ingressAffinity = "cookie";
    final String ingressClass = "voyager";

    // set the annotations for Voyager
    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("ingress.appscode.com/type", ingressType);
    annotations.put("ingress.appscode.com/affinity", ingressAffinity);
    annotations.put("kubernetes.io/ingress.class", ingressClass);

    // create an ingress in domain namespace
    List<String> ingressHostList =
        createIngress(ingressName, domainNamespace, domainUid, clusterNameMSPortMap, annotations);

    // wait until the Voyager ingress pod is ready.
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for Voyager ingress to be ready in namespace {0} (elapsed time {1}ms, remaining time {2}ms)",
                domainUid,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isVoyagerReady(domainNamespace, voyagerIngressName),
            "isVoyagerReady failed with ApiException"));

    assertNotNull(ingressHostList,
        String.format("Ingress creation failed for domain %s in namespace %s", domainUid, domainNamespace));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as("Test ingress {0} was found in namespace {1}", ingressName, domainNamespace)
        .withFailMessage("Ingress {0} was not found in namespace {1}", ingressName, domainNamespace)
        .contains(ingressName);

    // get ingress service Nodeport
    int ingressServiceNodePort = assertDoesNotThrow(
        () -> getServiceNodePort(domainNamespace, voyagerIngressName, channelName),
            "Getting admin server node port failed");
    logger.info("Node port for {0} is: {1} :", voyagerIngressName, ingressServiceNodePort);

    // check the ingress is ready to route the app to the server pod
    if (ingressServiceNodePort != 0) {
      for (String ingressHost : ingressHostList) {
        String curlCmd = "curl --silent --show-error --noproxy '*' -H 'host: " + ingressHost
            + "' http://" + K8S_NODEPORT_HOST + ":" + ingressServiceNodePort
            + "/weblogic/ready --write-out %{http_code} -o /dev/null";

        logger.info("Executing curl command {0}", curlCmd);
        assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));
      }
    }

    logger.info("ingress {0} for domain {1} was created in namespace {2}",
        ingressName, domainUid, domainNamespace);

    return ingressHostList;
  }

  /**
   * Check pod exists in the specified namespace.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodExists(String podName, String domainUid, String domainNamespace) {
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
      DateTime lastCreationTime
  ) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be restarted in namespace {1} "
            + "(elapsed time {2}ms, remaining time {3}ms)",
            podName,
            domNamespace,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isPodRestarted(podName, domainUid, domNamespace, lastCreationTime),
            String.format(
                "pod %s has not been restarted in namespace %s", podName, domNamespace)));
  }

  /**
   * Check service exists in the specified namespace.
   *
   * @param serviceName service name to check
   * @param namespace the namespace in which to check for the service
   */
  public static void checkServiceExists(String serviceName, String namespace) {
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
   * Check pod does not exist in the specified namespace.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param namespace the namespace in which to check whether the pod exists
   */
  public static void checkPodDoesNotExist(String podName, String domainUid, String namespace) {
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
   * Create a Docker image for a model in image domain.
   *
   * @param miiImageNameBase the base mii image name used in local or to construct the image name in repository
   * @param wdtModelFile the WDT model file used to build the Docker image
   * @param appName the sample application name used to build sample app ear file in WDT model file
   * @return image name with tag
   */
  public static  String createMiiImageAndVerify(String miiImageNameBase,
                                                String wdtModelFile,
                                                String appName) {
    return createMiiImageAndVerify(miiImageNameBase, wdtModelFile, appName,
        WLS_BASE_IMAGE_NAME, WLS_BASE_IMAGE_TAG, WLS);
  }

  /**
   * Create a Docker image for a model in image domain.
   *
   * @param miiImageNameBase the base mii image name used in local or to construct the image name in repository
   * @param wdtModelFile the WDT model file used to build the Docker image
   * @param appName the sample application name used to build sample app ear file in WDT model file
   * @param baseImageName the WebLogic base image name to be used while creating mii image
   * @param baseImageTag the WebLogic base image tag to be used while creating mii image
   * @param domainType the type of the WebLogic domain, valid values are "WLS, "JRF", and "Restricted JRF"
   * @return image name with tag
   */
  public static  String createMiiImageAndVerify(String miiImageNameBase,
                                                String wdtModelFile,
                                                String appName,
                                                String baseImageName,
                                                String baseImageTag,
                                                String domainType) {
    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + wdtModelFile);
    final List<String> appSrcDirList = Collections.singletonList(appName);

    return createMiiImageAndVerify(
        miiImageNameBase, modelList, appSrcDirList, baseImageName, baseImageTag, domainType, true);
  }

  /**
   * Create a Docker image for a model in image domain using multiple WDT model files and application ear files.
   *
   * @param miiImageNameBase the base mii image name used in local or to construct the image name in repository
   * @param wdtModelList list of WDT model files used to build the Docker image
   * @param appSrcDirList list of the sample application source directories used to build sample app ear files
   * @return image name with tag
   */
  public static  String createMiiImageAndVerify(String miiImageNameBase,
                                                List<String> wdtModelList,
                                                List<String> appSrcDirList) {
    return createMiiImageAndVerify(
        miiImageNameBase, wdtModelList, appSrcDirList, WLS_BASE_IMAGE_NAME, WLS_BASE_IMAGE_TAG, WLS, true);

  }

  /**
   * Create a Docker image for a model in image domain using multiple WDT model files and application ear files.
   *
   * @param miiImageNameBase the base mii image name used in local or to construct the image name in repository
   * @param wdtModelList list of WDT model files used to build the Docker image
   * @param appSrcDirList list of the sample application source directories used to build sample app ear files
   * @param baseImageName the WebLogic base image name to be used while creating mii image
   * @param baseImageTag the WebLogic base image tag to be used while creating mii image
   * @param domainType the type of the WebLogic domain, valid values are "WLS, "JRF", and "Restricted JRF"
   * @param oneArchiveContainsMultiApps whether one archive contains multiple apps
   * @return image name with tag
   */
  public static String createMiiImageAndVerify(String miiImageNameBase,
                                                List<String> wdtModelList,
                                                List<String> appSrcDirList,
                                                String baseImageName,
                                                String baseImageTag,
                                                String domainType,
                                                boolean oneArchiveContainsMultiApps) {

    return createImageAndVerify(
        miiImageNameBase, wdtModelList, appSrcDirList, null, baseImageName,
        baseImageTag, domainType, true, null, oneArchiveContainsMultiApps);
  }

  /**
   * Create an image with modelfile, application archive and property file. If the property file
   * is needed to be updated with a property that has been created by the framework, it is copied
   * onto RESULT_ROOT and updated. Hence the altModelDir. Call this method to create a domain home in image.
   * @param imageNameBase - base image name used in local or to construct image name in repository
   * @param wdtModelFile - model file used to build the image
   * @param appName - application to be added to the image
   * @param modelPropFile - property file to be used with the model file above
   * @param altModelDir - directory where the property file is found if not in the default MODEL_DIR
   * @return image name with tag
   */
  public static String createImageAndVerify(String imageNameBase,
                                             String wdtModelFile,
                                             String appName,
                                             String modelPropFile,
                                             String altModelDir,
                                             String domainHome) {

    final List<String> wdtModelList = Collections.singletonList(MODEL_DIR + "/" + wdtModelFile);
    final List<String> appSrcDirList = Collections.singletonList(appName);
    final List<String> modelPropList = Collections.singletonList(altModelDir + "/" + modelPropFile);

    return createImageAndVerify(
        imageNameBase, wdtModelList, appSrcDirList, modelPropList, WLS_BASE_IMAGE_NAME,
        WLS_BASE_IMAGE_TAG, WLS, false, domainHome, false);
  }

  /**
   * Create an image from the wdt model, application archives and property file. Call this method
   * to create a domain home in image.
   * @param imageNameBase - base image name used in local or to construct image name in repository
   * @param wdtModelFile - model file used to build the image
   * @param appName - application to be added to the image
   * @param modelPropFile - property file to be used with the model file above
   * @return image name with tag
   */
  public static String createImageAndVerify(String imageNameBase,
                                            String wdtModelFile,
                                            String appName,
                                            String modelPropFile,
                                            String domainHome) {

    final List<String> wdtModelList = Collections.singletonList(MODEL_DIR + "/" + wdtModelFile);
    final List<String> appSrcDirList = Collections.singletonList(appName);
    final List<String> modelPropList = Collections.singletonList(MODEL_DIR + "/" + modelPropFile);

    return createImageAndVerify(
        imageNameBase, wdtModelList, appSrcDirList, modelPropList, WLS_BASE_IMAGE_NAME,
        WLS_BASE_IMAGE_TAG, WLS, false, domainHome, false);
  }

  /**
   * Create a Docker image for a model in image domain or domain home in image using multiple WDT model
   * files and application ear files.
   * @param imageNameBase - the base mii image name used in local or to construct the image name in repository
   * @param wdtModelList - list of WDT model files used to build the Docker image
   * @param appSrcDirList - list of the sample application source directories used to build sample app ear files
   * @param modelPropList - the WebLogic base image name to be used while creating mii image
   * @param baseImageName - the WebLogic base image name to be used while creating mii image
   * @param baseImageTag - the WebLogic base image tag to be used while creating mii image
   * @param domainType - the type of the WebLogic domain, valid values are "WLS, "JRF", and "Restricted JRF"
   * @param modelType - create a model image only or domain in image. set to true for MII
   * @return image name with tag
   */
  public static String createImageAndVerify(String imageNameBase,
                                               List<String> wdtModelList,
                                               List<String> appSrcDirList,
                                               List<String> modelPropList,
                                               String baseImageName,
                                               String baseImageTag,
                                               String domainType,
                                               boolean modelType,
                                               String domainHome,
                                               boolean oneArchiveContainsMultiApps) {

    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = baseImageTag + "-" + dateFormat.format(date) + "-" + System.currentTimeMillis();
    // Add repository name in image name for Jenkins runs
    final String imageName = REPO_NAME + imageNameBase;
    final String image = imageName + ":" + imageTag;

    List<String> archiveList = new ArrayList<>();
    if (appSrcDirList != null && appSrcDirList.size() != 0 && appSrcDirList.get(0) != null) {
      List<String> archiveAppsList = new ArrayList<>();
      List<String> buildAppDirList = new ArrayList<>(appSrcDirList);

      for (String appSrcDir : appSrcDirList) {
        if (appSrcDir.contains(".war") || appSrcDir.contains(".ear")) {
          //remove from build
          buildAppDirList.remove(appSrcDir);
          archiveAppsList.add(appSrcDir);
        }
      }

      if (archiveAppsList.size() != 0 && archiveAppsList.get(0) != null) {
        assertTrue(archiveApp(defaultAppParams()
            .srcDirList(archiveAppsList)));
        //archive provided ear or war file
        String appName = archiveAppsList.get(0).substring(archiveAppsList.get(0).lastIndexOf("/") + 1,
            appSrcDirList.get(0).lastIndexOf("."));

        // build the archive list
        String zipAppFile = String.format("%s/%s.zip", ARCHIVE_DIR, appName);
        archiveList.add(zipAppFile);

      }

      if (buildAppDirList.size() != 0 && buildAppDirList.get(0) != null) {
        // build an application archive using what is in resources/apps/APP_NAME
        String zipFile = "";
        if (oneArchiveContainsMultiApps) {
          assertTrue(buildAppArchive(defaultAppParams()
                  .srcDirList(buildAppDirList)),
              String.format("Failed to create app archive for %s", buildAppDirList.get(0)));
          zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, buildAppDirList.get(0));
          // build the archive list
          archiveList.add(zipFile);
        } else {
          for (String appName : buildAppDirList) {
            assertTrue(buildAppArchive(defaultAppParams()
                    .srcDirList(Collections.singletonList(appName))
                    .appName(appName)),
                String.format("Failed to create app archive for %s", appName));
            zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, appName);
            // build the archive list
            archiveList.add(zipFile);
          }
        }
      }
    }

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    // For k8s 1.16 support and as of May 6, 2020, we presently need a different JDK for these
    // tests and for image tool. This is expected to no longer be necessary once JDK 11.0.8 or
    // the next JDK 14 versions are released.
    String witJavaHome = System.getenv("WIT_JAVA_HOME");
    if (witJavaHome != null) {
      env.put("JAVA_HOME", witJavaHome);
    }

    // build an image using WebLogic Image Tool
    logger.info("Creating image {0} using model directory {1}", image, MODEL_DIR);
    boolean result = false;
    if (!modelType) {  //create a domain home in image image
      result = createImage(
          new WitParams()
              .baseImageName(baseImageName)
              .baseImageTag(baseImageTag)
              .domainType(domainType)
              .modelImageName(imageName)
              .modelImageTag(imageTag)
              .modelFiles(wdtModelList)
              .modelVariableFiles(modelPropList)
              .modelArchiveFiles(archiveList)
              .domainHome(WDT_IMAGE_DOMAINHOME_BASE_DIR + "/" + domainHome)
              .wdtModelOnly(modelType)
              .wdtOperation("CREATE")
              .wdtVersion(WDT_VERSION)
              .env(env)
              .redirect(true));
    } else {
      result = createImage(
          new WitParams()
              .baseImageName(baseImageName)
              .baseImageTag(baseImageTag)
              .domainType(domainType)
              .modelImageName(imageName)
              .modelImageTag(imageTag)
              .modelFiles(wdtModelList)
              .modelVariableFiles(modelPropList)
              .modelArchiveFiles(archiveList)
              .wdtModelOnly(modelType)
              .wdtVersion(WDT_VERSION)
              .env(env)
              .redirect(true));
    }

    assertTrue(result, String.format("Failed to create the image %s using WebLogic Image Tool", image));

    // Check image exists using docker images | grep image tag.
    assertTrue(doesImageExist(imageTag),
        String.format("Image %s does not exist", image));

    logger.info("Image {0} are created successfully", image);
    return image;
  }

  /**
   * Create secret for OCR registry credentials in the specified namespace.
   *
   * @param namespace namespace in which the secret will be created
   */
  public static void createOCRRepoSecret(String namespace) {

    logger.info("Creating image pull secret in namespace {0}", namespace);
    createDockerRegistrySecret(OCR_USERNAME, OCR_PASSWORD, OCR_EMAIL, OCR_REGISTRY, OCR_SECRET_NAME, namespace);
  }


  /**
   * Create a Docker registry secret in the specified namespace.
   *
   * @param namespace the namespace in which the secret will be created
   */
  public static void createDockerRegistrySecret(String namespace) {
    createDockerRegistrySecret(REPO_USERNAME, REPO_PASSWORD, REPO_EMAIL,
        REPO_REGISTRY, REPO_SECRET_NAME, namespace);
  }

  /**
   * Create docker registry secret with given parameters.
   * @param userName repository user name
   * @param password repository password
   * @param email repository email
   * @param registry registry name
   * @param secretName name of the secret to create
   * @param namespace namespace in which to create the secret
   */
  public static void createDockerRegistrySecret(String userName, String password,
      String email, String registry, String secretName, String namespace) {

    // Create registry secret in the namespace to pull the image from repository
    JsonObject dockerConfigJsonObject = createDockerConfigJson(
        userName, password, email, registry);
    String dockerConfigJson = dockerConfigJsonObject.toString();

    // Create the V1Secret configuration
    V1Secret repoSecret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))
        .type("kubernetes.io/dockerconfigjson")
        .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(repoSecret),
        String.format("createSecret failed for %s", secretName));
    assertTrue(secretCreated, String.format("createSecret failed while creating secret %s in namespace %s",
        secretName, namespace));
  }

  /**
   * Docker login and push the image to Docker registry.
   *
   * @param dockerImage the Docker image to push to registry
   */
  public static void dockerLoginAndPushImageToRegistry(String dockerImage) {
    // push image, if necessary
    if (!REPO_NAME.isEmpty() && dockerImage.contains(REPO_NAME)) {
      // docker login, if necessary
      if (!REPO_USERNAME.equals(REPO_DUMMY_VALUE)) {
        logger.info("docker login");
        assertTrue(dockerLogin(REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD), "docker login failed");
      }

      logger.info("docker push image {0} to {1}", dockerImage, REPO_NAME);
      assertTrue(dockerPush(dockerImage), String.format("docker push failed for image %s", dockerImage));
    }
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

    // get the original managed server pod creation timestamp before scale
    List<DateTime> listOfPodCreationTimestamp = new ArrayList<>();
    for (int i = 1; i <= replicasBeforeScale; i++) {
      String managedServerPodName = manageServerPodNamePrefix + i;
      DateTime originalCreationTimestamp =
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
          .as("Verify scaling cluster {0} of domain {1} in namespace {2} with REST API succeeds",
              clusterName, domainUid, domainNamespace)
          .withFailMessage("Scaling cluster {0} of domain {1} in namespace {2} with REST API failed",
              clusterName, domainUid, domainNamespace)
          .isTrue();
    } else if (withWLDF) {
      // scale the cluster using WLDF policy
      assertThat(assertDoesNotThrow(() -> scaleClusterWithWLDF(clusterName, domainUid, domainNamespace,
          domainHomeLocation, scalingAction, scalingSize, opNamespace, opServiceAccount, myWebAppName,
          curlCmdForWLDFApp)))
          .as("Verify scaling cluster {0} of domain {1} in namespace {2} with WLDF policy succeeds",
              clusterName, domainUid, domainNamespace)
          .withFailMessage("Scaling cluster {0} of domain {1} in namespace {2} with WLDF policy failed",
              clusterName, domainUid, domainNamespace)
          .isTrue();
    } else {
      assertThat(assertDoesNotThrow(() -> scaleCluster(domainUid, domainNamespace, clusterName, replicasAfterScale)))
          .as("Verify scaling cluster {0} of domain {1} in namespace {2} succeeds",
              clusterName, domainUid, domainNamespace)
          .withFailMessage("Scaling cluster {0} of domain {1} in namespace {2} failed",
              clusterName, domainUid, domainNamespace)
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
          expectedServerNames.add(clusterName + "-" + MANAGED_SERVER_NAME_BASE + i);
        }
      }

      if (curlCmd != null && expectedServerNames != null) {
        // check that NGINX can access the sample apps from new and original managed servers
        logger.info("Checking that NGINX can access the sample app from the new and original managed servers "
            + "in the domain after the cluster is scaled up.");
        assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNames, 50))
            .as("Verify NGINX can access the sample app from all managed servers in the domain")
            .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
            .isTrue();
      }
    } else {
      // scale down
      // wait and check the pods are deleted
      for (int i = replicasBeforeScale; i > replicasAfterScale; i--) {
        logger.info("Checking that managed server pod {0} was deleted from namespace {1}",
            manageServerPodNamePrefix + i, domainNamespace);
        checkPodDoesNotExist(manageServerPodNamePrefix + i, domainUid, domainNamespace);
        expectedServerNames.remove(clusterName + "-" + MANAGED_SERVER_NAME_BASE + i);
      }

      if (curlCmd != null && expectedServerNames != null) {
        // check that NGINX can access the app from the remaining managed servers in the domain
        logger.info("Checking that NGINX can access the sample app from the remaining managed servers in the domain "
            + "after the cluster is scaled down.");
        assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNames, 50))
            .as("Verify NGINX can access the sample app from the remaining managed server in the domain")
            .withFailMessage("NGINX can not access the sample app from the remaining managed server")
            .isTrue();
      }
    }
  }

  /**
   * Install Prometheus and wait up to five minutes until the prometheus pods are ready.
   *
   * @param promReleaseName the prometheus release name
   * @param promNamespace the prometheus namespace in which the operator will be installed
   * @param promValueFile the promeheus value.yaml file path
   * @param promVersion the version of the prometheus helm chart
   * @param promServerNodePort nodePort value for prometheus server
   * @param alertManagerNodePort nodePort value for alertmanager
   * @return the prometheus Helm installation parameters
   */
  public static HelmParams installAndVerifyPrometheus(String promReleaseName,
                                                      String promNamespace,
                                                      String promValueFile,
                                                      String promVersion,
                                                      int promServerNodePort,
                                                      int alertManagerNodePort) {

    // Helm install parameters
    HelmParams promHelmParams = new HelmParams()
        .releaseName(promReleaseName)
        .namespace(promNamespace)
        .chartDir("stable/prometheus")
        .chartValuesFile(promValueFile);

    if (promVersion != null) {
      promHelmParams.chartVersion(promVersion);
    }

    // prometheus chart values to override
    PrometheusParams prometheusParams = new PrometheusParams()
        .helmParams(promHelmParams)
        .nodePortServer(promServerNodePort)
        .nodePortAlertManager(alertManagerNodePort);

    // install prometheus
    logger.info("Installing prometheus in namespace {0}", promNamespace);
    assertTrue(installPrometheus(prometheusParams),
        String.format("Failed to install prometheus in namespace %s", promNamespace));
    logger.info("Prometheus installed in namespace {0}", promNamespace);

    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking prometheus release {0} status in namespace {1}",
        promReleaseName, promNamespace);
    assertTrue(isHelmReleaseDeployed(promReleaseName, promNamespace),
        String.format("Prometheus release %s is not in deployed status in namespace %s",
            promReleaseName, promNamespace));
    logger.info("Prometheus release {0} status is deployed in namespace {1}",
        promReleaseName, promNamespace);

    // wait for the promethues pods to be ready
    logger.info("Wait for the promethues pod is ready in namespace {0}", promNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for prometheus to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                promNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isPrometheusReady(promNamespace),
            "prometheusIsReady failed with ApiException"));

    return promHelmParams;
  }

  /**
   * Install Grafana and wait up to five minutes until the grafana pod is ready.
   *
   * @param grafanaReleaseName the grafana release name
   * @param grafanaNamespace the grafana namespace in which the operator will be installed
   * @param grafanaValueFile the grafana value.yaml file path
   * @param grafanaVersion the version of the grafana helm chart
   * @param grafanaNodePort nodePort value for grafana server
   * @return the grafana Helm installation parameters
   */
  public static HelmParams installAndVerifyGrafana(String grafanaReleaseName,
                                                      String grafanaNamespace,
                                                      String grafanaValueFile,
                                                      String grafanaVersion,
                                                      int grafanaNodePort) {

    // Helm install parameters
    HelmParams grafanaHelmParams = new HelmParams()
        .releaseName(grafanaReleaseName)
        .namespace(grafanaNamespace)
        .chartDir("stable/grafana")
        .chartValuesFile(grafanaValueFile);

    if (grafanaVersion != null) {
      grafanaHelmParams.chartVersion(grafanaVersion);
    }

    // grafana chart values to override
    GrafanaParams grafanaParams = new GrafanaParams()
        .helmParams(grafanaHelmParams)
        .nodePort(grafanaNodePort);
    //create grafana secret
    createSecretWithUsernamePassword("grafana-secret", grafanaNamespace, "admin", "12345678");
    // install grafana
    logger.info("Installing grafana in namespace {0}", grafanaNamespace);
    assertTrue(installGrafana(grafanaParams),
        String.format("Failed to install grafana in namespace %s", grafanaNamespace));
    logger.info("Grafana installed in namespace {0}", grafanaNamespace);

    // list Helm releases matching grafana release name in  namespace
    logger.info("Checking grafana release {0} status in namespace {1}",
        grafanaReleaseName, grafanaNamespace);
    assertTrue(isHelmReleaseDeployed(grafanaReleaseName, grafanaNamespace),
        String.format("Grafana release %s is not in deployed status in namespace %s",
            grafanaReleaseName, grafanaNamespace));
    logger.info("Grafana release {0} status is deployed in namespace {1}",
        grafanaReleaseName, grafanaNamespace);

    // wait for the grafana pod to be ready
    logger.info("Wait for the grafana pod is ready in namespace {0}", grafanaNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for prometheus to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                grafanaNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isGrafanaReady(grafanaNamespace),
            "grafanaIsReady failed with ApiException"));

    return grafanaHelmParams;
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
  public static void createJobAndWaitUntilComplete(V1Job jobBody, String namespace) {

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
  }

  /**
   * Get the PodCreationTimestamp of a pod in a namespace.
   *
   * @param namespace Kubernetes namespace that the domain is hosted
   * @param podName name of the pod
   * @return PodCreationTimestamp of the pod
   */
  public static DateTime getPodCreationTime(String namespace, String podName) {
    DateTime podCreationTime =
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
   * @param modelFiles list of the names of the WDT mode files in the ConfigMap
   */
  public static void createConfigMapAndVerify(
      String configMapName,
      String domainUid,
      String namespace,
      List<String> modelFiles) {

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
   * A utility method to sed files.
   *
   * @throws java.io.IOException when copying files from source location to staging area fails
   */
  public static void replaceStringInFile(String filePath, String oldValue, String newValue)
          throws IOException {
    Path src = Paths.get(filePath);
    logger.info("Copying {0}", src.toString());
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(src), charset);
    content = content.replaceAll(oldValue, newValue);
    logger.info("to {0}", src.toString());
    Files.write(src, content.getBytes(charset));
  }

  /**
   * Read the content of a model file as a String and add it to a map.
   */
  private static void addModelFile(Map<String, String> data, String modelFileName) {
    logger.info("Add model file {0}", modelFileName);
    String dsModelFile = String.format("%s/%s", MODEL_DIR, modelFileName);

    String cmData = assertDoesNotThrow(() -> Files.readString(Paths.get(dsModelFile)),
        String.format("Failed to read model file %s", dsModelFile));
    assertNotNull(cmData,
        String.format("Failed to read model file %s", dsModelFile));

    data.put(modelFileName, cmData);
  }

  /**
   * Create an external REST Identity secret in the specified namespace.
   *
   * @param namespace the namespace in which the secret to be created
   * @param secretName name of the secret to be created
   * @return true if the command to create secret succeeds, false otherwise
   */
  private static boolean createExternalRestIdentitySecret(String namespace, String secretName) {

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
}

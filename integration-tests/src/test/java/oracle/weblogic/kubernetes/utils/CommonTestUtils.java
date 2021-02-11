// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonObject;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
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
import io.kubernetes.client.openapi.models.V1SecretList;
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
import oracle.weblogic.kubernetes.actions.impl.ApacheParams;
import oracle.weblogic.kubernetes.actions.impl.Exec;
import oracle.weblogic.kubernetes.actions.impl.GrafanaParams;
import oracle.weblogic.kubernetes.actions.impl.LoggingExporterParams;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.PrometheusParams;
import oracle.weblogic.kubernetes.actions.impl.TraefikParams;
import oracle.weblogic.kubernetes.actions.impl.VoyagerParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readString;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.APACHE_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.APACHE_SAMPLE_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.APPSCODE_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.APPSCODE_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTPS_PORT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_NAME;
import static oracle.weblogic.kubernetes.TestConstants.ELKSTACK_NAMESPACE;
import static oracle.weblogic.kubernetes.TestConstants.FSS_DIR;
import static oracle.weblogic.kubernetes.TestConstants.GEN_EXTERNAL_REST_IDENTITY_FILE;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.JAVA_LOGGING_LEVEL_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_NAME;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_PORT;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.LOGSTASH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.NFS_SERVER;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_IMAGE_DOMAINHOME_BASE_DIR;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;
import static oracle.weblogic.kubernetes.actions.TestActions.archiveApp;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.buildCoherenceArchive;
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
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.getPersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.installApache;
import static oracle.weblogic.kubernetes.actions.TestActions.installElasticsearch;
import static oracle.weblogic.kubernetes.actions.TestActions.installGrafana;
import static oracle.weblogic.kubernetes.actions.TestActions.installKibana;
import static oracle.weblogic.kubernetes.actions.TestActions.installNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.installPrometheus;
import static oracle.weblogic.kubernetes.actions.TestActions.installTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.installVoyager;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithWLDF;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallElasticsearch;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallGrafana;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallKibana;
import static oracle.weblogic.kubernetes.actions.TestActions.upgradeOperator;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listSecrets;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.checkHelmReleaseRevision;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.credentialsNotValid;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.credentialsValid;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isApacheReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isElkStackPodReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isGrafanaReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isNginxReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPrometheusReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isTraefikReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isVoyagerReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.jobCompleted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorRestServiceRunning;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podInitializing;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.secretExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
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

  private static ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  private static ConditionFactory withQuickRetryPolicy = with().pollDelay(0, SECONDS)
      .and().with().pollInterval(3, SECONDS)
      .atMost(120, SECONDS).await();

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static HelmParams installAndVerifyOperator(String opNamespace,
                                                    String... domainNamespace) {
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(opNamespace, opNamespace + "-sa", false,
        0, opHelmParams, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param domainPresenceFailureRetryMaxCount the number of introspector job retries for a Domain
   * @param domainPresenceFailureRetrySeconds the interval in seconds between these retries
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static HelmParams installAndVerifyOperator(String opNamespace,
                                                    int domainPresenceFailureRetryMaxCount,
                                                    int domainPresenceFailureRetrySeconds,
                                                    String... domainNamespace) {
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(opNamespace, opNamespace + "-sa", false, 0, opHelmParams, false, null, null,
        false, domainPresenceFailureRetryMaxCount, domainPresenceFailureRetrySeconds, domainNamespace);

  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opHelmParams the Helm parameters to install operator
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static HelmParams installAndVerifyOperator(String opNamespace, HelmParams opHelmParams,
                                                    String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opNamespace + "-sa", false,
        0, opHelmParams, domainNamespace);
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
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withRestAPI, externalRestHttpsPort, opHelmParams, domainNamespace);

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
                                                    boolean elkIntegrationEnabled,
                                                    String... domainNamespace) {
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withRestAPI, externalRestHttpsPort, opHelmParams, elkIntegrationEnabled, domainNamespace);

  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static HelmParams installAndVerifyOperator(String opNamespace,
                                                    String opServiceAccount,
                                                    boolean withRestAPI,
                                                    int externalRestHttpsPort,
                                                    HelmParams opHelmParams,
                                                    String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withRestAPI, externalRestHttpsPort, opHelmParams, false, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static HelmParams installAndVerifyOperator(String opNamespace,
                                                    String opServiceAccount,
                                                    boolean withRestAPI,
                                                    int externalRestHttpsPort,
                                                    HelmParams opHelmParams,
                                                    boolean elkIntegrationEnabled,
                                                    String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withRestAPI, externalRestHttpsPort, opHelmParams, elkIntegrationEnabled,
        null, null, false, -1, -1, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param domainNamespaceSelectionStrategy value to tell the operator
   *                                         how to select the set of namespaces that it will manage
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static HelmParams installAndVerifyOperator(String opNamespace,
                                                    String opServiceAccount,
                                                    boolean withRestAPI,
                                                    int externalRestHttpsPort,
                                                    HelmParams opHelmParams,
                                                    String domainNamespaceSelectionStrategy,
                                                    boolean elkIntegrationEnabled,
                                                    String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withRestAPI, externalRestHttpsPort, opHelmParams, elkIntegrationEnabled,
        domainNamespaceSelectionStrategy, null, false, -1, -1, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param domainNamespaceSelectionStrategy SelectLabel, RegExp or List, value to tell the operator
   *                                  how to select the set of namespaces that it will manage
   * @param domainNamespaceSelector the label or expression value to manage namespaces
   * @param enableClusterRoleBinding operator cluster role binding
   * @param domainPresenceFailureRetryMaxCount the number of introspector job retries for a Domain
   * @param domainPresenceFailureRetrySeconds the interval in seconds between these retries
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static HelmParams installAndVerifyOperator(String opNamespace,
                                                    String opServiceAccount,
                                                    boolean withRestAPI,
                                                    int externalRestHttpsPort,
                                                    HelmParams opHelmParams,
                                                    boolean elkIntegrationEnabled,
                                                    String domainNamespaceSelectionStrategy,
                                                    String domainNamespaceSelector,
                                                    boolean enableClusterRoleBinding,
                                                    int domainPresenceFailureRetryMaxCount,
                                                    int domainPresenceFailureRetrySeconds,
                                                    String... domainNamespace) {
    LoggingFacade logger = getLogger();

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
    // this secret is used only for non-kind cluster
    logger.info("Creating Docker registry secret in namespace {0}", opNamespace);
    createOcirRepoSecret(opNamespace);

    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", OCIR_SECRET_NAME);

    // operator chart values to override
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .imagePullSecrets(secretNameMap)
        .domainNamespaces(Arrays.asList(domainNamespace))
        .serviceAccount(opServiceAccount);

    if (domainNamespaceSelectionStrategy != null) {
      opParams.domainNamespaceSelectionStrategy(domainNamespaceSelectionStrategy);
    }

    // use default image in chart when repoUrl is set, otherwise use latest/current branch operator image
    if (opHelmParams.getRepoUrl() == null) {
      opParams.image(operatorImage);
    }

    // enable ELK Stack
    if (elkIntegrationEnabled) {
      opParams
          .elkIntegrationEnabled(elkIntegrationEnabled);
      opParams
          .elasticSearchHost(ELASTICSEARCH_HOST);
      opParams
          .elasticSearchPort(ELASTICSEARCH_HTTP_PORT);
      opParams
          .javaLoggingLevel(JAVA_LOGGING_LEVEL_VALUE);
      opParams
          .logStashImage(LOGSTASH_IMAGE);
    }

    if (withRestAPI) {
      // create externalRestIdentitySecret
      assertTrue(createExternalRestIdentitySecret(opNamespace, DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME),
          "failed to create external REST identity secret");
      opParams
          .externalRestEnabled(true)
          .externalRestHttpsPort(externalRestHttpsPort)
          .externalRestIdentitySecret(DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME);
    }
    // operator chart values to override
    if (enableClusterRoleBinding) {
      opParams.enableClusterRoleBinding(enableClusterRoleBinding);
    }
    if (domainNamespaceSelectionStrategy != null) {
      opParams.domainNamespaceSelectionStrategy(domainNamespaceSelectionStrategy);
      if (domainNamespaceSelectionStrategy.equalsIgnoreCase("LabelSelector")) {
        opParams.domainNamespaceLabelSelector(domainNamespaceSelector);
      } else if (domainNamespaceSelectionStrategy.equalsIgnoreCase("RegExp")) {
        opParams.domainNamespaceRegExp(domainNamespaceSelector);
      }
    }

    // domainPresenceFailureRetryMaxCount and domainPresenceFailureRetrySeconds
    if (domainPresenceFailureRetryMaxCount >= 0) {
      opParams.domainPresenceFailureRetryMaxCount(domainPresenceFailureRetryMaxCount);
    }
    if (domainPresenceFailureRetrySeconds > 0) {
      opParams.domainPresenceFailureRetrySeconds(domainPresenceFailureRetrySeconds);
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

    if (withRestAPI) {
      logger.info("Wait for the operator external service in namespace {0}", opNamespace);
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for operator external service in namespace {0} "
                      + "(elapsed time {1}ms, remaining time {2}ms)",
                  opNamespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> operatorRestServiceRunning(opNamespace),
              "operator external service is not running"));
    }
    return opHelmParams;
  }

  /**
   * Install WebLogic operator and wait up to two minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opReleaseName the operator release name
   * @param domainNamespaceSelectionStrategy SelectLabel, RegExp or List
   * @param domainNamespaceSelector the label or expression value to manage namespaces
   * @param enableClusterRoleBinding operator cluster role binding
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   *                        (only in case of List selector)
   * @return the operator Helm installation parameters
   */
  public static HelmParams installAndVerifyOperator(String opReleaseName, String opNamespace,
                                                     String domainNamespaceSelectionStrategy,
                                                     String domainNamespaceSelector,
                                                     boolean enableClusterRoleBinding,
                                                     String... domainNamespace) {

    HelmParams opHelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(opNamespace, opReleaseName + "-sa",
        true, 0, opHelmParams, false,
        domainNamespaceSelectionStrategy, domainNamespaceSelector, enableClusterRoleBinding,
        -1, -1, domainNamespace);
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

    return upgradeAndVerifyOperator(opNamespace, opParams);
  }

  /**
   * Upgrade WebLogic operator with the helm values provided.
   *
   * @param opNamespace the operator namespace in which the operator will be upgraded
   * @param opParams operator parameters to use in the upgrade
   * @return true if successful
   */
  public static boolean upgradeAndVerifyOperator(String opNamespace, OperatorParams opParams) {

    LoggingFacade logger = getLogger();

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
    return installAndVerifyNginx(nginxNamespace, nodeportshttp, nodeportshttps, NGINX_CHART_VERSION);
  }

  /**
   * Install NGINX and wait up to five minutes until the NGINX pod is ready.
   *
   * @param nginxNamespace the namespace in which the NGINX will be installed
   * @param nodeportshttp the http nodeport of NGINX
   * @param nodeportshttps the https nodeport of NGINX
   * @param chartVersion the chart version of NGINX
   * @return the NGINX Helm installation parameters
   */
  public static HelmParams installAndVerifyNginx(String nginxNamespace,
                                                 int nodeportshttp,
                                                 int nodeportshttps,
                                                 String chartVersion) {
    LoggingFacade logger = getLogger();
    // Helm install parameters
    HelmParams nginxHelmParams = new HelmParams()
        .releaseName(NGINX_RELEASE_NAME + "-" + nginxNamespace.substring(3))
        .namespace(nginxNamespace)
        .repoUrl(NGINX_REPO_URL)
        .repoName(NGINX_REPO_NAME)
        .chartName(NGINX_CHART_NAME);

    if (chartVersion != null) {
      nginxHelmParams.chartVersion(chartVersion);
    }

    // NGINX chart values to override
    NginxParams nginxParams = new NginxParams()
        .helmParams(nginxHelmParams);

    if (nodeportshttp != 0 && nodeportshttps != 0) {
      nginxParams
          .nodePortsHttp(nodeportshttp)
          .nodePortsHttps(nodeportshttps);
    }

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
    LoggingFacade logger = getLogger();
    final String voyagerPodNamePrefix = VOYAGER_CHART_NAME +  "-release";

    // Helm install parameters
    HelmParams voyagerHelmParams = new HelmParams()
        .releaseName(VOYAGER_RELEASE_NAME + "-" + voyagerNamespace.substring(3))
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
   * Install Apache and wait up to five minutes until the Apache pod is ready.
   *
   * @param apacheNamespace the namespace in which the Apache will be installed
   * @param image the image name of Apache webtier
   * @param httpNodePort the http nodeport of Apache
   * @param httpsNodePort the https nodeport of Apache
   * @param domainUid the uid of the domain to which Apache will route the services
   * @return the Apache Helm installation parameters
   */
  public static HelmParams installAndVerifyApache(String apacheNamespace,
                                                  String image,
                                                  int httpNodePort,
                                                  int httpsNodePort,
                                                  String domainUid) throws IOException {
    return installAndVerifyApache(apacheNamespace, image, httpNodePort, httpsNodePort, domainUid,
        null, null, 0, null);
  }

  /**
   * Install Apache and wait up to five minutes until the Apache pod is ready.
   *
   * @param apacheNamespace the namespace in which the Apache will be installed
   * @param image the image name of Apache webtier
   * @param httpNodePort the http nodeport of Apache
   * @param httpsNodePort the https nodeport of Apache
   * @param domainUid the uid of the domain to which Apache will route the services
   * @param pvcName name of the Persistent Volume Claim which contains your own custom_mod_wl_apache.conf file
   * @param virtualHostName the VirtualHostName of the Apache HTTP server which is used to enable custom SSL config
   * @param adminServerPort admin server port
   * @param clusterNamePortMap the map with clusterName as key and cluster port number as value
   * @return the Apache Helm installation parameters
   */
  public static HelmParams installAndVerifyApache(String apacheNamespace,
                                                  String image,
                                                  int httpNodePort,
                                                  int httpsNodePort,
                                                  String domainUid,
                                                  String pvcName,
                                                  String virtualHostName,
                                                  int adminServerPort,
                                                  LinkedHashMap<String, String> clusterNamePortMap)
      throws IOException {

    LoggingFacade logger = getLogger();

    // Create Docker registry secret in the apache namespace to pull the Apache webtier image from repository
    // this secret is used only for non-kind cluster
    if (!secretExists(OCIR_SECRET_NAME, apacheNamespace)) {
      logger.info("Creating Docker registry secret in namespace {0}", apacheNamespace);
      createOcirRepoSecret(apacheNamespace);
    }

    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", OCIR_SECRET_NAME);

    // Helm install parameters
    HelmParams apacheHelmParams = new HelmParams()
        .releaseName(APACHE_RELEASE_NAME + "-" + apacheNamespace.substring(3))
        .namespace(apacheNamespace)
        .chartDir(APACHE_SAMPLE_CHART_DIR);

    // Apache chart values to override
    ApacheParams apacheParams = new ApacheParams()
        .helmParams(apacheHelmParams)
        .imagePullSecrets(secretNameMap)
        .image(image)
        .imagePullPolicy("Always")
        .domainUID(domainUid);

    if (httpNodePort >= 0 && httpsNodePort >= 0) {
      apacheParams
          .httpNodePort(httpNodePort)
          .httpsNodePort(httpsNodePort);
    }

    if (pvcName != null && clusterNamePortMap != null) {
      // create a custom Apache plugin configuration file named custom_mod_wl_apache.conf
      // and put it under the directory specified in pv hostPath
      // this file provides a custom Apache plugin configuration to fine tune the behavior of Apache
      V1PersistentVolumeClaim v1pvc = getPersistentVolumeClaim(apacheNamespace, pvcName);
      assertNotNull(v1pvc);
      assertNotNull(v1pvc.getSpec());
      String pvName = v1pvc.getSpec().getVolumeName();
      logger.info("Got PV {0} from PVC {1} in namespace {2}", pvName, pvcName, apacheNamespace);

      V1PersistentVolume v1pv = getPersistentVolume(pvName);
      assertNotNull(v1pv);
      assertNotNull(v1pv.getSpec());
      assertNotNull(v1pv.getSpec().getHostPath());
      String volumePath = v1pv.getSpec().getHostPath().getPath();
      logger.info("hostPath of the PV {0} is {1}", pvName, volumePath);

      Path customConf = Paths.get(volumePath, "custom_mod_wl_apache.conf");
      ArrayList<String> lines = new ArrayList<>();
      lines.add("<IfModule mod_weblogic.c>");
      lines.add("WebLogicHost " + domainUid + "-admin-server");
      lines.add("WebLogicPort " + adminServerPort);
      lines.add("</IfModule>");

      // Directive for weblogic admin Console deployed on Weblogic Admin Server
      lines.add("<Location /console>");
      lines.add("SetHandler weblogic-handler");
      lines.add("WebLogicHost " + domainUid + "-admin-server");
      lines.add("WebLogicPort " + adminServerPort);
      lines.add("</Location>");

      // Directive for all application deployed on weblogic cluster with a prepath defined by LOCATION variable
      // For example, if the LOCAITON is set to '/weblogic1', all applications deployed on the cluster can be accessed
      // via http://myhost:myport/weblogic1/application_end_url
      // where 'myhost' is the IP of the machine that runs the Apache web tier, and
      //       'myport' is the port that the Apache web tier is publicly exposed to.
      // Note that LOCATION cannot be set to '/' unless this is the only Location module configured.
      // create a LOCATION variable for each domain cluster
      int i = 1;
      for (String clusterName : clusterNamePortMap.keySet()) {
        lines.add("<Location /weblogic" + i + ">");
        lines.add("WLSRequest On");
        lines.add("WebLogicCluster " + clusterName + ":" + clusterNamePortMap.get(clusterName));
        lines.add("PathTrim /weblogic" + i);
        lines.add("</Location>");
        i++;
      }

      try {
        Files.write(customConf, lines);
      } catch (IOException ioex) {
        logger.info("Got IOException while write to a file");
        throw ioex;
      }

      apacheParams.pvcName(pvcName);
    }

    if (virtualHostName != null) {
      // create the certificate and private key
      String certFile = RESULTS_ROOT + "/apache-sample.crt";
      String keyFile = RESULTS_ROOT + "/apache-sample.key";

      Map<String, String> envs = new HashMap<>();
      envs.put("VIRTUAL_HOST_NAME", virtualHostName);
      envs.put("SSL_CERT_FILE", certFile);
      envs.put("SSL_CERT_KEY_FILE", keyFile);

      String command = "sh ../kubernetes/samples/charts/apache-samples/custom-sample/certgen.sh";
      CommandParams params = Command
          .defaultCommandParams()
          .command(command)
          .env(envs)
          .saveResults(true)
          .redirect(true);

      Command.withParams(params).execute();

      String customCert = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(certFile)));
      String customKey = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(keyFile)));

      // set the Apache helm install parameters
      apacheParams.virtualHostName(virtualHostName);
      apacheParams.customCert(customCert);
      apacheParams.customKey(customKey);
    }

    // install Apache
    assertThat(installApache(apacheParams))
        .as("Test Apache installation succeeds")
        .withFailMessage("Apache installation is failed")
        .isTrue();

    // verify that Apache is installed
    logger.info("Checking Apache release {0} status in namespace {1}",
        APACHE_RELEASE_NAME + "-" + apacheNamespace.substring(3), apacheNamespace);
    assertTrue(isHelmReleaseDeployed(APACHE_RELEASE_NAME + "-" + apacheNamespace.substring(3), apacheNamespace),
        String.format("Apache release %s is not in deployed status in namespace %s",
            APACHE_RELEASE_NAME + "-" + apacheNamespace.substring(3), apacheNamespace));
    logger.info("Apache release {0} status is deployed in namespace {1}",
        APACHE_RELEASE_NAME + "-" + apacheNamespace.substring(3), apacheNamespace);

    // wait until the Apache pod is ready.
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for Apache to be ready in namespace {0} (elapsed time {1}ms, remaining time {2}ms)",
                apacheNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isApacheReady(apacheNamespace), "isApacheReady failed with ApiException"));

    return apacheHelmParams;
  }

  /**
   * Uninstall Elasticsearch.
   *
   * @param params logging exporter parameters to uninstall Elasticsearch
   *
   * @return true if the command to uninstall Elasticsearch succeeds, false otherwise
   */
  public static boolean uninstallAndVerifyElasticsearch(LoggingExporterParams params) {
    // uninstall Elasticsearch
    assertThat(uninstallElasticsearch(params))
        .as("Elasticsearch uninstallation succeeds")
        .withFailMessage("Elasticsearch uninstallation is failed")
        .isTrue();

    return true;
  }

  /**
   * Uninstall Kibana.
   *
   * @param params logging exporter parameters to uninstall Kibana
   *
   * @return true if the command to uninstall Kibana succeeds, false otherwise
   */
  public static boolean uninstallAndVerifyKibana(LoggingExporterParams params) {
    // uninstall Kibana
    assertThat(uninstallKibana(params))
        .as("Elasticsearch uninstallation succeeds")
        .withFailMessage("Elasticsearch uninstallation is failed")
        .isTrue();

    return true;
  }

  /**
   * Install Elasticsearch and wait up to five minutes until Elasticsearch pod is ready.
   *
   * @return Elasticsearch installation parameters
   */
  public static LoggingExporterParams installAndVerifyElasticsearch() {
    LoggingFacade logger = getLogger();
    final String elasticsearchPodNamePrefix = ELASTICSEARCH_NAME;

    // parameters to install Elasticsearch
    LoggingExporterParams elasticsearchParams = new LoggingExporterParams()
        .elasticsearchName(ELASTICSEARCH_NAME)
        .elasticsearchImage(ELASTICSEARCH_IMAGE)
        .elasticsearchHttpPort(ELASTICSEARCH_HTTP_PORT)
        .elasticsearchHttpsPort(ELASTICSEARCH_HTTPS_PORT)
        .loggingExporterNamespace(ELKSTACK_NAMESPACE);

    // install Elasticsearch
    assertThat(installElasticsearch(elasticsearchParams))
        .as("Elasticsearch installation succeeds")
        .withFailMessage("Elasticsearch installation is failed")
        .isTrue();

    // wait until the Elasticsearch pod is ready.
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for Elasticsearch to be ready in namespace {0} (elapsed time {1}ms, remaining time {2}ms)",
                ELKSTACK_NAMESPACE,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isElkStackPodReady(ELKSTACK_NAMESPACE, elasticsearchPodNamePrefix),
            "isElkStackPodReady failed with ApiException"));

    return elasticsearchParams;
  }

  /**
   * Install Kibana and wait up to five minutes until Kibana pod is ready.
   *
   * @return Kibana installation parameters
   */
  public static LoggingExporterParams installAndVerifyKibana() {
    LoggingFacade logger = getLogger();
    final String kibanaPodNamePrefix = ELASTICSEARCH_NAME;

    // parameters to install Kibana
    LoggingExporterParams kibanaParams = new LoggingExporterParams()
        .kibanaName(KIBANA_NAME)
        .kibanaImage(KIBANA_IMAGE)
        .kibanaType(KIBANA_TYPE)
        .loggingExporterNamespace(ELKSTACK_NAMESPACE)
        .kibanaContainerPort(KIBANA_PORT);

    // install Kibana
    assertThat(installKibana(kibanaParams))
        .as("Kibana installation succeeds")
        .withFailMessage("Kibana installation is failed")
        .isTrue();

    // wait until the Kibana pod is ready.
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for Kibana to be ready in namespace {0} (elapsed time {1}ms, remaining time {2}ms)",
                ELKSTACK_NAMESPACE,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isElkStackPodReady(ELKSTACK_NAMESPACE, kibanaPodNamePrefix),
            "isElkStackPodReady failed with ApiException"));

    return kibanaParams;
  }

  /**
   * Install WebLogic Logging Exporter.
   *
   * @param filter the value of weblogicLoggingExporterFilters to be added to WebLogic Logging Exporter YAML file
   * @param wlsLoggingExporterYamlFileLoc the directory where WebLogic Logging Exporter YAML file stores
   * @return true if WebLogic Logging Exporter is successfully installed, false otherwise.
   */
  public static boolean installAndVerifyWlsLoggingExporter(String filter,
                                                           String wlsLoggingExporterYamlFileLoc) {
    // Install WebLogic Logging Exporter
    assertThat(TestActions.installWlsLoggingExporter(filter,
        wlsLoggingExporterYamlFileLoc))
        .as("WebLogic Logging Exporter installation succeeds")
        .withFailMessage("WebLogic Logging Exporter installation failed")
        .isTrue();

    return true;
  }

  /**
   * Verify that the logging exporter is ready to use in Operator pod or WebLogic server pod.
   *
   * @param namespace namespace of Operator pod (for ELK Stack) or
   *                  WebLogic server pod (for WebLogic Logging Exporter)
   * @param labelSelector string containing the labels the Operator or WebLogic server is decorated with
   * @param index key word used to search the index status of the logging exporter
   * @return a map containing key and value pair of logging exporter index
   */
  public static Map<String, String> verifyLoggingExporterReady(String namespace,
                                                               String labelSelector,
                                                               String index) {
    return TestActions.verifyLoggingExporterReady(namespace, labelSelector, index);
  }

  /** Install Traefik and wait for up to five minutes for the Traefik pod to be ready.
   *
   * @param traefikNamespace the namespace in which the Traefik ingress controller is installed
   * @param nodeportshttp the web nodeport of Traefik
   * @param nodeportshttps the websecure nodeport of Traefik
   * @return the Traefik Helm installation parameters
   */
  public static HelmParams installAndVerifyTraefik(String traefikNamespace,
      int nodeportshttp,
      int nodeportshttps) {
    LoggingFacade logger = getLogger();
    // Helm install parameters
    HelmParams traefikHelmParams = new HelmParams()
        .releaseName(TRAEFIK_RELEASE_NAME + "-" + traefikNamespace.substring(3))
        .namespace(traefikNamespace)
        .repoUrl(TRAEFIK_REPO_URL)
        .repoName(TRAEFIK_REPO_NAME)
        .chartName(TRAEFIK_CHART_NAME);

    // Traefik chart values to override
    TraefikParams traefikParams = new TraefikParams()
        .helmParams(traefikHelmParams);
    traefikParams
        .nodePortsHttp(nodeportshttp)
        .nodePortsHttps(nodeportshttps);

    // install Traefik
    assertThat(installTraefik(traefikParams))
        .as("Test Traefik installation succeeds")
        .withFailMessage("Traefik installation is failed")
        .isTrue();

    // verify that Traefik is installed
    logger.info("Checking Traefik release {0} status in namespace {1}",
        TRAEFIK_RELEASE_NAME, traefikNamespace);
    assertTrue(isHelmReleaseDeployed(TRAEFIK_RELEASE_NAME, traefikNamespace),
        String.format("Traefik release %s is not in deployed status in namespace %s",
            TRAEFIK_RELEASE_NAME, traefikNamespace));
    logger.info("Traefik release {0} status is deployed in namespace {1}",
        TRAEFIK_RELEASE_NAME, traefikNamespace);

    // wait until the Traefik pod is ready.
    withStandardRetryPolicy
        .conditionEvaluationListener(condition -> logger.info("Waiting for Traefik to be ready in "
        + "namespace {0} (elapsed time {1}ms, remaining time {2}ms)",
        traefikNamespace,
        condition.getElapsedTimeInMS(),
        condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isTraefikReady(traefikNamespace), "isTraefikReady failed with ApiException"));

    return traefikHelmParams;
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
    return createIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMSPortMap, true, false, 0);
  }

  /**
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @param setIngressHost set specific host or set it to all
   * @return list of ingress hosts
   */
  public static List<String> createIngressForDomainAndVerify(String domainUid,
                                                             String domainNamespace,
                                                             Map<String, Integer> clusterNameMSPortMap,
                                                             boolean setIngressHost) {

    return createIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMSPortMap, setIngressHost,
        false, 0);
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

    return createIngressForDomainAndVerify(domainUid, domainNamespace, nodeport, clusterNameMSPortMap, true,
        false, 0);
  }

  /**
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param nodeport node port of the ingress controller
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @param setIngressHost if false does not set ingress host
   * @param enableAdminServerRouting enable the ingress rule to admin server
   * @param adminServerPort the port number of admin server pod of the domain
   * @return list of ingress hosts
   */
  public static List<String> createIngressForDomainAndVerify(String domainUid,
                                                             String domainNamespace,
                                                             int nodeport,
                                                             Map<String, Integer> clusterNameMSPortMap,
                                                             boolean setIngressHost,
                                                             boolean enableAdminServerRouting,
                                                             int adminServerPort) {

    LoggingFacade logger = getLogger();
    // create an ingress in domain namespace
    final String ingressNginxClass = "nginx";
    String ingressName = domainUid + "-" + domainNamespace + "-" + ingressNginxClass;

    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("kubernetes.io/ingress.class", ingressNginxClass);

    List<String> ingressHostList =
            createIngress(ingressName, domainNamespace, domainUid, clusterNameMSPortMap, annotations, setIngressHost,
                null, enableAdminServerRouting, adminServerPort);

    assertNotNull(ingressHostList,
            String.format("Ingress creation failed for domain %s in namespace %s", domainUid, domainNamespace));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
            .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
            .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
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
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param nodeport node port of the ingress controller
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @param setIngressHost if false does not set ingress host
   * @param tlsSecret name of the TLS secret if any
   * @return list of ingress hosts
   */
  public static List<String> createTraefikIngressForDomainAndVerify(
      String domainUid,
      String domainNamespace,
      int nodeport,
      Map<String, Integer> clusterNameMSPortMap,
      boolean setIngressHost,
      String tlsSecret) {

    LoggingFacade logger = getLogger();
    // create an ingress in domain namespace
    final String ingressTraefikClass = "traefik";
    String ingressName = domainUid + "-" + ingressTraefikClass;

    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("kubernetes.io/ingress.class", ingressTraefikClass);

    List<String> ingressHostList =
            createIngress(ingressName, domainNamespace, domainUid,
                clusterNameMSPortMap, annotations, setIngressHost, tlsSecret);

    assertNotNull(ingressHostList,
            String.format("Ingress creation failed for domain %s in namespace %s", domainUid, domainNamespace));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
            .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
            .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
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
    return installVoyagerIngressAndVerify(domainUid, domainNamespace, ingressName, clusterNameMSPortMap, null);
  }

  /**
   * Create an ingress for the domain with domainUid in a given namespace and verify.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param ingressName name of ingress to be created in a given domain
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @param tlsSecret the secret name for TLS
   * @return list of ingress hosts
   */
  public static List<String> installVoyagerIngressAndVerify(String domainUid,
                                                            String domainNamespace,
                                                            String ingressName,
                                                            Map<String, Integer> clusterNameMSPortMap,
                                                            String tlsSecret) {
    LoggingFacade logger = getLogger();
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
        createIngress(ingressName, domainNamespace, domainUid, clusterNameMSPortMap, annotations, true, tlsSecret);

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
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
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
      DateTime lastCreationTime
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
   * Check whether the cluster's replica count matches with input parameter value.
   *
   * @param clusterName Name of cluster to check
   * @param domainName Name of domain to which cluster belongs
   * @param namespace cluster's namespace
   * @param replicaCount replica count value to match
   * @return true if matches false if not
   */
  public static boolean checkClusterReplicaCountMatches(String clusterName, String domainName,
                                                        String namespace, Integer replicaCount) throws ApiException {
    Cluster cluster = TestActions.getDomainCustomResource(domainName, namespace).getSpec().getClusters()
            .stream().filter(c -> c.clusterName().equals(clusterName)).findAny().orElse(null);
    return Optional.ofNullable(cluster).get().replicas() == replicaCount;
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
        WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS);
  }

  /**
   * Create a Docker image for a model in image domain.
   *
   * @param miiImageNameBase the base mii image name used in local or to construct the image name in repository
   * @param wdtModelFile the WDT model file used to build the Docker image
   * @param appName the sample application name used to build sample app ear file in WDT model file
   * @param additionalBuildCommands - Path to a file with additional build commands
   * @param additionalBuildFilesVarargs - Additional files that are required by your additionalBuildCommands
   * @return image name with tag
   */
  public static  String createMiiImageAndVerify(String miiImageNameBase,
                                                String wdtModelFile,
                                                String appName,
                                                String additionalBuildCommands,
                                                String... additionalBuildFilesVarargs) {
    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + wdtModelFile);
    final List<String> appSrcDirList = Collections.singletonList(appName);

    return createImageAndVerify(
        miiImageNameBase, modelList, appSrcDirList, null, WEBLOGIC_IMAGE_NAME,
        WEBLOGIC_IMAGE_TAG, WLS, true, null, false,
        additionalBuildCommands, additionalBuildFilesVarargs);
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
        miiImageNameBase, wdtModelList, appSrcDirList, WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS, true);

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
   * @param wdtModelList - model file used to build the image
   * @param appSrcDirList - application to be added to the image
   * @param modelPropFile - property file to be used with the model file above
   * @param altModelDir - directory where the property file is found if not in the default MODEL_DIR
   * @return image name with tag
   */
  public static String createImageAndVerify(String imageNameBase,
                                            List<String> wdtModelList,
                                            List<String> appSrcDirList,
                                            String modelPropFile,
                                            String altModelDir,
                                            String domainHome) {

    final List<String> modelPropList = Collections.singletonList(altModelDir + "/" + modelPropFile);

    return createImageAndVerify(
      imageNameBase, wdtModelList, appSrcDirList, modelPropList, WEBLOGIC_IMAGE_NAME,
        WEBLOGIC_IMAGE_TAG, WLS, false, domainHome, false);
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
            imageNameBase, wdtModelList, appSrcDirList, modelPropList, WEBLOGIC_IMAGE_NAME,
        WEBLOGIC_IMAGE_TAG, WLS, false, domainHome, false);
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
   * @param domainHome - the domain home in the image
   * @param oneArchiveContainsMultiApps - whether one archive contains multiple apps
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
    return createImageAndVerify(
        imageNameBase, wdtModelList, appSrcDirList, modelPropList, baseImageName, baseImageTag, domainType,
        modelType, domainHome, oneArchiveContainsMultiApps, null);
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
   * @param additionalBuildCommands - Path to a file with additional build commands
   * @param additionalBuildFilesVarargs -Additional files that are required by your additionalBuildCommands
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
                                            boolean oneArchiveContainsMultiApps,
                                            String additionalBuildCommands,
                                            String... additionalBuildFilesVarargs) {

    LoggingFacade logger = getLogger();

    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = baseImageTag + "-" + dateFormat.format(date) + "-" + System.currentTimeMillis();
    // Add repository name in image name for Jenkins runs
    final String imageName = DOMAIN_IMAGES_REPO + imageNameBase;
    final String image = imageName + ":" + imageTag;

    List<String> archiveList = new ArrayList<>();
    if (appSrcDirList != null && appSrcDirList.size() != 0 && appSrcDirList.get(0) != null) {
      List<String> archiveAppsList = new ArrayList<>();
      List<String> buildAppDirList = new ArrayList<>(appSrcDirList);
      boolean buildCoherence = false;

      for (String appSrcDir : appSrcDirList) {
        if (appSrcDir.contains(".war") || appSrcDir.contains(".ear")) {
          //remove from build
          buildAppDirList.remove(appSrcDir);
          archiveAppsList.add(appSrcDir);
        }

        if (appSrcDir.contains("coherence-proxy") || appSrcDir.contains("CoherenceApp")) {
          buildCoherence = true;
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
        } else if (buildCoherence) {
          // build the Coherence GAR file
          assertTrue(buildCoherenceArchive(defaultAppParams()
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
      WitParams witParams = new WitParams()
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
          .redirect(true);

      if (additionalBuildCommands != null) {
        logger.info("additionalBuildCommands {0}", additionalBuildCommands);
        witParams.additionalBuildCommands(additionalBuildCommands);
        StringBuffer additionalBuildFilesBuff = new StringBuffer();
        for (String buildFile:additionalBuildFilesVarargs) {
          additionalBuildFilesBuff.append(buildFile).append(" ");
        }

        witParams.additionalBuildFiles(additionalBuildFilesBuff.toString().trim());
      }
      result = createImage(witParams);
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
  public static void createOcrRepoSecret(String namespace) {
    LoggingFacade logger = getLogger();
    logger.info("Creating image pull secret {0} in namespace {1}", OCR_SECRET_NAME, namespace);
    createDockerRegistrySecret(OCR_USERNAME, OCR_PASSWORD, OCR_EMAIL, OCR_REGISTRY, OCR_SECRET_NAME, namespace);
  }


  /**
   * Create a Docker registry secret in the specified namespace.
   *
   * @param namespace the namespace in which the secret will be created
   */
  public static void createOcirRepoSecret(String namespace) {
    LoggingFacade logger = getLogger();
    logger.info("Creating image pull secret {0} in namespace {1}", OCIR_SECRET_NAME, namespace);
    createDockerRegistrySecret(OCIR_USERNAME, OCIR_PASSWORD, OCIR_EMAIL,
        OCIR_REGISTRY, OCIR_SECRET_NAME, namespace);
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
    LoggingFacade logger = getLogger();
    // Create registry secret in the namespace to pull the image from repository
    JsonObject dockerConfigJsonObject = createDockerConfigJson(
        userName, password, email, registry);
    String dockerConfigJson = dockerConfigJsonObject.toString();

    // skip if the secret already exists
    V1SecretList listSecrets = listSecrets(namespace);
    if (listSecrets != null) {
      for (V1Secret item : listSecrets.getItems()) {
        if (item.getMetadata().getName().equals(secretName)) {
          logger.info("Secret {0} already exists in namespace {1}, skipping secret creation", secretName, namespace);
          return;
        }
      }
    }

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
   * Create a Docker registry secret in the specified namespace to pull base images.
   *
   * @param namespace the namespace in which the secret will be created
   */
  public static void createSecretForBaseImages(String namespace) {
    if (BASE_IMAGES_REPO.equals(OCR_REGISTRY)) {
      createOcrRepoSecret(namespace);
    } else {
      createOcirRepoSecret(namespace);
    }
  }

  /**
   * Docker login and push the image to Docker registry.
   *
   * @param dockerImage the Docker image to push to registry
   */
  public static void dockerLoginAndPushImageToRegistry(String dockerImage) {
    LoggingFacade logger = getLogger();
    // push image, if necessary
    if (!DOMAIN_IMAGES_REPO.isEmpty() && dockerImage.contains(DOMAIN_IMAGES_REPO)) {
      // docker login, if necessary
      if (!OCIR_USERNAME.equals(REPO_DUMMY_VALUE)) {
        logger.info("docker login");
        assertTrue(dockerLogin(OCIR_REGISTRY, OCIR_USERNAME, OCIR_PASSWORD), "docker login failed");
      }

      logger.info("docker push image {0} to {1}", dockerImage, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(dockerImage), String.format("docker push failed for image %s", dockerImage));
    }
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
    LoggingFacade logger = getLogger();
    // Helm install parameters
    HelmParams promHelmParams = new HelmParams()
        .releaseName(promReleaseName)
        .namespace(promNamespace)
        .repoUrl(PROMETHEUS_REPO_URL)
        .repoName(PROMETHEUS_REPO_NAME)
        .chartName("prometheus")
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
   * @return the grafana Helm installation parameters
   */
  public static GrafanaParams installAndVerifyGrafana(String grafanaReleaseName,
                                                   String grafanaNamespace,
                                                   String grafanaValueFile,
                                                   String grafanaVersion) {
    LoggingFacade logger = getLogger();
    // Helm install parameters
    HelmParams grafanaHelmParams = new HelmParams()
        .releaseName(grafanaReleaseName)
        .namespace(grafanaNamespace)
        .repoUrl(GRAFANA_REPO_URL)
        .repoName(GRAFANA_REPO_NAME)
        .chartName("grafana")
        .chartValuesFile(grafanaValueFile);

    if (grafanaVersion != null) {
      grafanaHelmParams.chartVersion(grafanaVersion);
    }

    boolean secretExists = false;
    V1SecretList listSecrets = listSecrets(grafanaNamespace);
    if (null != listSecrets) {
      for (V1Secret item : listSecrets.getItems()) {
        if (item.getMetadata().getName().equals("grafana-secret")) {
          secretExists = true;
          break;
        }
      }
    }
    if (!secretExists) {
      //create grafana secret
      createSecretWithUsernamePassword("grafana-secret", grafanaNamespace, "admin", "12345678");
    }
    // install grafana
    logger.info("Installing grafana in namespace {0}", grafanaNamespace);
    int grafanaNodePort = getNextFreePort(31060, 31200);
    logger.info("Installing grafana with node port {0}", grafanaNodePort);
    // grafana chart values to override
    GrafanaParams grafanaParams = new GrafanaParams()
        .helmParams(grafanaHelmParams)
        .nodePort(grafanaNodePort);
    boolean isGrafanaInstalled = false;
    try {
      assertTrue(installGrafana(grafanaParams),
          String.format("Failed to install grafana in namespace %s", grafanaNamespace));
    } catch (AssertionError err) {
      //retry with different nodeport
      uninstallGrafana(grafanaHelmParams);
      grafanaNodePort = getNextFreePort(31060, 31200);
      grafanaParams = new GrafanaParams()
          .helmParams(grafanaHelmParams)
          .nodePort(grafanaNodePort);
      isGrafanaInstalled = installGrafana(grafanaParams);
      if (!isGrafanaInstalled) {
        //clean up
        logger.info(String.format("Failed to install grafana in namespace %s with nodeport %s",
            grafanaNamespace, grafanaNodePort));
        uninstallGrafana(grafanaHelmParams);
        return null;
      }
    }
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
            condition -> logger.info("Waiting for grafana to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                grafanaNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isGrafanaReady(grafanaNamespace),
            "grafanaIsReady failed with ApiException"));

    //return grafanaHelmParams;
    return grafanaParams;
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
  public static DateTime getPodCreationTime(String namespace, String podName) {
    LoggingFacade logger = getLogger();
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
    LoggingFacade logger = getLogger();
    String msg = expectValid ? "valid" : "invalid";
    logger.info("Check if the given WebLogic admin credentials are {0}", msg);
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
            () -> credentialsValid(K8S_NODEPORT_HOST, podName, namespace, username, password)
                :
            () -> credentialsNotValid(K8S_NODEPORT_HOST, podName, namespace, username, password),
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
    if (!OKE_CLUSTER) {
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

    LoggingFacade logger = getLogger();
    logger.info("Running Kubernetes job to create domain for image: {1}: {2} "
        + " pvName: {3}, pvcName: {4}, domainScriptCM: {5}, namespace: {6}", image,
        pvName, pvcName, domainScriptCM, namespace);
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("create-domain-onpv-job-" + pvName) // name of the create domain job
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .initContainers(Arrays.asList(createfixPVCOwnerContainer(pvName, "/shared")))
                    .containers(Arrays.asList(jobContainer  // container containing WLST or WDT details
                        .name("create-weblogic-domain-onpv-container")
                        .image(image)
                        .imagePullPolicy("Always")
                        .ports(Arrays.asList(new V1ContainerPort()
                            .containerPort(7001)))
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("create-weblogic-domain-job-cm-volume") // domain creation scripts volume
                                .mountPath("/u01/weblogic"), // availble under /u01/weblogic inside pod
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
                            .name(BASE_IMAGES_REPO_SECRET))))));  // this secret is used only for non-kind cluster
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
    Map<String, DateTime> podsWithTimeStamps = new LinkedHashMap<>();
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
}

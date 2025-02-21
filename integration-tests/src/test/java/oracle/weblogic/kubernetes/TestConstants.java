// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static oracle.weblogic.kubernetes.actions.TestActions.listNamespaces;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getBaseImagesPrefixLength;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDomainImagePrefix;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getEnvironmentProperty;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getImageRepoFromImageName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getKindRepoImageForSpec;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getKindRepoValue;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNonEmptySystemProperty;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public interface TestConstants {

  public static final String OLD_DOMAIN_VERSION = "v8";
  public static final Boolean SKIP_CLEANUP =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.skip.cleanup", "false"));
  public static final Boolean COLLECT_LOGS_ON_SUCCESS =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.collect.logs.on.success", "false"));
  public static final int SLEEP_SECONDS_AFTER_FAILURE =
      Integer.parseInt(getNonEmptySystemProperty("wko.it.sleep.seconds.after.failure", "0"));
  public static final String K8S_NODEPORT_HOST1 = getNonEmptySystemProperty("wko.it.k8s.nodeport.host1");
  public static final String K8S_NODEPORT_HOST2 = getNonEmptySystemProperty("wko.it.k8s.nodeport.host2");
  public static final String OPDEMO = getNonEmptySystemProperty("wko.it.opdemo");
  //ARM constants
  public static final boolean ARM =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.arm.cluster", "false"));

  // domain constants
  public static final String DOMAIN_VERSION =
      getNonEmptySystemProperty("wko.it.domain.version", "v9");
  public static final String DOMAIN_API_VERSION = "weblogic.oracle/" + DOMAIN_VERSION;
  public static final String ADMIN_SERVER_NAME_BASE = "admin-server";
  public static final String MANAGED_SERVER_NAME_BASE = "managed-server";
  public static final String WLS_DOMAIN_TYPE = "WLS";
  public static final String WLS_DEFAULT_CHANNEL_NAME = "default";
  public static final String DEFAULT_WEBLOGIC_IMAGE_TAGS = "12.2.1.3, 12.2.1.4, 14.1.1.0-11";
  public static final String WEBLOGIC_IMAGE_TAGS =
      getNonEmptySystemProperty("wko.it.weblogic.image.tags", DEFAULT_WEBLOGIC_IMAGE_TAGS);
  public static final int DEFAULT_MAX_CLUSTER_SIZE = 5;
  public static final int ADMIN_SERVER_PORT_DEFAULT = 7001;

  // cluster constants
  public static final String CLUSTER_VERSION =
      getNonEmptySystemProperty("wko.it.cluster.version", "v1");
  public static final String CLUSTER_API_VERSION = "weblogic.oracle/" + CLUSTER_VERSION;

  // operator constants
  public static final String OPERATOR_RELEASE_NAME = "weblogic-operator";
  public static final String OPERATOR_CHART_DIR =
      "../kubernetes/charts/weblogic-operator";
  public static final String OPERATOR_RELEASE_IMAGE =
      getNonEmptySystemProperty("wko.it.release.image.name.operator",
          "ghcr.io/oracle/weblogic-kubernetes-operator:4.1.2");
  public static final String IMAGE_NAME_OPERATOR =
      getNonEmptySystemProperty("wko.it.image.name.operator", "oracle/weblogic-kubernetes-operator");
  public static final String OPERATOR_SERVICE_NAME = "internal-weblogic-operator-svc";
  public static final String OPERATOR_GITHUB_CHART_REPO_URL =
      "https://oracle.github.io/weblogic-kubernetes-operator/charts";
  public static final int OPERATOR_EXTERNAL_REST_HTTPSPORT = 30511;

  // kind constants
  public static final String KIND_REPO = getKindRepoValue("wko.it.kind.repo");
  public static final String KIND_NODE_NAME = getNonEmptySystemProperty("wko.it.kind.name", "kind");
  public static final boolean KIND_CLUSTER =
      Boolean.parseBoolean((getNonEmptySystemProperty("wko.it.kind.repo") != null) ? "true" : "false");

  // crio pipeline constants
  public static final String CRIO_PIPELINE_IMAGE = System.getProperty("wko.it.crio.pipeline.image");

  // BASE_IMAGES_REPO represents the repository from where all the base WebLogic
  // and InfraStructure images are pulled
  public static final String BASE_IMAGES_REPO = Optional.ofNullable(getImageRepoFromImageName(CRIO_PIPELINE_IMAGE))
      .orElse(System.getProperty("wko.it.base.images.repo"));

  public static final String BASE_IMAGES_TENANCY = System.getProperty("wko.it.base.images.tenancy");

  public static final String BASE_IMAGES_REPO_USERNAME = System.getenv("BASE_IMAGES_REPO_USERNAME");
  public static final String BASE_IMAGES_REPO_PASSWORD = System.getenv("BASE_IMAGES_REPO_PASSWORD");
  public static final String BASE_IMAGES_REPO_EMAIL = System.getenv("BASE_IMAGES_REPO_EMAIL");
  public static final String BASE_IMAGES_REPO_SECRET_NAME = "base-images-repo-secret";

  // TEST_IMAGES_REPO represents the repository (a) which contains few external
  // images such as nginx,elasticsearch,Oracle DB operator (b) all test domain 
  // images to be pushed into it.
  //
  public static final String TEST_IMAGES_REPO = System.getProperty("wko.it.test.images.repo");
  public static final String TEST_IMAGES_TENANCY = System.getProperty("wko.it.test.images.tenancy");
  public static final String TEST_IMAGES_PREFIX = getDomainImagePrefix(TEST_IMAGES_REPO, TEST_IMAGES_TENANCY);

  public static final String TEST_IMAGES_REPO_USERNAME = System.getenv("TEST_IMAGES_REPO_USERNAME");
  public static final String TEST_IMAGES_REPO_PASSWORD = System.getenv("TEST_IMAGES_REPO_PASSWORD");
  public static final String TEST_IMAGES_REPO_EMAIL = System.getenv("TEST_IMAGES_REPO_EMAIL");
  public static final String TEST_IMAGES_REPO_SECRET_NAME = "test-images-repo-secret";

  // Default image names, tags to be used to downlaod base images 
  // It depends on the default value of BASE_IMAGES_REPO. 
  // Following defaults are assumining OCIR as default for BASE_IMAGES_REPO.
  public static final String WEBLOGIC_IMAGE_NAME_DEFAULT = "test-images/weblogic";
  public static final String WEBLOGIC_IMAGE_TAG_DEFAULT = "14.1.2.0-generic-jdk17-ol8";
  public static final String FMWINFRA_IMAGE_NAME_DEFAULT = "test-images/fmw-infrastructure";
  public static final String FMWINFRA_IMAGE_TAG_DEFAULT = "14.1.2.0-jdk17-ol8";
  public static final String FMWINFRA_IMAGE_TAG_12213 = "12.2.1.3";
  public static final String DB_IMAGE_NAME_DEFAULT = "test-images/database/enterprise";
  public static final String DB_PREBUILT_IMAGE_NAME_DEFAULT = "test-images/database/express";
  public static final String DB_IMAGE_TAG_DEFAULT = "19.3.0.0";

  // repository to push the domain images created during test execution
  // (a) for kind cluster push to kind repo
  // (b) for OKD or OKE push to TEST_IMAGES_REPO 
  // (c) for local runs don't push the domain images to any repo
  public static final String DOMAIN_IMAGES_REPO = Optional.ofNullable(KIND_REPO)
      .orElse(getNonEmptySystemProperty("wko.it.test.images.repo") != null
          ? getNonEmptySystemProperty("wko.it.test.images.repo") : "");

  public static final String DOMAIN_IMAGES_PREFIX = getDomainImagePrefix(DOMAIN_IMAGES_REPO, TEST_IMAGES_TENANCY);

  // Get WEBLOGIC_IMAGE_NAME/WEBLOGIC_IMAGE_TAG from env var, 
  // if its not provided use OCIR default image values
  //
  public static final String BASE_IMAGES_PREFIX = getDomainImagePrefix(BASE_IMAGES_REPO, BASE_IMAGES_TENANCY);
  public static final String WEBLOGIC_IMAGE_NAME = BASE_IMAGES_PREFIX
      + getNonEmptySystemProperty("wko.it.weblogic.image.name", WEBLOGIC_IMAGE_NAME_DEFAULT);
  public static final String WEBLOGIC_IMAGE_TAG = getNonEmptySystemProperty("wko.it.weblogic.image.tag", 
       WEBLOGIC_IMAGE_TAG_DEFAULT);

  // Get FMWINFRA_IMAGE_NAME/FMWINFRA_IMAGE_TAG from env var, if its not 
  // provided and if base images repo is OCIR use OCIR default image values
  public static final String FMWINFRA_IMAGE_NAME = BASE_IMAGES_PREFIX
      + getNonEmptySystemProperty("wko.it.fmwinfra.image.name",  FMWINFRA_IMAGE_NAME_DEFAULT);
  public static final String FMWINFRA_IMAGE_TAG = getNonEmptySystemProperty("wko.it.fmwinfra.image.tag", 
        FMWINFRA_IMAGE_TAG_DEFAULT);

  // Get DB_IMAGE_NAME/DB_IMAGE_TAG from env var, if its not provided and
  // if base images repo is OCIR use OCIR default image values
  public static final String DB_IMAGE_NAME = BASE_IMAGES_PREFIX
      + getNonEmptySystemProperty("wko.it.db.image.name", DB_IMAGE_NAME_DEFAULT);
  public static final String DB_PREBUILT_IMAGE_NAME = BASE_IMAGES_PREFIX
      + getNonEmptySystemProperty("wko.it.db.prebuilt.image.name", DB_PREBUILT_IMAGE_NAME_DEFAULT);
  public static final String DB_IMAGE_TAG = getNonEmptySystemProperty("wko.it.db.image.tag", DB_IMAGE_TAG_DEFAULT);
  public static final String DB_IMAGE_PREBUILT_TAG = getNonEmptySystemProperty("wko.it.db.image.tag", "18.4.0-xe");

  // WebLogic Base Image with Japanese Locale
  public static final String LOCALE_IMAGE_NAME = TEST_IMAGES_PREFIX + "test-images/weblogic";
  public static final String LOCALE_IMAGE_TAG = "12.2.1.4-jp";

  // For kind, replace repo name in image name with KIND_REPO, 
  // otherwise use the actual image name
  // For example, if the image name is
  // container-registry.oracle.com/middleware/weblogic:12.2.1.4 
  // it will be pushed/used as
  // localhost:5000/middleware/weblogic:12.2.1.4 in kind and 
  // in non-kind cluster it will be used as is.

  static final int BASE_IMAGES_REPO_PREFIX_LENGTH = getBaseImagesPrefixLength(BASE_IMAGES_REPO, BASE_IMAGES_TENANCY);
  public static final String WEBLOGIC_IMAGE_TO_USE_IN_SPEC =
      getKindRepoImageForSpec(KIND_REPO, WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, BASE_IMAGES_REPO_PREFIX_LENGTH);
  public static final String FMWINFRA_IMAGE_TO_USE_IN_SPEC =
      getKindRepoImageForSpec(KIND_REPO, FMWINFRA_IMAGE_NAME, FMWINFRA_IMAGE_TAG, BASE_IMAGES_REPO_PREFIX_LENGTH);
  public static final String FMWINFRA_IMAGE_TO_USE_IN_SPEC_12213 =
      getKindRepoImageForSpec(KIND_REPO, FMWINFRA_IMAGE_NAME, FMWINFRA_IMAGE_TAG_12213, BASE_IMAGES_REPO_PREFIX_LENGTH);
  public static final String DB_IMAGE_TO_USE_IN_SPEC =
      getKindRepoImageForSpec(KIND_REPO, DB_IMAGE_NAME, DB_IMAGE_TAG, BASE_IMAGES_REPO_PREFIX_LENGTH);

  public static final String DB_19C_IMAGE_TAG = "19.3.0.0";

  // jenkins constants
  public static final String BUILD_ID = System.getProperty("wko.it.jenkins.build.id", "");
  public static final String BRANCH_NAME_FROM_JENKINS = System.getProperty("wko.it.jenkins.branch.name", "");
  public static final String SAFE_BRANCH_IMAGE_NAME =
      BRANCH_NAME_FROM_JENKINS.codePoints().map(cp -> Character.isLetterOrDigit(cp) ? cp : '-')
          .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  public static final String IMAGE_TAG_OPERATOR = getNonEmptySystemProperty("wko.it.image.tag.operator");
  public static final String IMAGE_TAG_OPERATOR_FOR_JENKINS =
      IMAGE_TAG_OPERATOR != null ? IMAGE_TAG_OPERATOR : SAFE_BRANCH_IMAGE_NAME + BUILD_ID;

  public static final String K8S_NODEPORT_HOST = getNonEmptySystemProperty("wko.it.k8s.nodeport.host",
      assertDoesNotThrow(() -> InetAddress.getLocalHost().getHostAddress()));
  public static final String K8S_NODEPORT_HOSTNAME = getNonEmptySystemProperty("wko.it.k8s.nodeport.host",
        assertDoesNotThrow(() -> InetAddress.getLocalHost().getHostName()));
  public static final String RESULTS_BASE = getNonEmptySystemProperty("wko.it.result.root",
      System.getProperty("java.io.tmpdir") + "/it-testsresults");

  public static final String LOGS_DIR = RESULTS_BASE + "/diagnostics";
  public static final String PV_ROOT =
      getNonEmptySystemProperty("wko.it.pv.root", RESULTS_BASE + "/pvroot");
  public static final String RESULTS_ROOT = RESULTS_BASE + "/workdir";
  public static final String RESULTS_TEMPFILE = RESULTS_BASE + "/tmpfile";

  // NGINX constants
  public static final String NGINX_REPO_URL = "https://kubernetes.github.io/ingress-nginx";
  public static final String NGINX_RELEASE_NAME = "nginx-release" + BUILD_ID;
  public static final String NGINX_REPO_NAME = "ingress-nginx";
  public static final String NGINX_CHART_NAME = "ingress-nginx";
  public static final String NGINX_CHART_VERSION = "4.0.17";
  public static final String NGINX_INGRESS_IMAGE_DIGEST =
      "sha256:314435f9465a7b2973e3aa4f2edad7465cc7bcdc8304be5d146d70e4da136e51";
  public static final String TEST_NGINX_IMAGE_NAME = TEST_IMAGES_TENANCY + "/test-images/ingress-nginx/controller";
  public static final String NGINX_INGRESS_IMAGE_TAG = "v1.2.0";
  public static final String NGINX_NAMESPACE = "ns-nginx";
  public static final int NGINX_INGRESS_HTTP_NODEPORT = 31880;
  public static final int NGINX_INGRESS_HTTPS_NODEPORT = 31443;
  public static final int NGINX_INGRESS_HTTP_HOSTPORT = 2180;
  public static final int NGINX_INGRESS_HTTPS_HOSTPORT = 2543;

  public static final Path RESULTS_TEMPFILE_DIR = assertDoesNotThrow(()
      -> Files.createDirectories(Paths.get(RESULTS_TEMPFILE)));
  public static final Path INGRESS_CLASS_FILE_NAME = assertDoesNotThrow(()
      -> Files.createTempFile(RESULTS_TEMPFILE_DIR, "ingressclass", ".name"));

  // Traefik constants
  public static final String TRAEFIK_REPO_URL = "https://helm.traefik.io/traefik";
  public static final String TRAEFIK_RELEASE_NAME = "traefik-release" + BUILD_ID;
  public static final String TRAEFIK_REPO_NAME = "traefik";
  public static final String TRAEFIK_CHART_NAME = "traefik";
  public static final String TRAEFIK_CHART_VERSION = "25.0.0";
  public static final String TRAEFIK_INGRESS_IMAGE_NAME = TEST_IMAGES_TENANCY + "/test-images/traefik";
  public static final String TRAEFIK_INGRESS_IMAGE_REGISTRY = TEST_IMAGES_REPO;

  public static final String TRAEFIK_INGRESS_IMAGE_TAG = "v3.0.0";
  public static final String TRAEFIK_NAMESPACE = "ns-traefik";
  public static final int TRAEFIK_INGRESS_HTTP_NODEPORT = 30080;
  public static final int TRAEFIK_INGRESS_HTTP_HOSTPORT = 2080;
  public static final int TRAEFIK_INGRESS_HTTPS_NODEPORT = 30443;
  public static final int TRAEFIK_INGRESS_HTTPS_HOSTPORT = 2043;  
  
  // ELK Stack and WebLogic logging exporter constants
  public static final String ELASTICSEARCH_NAME = "elasticsearch";
  public static final String ELASTICSEARCH_IMAGE_NAME = ARM ? TEST_IMAGES_PREFIX + "test-images/elasticsearch"
      : TEST_IMAGES_PREFIX + "test-images/docker/elasticsearch";
  public static final String ELK_STACK_VERSION = ARM ? "8.10.3-arm64" : "7.8.1";
  public static final String FLUENTD_IMAGE_VERSION =
      getNonEmptySystemProperty("wko.it.fluentd.image.version", "v1.14.5");
  public static final String ELASTICSEARCH_IMAGE = ELASTICSEARCH_IMAGE_NAME + ":" + ELK_STACK_VERSION;
  public static final String ELASTICSEARCH_HOST = "elasticsearch.default.svc.cluster.local";
  public static final int DEFAULT_LISTEN_PORT = 7100;
  public static final int ELASTICSEARCH_HTTP_PORT = 9200;
  public static final int ELASTICSEARCH_HTTPS_PORT = 9300;
  public static final int DEFAULT_SSL_LISTEN_PORT = 8100;
  public static final String ELKSTACK_NAMESPACE = "default";
  public static final String LOGSTASH_INDEX_KEY = "logstash";
  public static final String FLUENTD_INDEX_KEY = "fluentd";
  public static final String INTROSPECTOR_INDEX_KEY = "introspectord";
  public static final String WEBLOGIC_INDEX_KEY = "wls";
  public static final String KIBANA_INDEX_KEY = "kibana";
  public static final String KIBANA_NAME = "kibana";
  public static final String KIBANA_IMAGE_NAME = ARM ? TEST_IMAGES_PREFIX + "test-images/kibana"
      : TEST_IMAGES_PREFIX + "test-images/docker/kibana";
  public static final String KIBANA_IMAGE = KIBANA_IMAGE_NAME + ":" + ELK_STACK_VERSION;
  public static final String KIBANA_TYPE = "NodePort";
  public static final int KIBANA_PORT = 5601;
  public static final String LOGSTASH_NAME = "logstash";
  public static final String LOGSTASH_IMAGE_NAME = ARM ? TEST_IMAGES_PREFIX + "test-images/logstash" :
      TEST_IMAGES_PREFIX + "test-images/docker/logstash";
  public static final String LOGSTASH_IMAGE = LOGSTASH_IMAGE_NAME + ":" + ELK_STACK_VERSION;
  public static final String FLUENTD_IMAGE = TEST_IMAGES_PREFIX
            + "test-images/docker/fluentd-kubernetes-daemonset:"
            + FLUENTD_IMAGE_VERSION;
  public static final String JAVA_LOGGING_LEVEL_VALUE = "INFO";

  // MII image constants
  public static final String MII_BASIC_WDT_MODEL_FILE = "model-singleclusterdomain-sampleapp-wls.yaml";
  public static final String MII_BASIC_IMAGE_NAME = DOMAIN_IMAGES_PREFIX + "mii-basic-image";
  public static final String MII_AUXILIARY_IMAGE_NAME = DOMAIN_IMAGES_PREFIX + "mii-ai-image";
  public static final boolean SKIP_BUILD_IMAGES_IF_EXISTS =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.skip.build.images.if.exists", "false"));

  public static final String BUSYBOX_IMAGE = TEST_IMAGES_PREFIX + "test-images/docker/busybox";
  public static final String BUSYBOX_TAG = "1.36";

  // Skip the mii/wdt basic image build locally if needed
  public static final String MII_BASIC_IMAGE_TAG = SKIP_BUILD_IMAGES_IF_EXISTS ? "local" : getDateAndTimeStamp();
  public static final String MII_BASIC_IMAGE_DOMAINTYPE = "mii";
  public static final String MII_BASIC_APP_NAME = "sample-app";
  public static final String MII_BASIC_APP_DEPLOYMENT_NAME = "myear";
  public static final String MII_TWO_APP_WDT_MODEL_FILE = "model-singlecluster-two-sampleapp-wls.yaml";
  public static final String MII_UPDATED_RESTART_REQUIRED_LABEL = "weblogic.configChangesPendingRestart";

  // application constants
  public static final String MII_APP_RESPONSE_V1 = "Hello World, you have reached server managed-server";
  public static final String MII_APP_RESPONSE_V2 = "Hello World AGAIN, you have reached server managed-server";
  public static final String MII_APP_RESPONSE_V3 = "How are you doing! You have reached server managed-server";
  public static final String READ_STATE_COMMAND = "/weblogic-operator/scripts/readState.sh";

  // WDT domain-in-image constants
  public static final String WDT_BASIC_MODEL_FILE = "wdt-singlecluster-sampleapp-usingprop-wls.yaml";
  public static final String WDT_BASIC_MODEL_PROPERTIES_FILE = "wdt-singleclusterdomain-sampleapp-wls.properties";
  public static final String WDT_BASIC_IMAGE_NAME = DOMAIN_IMAGES_PREFIX + "wdt-basic-image";
  // Skip the mii/wdt basic image build locally if needed
  public static final String WDT_BASIC_IMAGE_TAG = SKIP_BUILD_IMAGES_IF_EXISTS ? "local" : getDateAndTimeStamp();
  public static final String WDT_BASIC_IMAGE_DOMAINHOME = "/u01/oracle/user_projects/domains/domain1";
  public static final String WDT_IMAGE_DOMAINHOME_BASE_DIR = "/u01/oracle/user_projects/domains";
  public static final String WDT_BASIC_IMAGE_DOMAINTYPE = "wdt";
  public static final String WDT_BASIC_APP_NAME = "sample-app";

  // Here we need a old version of WDT to build a Auxiliary Image with 
  // WDT binary only. Later it will be overwritten by latest WDT version
  // See ItMiiAuxiliaryImage.testUpdateWDTVersionUsingMultipleAuxiliaryImages
  public static final String WDT_TEST_VERSION =
      getNonEmptySystemProperty("wko.it.wdt.test.version", "1.9.20");

  //monitoring constants
  public static final String MONITORING_EXPORTER_WEBAPP_VERSION =
      getNonEmptySystemProperty("wko.it.monitoring.exporter.webapp.version", "2.1.3");
  public static final String MONITORING_EXPORTER_BRANCH =
      getNonEmptySystemProperty("wko.it.monitoring.exporter.branch", "main");
  public static final String PROMETHEUS_CHART_VERSION =
      getNonEmptySystemProperty("wko.it.prometheus.chart.version", "17.0.0");
  public static final String GRAFANA_CHART_VERSION =
      getNonEmptySystemProperty("wko.it.grafana.chart.version", "6.44.11");
  public static final String PROMETHEUS_REPO_NAME = "prometheus-community";
  public static final String PROMETHEUS_REPO_URL = "https://prometheus-community.github.io/helm-charts";

  public static final String PROMETHEUS_IMAGE_NAME = TEST_IMAGES_PREFIX + "test-images/prometheus/prometheus";
  public static final String PROMETHEUS_IMAGE_TAG = "v2.39.1";

  public static final String PROMETHEUS_ALERT_MANAGER_IMAGE_NAME = TEST_IMAGES_PREFIX
      + "test-images/prometheus/alertmanager";
  public static final String PROMETHEUS_ALERT_MANAGER_IMAGE_TAG = "v0.24.0";

  public static final String PROMETHEUS_CONFIG_MAP_RELOAD_IMAGE_NAME = TEST_IMAGES_PREFIX
      + "test-images/jimmidyson/configmap-reload";
  public static final String PROMETHEUS_CONFIG_MAP_RELOAD_IMAGE_TAG = "v0.5.0";

  public static final String PROMETHEUS_PUSHGATEWAY_IMAGE_NAME = TEST_IMAGES_PREFIX
      + "test-images/prometheus/pushgateway";
  public static final String PROMETHEUS_PUSHGATEWAY_IMAGE_TAG = "v1.4.3";

  public static final String PROMETHEUS_NODE_EXPORTER_IMAGE_NAME = TEST_IMAGES_PREFIX
      + "test-images/prometheus/node-exporter";
  public static final String PROMETHEUS_NODE_EXPORTER_IMAGE_TAG = "v1.3.1";

  public static final String GRAFANA_REPO_NAME = "grafana";
  public static final String GRAFANA_REPO_URL = "https://grafana.github.io/helm-charts";
  public static final String GRAFANA_IMAGE_NAME = TEST_IMAGES_PREFIX + "test-images/grafana/grafana";
  public static final String GRAFANA_IMAGE_TAG = "9.3.0";

  // credentials
  public static final String ADMIN_USERNAME_DEFAULT = "weblogic";
  public static final String ADMIN_PASSWORD_DEFAULT = "welcome1";
  public static final String ADMIN_USERNAME_PATCH = "weblogicnew";
  public static final String ADMIN_PASSWORD_PATCH = "welcome1new";

  public static final String ENCRYPION_USERNAME_DEFAULT = "weblogicenc";
  public static final String ENCRYPION_PASSWORD_DEFAULT = "weblogicenc";

  // REST API
  public static final String PROJECT_ROOT = System.getProperty("user.dir");
  public static final String GEN_EXTERNAL_REST_IDENTITY_FILE =
      PROJECT_ROOT + "/../kubernetes/samples/scripts/rest/generate-external-rest-identity.sh";
  public static final String DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME = "weblogic-operator-external-rest-identity";

  public static final String ISTIO_VERSION =
      getNonEmptySystemProperty("wko.it.istio.version", "1.23.0");
  public static final int ISTIO_HTTP_HOSTPORT = 2480;
  public static final int ISTIO_HTTPS_HOSTPORT = 2490;  

  //MySQL database constants
  public static final String MYSQL_IMAGE = BASE_IMAGES_PREFIX + "test-images/database/mysql";
  public static final String MYSQL_VERSION = "8.0.29";

  //OKE constants
  public static final String COMPARTMENT_OCID = System.getProperty("wko.it.oci.compartment.ocid", "");
  public static final boolean OKE_CLUSTER =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.oke.cluster", "false"));
  public static final boolean OKE_CLUSTER_PRIVATEIP =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.oke.cluster.privateip", "false"));
  public static final String NFS_SERVER = System.getProperty("wko.it.nfs.server", "");
  public static final String NODE_IP = System.getProperty("wko.it.node.ip", "");
  public static final String [] FSS_DIR = System.getProperty("wko.it.fss.dir","").split(",");
  public static final String IMAGE_PULL_POLICY = "IfNotPresent";

  //OKD constants
  public static final boolean OKD =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.okd.cluster", "false"));

  // OCNE constants
  public static final boolean OCNE =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.ocne.cluster", "false"));
  // CRIO_PIPELINE constants
  public static final boolean CRIO =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.crio.pipeline", "false"));

  // default name suffixes
  public String DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX = "-ext";
  public String DEFAULT_INTROSPECTOR_JOB_NAME_SUFFIX = "-introspector";
  public String OLD_DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX = "-external";

  // MII domain dynamic update
  public static final String MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG =
      "The Domain resource specified 'spec.configuration.model.onlineUpdate.enabled=true', "
          + "but there are unsupported model changes for online update";
  public static final String SSL_PROPERTIES =
      "-Dweblogic.security.SSL.ignoreHostnameVerification=true -Dweblogic.security.TrustKeyStore=DemoTrust";

  public static final boolean WEBLOGIC_SLIM = WEBLOGIC_IMAGE_TAG.contains("slim");
  public static final boolean WEBLOGIC_12213 = WEBLOGIC_IMAGE_TAG.contains("12.2.1.3") 
          && !WEBLOGIC_IMAGE_TAG.toLowerCase().contains("cpu");

  public static final String WEBLOGIC_VERSION = WEBLOGIC_IMAGE_TAG.substring(0,8) + ".0";
  public static final String HTTP_PROXY =
      Optional.ofNullable(System.getenv("HTTP_PROXY")).orElse(System.getenv("http_proxy"));
  public static final String HTTPS_PROXY =
      Optional.ofNullable(System.getenv("HTTPS_PROXY")).orElse(System.getenv("https_proxy"));
  public static final String NO_PROXY =
      Optional.ofNullable(System.getenv("NO_PROXY")).orElse(System.getenv("no_proxy"));

  // domain status condition type
  public static final String DOMAIN_STATUS_CONDITION_COMPLETED_TYPE = "Completed";
  public static final String DOMAIN_STATUS_CONDITION_PROGRESSING_TYPE = "Progressing";
  public static final String DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE = "Available";
  public static final String DOMAIN_STATUS_CONDITION_FAILED_TYPE = "Failed";
  public static final String DOMAIN_STATUS_CONDITION_ROLLING_TYPE = "Rolling";

  // Oracle RCU setup constants
  public static final String ORACLE_RCU_SECRET_NAME = "oracle-rcu-secret";
  public static final String ORACLE_RCU_SECRET_VOLUME = "oracle-rcu-volume";
  public static final String ORACLE_RCU_SECRET_MOUNT_PATH = "/rcu-secret";

  // Oracle database "no operator" constant(s)
  public static final String ORACLE_DB_SECRET_NAME = "oracle-db-secret";

  // Oracle database operator constants
  public static final String ORACLE_DB_OPERATOR_RELEASE_LATEST = "release/0.2.1";
  public static final String ORACLE_DB_OPERATOR_RELEASE =
      getNonEmptySystemProperty("wko.it.oracle.db.operator.release", ORACLE_DB_OPERATOR_RELEASE_LATEST);
  public static final String DB_OPERATOR_IMAGE = BASE_IMAGES_PREFIX + "test-images/database/operator:0.2.1";
  public static final String CERT_MANAGER
      = "https://github.com/jetstack/cert-manager/releases/latest/download/cert-manager.yaml";
  public static final String DB_OPERATOR_YAML_URL = "https://raw.githubusercontent.com/"
      + "oracle/oracle-database-operator/" + ORACLE_DB_OPERATOR_RELEASE + "/oracle-database-operator.yaml";
  public static final String SIDB_YAML_URL = "https://raw.githubusercontent.com/oracle/oracle-database-operator/main/"
      + "config/samples/sidb/singleinstancedatabase.yaml";
  public static final String ORACLELINUX_TEST_VERSION =
      getNonEmptySystemProperty("wko.it.oraclelinux.test.version", "7");

  // retry improvement
  // Defaulting to 120 seconds
  public static final Long FAILURE_RETRY_INTERVAL_SECONDS =
      Long.valueOf(getNonEmptySystemProperty("failure.retry.interval.seconds", "20"));
  // Defaulting to 1440 minutes (24 hours)
  public static final Long FAILURE_RETRY_LIMIT_MINUTES =
      Long.valueOf(getNonEmptySystemProperty("failure.retry.limit.minutes", "10"));
  String YAML_MAX_FILE_SIZE_PROPERTY = "-Dwdt.config.yaml.max.file.size=25000000";

  // kubernetes CLI, some may set this to 'oc'
  public static final String KUBERNETES_CLI_DEFAULT = "kubectl";
  public static final String KUBERNETES_CLI =
      getEnvironmentProperty("KUBERNETES_CLI", KUBERNETES_CLI_DEFAULT);

  // image build CLI, some may set this to 'podman'
  //note: 'WLSIMG_BUILDER' is the same name as the env-var/prop used by WIT
  public static final String WLSIMG_BUILDER_DEFAULT = "docker";
  public static final String WLSIMG_BUILDER =
      getEnvironmentProperty("WLSIMG_BUILDER", WLSIMG_BUILDER_DEFAULT);

  // metrics server constants
  public static final String METRICS_SERVER_YAML =
      "https://github.com/kubernetes-sigs/metrics-server/releases/download/metrics-server-helm-chart-3.8.2/components.yaml";
  
  // verrazzano related constants
  public static final String VZ_INGRESS_NS = "ingress-nginx";
  public static final String VZ_SYSTEM_NS = "verrazzano-system";
  public static final String VZ_ISTIO_NS = "istio-system";
  public static final boolean VZ_ENV = assertDoesNotThrow(() -> listNamespaces().stream()
        .anyMatch(ns -> ns.equals(VZ_SYSTEM_NS)));
  public static final String LARGE_DOMAIN_TESTING_PROPS_FILE =
      "largedomaintesting.props";
  
  public static final boolean INSTALL_WEBLOGIC = Boolean.valueOf((getNonEmptySystemProperty("wko.it.install.weblogic",
      "false")));
  public static final String WEBLOGIC_SHIPHOME = getNonEmptySystemProperty("wko.it.wls.shiphome",
      "https://home.us.oracle.com/results/release/src141200/fmw_14.1.2.0.0_wls_generic.jar");
  
  public static final String ORACLE_OPERATOR_NS = "ns-oracle-operator";  
  
  //node ports used by the integration tests
  public static final int IT_EXTERNALNODEPORTSERVICE_NODEPORT = 31000;
  public static final int IT_EXTERNALNODEPORTSERVICE_HOSTPORT = 2100;
  
  public static final int IT_DEDICATEDMODE_NODEPORT = 31004;
  public static final int IT_DEDICATEDMODE_HOSTPORT = 2104;
  
  public static final int IT_EXTERNALLBTUNNELING_HTTP_NODEPORT = 31008;
  public static final int IT_EXTERNALLBTUNNELING_HTTP_HOSTPORT = 2108;
  public static final int IT_EXTERNALLBTUNNELING_HTTPS_NODEPORT = 31012;
  public static final int IT_EXTERNALLBTUNNELING_HTTPS_HOSTPORT = 2112;
  
  public static final int IT_ITMIIDOMAINUPGRADETOSECUREMODE_HTTP_NODEPORT = 31016;
  public static final int IT_ITMIIDOMAINUPGRADETOSECUREMODE_HTTP_HOSTPORT = 2116;
  public static final int IT_ITMIIDOMAINUPGRADETOSECUREMODE_HTTPS_NODEPORT = 31020;
  public static final int IT_ITMIIDOMAINUPGRADETOSECUREMODE_HTTPS_HOSTPORT = 2120;
  
  public static final int IT_MONITORINGEXPORTER_PROMETHEUS_HTTP_NODEPORT = 31024;
  public static final int IT_MONITORINGEXPORTER_PROMETHEUS_HTTP_HOSTPORT = 2124;
  public static final int IT_MONITORINGEXPORTER_ALERT_HTTP_NODEPORT = 31028;
  public static final int IT_MONITORINGEXPORTER_ALERT_HTTP_HOSTPORT = 2128;

  public static final int IT_MONITORINGEXPORTERWEBAPP_NGINX_HTTP_NODEPORT = 31032;
  public static final int IT_MONITORINGEXPORTERWEBAPP_NGINX_HTTP_HOSTPORT = 2132;
  public static final int IT_MONITORINGEXPORTERWEBAPP_NGINX_HTTPS_NODEPORT = 31036;
  public static final int IT_MONITORINGEXPORTERWEBAPP_NGINX_HTTPS_HOSTPORT = 2136;
  
  public static final int IT_MONITORINGEXPORTERWEBAPP_PROMETHEUS_HTTP_NODEPORT = 31040;
  public static final int IT_MONITORINGEXPORTERWEBAPP_PROMETHEUS_HTTP_HOSTPORT = 2140;
  public static final int IT_MONITORINGEXPORTERWEBAPP_ALERT_HTTP_NODEPORT = 31044;
  public static final int IT_MONITORINGEXPORTERWEBAPP_ALERT_HTTP_HOSTPORT = 2144;
  
  public static final int IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTP_NODEPORT = 31048;
  public static final int IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTP_HOSTPORT = 2148;
  public static final int IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTPS_NODEPORT = 31052;
  public static final int IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTPS_HOSTPORT = 2152;  

  public static final int IT_MONITORINGEXPORTERSIDECAR_PROMETHEUS_HTTP_NODEPORT = 31056;
  public static final int IT_MONITORINGEXPORTERSIDECAR_PROMETHEUS_HTTP_HOSTPORT = 2156;
  public static final int IT_MONITORINGEXPORTERSIDECAR_ALERT_HTTP_NODEPORT = 31060;
  public static final int IT_MONITORINGEXPORTERSIDECAR_ALERT_HTTP_HOSTPORT = 2160;
  
  public static final int IT_MONITORINGEXPORTERMF_PROMETHEUS_HTTP_NODEPORT = 31064;
  public static final int IT_MONITORINGEXPORTERMF_PROMETHEUS_HTTP_HOSTPORT = 2164;
  public static final int IT_MONITORINGEXPORTERMF_ALERT_HTTP_NODEPORT = 31068;
  public static final int IT_MONITORINGEXPORTERMF_ALERT_HTTP_HOSTPORT = 2168;
  
  public static final int ITHORIZONTALPODSCALER_PROMETHEUS_HTTP_NODEPORT = 31072;
  public static final int ITHORIZONTALPODSCALER_PROMETHEUS_HTTP_HOSTPORT = 2172;
  public static final int ITHORIZONTALPODSCALER_ALERT_HTTP_CONAINERPORT = 31076;
  public static final int ITHORIZONTALPODSCALER_ALERT_HTTP_HOSTPORT = 2176;
  
  public static final int IT_ISTIOMONITORINGEXPORTER_PROMETHEUS_HTTP_NODEPORT = 31080;
  public static final int IT_ISTIOMONITORINGEXPORTER_PROMETHEUS_HTTP_HOSTPORT = 2180;

  public static final int IT_LBTWODOMAINSNGINX_INGRESS_HTTP_NODEPORT = 31084;
  public static final int IT_LBTWODOMAINSNGINX_INGRESS_HTTP_HOSTPORT = 2184;
  public static final int IT_LBTWODOMAINSNGINX_INGRESS_HTTPS_NODEPORT = 31088;  
  public static final int IT_LBTWODOMAINSNGINX_INGRESS_HTTPS_HOSTPORT = 2188;
  
  public static final int IT_WSEESSONGINX_INGRESS_HTTP_NODEPORT = 31092;
  public static final int IT_WSEESSONGINX_INGRESS_HTTP_HOSTPORT = 2192;
  public static final int IT_WSEESSONGINX_INGRESS_HTTPS_NODEPORT = 31096;
  public static final int IT_WSEESSONGINX_INGRESS_HTTPS_HOSTPORT = 2196;
  
  public static final int IT_HPACUSTOMNGINX_INGRESS_HTTP_NODEPORT = 31100;
  public static final int IT_HPACUSTOMNGINX_INGRESS_HTTP_HOSTPORT = 2200;
  public static final int IT_HPACUSTOMNGINX_INGRESS_HTTPS_NODEPORT = 31104;
  public static final int IT_HPACUSTOMNGINX_INGRESS_HTTPS_HOSTPORT = 2204;

  public static final int IT_WEBAPPACCESSNGINX_INGRESS_HTTP_NODEPORT = 31108;
  public static final int IT_WEBAPPACCESSNGINX_INGRESS_HTTP_HOSTPORT = 2208;
  public static final int IT_WEBAPPACCESSNGINX_INGRESS_HTTPS_NODEPORT = 31112;
  public static final int IT_WEBAPPACCESSNGINX_INGRESS_HTTPS_HOSTPORT = 2212;
  
  public static final int IT_MONITORINGEXPORTERSAMPLES_PROMETHEUS_HTTP_NODEPORT = 31116;
  public static final int IT_MONITORINGEXPORTERSAMPLES_PROMETHEUS_HTTP_HOSTPORT = 2216;
  public static final int IT_MONITORINGEXPORTERSAMPLES_ALERT_HTTP_NODEPORT = 31120;
  public static final int IT_MONITORINGEXPORTERSAMPLES_ALERT_HTTP_HOSTPORT = 2220;
  
  public static final int IT_REMOTECONSOLENGINX_INGRESS_HTTP_NODEPORT = 31124;
  public static final int IT_REMOTECONSOLENGINX_INGRESS_HTTP_HOSTPORT = 2224;
  public static final int IT_REMOTECONSOLENGINX_INGRESS_HTTPS_NODEPORT = 31128;
  public static final int IT_REMOTECONSOLENGINX_INGRESS_HTTPS_HOSTPORT = 2228;
  
  public static final int IT_ONPREMCRDOMAINTX_INGRESS_HTTP_NODEPORT = 31132;
  public static final int IT_ONPREMCRDOMAINTX_INGRESS_HTTP_HOSTPORT = 8001;
  public static final int IT_ONPREMCRDOMAINTX_CLUSTER_NODEPORT = 31136;
  public static final int IT_ONPREMCRDOMAINTX_CLUSTER_HOSTPORT = 2232;
  
}

// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
import java.util.Optional;

import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getKindRepoValue;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNonEmptySystemProperty;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


public interface TestConstants {

  public static final Boolean SKIP_CLEANUP =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.skip.cleanup", "false"));
  public static final Boolean COLLECT_LOGS_ON_SUCCESS =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.collect.logs.on.success", "false"));
  public static final int SLEEP_SECONDS_AFTER_FAILURE =
      Integer.parseInt(getNonEmptySystemProperty("wko.it.sleep.seconds.after.failure", "0"));
  public static final String K8S_NODEPORT_HOST1 = getNonEmptySystemProperty("wko.it.k8s.nodeport.host1");
  public static final String K8S_NODEPORT_HOST2 = getNonEmptySystemProperty("wko.it.k8s.nodeport.host2");
  public static final String OPDEMO = getNonEmptySystemProperty("wko.it.opdemo");

  // domain constants
  public static final String DOMAIN_VERSION =
      getNonEmptySystemProperty("wko.it.domain.version", "v9");
  public static final String DOMAIN_API_VERSION = "weblogic.oracle/" + DOMAIN_VERSION;
  public static final String ADMIN_SERVER_NAME_BASE = "admin-server";
  public static final String MANAGED_SERVER_NAME_BASE = "managed-server";
  public static final String WLS_DOMAIN_TYPE = "WLS";
  public static final String WLS_DEFAULT_CHANNEL_NAME = "default";
  public static final String DEFAULT_WLS_IMAGE_TAGS = "12.2.1.3, 12.2.1.4, 14.1.1.0-11";
  public static final String WEBLOGIC_IMAGE_TAGS =
      getNonEmptySystemProperty("wko.it.weblogic.image_tags", DEFAULT_WLS_IMAGE_TAGS);

  // operator constants
  public static final String OPERATOR_RELEASE_NAME = "weblogic-operator";
  public static final String OPERATOR_CHART_DIR =
      "../kubernetes/charts/weblogic-operator";
  public static final String IMAGE_NAME_OPERATOR =
      getNonEmptySystemProperty("wko.it.image.name.operator", "oracle/weblogic-kubernetes-operator");
  public static final String OPERATOR_DOCKER_BUILD_SCRIPT =
      "../buildDockerImage.sh";
  public static final String OPERATOR_SERVICE_NAME = "internal-weblogic-operator-svc";
  public static final String OPERATOR_GITHUB_CHART_REPO_URL =
      "https://oracle.github.io/weblogic-kubernetes-operator/charts";


  // kind constants
  public static final String KIND_REPO = getKindRepoValue("wko.it.kind.repo");
  public static final String REPO_DUMMY_VALUE = "dummy";

  // ocir constants
  public static final String OCIR_DEFAULT = "phx.ocir.io";
  public static final String OCIR_REGISTRY = getNonEmptySystemProperty("wko.it.ocir.registry", OCIR_DEFAULT);
  public static final String OCIR_USERNAME = Optional.ofNullable(System.getenv("OCIR_USERNAME"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String OCIR_PASSWORD = Optional.ofNullable(System.getenv("OCIR_PASSWORD"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String OCIR_EMAIL = Optional.ofNullable(System.getenv("OCIR_EMAIL"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String OCIR_SECRET_NAME = "ocir-secret";

  // ocir default image values, these values will be used while running locally
  public static final String OCIR_WEBLOGIC_IMAGE_NAME = "weblogick8s/test-images/weblogic";
  public static final String OCIR_WEBLOGIC_IMAGE_TAG = "12.2.1.4";
  public static final String OCIR_FMWINFRA_IMAGE_NAME = "weblogick8s/test-images/fmw-infrastructure";
  public static final String OCIR_FMWINFRA_IMAGE_TAG = "12.2.1.4";
  public static final String OCIR_DB_IMAGE_NAME = "weblogick8s/test-images/database/enterprise";
  public static final String OCIR_DB_IMAGE_TAG = "12.2.0.1-slim";

  // repository to push the domain images, for kind push to kind repo
  // for others push to REPO_REGISTRY if REPO_REGISTRY env var is provided,
  // if its not provided (like local runs) don't push the domain images to any repo
  public static final String DOMAIN_IMAGES_REPO = Optional.ofNullable(KIND_REPO)
      .orElse(getNonEmptySystemProperty("wko.it.repo.registry") != null
          ? getNonEmptySystemProperty("wko.it.repo.registry") + "/weblogick8s/" : "");

  // OCR constants
  public static final String OCR_SECRET_NAME = "ocr-secret";
  public static final String OCR_REGISTRY = "container-registry.oracle.com";
  public static final String OCR_USERNAME = Optional.ofNullable(System.getenv("OCR_USERNAME"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String OCR_PASSWORD = Optional.ofNullable(System.getenv("OCR_PASSWORD"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String OCR_EMAIL = Optional.ofNullable(System.getenv("OCR_EMAIL"))
      .orElse(OCR_USERNAME);

  // OCR default image values, these values will be used while running locally
  public static final String OCR_WEBLOGIC_IMAGE_NAME = "middleware/weblogic";
  public static final String OCR_WEBLOGIC_IMAGE_TAG = "12.2.1.4";
  public static final String OCR_FMWINFRA_IMAGE_NAME = "middleware/fmw-infrastructure";
  public static final String OCR_FMWINFRA_IMAGE_TAG = "12.2.1.4";
  public static final String OCR_DB_IMAGE_NAME = "database/enterprise";
  public static final String OCR_DB_IMAGE_TAG = "12.2.0.1-slim";
  public static final String OCR_DB_19C_IMAGE_TAG = "19.3.0.0";

  // ----------------------------- base images constants ---------------------
  // Get BASE_IMAGES_REPO from env var, if its not provided use OCIR as default to pull base images
  public static final String BASE_IMAGES_REPO = getNonEmptySystemProperty("wko.it.base.images.repo", OCIR_DEFAULT);
  // Use OCR secret name if OCR is used for base images, if not use OCIR secret name
  public static final String BASE_IMAGES_REPO_SECRET =
      BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_SECRET_NAME : OCIR_SECRET_NAME;

  // Get WEBLOGIC_IMAGE_NAME/WEBLOGIC_IMAGE_TAG from env var, if its not provided and
  // if base images repo is OCR use OCR default image values
  // or if base images repo is OCIR use OCIR default image values
  public static final String WEBLOGIC_IMAGE_NAME =
      BASE_IMAGES_REPO + "/" + getNonEmptySystemProperty("wko.it.weblogic.image.name",
      BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_WEBLOGIC_IMAGE_NAME : OCIR_WEBLOGIC_IMAGE_NAME);
  public static final String WEBLOGIC_IMAGE_TAG = getNonEmptySystemProperty("wko.it.weblogic.image.tag",
      BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_WEBLOGIC_IMAGE_TAG : OCIR_WEBLOGIC_IMAGE_TAG);

  // Get FMWINFRA_IMAGE_NAME/FMWINFRA_IMAGE_TAG from env var, if its not provided and
  // if base images repo is OCR use OCR default image values
  // or if base images repo is OCIR use OCIR default image values
  public static final String FMWINFRA_IMAGE_NAME =
      BASE_IMAGES_REPO + "/" + getNonEmptySystemProperty("wko.it.fmwinfra.image.name",
          BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_FMWINFRA_IMAGE_NAME : OCIR_FMWINFRA_IMAGE_NAME);
  public static final String FMWINFRA_IMAGE_TAG = getNonEmptySystemProperty("wko.it.fmwinfra.image.tag",
      BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_FMWINFRA_IMAGE_TAG : OCIR_FMWINFRA_IMAGE_TAG);

  // Get DB_IMAGE_NAME/DB_IMAGE_TAG from env var, if its not provided and
  // if base images repo is OCR use OCR default image values
  // or if base images repo is OCIR use OCIR default image values
  public static final String DB_IMAGE_NAME =
      BASE_IMAGES_REPO + "/" + getNonEmptySystemProperty("wko.it.db.image.name",
      BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_DB_IMAGE_NAME : OCIR_DB_IMAGE_NAME);
  public static final String DB_IMAGE_TAG = getNonEmptySystemProperty("wko.it.db.image.tag",
      BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_DB_IMAGE_TAG : OCIR_DB_IMAGE_TAG);

  // For kind, replace repo name in image name with KIND_REPO, otherwise use the actual image name
  // For example, image container-registry.oracle.com/middleware/weblogic:12.2.1.4 will be pushed/used as
  // localhost:5000/middleware/weblogic:12.2.1.4 in kind and in non-kind cluster it will be used as is.
  public static final String WEBLOGIC_IMAGE_TO_USE_IN_SPEC = KIND_REPO != null ? KIND_REPO
      + (WEBLOGIC_IMAGE_NAME + ":" + WEBLOGIC_IMAGE_TAG).substring(TestConstants.BASE_IMAGES_REPO.length() + 1)
      : WEBLOGIC_IMAGE_NAME + ":" + WEBLOGIC_IMAGE_TAG;
  public static final String FMWINFRA_IMAGE_TO_USE_IN_SPEC = KIND_REPO != null ? KIND_REPO
      + (FMWINFRA_IMAGE_NAME + ":" + FMWINFRA_IMAGE_TAG).substring(TestConstants.BASE_IMAGES_REPO.length() + 1)
      : FMWINFRA_IMAGE_NAME + ":" + FMWINFRA_IMAGE_TAG;
  public static final String DB_IMAGE_TO_USE_IN_SPEC = KIND_REPO != null ? KIND_REPO
      + (DB_IMAGE_NAME + ":" + DB_IMAGE_TAG).substring(TestConstants.BASE_IMAGES_REPO.length() + 1)
      : DB_IMAGE_NAME + ":" + DB_IMAGE_TAG;

  // ----------------------------- base images constants - end -------------------

  // jenkins constants
  public static final String BUILD_ID = System.getProperty("wko.it.jenkins.build.id", "");
  public static final String BRANCH_NAME_FROM_JENKINS = System.getProperty("wko.it.jenkins.branch.name", "");
  public static final String DOCKER_SAFE_BRANCH_NAME =
      BRANCH_NAME_FROM_JENKINS.codePoints().map(cp -> Character.isLetterOrDigit(cp) ? cp : '-')
          .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  public static final String IMAGE_TAG_OPERATOR = getNonEmptySystemProperty("wko.it.image.tag.operator");
  public static final String IMAGE_TAG_OPERATOR_FOR_JENKINS =
      IMAGE_TAG_OPERATOR != null ? IMAGE_TAG_OPERATOR : DOCKER_SAFE_BRANCH_NAME + BUILD_ID;

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

  // NGINX constants
  public static final String NGINX_REPO_URL = "https://kubernetes.github.io/ingress-nginx";
  public static final String NGINX_RELEASE_NAME = "nginx-release" + BUILD_ID;
  public static final String NGINX_REPO_NAME = "ingress-nginx";
  public static final String NGINX_CHART_NAME = "ingress-nginx";
  public static final String NGINX_CHART_VERSION = "4.0.17";
  public static final String NGINX_INGRESS_IMAGE_TAG = "v1.1.1";
  public static final String OCIR_NGINX_IMAGE_NAME = "weblogick8s/ingress-nginx/controller";
  public static final String GCR_NGINX_IMAGE_NAME = "k8s.gcr.io/ingress-nginx/controller";

  // Traefik constants
  public static final String TRAEFIK_REPO_URL = "https://helm.traefik.io/traefik";
  public static final String TRAEFIK_REPO_NAME = "traefik";
  public static final String TRAEFIK_RELEASE_NAME = "traefik-release" + BUILD_ID;
  public static final String TRAEFIK_CHART_NAME = "traefik";

  // Voyager constants
  public static final String APPSCODE_REPO_URL = "https://charts.appscode.com/stable/";
  public static final String VOYAGER_RELEASE_NAME = "voyager-release" + BUILD_ID;
  public static final String APPSCODE_REPO_NAME = "appscode";
  public static final String VOYAGER_CHART_NAME = "voyager";
  public static final String VOYAGER_CHART_VERSION = "12.0.0";

  // Apache constants
  public static final String OCIR_APACHE_IMAGE_NAME = "weblogick8s/oracle/apache";
  public static final String OCIR_APACHE_IMAGE_TAG = "12.2.1.4";

  // Get APACHE_IMAGE_NAME/APACHE_IMAGE_TAG from env var, if it is not provided use OCIR default image values
  public static final String APACHE_IMAGE_NAME = BASE_IMAGES_REPO + "/"
      + getNonEmptySystemProperty("wko.it.apache.image.name", OCIR_APACHE_IMAGE_NAME);
  public static final String APACHE_IMAGE_TAG =
      getNonEmptySystemProperty("wko.it.apache.image.tag", OCIR_APACHE_IMAGE_TAG);
  public static final String APACHE_IMAGE = APACHE_IMAGE_NAME + ":" + APACHE_IMAGE_TAG;
  public static final String APACHE_RELEASE_NAME = "apache-release" + BUILD_ID;
  public static final String APACHE_SAMPLE_CHART_DIR = "../kubernetes/samples/charts/apache-webtier";

  // ELK Stack and WebLogic logging exporter constants
  public static final String ELASTICSEARCH_NAME = "elasticsearch";
  public static final String ELASTICSEARCH_IMAGE_NAME = "phx.ocir.io/weblogick8s/test-images/docker/elasticsearch";
  public static final String ELK_STACK_VERSION = "7.8.1";
  public static final String FLUENTD_IMAGE_VERSION =
      getNonEmptySystemProperty("wko.it.fluentd.image.version", "v1.14.5-debian-elasticsearch7-1.1");
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
  public static final String KIBANA_IMAGE = KIBANA_NAME + ":" + ELK_STACK_VERSION;
  public static final String KIBANA_TYPE = "NodePort";
  public static final int KIBANA_PORT = 5601;
  public static final String LOGSTASH_NAME = "logstash";
  public static final String LOGSTASH_IMAGE = LOGSTASH_NAME + ":" + ELK_STACK_VERSION;
  public static final String FLUENTD_IMAGE = "fluent/fluentd-kubernetes-daemonset:" + FLUENTD_IMAGE_VERSION;
  public static final String JAVA_LOGGING_LEVEL_VALUE = "INFO";

  public static final String WLS_LOGGING_EXPORTER_YAML_FILE_NAME = "WebLogicLoggingExporter.yaml";
  public static final String COPY_WLS_LOGGING_EXPORTER_FILE_NAME = "copy-logging-files-cmds.txt";

  // MII image constants
  public static final String MII_BASIC_WDT_MODEL_FILE = "model-singleclusterdomain-sampleapp-wls.yaml";
  public static final String MII_BASIC_IMAGE_NAME = DOMAIN_IMAGES_REPO + "mii-basic-image";
  public static final String MII_AUXILIARY_IMAGE_NAME = DOMAIN_IMAGES_REPO + "mii-ai-image";
  public static final boolean SKIP_BUILD_IMAGES_IF_EXISTS =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.skip.build.images.if.exists", "false"));
  public static final String BUSYBOX_IMAGE = "phx.ocir.io/weblogick8s/test-images/docker/busybox";
  public static final String BUSYBOX_TAG = "1.34.1";

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
  public static final String WDT_BASIC_IMAGE_NAME = DOMAIN_IMAGES_REPO + "wdt-basic-image";
  // Skip the mii/wdt basic image build locally if needed
  public static final String WDT_BASIC_IMAGE_TAG = SKIP_BUILD_IMAGES_IF_EXISTS ? "local" : getDateAndTimeStamp();
  public static final String WDT_BASIC_IMAGE_DOMAINHOME = "/u01/oracle/user_projects/domains/domain1";
  public static final String WDT_IMAGE_DOMAINHOME_BASE_DIR = "/u01/oracle/user_projects/domains";
  public static final String WDT_BASIC_IMAGE_DOMAINTYPE = "wdt";
  public static final String WDT_BASIC_APP_NAME = "sample-app";

  // TODO - why do we need a different variable for this and why is the default so old?
  public static final String WDT_TEST_VERSION =
      getNonEmptySystemProperty("wko.it.wdt.test.version", "1.9.15");

  //monitoring constants
  public static final String MONITORING_EXPORTER_WEBAPP_VERSION =
      getNonEmptySystemProperty("wko.it.monitoring.exporter.webapp.version", "2.0");
  public static final String MONITORING_EXPORTER_BRANCH =
      getNonEmptySystemProperty("wko.it.monitoring.exporter.branch", "main");
  public static final String PROMETHEUS_CHART_VERSION =
      getNonEmptySystemProperty("wko.it.prometheus.chart.version", "15.2.0");
  public static final String GRAFANA_CHART_VERSION =
      getNonEmptySystemProperty("wko.it.grafana.chart.version", "6.22.0");
  public static final String PROMETHEUS_REPO_NAME = "prometheus-community";
  public static final String PROMETHEUS_REPO_URL = "https://prometheus-community.github.io/helm-charts";
  public static final String GRAFANA_REPO_NAME = "grafana";
  public static final String GRAFANA_REPO_URL = "https://grafana.github.io/helm-charts";

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
      getNonEmptySystemProperty("wko.it.istio.version", "1.13.2");

  //MySQL database constants
  public static final String MYSQL_VERSION = "5.6";

  //OKE constants
  public static final boolean OKE_CLUSTER =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.oke.cluster", "false"));
  public static final String NFS_SERVER = System.getProperty("wko.it.nfs.server", "");
  public static final String FSS_DIR = System.getProperty("wko.it.fss.dir", "");

  //OKD constants
  public static final boolean OKD =
      Boolean.parseBoolean(getNonEmptySystemProperty("wko.it.okd.cluster", "false"));

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

  public static final String WEBLOGIC_VERSION = "12.2.1.4.0";
  public static final String HTTP_PROXY =
      Optional.ofNullable(System.getenv("HTTP_PROXY")).orElse(System.getenv("http_proxy"));
  public static final String HTTPS_PROXY =
      Optional.ofNullable(System.getenv("HTTPS_PROXY")).orElse(System.getenv("https_proxy"));
  public static final String NO_PROXY =
      Optional.ofNullable(System.getenv("NO_PROXY")).orElse(System.getenv("no_proxy"));

  // domain status condition type
  public static final String DOMAIN_STATUS_CONDITION_COMPLETED_TYPE = "Completed";
  public static final String DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE = "Available";
  public static final String DOMAIN_STATUS_CONDITION_FAILED_TYPE = "Failed";
  public static final String DOMAIN_STATUS_CONDITION_ROLLING_TYPE = "Rolling";

  //Oracle database operator constants
  public static final String ORACLE_DB_OPERATOR_RELEASE_LATEST = "release/0.1.0";
  public static final String ORACLE_DB_OPERATOR_RELEASE =
      getNonEmptySystemProperty("wko.it.oracle.db.operator.release", ORACLE_DB_OPERATOR_RELEASE_LATEST);
  public static final String DB_OPERATOR_IMAGE = BASE_IMAGES_REPO + "/weblogick8s/test-images/database/operator:0.1.0";
  public static final String CERT_MANAGER
      = "https://github.com/jetstack/cert-manager/releases/latest/download/cert-manager.yaml";
  public static final String DB_OPERATOR_YAML_URL = "https://raw.githubusercontent.com/"
      + "oracle/oracle-database-operator/" + ORACLE_DB_OPERATOR_RELEASE + "/oracle-database-operator.yaml";
  public static final String SIDB_YAML_URL = "https://raw.githubusercontent.com/oracle/oracle-database-operator/main/"
      + "config/samples/sidb/singleinstancedatabase.yaml";
  public static final String ORACLELINUX_TEST_VERSION =
      getNonEmptySystemProperty("wko.it.oraclelinux.test.version", "7");
}

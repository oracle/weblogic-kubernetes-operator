// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
import java.util.Optional;

import oracle.weblogic.kubernetes.utils.TestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public interface TestConstants {

  // domain constants
  public static final String DOMAIN_VERSION = Optional.ofNullable(System.getenv("DOMAIN_VERSION"))
      .orElse("v8");
  public static final String DOMAIN_API_VERSION = "weblogic.oracle/" + DOMAIN_VERSION;
  public static final String ADMIN_SERVER_NAME_BASE = "admin-server";
  public static final String MANAGED_SERVER_NAME_BASE = "managed-server";
  public static final String WLS_DOMAIN_TYPE = "WLS";
  public static final String WLS_DEFAULT_CHANNEL_NAME = "default";
  public static final String DEFAULT_WLS_IMAGE_TAGS = "12.2.1.3, 12.2.1.4, 14.1.1.0-11";

  // operator constants
  public static final String OPERATOR_RELEASE_NAME = "weblogic-operator";
  public static final String OPERATOR_CHART_DIR =
      "../kubernetes/charts/weblogic-operator";
  public static final String IMAGE_NAME_OPERATOR =
      "oracle/weblogic-kubernetes-operator";
  public static final String OPERATOR_DOCKER_BUILD_SCRIPT =
      "../buildDockerImage.sh";
  public static final String OPERATOR_SERVICE_NAME = "internal-weblogic-operator-svc";
  public static final String OPERATOR_GITHUB_CHART_REPO_URL =
      "https://oracle.github.io/weblogic-kubernetes-operator/charts";


  // kind constants
  public static final String KIND_REPO = System.getenv("KIND_REPO");
  public static final String REPO_DUMMY_VALUE = "dummy";

  // ocir constants
  public static final String OCIR_DEFAULT = "phx.ocir.io";
  public static final String OCIR_REGISTRY = Optional.ofNullable(System.getenv("OCIR_REGISTRY"))
      .orElse(OCIR_DEFAULT);
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
      .orElse(System.getenv("REPO_REGISTRY") != null ? System.getenv("REPO_REGISTRY") + "/weblogick8s/" : "");

  // OCR constants
  public static final String OCR_SECRET_NAME = "ocr-secret";
  public static final String OCR_REGISTRY = "container-registry.oracle.com";
  public static final String OCR_USERNAME = Optional.ofNullable(System.getenv("OCR_USERNAME"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String OCR_PASSWORD = Optional.ofNullable(System.getenv("OCR_PASSWORD"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String OCR_EMAIL = Optional.ofNullable(System.getenv("OCR_EMAIL"))
      .orElse(REPO_DUMMY_VALUE);

  // OCR default image values, these values will be used while running locally
  public static final String OCR_WEBLOGIC_IMAGE_NAME = "middleware/weblogic";
  public static final String OCR_WEBLOGIC_IMAGE_TAG = "12.2.1.4";
  public static final String OCR_FMWINFRA_IMAGE_NAME = "middleware/fmw-infrastructure";
  public static final String OCR_FMWINFRA_IMAGE_TAG = "12.2.1.4";
  public static final String OCR_DB_IMAGE_NAME = "database/enterprise";
  public static final String OCR_DB_IMAGE_TAG = "12.2.0.1-slim";

  // ----------------------------- base images constants ---------------------
  // Get BASE_IMAGES_REPO from env var, if its not provided use OCIR as default to pull base images
  public static final String BASE_IMAGES_REPO = Optional.ofNullable(System.getenv("BASE_IMAGES_REPO"))
      .orElse(OCIR_DEFAULT);
  // Use OCR secret name if OCR is used for base images, if not use OCIR secret name
  public static final String BASE_IMAGES_REPO_SECRET =
      BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_SECRET_NAME : OCIR_SECRET_NAME;

  // Get WEBLOGIC_IMAGE_NAME/WEBLOGIC_IMAGE_TAG from env var, if its not provided and
  // if base images repo is OCR use OCR default image values
  // or if base images repo is OCIR use OCIR default image values
  public static final String WEBLOGIC_IMAGE_NAME
          = BASE_IMAGES_REPO + "/" + Optional.ofNullable(System.getenv("WEBLOGIC_IMAGE_NAME"))
      .orElse(BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_WEBLOGIC_IMAGE_NAME : OCIR_WEBLOGIC_IMAGE_NAME);
  public static final String WEBLOGIC_IMAGE_TAG = Optional.ofNullable(System.getenv("WEBLOGIC_IMAGE_TAG"))
      .orElse(BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_WEBLOGIC_IMAGE_TAG : OCIR_WEBLOGIC_IMAGE_TAG);
  public static final String WLS_UPDATE_IMAGE_TAG = "14.1.1.0-11";
  public static final String WLS_LATEST_IMAGE_TAG = "14.1.1.0-11";

  // Get FMWINFRA_IMAGE_NAME/FMWINFRA_IMAGE_TAG from env var, if its not provided and
  // if base images repo is OCR use OCR default image values
  // or if base images repo is OCIR use OCIR default image values
  public static final String FMWINFRA_IMAGE_NAME
      = BASE_IMAGES_REPO + "/" + Optional.ofNullable(System.getenv("FMWINFRA_IMAGE_NAME"))
      .orElse(BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_FMWINFRA_IMAGE_NAME : OCIR_FMWINFRA_IMAGE_NAME);
  public static final String FMWINFRA_IMAGE_TAG = Optional.ofNullable(System.getenv("FMWINFRA_IMAGE_TAG"))
      .orElse(BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_FMWINFRA_IMAGE_TAG : OCIR_FMWINFRA_IMAGE_TAG);

  // Get DB_IMAGE_NAME/DB_IMAGE_TAG from env var, if its not provided and
  // if base images repo is OCR use OCR default image values
  // or if base images repo is OCIR use OCIR default image values
  public static final String DB_IMAGE_NAME
      = BASE_IMAGES_REPO + "/" + Optional.ofNullable(System.getenv("DB_IMAGE_NAME"))
      .orElse(BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_DB_IMAGE_NAME : OCIR_DB_IMAGE_NAME);
  public static final String DB_IMAGE_TAG = Optional.ofNullable(System.getenv("DB_IMAGE_TAG"))
      .orElse(BASE_IMAGES_REPO.equals(OCR_REGISTRY) ? OCR_DB_IMAGE_TAG : OCIR_DB_IMAGE_TAG);

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
  public static final String BUILD_ID = Optional.ofNullable(System.getenv("BUILD_ID"))
      .orElse("");
  public static final String BRANCH_NAME_FROM_JENKINS = Optional.ofNullable(System.getenv("BRANCH"))
      .orElse("");

  public static final String K8S_NODEPORT_HOST = Optional.ofNullable(System.getenv("K8S_NODEPORT_HOST"))
        .orElse(assertDoesNotThrow(() -> InetAddress.getLocalHost().getHostAddress()));
  public static final String RESULTS_BASE = System.getenv().getOrDefault("RESULT_ROOT",
      System.getProperty("java.io.tmpdir") + "/it-testsresults");

  public static final String LOGS_DIR = RESULTS_BASE + "/diagnostics";
  public static final String PV_ROOT = System.getenv().getOrDefault("PV_ROOT", 
      RESULTS_BASE + "/pvroot");
  public static final String RESULTS_ROOT = RESULTS_BASE + "/workdir";

  // NGINX constants
  public static final String NGINX_REPO_URL = "https://kubernetes.github.io/ingress-nginx";
  public static final String NGINX_RELEASE_NAME = "nginx-release" + BUILD_ID;
  public static final String NGINX_REPO_NAME = "ingress-nginx";
  public static final String NGINX_CHART_NAME = "ingress-nginx";
  public static final String NGINX_CHART_VERSION = "2.16.0";

  // Traefik constants
  public static final String TRAEFIK_REPO_URL = "https://containous.github.io/traefik-helm-chart";
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
  public static final String APACHE_IMAGE_NAME = "phx.ocir.io/weblogick8s/oracle/apache";
  public static final String APACHE_IMAGE_VERSION = "12.2.1.4";
  public static final String APACHE_IMAGE = APACHE_IMAGE_NAME + ":" + APACHE_IMAGE_VERSION;
  public static final String APACHE_RELEASE_NAME = "apache-release" + BUILD_ID;
  public static final String APACHE_SAMPLE_CHART_DIR = "../kubernetes/samples/charts/apache-webtier";

  // ELK Stack and WebLogic logging exporter constants
  public static final String ELASTICSEARCH_NAME = "elasticsearch";
  public static final String ELK_STACK_VERSION = "7.8.1";
  public static final String FLUENTD_IMAGE_VERSION = Optional.ofNullable(System.getenv("FLUENTD_IMAGE_VERSION"))
      .orElse("v1.3.3-debian-elasticsearch-1.3");
  public static final String ELASTICSEARCH_IMAGE = ELASTICSEARCH_NAME + ":" + ELK_STACK_VERSION;
  public static final String ELASTICSEARCH_HOST = "elasticsearch.default.svc.cluster.local";
  public static final int ELASTICSEARCH_HTTP_PORT = 9200;
  public static final int ELASTICSEARCH_HTTPS_PORT = 9300;
  public static final String ELKSTACK_NAMESPACE = "default";
  public static final String LOGSTASH_INDEX_KEY = "logstash";
  public static final String FLUENTD_INDEX_KEY = "fluentd";
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
  public static final String MII_BASIC_IMAGE_TAG = TestUtils.getDateAndTimeStamp();
  public static final String MII_BASIC_IMAGE_DOMAINTYPE = "mii";
  public static final String MII_BASIC_APP_NAME = "sample-app";
  public static final String MII_TWO_APP_WDT_MODEL_FILE = "model-singlecluster-two-sampleapp-wls.yaml";

  // application constants
  public static final String MII_APP_RESPONSE_V1 = "Hello World, you have reached server managed-server";
  public static final String MII_APP_RESPONSE_V2 = "Hello World AGAIN, you have reached server managed-server";
  public static final String MII_APP_RESPONSE_V3 = "How are you doing! You have reached server managed-server";
  public static final String READ_STATE_COMMAND = "/weblogic-operator/scripts/readState.sh";

  // WDT domain-in-image constants
  public static final String WDT_BASIC_MODEL_FILE = "wdt-singlecluster-sampleapp-usingprop-wls.yaml";
  public static final String WDT_BASIC_MODEL_PROPERTIES_FILE = "wdt-singleclusterdomain-sampleapp-wls.properties";
  public static final String WDT_BASIC_IMAGE_NAME = DOMAIN_IMAGES_REPO + "wdt-basic-image";
  public static final String WDT_BASIC_IMAGE_TAG = TestUtils.getDateAndTimeStamp();
  public static final String WDT_BASIC_IMAGE_DOMAINHOME = "/u01/oracle/user_projects/domains/domain1";
  public static final String WDT_IMAGE_DOMAINHOME_BASE_DIR = "/u01/oracle/user_projects/domains";
  public static final String WDT_BASIC_IMAGE_DOMAINTYPE = "wdt";
  public static final String WDT_BASIC_APP_NAME = "sample-app";

  //monitoring constants
  public static final String MONITORING_EXPORTER_VERSION = Optional.ofNullable(System.getenv(
      "MONITORING_EXPORTER_VERSION"))
      .orElse("1.3.0");
  public static final String MONITORING_EXPORTER_BRANCH = Optional.ofNullable(System.getenv(
      "MONITORING_EXPORTER_BRANCH"))
      .orElse("master");
  public static final String PROMETHEUS_CHART_VERSION = Optional.ofNullable(System.getenv("PROMETHEUS_CHART_VERSION"))
      .orElse("11.1.5");
  public static final String GRAFANA_CHART_VERSION = Optional.ofNullable(System.getenv("GRAFANA_CHART_VERSION"))
      .orElse("5.0.20");
  public static final String PROMETHEUS_REPO_NAME = "stable";
  public static final String PROMETHEUS_REPO_URL = "https://charts.helm.sh/stable/";
  public static final String GRAFANA_REPO_NAME = "stable";
  public static final String GRAFANA_REPO_URL = "https://kubernetes-charts.storage.googleapis.com/";

  // credentials
  public static final String ADMIN_USERNAME_DEFAULT = "weblogic";
  public static final String ADMIN_PASSWORD_DEFAULT = "welcome1";
  public static final String ADMIN_USERNAME_PATCH = "weblogicnew";
  public static final String ADMIN_PASSWORD_PATCH = "welcome1new";

  // REST API
  public static final String PROJECT_ROOT = System.getProperty("user.dir");
  public static final String GEN_EXTERNAL_REST_IDENTITY_FILE =
      PROJECT_ROOT + "/../kubernetes/samples/scripts/rest/generate-external-rest-identity.sh";
  public static final String DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME = "weblogic-operator-external-rest-identity";

  // Default ISTIO version is 1.7.3
  public static final String ISTIO_VERSION = 
        Optional.ofNullable(System.getenv("ISTIO_VERSION")).orElse("1.7.3");

  //MySQL database constants
  public static final String MYSQL_VERSION = "5.6";

  //OKE constants
  public static final boolean OKE_CLUSTER = Boolean.parseBoolean(Optional.ofNullable(System.getenv("OKE_CLUSTER"))
      .orElse("false"));
  public static final String NFS_SERVER = Optional.ofNullable(System.getenv("NFS_SERVER"))
      .orElse("");
  public static final String FSS_DIR = Optional.ofNullable(System.getenv("FSS_DIR"))
      .orElse("");

  // default name suffixes
  public String DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX = "-ext";
  public String DEFAULT_INTROSPECTOR_JOB_NAME_SUFFIX = "-introspector";
  public String OLD_DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX = "-external";
  public static final boolean WEBLOGIC_SLIM =
      WEBLOGIC_IMAGE_TAG.contains("slim") ? true : false;
}

// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
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
  public static final String DEFAULT_WLS_IMAGE_TAGS = "12.2.1.3, 14.1.1.0";

  // operator constants
  public static final String OPERATOR_RELEASE_NAME = "weblogic-operator";
  public static final String OPERATOR_CHART_DIR =
      "../kubernetes/charts/weblogic-operator";
  public static final String IMAGE_NAME_OPERATOR =
      "oracle/weblogic-kubernetes-operator";
  public static final String OPERATOR_DOCKER_BUILD_SCRIPT =
      "../buildDockerImage.sh";
  public static final String OPERATOR_SERVICE_NAME = "internal-weblogic-operator-svc";
  public static final String REPO_DUMMY_VALUE = "dummy";
  public static final String REPO_SECRET_NAME = "ocir-secret";
  public static final String REPO_REGISTRY = Optional.ofNullable(System.getenv("REPO_REGISTRY"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String REPO_DEFAULT = "phx.ocir.io/weblogick8s/";
  public static final String KIND_REPO = System.getenv("KIND_REPO");
  public static final String REPO_NAME = Optional.ofNullable(KIND_REPO)
      .orElse(!REPO_REGISTRY.equals(REPO_DUMMY_VALUE) ? REPO_DEFAULT : "");
  public static final String REPO_USERNAME = Optional.ofNullable(System.getenv("REPO_USERNAME"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String REPO_PASSWORD = Optional.ofNullable(System.getenv("REPO_PASSWORD"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String REPO_EMAIL = Optional.ofNullable(System.getenv("REPO_EMAIL"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String OPERATOR_GITHUB_CHART_REPO_URL =
        "https://oracle.github.io/weblogic-kubernetes-operator/charts";

  // OCR registry
  public static final String OCR_SECRET_NAME = "ocr-secret";
  public static final String OCR_REGISTRY = "container-registry.oracle.com";
  public static final String OCR_USERNAME = Optional.ofNullable(System.getenv("OCR_USERNAME"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String OCR_PASSWORD = Optional.ofNullable(System.getenv("OCR_PASSWORD"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String OCR_EMAIL = Optional.ofNullable(System.getenv("OCR_EMAIL"))
      .orElse(REPO_DUMMY_VALUE);

  // jenkins constants
  public static final String BUILD_ID = Optional.ofNullable(System.getenv("BUILD_ID"))
      .orElse("");
  public static final String BRANCH_NAME_FROM_JENKINS = Optional.ofNullable(System.getenv("BRANCH"))
      .orElse("");

  public static final String K8S_NODEPORT_HOST = Optional.ofNullable(System.getenv("K8S_NODEPORT_HOST"))
        .orElse(assertDoesNotThrow(() -> InetAddress.getLocalHost().getHostName()));
  public static final String GOOGLE_REPO_URL = "https://kubernetes-charts.storage.googleapis.com/";
  public static final String RESULTS_ROOT = System.getenv().getOrDefault("RESULT_ROOT",
      System.getProperty("java.io.tmpdir")) + "/ittestsresults";
  public static final String LOGS_DIR = System.getenv().getOrDefault("RESULT_ROOT",
      System.getProperty("java.io.tmpdir")) + "/diagnosticlogs";

  public static final String PV_ROOT = System.getenv().getOrDefault("PV_ROOT",
      System.getProperty("java.io.tmpdir") + "/ittestspvroot");

  // NGINX constants
  public static final String NGINX_RELEASE_NAME = "nginx-release" + BUILD_ID;
  public static final String STABLE_REPO_NAME = "stable";
  public static final String NGINX_CHART_NAME = "nginx-ingress";

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
  public static final String APACHE_IMAGE_VERSION = "12.2.1.3";
  public static final String APACHE_IMAGE = APACHE_IMAGE_NAME + ":" + APACHE_IMAGE_VERSION;
  public static final String APACHE_RELEASE_NAME = "apache-release" + BUILD_ID;
  public static final String APACHE_SAMPLE_CHART_DIR = "../kubernetes/samples/charts/apache-webtier";

  // ELK Stack and WebLogic logging exporter constants
  public static final String ELASTICSEARCH_NAME = "elasticsearch";
  public static final String ELK_STACK_VERSION = "7.8.1";
  public static final String ELASTICSEARCH_IMAGE = ELASTICSEARCH_NAME + ":" + ELK_STACK_VERSION;
  public static final String ELASTICSEARCH_HOST = "elasticsearch.default.svc.cluster.local";
  public static final int ELASTICSEARCH_HTTP_PORT = 9200;
  public static final int ELASTICSEARCH_HTTPS_PORT = 9300;
  public static final String ELKSTACK_NAMESPACE = "default";
  public static final String LOGSTASH_INDEX_KEY = "logstash";
  public static final String WEBLOGIC_INDEX_KEY = "wls";
  public static final String KIBANA_INDEX_KEY = "kibana";
  public static final String KIBANA_NAME = "kibana";
  public static final String KIBANA_IMAGE = KIBANA_NAME + ":" + ELK_STACK_VERSION;
  public static final String KIBANA_TYPE = "NodePort";
  public static final int KIBANA_PORT = 5601;
  public static final String LOGSTASH_NAME = "logstash";
  public static final String LOGSTASH_IMAGE = LOGSTASH_NAME + ":" + ELK_STACK_VERSION;
  public static final String JAVA_LOGGING_LEVEL_VALUE = "INFO";

  public static final String WLS_LOGGING_EXPORTER_JAR_VERSION = "1.0.0";
  public static final String WLS_LOGGING_EXPORTER_JAR_REPOS =
      "https://github.com/oracle/weblogic-logging-exporter/releases/download/v" + WLS_LOGGING_EXPORTER_JAR_VERSION;
  public static final String WLS_LOGGING_EXPORTER_JAR_NAME =
      "weblogic-logging-exporter-" + WLS_LOGGING_EXPORTER_JAR_VERSION + ".jar";
  public static final String SNAKE_YAML_JAR_VERSION = "1.23";
  public static final String SNAKE_YAML_JAR_REPOS =
      "https://repo1.maven.org/maven2/org/yaml/snakeyaml/" + SNAKE_YAML_JAR_VERSION;
  public static final String SNAKE_YAML_JAR_NAME = "snakeyaml-" + SNAKE_YAML_JAR_VERSION + ".jar";
  public static final String WLS_LOGGING_EXPORTER_YAML_FILE_NAME = "WebLogicLoggingExporter.yaml";
  public static final String COPY_WLS_LOGGING_EXPORTER_FILE_NAME = "copy-logging-files-cmds.txt";

  // MII image constants
  public static final String MII_BASIC_WDT_MODEL_FILE = "model-singleclusterdomain-sampleapp-wls.yaml";
  public static final String MII_BASIC_IMAGE_NAME = REPO_NAME + "mii-basic-image";
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
  public static final String WDT_BASIC_IMAGE_NAME = REPO_NAME + "wdt-basic-image";
  public static final String WDT_BASIC_IMAGE_TAG = TestUtils.getDateAndTimeStamp();
  public static final String WDT_BASIC_IMAGE_DOMAINHOME = "/u01/oracle/user_projects/domains/domain1";
  public static final String WDT_IMAGE_DOMAINHOME_BASE_DIR = "/u01/oracle/user_projects/domains";
  public static final String WDT_BASIC_IMAGE_DOMAINTYPE = "wdt";
  public static final String WDT_BASIC_APP_NAME = "sample-app";

  //monitoring constants
  public static final String MONITORING_EXPORTER_VERSION = Optional.ofNullable(System.getenv(
      "MONITORING_EXPORTER_VERSION"))
      .orElse("1.2.0");
  public static final String MONITORING_EXPORTER_BRANCH = Optional.ofNullable(System.getenv(
      "MONITORING_EXPORTER_BRANCH"))
      .orElse("master");
  public static final String PROMETHEUS_CHART_VERSION = Optional.ofNullable(System.getenv("PROMETHEUS_CHART_VERSION"))
      .orElse("11.1.5");
  public static final String GRAFANA_CHART_VERSION = Optional.ofNullable(System.getenv("GRAFANA_CHART_VERSION"))
      .orElse("5.0.20");
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

  // JRF constants
  public static final String JRF_BASE_IMAGE_NAME = OCR_REGISTRY + "/middleware/fmw-infrastructure";
  public static final String JRF_BASE_IMAGE_TAG = "12.2.1.4";
  public static final String DB_IMAGE_NAME = OCR_REGISTRY + "/database/enterprise";
  public static final String DB_IMAGE_TAG = "12.2.0.1-slim";

  // istio constants
  public static final String ISTIO_VERSION = "1.5.4";

  //MySQL database constants
  public static final String MYSQL_VERSION = "5.6";

}

// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
import java.util.Optional;

import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
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

  // TEST_IMAGES_REPO constants
  // TEST_IMAGES_REPO represents the repository (a) which contains few external
  // images such as nginx,elasticsearch,Oracle DB operator (b) all test domain 
  // images to be pushed into it.
  // Default for TEST_IMAGES_REPO is phx.ocir.io

  public static final String TEST_IMAGES_REPO_DEFAULT  = "phx.ocir.io";
  public static final String TEST_IMAGES_REPO = Optional.ofNullable(System.getenv("TEST_IMAGES_REPO"))
      .orElse(TEST_IMAGES_REPO_DEFAULT);
  public static final String TEST_IMAGES_REPO_USERNAME = System.getenv("TEST_IMAGES_REPO_USERNAME");
  public static final String TEST_IMAGES_REPO_PASSWORD = System.getenv("TEST_IMAGES_REPO_PASSWORD");
  public static final String TEST_IMAGES_REPO_EMAIL = System.getenv("TEST_IMAGES_REPO_EMAIL");
  public static final String TEST_IMAGES_REPO_SECRET_NAME = "test-images-repo-secret";

  // Default image names, tags to be used to downlaod base images 
  // It depends on the default value of BASE_IMAGES_REPO. 
  // Following defaults are assumining OCIR as default for BASE_IMAGES_REPO.
  public static final String WEBLOGIC_IMAGE_NAME_DEFAULT = "weblogick8s/test-images/weblogic";
  public static final String WEBLOGIC_IMAGE_TAG_DEFAULT = "12.2.1.4";
  public static final String FMWINFRA_IMAGE_NAME_DEFAULT = "weblogick8s/test-images/fmw-infrastructure";
  public static final String FMWINFRA_IMAGE_TAG_DEFAULT = "12.2.1.4";
  public static final String DB_IMAGE_NAME_DEFAULT = "weblogick8s/test-images/database/enterprise";
  public static final String DB_IMAGE_TAG_DEFAULT = "12.2.0.1-slim";

  // repository to push the domain images, for kind push to kind repo
  // for others push to REPO_REGISTRY if REPO_REGISTRY env var is provided,
  // if its not provided (like local runs) don't push the domain images to any repo
  public static final String DOMAIN_IMAGES_REPO = Optional.ofNullable(KIND_REPO)
      .orElse(System.getenv("TEST_IMAGES_REPO") != null  
      ? System.getenv("TEST_IMAGES_REPO") + "/weblogick8s/" : "");

  // BASE_IMAGES_REPO represents the repository from where all the base WebLogic
  // and InfraStructure images are pulled
  // Default for BASE_IMAGES_REPO is phx.ocir.io
  public static final String BASE_IMAGES_REPO_DEFAULT = "phx.ocir.io";
  public static final String BASE_IMAGES_REPO = Optional.ofNullable(System.getenv("BASE_IMAGES_REPO"))
      .orElse(BASE_IMAGES_REPO_DEFAULT);
  public static final String BASE_IMAGES_REPO_SECRET_NAME = "base-images-repo-secret";
  public static final String BASE_IMAGES_REPO_USERNAME = System.getenv("BASE_IMAGES_REPO_USERNAME");
  public static final String BASE_IMAGES_REPO_PASSWORD = System.getenv("BASE_IMAGES_REPO_PASSWORD");
  public static final String BASE_IMAGES_REPO_EMAIL = System.getenv("BASE_IMAGES_REPO_EMAIL");

  // Get WEBLOGIC_IMAGE_NAME/WEBLOGIC_IMAGE_TAG from env var, 
  // or use default based on BASE_IMAGES_REPO_DEFAULT
  public static final String WEBLOGIC_IMAGE_NAME
          = BASE_IMAGES_REPO + "/" + Optional.ofNullable(System.getenv("WEBLOGIC_IMAGE_NAME"))
      .orElse(WEBLOGIC_IMAGE_NAME_DEFAULT);
  public static final String WEBLOGIC_IMAGE_TAG = Optional.ofNullable(System.getenv("WEBLOGIC_IMAGE_TAG"))
      .orElse(WEBLOGIC_IMAGE_TAG_DEFAULT);

  // Get FMWINFRA_IMAGE_NAME/FMWINFRA_IMAGE_TAG from env var 
  // or use default based on BASE_IMAGES_REPO_DEFAULT
  public static final String FMWINFRA_IMAGE_NAME
      = BASE_IMAGES_REPO + "/" + Optional.ofNullable(System.getenv("FMWINFRA_IMAGE_NAME"))
      .orElse(FMWINFRA_IMAGE_NAME_DEFAULT);
  public static final String FMWINFRA_IMAGE_TAG = Optional.ofNullable(System.getenv("FMWINFRA_IMAGE_TAG"))
      .orElse(FMWINFRA_IMAGE_TAG_DEFAULT);

  // Get DB_IMAGE_NAME/DB_IMAGE_TAG from env var, if its not provided
  // or use default based on BASE_IMAGES_REPO_DEFAULT
  public static final String DB_IMAGE_NAME
      = BASE_IMAGES_REPO + "/" + Optional.ofNullable(System.getenv("DB_IMAGE_NAME"))
      .orElse(DB_IMAGE_NAME_DEFAULT);
  public static final String DB_IMAGE_TAG = Optional.ofNullable(System.getenv("DB_IMAGE_TAG"))
      .orElse(DB_IMAGE_TAG_DEFAULT);

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

  // jenkins constants
  public static final String BUILD_ID = Optional.ofNullable(System.getenv("BUILD_ID"))
      .orElse("");
  public static final String BRANCH_NAME_FROM_JENKINS = Optional.ofNullable(System.getenv("BRANCH"))
      .orElse("");

  public static final String K8S_NODEPORT_HOST = Optional.ofNullable(System.getenv("K8S_NODEPORT_HOST"))
        .orElse(assertDoesNotThrow(() -> InetAddress.getLocalHost().getHostAddress()));
  public static final String K8S_NODEPORT_HOSTNAME = Optional.ofNullable(System.getenv("K8S_NODEPORT_HOSTNAME"))
        .orElse(assertDoesNotThrow(() -> InetAddress.getLocalHost().getHostName()));
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
  public static final String APACHE_IMAGE_NAME_DEFAULT = "weblogick8s/oracle/apache";
  public static final String APACHE_IMAGE_TAG_DEFAULT = "12.2.1.4";

  // Get APACHE_IMAGE_NAME/APACHE_IMAGE_TAG from env var, if it is not 
  // use default image values
  public static final String APACHE_IMAGE_NAME = BASE_IMAGES_REPO + "/"
      + Optional.ofNullable(System.getenv("APACHE_IMAGE_NAME")).orElse(APACHE_IMAGE_NAME_DEFAULT);
  public static final String APACHE_IMAGE_TAG =
      Optional.ofNullable(System.getenv("APACHE_IMAGE_TAG")).orElse(APACHE_IMAGE_TAG_DEFAULT);
  public static final String APACHE_IMAGE = APACHE_IMAGE_NAME + ":" + APACHE_IMAGE_TAG;
  public static final String APACHE_RELEASE_NAME = "apache-release" + BUILD_ID;
  public static final String APACHE_SAMPLE_CHART_DIR = "../kubernetes/samples/charts/apache-webtier";

  // ELK Stack and WebLogic logging exporter constants
  public static final String ELASTICSEARCH_NAME = "elasticsearch";
  public static final String ELK_STACK_VERSION = "7.8.1";
  public static final String FLUENTD_IMAGE_VERSION = Optional.ofNullable(System.getenv("FLUENTD_IMAGE_VERSION"))
      .orElse("v1.3.3-debian-elasticsearch-1.3");
  public static final String ELASTICSEARCH_IMAGE = ELASTICSEARCH_NAME + ":" + ELK_STACK_VERSION;
  public static final String ELASTICSEARCH_HOST = "elasticsearch.default.svc.cluster.local";
  public static final int DEFAULT_LISTEN_PORT = 7100;
  public static final int ELASTICSEARCH_HTTP_PORT = 9200;
  public static final int ELASTICSEARCH_HTTPS_PORT = 9300;
  public static final int DEFAULT_SSL_LISTEN_PORT = 8100;
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
  public static final String MII_AUXILIARY_IMAGE_NAME = DOMAIN_IMAGES_REPO + "mii-ai-image";
  public static final String SKIP_BUILD_IMAGES_IF_EXISTS =
        Optional.ofNullable(System.getenv("SKIP_BUILD_IMAGES_IF_EXISTS")).orElse("false");
  public static final String BUSYBOX_IMAGE = "phx.ocir.io/weblogick8s/test-images/docker/busybox";
  public static final String BUSYBOX_TAG = "1.34.1";

  // Skip the mii/wdt basic image build locally if needed
  public static final String MII_BASIC_IMAGE_TAG =
      Boolean.valueOf(SKIP_BUILD_IMAGES_IF_EXISTS) ? "local" : getDateAndTimeStamp();
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
  public static final String WDT_BASIC_IMAGE_TAG = getDateAndTimeStamp();
  public static final String WDT_BASIC_IMAGE_DOMAINHOME = "/u01/oracle/user_projects/domains/domain1";
  public static final String WDT_IMAGE_DOMAINHOME_BASE_DIR = "/u01/oracle/user_projects/domains";
  public static final String WDT_BASIC_IMAGE_DOMAINTYPE = "wdt";
  public static final String WDT_BASIC_APP_NAME = "sample-app";
  public static final String WDT_TEST_VERSION = Optional.ofNullable(System.getenv(
      "WDT_TEST_VERSION"))
      .orElse("1.9.15");

  //monitoring constants
  public static final String MONITORING_EXPORTER_WEBAPP_VERSION = Optional.ofNullable(System.getenv(
      "MONITORING_EXPORTER_WEBAPP_VERSION"))
      .orElse("2.0");
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

  // Default ISTIO version is 1.10.4
  public static final String ISTIO_VERSION =
        Optional.ofNullable(System.getenv("ISTIO_VERSION")).orElse("1.10.4");

  //MySQL database constants
  public static final String MYSQL_VERSION = "5.6";

  //OKE constants
  public static final boolean OKE_CLUSTER = Boolean.parseBoolean(Optional.ofNullable(System.getenv("OKE_CLUSTER"))
      .orElse("false"));
  public static final String NFS_SERVER = Optional.ofNullable(System.getenv("NFS_SERVER"))
      .orElse("");
  public static final String FSS_DIR = Optional.ofNullable(System.getenv("FSS_DIR"))
      .orElse("");

  //OKD constants
  public static final boolean OKD = Boolean.parseBoolean(Optional.ofNullable(System.getenv("OKD"))
      .orElse("false"));

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

  public static final boolean WEBLOGIC_SLIM =
      WEBLOGIC_IMAGE_TAG.contains("slim") ? true : false;

  public static final String WEBLOGIC_VERSION = "12.2.1.4.0";
  public static final String HTTPS_PROXY = Optional.ofNullable(System.getenv("https_proxy")).orElse("");
}

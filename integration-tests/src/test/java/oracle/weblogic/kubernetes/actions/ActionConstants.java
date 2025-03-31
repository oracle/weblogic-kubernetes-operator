// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions;

import static oracle.weblogic.kubernetes.TestConstants.RESULTS_BASE;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNonEmptySystemProperty;

public interface ActionConstants {

  // Work directory for the integration test suite
  String WORK_DIR = RESULTS_BASE + "/workdir";
  // Integration tests directory
  String ITTESTS_DIR = System.getProperty("user.dir");
  // Directory for resources
  String RESOURCE_DIR = System.getProperty("user.dir") + "/src/test/resources";
  // Directory for all applications
  String APP_DIR = RESOURCE_DIR + "/apps";
  // Directory for all WDT models
  String MODEL_DIR = RESOURCE_DIR + "/wdt-models";
  // Directory for download items
  String DOWNLOAD_DIR = WORK_DIR + "/download";
  // Directory for staging purposes
  String STAGE_DIR = WORK_DIR + "/stage";
  //Directory for archiving purposes
  String ARCHIVE_DIR = STAGE_DIR + "/archive";
  // Directory for WIT build
  String WIT_BUILD_DIR = WORK_DIR + "/wit-build";

  // ------------ WebLogicImageTool action constants -------------
  String WLS = "WLS";
  String DEFAULT_MODEL_IMAGE_NAME = "test-mii-image";
  String DEFAULT_MODEL_IMAGE_TAG  = "v1";

  // ------------ WebLogic Image Tool constants----------------------------
  String WIT = "WIT";
  String WDT = "WDT";

  String WIT_JAVA_HOME = getNonEmptySystemProperty("wko.it.wit.java.home");
  String WIT_DOWNLOAD_URL_DEFAULT
      = "https://github.com/oracle/weblogic-image-tool/releases/latest";
  String WIT_DOWNLOAD_URL
      = getNonEmptySystemProperty("wko.it.wit.download.url", WIT_DOWNLOAD_URL_DEFAULT);
  String WIT_DOWNLOAD_FILENAME_DEFAULT = "imagetool.zip";

  String WDT_DOWNLOAD_URL_BASE =
      "https://github.com/oracle/weblogic-deploy-tooling/releases/download/release-";
  String WDT_DOWNLOAD_URL_DEFAULT =
      "https://github.com/oracle/weblogic-deploy-tooling/releases/latest";
  String WDT_DOWNLOAD_URL =
      getNonEmptySystemProperty("wko.it.wdt.download.url", WDT_DOWNLOAD_URL_DEFAULT);
  //WDT_VERSION is used as the sub-directory identifier when we need to find out
  //from where we can locate weblogic-deploy.zip file. Right now when we install WDT
  //we always put zip file under "latest" sub-directory no matter using "latest" branch or
  //explicit version download URL or custom download URL.
  String WDT_VERSION = "latest";

  String WDT_DOWNLOAD_FILENAME_DEFAULT = "weblogic-deploy.zip";

  String IMAGE_TOOL = WORK_DIR + "/imagetool/bin/imagetool.sh";
  String WDT_ZIP_PATH = DOWNLOAD_DIR + "/" + WDT_DOWNLOAD_FILENAME_DEFAULT;

  String WLE = "WLE";
  String SNAKE = "SNAKE";

  String REMOTECONSOLE = "REMOTECONSOLE";
  String REMOTECONSOLE_VERSION =
      getNonEmptySystemProperty("wko.it.remoteconsole.version", "2.4.7");
  String REMOTECONSOLE_FILE = WORK_DIR + "/backend/console.jar";
  String REMOTECONSOLE_DOWNLOAD_URL_DEFAULT =
      "https://github.com/oracle/weblogic-remote-console/releases/download/v" 
           + REMOTECONSOLE_VERSION + "/";
  String REMOTECONSOLE_DOWNLOAD_URL
      =  getNonEmptySystemProperty("wko.it.remoteconsole.download.url", REMOTECONSOLE_DOWNLOAD_URL_DEFAULT);
  String REMOTECONSOLE_DOWNLOAD_FILENAME_DEFAULT =
        "WebLogic-Remote-Console-" + REMOTECONSOLE_VERSION + "-linux.zip";
  String REMOTECONSOLE_ZIP_PATH = DOWNLOAD_DIR + "/" + REMOTECONSOLE_DOWNLOAD_FILENAME_DEFAULT;

  String WLE_VERSION = getNonEmptySystemProperty("wko.it.wle.version", "latest");
  String WLE_DOWNLOAD_URL_DEFAULT
      = "https://github.com/oracle/weblogic-logging-exporter/releases/" + WLE_VERSION;
  String WLE_DOWNLOAD_URL
      = getNonEmptySystemProperty("wko.it.wle.download.url", WLE_DOWNLOAD_URL_DEFAULT);
  String WLE_DOWNLOAD_FILENAME_DEFAULT =
      getNonEmptySystemProperty("wko.it.wle.file.name", "weblogic-logging-exporter.jar");
  String SNAKE_VERSION =
      getNonEmptySystemProperty("wko.it.snake.version", "1.27");
  String SNAKE_DOWNLOAD_FILENAME_DEFAULT = "snakeyaml-" + SNAKE_VERSION + ".jar";
  String SNAKE_DOWNLOAD_URL_DEFAULT
      = "https://repo1.maven.org/maven2/org/yaml/snakeyaml/" + SNAKE_VERSION + "/" + SNAKE_DOWNLOAD_FILENAME_DEFAULT;
  String SNAKE_DOWNLOAD_URL
      = getNonEmptySystemProperty("wko.it.snake.download.url", SNAKE_DOWNLOAD_URL_DEFAULT);
  String SNAKE_DOWNLOADED_FILENAME = "snakeyaml.jar";

  // ------------ WLDF RBAC constants ------------------------------------------
  String WLDF_CLUSTER_ROLE_NAME = "wldf-weblogic-domain-cluster-role";
  String WLDF_CLUSTER_ROLE_BINDING_NAME = "domain-cluster-rolebinding";
  String WLDF_ROLE_BINDING_NAME = "weblogic-domain-operator-rolebinding";
  String RBAC_CLUSTER_ROLE = "ClusterRole";
  String RBAC_CLUSTER_ROLE_BINDING = "ClusterRoleBinding";
  String RBAC_ROLE_BINDING = "RoleBinding";
  String RBAC_API_VERSION = "rbac.authorization.k8s.io/v1";
  String RBAC_API_GROUP = "rbac.authorization.k8s.io";

  String MONITORING_EXPORTER_DOWNLOAD_URL
      = "https://github.com/oracle/weblogic-monitoring-exporter.git";

  // ------------ Ingress constants----------------------------
  String INGRESS_API_VERSION = "networking.k8s.io/v1";
  String INGRESS_KIND = "Ingress";
}

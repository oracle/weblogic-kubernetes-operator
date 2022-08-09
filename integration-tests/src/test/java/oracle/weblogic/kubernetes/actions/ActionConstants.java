// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions;

import static oracle.weblogic.kubernetes.TestConstants.RESULTS_BASE;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNonEmptySystemProperty;

public interface ActionConstants {

  // Work directory for the integration test suite
  public static final String WORK_DIR = RESULTS_BASE + "/workdir";
  // Integration tests directory
  public static final String ITTESTS_DIR = System.getProperty("user.dir");
  // Directory for resources
  public static final String RESOURCE_DIR
      = System.getProperty("user.dir") + "/src/test/resources";
  // Directory for all applications
  public static final String APP_DIR = RESOURCE_DIR + "/apps";
  // Directory for all WDT models
  public static final String MODEL_DIR = RESOURCE_DIR + "/wdt-models";
  // Directory for download items
  public static final String DOWNLOAD_DIR = WORK_DIR + "/download";
  // Directory for staging purposes
  public static final String STAGE_DIR = WORK_DIR + "/stage";
  //Directory for archiving purposes
  public static final String ARCHIVE_DIR = STAGE_DIR + "/archive";
  // Directory for WIT build
  public static final String WIT_BUILD_DIR = WORK_DIR + "/wit-build";

  // ------------ WebLogicImageTool action constants -------------
  public static final String WLS = "WLS";
  public static final String DEFAULT_MODEL_IMAGE_NAME = "test-mii-image";
  public static final String DEFAULT_MODEL_IMAGE_TAG  = "v1";

  // ------------ WebLogic Image Tool constants----------------------------
  public static final String WIT = "WIT";
  public static final String WDT = "WDT";

  public static final String WIT_JAVA_HOME = getNonEmptySystemProperty("wko.it.wit.java.home");
  public static final String WIT_DOWNLOAD_URL_DEFAULT
      = "https://github.com/oracle/weblogic-image-tool/releases/latest";
  public static final String WIT_DOWNLOAD_URL
      = getNonEmptySystemProperty("wko.it.wit.download.url", WIT_DOWNLOAD_URL_DEFAULT);
  public static final String WIT_DOWNLOAD_FILENAME_DEFAULT = "imagetool.zip";

  public static final String WDT_DOWNLOAD_URL_BASE =
      "https://github.com/oracle/weblogic-deploy-tooling/releases/download/release-";
  public static final String WDT_DOWNLOAD_URL_DEFAULT =
      "https://github.com/oracle/weblogic-deploy-tooling/releases/latest";
  public static final String WDT_DOWNLOAD_URL =
      getNonEmptySystemProperty("wko.it.wdt.download.url", WDT_DOWNLOAD_URL_DEFAULT);
  //WDT_VERSION is used as the sub-directory identifier when we need to find out
  //from where we can locate weblogic-deploy.zip file. Right now when we install WDT
  //we always put zip file under "latest" sub-directory no matter using "latest" branch or
  //explicit version download URL or custom download URL.
  public static final String WDT_VERSION = "latest";

  public static final String WDT_DOWNLOAD_FILENAME_DEFAULT = "weblogic-deploy.zip";

  public static final String IMAGE_TOOL = WORK_DIR + "/imagetool/bin/imagetool.sh";
  public static final String WDT_ZIP_PATH = DOWNLOAD_DIR + "/" + WDT_DOWNLOAD_FILENAME_DEFAULT;

  public static final String WLE = "WLE";
  public static final String SNAKE = "SNAKE";

  public static final String REMOTECONSOLE = "REMOTECONSOLE";
  //remoteconsole jar file after unzipping
  public static final String REMOTECONSOLE_FILE = WORK_DIR + "/console/console.jar";
  public static final String REMOTECONSOLE_DOWNLOAD_URL_DEFAULT =
      "https://github.com/oracle/weblogic-remote-console/releases/latest";
  public static final String REMOTECONSOLE_DOWNLOAD_URL
      =  getNonEmptySystemProperty("wko.it.remoteconsole.download.url", REMOTECONSOLE_DOWNLOAD_URL_DEFAULT);
  public static final String REMOTECONSOLE_VERSION =
      getNonEmptySystemProperty("remoteconsole.version", "1.1.0");
  public static final String REMOTECONSOLE_DOWNLOAD_FILENAME_DEFAULT = "console.zip";
  public static final String REMOTECONSOLE_ZIP_PATH = DOWNLOAD_DIR + "/" + REMOTECONSOLE_DOWNLOAD_FILENAME_DEFAULT;

  public static final String WLE_VERSION = getNonEmptySystemProperty("wko.it.wle.version", "latest");
  public static final String WLE_DOWNLOAD_URL_DEFAULT
      = "https://github.com/oracle/weblogic-logging-exporter/releases/" + WLE_VERSION;
  public static final String WLE_DOWNLOAD_URL
      = getNonEmptySystemProperty("wko.it.wle.download.url", WLE_DOWNLOAD_URL_DEFAULT);
  public static final String WLE_DOWNLOAD_FILENAME_DEFAULT =
      getNonEmptySystemProperty("wko.it.wle.file.name", "weblogic-logging-exporter.jar");
  public static final String SNAKE_VERSION =
      getNonEmptySystemProperty("wko.it.snake.version", "1.27");
  public static final String SNAKE_DOWNLOAD_FILENAME_DEFAULT = "snakeyaml-" + SNAKE_VERSION + ".jar";
  public static final String SNAKE_DOWNLOAD_URL_DEFAULT
      = "https://repo1.maven.org/maven2/org/yaml/snakeyaml/" + SNAKE_VERSION + "/" + SNAKE_DOWNLOAD_FILENAME_DEFAULT;
  public static final String SNAKE_DOWNLOAD_URL
      = getNonEmptySystemProperty("wko.it.snake.download.url", SNAKE_DOWNLOAD_URL_DEFAULT);
  public static final String SNAKE_DOWNLOADED_FILENAME = "snakeyaml.jar";

  // ------------ WLDF RBAC constants ------------------------------------------
  public static final String WLDF_CLUSTER_ROLE_NAME = "wldf-weblogic-domain-cluster-role";
  public static final String WLDF_CLUSTER_ROLE_BINDING_NAME = "domain-cluster-rolebinding";
  public static final String WLDF_ROLE_BINDING_NAME = "weblogic-domain-operator-rolebinding";
  public static final String RBAC_CLUSTER_ROLE = "ClusterRole";
  public static final String RBAC_CLUSTER_ROLE_BINDING = "ClusterRoleBinding";
  public static final String RBAC_ROLE_BINDING = "RoleBinding";
  public static final String RBAC_API_VERSION = "rbac.authorization.k8s.io/v1";
  public static final String RBAC_API_GROUP = "rbac.authorization.k8s.io";

  public static final String MONITORING_EXPORTER_DOWNLOAD_URL 
      = "https://github.com/oracle/weblogic-monitoring-exporter.git";

  // ------------ Ingress constants----------------------------
  public static final String INGRESS_API_VERSION = "networking.k8s.io/v1";
  public static final String INGRESS_KIND = "Ingress";
}

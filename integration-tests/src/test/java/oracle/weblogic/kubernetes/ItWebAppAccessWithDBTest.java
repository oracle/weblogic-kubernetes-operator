// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.openapi.models.V1Pod;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.IT_WEBAPPACCESSNGINX_INGRESS_HTTPS_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_WEBAPPACCESSNGINX_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_WEBAPPACCESSNGINX_INGRESS_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.callTestWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DbUtils.createSqlFileInPod;
import static oracle.weblogic.kubernetes.utils.DbUtils.runMysqlInsidePod;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.createAndVerifyDomain;
import static oracle.weblogic.kubernetes.utils.MySQLDBUtils.createMySQLDB;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verify WebApp can be accessed via NGINX ingress controller if db is installed.
 */
@Tag("oke-gate")
@Tag("kind-parallel")
@IntegrationTest
class ItWebAppAccessWithDBTest {

  // domain constants
  private static final int replicaCount = 2;
  private static int managedServersCount = 2;
  private static String domainNamespace = null;
  private static String domainUid = "dbtestdomain";
  private static NginxParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static List<String> ingressHostList = null;
  private static String ingressIP = null;
  private static final String TEST_WDT_FILE = "/sample-topology.yaml";
  private static final String TEST_IMAGE_NAME = "dbtest-image";
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";
  private static String cluster1Name = "cluster-1";
  private static String cluster2Name = "cluster-2";
  private static String miiImage = null;
  private static String wdtImage = null;
  private static final String SESSMIGR_APP_WAR_NAME = "sessmigr-war";
  private static int managedServerPort = 8001;
  private static Map<String, Integer> clusterNameMsPortMap;
  private static LoggingFacade logger = null;
  private static List<String> clusterNames = new ArrayList<>();
  private static String dbUrl = "";

  /**
   * Install operator and NGINX. Create model in image domain with multiple clusters.
   * Create ingress for the domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  static void initAll(@Namespaces(4) List<String> namespaces) {

    logger = getLogger();

    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);


    logger.info("Get a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);


    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    final String nginxNamespace = namespaces.get(2);


    logger.info("install and verify operator");
    installAndVerifyOperator(opNamespace, domainNamespace);

    if (!OKD) {
      // install and verify NGINX
      nginxHelmParams = installAndVerifyNginx(nginxNamespace,
          IT_WEBAPPACCESSNGINX_INGRESS_HTTP_NODEPORT, IT_WEBAPPACCESSNGINX_INGRESS_HTTPS_NODEPORT,
          NGINX_CHART_VERSION, (OKE_CLUSTER ? null : "NodePort"));
      String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
      ingressIP = getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) != null
          ? getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) : K8S_NODEPORT_HOST;
      logger.info("NGINX service name: {0}", nginxServiceName);
      nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
      if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
        try {
          ingressIP = formatIPv6Host(InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException ex) {
          logger.severe(ex.getLocalizedMessage());
        }
        nodeportshttp = IT_WEBAPPACCESSNGINX_INGRESS_HTTP_HOSTPORT;
      }

      logger.info("NGINX http node port: {0}", nodeportshttp);
    }
    clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(cluster1Name, managedServerPort);
    clusterNameMsPortMap.put(cluster2Name, managedServerPort);
    clusterNames.add(cluster1Name);
    clusterNames.add(cluster2Name);


    //start  MySQL database instance
    if (!WEBLOGIC_IMAGE_TAG.contains("12.2.1.4")) {
      logger.info("Installing database for wls" + WEBLOGIC_IMAGE_TAG);
      assertDoesNotThrow(() -> {
        String dbService = createMySQLDB("mysql", "root", "root123", domainNamespace, null);
        assertNotNull(dbService, "Failed to create database");
        V1Pod pod = getPod(domainNamespace, null, "mysql");
        assertNotNull(pod, "pod is null");
        assertNotNull(pod.getMetadata(), "pod metadata is null");
        createFileInPod(pod.getMetadata().getName(), domainNamespace, "root123");
        runMysqlInsidePod(pod.getMetadata().getName(), domainNamespace, "root123", "/tmp/grant.sql");
        runMysqlInsidePod(pod.getMetadata().getName(), domainNamespace, "root123", "/tmp/create.sql");
        dbUrl = "jdbc:mysql://" + dbService + "." + domainNamespace + ".svc:3306";
      });
    }
  }

  /**
   * Test that if db is started, access to app passes in Weblogic versions
   * Test that if WLS is 12.2.1.4 test passes without db is started.
   * Create domain in Image with app.
   * Verify access to app via nginx.
   */
  @Test
  @DisplayName("Test that if db is  started, access to app successful.")
  void testAccesToWebApp() {

    wdtImage = createAndVerifyDomainInImage();
    logger.info("Create wdt domain and verify that it's running");
    createAndVerifyDomain(wdtImage, domainUid, domainNamespace, "Image", replicaCount,
        false, null, null);

    if (!OKD) {
      ingressHostList
          = createIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMsPortMap,
          true, nginxHelmParams.getIngressClassName(), false, 0);
      logger.info("verify access to Monitoring Exporter");
      if (OKE_CLUSTER_PRIVATEIP) {
        verifyMyAppAccessThroughNginx(ingressHostList.get(0), managedServersCount, ingressIP);
      } else {
        String hostPort = ingressIP  + ":" + nodeportshttp;
        verifyMyAppAccessThroughNginx(ingressHostList.get(0), managedServersCount,
            hostPort);
      }
    }
  }

  public static void verifyMyAppAccessThroughNginx(String nginxHost, int replicaCount, String hostPort) {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // check that NGINX can access the sample apps from all managed servers in the domain
    String curlCmd =
        String.format("curl -g --silent --show-error --noproxy '*' -H 'host: %s' http://%s:%s@%s/"
                + SESSMIGR_APP_WAR_NAME + "/?getCounter",
            nginxHost,
            ADMIN_USERNAME_DEFAULT,
            ADMIN_PASSWORD_DEFAULT,
            hostPort);
    testUntil(withLongRetryPolicy,
        callTestWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, 10),
        logger,
        "Verify NGINX can access the webapp \n"
            + "from all managed servers in the domain via http");
  }

  private static void createFileInPod(String podName, String namespace, String password) throws IOException {

    ExecResult result = assertDoesNotThrow(() -> exec(new String("hostname -i"), true));
    String ip = result.stdout();
    String sqlCommand = "select user();\n"
        + "SELECT host, user FROM mysql.user;\n"
        + "CREATE USER 'root'@'%' IDENTIFIED BY '" + password + "';\n"
        + "GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;\n"
        + "CREATE USER 'root'@'" + ip + "' IDENTIFIED BY '" + password + "';\n"
        + "GRANT ALL PRIVILEGES ON *.* TO 'root'@'" + ip + "' WITH GRANT OPTION;\n"
        + "SELECT host, user FROM mysql.user;";
    String fileName = "grant.sql";
    createSqlFileInPod(podName, namespace, sqlCommand, fileName);
    fileName = "create.sql";
    sqlCommand =
        "CREATE DATABASE " + domainUid + ";\n"
            + "CREATE USER 'wluser1' IDENTIFIED BY 'wlpwd123';\n"
            + "GRANT ALL ON " + domainUid + ".* TO 'wluser1';";
    createSqlFileInPod(podName, namespace, sqlCommand, fileName);
  }

  @AfterAll
  void tearDownAll() {

    // delete mii domain images created for parameterized test
    if (wdtImage != null) {
      deleteImage(miiImage);
    }
  }

  /**
   * Create and verify domain in image from endtoend sample topology with webapp.
   *
   * @return image name
   */
  private static String createAndVerifyDomainInImage() {
    // create image with model files
    logger.info("Create image with model file with  app and verify");
    List<String> appList = new ArrayList<>();
    appList.add(SESSMIGR_APP_NAME);
    int t3ChannelPort = getNextFreePort();

    Properties p = new Properties();
    p.setProperty("ADMIN_USER", ADMIN_USERNAME_DEFAULT);
    p.setProperty("ADMIN_PWD", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("DOMAIN_NAME", domainUid);
    p.setProperty("DBURL", dbUrl);
    p.setProperty("ADMIN_NAME", "admin-server");
    p.setProperty("PRODUCTION_MODE_ENABLED", "true");
    p.setProperty("CLUSTER_NAME", cluster1Name);
    p.setProperty("CLUSTER_TYPE", "DYNAMIC");
    p.setProperty("CONFIGURED_MANAGED_SERVER_COUNT", "2");
    p.setProperty("MANAGED_SERVER_NAME_BASE", "managed-server");
    p.setProperty("T3_CHANNEL_PORT", Integer.toString(t3ChannelPort));
    p.setProperty("T3_PUBLIC_ADDRESS", K8S_NODEPORT_HOST);
    p.setProperty("MANAGED_SERVER_PORT", "8001");
    p.setProperty("SERVER_START_MODE", "prod");
    p.setProperty("ADMIN_PORT", "7001");
    p.setProperty("MYSQL_USER", "wluser1");
    p.setProperty("MYSQL_PWD", "wlpwd123");
    // create a temporary WebLogic domain property file as an input for WDT model file
    File domainPropertiesFile = assertDoesNotThrow(() ->
            File.createTempFile("domain", ".properties", new File(RESULTS_TEMPFILE)),
        "Failed to create domain properties file");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "WDT properties file"),
        "Failed to write domain properties file");

    final List<String> propertyList = Collections.singletonList(domainPropertiesFile.getPath());
    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR
        + TEST_WDT_FILE);
    wdtImage =
        createImageAndVerify(TEST_IMAGE_NAME,
            modelList,
            appList,
            propertyList,
            WEBLOGIC_IMAGE_NAME,
            WEBLOGIC_IMAGE_TAG,
            WLS,
            false,
            domainUid, false);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(wdtImage);
    return wdtImage;
  }
}

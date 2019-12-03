// Copyright (c) 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;

/**
 * Base class which contains common methods to create/shutdown operator and domain. IT tests can
 * extend this class.
 */
public class BaseTest {
  public static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");
  public static final String TESTWEBAPP = "testwebapp";
  public static final String TESTWSAPP = "testwsapp";
  public static final String TESTWSSERVICE = "TestWsApp";

  // property file used to customize operator properties for operator inputs yaml

  public static final String OPERATOR1_YAML = "operator1.yaml";
  public static final String OPERATOR2_YAML = "operator2.yaml";
  public static final String OPERATORBC_YAML = "operator_bc.yaml";
  public static final String OPERATOR_CHAIN_YAML = "operator_chain.yaml";
  public static final String OPERATOR1_ELK_YAML = "operator_elk.yaml";

  // file used to customize domain properties for domain, PV and LB inputs yaml
  public static final String DOMAINONPV_WLST_YAML = "domainonpvwlst.yaml";
  public static final String DOMAINONPV_WDT_YAML = "domainonpvwdt.yaml";
  public static final String DOMAIN_ADMINONLY_YAML = "domainadminonly.yaml";
  public static final String DOMAIN_RECYCLEPOLICY_YAML = "domainrecyclepolicy.yaml";
  public static final String DOMAIN_SAMPLE_DEFAULTS_YAML = "domainsampledefaults.yaml";
  public static final String DOMAININIMAGE_WLST_YAML = "domaininimagewlst.yaml";
  public static final String DOMAININIMAGE_WDT_YAML = "domaininimagewdt.yaml";
  public static final String DOMAINONSHARINGPV_WLST_YAML = "domainonsharingpvwlst.yaml";
  public static final String DOMAINONPV_LOGGINGEXPORTER_YAML = "loggingexpdomainonpvwlst.yaml";

  // property file used to configure constants for integration tests
  public static final String APP_PROPS_FILE = "OperatorIT.properties";

  public static boolean QUICKTEST = true;
  public static boolean FULLTEST;
  public static boolean JENKINS;
  public static boolean SHARED_CLUSTER;
  public static boolean OPENSHIFT;
  public static String WDT_VERSION;
  //currently certified chart versions of Prometheus and Grafana
  public static String PROMETHEUS_CHART_VERSION;
  public static String GRAFANA_CHART_VERSION;
  public static String MONITORING_EXPORTER_VERSION;
  public static boolean INGRESSPERDOMAIN = true;
  protected static String appLocationInPod = "/u01/oracle/apps";
  private static String resultRoot = "";
  private static String pvRoot = "";
  private static String resultDir = "";
  private static String userProjectsDir = "";
  private static String projectRoot = "";
  private static String username = "weblogic";
  private static String password = "welcome1";
  private static int maxIterationsPod = 50;
  private static int waitTimePod = 5;
  private static String leaseId = "";
  private static String branchName = "";
  private static String appLocationOnHost;
  private static Properties appProps;
  private static String weblogicImageTag;
  private static String weblogicImageDevTag;
  private static String weblogicImageName;
  private static String weblogicImageServer;
  private static String domainApiVersion;

  // Set QUICKTEST env var to true to run a small subset of tests.
  // Set SMOKETEST env var to true to run an even smaller subset of tests
  // Set FULLTEST env var to true to run a all the tests, includes quick tests
  // set INGRESSPERDOMAIN to false to create LB's ingress by kubectl yaml file
  static {
    QUICKTEST =
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true");
    
    // if QUICKTEST is false, run all the tests including QUICKTEST
    if (!QUICKTEST) {
      FULLTEST = true;
      QUICKTEST = true;
    }
   
    logger.info("QUICKTEST " + QUICKTEST + " FULLTEST " + FULLTEST);
    if (System.getenv("JENKINS") != null) {
      JENKINS = new Boolean(System.getenv("JENKINS")).booleanValue();
    }
    if (System.getenv("SHARED_CLUSTER") != null) {
      SHARED_CLUSTER = new Boolean(System.getenv("SHARED_CLUSTER")).booleanValue();
    }
    if (System.getenv("OPENSHIFT") != null) {
      OPENSHIFT = new Boolean(System.getenv("OPENSHIFT")).booleanValue();
    }
    if (System.getenv("INGRESSPERDOMAIN") != null) {
      INGRESSPERDOMAIN = new Boolean(System.getenv("INGRESSPERDOMAIN")).booleanValue();
    }
  }

  public static void initialize(String appPropsFile) throws Exception {

    // load app props defined
    appProps = TestUtils.loadProps(appPropsFile);

    // check app props
    String baseDir = appProps.getProperty("baseDir");
    if (baseDir == null) {
      throw new IllegalArgumentException("FAILURE: baseDir is not set");
    }
    username = appProps.getProperty("username", username);
    password = appProps.getProperty("password", password);
    weblogicImageTag =
        System.getenv("IMAGE_TAG_WEBLOGIC") != null
            ? System.getenv("IMAGE_TAG_WEBLOGIC")
            : appProps.getProperty("weblogicImageTag");
    weblogicImageDevTag =
        System.getenv("IMAGE_DEVTAG_WEBLOGIC") != null
            ? System.getenv("IMAGE_DEVTAG_WEBLOGIC")
            : appProps.getProperty("weblogicImageDevTag");
    weblogicImageName =
        System.getenv("IMAGE_NAME_WEBLOGIC") != null
            ? System.getenv("IMAGE_NAME_WEBLOGIC")
            : appProps.getProperty("weblogicImageName");
    weblogicImageServer =
        System.getenv("OCR_SERVER") != null
            ? System.getenv("OCR_SERVER")
            : appProps.getProperty("OCR_SERVER");
    domainApiVersion =
        System.getenv("DOMAIN_API_VERSION") != null
            ? System.getenv("DOMAIN_API_VERSION")
            : appProps.getProperty("DOMAIN_API_VERSION");
    WDT_VERSION =
        System.getenv("WDT_VERSION") != null
            ? System.getenv("WDT_VERSION")
            : appProps.getProperty("WDT_VERSION");
    PROMETHEUS_CHART_VERSION =
            System.getenv("PROMETHEUS_CHART_VERSION") != null
                    ? System.getenv("PROMETHEUS_CHART_VERSION")
                    : appProps.getProperty("PROMETHEUS_CHART_VERSION");
    GRAFANA_CHART_VERSION =
            System.getenv("GRAFANA_CHART_VERSION") != null
                    ? System.getenv("GRAFANA_CHART_VERSION")
                    : appProps.getProperty("GRAFANA_CHART_VERSION");
    MONITORING_EXPORTER_VERSION =
            System.getenv("MONITORING_EXPORTER_VERSION") != null
                    ? System.getenv("MONITORING_EXPORTER_VERSION")
                    : appProps.getProperty("MONITORING_EXPORTER_VERSION");
            
    maxIterationsPod =
        new Integer(appProps.getProperty("maxIterationsPod", "" + maxIterationsPod)).intValue();
    waitTimePod = new Integer(appProps.getProperty("waitTimePod", "" + waitTimePod)).intValue();
    if (System.getenv("RESULT_ROOT") != null) {
      resultRoot = System.getenv("RESULT_ROOT");
    } else {
      resultRoot = baseDir + "/" + System.getProperty("user.name") + "/wl_k8s_test_results";
    }
    if (System.getenv("PV_ROOT") != null) {
      pvRoot = System.getenv("PV_ROOT");
    } else {
      pvRoot = resultRoot;
    }
    if (System.getenv("LEASE_ID") != null) {
      leaseId = System.getenv("LEASE_ID");
    }
    resultDir = resultRoot + "/acceptance_test_tmp";
    userProjectsDir = resultDir + "/user-projects";
    projectRoot = System.getProperty("user.dir") + "/..";

    // BRANCH_NAME var is used in Jenkins job
    if (System.getenv("BRANCH_NAME") != null) {
      branchName = System.getenv("BRANCH_NAME");
    } else {
      branchName = TestUtils.getGitBranchName();
    }

    // for manual/local run, do cleanup
    if (!JENKINS) {

      // delete k8s artifacts created if any, delete PV directories
      ExecResult clnResult = cleanup();
      /* if (clnResult.exitValue() != 0) {
        throw new RuntimeException(
            "FAILED: Command to call cleanup script failed " + clnResult.stderr());
      } */
      logger.info(
          "Command to call cleanup script returned "
              + clnResult.stdout()
              + "\n"
              + clnResult.stderr());
    }

    if (JENKINS) {
      logger.info("Deleting and creating " + resultRoot + "/acceptance_test_tmp");
      TestUtils.exec(
          "/usr/local/packages/aime/ias/run_as_root \"rm -rf "
              + resultRoot
              + "/acceptance_test_tmp\" && "
              + "/usr/local/packages/aime/ias/run_as_root \"mkdir -p "
              + resultRoot
              + "/acceptance_test_tmp\"");
      TestUtils.exec(
          "/usr/local/packages/aime/ias/run_as_root \"chmod 777 "
              + resultRoot
              + "/acceptance_test_tmp\"");
      logger.info("Deleting and Creating " + pvRoot + "/acceptance_test_pv");
      TestUtils.exec(
          "/usr/local/packages/aime/ias/run_as_root \"rm -rf "
              + pvRoot
              + "/acceptance_test_pv\" && "
              + "/usr/local/packages/aime/ias/run_as_root \"mkdir -p "
              + pvRoot
              + "/acceptance_test_pv\"");
      TestUtils.exec(
          "/usr/local/packages/aime/ias/run_as_root \"chmod 777 "
              + pvRoot
              + "/acceptance_test_pv\"");
    }

    // create resultRoot, PVRoot, etc
    Files.createDirectories(Paths.get(resultRoot));
    Files.createDirectories(Paths.get(resultDir));
    Files.createDirectories(Paths.get(userProjectsDir));

    // create file handler
    FileHandler fh = new FileHandler(resultDir + "/java_test_suite.out");
    SimpleFormatter formatter = new SimpleFormatter();
    fh.setFormatter(formatter);
    logger.addHandler(fh);
    logger.info("Adding file handler, logging to file at " + resultDir + "/java_test_suite.out");

    // for manual/local run, create file handler, create PVROOT
    if (!JENKINS && !SHARED_CLUSTER) {
      logger.info("Creating PVROOT " + pvRoot);
      Files.createDirectories(Paths.get(pvRoot));
      ExecResult result = ExecCommand.exec("chmod 777 " + pvRoot);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Couldn't change permissions for PVROOT " + result.stderr());
      }
    }

    appLocationOnHost = getProjectRoot() + "/integration-tests/src/test/resources/apps";

    logger.info("appProps = " + appProps);
    logger.info("maxIterationPod = " + appProps.getProperty("maxIterationsPod"));
    logger.info(
        "maxIterationPod with default= "
            + appProps.getProperty("maxIterationsPod", "" + maxIterationsPod));
    logger.info("RESULT_ROOT =" + resultRoot);
    logger.info("PV_ROOT =" + pvRoot);
    logger.info("userProjectsDir =" + userProjectsDir);
    logger.info("projectRoot =" + projectRoot);
    logger.info("branchName =" + branchName);

    logger.info("Env var RESULT_ROOT " + System.getenv("RESULT_ROOT"));
    logger.info("Env var PV_ROOT " + System.getenv("PV_ROOT"));
    logger.info("Env var K8S_NODEPORT_HOST " + System.getenv("K8S_NODEPORT_HOST"));
    logger.info("Env var IMAGE_NAME_OPERATOR= " + System.getenv("IMAGE_NAME_OPERATOR"));
    logger.info("Env var IMAGE_TAG_OPERATOR " + System.getenv("IMAGE_TAG_OPERATOR"));
    logger.info(
        "Env var IMAGE_PULL_POLICY_OPERATOR " + System.getenv("IMAGE_PULL_POLICY_OPERATOR"));
    logger.info(
        "Env var IMAGE_PULL_SECRET_OPERATOR " + System.getenv("IMAGE_PULL_SECRET_OPERATOR"));
    logger.info(
        "Env var IMAGE_PULL_SECRET_WEBLOGIC " + System.getenv("IMAGE_PULL_SECRET_WEBLOGIC"));
    logger.info("Env var IMAGE_NAME_WEBLOGIC " + System.getenv("IMAGE_NAME_WEBLOGIC"));
    logger.info("Env var IMAGE_TAG_WEBLOGIC " + System.getenv("IMAGE_TAG_WEBLOGIC"));

    logger.info("Env var BRANCH_NAME " + System.getenv("BRANCH_NAME"));
  }

  /**
   * getter method for weblogicImageTag field.
   *
   * @return image tag of the WLS docker images
   */
  public static String getWeblogicImageTag() {
    return weblogicImageTag;
  }
  
  /**
   * getter method for weblogicImageDevTag field.
   *
   * @return image tag of the WLS Dev docker images
   */
  public static String getWeblogicImageDevTag() {
    return weblogicImageDevTag;
  }

  /**
   * getter method for weblogicImageName.
   *
   * @return image name of the WLS docker image
   */
  public static String getWeblogicImageName() {
    return weblogicImageName;
  }

  /**
   * getter method for weblogicImageServer.
   *
   * @return registry name of the WLS container
   */
  public static String getWeblogicImageServer() {
    return weblogicImageServer;
  }

  public static String getDomainApiVersion() {
    return domainApiVersion;
  }

  public static ExecResult cleanup() throws Exception {
    String cmd =
        "export RESULT_ROOT="
            + getResultRoot()
            + " export PV_ROOT="
            + getPvRoot()
            + " export SHARED_CLUSTER=false && "
            + getProjectRoot()
            + "/src/integration-tests/bash/cleanup.sh";
    logger.info("Command to call cleanup script " + cmd);
    return ExecCommand.exec(cmd);
  }

  public static String getResultRoot() {
    return resultRoot;
  }

  public static String getPvRoot() {
    return pvRoot;
  }

  public static String getUserProjectsDir() {
    return userProjectsDir;
  }

  public static String getProjectRoot() {
    return projectRoot;
  }

  public static String getUsername() {
    return username;
  }

  public static String getPassword() {
    return password;
  }

  public static String getResultDir() {
    return resultDir;
  }

  public static int getMaxIterationsPod() {
    return maxIterationsPod;
  }

  public static void setMaxIterationsPod(int iterationsPod) {
    maxIterationsPod = iterationsPod;
  }

  public static int getWaitTimePod() {
    return waitTimePod;
  }

  public static void setWaitTimePod(int timePod) {
    waitTimePod = timePod;
  }

  public static Properties getAppProps() {
    return appProps;
  }

  public static String getLeaseId() {
    return leaseId;
  }

  public static String getBranchName() {
    return branchName;
  }

  public static String getAppLocationInPod() {
    return appLocationInPod;
  }

  public static String getAppLocationOnHost() {
    return appLocationOnHost;
  }

  public static void buildDeployWebServiceApp(Domain domain, String testAppName, String wsName)
      throws Exception {
    String scriptName = "buildDeployWSAndWSClientAppInPod.sh";
    // Build WS and WS client WARs in the admin pod and deploy it from the admin pod to a weblogic
    // target
    TestUtils.buildDeployWebServiceAppInPod(
        domain, testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword(), wsName);
  }

  /**
   * Calls statedump.sh which places k8s logs, descriptions, etc in directory
   * $RESULT_DIR/state-dump-logs and calls archive.sh on RESULT_DIR locally, and on PV_ROOT via a
   * job or pod. Also calls cleanup.sh which does a best-effort delete of acceptance test k8s
   * artifacts, the local test tmp directory, and the potentially remote domain pv directories.
   *
   * @param itClassName - IT class name to be used in the archive file name
   * @throws Exception when errors while running statedump.sh or cleanup.sh scripts or while
   *     renewing the lease for shared cluster run
   */
  public static void tearDown(String itClassName) throws Exception {
    logger.log(
        Level.INFO,
        "TEARDOWN: Starting Test Run TearDown (cleanup and state-dump)."
            + " Note that if the test failed previous to tearDown, "
            + " the error that caused the test failure may be reported "
            + "after the tearDown completes. Note that tearDown itself may report errors,"
            + " but this won't affect the outcome of the test results.");
    StringBuffer cmd =
        new StringBuffer(
            "export RESULT_ROOT=$RESULT_ROOT && export PV_ROOT=$PV_ROOT && export IT_CLASS=");
    cmd.append(itClassName)
        .append(" && export JENKINS_RESULTS_DIR=${WORKSPACE}/logdir/${BUILD_TAG} && ")
        .append(BaseTest.getProjectRoot())
        .append("/integration-tests/src/test/resources/statedump.sh");
    logger.info("Running " + cmd);

    // renew lease before callin statedump.sh
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() == 0) {
      logger.info("Executed statedump.sh " + result.stdout());
    } else {
      logger.info("Execution of statedump.sh failed, " + result.stderr() + "\n" + result.stdout());
    }

    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());

    if (JENKINS || SHARED_CLUSTER) {
      result = cleanup();
      if (result.exitValue() != 0) {
        logger.info("cleanup result =" + result.stdout() + "\n " + result.stderr());
      }
    }

    if (getLeaseId() != "") {
      logger.info("Release the k8s cluster lease");
      TestUtils.releaseLease(getProjectRoot(), getLeaseId());
    }
  }

  /**
   * Call the basic usecases tests.
   *
   * @param domain domain
   * @throws Exception exception
   */
  protected void testBasicUseCases(Domain domain) throws Exception {
    testAdminT3Channel(domain);
    testAdminServerExternalService(domain);
  }

  /**
   * Access Admin REST endpoint using admin node host and node port.
   *
   * @throws Exception exception
   */
  public void testAdminServerExternalService(Domain domain) throws Exception {
    logger.info("Inside testAdminServerExternalService");
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
    domain.verifyAdminServerExternalService(getUsername(), getPassword());
    logger.info("Done - testAdminServerExternalService");
  }

  /**
   * Verify t3channel port by deploying webapp using the port.
   *
   * @throws Exception exception
   */
  public void testAdminT3Channel(Domain domain) throws Exception {
    logger.info("Inside testAdminT3Channel");
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
    Map<String, Object> domainMap = domain.getDomainMap();
    // check if the property is set to true
    Boolean exposeAdmint3Channel = (Boolean) domainMap.get("exposeAdminT3Channel");

    if (exposeAdmint3Channel != null && exposeAdmint3Channel.booleanValue()) {
      ExecResult result =
          TestUtils.kubectlexecNoCheck(
              domain.getDomainUid() + ("-") + domainMap.get("adminServerName"),
              "" + domainMap.get("namespace"),
              " -- mkdir -p " + appLocationInPod);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command to create directory "
                + appLocationInPod
                + " in the pod failed, returned "
                + result.stderr()
                + " "
                + result.stdout());
      }

      domain.deployWebAppViaWlst(
          TESTWEBAPP,
          getProjectRoot() + "/src/integration-tests/apps/testwebapp.war",
          appLocationInPod,
          getUsername(),
          getPassword());
      domain.verifyWebAppLoadBalancing(TESTWEBAPP);

      /* The below check is done for domain-home-in-image domains, it needs 12.2.1.3 patched image
       * otherwise managed servers will see unicast errors after app deployment and run as standalone servers,
       * not in cluster.
       * Here is the error message
       * <Jan 18, 2019 8:54:16,214 PM GMT> <Error> <Kernel> <BEA-000802> <ExecuteRequest failed
       * java.lang.AssertionError: LocalGroup should atleast have the local server!.
       * java.lang.AssertionError: LocalGroup should atleast have the local server!
       *    at weblogic.cluster.messaging.internal.GroupImpl.send(GroupImpl.java:176)
       *    at weblogic.cluster.messaging.internal.server.UnicastFragmentSocket.send(UnicastFragmentSocket.java:97)
       *    at weblogic.cluster.FragmentSocketWrapper.send(FragmentSocketWrapper.java:84)
       *    at weblogic.cluster.UnicastSender.send(UnicastSender.java:53)
       *    at weblogic.cluster.UnicastSender.send(UnicastSender.java:21)
       *    Truncated. see log file for complete stacktrace
       */

      if (domainMap.containsKey("domainHomeImageBase")) {
        if (domainMap.get("initialManagedServerReplicas") != null
            && ((Integer) domainMap.get("initialManagedServerReplicas")) >= 1) {

          result =
              ExecCommand.exec(
                  "kubectl logs "
                      + domain.getDomainUid()
                      + ("-")
                      + domainMap.get("managedServerNameBase")
                      + "1 -n "
                      + domainMap.get("namespace")
                      + " | grep BEA-000802");
          if (result.exitValue() == 0) {
            throw new RuntimeException(
                "FAILURE: Managed Servers are not part of the cluster, failing with "
                    + result.stdout()
                    + ". \n Make sure WebLogic Server 12.2.1.3.0 with patch 29135930 applied is used.");
          }
        }
      }

    } else {
      logger.info("exposeAdminT3Channel is false, can not test t3ChannelPort");
    }

    logger.info("Done - testAdminT3Channel");
  }

  /**
   * Verify t3channel port by a JMS connection.
   * This method is not used. See OWLS-76081
   * @throws Exception exception
   */
  public void testAdminT3ChannelWithJms(Domain domain) throws Exception {
    logger.info("Inside testAdminT3ChannelWithJms");
    ConnectionFactory cf = domain.createJmsConnectionFactory();
    final Connection c = cf.createConnection();
    logger.info("Connection created successfully before cycle.");
    domain.shutdownUsingServerStartPolicy();
    domain.restartUsingServerStartPolicy();
    Connection d = cf.createConnection();
    logger.info("Connection created successfully after cycle");
    d.close();
    logger.info("Done - testAdminT3ChannelWithJms");
  }

  /**
   * Verify Load Balancing by deploying and invoking webservicebapp.
   *
   * @param domain - domain where the app will be tested
   * @throws Exception exception reported as a failure to build, deploy or verify load balancing for
   *     Web Service app
   */
  public void testWsLoadBalancing(Domain domain) throws Exception {
    logger.info("Inside testWsLoadBalancing");
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
    buildDeployWebServiceApp(domain, TESTWSAPP, TESTWSSERVICE);

    // invoke webservice via servlet client
    domain.verifyWebAppLoadBalancing(TESTWSSERVICE + "Servlet");
    logger.info("Done - testWsLoadBalancing");
  }

  /**
   * use default cluster service port 8011.
   *
   * @param operator operator
   * @param domain domain
   * @throws Exception exception
   */
  public void testDomainLifecyle(Operator operator, Domain domain) throws Exception {
    testDomainLifecyle(operator, domain, 8011);
  }

  /**
   * Restarting the domain should not have any impact on Operator managing the domain, web app load
   * balancing and node port service.
   *
   * @throws Exception exception
   */
  public void testDomainLifecyle(Operator operator, Domain domain, int port) throws Exception {
    logger.info("Inside testDomainLifecyle");
    domain.destroy();
    domain.create();
    operator.verifyExternalRestService();
    operator.verifyDomainExists(domain.getDomainUid());
    domain.verifyDomainCreated();
    // if domain created with domain home in image, re-deploy the webapp and verify load balancing
    if (domain.getDomainMap().containsKey("domainHomeImageBase")) {
      testAdminT3Channel(domain);
    } else {
      domain.verifyWebAppLoadBalancing(TESTWEBAPP);
    }

    // intermittent failure, see OWLS-73416
    // testWsLoadBalancing(domain);
    domain.verifyAdminServerExternalService(getUsername(), getPassword());
    domain.verifyHasClusterServiceChannelPort("TCP", port, TESTWEBAPP + "/");
    logger.info("Done - testDomainLifecyle");
  }

  /**
   * Scale the cluster up/down using Operator REST endpoint, load balancing should adjust
   * accordingly.
   *
   * @throws Exception exception
   */
  public void testClusterScaling(Operator operator, Domain domain) throws Exception {
    logger.info("Inside testClusterScaling");
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainUid = domain.getDomainUid();
    final String domainNS = domainMap.get("namespace").toString();
    String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    int replicas = 3;
    String podName = domain.getDomainUid() + "-" + managedServerNameBase + replicas;
    final String clusterName = domainMap.get("clusterName").toString();

    logger.info(
        "Scale domain " + domain.getDomainUid() + " Up to " + replicas + " managed servers");
    operator.scale(domainUid, domainMap.get("clusterName").toString(), replicas);

    logger.info("Checking if managed pod(" + podName + ") is Running");
    TestUtils.checkPodCreated(podName, domainNS);

    logger.info("Checking if managed server (" + podName + ") is Running");
    TestUtils.checkPodReady(podName, domainNS);

    logger.info("Checking if managed service(" + podName + ") is created");
    TestUtils.checkServiceCreated(podName, domainNS);

    int replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCnt != replicas) {
      throw new RuntimeException(
          "FAILURE: Cluster replica doesn't match with scaled up size "
              + replicaCnt
              + "/"
              + replicas);
    }

    domain.verifyWebAppLoadBalancing(TESTWEBAPP);

    replicas = 2;
    podName = domainUid + "-" + managedServerNameBase + (replicas + 1);
    logger.info("Scale down to " + replicas + " managed servers");
    operator.scale(domainUid, clusterName, replicas);

    logger.info("Checking if managed pod(" + podName + ") is deleted");
    TestUtils.checkPodDeleted(podName, domainNS);

    replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCnt != replicas) {
      throw new RuntimeException(
          "FAILURE: Cluster replica doesn't match with scaled down size "
              + replicaCnt
              + "/"
              + replicas);
    }

    domain.verifyWebAppLoadBalancing(TESTWEBAPP);

    logger.info("Done - testClusterScaling");
  }

  /**
   * Scale the cluster up using Weblogic WLDF scaling.
   *
   * @throws Exception exception
   */
  public void testWldfScaling(Operator operator, Domain domain) throws Exception {
    logger.info("Inside testWldfScaling");
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainUid = domain.getDomainUid();
    String domainNS = (String) domainMap.get("namespace");
    String adminServerName = (String) domainMap.get("adminServerName");
    String adminPodName = domainUid + "-" + adminServerName;
    String domainName = (String) domainMap.get("domainName");

    copyScalingScriptToPod(domainUid, adminPodName, domainNS);
    TestUtils.createRbacPoliciesForWldfScaling();

    // deploy opensessionapp
    domain.deployWebAppViaWlst(
        "opensessionapp",
        getProjectRoot() + "/src/integration-tests/apps/opensessionapp.war",
        appLocationInPod,
        getUsername(),
        getPassword());

    TestUtils.createWldfModule(
        adminPodName, domainNS, ((Integer) domainMap.get("t3ChannelPort")).intValue());

    String clusterName = domainMap.get("clusterName").toString();
    int replicaCntBeforeScaleup = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    logger.info("replica count before scaleup " + replicaCntBeforeScaleup);

    logger.info("Scale domain " + domainUid + " by calling the webapp");

    int replicas = 3;
    callWebAppAndVerifyScaling(domain, replicas);

    int replicaCntAfterScaleup = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCntAfterScaleup <= replicaCntBeforeScaleup) {
      throw new RuntimeException(
          "FAILURE: Cluster replica count has not increased after scaling up,"
              + " replicaCntBeforeScaleup/replicaCntAfterScaleup "
              + replicaCntBeforeScaleup
              + "/"
              + replicaCntAfterScaleup);
    }

    logger.info("Done - testWldfScaling");
  }

  /**
   * Restarting Operator should not impact the running domain.
   *
   * @throws Exception exception
   */
  public void testOperatorLifecycle(Operator operator, Domain domain) throws Exception {
    logger.info("Inside testOperatorLifecycle");
    operator.destroy();
    operator.create();
    operator.verifyExternalRestService();
    operator.verifyDomainExists(domain.getDomainUid());
    domain.verifyDomainCreated();
    logger.info("Done - testOperatorLifecycle");
  }

  protected void logTestBegin(String testName) throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN " + testName);
    // renew lease at the beginning for every test method, leaseId is set only for shared cluster
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
  }

  private void copyScalingScriptToPod(String domainUid, String podName, String domainNS)
      throws Exception {

    String pvDir = BaseTest.getPvRoot() + "/acceptance_test_pv/persistentVolume-" + domainUid;
    String scriptsDir = pvDir + "/domains/" + domainUid + "/bin/scripts";

    // create scripts dir under domain pv
    TestUtils.createDirUnderDomainPV(scriptsDir);
    if (OPENSHIFT) {
      Files.copy(Paths.get(getProjectRoot() + "/src/scripts/scaling/scalingAction.sh"),
          Paths.get(scriptsDir + "/scalingAction.sh"), StandardCopyOption.REPLACE_EXISTING);
    } else {
      // workaround for the issue with not allowing .. in the host-path in krun.sh
      Files.copy(Paths.get(getProjectRoot() + "/src/scripts/scaling/scalingAction.sh"),
          Paths.get(getResultDir() + "/scalingAction.sh"), StandardCopyOption.REPLACE_EXISTING);
      // copy script to pod
      String cpUsingKrunCmd = getProjectRoot() + "/src/integration-tests/bash/krun.sh -m "
          + getResultDir() + ":/tmpdir -m " + pvDir
          + ":/pvdir -c 'cp -f /tmpdir/scalingAction.sh /pvdir/domains/domainonpvwdt/bin/scripts' -n "
          + domainNS;
      TestUtils.exec(cpUsingKrunCmd, true);
    }
  }

  private void callWebAppAndVerifyScaling(Domain domain, int replicas) throws Exception {
    Map<String, Object> domainMap = domain.getDomainMap();
    final String domainNs = domainMap.get("namespace").toString();
    final String domainUid = domain.getDomainUid();
    final String clusterName = domainMap.get("clusterName").toString();

    // call opensessionapp
    domain.callWebAppAndVerifyLoadBalancing("opensessionapp", false);
    logger.info("Sleeping for 30 seconds for scaleup");
    Thread.sleep(30 * 1000);

    int replicaCntAfterScaleup = TestUtils.getClusterReplicas(domainUid, clusterName, domainNs);
    String managedServerNameBase = (String) domainMap.get("managedServerNameBase");
    for (int i = replicas; i <= replicaCntAfterScaleup; i++) {
      String podName = domain.getDomainUid() + "-" + managedServerNameBase + i;

      logger.info("Checking if managed pod(" + podName + ") is Running");
      TestUtils.checkPodCreated(podName, domainNs);

      logger.info("Checking if managed server (" + podName + ") is Running");
      TestUtils.checkPodReady(podName, domainNs);

      logger.info("Checking if managed service(" + podName + ") is created");
      TestUtils.checkServiceCreated(podName, domainNs);
    }
  }
}

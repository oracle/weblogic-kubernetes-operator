// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import javax.jms.ConnectionFactory;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import oracle.kubernetes.operator.BaseTest;
import org.yaml.snakeyaml.Yaml;

/** Domain class with all the utility methods for a Domain. */
public class Domain {
  public static final String CREATE_DOMAIN_JOB_MESSAGE =
      "Domain base_domain was created and will be started by the WebLogic Kubernetes Operator";

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  private Map<String, Object> domainMap;
  private Map<String, Object> pvMap;

  // attributes from domain properties
  private String domainUid = "";
  // default values as in create-weblogic-domain-inputs.yaml, generated yaml file will have the
  // customized property values
  private String domainNS;
  private String adminServerName;
  private String managedServerNameBase;
  private int initialManagedServerReplicas;
  private int configuredManagedServerCount;
  private boolean exposeAdminT3Channel;
  private boolean exposeAdminNodePort;
  private int t3ChannelPort;
  private String clusterName;
  private String clusterType;
  private String serverStartPolicy;
  private String weblogicDomainStorageReclaimPolicy;
  private String weblogicDomainStorageSize;
  private String loadBalancer = "TRAEFIK";
  private int loadBalancerWebPort = 30305;
  private String userProjectsDir = "";
  private String projectRoot = "";
  private boolean ingressPerDomain = true;

  private String createDomainScript = "";
  private String inputTemplateFile = "";
  private String generatedInputYamlFile;

  private static int maxIterations = BaseTest.getMaxIterationsPod(); // 50 * 5 = 250 seconds
  private static int waitTime = BaseTest.getWaitTimePod();

  private boolean voyager;
  // LB_TYPE is an evn var. Set to "VOYAGER" to use it as loadBalancer
  private static String LB_TYPE;
  // set INGRESSPERDOMAIN to false to create LB's ingress by kubectl yaml file
  private static boolean INGRESSPERDOMAIN = true;

  public Domain(String inputYaml) throws Exception {
    // read input domain yaml to test
    this(TestUtils.loadYaml(inputYaml));
  }

  public Domain(Map<String, Object> inputDomainMap) throws Exception {
    initialize(inputDomainMap);
    createPV();
    createSecret();
    generateInputYaml();
    callCreateDomainScript(userProjectsDir);
    createLoadBalancer();
  }

  /**
   * Verifies the required pods are created, services are created and the servers are ready.
   *
   * @throws Exception
   */
  public void verifyDomainCreated() throws Exception {
    StringBuffer command = new StringBuffer();
    command.append("kubectl get domain ").append(domainUid).append(" -n ").append(domainNS);
    ExecResult result = ExecCommand.exec(command.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILED: command to get domain " + command + " failed with " + result.stderr());
    }
    if (!result.stdout().contains(domainUid))
      throw new RuntimeException("FAILURE: domain not found, exiting!");

    verifyPodsCreated();
    verifyServicesCreated();
    verifyServersReady();
  }

  /**
   * verify pods are created
   *
   * @throws Exception
   */
  public void verifyPodsCreated() throws Exception {
    // check admin pod
    logger.info("Checking if admin pod(" + domainUid + "-" + adminServerName + ") is Running");
    TestUtils.checkPodCreated(domainUid + "-" + adminServerName, domainNS);

    if (domainMap.get("serverStartPolicy") == null
        || (domainMap.get("serverStartPolicy") != null
            && !domainMap.get("serverStartPolicy").toString().trim().equals("ADMIN_ONLY"))) {
      // check managed server pods
      for (int i = 1; i <= initialManagedServerReplicas; i++) {
        logger.info(
            "Checking if managed pod("
                + domainUid
                + "-"
                + managedServerNameBase
                + i
                + ") is Running");
        TestUtils.checkPodCreated(domainUid + "-" + managedServerNameBase + i, domainNS);
      }
    }
  }

  /**
   * verify services are created
   *
   * @throws Exception
   */
  public void verifyServicesCreated() throws Exception {
    // check admin service
    logger.info("Checking if admin service(" + domainUid + "-" + adminServerName + ") is created");
    TestUtils.checkServiceCreated(domainUid + "-" + adminServerName, domainNS);

    if (exposeAdminT3Channel) {
      logger.info(
          "Checking if admin t3 channel service("
              + domainUid
              + "-"
              + adminServerName
              + "-external) is created");
      TestUtils.checkServiceCreated(domainUid + "-" + adminServerName + "-external", domainNS);
    }
    if (domainMap.get("serverStartPolicy") == null
        || (domainMap.get("serverStartPolicy") != null
            && !domainMap.get("serverStartPolicy").toString().trim().equals("ADMIN_ONLY"))) {
      // check managed server services
      for (int i = 1; i <= initialManagedServerReplicas; i++) {
        logger.info(
            "Checking if managed service("
                + domainUid
                + "-"
                + managedServerNameBase
                + i
                + ") is created");
        TestUtils.checkServiceCreated(domainUid + "-" + managedServerNameBase + i, domainNS);
      }
    }
  }

  /**
   * verify servers are ready
   *
   * @throws Exception
   */
  public void verifyServersReady() throws Exception {
    // check admin pod
    logger.info("Checking if admin server is Running");
    TestUtils.checkPodReady(domainUid + "-" + adminServerName, domainNS);
    if (domainMap.get("serverStartPolicy") == null
        || (domainMap.get("serverStartPolicy") != null
            && !domainMap.get("serverStartPolicy").toString().trim().equals("ADMIN_ONLY"))) {

      // check managed server pods
      for (int i = 1; i <= initialManagedServerReplicas; i++) {
        logger.info("Checking if managed server (" + managedServerNameBase + i + ") is Running");
        TestUtils.checkPodReady(domainUid + "-" + managedServerNameBase + i, domainNS);
      }
    }
    // check no additional servers are started
    if (domainMap.get("serverStartPolicy").toString().trim().equals("ADMIN_ONLY")) {
      initialManagedServerReplicas = 0;
    }
    String additionalManagedServer =
        domainUid + "-" + managedServerNameBase + (initialManagedServerReplicas + 1);
    logger.info(
        "Checking if managed server " + additionalManagedServer + " is started, it should not be");
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pod ").append(additionalManagedServer).append(" -n ").append(domainNS);
    ExecResult result = ExecCommand.exec(cmd.toString());

    if (result.exitValue() == 0 && result.stdout().contains("1/1")) {
      throw new RuntimeException(
          "FAILURE: Managed Server "
              + additionalManagedServer
              + " is started, but its not expected.");
    } else {
      logger.info(additionalManagedServer + " is not running, which is expected behaviour");
    }

    // check logs are written on PV if logHomeOnPV is true for domain-home-in-image case
    if ((domainMap.containsKey("domainHomeImageBase")
        && domainMap.containsKey("logHomeOnPV")
        && ((Boolean) domainMap.get("logHomeOnPV")).booleanValue())) {
      logger.info("logHomeOnPV is true, checking if logs are written on PV");
      cmd = new StringBuffer();
      cmd.append("kubectl -n ")
          .append(domainNS)
          .append(" exec -it ")
          .append(domainUid)
          .append("-")
          .append(adminServerName)
          .append(" -- ls /shared/logs/")
          .append(domainUid)
          .append("/")
          .append(adminServerName)
          .append(".log");

      result = ExecCommand.exec(cmd.toString());

      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: logHomeOnPV is true, but logs are not written at /shared/logs/"
                + domainUid
                + " inside the pod");
      } else {
        logger.info("Logs are written at /shared/logs/" + domainUid + " inside the pod");
      }
    }
  }

  /**
   * verify nodeport by accessing admin REST endpoint
   *
   * @param username
   * @param password
   * @throws Exception
   */
  public void verifyAdminServerExternalService(String username, String password) throws Exception {

    // logger.info("Inside verifyAdminServerExternalService");
    if (exposeAdminNodePort) {
      String nodePortHost = getHostNameForCurl();
      String nodePort = getNodePort();
      logger.info("nodePortHost " + nodePortHost + " nodePort " + nodePort);

      StringBuffer cmd = new StringBuffer();
      cmd.append("curl --silent --show-error --noproxy ")
          .append(nodePortHost)
          .append(" http://")
          .append(nodePortHost)
          .append(":")
          .append(nodePort)
          .append("/management/weblogic/latest/serverRuntime")
          .append(" --user ")
          .append(username)
          .append(":")
          .append(password)
          .append(" -H X-Requested-By:Integration-Test --write-out %{http_code} -o /dev/null");
      logger.info("cmd for curl " + cmd);
      ExecResult result = ExecCommand.exec(cmd.toString());
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command " + cmd + " failed, returned " + result.stderr());
      }
      String output = result.stdout().trim();
      logger.info("output " + output);
      if (!output.equals("200")) {
        throw new RuntimeException(
            "FAILURE: accessing admin server REST endpoint did not return 200 status code, "
                + output);
      }
    } else {
      logger.info("exposeAdminNodePort is false, can not test adminNodePort");
    }
  }

  /**
   * Verify that we have the channel set for the cluster
   *
   * @param protocol
   * @param port
   * @param path
   * @throws Exception
   */
  public void verifyHasClusterServiceChannelPort(String protocol, int port, String path)
      throws Exception {

    /* Make sure the service exists in k8s */
    if (!TestUtils.checkHasServiceChannelPort(
        this.getDomainUid() + "-cluster-" + this.clusterName, domainNS, protocol, port)) {
      throw new RuntimeException(
          "FAILURE: Cannot find channel port in cluster, but expecting one: "
              + port
              + "/"
              + protocol);
    }

    /*
     * Construct a curl command that will be run via kubectl exec on the admin server
     */
    StringBuffer curlCmd =
        new StringBuffer(
            "kubectl exec " + this.getDomainUid() + "-" + this.adminServerName + " /usr/bin/curl ");

    /*
     * Make sure we can reach the port,
     * first via each managed server URL
     */
    for (int i = 1; i <= TestUtils.getClusterReplicas(domainUid, clusterName, domainNS); i++) {
      StringBuffer serverAppUrl = new StringBuffer("http://");
      serverAppUrl
          .append(this.getDomainUid() + "-" + managedServerNameBase + i)
          .append(":")
          .append(port)
          .append("/");
      serverAppUrl.append(path);

      callWebAppAndWaitTillReady(
          new StringBuffer(curlCmd.toString())
              .append(serverAppUrl.toString())
              .append(" -- --write-out %{http_code} -o /dev/null")
              .toString());
    }

    /*
     * Make sure we can reach the port,
     * second via cluster URL which should round robin through each managed server.
     * Use the callWebAppAndCheckForServerNameInResponse method with verifyLoadBalancing
     * enabled to verify each managed server is responding.
     */
    StringBuffer clusterAppUrl = new StringBuffer("http://");
    clusterAppUrl
        .append(this.getDomainUid() + "-cluster-" + this.clusterName)
        .append(":")
        .append(port)
        .append("/");
    clusterAppUrl.append(path);

    // execute curl and look for each managed server name in response
    callWebAppAndCheckForServerNameInResponse(
        new StringBuffer(curlCmd.toString()).append(clusterAppUrl.toString()).toString(), true);
  }

  /**
   * deploy webapp using nodehost and nodeport
   *
   * @throws Exception
   */
  public void deployWebAppViaREST(
      String webappName, String webappLocation, String username, String password) throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append("curl --noproxy '*' --silent  --user ")
        .append(username)
        .append(":")
        .append(password)
        .append(" -H X-Requested-By:MyClient -H Accept:application/json")
        .append(" -H Content-Type:multipart/form-data -F \"model={ name: '")
        .append(webappName)
        .append("', targets: [ { identity: [ clusters, '")
        .append(clusterName)
        .append("' ] } ] }\" -F \"sourcePath=@")
        .append(webappLocation)
        .append("\" -H \"Prefer:respond-async\" -X POST http://")
        .append(getNodeHost())
        .append(":")
        .append(getNodePort())
        .append("/management/weblogic/latest/edit/appDeployments")
        .append(" --write-out %{http_code} -o /dev/null");
    logger.fine("Command to deploy webapp " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    String output = result.stdout().trim();
    if (!output.contains("202")) {
      throw new RuntimeException("FAILURE: Webapp deployment failed with response code " + output);
    }
  }
  /**
   * deploy webapp using t3 channel port for wlst
   *
   * @param webappName
   * @param webappLocation
   * @param username
   * @param password
   * @throws Exception
   */
  public void deployWebAppViaWLST(
      String webappName,
      String webappLocation,
      String appLocationInPod,
      String username,
      String password)
      throws Exception {
    String adminPod = domainUid + "-" + adminServerName;

    TestUtils.copyFileViaCat(
        webappLocation, appLocationInPod + "/" + webappName + ".war", adminPod, domainNS);

    TestUtils.copyFileViaCat(
        projectRoot + "/integration-tests/src/test/resources/deploywebapp.py",
        appLocationInPod + "/deploywebapp.py",
        adminPod,
        domainNS);

    TestUtils.copyFileViaCat(
        projectRoot + "/integration-tests/src/test/resources/callpyscript.sh",
        appLocationInPod + "/callpyscript.sh",
        adminPod,
        domainNS);

    callShellScriptByExecToPod(username, password, webappName, appLocationInPod);
  }

  /**
   * Creates a Connection Factory using JMS.
   *
   * @return connection facotry.
   * @throws Exception
   */
  public ConnectionFactory createJMSConnectionFactory() throws Exception {
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "weblogic.jndi.WLInitialContextFactory");
    env.put(Context.PROVIDER_URL, "t3://" + TestUtils.getHostName() + ":" + t3ChannelPort);
    logger.info("Creating JNDI context with URL " + env.get(Context.PROVIDER_URL));
    InitialContext ctx = new InitialContext(env);
    QueueConnection qcc = null;
    logger.info("Getting JMS Connection Factory");
    QueueConnectionFactory cf =
        (QueueConnectionFactory) ctx.lookup("weblogic.jms.ConnectionFactory");
    logger.info("Connection Factory created successfully");
    return cf;
  }

  /**
   * Test http load balancing using loadBalancerWebPort
   *
   * @param webappName
   * @throws Exception
   */
  public void verifyWebAppLoadBalancing(String webappName) throws Exception {
    // webapp is deployed with t3channelport, so check if that is true
    if (exposeAdminT3Channel) {
      callWebAppAndVerifyLoadBalancing(webappName, true);
    } else {
      logger.info(
          "webapp is not deployed as exposeAdminT3Channel is false, can not verify loadbalancing");
    }
  }
  /**
   * call webapp and verify load balancing by checking server name in the response
   *
   * @param webappName
   * @param verifyLoadBalance
   * @throws Exception
   */
  public void callWebAppAndVerifyLoadBalancing(String webappName, boolean verifyLoadBalance)
      throws Exception {
    if (!loadBalancer.equals("NONE")) {
      // url
      StringBuffer testAppUrl = new StringBuffer("http://");
      testAppUrl.append(getHostNameForCurl()).append(":").append(loadBalancerWebPort).append("/");
      if (loadBalancer.equals("APACHE")) {
        testAppUrl.append("weblogic/");
      }
      testAppUrl.append(webappName).append("/");
      // curl cmd to call webapp
      StringBuffer curlCmd = new StringBuffer("curl --silent ");
      curlCmd
          .append(" -H 'host: ")
          .append(domainUid)
          .append(".org' ")
          .append(testAppUrl.toString());

      // curl cmd to get response code
      StringBuffer curlCmdResCode = new StringBuffer(curlCmd.toString());
      curlCmdResCode.append(" --write-out %{http_code} -o /dev/null");

      logger.info("Curl cmd with response code " + curlCmdResCode);
      logger.info("Curl cmd " + curlCmd);

      // call webapp iteratively till its deployed/ready
      callWebAppAndWaitTillReady(curlCmdResCode.toString());

      // execute curl and look for the managed server name in response
      callWebAppAndCheckForServerNameInResponse(curlCmd.toString(), verifyLoadBalance);
      // logger.info("curlCmd "+curlCmd);
    }
  }

  /**
   * create domain crd
   *
   * @throws Exception
   */
  public void create() throws Exception {
    StringBuffer cmd = new StringBuffer("kubectl create -f ");
    cmd.append(userProjectsDir)
        .append("/weblogic-domains/")
        .append(domainUid)
        .append("/domain.yaml");
    logger.info("Running " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command "
              + cmd
              + " failed, returned "
              + result.stdout()
              + "\n"
              + result.stderr());
    }
    String outputStr = result.stdout().trim();
    logger.info("Command returned " + outputStr);

    verifyDomainCreated();
  }

  /**
   * delete domain crd using yaml
   *
   * @throws Exception
   */
  public void destroy() throws Exception {
    int replicas = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    StringBuffer cmd = new StringBuffer("kubectl delete -f ");
    cmd.append(userProjectsDir)
        .append("/weblogic-domains/")
        .append(domainUid)
        .append("/domain.yaml");
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    String output = result.stdout().trim();
    logger.info("command to delete domain " + cmd + " \n returned " + output);
    verifyDomainDeleted(replicas);
  }

  /**
   * delete domain using domain name
   *
   * @throws Exception
   */
  public void shutdown() throws Exception {
    int replicas = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    String cmd = "kubectl delete domain " + domainUid + " -n " + domainNS;
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    String output = result.stdout().trim();
    logger.info("command to delete domain " + cmd + " \n returned " + output);
    verifyDomainDeleted(replicas);
  }

  /**
   * shutdown domain by setting serverStartPolicy to NEVER
   *
   * @throws Exception
   */
  public void shutdownUsingServerStartPolicy() throws Exception {
    int replicas = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    String cmd =
        "kubectl patch domain "
            + domainUid
            + " -n "
            + domainNS
            + " -p '{\"spec\":{\"serverStartPolicy\":\"NEVER\"}}' --type merge";
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    String output = result.stdout().trim();
    logger.info("command to shutdown domain " + cmd + " \n returned " + output);
    verifyServerPodsDeleted(replicas);
  }

  /**
   * restart domain by setting serverStartPolicy to IF_NEEDED
   *
   * @throws Exception
   */
  public void restartUsingServerStartPolicy() throws Exception {
    String cmd =
        "kubectl patch domain "
            + domainUid
            + " -n "
            + domainNS
            + " -p '{\"spec\":{\"serverStartPolicy\":\"IF_NEEDED\"}}' --type merge";
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    String output = result.stdout().trim();
    logger.info("command to restart domain " + cmd + " \n returned " + output);
    verifyPodsCreated();
    verifyServersReady();
  }
  /**
   * verify domain is deleted
   *
   * @param replicas
   * @throws Exception
   */
  public void verifyDomainDeleted(int replicas) throws Exception {
    logger.info("Inside verifyDomainDeleted, replicas " + replicas);
    TestUtils.checkDomainDeleted(domainUid, domainNS);
    verifyServerPodsDeleted(replicas);
  }

  /**
   * verify server pods are deleted
   *
   * @param replicas
   * @throws Exception
   */
  public void verifyServerPodsDeleted(int replicas) throws Exception {
    TestUtils.checkPodDeleted(domainUid + "-" + adminServerName, domainNS);
    for (int i = 1; i <= replicas; i++) {
      TestUtils.checkPodDeleted(domainUid + "-" + managedServerNameBase + i, domainNS);
    }
  }

  public Map<String, Object> getDomainMap() {
    return domainMap;
  }

  /**
   * delete PVC and check PV status released when weblogicDomainStorageReclaimPolicy is Recycle
   *
   * @throws Exception
   */
  public void deletePVCAndCheckPVReleased() throws Exception {
    StringBuffer cmd = new StringBuffer("kubectl get pv ");
    String pvBaseName = (String) pvMap.get("baseName");
    if (domainUid != null) pvBaseName = domainUid + "-" + pvBaseName;
    cmd.append(pvBaseName).append("-pv -n ").append(domainNS);

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() == 0) {
      logger.info("Status of PV before deleting PVC " + result.stdout());
    }
    TestUtils.deletePVC(pvBaseName + "-pvc", domainNS, domainUid);
    String reclaimPolicy = (String) domainMap.get("weblogicDomainStorageReclaimPolicy");
    boolean pvReleased = TestUtils.checkPVReleased(pvBaseName, domainNS);
    if (reclaimPolicy != null && reclaimPolicy.equals("Recycle") && !pvReleased) {
      throw new RuntimeException(
          "ERROR: pv for " + domainUid + " still exists after the pvc is deleted, exiting!");
    } else {
      logger.info("PV is released when PVC is deleted");
    }
  }
  /**
   * create domain on existing directory
   *
   * @throws Exception
   */
  public void createDomainOnExistingDirectory() throws Exception {
    String domainStoragePath = domainMap.get("weblogicDomainStoragePath").toString();
    String domainDir = domainStoragePath + "/domains/" + domainMap.get("domainUID").toString();
    logger.info("making sure the domain directory exists");
    if (domainDir != null && !(new File(domainDir).exists())) {
      throw new RuntimeException(
          "FAIL: the domain directory " + domainDir + " does not exist, exiting!");
    }
    logger.info("Run the script to create domain");

    // create domain using different output dir but pv is same, it fails as the domain was already
    // created on the pv dir
    try {
      callCreateDomainScript(userProjectsDir + "2");
    } catch (RuntimeException re) {
      re.printStackTrace();
      logger.info("[SUCCESS] create domain job failed, this is the expected behavior");
      return;
    }
    throw new RuntimeException("FAIL: unexpected result, create domain job did not report error");
  }
  /**
   * access admin console using load balancer web port for Apache load balancer
   *
   * @throws Exception
   */
  public void verifyAdminConsoleViaLB() throws Exception {
    if (!loadBalancer.equals("APACHE")) {
      logger.info("This check is done only for APACHE load balancer");
      return;
    }
    String nodePortHost = getHostNameForCurl();
    int nodePort = getAdminSericeLBNodePort();
    String responseBodyFile =
        userProjectsDir + "/weblogic-domains/" + domainUid + "/testconsole.response.body";
    logger.info("nodePortHost " + nodePortHost + " nodePort " + nodePort);

    StringBuffer cmd = new StringBuffer();
    cmd.append("curl --silent --show-error --noproxy ")
        .append(nodePortHost)
        .append(" http://")
        .append(nodePortHost)
        .append(":")
        .append(nodePort)
        .append("/console/login/LoginForm.jsp")
        .append(" --write-out %{http_code} -o ")
        .append(responseBodyFile);
    logger.info("cmd for curl " + cmd);

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command "
              + cmd
              + " failed, returned "
              + result.stderr()
              + "\n "
              + result.stdout());
    }

    String output = result.stdout().trim();
    logger.info("output " + output);
    if (!output.equals("200")) {
      throw new RuntimeException(
          "FAILURE: accessing admin console via load balancer did not return 200 status code, got "
              + output);
    }
  }

  public String getDomainUid() {
    return domainUid;
  }

  /**
   * Get the name of the administration server in the domain
   *
   * @return the name of the admin server
   */
  public String getAdminServerName() {
    return adminServerName;
  }

  /**
   * Get the namespace in which the domain is running
   *
   * @return the name of the domain name space
   */
  public String getDomainNS() {
    return domainNS;
  }
  /**
   * test liveness probe for managed server 1
   *
   * @throws Exception
   */
  public void testWlsLivenessProbe() throws Exception {

    // test managed server1 pod auto restart
    String serverName = managedServerNameBase + "1";
    TestUtils.testWlsLivenessProbe(domainUid, serverName, domainNS);
  }

  /**
   * Get number of server addresses in cluster service endpoint
   *
   * @param clusterName
   * @return
   * @throws Exception
   */
  public int getNumberOfServersInClusterServiceEndpoint(String clusterName) throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl describe service ")
        .append(domainUid)
        .append("-cluster-")
        .append(clusterName)
        .append(" -n ")
        .append(domainNS)
        .append(" | grep Endpoints | awk '{print $2}'");

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: Commmand " + cmd + " failed, cluster service is not ready.");
    }
    logger.info("Cluster service Endpoint " + result.stdout());
    return new StringTokenizer(result.stdout(), ",").countTokens();
  }

  private int getAdminSericeLBNodePort() throws Exception {

    String adminServerLBNodePortService = domainUid + "-apache-webtier";

    StringBuffer cmd = new StringBuffer("kubectl get services -n ");
    cmd.append(domainNS)
        .append(" -o jsonpath='{.items[?(@.metadata.name == \"")
        .append(adminServerLBNodePortService)
        .append("\")].spec.ports[0].nodePort}'");

    logger.info("Cmd to get the admins service node port " + cmd);

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() == 0) {
      return new Integer(result.stdout().trim()).intValue();
    } else {
      throw new RuntimeException("Cmd failed " + result.stderr() + " \n " + result.stdout());
    }
  }

  private void createPV() throws Exception {

    Yaml yaml = new Yaml();
    InputStream pv_is =
        new FileInputStream(
            new File(
                BaseTest.getResultDir()
                    + "/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml"));
    pvMap = yaml.load(pv_is);
    pv_is.close();
    pvMap.put("domainUID", domainUid);

    // each domain uses its own pv for now
    if (domainUid != null)
      domainMap.put("persistentVolumeClaimName", domainUid + "-" + pvMap.get("baseName") + "-pvc");
    else domainMap.put("persistentVolumeClaimName", pvMap.get("baseName") + "-pvc");

    if (domainMap.get("weblogicDomainStorageReclaimPolicy") != null) {
      pvMap.put(
          "weblogicDomainStorageReclaimPolicy",
          domainMap.get("weblogicDomainStorageReclaimPolicy"));
    }
    if (domainMap.get("weblogicDomainStorageSize") != null) {
      pvMap.put("weblogicDomainStorageSize", domainMap.get("weblogicDomainStorageSize"));
    }
    pvMap.put("namespace", domainNS);

    weblogicDomainStorageReclaimPolicy = (String) pvMap.get("weblogicDomainStorageReclaimPolicy");
    weblogicDomainStorageSize = (String) pvMap.get("weblogicDomainStorageSize");

    pvMap.put("weblogicDomainStorageNFSServer", TestUtils.getHostName());

    // set pv path
    domainMap.put(
        "weblogicDomainStoragePath",
        BaseTest.getPvRoot() + "/acceptance_test_pv/persistentVolume-" + domainUid);

    pvMap.put(
        "weblogicDomainStoragePath",
        BaseTest.getPvRoot() + "/acceptance_test_pv/persistentVolume-" + domainUid);

    pvMap.values().removeIf(Objects::isNull);
    // k8s job mounts PVROOT /scratch/<usr>/wl_k8s_test_results to /scratch, create PV/PVC
    new PersistentVolume("/scratch/acceptance_test_pv/persistentVolume-" + domainUid, pvMap);
  }

  /**
   * verify domain server pods get restarted after a property change
   *
   * @param oldPropertyString
   * @param newPropertyString
   * @throws Exception
   */
  public void testDomainServerPodRestart(String oldPropertyString, String newPropertyString)
      throws Exception {
    logger.info("Inside testDomainServerPodRestart");
    String content =
        new String(
            Files.readAllBytes(
                Paths.get(
                    BaseTest.getUserProjectsDir()
                        + "/weblogic-domains/"
                        + domainUid
                        + "/domain.yaml")));
    boolean result = content.indexOf(newPropertyString) >= 0;
    logger.info("The search result for " + newPropertyString + " is: " + result);
    if (!result) {
      TestUtils.createNewYamlFile(
          BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml",
          BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain_new.yaml",
          oldPropertyString,
          newPropertyString);
      logger.info(
          "Done - generate new domain.yaml for "
              + domainUid
              + " oldProperty: "
              + oldPropertyString
              + " newProperty: "
              + newPropertyString);

      // kubectl apply the new generated domain yaml file with changed property
      StringBuffer command = new StringBuffer();
      command
          .append("kubectl apply  -f ")
          .append(
              BaseTest.getUserProjectsDir()
                  + "/weblogic-domains/"
                  + domainUid
                  + "/domain_new.yaml");
      logger.info("kubectl execut with command: " + command.toString());
      TestUtils.exec(command.toString());

      // verify the servers in the domain are being restarted in a sequence
      verifyAdminServerRestarted();
      verifyManagedServersRestarted();
      // make domain.yaml include the new changed property
      TestUtils.copyFile(
          BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain_new.yaml",
          BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml");
    }
    logger.info("Done - testDomainServerPodRestart");
  }

  /**
   * verify that admin server pod gets restarted.
   *
   * @throws Exception
   */
  public void verifyAdminServerRestarted() throws Exception {
    logger.info("Checking if admin pod(" + domainUid + "-" + adminServerName + ") is Terminating");
    TestUtils.checkPodTerminating(domainUid + "-" + adminServerName, domainNS);

    logger.info("Checking if admin pod(" + domainUid + "-" + adminServerName + ") is Running");
    TestUtils.checkPodCreated(domainUid + "-" + adminServerName, domainNS);
  }

  /**
   * verify that managed server pods get restarted.
   *
   * @throws Exception
   */
  public void verifyManagedServersRestarted() throws Exception {
    if (domainMap.get("serverStartPolicy") == null
        || (domainMap.get("serverStartPolicy") != null
            && !domainMap.get("serverStartPolicy").toString().trim().equals("ADMIN_ONLY"))) {
      // check managed server pods
      for (int i = 1; i <= initialManagedServerReplicas; i++) {
        logger.info(
            "Checking if managed pod("
                + domainUid
                + "-"
                + managedServerNameBase
                + i
                + ") is Terminating");
        TestUtils.checkPodTerminating(domainUid + "-" + managedServerNameBase + i, domainNS);

        logger.info(
            "Checking if managed pod("
                + domainUid
                + "-"
                + managedServerNameBase
                + i
                + ") is Running");
        TestUtils.checkPodCreated(domainUid + "-" + managedServerNameBase + i, domainNS);
      }
    }
  }

  private void createSecret() throws Exception {
    Secret secret =
        new Secret(
            domainNS,
            domainMap.getOrDefault("secretName", domainUid + "-weblogic-credentials").toString(),
            BaseTest.getUsername(),
            BaseTest.getPassword());
    domainMap.put("weblogicCredentialsSecretName", secret.getSecretName());
    final String labelCmd =
        String.format(
            "kubectl label secret %s weblogic.domainUID=%s -n %s",
            secret.getSecretName(), domainUid, domainNS);
    ExecResult result = ExecCommand.exec(labelCmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command to label secret \""
              + labelCmd
              + "\" failed, returned "
              + result.stdout()
              + "\n"
              + result.stderr());
    }
  }

  private void generateInputYaml() throws Exception {
    Path parentDir =
        Files.createDirectories(Paths.get(userProjectsDir + "/weblogic-domains/" + domainUid));
    generatedInputYamlFile = parentDir + "/weblogic-domain-values.yaml";
    TestUtils.createInputFile(domainMap, generatedInputYamlFile);
  }

  private void callCreateDomainScript(String outputDir) throws Exception {

    // copy create domain py script for domain on pv case
    if (domainMap.containsKey("createDomainPyScript")
        && !domainMap.containsKey("domainHomeImageBase")) {
      Files.copy(
          new File(BaseTest.getProjectRoot() + "/" + domainMap.get("createDomainPyScript"))
              .toPath(),
          new File(
                  BaseTest.getResultDir()
                      + "/samples/scripts/create-weblogic-domain/domain-home-on-pv/wlst/create-domain.py")
              .toPath(),
          StandardCopyOption.REPLACE_EXISTING);
    }

    StringBuffer createDomainScriptCmd = new StringBuffer(BaseTest.getResultDir());

    // call different create-domain.sh based on the domain type
    if (domainMap.containsKey("domainHomeImageBase")) {

      // clone docker sample from github and copy create domain py script for domain in image case
      gitCloneDockerImagesSample(domainMap);

      createDomainScriptCmd
          .append(
              "/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain.sh -u ")
          .append(BaseTest.getUsername())
          .append(" -p ")
          .append(BaseTest.getPassword())
          .append(" -k -i ");
    } else {
      createDomainScriptCmd.append(
          "/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain.sh -v -i ");
    }
    createDomainScriptCmd.append(generatedInputYamlFile);

    // skip executing yaml if configOverrides
    if (!domainMap.containsKey("configOverrides")) {
      createDomainScriptCmd.append(" -e ");
    }
    createDomainScriptCmd.append(" -o ").append(outputDir);

    logger.info("Running " + createDomainScriptCmd);
    ExecResult result = ExecCommand.exec(createDomainScriptCmd.toString(), true);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command "
              + createDomainScriptCmd
              + " failed, returned "
              + result.stdout()
              + "\n"
              + result.stderr());
    }
    String outputStr = result.stdout().trim();
    logger.info("Command returned " + outputStr);

    // write configOverride and configOverrideSecrets to domain.yaml
    if (domainMap.containsKey("configOverrides")) {
      appendToDomainYamlAndCreate(domainMap);
    }
  }

  private void createLoadBalancer() throws Exception {
    Map<String, Object> lbMap = new HashMap<String, Object>();
    lbMap.put("domainUID", domainUid);
    lbMap.put("namespace", domainNS);
    lbMap.put("host", domainUid + ".org");
    lbMap.put("serviceName", domainUid + "-cluster-" + domainMap.get("clusterName"));
    if (voyager) {
      lbMap.put("loadBalancer", "VOYAGER");
      lbMap.put("loadBalancerWebPort", domainMap.get("voyagerWebPort"));
    } else {
      lbMap.put("loadBalancer", domainMap.getOrDefault("loadBalancer", loadBalancer));
      lbMap.put(
          "loadBalancerWebPort",
          domainMap.getOrDefault("loadBalancerWebPort", new Integer(loadBalancerWebPort)));
    }
    if (!INGRESSPERDOMAIN) {
      lbMap.put("ingressPerDomain", new Boolean("false"));
      logger.info("For this domain, INGRESSPERDOMAIN is set to false");
    } else {
      lbMap.put(
          "ingressPerDomain",
          domainMap.getOrDefault("ingressPerDomain", new Boolean(ingressPerDomain)));
    }
    lbMap.put("clusterName", domainMap.get("clusterName"));

    loadBalancer = (String) lbMap.get("loadBalancer");
    loadBalancerWebPort = ((Integer) lbMap.get("loadBalancerWebPort")).intValue();
    ingressPerDomain = ((Boolean) lbMap.get("ingressPerDomain")).booleanValue();
    logger.info(
        "For this domain loadBalancer is: "
            + loadBalancer
            + " ingressPerDomain is: "
            + ingressPerDomain
            + " loadBalancerWebPort is: "
            + loadBalancerWebPort);

    if (loadBalancer.equals("TRAEFIK") && !ingressPerDomain) {
      lbMap.put("name", "traefik-hostrouting-" + domainUid);
    }

    if (loadBalancer.equals("TRAEFIK") && ingressPerDomain) {
      lbMap.put("name", "traefik-ingress-" + domainUid);
    }

    if (loadBalancer.equals("VOYAGER") && ingressPerDomain) {
      lbMap.put("name", "voyager-ingress-" + domainUid);
    }

    if (loadBalancer.equals("APACHE")) {
      /* lbMap.put("loadBalancerAppPrepath", "/weblogic");
      lbMap.put("loadBalancerExposeAdminPort", new Boolean(true)); */
    }
    lbMap.values().removeIf(Objects::isNull);
    new LoadBalancer(lbMap);
  }

  private void callShellScriptByExecToPod(
      String username, String password, String webappName, String appLocationInPod)
      throws Exception {

    StringBuffer cmdKubectlSh = new StringBuffer("kubectl -n ");
    cmdKubectlSh
        .append(domainNS)
        .append(" exec -it ")
        .append(domainUid)
        .append("-")
        .append(adminServerName)
        .append(" -- bash -c 'chmod +x -R ")
        .append(appLocationInPod)
        .append("  && ")
        .append(appLocationInPod)
        .append("/callpyscript.sh ")
        .append(appLocationInPod)
        .append("/deploywebapp.py ")
        .append(username)
        .append(" ")
        .append(password)
        .append(" t3://")
        // .append(TestUtils.getHostName())
        .append(domainUid)
        .append("-")
        .append(adminServerName)
        .append(":")
        .append(t3ChannelPort)
        .append(" ")
        .append(webappName)
        .append(" ")
        .append(appLocationInPod)
        .append("/")
        .append(webappName)
        .append(".war ")
        .append(clusterName)
        .append("'");
    logger.info("Command to call kubectl sh file " + cmdKubectlSh);
    ExecResult result = ExecCommand.exec(cmdKubectlSh.toString());
    String resultStr =
        "Command= '"
            + cmdKubectlSh
            + "'"
            + ", exitValue="
            + result.exitValue()
            + ", stdout='"
            + result.stdout()
            + "'"
            + ", stderr='"
            + result.stderr()
            + "'";
    if (result.exitValue() != 0 || !resultStr.contains("Deployment State : completed"))
      throw new RuntimeException("FAILURE: webapp deploy failed - " + resultStr);
  }

  private void callWebAppAndWaitTillReady(String curlCmd) throws Exception {
    for (int i = 0; i < maxIterations; i++) {
      ExecResult result = ExecCommand.exec(curlCmd.toString());
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command " + curlCmd + " failed, returned " + result.stderr());
      }
      String responseCode = result.stdout().trim();
      if (!responseCode.equals("200")) {
        logger.info(
            "testwebapp did not return 200 status code, got "
                + responseCode
                + ", iteration "
                + i
                + " of "
                + maxIterations);
        if (i == (maxIterations - 1)) {
          throw new RuntimeException(
              "FAILURE: testwebapp did not return 200 status code, got " + responseCode);
        }
        try {
          Thread.sleep(waitTime * 1000);
        } catch (InterruptedException ignore) {
        }
      } else {
        logger.info("testwebapp returned 200 response code, iteration " + i);
        break;
      }
    }
  }

  private void callWebAppAndCheckForServerNameInResponse(
      String curlCmd, boolean verifyLoadBalancing) throws Exception {
    // map with server names and boolean values
    HashMap<String, Boolean> managedServers = new HashMap<String, Boolean>();
    for (int i = 1; i <= TestUtils.getClusterReplicas(domainUid, clusterName, domainNS); i++) {
      managedServers.put(domainUid + "-" + managedServerNameBase + i, new Boolean(false));
    }
    logger.info("Calling webapp 20 times " + curlCmd);
    // number of times to call webapp
    for (int i = 0; i < 20; i++) {
      ExecResult result = ExecCommand.exec(curlCmd.toString());
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command "
                + curlCmd
                + " failed, returned "
                + result.stderr()
                + " \n "
                + result.stdout());
      } else {
        logger.info("webapp invoked successfully for curlCmd:" + curlCmd);
      }
      if (verifyLoadBalancing) {
        String response = result.stdout().trim();
        // logger.info("response: " + response);
        for (String key : managedServers.keySet()) {
          if (response.contains(key)) {
            managedServers.put(key, new Boolean(true));
            break;
          }
        }
      }
    }
    logger.info("ManagedServers " + managedServers);

    // error if any managedserver value is false
    if (verifyLoadBalancing) {
      for (Map.Entry<String, Boolean> entry : managedServers.entrySet()) {
        logger.info("Load balancer will try to reach server " + entry.getKey());
        if (!entry.getValue().booleanValue()) {
          // print service and pods info for debugging
          TestUtils.describeService(domainNS, domainUid + "-cluster-" + clusterName);
          TestUtils.getPods(domainNS);
          throw new RuntimeException(
              "FAILURE: Load balancer can not reach server " + entry.getKey());
        }
      }
    }
  }

  private void initialize(Map<String, Object> inputDomainMap) throws Exception {
    domainMap = inputDomainMap;
    this.userProjectsDir = BaseTest.getUserProjectsDir();
    this.projectRoot = BaseTest.getProjectRoot();

    // copy samples to RESULT_DIR
    TestUtils.exec(
        "cp -rf " + BaseTest.getProjectRoot() + "/kubernetes/samples " + BaseTest.getResultDir());

    this.voyager =
        System.getenv("LB_TYPE") != null && System.getenv("LB_TYPE").equalsIgnoreCase("VOYAGER");
    if (System.getenv("INGRESSPERDOMAIN") != null) {
      INGRESSPERDOMAIN = new Boolean(System.getenv("INGRESSPERDOMAIN")).booleanValue();
    }

    domainMap.put("domainName", domainMap.get("domainUID"));

    // read sample domain inputs
    String sampleDomainInputsFile =
        "/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain-inputs.yaml";
    if (domainMap.containsKey("domainHomeImageBase")) {
      sampleDomainInputsFile =
          "/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain-inputs.yaml";
    }
    Yaml dyaml = new Yaml();
    InputStream sampleDomainInputStream =
        new FileInputStream(new File(BaseTest.getResultDir() + sampleDomainInputsFile));
    logger.info(
        "loading domain inputs template file " + BaseTest.getResultDir() + sampleDomainInputsFile);
    Map<String, Object> sampleDomainMap = dyaml.load(sampleDomainInputStream);
    sampleDomainInputStream.close();

    // add attributes with default values from sample domain inputs to domain map
    sampleDomainMap.forEach(domainMap::putIfAbsent);

    domainUid = (String) domainMap.get("domainUID");
    // Customize the create domain job inputs
    domainNS = (String) domainMap.get("namespace");
    adminServerName = (String) domainMap.get("adminServerName");
    managedServerNameBase = (String) domainMap.get("managedServerNameBase");
    initialManagedServerReplicas =
        ((Integer) domainMap.get("initialManagedServerReplicas")).intValue();
    configuredManagedServerCount =
        ((Integer) domainMap.get("configuredManagedServerCount")).intValue();
    exposeAdminT3Channel = ((Boolean) domainMap.get("exposeAdminT3Channel")).booleanValue();
    exposeAdminNodePort = ((Boolean) domainMap.get("exposeAdminNodePort")).booleanValue();
    t3ChannelPort = ((Integer) domainMap.get("t3ChannelPort")).intValue();
    clusterName = (String) domainMap.get("clusterName");
    clusterType = (String) domainMap.get("clusterType");
    serverStartPolicy = (String) domainMap.get("serverStartPolicy");

    if (exposeAdminT3Channel) {
      domainMap.put("t3PublicAddress", TestUtils.getHostName());
    }

    String imageName = "store/oracle/weblogic";
    if (System.getenv("IMAGE_NAME_WEBLOGIC") != null) {
      imageName = System.getenv("IMAGE_NAME_WEBLOGIC");
      logger.info("IMAGE_NAME_WEBLOGIC " + imageName);
    }

    String imageTag = "12.2.1.3";
    if (System.getenv("IMAGE_TAG_WEBLOGIC") != null) {
      imageTag = System.getenv("IMAGE_TAG_WEBLOGIC");
      logger.info("IMAGE_TAG_WEBLOGIC " + imageTag);
    }
    domainMap.put("logHome", "/shared/logs/" + domainUid);
    if (!domainMap.containsKey("domainHomeImageBase")) {
      domainMap.put("domainHome", "/shared/domains/" + domainUid);
      /* domainMap.put(
      "createDomainFilesDir",
      BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/domain-home-on-pv"); */
      domainMap.put("image", imageName + ":" + imageTag);
    }

    if (domainMap.containsKey("domainHomeImageBuildPath")) {
      domainMap.put(
          "domainHomeImageBuildPath",
          BaseTest.getResultDir() + "/" + domainMap.get("domainHomeImageBuildPath"));
    }
    if (System.getenv("IMAGE_PULL_SECRET_WEBLOGIC") != null) {
      domainMap.put("imagePullSecretName", System.getenv("IMAGE_PULL_SECRET_WEBLOGIC"));
      if (System.getenv("WERCKER") != null) {
        // create docker registry secrets
        TestUtils.createDockerRegistrySecret(
            System.getenv("IMAGE_PULL_SECRET_WEBLOGIC"),
            System.getenv("REPO_SERVER"),
            System.getenv("REPO_USERNAME"),
            System.getenv("REPO_PASSWORD"),
            System.getenv("REPO_EMAIL"),
            domainNS);
      }
    } else {
      domainMap.put("imagePullSecretName", "docker-store");
    }
    // remove null values if any attributes
    domainMap.values().removeIf(Objects::isNull);

    // create config map and secret for custom sit config
    if ((domainMap.get("configOverrides") != null)
        && (domainMap.get("configOverridesFile") != null)) {
      // write hostname in config file for public address

      String configOverridesFile = domainMap.get("configOverridesFile").toString();

      String cmd =
          "kubectl -n "
              + domainNS
              + " create cm "
              + domainUid
              + "-"
              + domainMap.get("configOverrides")
              + " --from-file "
              + configOverridesFile;
      ExecResult result = ExecCommand.exec(cmd);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command " + cmd + " failed, returned " + result.stderr());
      }
      cmd =
          "kubectl -n "
              + domainNS
              + " label cm "
              + domainUid
              + "-"
              + domainMap.get("configOverrides")
              + " weblogic.domainUID="
              + domainUid;
      result = ExecCommand.exec(cmd);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command " + cmd + " failed, returned " + result.stderr());
      }
      // create secret for custom sit config t3 public address
      // create datasource secret for user and password
      cmd =
          "kubectl -n "
              + domainNS
              + " create secret generic "
              + domainUid
              + "-test-secrets"
              + " --from-literal=hostname="
              + TestUtils.getHostName()
              + " --from-literal=dbusername=root"
              + " --from-literal=dbpassword=root123";
      result = ExecCommand.exec(cmd);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command " + cmd + " failed, returned " + result.stderr());
      }
    }
  }

  private String getNodeHost() throws Exception {
    String cmd =
        "kubectl describe pod "
            + domainUid
            + "-"
            + adminServerName
            + " -n "
            + domainNS
            + " | grep Node:";

    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    String nodePortHost = result.stdout();
    // logger.info("nodePortHost "+nodePortHost);
    if (nodePortHost.contains(":") && nodePortHost.contains("/")) {
      return nodePortHost
          .substring(nodePortHost.indexOf(":") + 1, nodePortHost.indexOf("/"))
          .trim();
    } else {
      throw new RuntimeException("FAILURE: Invalid nodePortHost from admin pod " + nodePortHost);
    }
  }

  private String getNodePort() throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl describe domain ")
        .append(domainUid)
        .append(" -n ")
        .append(domainNS)
        .append(" | grep \"Node Port:\"");
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    String output = result.stdout();
    if (output.contains("Node Port")) {
      return output.substring(output.indexOf(":") + 1).trim();
    } else {
      throw new RuntimeException(
          "FAILURE: Either domain "
              + domainUid
              + " does not exist or no NodePort is not configured "
              + "for the admin server in domain.");
    }
  }

  private void gitCloneDockerImagesSample(Map domainMap) throws Exception {
    if (domainMap.containsKey("domainHomeImageBuildPath")
        && !(((String) domainMap.get("domainHomeImageBuildPath")).trim().isEmpty())) {
      String domainHomeImageBuildPath = (String) domainMap.get("domainHomeImageBuildPath");
      StringBuffer removeAndClone = new StringBuffer();
      logger.info(
          "Checking if directory "
              + domainHomeImageBuildPath
              + " exists "
              + new File(domainHomeImageBuildPath).exists());
      if (new File(domainHomeImageBuildPath).exists()) {
        removeAndClone
            .append("rm -rf ")
            .append(BaseTest.getResultDir())
            .append("/docker-images && ");
      }
      // git clone docker-images project
      removeAndClone
          .append(" git clone https://github.com/oracle/docker-images.git ")
          .append(BaseTest.getResultDir())
          .append("/docker-images");
      logger.info("Executing cmd " + removeAndClone);
      ExecResult result = ExecCommand.exec(removeAndClone.toString());
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command "
                + removeAndClone
                + " failed "
                + result.stderr()
                + " "
                + result.stdout());
      }

      // copy create domain py script to cloned location
      if (domainMap.containsKey("createDomainPyScript")) {
        Files.copy(
            new File(BaseTest.getProjectRoot() + "/" + domainMap.get("createDomainPyScript"))
                .toPath(),
            new File(domainHomeImageBuildPath + "/container-scripts/create-wls-domain.py").toPath(),
            StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private void appendToDomainYamlAndCreate(Map domainMap) throws Exception {
    String contentToAppend =
        "  configOverrides: "
            + domainUid
            + "-"
            + domainMap.get("configOverrides")
            + "\n"
            + "  configOverrideSecrets: [ \""
            + domainUid
            + "-test-secrets\" ]"
            + "\n";

    String domainYaml =
        BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
    Files.write(Paths.get(domainYaml), contentToAppend.getBytes(), StandardOpenOption.APPEND);

    String command = "kubectl create -f " + domainYaml;
    ExecResult result = ExecCommand.exec(command);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command "
              + command
              + " failed, returned "
              + result.stdout()
              + "\n"
              + result.stderr());
    }
    logger.info("Command returned " + result.stdout().trim());
  }

  public String getHostNameForCurl() throws Exception {
    if (System.getenv("K8S_NODEPORT_HOST") != null) {
      return System.getenv("K8S_NODEPORT_HOST");
    } else {
      // ExecResult result = ExecCommand.exec("hostname | awk -F. '{print $1}'");
      ExecResult result1 =
          ExecCommand.exec("kubectl get nodes -o=jsonpath='{range .items[0]}{.metadata.name}'");
      if (result1.exitValue() != 0) {
        throw new RuntimeException("FAILURE: Could not get K8s Node name");
      }
      ExecResult result2 =
          ExecCommand.exec(
              "nslookup " + result1.stdout() + " | grep \"^Name\" | awk '{ print $2 }'");
      if (result2.stdout().trim().equals("")) {
        return result1.stdout().trim();
      } else {
        return result2.stdout().trim();
      }
    }
  }

  public int getLoadBalancerWebPort() {
    return loadBalancerWebPort;
  }

  /**
   * Shut down a ms by setting serverStartPolicy to NEVER
   *
   * @throws Exception
   */
  public void shutdownManagedServerUsingServerStartPolicy(String msName) throws Exception {
    String cmd =
        "kubectl patch domain "
            + domainUid
            + " -n "
            + domainNS
            + " -p '{\"spec\":{\"managedServers\":[{\"serverName\":\""
            + msName
            + "\",\"serverStartPolicy\":\"NEVER\"}]}}' --type merge";

    logger.info("command to shutdown managed server <" + msName + "> is: " + cmd);

    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new Exception("FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    String output = result.stdout().trim();
    logger.info("output from shutting down managed server:\n" + output);

    TestUtils.checkPodDeleted(domainUid + "-" + msName, domainNS);
  }

  /**
   * Restart a ms by setting serverStartPolicy to IF_NEEDED
   *
   * @throws Exception
   */
  public void restartManagedServerUsingServerStartPolicy(String msName) throws Exception {
    String cmd =
        "kubectl patch domain "
            + domainUid
            + " -n "
            + domainNS
            + " -p '{\"spec\":{\"managedServers\":[{\"serverName\":\""
            + msName
            + "\",\"serverStartPolicy\":\"IF_NEEDED\"}]}}' --type merge";

    logger.info("command to restart managed server <" + msName + "> is: " + cmd);

    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new Exception("FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    String output = result.stdout().trim();
    logger.info("output from restarting managed server:\n" + output);

    TestUtils.checkPodCreated(domainUid + "-" + msName, domainNS);
    TestUtils.checkPodReady(domainUid + "-" + msName, domainNS);
  }

  /**
   * Run the shell script to build WAR, EAR or JAR file and deploy the App in the admin pod
   *
   * @param webappName - Web App Name to be deployed
   * @param scriptName - a shell script to build WAR, EAR or JAR file and deploy the App in the
   *     admin pod
   * @param archiveExt - archive extention
   * @param infoDirNames - archive information dir location
   * @param username - weblogic user name
   * @param password - weblogc password
   * @throws Exception
   */
  private void callShellScriptToBuildDeployAppInPod(
      String webappName,
      String scriptName,
      String archiveExt,
      String infoDirNames,
      String username,
      String password)
      throws Exception {

    String nodeHost = getHostNameForCurl();
    String nodePort = getNodePort();
    String appLocationInPod = BaseTest.getAppLocationInPod();

    StringBuffer cmdKubectlSh = new StringBuffer("kubectl -n ");
    cmdKubectlSh
        .append(domainNS)
        .append(" exec -it ")
        .append(domainUid)
        .append("-")
        .append(adminServerName)
        .append(" -- bash -c 'chmod +x -R ")
        .append(appLocationInPod)
        .append(" && sh ")
        .append(appLocationInPod)
        .append("/")
        .append(scriptName)
        .append(" ")
        .append(nodeHost)
        .append(" ")
        .append(nodePort)
        .append(" ")
        .append(username)
        .append(" ")
        .append(password)
        .append(" ")
        .append(appLocationInPod)
        .append("/")
        .append(webappName)
        .append(" ")
        .append(webappName)
        .append(" ")
        .append(clusterName)
        .append(" ")
        .append(infoDirNames)
        .append(" ")
        .append(archiveExt)
        .append("'");

    logger.info("Command to exec script file: " + cmdKubectlSh);
    ExecResult result = ExecCommand.exec(cmdKubectlSh.toString());

    String resultStr =
        "Command= '"
            + cmdKubectlSh
            + "'"
            + ", exitValue="
            + result.exitValue()
            + ", stdout='"
            + result.stdout()
            + "'"
            + ", stderr='"
            + result.stderr()
            + "'";

    if (!resultStr.contains("Unable to use a TTY") && result.exitValue() != 0) {
      throw new RuntimeException("FAILURE: webapp deploy failed - " + resultStr);
    }
  }

  /**
   * Create dir to save Web App files Copy the shell script file and all App files over to the admin
   * pod Run the shell script to build WAR, EAR or JAR file and deploy the App in the admin pod
   *
   * @param appName - Java App name to be deployed
   * @param scriptName - a shell script to build WAR, EAR or JAR file and deploy the App in the
   *     admin pod
   * @param username - weblogic user name
   * @param password - weblogc password
   * @param args - by default, a WAR file is created for a Web App and a EAR file is created for EJB
   *     App. this varargs gives a client a chance to change EJB's archive extenyion to JAR
   * @throws Exception
   */
  public void buildDeployJavaAppInPod(
      String appName, String scriptName, String username, String password, String... args)
      throws Exception {
    String adminServerPod = domainUid + "-" + adminServerName;

    String appLocationOnHost = BaseTest.getAppLocationOnHost() + "/" + appName;
    String appLocationInPod = BaseTest.getAppLocationInPod() + "/" + appName;
    String scriptPathOnHost = BaseTest.getAppLocationOnHost() + "/" + scriptName;
    String scriptPathInPod = BaseTest.getAppLocationInPod() + "/" + scriptName;

    // Default velues to build archive file
    final String initInfoDirName = "WEB-INF";
    String archiveExt = "war";
    String infoDirName = initInfoDirName;

    // Get archive info dir name
    File appFiles = new File(appLocationOnHost);

    String[] subDirArr =
        appFiles.list(
            new FilenameFilter() {
              @Override
              public boolean accept(File dir, String name) {
                return name.equals(initInfoDirName);
              }
            });

    List<String> subDirList = Arrays.asList(subDirArr);

    // Check archive file type
    if (!subDirList.contains(infoDirName)) {
      infoDirName = "META-INF";
      // Create .ear file or .jar file for EJB
      archiveExt = (args.length == 0) ? "ear" : args[0];
    }

    logger.info("Build and deploy: " + appName + "." + archiveExt + " in the admin pod");

    // Create app dir in the admin pod
    StringBuffer mkdirCmd = new StringBuffer(" -- bash -c 'mkdir -p ");
    mkdirCmd.append(appLocationInPod).append("/" + infoDirName + "'");
    TestUtils.kubectlexec(adminServerPod, domainNS, mkdirCmd.toString());

    // Copy shell script to the pod
    TestUtils.copyFileViaCat(scriptPathOnHost, scriptPathInPod, adminServerPod, domainNS);

    // Copy all App files to the admin pod
    TestUtils.copyAppFilesToPod(appLocationOnHost, appLocationInPod, adminServerPod, domainNS);

    // Run the script to build WAR, EAR or JAR file and deploy the App in the admin pod
    callShellScriptToBuildDeployAppInPod(
        appName, scriptName, archiveExt, infoDirName, username, password);
  }
}

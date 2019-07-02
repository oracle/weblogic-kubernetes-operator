// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import java.util.logging.Level;
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

  protected static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");
  private static int maxIterations = BaseTest.getMaxIterationsPod(); // 50 * 5 = 250 seconds
  private static int waitTime = BaseTest.getWaitTimePod();
  // LB_TYPE is an evn var. Set to "VOYAGER" to use it as loadBalancer
  private static String LB_TYPE;
  // set INGRESSPERDOMAIN to false to create LB's ingress by kubectl yaml file
  private static boolean INGRESSPERDOMAIN = true;
  protected Map<String, Object> domainMap;
  protected Map<String, Object> pvMap;
  // attributes from domain properties
  protected String domainUid = "";
  // default values as in create-weblogic-domain-inputs.yaml, generated yaml file will have the
  // customized property values
  protected String domainNS;
  protected String userProjectsDir = "";
  protected String generatedInputYamlFile;
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
  private String loadBalancer = "TRAEFIK";
  private int loadBalancerWebPort = 30305;
  private String domainHomeImageBuildPath = "";
  private String projectRoot = "";
  private boolean ingressPerDomain = true;
  private String imageTag;
  private String imageName;
  private boolean voyager;

  public Domain() throws Exception {
    domainMap = new HashMap<>();
  }

  public Domain(String inputYaml) throws Exception {
    // read input domain yaml to test
    this(TestUtils.loadYaml(inputYaml));
  }

  public Domain(Map<String, Object> inputDomainMap) throws Exception {
    initialize(inputDomainMap);
    createPv();
    createSecret();
    generateInputYaml();
    callCreateDomainScript(userProjectsDir);
    createLoadBalancer();
  }

  /**
   * Verifies the required pods are created, services are created and the servers are ready.
   *
   * @throws Exception exception
   */
  public void verifyDomainCreated() throws Exception {
    StringBuffer command = new StringBuffer();
    command.append("kubectl get domain ").append(domainUid).append(" -n ").append(domainNS);
    ExecResult result = TestUtils.exec(command.toString());
    if (!result.stdout().contains(domainUid))
      throw new RuntimeException("FAILURE: domain not found, exiting!");

    verifyPodsCreated();
    verifyServicesCreated();
    verifyServersReady();
  }

  /**
   * verify pods are created.
   *
   * @throws Exception exception
   */
  public void verifyPodsCreated() throws Exception {
    // check admin pod
    logger.info("Checking if admin pod(" + domainUid + "-" + adminServerName + ") is Running");
    TestUtils.checkPodCreated(domainUid + "-" + adminServerName, domainNS);

    if (!serverStartPolicy.equals("ADMIN_ONLY")) {
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
   * verify services are created.
   *
   * @throws Exception exception
   */
  public void verifyServicesCreated() throws Exception {
    verifyServicesCreated(false);
  }

  /**
   * verify services are created.
   *
   * @param precreateService - if true check services are created for configuredManagedServerCount
   *     number of servers else check for initialManagedServerReplicas number of servers
   * @throws Exception exception
   */
  public void verifyServicesCreated(boolean precreateService) throws Exception {
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

    if (!serverStartPolicy.equals("ADMIN_ONLY")) {
      // check managed server services
      for (int i = 1;
          i <= (precreateService ? configuredManagedServerCount : initialManagedServerReplicas);
          i++) {
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
   * verify servers are ready.
   *
   * @throws Exception exception
   */
  public void verifyServersReady() throws Exception {
    // check admin pod
    logger.info("Checking if admin server is Running");
    TestUtils.checkPodReady(domainUid + "-" + adminServerName, domainNS);

    if (!serverStartPolicy.equals("ADMIN_ONLY")) {
      // check managed server pods
      for (int i = 1; i <= initialManagedServerReplicas; i++) {
        logger.info("Checking if managed server (" + managedServerNameBase + i + ") is Running");
        TestUtils.checkPodReady(domainUid + "-" + managedServerNameBase + i, domainNS);
      }
    } else {
      // check no additional servers are started
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
   * verify nodeport by accessing admin REST endpoint.
   *
   * @param username username
   * @param password password
   * @throws Exception exception
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
      ExecResult result = TestUtils.exec(cmd.toString());
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
   * Verify that we have the channel set for the cluster.
   *
   * @param protocol protocol
   * @param port port
   * @param path path
   * @throws Exception exception
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
            "kubectl exec -n "
                + this.domainNS
                + " "
                + this.getDomainUid()
                + "-"
                + this.adminServerName
                + " /usr/bin/curl ");

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
   * deploy webapp using nodehost and nodeport.
   *
   * @throws Exception exception
   */
  public void deployWebAppViaRest(
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
        .append(" --write-out %{http_code} ");
    logger.info("Command to deploy webapp " + cmd);
    ExecResult result = TestUtils.exec(cmd.toString());
    String output = result.stdout().trim();
    logger.info("curl output " + output + " \n err " + result.stderr());
    if (!output.contains("202")) {
      throw new RuntimeException("FAILURE: Webapp deployment failed with response code " + output);
    }
  }

  /**
   * undeploy webapp using nodehost and nodeport.
   *
   * @throws Exception exception
   */
  public void undeployWebAppViaRest(
      String webappName, String webappLocation, String username, String password) throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append("curl --noproxy '*' --silent  --user ")
        .append(username)
        .append(":")
        .append(password)
        .append(" -H X-Requested-By:MyClient -H Accept:application/json")
        .append(" -H Content-Type:application/json -d \"{}\" ")
        .append(" -X DELETE http://")
        .append(getNodeHost())
        .append(":")
        .append(getNodePort())
        .append("/management/weblogic/latest/edit/appDeployments/")
        .append(webappName)
        .append(" --write-out %{http_code} -o /dev/null");
    logger.fine("Command to undeploy webapp " + cmd);
    ExecResult result = TestUtils.exec(cmd.toString());
    String output = result.stdout().trim();
    if (!output.contains("200")) {
      throw new RuntimeException(
          "FAILURE: Webapp undeployment failed with response code " + output);
    }
  }

  /**
   * deploy webapp using t3 channel port for wlst.
   *
   * @param webappName webappName
   * @param webappLocation webappLocation
   * @param username username
   * @param password password
   * @throws Exception exception
   */
  public void deployWebAppViaWlst(
      String webappName,
      String webappLocation,
      String appLocationInPod,
      String username,
      String password)
      throws Exception {
    deployWebAppViaWlst(webappName, webappLocation, appLocationInPod, username, password, false);
  }

  /**
   * deploy webapp using adminPort or t3 channel port.
   *
   * @param webappName webappName
   * @param webappLocation webappLocation
   * @param appLocationInPod appLocationInPod
   * @param username username
   * @param password password
   * @param useAdminPortToDeploy useAdminPortToDeploy
   * @throws Exception exception
   */
  public void deployWebAppViaWlst(
      String webappName,
      String webappLocation,
      String appLocationInPod,
      String username,
      String password,
      boolean useAdminPortToDeploy)
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

    String t3Url = "t3://" + adminPod + ":";
    if (useAdminPortToDeploy) {
      t3Url = t3Url + domainMap.getOrDefault("adminPort", 7001);
    } else {
      t3Url = t3Url + t3ChannelPort;
    }

    String[] args = {
      appLocationInPod + "/deploywebapp.py",
      BaseTest.getUsername(),
      BaseTest.getPassword(),
      t3Url,
      webappName,
      appLocationInPod + "/" + webappName + ".war",
      clusterName
    };

    TestUtils.callShellScriptByExecToPod(
        adminPod, domainNS, appLocationInPod, "callpyscript.sh", args);
  }

  /**
   * Creates a Connection Factory using JMS.
   *
   * @return connection factory.
   * @throws Exception exception
   */
  public ConnectionFactory createJmsConnectionFactory() throws Exception {
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
   * Test http load balancing using loadBalancerWebPort.
   *
   * @param webappName webappName
   * @throws Exception exception
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
   * call webapp and verify load balancing by checking server name in the response.
   *
   * @param webappName webappName
   * @param verifyLoadBalance verifyLoadBalance
   * @throws Exception exception
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
      StringBuffer curlCmd = new StringBuffer("curl --silent --noproxy '*' ");
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
   * create domain crd.
   *
   * @throws Exception exception
   */
  public void create() throws Exception {
    StringBuffer cmd = new StringBuffer("kubectl create -f ");
    cmd.append(userProjectsDir)
        .append("/weblogic-domains/")
        .append(domainUid)
        .append("/domain.yaml");
    logger.info("Running " + cmd);
    ExecResult result = TestUtils.exec(cmd.toString());
    String outputStr = result.stdout().trim();
    logger.info("Command returned " + outputStr);

    verifyDomainCreated();
  }

  /**
   * delete domain crd using yaml.
   *
   * @throws Exception exception
   */
  public void destroy() throws Exception {
    int replicas = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    StringBuffer cmd = new StringBuffer("kubectl delete -f ");
    cmd.append(userProjectsDir)
        .append("/weblogic-domains/")
        .append(domainUid)
        .append("/domain.yaml");
    ExecResult result = TestUtils.exec(cmd.toString());
    String output = result.stdout().trim();
    logger.info("command to delete domain " + cmd + " \n returned " + output);
    verifyDomainDeleted(replicas);
  }

  /**
   * delete domain using domain name.
   *
   * @throws Exception exception
   */
  public void shutdown() throws Exception {
    int replicas = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    String cmd = "kubectl delete domain " + domainUid + " -n " + domainNS;
    ExecResult result = TestUtils.exec(cmd.toString(), true);
    verifyDomainDeleted(replicas);
  }

  /**
   * shutdown domain by setting serverStartPolicy to NEVER.
   *
   * @throws Exception exception
   */
  public void shutdownUsingServerStartPolicy() throws Exception {
    int replicas = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    String patchStr = "'{\"spec\":{\"serverStartPolicy\":\"NEVER\"}}' ";
    TestUtils.kubectlpatch(domainUid, domainNS, patchStr);
    verifyServerPodsDeleted(replicas);
  }

  /**
   * restart domain by setting serverStartPolicy to IF_NEEDED.
   *
   * @throws Exception exception
   */
  public void restartUsingServerStartPolicy() throws Exception {
    String patchStr = "'{\"spec\":{\"serverStartPolicy\":\"IF_NEEDED\"}}'";
    TestUtils.kubectlpatch(domainUid, domainNS, patchStr);
    verifyPodsCreated();
    verifyServersReady();
  }

  /**
   * add precreateService true in domain.yaml.
   *
   * @throws Exception exception
   */
  public void enablePrecreateService() throws Exception {
    String patchStr = "'{\"spec\":{\"serverService\":{\"precreateService\":true}}}'";
    TestUtils.kubectlpatch(domainUid, domainNS, patchStr);
    verifyServicesCreated(true);
  }

  /**
   * verify domain is deleted.
   *
   * @param replicas replicas
   * @throws Exception exception
   */
  public void verifyDomainDeleted(int replicas) throws Exception {
    logger.info("Inside verifyDomainDeleted, replicas " + replicas);
    TestUtils.checkDomainDeleted(domainUid, domainNS);
    verifyServerPodsDeleted(replicas);
  }

  /**
   * verify server pods are deleted.
   *
   * @param replicas replicas
   * @throws Exception exception
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
   * delete PVC and check PV status released when weblogicDomainStorageReclaimPolicy is Recycle.
   *
   * @throws Exception exception
   */
  public void deletePvcAndCheckPvReleased() throws Exception {
    deletePvcAndCheckPvReleased("create-weblogic-sample-domain-job");
  }

  public void deletePvcAndCheckPvReleased(String jobName) throws Exception {
    StringBuffer cmd = new StringBuffer("kubectl get pv ");
    String pvBaseName = (String) pvMap.get("baseName");
    if (domainUid != null) pvBaseName = domainUid + "-" + pvBaseName;
    cmd.append(pvBaseName).append("-pv -n ").append(domainNS);

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() == 0) {
      logger.info("Status of PV before deleting PVC " + result.stdout());
    }
    TestUtils.deletePvc(pvBaseName + "-pvc", domainNS, domainUid, jobName);
    String reclaimPolicy = (String) domainMap.get("weblogicDomainStorageReclaimPolicy");
    boolean pvReleased = TestUtils.checkPvReleased(pvBaseName, domainNS);
    if (reclaimPolicy != null && reclaimPolicy.equals("Recycle") && !pvReleased) {
      throw new RuntimeException(
          "ERROR: pv for " + domainUid + " still exists after the pvc is deleted, exiting!");
    } else {
      logger.info("PV is released when PVC is deleted");
    }
  }

  /**
   * create domain on existing directory.
   *
   * @throws Exception exception
   */
  public void createDomainOnExistingDirectory() throws Exception {

    // use krun.sh so that the dir check can work on shared cluster/remote k8s cluster env as well
    String cmd =
        BaseTest.getProjectRoot()
            + "/src/integration-tests/bash/krun.sh -m "
            + domainMap.get("persistentVolumeClaimName")
            + ":/pvc-"
            + domainMap.get("domainUID")
            + " -c \"ls -ltr /pvc-"
            + domainMap.get("domainUID")
            + "/domains/"
            + domainMap.get("domainUID")
            + "\"";
    logger.info("making sure the domain directory exists by running " + cmd);
    ExecResult result = TestUtils.exec(cmd);
    // logger.info("Command result " + result.stdout() + " err =" + result.stderr());
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
   * access admin console using load balancer web port for Apache load balancer.
   *
   * @throws Exception exception
   */
  public void verifyAdminConsoleViaLB() throws Exception {
    if (!loadBalancer.equals("APACHE")) {
      logger.info("This check is done only for APACHE load balancer");
      return;
    }
    String nodePortHost = getHostNameForCurl();
    int nodePort = getAdminSericeLbNodePort();
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

    ExecResult result = TestUtils.exec(cmd.toString());

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
   * Get the name of the administration server in the domain.
   *
   * @return the name of the admin server
   */
  public String getAdminServerName() {
    return adminServerName;
  }

  /**
   * Get the name of the cluster in the domain.
   *
   * @return the name of the cluster
   */
  public String getClusterName() {
    return clusterName;
  }

  /**
   * Get the namespace in which the domain is running.
   *
   * @return the name of the domain name space
   */
  public String getDomainNs() {
    return domainNS;
  }

  /**
   * test liveness probe for managed server 1.
   *
   * @throws Exception exception
   */
  public void testWlsLivenessProbe() throws Exception {

    // test managed server1 pod auto restart
    String serverName = managedServerNameBase + "1";
    TestUtils.testWlsLivenessProbe(domainUid, serverName, domainNS);
  }

  /**
   * Get number of server addresses in cluster service endpoint.
   *
   * @param clusterName cluster name
   * @return number of server addresses
   * @throws Exception exception
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

    ExecResult result = TestUtils.exec(cmd.toString());
    logger.info("Cluster service Endpoint " + result.stdout());
    return new StringTokenizer(result.stdout(), ",").countTokens();
  }

  private int getAdminSericeLbNodePort() throws Exception {

    String adminServerLbNodePortService = domainUid + "-apache-webtier";

    StringBuffer cmd = new StringBuffer("kubectl get services -n ");
    cmd.append(domainNS)
        .append(" -o jsonpath='{.items[?(@.metadata.name == \"")
        .append(adminServerLbNodePortService)
        .append("\")].spec.ports[0].nodePort}'");

    logger.info("Cmd to get the admins service node port " + cmd);

    ExecResult result = TestUtils.exec(cmd.toString());
    return new Integer(result.stdout().trim()).intValue();
  }

  /**
   * Create a map with attributes required to create PV/PVC and create PV dir by calling
   * PersistentVolume.
   *
   * @throws Exception If the file create-pv-pvc-inputs.yaml does not exist, is a directory rather
   *     than a regular file, or for some other reason cannot be opened for reading. or if an I/O
   *     error occurs or any errors while creating PV dir or generating PV/PVC input file or any
   *     errors while executing sample create-pv-pvc.sh script
   */
  protected void createPv() throws Exception {

    Yaml yaml = new Yaml();
    InputStream pvis =
        new FileInputStream(
            new File(
                BaseTest.getResultDir()
                    + "/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml"));
    pvMap = yaml.load(pvis);
    pvis.close();
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

    String cmd =
        BaseTest.getProjectRoot()
            + "/src/integration-tests/bash/krun.sh -m "
            // + BaseTest.getPvRoot()
            + "/scratch:/scratch -c \"ls -ltr /scratch "
            + BaseTest.getPvRoot()
            + " "
            + BaseTest.getPvRoot()
            + "/acceptance_test_pv"
            + "\"";
    logger.info("Check PVROOT by running " + cmd);
    ExecResult result = ExecCommand.exec(cmd);
    logger.info("ls -ltr output " + result.stdout() + " err " + result.stderr());
  }

  /**
   * Verify domain server pods get restarted after a property change.
   *
   * @param oldPropertyString - the old property value
   * @param newPropertyString - the new property value
   * @throws Exception - IOException or errors occurred if the tested server is not restarted
   */
  public void verifyDomainServerPodRestart(String oldPropertyString, String newPropertyString)
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
   * Verify domain server pods get restarted after the property change by kubectl apply -f new
   * domain yaml file with added/changed property.
   *
   * @param fileNameWithChangedProperty - the fragment of domain yaml file with new added property
   *     change
   * @throws Exception - IOException or errors occurred if the tested server is not restarted
   */
  public void verifyDomainServerPodRestart(String fileNameWithChangedProperty) throws Exception {
    logger.info("Inside testDomainServerPodRestart domainYamlWithChangedProperty");

    String newDomainYamlFile =
        BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain_new.yaml";
    String domainYamlFile =
        BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
    String fileWithChangedProperty =
        BaseTest.getProjectRoot()
            + "/integration-tests/src/test/resources/"
            + fileNameWithChangedProperty;

    // copy the original domain.yaml to domain_new.yaml
    TestUtils.copyFile(domainYamlFile, newDomainYamlFile);

    // append the file with changed property to the end of domain_new.yaml
    Files.write(
        Paths.get(newDomainYamlFile),
        Files.readAllBytes(Paths.get(fileWithChangedProperty)),
        StandardOpenOption.APPEND);

    // kubectl apply the new constructed domain_new.yaml
    StringBuffer command = new StringBuffer();
    command.append("kubectl apply  -f ").append(newDomainYamlFile);
    logger.info("kubectl execut with command: " + command.toString());
    TestUtils.exec(command.toString());

    // verify the servers in the domain are being restarted in a sequence
    verifyAdminServerRestarted();
    verifyManagedServersRestarted();

    // make domain.yaml include the new changed property
    TestUtils.copyFile(newDomainYamlFile, domainYamlFile);

    logger.info("Done - testDomainServerPodRestart with domainYamlWithChangedProperty");
  }

  /**
   * Get runtime server yaml file and verify the changed property is in that file.
   *
   * @param changedProperty - the changed/added property
   * @param serverName - server name that is being tested
   * @throws Exception - test FAILURE Exception if the changed property is not found in the server
   *     yaml file
   */
  public void findServerPropertyChange(String changedProperty, String serverName) throws Exception {
    logger.info("Inside findServerPropertyChange");
    // get runtime server pod yaml file
    String outDir = BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/";
    StringBuffer command = new StringBuffer();
    command
        .append("kubectl get po/")
        .append(
            domainUid
                + "-"
                + serverName
                + " -o yaml -n "
                + domainNS
                + "|"
                + "grep "
                + "\""
                + changedProperty
                + "\"");
    logger.info("kubectl execut with command: " + command.toString());
    TestUtils.exec(command.toString());

    String result = ((TestUtils.exec(command.toString())).stdout());
    logger.info(
        "in the method findServerPropertyChange, " + command.toString() + " return " + result);
    if (!result.contains(changedProperty)) {
      throw new Exception(
          "FAILURE: didn't find the property: " + changedProperty + " for the server" + serverName);
    }

    logger.info("Done - findServerPropertyChange");
  }

  /**
   * verify that admin server pod gets restarted.
   *
   * @throws Exception exception
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
   * @throws Exception exception
   */
  public void verifyManagedServersRestarted() throws Exception {
    if (!serverStartPolicy.equals("ADMIN_ONLY")) {
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

  /**
   * Create a Kubernetes secret and label the secret with domainUid. This secret is used for
   * weblogicCredentialsSecretName in the domain inputs.
   *
   * @throws Exception when the kubectl create secret command fails or label secret fails
   */
  protected void createSecret() throws Exception {
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
    TestUtils.exec(labelCmd);
  }

  /**
   * Creates a directory using domainUid under userProjects weblogic-domains location. Creates
   * weblogic-domain-values.yaml files using the domain map inputs at this new location.
   *
   * @throws Exception if the dir/file can not be created
   */
  protected void generateInputYaml() throws Exception {
    Path parentDir =
        Files.createDirectories(Paths.get(userProjectsDir + "/weblogic-domains/" + domainUid));
    generatedInputYamlFile = parentDir + "/weblogic-domain-values.yaml";
    TestUtils.createInputFile(domainMap, generatedInputYamlFile);
  }

  /**
   * Copy create-domain.py if domain Map contains createDomainPyScript, git clone docker-images for
   * domain in image and call create-domain.sh script based on the domain type. Append
   * configOverrides to domain.yaml.
   *
   * @param outputDir directory for the generated Kubernetes YAML files for the domain when
   *     create-domain.sh is called
   * @throws Exception if git clone fails or if createDomainPyScript can not be copied or if the
   *     cluster topology file can not be copied or if create-domain.sh fails
   */
  protected void callCreateDomainScript(String outputDir) throws Exception {

    // call different create domain script based on the domain type
    final String createDomainScriptCmd = prepareCmdToCallCreateDomainScript(outputDir);

    // clone docker sample from github and copy create domain py script for domain in image case
    if (domainMap.containsKey("domainHomeImageBase")) {
      gitCloneDockerImagesSample();
    }

    copyDomainTemplate(domainMap);

    // copy create domain py script if domain map contains createDomainPyScript
    copyCreateDomainPy();

    // change CLUSTER_TYPE to CONFIGURED in create-domain-job-template.yaml for configured cluster
    // in domain on pv
    // as samples only support DYNAMIC cluster or copy config cluster topology for domain in image
    changeClusterTypeInCreateDomainJobTemplate();

    logger.info("Running " + createDomainScriptCmd);
    ExecResult result = ExecCommand.exec(createDomainScriptCmd, true);
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

    // for remote k8s cluster and domain in image case, push the domain image to OCIR
    if (domainMap.containsKey("domainHomeImageBase") && BaseTest.SHARED_CLUSTER) {
      String image =
          System.getenv("REPO_REGISTRY") + "/weblogick8s/domain-home-in-image:" + imageTag;
      TestUtils.loginAndPushImageToOcir(image);

      // create ocir registry secret in the same ns as domain which is used while pulling the domain
      // image
      TestUtils.createDockerRegistrySecret(
          "ocir-domain",
          System.getenv("REPO_REGISTRY"),
          System.getenv("REPO_USERNAME"),
          System.getenv("REPO_PASSWORD"),
          System.getenv("REPO_EMAIL"),
          domainNS);
    }

    // write configOverride and configOverrideSecrets to domain.yaml and/or create domain
    if (domainMap.containsKey("configOverrides") || domainMap.containsKey("domainHomeImageBase")) {
      appendToDomainYamlAndCreate();
    }
  }

  protected void createLoadBalancer() throws Exception {
    Map<String, Object> lbMap = new HashMap<String, Object>();
    lbMap.put("domainUID", domainUid);
    lbMap.put("namespace", domainNS);
    lbMap.put("host", domainUid + ".org");
    lbMap.put("serviceName", domainUid + "-cluster-" + domainMap.get("clusterName"));
    if (voyager) {
      lbMap.put("loadBalancer", "VOYAGER");
      lbMap.put(
          "loadBalancerWebPort",
          domainMap.getOrDefault("voyagerWebPort", new Integer(loadBalancerWebPort)));
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

  private void callWebAppAndWaitTillReady(String curlCmd) throws Exception {
    for (int i = 0; i < maxIterations; i++) {
      ExecResult result = TestUtils.exec(curlCmd);
      String responseCode = result.stdout().trim();
      if (!responseCode.equals("200")) {
        logger.info(
            "callWebApp did not return 200 status code, got "
                + responseCode
                + ", iteration "
                + i
                + " of "
                + maxIterations);
        if (i == (maxIterations - 1)) {
          throw new RuntimeException(
              "FAILURE: callWebApp did not return 200 status code, got " + responseCode);
        }
        try {
          Thread.sleep(waitTime * 1000);
        } catch (InterruptedException ignore) {
          // no-op
        }
      } else {
        logger.info("callWebApp returned 200 response code, iteration " + i);
        break;
      }
    }
  }

  private void callWebAppAndCheckForServerNameInResponse(
      String curlCmd, boolean verifyLoadBalancing) throws Exception {
    callWebAppAndCheckForServerNameInResponse(curlCmd, verifyLoadBalancing, 50);
  }

  private void callWebAppAndCheckForServerNameInResponse(
      String curlCmd, boolean verifyLoadBalancing, int maxIterations) throws Exception {
    // map with server names and boolean values
    HashMap<String, Boolean> managedServers = new HashMap<String, Boolean>();
    for (int i = 1; i <= TestUtils.getClusterReplicas(domainUid, clusterName, domainNS); i++) {
      managedServers.put(domainUid + "-" + managedServerNameBase + i, new Boolean(false));
    }
    logger.info("Calling webapp " + maxIterations + " times " + curlCmd);
    // number of times to call webapp

    for (int i = 0; i < maxIterations; i++) {
      ExecResult result = ExecCommand.exec(curlCmd.toString());
      logger.info("webapp invoked successfully for curlCmd:" + curlCmd);
      if (verifyLoadBalancing) {
        String response = result.stdout().trim();
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

  /**
   * Reads the create-domain-inputs.yaml from samples and overrides with attribute in input domain
   * map. Initializes the variables for the attributes in the map to be used later.
   *
   * @param inputDomainMap domain, LB, PV and custom input attributes for the domain
   * @throws Exception if removing the results dir fails or if create-domain-inputs.yaml cannot be
   *     accessed to read or if creating config map or secret fails for configoverrides
   */
  protected void initialize(Map<String, Object> inputDomainMap) throws Exception {
    imageTag = BaseTest.getWeblogicImageTag();
    imageName = BaseTest.getWeblogicImageName();
    domainMap = inputDomainMap;
    this.userProjectsDir = BaseTest.getUserProjectsDir();
    this.projectRoot = BaseTest.getProjectRoot();

    // copy samples to RESULT_DIR
    TestUtils.exec(
        "cp -rf " + BaseTest.getProjectRoot() + "/kubernetes/samples " + BaseTest.getResultDir());

    this.voyager =
        (System.getenv("LB_TYPE") != null && System.getenv("LB_TYPE").equalsIgnoreCase("VOYAGER"))
            || (inputDomainMap.containsKey("loadBalancer")
                && ((String) inputDomainMap.get("loadBalancer")).equalsIgnoreCase("VOYAGER"));

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
    clusterType = (String) domainMap.getOrDefault("clusterType", "DYNAMIC");
    serverStartPolicy = ((String) domainMap.get("serverStartPolicy")).trim();

    if (exposeAdminT3Channel) {
      domainMap.put("t3PublicAddress", TestUtils.getHostName());
    }

    if (System.getenv("IMAGE_NAME_WEBLOGIC") != null) {
      imageName = System.getenv("IMAGE_NAME_WEBLOGIC");
      logger.info("IMAGE_NAME_WEBLOGIC " + imageName);
    }

    if (System.getenv("IMAGE_TAG_WEBLOGIC") != null) {
      imageTag = System.getenv("IMAGE_TAG_WEBLOGIC");
      logger.info("IMAGE_TAG_WEBLOGIC " + imageTag);
    }
    domainMap.put("logHome", "/shared/logs/" + domainUid);
    if (!domainMap.containsKey("domainHomeImageBase")) {
      domainMap.put("domainHome", "/shared/domains/" + domainUid);
      domainMap.put("image", imageName + ":" + imageTag);
      if (System.getenv("IMAGE_PULL_SECRET_WEBLOGIC") != null) {
        domainMap.put("imagePullSecretName", System.getenv("IMAGE_PULL_SECRET_WEBLOGIC"));
      } else {
        domainMap.put("imagePullSecretName", "docker-store");
      }
    } else {
      // use default image attibute value for JENKINS and standalone runs and for SHARED_CLUSTER use
      // below
      if (BaseTest.SHARED_CLUSTER) {
        domainMap.put(
            "image",
            System.getenv("REPO_REGISTRY") + "/weblogick8s/domain-home-in-image:" + imageTag);
        domainMap.put("imagePullSecretName", "ocir-domain");
        domainMap.put("imagePullPolicy", "Always");
      }
    }

    if (domainMap.containsKey("domainHomeImageBuildPath")) {
      domainHomeImageBuildPath =
          BaseTest.getResultDir()
              + "/"
              + ((String) domainMap.get("domainHomeImageBuildPath")).trim();
      domainMap.put(
          "domainHomeImageBuildPath",
          BaseTest.getResultDir()
              + "/"
              + ((String) domainMap.get("domainHomeImageBuildPath")).trim());
    }

    // remove null values if any attributes
    domainMap.values().removeIf(Objects::isNull);

    // create config map and secret for custom sit config
    createConfigMapAndSecretForSitConfig();
  }

  private void copyDomainTemplate(Map<String, Object> inputDomainMap) throws IOException {
    if (inputDomainMap.containsKey("customDomainTemplate")) {
      Files.copy(
          Paths.get((String) inputDomainMap.get("customDomainTemplate")),
          Paths.get(BaseTest.getResultDir() + "/samples/scripts/common/domain-template.yaml"),
          StandardCopyOption.REPLACE_EXISTING);
    }
    logger.log(Level.FINEST, "Domain Template");
    byte[] readAllBytes =
        Files.readAllBytes(
            Paths.get(BaseTest.getResultDir() + "/samples/scripts/common/domain-template.yaml"));
    logger.log(Level.FINEST, new String(readAllBytes, StandardCharsets.UTF_8));
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

    ExecResult result = TestUtils.exec(cmd);
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
    ExecResult result = TestUtils.exec(cmd.toString());
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

  /**
   * Remove docker-images sample directory if exists and clone latest from github for domain home in
   * image.
   *
   * @throws Exception if could not run the command successfully to clone of docker-images sample
   *     from github
   */
  private void gitCloneDockerImagesSample() throws Exception {
    if (!domainHomeImageBuildPath.isEmpty()) {
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
      TestUtils.exec(removeAndClone.toString());
    }
  }

  /**
   * Append configOverrides and configOverrideSecrets section to the generated domain.yaml and
   * create the domain crd by calling kubectl create on the generated domain.yaml.
   *
   * @throws Exception if any error occurs writing to the file or if could not run kubectl create
   *     command
   */
  private void appendToDomainYamlAndCreate() throws Exception {

    String domainYaml =
        BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";

    if (domainMap.containsKey("configOverrides")) {
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

      Files.write(Paths.get(domainYaml), contentToAppend.getBytes(), StandardOpenOption.APPEND);
    }

    String command = "kubectl create -f " + domainYaml;
    ExecResult result = TestUtils.exec(command);
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

  /**
   * Option to provide custom create-domain.py script. Copies create-domain.py to the correct
   * location if domain map contains createDomainPyScript attribute.
   *
   * @throws IOException if error occurs when readin or writing the file
   */
  private void copyCreateDomainPy() throws IOException {

    if (domainMap.containsKey("createDomainPyScript")) {
      if (domainMap.containsKey("domainHomeImageBase")) {
        // copy create domain py script to cloned location for domain in image case
        if (domainMap.containsKey("createDomainPyScript")) {
          Files.copy(
              new File(BaseTest.getProjectRoot() + "/" + domainMap.get("createDomainPyScript"))
                  .toPath(),
              new File(domainHomeImageBuildPath + "/container-scripts/create-wls-domain.py")
                  .toPath(),
              StandardCopyOption.REPLACE_EXISTING);
        }
      } else if (domainMap.containsKey("rcuDatabaseURL")) {
        Files.copy(
            new File(BaseTest.getProjectRoot() + "/" + domainMap.get("createDomainPyScript"))
                .toPath(),
            new File(
                    BaseTest.getResultDir()
                        + "/samples/scripts/create-fmw-infrastructure-domain/common/createFMWDomain.py")
                .toPath(),
            StandardCopyOption.REPLACE_EXISTING);
      } else {
        // domain on pv case
        Files.copy(
            new File(BaseTest.getProjectRoot() + "/" + domainMap.get("createDomainPyScript"))
                .toPath(),
            new File(
                    BaseTest.getResultDir()
                        + "/samples/scripts/create-weblogic-domain/domain-home-on-pv/wlst/create-domain.py")
                .toPath(),
            StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  /**
   * Prepare the command to call create-domain.sh based on the domain type.
   *
   * @param outputDir directory for the generated Kubernetes YAML files for the domain when
   *     create-domain.sh is called
   * @return the command
   */
  private String prepareCmdToCallCreateDomainScript(String outputDir) {

    StringBuffer createDomainScriptCmd = new StringBuffer(BaseTest.getResultDir());
    // call different create-domain.sh based on the domain type
    if (domainMap.containsKey("domainHomeImageBase")) {

      createDomainScriptCmd
          .append(
              "/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain.sh -u ")
          .append(BaseTest.getUsername())
          .append(" -p ")
          .append(BaseTest.getPassword())
          .append(" -k -i ");
    } else if (domainMap.containsKey("rcuDatabaseURL")) {
      createDomainScriptCmd.append(
          "/samples/scripts/create-fmw-infrastructure-domain/create-domain.sh -v -i ");
    } else {
      createDomainScriptCmd.append(
          "/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain.sh -v -i ");
    }
    createDomainScriptCmd.append(generatedInputYamlFile);

    // skip executing yaml if configOverrides or domain in image
    if (!domainMap.containsKey("configOverrides")
        && !domainMap.containsKey("domainHomeImageBase")) {
      createDomainScriptCmd.append(" -e ");
    }

    createDomainScriptCmd.append(" -o ").append(outputDir);
    return createDomainScriptCmd.toString();
  }

  /**
   * Option to provide cluster type. Change cluster type in domain template to CONFIGURED or use
   * configured cluster topology if clusterType is CONFIGURED.
   *
   * @throws Exception when errors occured during reading/writing the file or executing the command
   *     to change the value in create-domain-job-template.yaml
   */
  private void changeClusterTypeInCreateDomainJobTemplate() throws Exception {

    // change CLUSTER_TYPE to CONFIGURED in create-domain-job-template.yaml for configured cluster
    // as samples only support DYNAMIC cluster

    // domain in image
    if (domainMap.containsKey("customWdtTemplate")) {
      TestUtils.copyFile(
          (String) domainMap.get("customWdtTemplate"),
          BaseTest.getResultDir()
              + "/docker-images/OracleWebLogic/samples/12213-domain-home-in-image-wdt/simple-topology.yaml");
      ExecResult exec =
          TestUtils.exec(
              "cat "
                  + BaseTest.getResultDir()
                  + "/docker-images/OracleWebLogic/samples/12213-domain-home-in-image-wdt/simple-topology.yaml");
      logger.log(Level.FINEST, exec.stdout());
    } else if (clusterType.equalsIgnoreCase("CONFIGURED")) {
      // domain on pv
      StringBuffer createDomainJobTemplateFile = new StringBuffer(BaseTest.getResultDir());
      createDomainJobTemplateFile.append(
          "/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain-job-template.yaml");
      TestUtils.exec("sed -i -e 's?DYNAMIC?CONFIGURED?g' " + createDomainJobTemplateFile);
    }
  }

  public int getLoadBalancerWebPort() {
    return loadBalancerWebPort;
  }

  /**
   * Shut down a ms by setting serverStartPolicy to NEVER.
   *
   * @param msName - a managed server name to be stopped
   * @throws Exception exception
   */
  public void shutdownManagedServerUsingServerStartPolicy(String msName) throws Exception {
    logger.info("About to shutdown managed server <" + msName + ">");
    String patchStr =
        "'{\"spec\":{\"managedServers\":[{\"serverName\":\""
            + msName
            + "\",\"serverStartPolicy\":\"NEVER\"}]}}' ";
    TestUtils.kubectlpatch(domainUid, domainNS, patchStr);

    TestUtils.checkPodDeleted(domainUid + "-" + msName, domainNS);
  }

  /**
   * Restart a ms by setting serverStartPolicy to IF_NEEDED.
   *
   * @param msName - a managed server name to be started
   * @throws Exception exception
   */
  public void restartManagedServerUsingServerStartPolicy(String msName) throws Exception {
    logger.info("About to restart managed server <" + msName + "> ");
    String patchStr =
        "'{\"spec\":{\"managedServers\":[{\"serverName\":\""
            + msName
            + "\",\"serverStartPolicy\":\"IF_NEEDED\"}]}}'";
    TestUtils.kubectlpatch(domainUid, domainNS, patchStr);

    TestUtils.checkPodCreated(domainUid + "-" + msName, domainNS);
    TestUtils.checkPodReady(domainUid + "-" + msName, domainNS);
  }

  /**
   * Run the shell script to build WAR, EAR or JAR file and deploy the App in the admin pod.
   *
   * @param webappName - Web App Name to be deployed
   * @param scriptName - a shell script to build WAR, EAR or JAR file and deploy the App in the
   *     admin pod
   * @param username - weblogic user name
   * @param password - weblogc password
   * @param args - optional args to add for script if needed
   * @throws Exception exception
   */
  public void callShellScriptToBuildDeployAppInPod(
      String webappName, String scriptName, String username, String password, String... args)
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
        .append(String.join(" ", args).toString())
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
   * create config map and label with domainUid and create secret used in custom situational
   * configuration which contains hostname, db user, db password.
   *
   * @throws Exception when any of the kubectl commands to create config map, label, secret fails or
   *     if could not run them
   */
  private void createConfigMapAndSecretForSitConfig() throws Exception {

    if ((domainMap.get("configOverrides") != null)
        && (domainMap.get("configOverridesFile") != null)) {
      // write hostname in config file for public address
      String configOverridesFile = domainMap.get("configOverridesFile").toString();

      // create configmap
      String cmd =
          "kubectl -n "
              + domainNS
              + " create cm "
              + domainUid
              + "-"
              + domainMap.get("configOverrides")
              + " --from-file "
              + configOverridesFile;
      TestUtils.exec(cmd);

      // create label for configmap
      cmd =
          "kubectl -n "
              + domainNS
              + " label cm "
              + domainUid
              + "-"
              + domainMap.get("configOverrides")
              + " weblogic.domainUID="
              + domainUid;
      TestUtils.exec(cmd);

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
      TestUtils.exec(cmd);
    }
  }

  /**
   * Create dir to save Web App files Copy the shell script file and all App files over to the admin
   * pod Run the shell script to build WAR, EAR or JAR file and deploy the App in the admin pod.
   *
   * @param appName - Java App name to be deployed
   * @param scriptName - a shell script to build WAR, EAR or JAR file and deploy the App in the
   *     admin pod
   * @param username - weblogic user name
   * @param password - weblogc password
   * @param args - by default, a WAR file is created for a Web App and a EAR file is created for EJB
   *     App. this varargs gives a client a chance to change EJB's archive extenyion to JAR
   * @throws Exception exception
   */
  public void buildDeployJavaAppInPod(
      String appName, String scriptName, String username, String password, String... args)
      throws Exception {
    final String adminServerPod = domainUid + "-" + adminServerName;

    String appLocationOnHost = BaseTest.getAppLocationOnHost() + "/" + appName;
    String appLocationInPod = BaseTest.getAppLocationInPod() + "/" + appName;
    final String scriptPathOnHost = BaseTest.getAppLocationOnHost() + "/" + scriptName;
    final String scriptPathInPod = BaseTest.getAppLocationInPod() + "/" + scriptName;

    // Default values to build archive file
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
        appName, scriptName, username, password, infoDirName, archiveExt);
  }
}

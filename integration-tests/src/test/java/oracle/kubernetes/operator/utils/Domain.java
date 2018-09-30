// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import oracle.kubernetes.operator.BaseTest;
import org.yaml.snakeyaml.Yaml;

/** Domain class with all the utility methods for a Domain. */
public class Domain {
  public static final String CREATE_DOMAIN_JOB_MESSAGE =
      "Domain base_domain was created and will be started by the WebLogic Kubernetes Operator";

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  private Map<String, Object> domainMap;
  private Map<String, Object> lbMap;

  // attributes from domain properties
  private String domainUid = "";
  // default values as in create-weblogic-domain-inputs.yaml, generated yaml file will have the
  // customized property values
  private String domainNS = "weblogic-domain";
  private String adminServerName = "admin-server";
  private String managedServerNameBase = "managed-server";
  private int initialManagedServerReplicas = 2;
  private boolean exposeAdminT3Channel = false;
  private int t3ChannelPort = 30012;
  private String clusterName = "cluster-1";
  private String loadBalancer = "TRAEFIK";
  private int loadBalancerWebPort = 30305;
  private String userProjectsDir = "";
  private String projectRoot = "";

  private String createDomainScript = "";
  private String inputTemplateFile = "";
  private String generatedDomainYamlFile;
  private String generatedLBYamlFile;

  private static int maxIterations = BaseTest.getMaxIterationsPod(); // 50 * 5 = 250 seconds
  private static int waitTime = BaseTest.getWaitTimePod();

  public Domain(String inputYaml) throws Exception {
    Yaml yaml = new Yaml();
    InputStream domain_is = this.getClass().getClassLoader().getResourceAsStream(inputYaml);
    domainMap = yaml.load(domain_is);
    domain_is.close();

    initialize(inputYaml);
    createPV();
    createSecret();
    generateInputYaml();
    callCreateDomainScript();
    callCreateLBScript();
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

    if (domainMap.get("startupControl") == null
        || (domainMap.get("startupControl") != null
            && !domainMap.get("startupControl").toString().trim().equals("ADMIN"))) {
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
              + "-extchannel-t3channel) is created");
      TestUtils.checkServiceCreated(
          domainUid + "-" + adminServerName + "-extchannel-t3channel", domainNS);
    }
    if (domainMap.get("startupControl") == null
        || (domainMap.get("startupControl") != null
            && !domainMap.get("startupControl").toString().trim().equals("ADMIN"))) {
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
    if (domainMap.get("startupControl") == null
        || (domainMap.get("startupControl") != null
            && !domainMap.get("startupControl").toString().trim().equals("ADMIN"))) {

      // check managed server pods
      for (int i = 1; i <= initialManagedServerReplicas; i++) {
        logger.info("Checking if managed server (" + managedServerNameBase + i + ") is Running");
        TestUtils.checkPodReady(domainUid + "-" + managedServerNameBase + i, domainNS);
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
    String nodePortHost = TestUtils.getHostName();
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
      String webappName, String webappLocation, String username, String password) throws Exception {

    TestUtils.kubectlcp(
        webappLocation,
        "/shared/applications/testwebapp.war",
        domainUid + "-" + adminServerName,
        domainNS);

    TestUtils.kubectlcp(
        projectRoot + "/integration-tests/src/test/resources/deploywebapp.py",
        "/shared/deploywebapp.py",
        domainUid + "-" + adminServerName,
        domainNS);

    TestUtils.kubectlcp(
        projectRoot + "/integration-tests/src/test/resources/calldeploywebapp.sh",
        "/shared/calldeploywebapp.sh",
        domainUid + "-" + adminServerName,
        domainNS);

    callShellScriptByExecToPod(username, password, webappName);
  }

  /**
   * Test http load balancing using loadBalancerWebPort
   *
   * @param webappName
   * @throws Exception
   */
  public void verifyWebAppLoadBalancing(String webappName) throws Exception {
    if (!loadBalancer.equals("NONE")) {
      // url
      StringBuffer testAppUrl = new StringBuffer("http://");
      testAppUrl
          .append(TestUtils.getHostName())
          .append(":")
          .append(loadBalancerWebPort)
          .append("/");

      if (loadBalancer.equals("APACHE")) {
        testAppUrl.append("weblogic/");
      }
      testAppUrl.append(webappName).append("/");

      // curl cmd to call webapp
      StringBuffer curlCmd = new StringBuffer("curl --silent --show-error --noproxy ");
      curlCmd.append(TestUtils.getHostName()).append(" ");
      if (loadBalancer.equals("TRAEFIK")) {
        curlCmd.append(" -H 'host: " + domainUid + ".org' ");
      }
      curlCmd.append(testAppUrl.toString());

      // curl cmd to get response code
      StringBuffer curlCmdResCode = new StringBuffer(curlCmd.toString());
      curlCmdResCode.append(" --write-out %{http_code} -o /dev/null");

      logger.info("Running " + curlCmdResCode);
      // call webapp iteratively till its deployed/ready
      callWebAppAndWaitTillReady(curlCmdResCode.toString());

      // execute curl and look for the managed server name in response
      callWebAppAndCheckForServerNameInResponse(curlCmd.toString());
      // logger.info("curlCmd "+curlCmd);

    }
  }

  /**
   * startup the domain
   *
   * @throws Exception
   */
  public void create() throws Exception {
    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(BaseTest.getProjectRoot())
        .append(" && helm install kubernetes/charts/weblogic-domain");
    cmd.append(" --name ")
        .append(domainMap.get("domainUID"))
        .append(" --values ")
        .append(generatedDomainYamlFile)
        .append(" --set createWebLogicDomain=false --namespace ")
        .append(domainNS)
        .append(" --wait");
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
   * shutdown the domain
   *
   * @throws Exception
   */
  public void destroy() throws Exception {
    int replicas = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    String cmd = "helm del --purge " + domainUid;
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    String output = result.stdout().trim();
    logger.info("command to delete domain " + cmd + " \n returned " + output);
    verifyDomainDeleted(replicas);
  }

  public void shutdown() throws Exception {
    String cmd = "kubectl delete domain " + domainUid + " -n " + domainNS;
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    String output = result.stdout().trim();
    logger.info("command to delete domain " + cmd + " \n returned " + output);
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
    TestUtils.checkPodDeleted(domainUid + "-" + adminServerName, domainNS);

    for (int i = 1; i <= replicas; i++) {
      TestUtils.checkPodDeleted(domainUid + "-" + managedServerNameBase + i, domainNS);
    }
  }

  public Map<String, Object> getDomainMap() {
    return domainMap;
  }

  public void deletePVCAndCheckPVReleased() throws Exception {
    StringBuffer cmd = new StringBuffer("kubectl get pv ");
    cmd.append(domainUid).append("-weblogic-domain-pv -n ").append(domainNS);

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() == 0) {
      logger.info("Status of PV before deleting PVC " + result.stdout());
    }
    TestUtils.deletePVC(domainUid + "-weblogic-domain-pvc", domainNS);
    String reclaimPolicy = domainMap.get("weblogicDomainStorageReclaimPolicy").toString();
    boolean pvReleased = TestUtils.checkPVReleased(domainUid, domainNS);
    if (reclaimPolicy != null && reclaimPolicy.equals("Recycle") && !pvReleased) {
      throw new RuntimeException(
          "ERROR: pv for " + domainUid + " still exists after the pvc is deleted, exiting!");
    } else {
      logger.info("PV is released when PVC is deleted");
    }
  }

  public void createDomainOnExistingDirectory() throws Exception {
    String domainStoragePath = domainMap.get("weblogicDomainStoragePath").toString();
    String domainDir = domainStoragePath + "/domain/" + domainMap.get("domainName").toString();
    logger.info("making sure the domain directory exists");
    if (domainDir != null && !(new File(domainDir).exists())) {
      throw new RuntimeException(
          "FAIL: the domain directory " + domainDir + " does not exist, exiting!");
    }
    logger.info("Run the script to create domain");
    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(BaseTest.getProjectRoot())
        .append(" && helm install kubernetes/charts/weblogic-domain");
    cmd.append(" --name ")
        .append(domainMap.get("domainUID"))
        .append(" --values ")
        .append(generatedDomainYamlFile)
        .append(" --namespace ")
        .append(domainNS)
        .append(" --wait");
    logger.info("Running " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() == 1) {
      logger.info("[SUCCESS] create domain job failed, this is the expected behavior");
    } else {
      throw new RuntimeException(
          "FAIL: unexpected result, create domain job exit code: " + result.exitValue());
    }
  }

  public void verifyAdminConsoleViaLB() throws Exception {
    if (!loadBalancer.equals("APACHE")) {
      logger.info("This check is done only for APACHE load balancer");
      return;
    }
    String nodePortHost = TestUtils.getHostName();
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
    // k8s job mounts PVROOT /scratch/<usr>/wl_k8s_test_results to /scratch
    new PersistentVolume("/scratch/acceptance_test_pv/persistentVolume-" + domainUid);

    // set pv path, weblogicDomainStoragePath in domain props file is ignored
    domainMap.put(
        "weblogicDomainStoragePath",
        BaseTest.getPvRoot() + "/acceptance_test_pv/persistentVolume-" + domainUid);
  }

  private void createSecret() throws Exception {
    new Secret(
        domainNS,
        domainMap.getOrDefault("secretName", domainUid + "-weblogic-credentials").toString(),
        BaseTest.getUsername(),
        BaseTest.getPassword());
    domainMap.put("weblogicCredentialsSecretName", domainUid + "-weblogic-credentials");
  }

  private void generateInputYaml() throws Exception {
    Path parentDir =
        Files.createDirectories(Paths.get(userProjectsDir + "/weblogic-domains/" + domainUid));
    generatedDomainYamlFile = parentDir + "/weblogic-domain-values.yaml";
    TestUtils.createInputFile(domainMap, generatedDomainYamlFile);

    if (!loadBalancer.equals("NONE")) {
      generatedLBYamlFile = parentDir + "loadBalancer-values.yaml";
      TestUtils.createInputFile(lbMap, generatedLBYamlFile);
    }
  }

  private void callCreateDomainScript() throws Exception {
    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(BaseTest.getProjectRoot())
        .append(" && helm install kubernetes/charts/weblogic-domain");
    cmd.append(" --name ")
        .append(domainMap.get("domainUID"))
        .append(" --values ")
        .append(generatedDomainYamlFile)
        .append(" --namespace ")
        .append(domainNS)
        .append(" --wait");
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
  }

  private void callCreateLBScript() throws Exception {
    if (loadBalancer.equals("NONE")) return;
    // call script to install Ingress controller.
    String lbParam = null;
    if (loadBalancer.equals("TRAEFIK")) {
      lbParam = "traefik";
    } else if (loadBalancer.equals("VOYAGER")) {
      lbParam = "voyager";
    }

    // setup.sh is only needed for traefik and voyager
    if (lbParam != null) {
      StringBuffer cmd = new StringBuffer();
      cmd.append(BaseTest.getProjectRoot())
          .append("/integration-tests/src/test/resources/loadBalancer/setup.sh create ")
          .append(lbParam);
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
    }

    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(BaseTest.getProjectRoot())
        .append(" && helm install kubernetes/charts/loadBalancer-per-domain");
    cmd.append(" --name ")
        .append(domainMap.get("domainUID") + "-lb")
        .append(" --values ")
        .append(generatedLBYamlFile)
        .append(" --wait");
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
  }

  private void callShellScriptByExecToPod(String username, String password, String webappName)
      throws Exception {

    StringBuffer cmdKubectlSh = new StringBuffer("kubectl -n ");
    cmdKubectlSh
        .append(domainNS)
        .append(" exec -it ")
        .append(domainUid)
        .append("-")
        .append(adminServerName)
        .append(" /shared/calldeploywebapp.sh /shared/deploywebapp.py ")
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
        .append(" /shared/applications/testwebapp.war ")
        .append(clusterName);
    logger.info("Command to call kubectl sh file " + cmdKubectlSh);
    ExecResult result = ExecCommand.exec(cmdKubectlSh.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmdKubectlSh + " failed, returned " + result.stderr());
    }
    String output = result.stdout().trim();
    if (!output.contains("Deployment State : completed")) {
      throw new RuntimeException("Failure: webapp deployment failed." + output);
    }
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
      }
    }
  }

  private void callWebAppAndCheckForServerNameInResponse(String curlCmd) throws Exception {
    // map with server names and boolean values
    HashMap<String, Boolean> managedServers = new HashMap<String, Boolean>();
    for (int i = 1; i <= TestUtils.getClusterReplicas(domainUid, clusterName, domainNS); i++) {
      managedServers.put(domainUid + "-" + managedServerNameBase + i, new Boolean(false));
    }

    for (int i = 0; i < 20; i++) {
      ExecResult result = ExecCommand.exec(curlCmd.toString());
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command " + curlCmd + " failed, returned " + result.stderr());
      }
      String response = result.stdout().trim();
      // logger.info("response "+ response);
      for (String key : managedServers.keySet()) {
        if (response.contains(key)) {
          managedServers.put(key, new Boolean(true));
          break;
        }
      }
    }
    logger.info("ManagedServers " + managedServers);
    // error if any managedserver value is false
    for (Map.Entry<String, Boolean> entry : managedServers.entrySet()) {
      if (!entry.getValue().booleanValue()) {
        throw new RuntimeException("FAILURE: Load balancer can not reach server " + entry.getKey());
      }
    }
  }

  private void initialize(String inputYaml) throws Exception {
    this.userProjectsDir = BaseTest.getUserProjectsDir();
    this.projectRoot = BaseTest.getProjectRoot();

    domainUid = domainMap.get("domainUID").toString();
    // Customize the create domain job inputs
    domainNS = domainMap.getOrDefault("namespace", domainNS).toString();

    adminServerName = domainMap.getOrDefault("adminServerName", adminServerName).toString();
    managedServerNameBase =
        domainMap.getOrDefault("managedServerNameBase", managedServerNameBase).toString();
    initialManagedServerReplicas =
        ((Integer)
                domainMap.getOrDefault(
                    "initialManagedServerReplicas", initialManagedServerReplicas))
            .intValue();
    exposeAdminT3Channel =
        ((Boolean)
                domainMap.getOrDefault("exposeAdminT3Channel", new Boolean(exposeAdminT3Channel)))
            .booleanValue();
    t3ChannelPort = ((Integer) domainMap.getOrDefault("t3ChannelPort", t3ChannelPort)).intValue();
    clusterName = domainMap.getOrDefault("clusterName", clusterName).toString();

    if (exposeAdminT3Channel && domainMap.get("t3PublicAddress") == null) {
      domainMap.put("t3PublicAddress", TestUtils.getHostName());
    }
    if (System.getenv("IMAGE_PULL_SECRET_WEBLOGIC") != null) {
      domainMap.put("weblogicImagePullSecretName", System.getenv("IMAGE_PULL_SECRET_WEBLOGIC"));
      // create docker registry secrets
      TestUtils.createDockerRegistrySecret(
          System.getenv("IMAGE_PULL_SECRET_WEBLOGIC"),
          "index.docker.io/v1/",
          System.getenv("DOCKER_USERNAME"),
          System.getenv("DOCKER_PASSWORD"),
          System.getenv("DOCKER_EMAIL"),
          domainNS);
    }
    // test NFS for domain5 on JENKINS
    if (domainUid.equals("domain5")
        && (System.getenv("JENKINS") != null
            && System.getenv("JENKINS").equalsIgnoreCase("true"))) {
      domainMap.put("weblogicDomainStorageType", "NFS");
      domainMap.put("weblogicDomainStorageNFSServer", TestUtils.getHostName());
    }

    initLoadBalancer();
  }
  // Initialize load balancer values.
  private void initLoadBalancer() throws Exception {
    lbMap = new HashMap<String, Object>();

    if (domainMap.get("loadBalancer") != null) {
      loadBalancer = domainMap.get("loadBalancer").toString();
    } else if (System.getenv("LB_TYPE") != null) {
      loadBalancer = System.getenv("LB_TYPE");
    }
    if (!loadBalancer.equals("APACHE")
        && !loadBalancer.equals("TRAEFIK")
        && !loadBalancer.equals("VOYAGER")
        && !loadBalancer.equals("NONE")) {
      throw new RuntimeException(
          "FAILURE: given load balancer type '"
              + loadBalancer
              + "' is not correct. Valid values are APACHE, TRAEFIK, VOYAGER");
    }

    if (loadBalancer.equals("NONE")) return;

    lbMap.put("type", loadBalancer);

    // For TRAEFIK all domains share the same default web port.
    if (domainMap.get("loadBalancerWebPort") != null && !loadBalancer.equals("TRAEFIK")) {
      loadBalancerWebPort = ((Integer) domainMap.get("loadBalancerWebPort")).intValue();
      lbMap.put("webPort", loadBalancerWebPort);
    }
    if (domainMap.get("loadBalancerDashboardPort") != null) {
      lbMap.put("dashboardPort", domainMap.get("loadBalancerDashboardPort"));
    }

    // create wlsDomain section
    Map<String, String> wlsDomainMap = new HashMap<>();
    wlsDomainMap.put("namespace", domainNS);
    wlsDomainMap.put("domainUID", domainUid);
    wlsDomainMap.put("domainName", "base_domain");
    wlsDomainMap.put("clusterName", clusterName);
    wlsDomainMap.put("managedServerPort", "8001");
    wlsDomainMap.put("adminServerName", "admin-server");
    wlsDomainMap.put("adminPort", "7001");
    lbMap.put("wlsDomain", wlsDomainMap);

    // create Traefik section
    if (loadBalancer.equals("TRAEFIK")) {
      Map<String, String> traefikMap = new HashMap<>();
      traefikMap.put("hostname", domainUid + ".org");
      lbMap.put("traefik", traefikMap);
    }

    // create apache section
    if (loadBalancer.equals("APACHE")) {
      Map<String, String> apacheMap = new HashMap<>();
      // TODO: only for domain7?
      apacheMap.put("appPrepath", "/weblogic");
      apacheMap.put("exposeAdminPort", "true");

      lbMap.put("apache", apacheMap);
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
}

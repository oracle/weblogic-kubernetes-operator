// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import oracle.kubernetes.operator.BaseTest;

/** Domain class with all the utility methods for a Domain. */
public class Domain {
  public static final String CREATE_DOMAIN_JOB_MESSAGE =
      "Domain base_domain was created and will be started by the WebLogic Kubernetes Operator";

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  private Properties domainProps = new Properties();

  //attributes from domain properties
  private String domainUid = "";
  //default values as in create-weblogic-domain-inputs.yaml, generated yaml file will have the customized property values
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
  private String generatedInputYamlFile;

  /**
   * Takes domain properties which should be customized while generating domain input yaml file.
   *
   * @param inputProps
   * @throws Exception
   */
  public Domain(Properties inputProps) throws Exception {
    this.domainProps = inputProps;

    initialize();
    createPV();
    createSecret();
    generateInputYaml();
    callCreateDomainScript();
  }

  
  /** Verifies the required pods are created, services are created and the servers are ready. */
  public void verifyDomainCreated() {
    StringBuffer command = new StringBuffer();
    command.append("kubectl get domain ").append(domainUid).append(" -n ").append(domainNS);
    String outputStr = TestUtils.executeCommand(command.toString());
    if (!outputStr.contains(domainUid))
      throw new RuntimeException("FAILURE: domain not found, exiting!");

    verifyPodsCreated();
    verifyServicesCreated();
    verifyServersReady();
  }

  /** verify pods are created */
  public void verifyPodsCreated() {
    //check admin pod
    logger.info("Checking if admin pod(" + domainUid + "-" + adminServerName + ") is Running");
    TestUtils.checkPodCreated(domainUid + "-" + adminServerName, domainNS);

    //check managed server pods
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

  /** verify services are created */
  public void verifyServicesCreated() {
    //check admin service
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

    //check managed server services
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

  /** verify servers are ready */
  public void verifyServersReady() {
    //check admin pod
    logger.info("Checking if admin server is Running");
    TestUtils.checkPodReady(domainUid + "-" + adminServerName, domainNS);

    //check managed server pods
    for (int i = 1; i <= initialManagedServerReplicas; i++) {
      logger.info("Checking if managed server (" + managedServerNameBase + i + ") is Running");
      TestUtils.checkPodReady(domainUid + "-" + managedServerNameBase + i, domainNS);
    }
  }
  /**
   * verify nodeport by accessing admin REST endpoint
   *
   * @param username
   * @param password
   */
  public void verifyAdminServerExternalService(String username, String password) {

    //logger.info("Inside verifyAdminServerExternalService");
    String nodePortHost = getNodeHost();
    String nodePort = getNodePort();
    logger.fine("nodePortHost " + nodePortHost + " nodePort " + nodePort);

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
    logger.fine("cmd for curl " + cmd);
    String output = TestUtils.executeCommand(cmd.toString());
    logger.fine("output " + output);
    if (!output.trim().equals("200")) {
      throw new RuntimeException(
          "FAILURE: accessing admin server REST endpoint did not return 200 status code, "
              + output);
    }
  }

  /** deploy webapp using nodehost and nodeport */
  public void deployWebAppViaREST(
      String webappName, String webappLocation, String username, String password) {
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
    String output = TestUtils.executeCommandStrArray(cmd.toString());
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
   */
  public void deployWebAppViaWLST(
      String webappName, String webappLocation, String username, String password) {
    StringBuffer cmdTocpwar = new StringBuffer("kubectl cp ");
    cmdTocpwar
        .append(webappLocation)
        .append(" ")
        .append(domainNS)
        .append("/")
        .append(domainUid)
        .append("-")
        .append(adminServerName)
        .append(":/shared/applications/testwebapp.war");

    logger.info("Command to copy war file " + cmdTocpwar);
    String output = TestUtils.executeCommandStrArray(cmdTocpwar.toString());
    if (!output.trim().equals("")) {
      throw new RuntimeException("FAILURE: kubectl cp command failed." + output.trim());
    }

    StringBuffer cmdTocppy = new StringBuffer("kubectl cp ");
    cmdTocppy
        .append(projectRoot)
        .append("/integration-tests/src/integration-tests/resources/deploywebapp.py ")
        .append(domainNS)
        .append("/")
        .append(domainUid)
        .append("-")
        .append(adminServerName)
        .append(":/shared/deploywebapp.py");

    logger.info("Command to copy py file " + cmdTocppy);
    output = TestUtils.executeCommandStrArray(cmdTocppy.toString());
    if (!output.trim().equals("")) {
      throw new RuntimeException("FAILURE: kubectl cp command failed." + output.trim());
    }

    StringBuffer cmdTocpsh = new StringBuffer("kubectl cp ");
    cmdTocpsh
        .append(projectRoot)
        .append("/integration-tests/src/integration-tests/resources/calldeploywebapp.sh ")
        .append(domainNS)
        .append("/")
        .append(domainUid)
        .append("-")
        .append(adminServerName)
        .append(":/shared/calldeploywebapp.py");

    logger.info("Command to copy sh file " + cmdTocpsh);
    output = TestUtils.executeCommandStrArray(cmdTocpsh.toString());
    if (!output.trim().equals("")) {
      throw new RuntimeException("FAILURE: kubectl cp command failed." + output.trim());
    }

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
        .append(TestUtils.getHostName())
        .append(":")
        .append(t3ChannelPort)
        .append(" ")
        .append(webappName)
        .append(" /shared/applications/testwebapp.war ")
        .append(clusterName);
    logger.info("Command to call kubectl sh file " + cmdKubectlSh);
    output = TestUtils.executeCommand(cmdKubectlSh.toString());
    if (!output.contains("Deployment State : completed")) {
      throw new RuntimeException("Failure: webapp deployment failed." + output);
    }
  }
  /**
   * Test http load balancing using loadBalancerWebPort
   *
   * @param webappName
   */
  public void verifyWebAppLoadBalancing(String webappName) {
    if (!loadBalancer.equals("NONE")) {
      //url
      StringBuffer testAppUrl = new StringBuffer("http://");
      testAppUrl
          .append(TestUtils.getHostName())
          .append(":")
          .append(loadBalancerWebPort)
          .append("/")
          .append(webappName)
          .append("/");

      //curl cmd
      StringBuffer curlCmd = new StringBuffer("curl --silent --show-error --noproxy ");
      curlCmd.append(TestUtils.getHostName()).append(" ").append(testAppUrl.toString());

      //curl cmd to get response code
      StringBuffer curlCmdResCode = new StringBuffer(curlCmd.toString());
      curlCmdResCode.append(" --write-out %{http_code} -o /dev/null");

      int maxIterations = 30;
      for (int i = 0; i < maxIterations; i++) {
        String responseCode = TestUtils.executeCommand(curlCmdResCode.toString()).trim();
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
            Thread.sleep(5 * 1000);
          } catch (InterruptedException ignore) {
          }
        }
      }

      //map with server names and boolean values
      HashMap<String, Boolean> managedServers = new HashMap<String, Boolean>();
      for (int i = 1; i <= initialManagedServerReplicas; i++) {
        managedServers.put(domainUid + "-" + managedServerNameBase + i, new Boolean(false));
      }

      //logger.info("curlCmd "+curlCmd);
      //execute curl and look for the managed server name in response
      for (int i = 0; i < 20; i++) {
        String response = TestUtils.executeCommand(curlCmd.toString());
        //logger.info("response "+ response);
        for (String key : managedServers.keySet()) {
          if (response.contains(key)) {
            managedServers.put(key, new Boolean(true));
            break;
          }
        }
      }
      logger.info("ManagedServers " + managedServers);
      //error if any managedserver value is false
      for (Map.Entry<String, Boolean> entry : managedServers.entrySet()) {
        if (!entry.getValue().booleanValue()) {
          throw new RuntimeException(
              "FAILURE: Load balancer can not reach server " + entry.getKey());
        }
      }
    }
  }

  /** startup the domain */
  public void create() {
    TestUtils.executeCommand(
        "kubectl create -f "
            + userProjectsDir
            + "/weblogic-domains/"
            + domainUid
            + "/domain-custom-resource.yaml");
    verifyDomainCreated();
  }

  /** shutdown the domain */
  public void destroy() {
    int replicas = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    TestUtils.executeCommand(
        "kubectl delete -f "
            + userProjectsDir
            + "/weblogic-domains/"
            + domainUid
            + "/domain-custom-resource.yaml");
    verifyDomainDeleted(replicas);
  }

  /**
   * verify domain is deleted
   *
   * @param replicas
   */
  public void verifyDomainDeleted(int replicas) {
    logger.info("Inside verifyDomainDeleted, replicas " + replicas);
    TestUtils.checkDomainDeleted(domainUid, domainNS);
    TestUtils.checkPodDeleted(domainUid + "-" + adminServerName, domainNS);

    for (int i = 1; i <= replicas; i++) {
      TestUtils.checkPodDeleted(domainUid + "-" + managedServerNameBase + i, domainNS);
    }
  }

  /**
   * cleanup the domain
   *
   * @param userProjectsDir
   */
  public void cleanup(String userProjectsDir) {
    TestUtils.executeCommand("../kubernetes/delete-weblogic-domain-resources.sh -d " + domainUid);
    if (!domainUid.trim().equals("")) {
      TestUtils.executeCommand("rm -rf " + userProjectsDir + "/weblogic-domains/" + domainUid);
    }
  }

  public Properties getDomainProps() {
    return domainProps;
  }

  private void createPV() {
    //k8s job mounts PVROOT /scratch/<usr>/wl_k8s_test_results to /scratch
    new PersistentVolume("/scratch/acceptance_test_pv/persistentVolume-" + domainUid);

    //set pv path
    domainProps.setProperty(
        "weblogicDomainStoragePath",
        BaseTest.getPvRoot() + "/acceptance_test_pv/persistentVolume-" + domainUid);
  }

  private void createSecret() {
	    new Secret(
	            domainNS,
	            domainProps.getProperty("secretName", domainUid + "-weblogic-credentials"),
	            BaseTest.getUsername(),
	            BaseTest.getPassword());
  }
  private void generateInputYaml() throws Exception {
	    Path parentDir =
	            Files.createDirectories(Paths.get(userProjectsDir + "/weblogic-domains/" + domainUid));
	        generatedInputYamlFile = parentDir + "/" + domainUid + "-inputs.yaml";
	        TestUtils.createInputFile(domainProps, inputTemplateFile, generatedInputYamlFile);

  }
  

  private void callCreateDomainScript() {
    StringBuffer cmd = new StringBuffer(createDomainScript);
    cmd.append(" -i ").append(generatedInputYamlFile).append(" -o ").append(userProjectsDir);
    logger.info("Running " + cmd);
    String outputStr = TestUtils.executeCommand(cmd.toString());
    logger.info("run " + outputStr);
    if (!outputStr.contains(CREATE_DOMAIN_JOB_MESSAGE)) {
    	throw new RuntimeException("FAILURE: Create domain Script failed..");
    }
    
  }
  
  private void initialize() {
    this.userProjectsDir = BaseTest.getUserProjectsDir();
    this.projectRoot = BaseTest.getProjectRoot();

    createDomainScript = projectRoot + "/kubernetes/create-weblogic-domain.sh";
    inputTemplateFile = projectRoot + "/kubernetes/create-weblogic-domain-inputs.yaml";
    domainUid = domainProps.getProperty("domainUID");
    //Customize the create domain job inputs
    domainNS = domainProps.getProperty("namespace", domainNS);
    adminServerName = domainProps.getProperty("adminServerName", adminServerName);
    managedServerNameBase = domainProps.getProperty("managedServerNameBase", managedServerNameBase);
    initialManagedServerReplicas =
        new Integer(
                domainProps.getProperty(
                    "initialManagedServerReplicas", initialManagedServerReplicas + ""))
            .intValue();
    exposeAdminT3Channel =
        new Boolean(
                domainProps.getProperty(
                    "exposeAdminT3Channel", new Boolean(exposeAdminT3Channel).toString()))
            .booleanValue();
    t3ChannelPort =
        new Integer(domainProps.getProperty("t3ChannelPort", t3ChannelPort + "")).intValue();
    clusterName = domainProps.getProperty("clusterName", clusterName);
    loadBalancer = domainProps.getProperty("loadBalancer", loadBalancer);
    loadBalancerWebPort =
        new Integer(domainProps.getProperty("loadBalancerWebPort", loadBalancerWebPort + ""))
            .intValue();
    if (exposeAdminT3Channel && domainProps.getProperty("t3PublicAddress") == null) {
      domainProps.put("t3PublicAddress", TestUtils.getHostName());
    }
  }

  private String getNodeHost() {
    String cmd =
        "kubectl describe pod "
            + domainUid
            + "-"
            + adminServerName
            + " -n "
            + domainNS
            + " | grep Node:";

    String nodePortHost = TestUtils.executeCommandStrArray(cmd);
    //logger.info("nodePortHost "+nodePortHost);
    if (nodePortHost.contains(":") && nodePortHost.contains("/")) {
      return nodePortHost
          .substring(nodePortHost.indexOf(":") + 1, nodePortHost.indexOf("/"))
          .trim();
    } else {
      throw new RuntimeException("FAILURE: Invalid nodePortHost from admin pod " + nodePortHost);
    }
  }

  private String getNodePort() {
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl describe domain ")
        .append(domainUid)
        .append(" -n ")
        .append(domainNS)
        .append(" | grep \"Node Port:\"");
    String output = TestUtils.executeCommandStrArray(cmd.toString());
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

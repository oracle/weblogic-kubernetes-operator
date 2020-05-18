// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.BaseTest;

public class LoadBalancer {

  private Map<String, Object> lbMap;
  private String userProjectsDir;
  private static int maxIterationsPod = 60;
  private static int waitTimePod = 5;

  /**
   * Construct load balancer.
   * @param lbMap load balancer map
   * @throws Exception on failure
   */
  public LoadBalancer(Map lbMap) throws Exception {
    this.lbMap = lbMap;
    userProjectsDir = (String) lbMap.get("userProjectsDir");
    Files.createDirectories(
        Paths.get(userProjectsDir + "/load-balancers/" + lbMap.get("domainUID")));

    if (lbMap.get("loadBalancer").equals("TRAEFIK")) {
      String cmdLb = "";
      cmdLb = "helm list --namespace traefik | grep traefik-operator | grep -i DEPLOYED";
      LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        createTraefikLoadBalancer();
      }

      if (!((Boolean) lbMap.get("ingressPerDomain")).booleanValue()) {
        LoggerHelper.getLocal().log(Level.INFO, "Is going to createTraefikHostRouting");
        createTraefikHostRouting();
      } else {
        LoggerHelper.getLocal().log(Level.INFO, "Is going to createTraefikIngressPerDomain");
        createTraefikIngressPerDomain();
      }
    }

    if (lbMap.get("loadBalancer").equals("VOYAGER")) {
      String cmdLb = "";
      cmdLb = "helm list --namespace voyager | grep voyager-operator | grep -i DEPLOYED";
      LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        createVoyagerLoadBalancer();
        LoggerHelper.getLocal().log(Level.INFO,
            "Sleeping for 30 seconds to ensure voyager to be ready");
        Thread.sleep(30 * 1000);
      }

      if (((Boolean) lbMap.get("ingressPerDomain")).booleanValue()) {
        LoggerHelper.getLocal().log(Level.INFO, "Is going to createVoyagerIngressPerDomain");
        createVoyagerIngressPerDomain();
      }
    }
  }

  /**
   * Create Traefik load balancer.
   * @throws Exception on failure
   */
  public void createTraefikLoadBalancer() throws Exception {
    String cmdLb = null;
    cmdLb = BaseTest.getProjectRoot() + "/kubernetes/samples/charts/util/setup.sh create traefik ";
    LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
    ExecResult result = ExecCommand.exec(cmdLb);
    if (result.exitValue() != 0) {
      if (!result.stderr().contains("release named traefik-operator already exists")) {
        throw new RuntimeException(
            "FAILURE: command to create load balancer "
                + cmdLb
                + " failed, returned "
                + result.stdout()
                + result.stderr());
      }
    }
  }

  /**
   * Create Traefik host routing.
   * @throws Exception on failure
   */
  public void createTraefikHostRouting() throws Exception {

    createInputFile(
        BaseTest.getProjectRoot()
            + "/integration-tests/src/test/resources/charts/traefik/host-routing.yaml",
        userProjectsDir
            + "/load-balancers/"
            + lbMap.get("domainUID")
            + "/host-routing.yaml");

    String cmdLb =
        "kubectl create -f "
            + userProjectsDir
            + "/load-balancers/"
            + lbMap.get("domainUID")
            + "/host-routing.yaml";
    LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);

    ExecResult result = ExecCommand.exec(cmdLb);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command to create ingress host routing "
              + cmdLb
              + " failed, returned "
              + result.stdout()
              + result.stderr());
    }
  }

  private void createTraefikIngressPerDomain() throws Exception {
    upgradeTraefikNamespace();
    createTraefikIngress();
  }

  private synchronized void upgradeTraefikNamespace() throws Exception {

    String namespace = getKubernetesNamespaceToUpdate((String) lbMap.get("namespace"));
    LoggerHelper.getLocal().log(Level.INFO, "namespace to update" + namespace);
    String traefikPod = TestUtils.getPodName(" -l app=traefik ", "traefik");
    StringBuffer cmd = new StringBuffer("helm upgrade ");
    cmd.append(" traefik-operator")
       .append(" stable/traefik ")
       .append("--namespace traefik ")
       .append("--reuse-values ")
       .append("--set ")
       .append("\"")
       .append("kubernetes.namespaces=")
       .append(namespace)
        .append("\" --wait");

    LoggerHelper.getLocal().log(Level.INFO, " upgradeTraefikNamespace() Running " + cmd.toString());
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      TestUtils.printHelmChartInfo("traefik-operator","traefik");
      reportHelmInstallFailure(cmd.toString(), result);
    }
    String outputStr = result.stdout().trim();
    LoggerHelper.getLocal().log(Level.INFO, "Command returned " + outputStr);
    //check release status
    String helmCmd = "helm status traefik-operator --namespace traefik";
    TestUtils.checkAnyCmdInLoop(helmCmd, "deployed");
    int i = 0;
    //wait in case if pod is restarted after upgrade and check the status
    cmd = new StringBuffer();
    cmd.append("kubectl get pod -n traefik");
    while (i < maxIterationsPod) {
      if (TestUtils.checkPodContains(cmd.toString(), "Terminating", traefikPod)) {
        Thread.sleep(5000);
        i++;
      } else {
        break;
      }
      //if still in Terminating state, force to delete the pod
      if (i == (maxIterationsPod - 1)) {
        LoggerHelper.getLocal().log(Level.INFO, "Traefik pod "
            + traefikPod + "is still in Terminating state , call delete pod ");
        cmd = new StringBuffer();
        cmd.append("kubectl delete ")
            .append(traefikPod)
            .append(" --force --grace-period=0 --ignore-not-found ")
            .append(" -n traefik ");
        ExecCommand.exec(cmd.toString());
        Thread.sleep(5000);
      }
    }
    traefikPod = TestUtils.getPodName(" -l app=traefik ", "traefik");
    TestUtils.checkPodReadyAndRunning(traefikPod, "traefik");
  }

  /**
   * append current namespace to existing namespaces.
   *
   * @param domainNamespace namepace to append
   * @return string updated namespace list
   * @throws Exception when could not get values
   */
  private String getKubernetesNamespaceToUpdate(String domainNamespace) throws Exception {
    String cmd = "helm get values traefik-operator ";
    cmd = cmd + " --namespace traefik ";
    ExecResult result = ExecCommand.exec(cmd);
    Map<String, Object> yamlMap = TestUtils.loadYamlFromString(result.stdout());
    LoggerHelper.getLocal().log(Level.INFO, "map " + yamlMap);
    if (yamlMap.containsKey("kubernetes")) {
      Map<String, Object> kubernetesMap = (Map<String, Object>) yamlMap.get("kubernetes");
      if (kubernetesMap.containsKey("namespaces")) {
        String kubernetesNamespace = ((ArrayList) kubernetesMap.get("namespaces")).toString();
        LoggerHelper.getLocal().log(Level.INFO,
            "traefik-operator contains kubernetes.namespaces " + kubernetesNamespace);
        // now be "foo, bar, baz" from ["foo, bar, baz"]
        String debracketed = kubernetesNamespace.replace("[", "").replace("]", "");
        // now is "foo,bar,baz"
        String trimmed = debracketed.replaceAll("\\s+", "");
        // now have an ArrayList containing "foo", "bar" and "baz"
        ArrayList<String> list = new ArrayList<String>(Arrays.asList(trimmed.split(",")));
        if (!list.contains(domainNamespace)) {
          list.add(domainNamespace);
        } else {
          LoggerHelper.getLocal().log(Level.INFO,
              "Skip namespace as the namespace is already in the list");
        }
        return list.toString().replace("[", "{")
            .replace("]", "}").replace(" ", "");
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "traefik-operator doesn't contain kubernetes.namespaces");
    return "{traefik," + domainNamespace + "}";
  }

  private void createTraefikIngress() throws Exception {

    String chartDir = BaseTest.getProjectRoot() + "/kubernetes/samples/charts";

    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(chartDir).append(" && ");
    cmd.append(" helm install ")
         .append(lbMap.get("name"))
          .append(" ingress-per-domain");
    cmd.append(" --namespace ")
       .append(lbMap.get("namespace"))
       .append(" --set ")
       .append("wlsDomain.domainUID=")
       .append(lbMap.get("domainUID"))
       .append(" --set ")
       .append("wlsDomain.clusterName=")
       .append(lbMap.get("clusterName"))
       .append(" --set ")
       .append("traefik.hostname=")
       .append(lbMap.get("domainUID"))
        .append(".org");

    LoggerHelper.getLocal().log(Level.INFO, "createTraefikIngress() Running " + cmd.toString());
    ExecResult result = ExecCommand.exec(cmd.toString());
    TestUtils.checkHelmChartStatus((String)lbMap.get("name"),
        (String)lbMap.get("namespace"),"deployed");
    TestUtils.printHelmChartInfo((String)lbMap.get("name"),
        (String)lbMap.get("namespace"));
    if (result.exitValue() != 0) {
      TestUtils.printHelmChartInfo((String)lbMap.get("name")
          + "-ingress-"
          + (String)lbMap.get("domainUID"),
          (String)lbMap.get("namespace"));
      reportHelmInstallFailure(cmd.toString(), result);
    }
    LoggerHelper.getLocal().log(Level.INFO, "Checking if Ingress is created  ");
    cmd = new StringBuffer();
    cmd.append("kubectl get ingress")
        .append(" -n ")
        .append((String)lbMap.get("namespace"))
        .append(" | grep ")
        .append(lbMap.get("domainUID"));

    TestUtils.checkAnyCmdInLoop(cmd.toString(),lbMap.get("domainUID") + "-traefik");
  }

  /**
   * Create Voyager load balancer.
   * @throws Exception on failure
   */
  public void createVoyagerLoadBalancer() throws Exception {
    String cmdLb = "";
    String vversion = BaseTest.VOYAGER_VERSION;
    cmdLb = BaseTest.getProjectRoot() + "/kubernetes/samples/charts/util/setup.sh create voyager " + vversion;
    LoggerHelper.getLocal().log(Level.INFO, "Executing Install voyager operator cmd " + cmdLb);
    executeHelmCommand(cmdLb, "voyager-operator", "voyager");
    String voyagerPod = TestUtils.getPodName(" -l app=voyager ", "voyager");
    TestUtils.checkPodReady(voyagerPod, "voyager");
  }

  private void createVoyagerIngressPerDomain() throws Exception {
    upgradeVoyagerNamespace();
    LoggerHelper.getLocal().log(Level.INFO, "Sleeping for 20 seconds after upgradeVoyagerNamespace ");
    Thread.sleep(20 * 1000);
    createVoyagerIngress();
    LoggerHelper.getLocal().log(Level.INFO, "Sleeping for 20 seconds after createVoyagerIngress ");
    Thread.sleep(20 * 1000);
  }

  private synchronized void upgradeVoyagerNamespace() throws Exception {
    String vversion = BaseTest.VOYAGER_VERSION;
    String voyagerPod = TestUtils.getPodName(" -l app=voyager ", "voyager");
    StringBuffer cmd = new StringBuffer("helm upgrade ");
    cmd.append(" voyager-operator")
        .append(" appscode/voyager ")
        .append("--namespace voyager ")
        .append("--reuse-values ")
        .append("--set ")
        .append("\"")
        .append("kubernetes.namespaces={voyager,")
        .append(lbMap.get("namespace"))
        .append("}")
        .append("\"")
        .append(" --version ")
        .append(vversion)
        .append(" --set cloudProvider=baremetal")
        .append(" --set apiserver.enableValidatingWebhook=false");

    LoggerHelper.getLocal().log(Level.INFO, " upgradeVoyagerNamespace() Running " + cmd.toString());

    String returnStr = null;
    int i = 0;
    // Wait max 300 seconds
    while (i < maxIterationsPod) {
      returnStr = executeHelmCommand(cmd.toString(),"voyager-operator","voyager");
      if (null != returnStr && returnStr.contains("upgraded")) {
        LoggerHelper.getLocal().log(Level.INFO, "upgradeVoyagerNamespace() Result: " + returnStr);
        break;
      }

      LoggerHelper.getLocal().log(Level.INFO,
          "Voyager pod is not ready to use yet ["
              + i
              + "/"
              + maxIterationsPod
              + "], sleeping "
              + waitTimePod
              + " seconds more");
      Thread.sleep(waitTimePod * 1000);
      i++;
    }

    if (null == returnStr) {
      executeHelmCommand(cmd.toString(), "voyager-operator","voyager");
    }
    //check release status
    String helmCmd = "helm status voyager-operator --namespace voyager";
    TestUtils.checkAnyCmdInLoop(helmCmd, "deployed");
    i = 0;
    //wait in case if pod is restarted after upgrade and check the status
    cmd = new StringBuffer();
    cmd.append("kubectl get pod -n voyager");
    while (i < maxIterationsPod) {
      if (TestUtils.checkPodContains(cmd.toString(), "Terminating", voyagerPod)) {
        Thread.sleep(5000);
        i++;
      } else {
        break;
      }
    }
    voyagerPod = TestUtils.getPodName(" -l app=voyager ", "voyager");
    TestUtils.checkPodReadyAndRunning(voyagerPod, "voyager");
  }

  private void createVoyagerIngress() throws Exception {
    String chartDir = BaseTest.getProjectRoot() + "/kubernetes/samples/charts";

    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(chartDir).append(" && ");
    cmd.append(" helm install ")
         .append(lbMap.get("name"))
          .append(" ingress-per-domain");
    cmd.append(" --namespace ")
       .append(lbMap.get("namespace"))
       .append(" --set type=VOYAGER")
       .append(" --set ")
       .append("wlsDomain.domainUID=")
       .append(lbMap.get("domainUID"))
       .append(" --set ")
       .append("wlsDomain.clusterName=")
       .append(lbMap.get("clusterName"))
       .append(" --set ")
       .append("voyager.webPort=")
        .append(lbMap.get("loadBalancerWebPort"));
    LoggerHelper.getLocal().log(Level.INFO, "createVoyagerIngress() Running " + cmd.toString());

    String returnStr = null;
    int i = 0;
    // Wait max 300 seconds
    while (i < maxIterationsPod) {
      try {
        returnStr = executeHelmCommand(cmd.toString(),(String)lbMap.get("name"),(String)lbMap.get("namespace"));
      } catch (RuntimeException rtex) {
        LoggerHelper.getLocal().log(Level.INFO, "createVoyagerIngress() caught Exception. Retry");
      }

      if (null != returnStr && !returnStr.contains("failed")) {
        LoggerHelper.getLocal().log(Level.INFO, "createVoyagerIngress() Result: " + returnStr);
        break;
      }

      LoggerHelper.getLocal().log(Level.INFO,
          "Voyager ingress is not created yet ["
              + i
              + "/"
              + maxIterationsPod
              + "], sleeping "
              + waitTimePod
              + " seconds more");
      Thread.sleep(waitTimePod * 1000);
      i++;
    }

    if (null == returnStr) {
      executeHelmCommand(cmd.toString(),(String)lbMap.get("name"),(String)lbMap.get("namespace"));
    }

    TestUtils.checkHelmChartStatus((String)lbMap.get("name"),
        (String)lbMap.get("namespace"),"deployed");

    cmd = new StringBuffer();
    cmd.append("kubectl get ingress.voyager.appscode.com")
        .append(" -n ")
        .append((String)lbMap.get("namespace"))
        .append(" | grep ")
        .append(lbMap.get("domainUID"));

    TestUtils.checkAnyCmdInLoop(cmd.toString(),lbMap.get("domainUID") + "-voyager");
  }

  private String executeHelmCommand(String cmd, String chartName, String chartNS) throws Exception {
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      LoggerHelper.getLocal().log(Level.INFO, "executeHelmCommand failed with " + cmd);
      TestUtils.printHelmChartInfo(chartName, chartNS);
      reportHelmInstallFailure(cmd, result);
    }
    String outputStr = result.stdout().trim();
    LoggerHelper.getLocal().log(Level.INFO, "Command returned " + outputStr);
    return outputStr;
  }

  private void reportHelmInstallFailure(String cmd, ExecResult result) throws Exception {
    throw new RuntimeException(getExecFailure(cmd, result));
  }

  private String getExecFailure(String cmd, ExecResult result) throws Exception {
    return "FAILURE: command "
        + cmd
        + " failed, stdout:\n"
        + result.stdout()
        + "stderr:\n"
        + result.stderr();
  }

  public Map<String, Object> getLbMap() {
    return lbMap;
  }

  private void createInputFile(String inputFileTemplate, String generatedYamlFile)
      throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Creating input yaml file at " + generatedYamlFile);

    // copy input template file and modify it
    Files.copy(
        new File(inputFileTemplate).toPath(),
        Paths.get(generatedYamlFile),
        StandardCopyOption.REPLACE_EXISTING);

    // read each line in input template file and replace only customized props
    BufferedReader reader = new BufferedReader(new FileReader(generatedYamlFile));
    String line = "";
    StringBuffer changedLines = new StringBuffer();
    boolean isLineChanged = false;
    while ((line = reader.readLine()) != null) {
      Iterator it = lbMap.keySet().iterator();
      while (it.hasNext()) {
        String key = (String) it.next();
        // if a line starts with the props key then replace
        // the line with key:value in the file
        if (line.contains(key + ":")) {
          String changedLine =
              line.replace(line.substring(line.indexOf((key + ":"))), key + ": " + lbMap.get(key));
          changedLines.append(changedLine).append("\n");
          isLineChanged = true;
          break;
        }
      }
      if (!isLineChanged) {
        changedLines.append(line).append("\n");
      }
      isLineChanged = false;
    }
    reader.close();
    // writing to the file
    Files.write(Paths.get(generatedYamlFile), changedLines.toString().getBytes());
  }
}

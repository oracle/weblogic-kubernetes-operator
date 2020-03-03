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
      if (! BaseTest.HELM_VERSION.equals("V2")) {
        cmdLb = "helm list traefik-operator | grep DEPLOYED";
      } else {
        cmdLb = "helm list --namespace traefik | grep traefik-operator | grep -i DEPLOYED";
      }
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
      if (BaseTest.HELM_VERSION.equals("V2")) {
        cmdLb = "helm list voyager-operator | grep DEPLOYED";
      } else {
        cmdLb = "helm list --namespace voyager | grep voyager-operator | grep -i DEPLOYED";
      }
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

  private void upgradeTraefikNamespace() throws Exception {

    String namespace = getKubernetesNamespaceToUpdate((String) lbMap.get("namespace"));
    LoggerHelper.getLocal().log(Level.INFO, "namespace to update" + namespace);
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
      reportHelmInstallFailure(cmd.toString(), result);
    }
    String outputStr = result.stdout().trim();
    LoggerHelper.getLocal().log(Level.INFO, "Command returned " + outputStr);
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
    if (! BaseTest.HELM_VERSION.equals("V2")) {
      cmd = cmd + " --namespace traefik ";
    }
    ExecResult result = TestUtils.exec(cmd, true);
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
        list.add(domainNamespace);
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
    
    if (BaseTest.HELM_VERSION.equals("V2")) {
      cmd.append(" helm install ingress-per-domain ")
         .append(" --name ")
          .append(lbMap.get("name"));
    } else {
      cmd.append(" helm install ")
         .append(lbMap.get("name"))
          .append(" ingress-per-domain");
    }
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
    if (result.exitValue() != 0) {
      reportHelmInstallFailure(cmd.toString(), result);
    }
    String outputStr = result.stdout().trim();
    LoggerHelper.getLocal().log(Level.INFO, "Command returned " + outputStr);
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
    executeHelmCommand(cmdLb);
  }

  private void createVoyagerIngressPerDomain() throws Exception {
    upgradeVoyagerNamespace();
    LoggerHelper.getLocal().log(Level.INFO, "Sleeping for 20 seconds after upgradeVoyagerNamespace ");
    Thread.sleep(20 * 1000);
    createVoyagerIngress();
    LoggerHelper.getLocal().log(Level.INFO, "Sleeping for 20 seconds after createVoyagerIngress ");
    Thread.sleep(20 * 1000);
  }

  private void upgradeVoyagerNamespace() throws Exception {
    String vversion = BaseTest.VOYAGER_VERSION;
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
      returnStr = executeHelmCommand(cmd.toString());
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
      executeHelmCommand(cmd.toString());
    }
  }

  private void createVoyagerIngress() throws Exception {
    String chartDir = BaseTest.getProjectRoot() + "/kubernetes/samples/charts";

    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(chartDir).append(" && ");

    if (BaseTest.HELM_VERSION.equals("V2")) {
      cmd.append(" helm install ingress-per-domain ")
         .append(" --name ")
          .append(lbMap.get("name"));
    } else {
      cmd.append(" helm install ")
         .append(lbMap.get("name"))
          .append(" ingress-per-domain");
    }
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
        returnStr = executeHelmCommand(cmd.toString());
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
      executeHelmCommand(cmd.toString());
    }
  }

  private String executeHelmCommand(String cmd) throws Exception {
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      LoggerHelper.getLocal().log(Level.INFO, "executeHelmCommand failed with " + cmd);
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

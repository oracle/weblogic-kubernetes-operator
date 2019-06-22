// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;
import oracle.kubernetes.operator.BaseTest;

public class LoadBalancer {

  private Map<String, Object> lbMap;

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  public LoadBalancer(Map lbMap) throws Exception {
    this.lbMap = lbMap;
    Files.createDirectories(
        Paths.get(BaseTest.getUserProjectsDir() + "/load-balancers/" + lbMap.get("domainUID")));

    if (lbMap.get("loadBalancer").equals("TRAEFIK")) {
      String cmdLb = "helm list traefik-operator | grep DEPLOYED";
      logger.info("Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        createTraefikLoadBalancer();
      }

      if (!((Boolean) lbMap.get("ingressPerDomain")).booleanValue()) {
        logger.info("Is going to createTraefikHostRouting");
        createTraefikHostRouting();
      } else {
        logger.info("Is going to createTraefikIngressPerDomain");
        createTraefikIngressPerDomain();
      }
    }

    if (lbMap.get("loadBalancer").equals("VOYAGER")) {
      String cmdLb = "helm list voyager-operator | grep DEPLOYED";
      logger.info("Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        createVoyagerLoadBalancer();
        logger.info("Sleeping for 30 seconds to ensure voyager to be ready");
        Thread.sleep(30 * 1000);
      }

      if (((Boolean) lbMap.get("ingressPerDomain")).booleanValue()) {
        logger.info("Is going to createVoyagerIngressPerDomain");
        createVoyagerIngressPerDomain();
      }
    }
  }

  public void createTraefikLoadBalancer() throws Exception {
    String cmdLb =
        "helm install --name traefik-operator --namespace traefik --values "
            + BaseTest.getProjectRoot()
            + "/integration-tests/src/test/resources/charts/traefik/values.yaml stable/traefik";
    logger.info("Executing cmd " + cmdLb);

    ExecResult result = ExecCommand.exec(cmdLb);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command to create load balancer "
              + cmdLb
              + " failed, returned "
              + result.stdout()
              + result.stderr());
    }
  }

  public void createTraefikHostRouting() throws Exception {

    createInputFile(
        BaseTest.getProjectRoot()
            + "/integration-tests/src/test/resources/charts/traefik/host-routing.yaml",
        BaseTest.getUserProjectsDir()
            + "/load-balancers/"
            + lbMap.get("domainUID")
            + "/host-routing.yaml");

    String cmdLb =
        "kubectl create -f "
            + BaseTest.getUserProjectsDir()
            + "/load-balancers/"
            + lbMap.get("domainUID")
            + "/host-routing.yaml";
    logger.info("Executing cmd " + cmdLb);

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

    StringBuffer cmd = new StringBuffer("helm upgrade ");
    cmd.append("--reuse-values ")
        .append("--set ")
        .append("\"")
        .append("kubernetes.namespaces={traefik,")
        .append(lbMap.get("namespace"))
        .append("}")
        .append("\"")
        .append(" traefik-operator")
        .append(" stable/traefik ");

    logger.info(" upgradeTraefikNamespace() Running " + cmd.toString());
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      reportHelmInstallFailure(cmd.toString(), result);
    }
    String outputStr = result.stdout().trim();
    logger.info("Command returned " + outputStr);
  }

  private void createTraefikIngress() throws Exception {

    String chartDir = BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/charts";

    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(chartDir).append(" && helm install ingress-per-domain ");
    cmd.append(" --name ")
        .append(lbMap.get("name"))
        .append(" --namespace ")
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

    logger.info("createTraefikIngress() Running " + cmd.toString());
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      reportHelmInstallFailure(cmd.toString(), result);
    }
    String outputStr = result.stdout().trim();
    logger.info("Command returned " + outputStr);
  }

  public void createVoyagerLoadBalancer() throws Exception {

    String cmd1 = "helm repo add appscode https://charts.appscode.com/stable/";
    logger.info("Executing Add Appscode Chart Repository cmd " + cmd1);

    executeHelmCommand(cmd1);

    String cmd2 = "helm repo update";
    logger.info("Executing Appscode Chart Repository upgrade cmd " + cmd2);

    executeHelmCommand(cmd2);

    String cmd3 =
        "helm install appscode/voyager --name voyager-operator --version 7.4.0 --namespace voyage "
            + "--set cloudProvider=baremetal --set apiserver.enableValidatingWebhook=false";
    logger.info("Executing Install voyager operator cmd " + cmd3);

    executeHelmCommand(cmd3);
  }

  private void createVoyagerIngressPerDomain() throws Exception {
    upgradeVoyagerNamespace();
    logger.info("Sleeping for 20 seconds after upgradeVoyagerNamespace ");
    Thread.sleep(20 * 1000);
    createVoyagerIngress();
    logger.info("Sleeping for 20 seconds after createVoyagerIngress ");
    Thread.sleep(20 * 1000);
  }

  private void upgradeVoyagerNamespace() throws Exception {

    StringBuffer cmd = new StringBuffer("helm upgrade ");
    cmd.append("--reuse-values ")
        .append("--set ")
        .append("\"")
        .append("kubernetes.namespaces={voyager,")
        .append(lbMap.get("namespace"))
        .append("}")
        .append("\"")
        .append(" --version 7.4.0")
        .append(" --set cloudProvider=baremetal")
        .append(" --set apiserver.enableValidatingWebhook=false")
        .append(" voyager-operator")
        .append(" appscode/voyager");

    logger.info(" upgradeVoyagerNamespace() Running " + cmd.toString());
    executeHelmCommand(cmd.toString());
  }

  private void createVoyagerIngress() throws Exception {

    String chartDir = BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/charts";

    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(chartDir).append(" && helm install ingress-per-domain ");
    cmd.append(" --name ")
        .append(lbMap.get("name"))
        .append(" --namespace ")
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

    logger.info("createVoyagerIngress() Running " + cmd.toString());
    executeHelmCommand(cmd.toString());
  }

  private void executeHelmCommand(String cmd) throws Exception {
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      logger.info("executeHelmCommand failed with " + cmd);
      reportHelmInstallFailure(cmd, result);
    }
    String outputStr = result.stdout().trim();
    logger.info("Command returned " + outputStr);
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

  public Map<String, Object> getLBMap() {
    return lbMap;
  }

  private void createInputFile(String inputFileTemplate, String generatedYamlFile)
      throws Exception {
    logger.info("Creating input yaml file at " + generatedYamlFile);

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

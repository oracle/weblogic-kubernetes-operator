// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
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
      createTraefikHostRouting();
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

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Logger;
import oracle.kubernetes.operator.BaseTest;

public class LoadBalancer {

  private Map<String, Object> lbMap;

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  public LoadBalancer(Map lbMap) throws Exception {
    this.lbMap = lbMap;
    Path parentDir =
        Files.createDirectories(
            Paths.get(
                BaseTest.getUserProjectsDir() + "/weblogic-domains/" + lbMap.get("domainUID")));
    // generate input yaml
    TestUtils.createInputFile(lbMap, parentDir + "/lb-inputs.yaml");

    // create load balancer
    String cmdLb =
        BaseTest.getProjectRoot()
            + "/kubernetes/samples/scripts/create-weblogic-domain-load-balancer/create-load-balancer.sh "
            + " -i "
            + parentDir
            + "/lb-inputs.yaml -e -o "
            + BaseTest.getUserProjectsDir();
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
    logger.info("command result " + result.stdout().trim());
  }

  public Map<String, Object> getLBMap() {
    return lbMap;
  }
}

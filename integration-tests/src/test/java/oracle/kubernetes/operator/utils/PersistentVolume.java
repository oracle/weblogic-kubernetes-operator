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

public class PersistentVolume {

  private Map<String, Object> pvMap;
  private String dirPath;

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  public PersistentVolume(String dirPath, Map pvMap) throws Exception {
    this.dirPath = dirPath;
    this.pvMap = pvMap;

    String cmd =
        BaseTest.getProjectRoot()
            + "/src/integration-tests/bash/job.sh \"mkdir -p "
            + dirPath
            + "\"";
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command to create domain PV directory "
              + cmd
              + " failed, returned "
              + result.stdout()
              + result.stderr());
    }
    logger.info("command result " + result.stdout().trim());

    Path parentDir =
        pvMap.get("domainUID") != null
            ? Files.createDirectories(
                Paths.get(BaseTest.getUserProjectsDir() + "/pv-pvcs/" + pvMap.get("domainUID")))
            : Files.createDirectories(Paths.get(BaseTest.getUserProjectsDir() + "/pv-pvcs/"));

    // generate input yaml
    TestUtils.createInputFile(pvMap, parentDir + "/" + pvMap.get("baseName") + "-pv-inputs.yaml");

    // create PV/PVC
    String cmdPvPvc =
        BaseTest.getResultDir()
            + "/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc.sh "
            + " -i "
            + parentDir
            + "/"
            + pvMap.get("baseName")
            + "-pv-inputs.yaml -e -o "
            + BaseTest.getUserProjectsDir();
    logger.info("Executing cmd " + cmdPvPvc);

    result = ExecCommand.exec(cmdPvPvc);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command to create PV/PVC "
              + cmdPvPvc
              + " failed, returned "
              + result.stdout()
              + result.stderr());
    }
    logger.info("command result " + result.stdout().trim());
  }

  public String getDirPath() {
    return dirPath;
  }

  public Map getPvMap() {
    return pvMap;
  }
}

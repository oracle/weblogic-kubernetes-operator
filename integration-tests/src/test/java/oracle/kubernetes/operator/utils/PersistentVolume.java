// Copyright (c) 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kubernetes.operator.BaseTest;

public class PersistentVolume {

  private Map<String, Object> pvMap;
  private String dirPath;

  /**
   * Create PV directory and k8s pv and pvc for the domain
   *
   * @param dirPath
   * @param pvMap
   * @throws Exception
   */
  public PersistentVolume(String dirPath, Map<String, Object> pvMap) throws Exception {
    this.dirPath = dirPath;
    this.pvMap = pvMap;
    UUID uuid = UUID.randomUUID();
    String cmd;
    if (BaseTest.OPENSHIFT) {
      cmd = "mkdir -m 777 -p " + dirPath;
    } else {
      cmd =
          BaseTest.getProjectRoot()
              + "/src/integration-tests/bash/krun.sh -m " + BaseTest.getPvRoot()
              + ":/shareddir-" + uuid + " -t 120 -p pod-"
              + uuid + " -c 'mkdir -m 777 -p "
              + dirPath.replace(BaseTest.getPvRoot(), "/shareddir-" + uuid + "/")
              + "'";
    }
    // retry logic for PV dir creation as sometimes krun.sh fails
    int cnt = 0;
    int maxCnt = 10;
    while (cnt < maxCnt) {
      LoggerHelper.getLocal().log(Level.INFO, "Executing command " + cmd);
      ExecResult result = ExecCommand.exec(cmd);
      if (result.exitValue() == 0) {
        break;
      } else {
        LoggerHelper.getLocal().log(Level.INFO,
            "PV dir creation command failed with exitValue= " + result.exitValue()
                + "stderr= " + result.stderr() + " stdout=" + result.stdout());
        Thread.sleep(BaseTest.getWaitTimePod());
        cnt = cnt + 1;
      }
      if (cnt == maxCnt) {
        throw new RuntimeException("FAILED: Failed to create PV directory");
      }
    }

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
            + "/" + (pvMap.containsKey("domainUID") ? pvMap.get("domainUID") : "")
            + "/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc.sh "
            + " -i "
            + parentDir
            + "/"
            + pvMap.get("baseName")
            + "-pv-inputs.yaml -e -o "
            + BaseTest.getUserProjectsDir();
    LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdPvPvc);

    TestUtils.exec(cmdPvPvc, true);
  }

  public String getDirPath() {
    return dirPath;
  }

  public Map<String, Object> getPvMap() {
    return pvMap;
  }

}

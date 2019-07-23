// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
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

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");
  private Map<String, Object> pvMap;
  private String dirPath;

  public PersistentVolume(String dirPath, Map pvMap) throws Exception {
    this.dirPath = dirPath;
    this.pvMap = pvMap;
    
    String cmd =
            BaseTest.getProjectRoot()
        + "/src/integration-tests/bash/krun.sh -m "+BaseTest.getPvRoot()
        + ":/sharedparent -t 120 -c 'ls -ltr /sharedparent/* && mkdir -m 777 -p "
        + dirPath.replace(BaseTest.getPvRoot(), "/sharedparent/")
        + "'"; 
    
    TestUtils.exec(cmd, true);

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

    TestUtils.exec(cmdPvPvc, true);
  }

  public String getDirPath() {
    return dirPath;
  }

  public Map getPvMap() {
    return pvMap;
  }
}

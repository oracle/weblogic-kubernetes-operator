// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.logging.Logger;

public class DBUtils {
  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  /**
   * create oracle db pod in the k8s cluster
   *
   * @param dbPropsFile - db properties file
   * @return - OracleDB instance
   * @throws Exception - if any error occurs when creating Oracle DB pod
   */
  public static OracleDB createOracleDB(String dbPropsFile) throws Exception {
    OracleDB oracledb = new OracleDB(dbPropsFile);

    // check the db is ready
    String dbnamespace = oracledb.getNamespace();

    String cmd = "kubectl get pod -n " + dbnamespace + " -o jsonpath=\"{.items[0].metadata.name}\"";
    ExecResult result = ExecCommand.exec(cmd);
    String podName = result.stdout();

    logger.info("DEBUG: db namespace=" + dbnamespace);
    logger.info("DEBUG: podname=" + podName);
    TestUtils.checkPodReady("", dbnamespace);

    // check the db is ready to use
    cmd = "kubectl logs " + podName + " -n " + dbnamespace;
    TestUtils.checkCmdInLoop(cmd, "The database is ready for use", podName);

    return oracledb;
  }
}

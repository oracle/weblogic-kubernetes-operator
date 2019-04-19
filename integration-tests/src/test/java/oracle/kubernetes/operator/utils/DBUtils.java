// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Map;
import java.util.logging.Logger;

public class DBUtils {
  public static final String DEFAULT_FMWINFRA_DOCKER_IMAGENAME =
      "phx.ocir.io/weblogick8s/oracle/fmw-infrastructure";
  public static final String DEFAULT_FMWINFRA_DOCKER_IMAGETAG = "12.2.1.3";
  public static final String DEFAULT_RCU_SCHEMA_USERNAME = "myrcuuser";
  public static final String DEFAULT_RCU_SCHEMA_PASSWORD = "Oradoc_db1";
  public static final String DEFAULT_RCU_SYS_USERNAME = "sys";
  public static final String DEFAULT_RCU_SYS_PASSWORD = "Oradoc_db1";
  public static final String RCU_NAMESPACE = "rcu";
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
    logger.info("running command " + cmd);
    ExecResult result = TestUtils.exec(cmd);
    String podName = result.stdout();

    logger.info("DEBUG: db namespace=" + dbnamespace);
    logger.info("DEBUG: podname=" + podName);
    TestUtils.checkPodReady("", dbnamespace);

    // check the db is ready to use
    cmd = "kubectl logs " + podName + " -n " + dbnamespace;
    TestUtils.checkCmdInLoop(cmd, "The database is ready for use", podName);

    return oracledb;
  }

  /**
   * run RCU script to load database schema
   *
   * @param inputYaml - create domain input file
   * @throws Exception - if any error occurs
   */
  public static void runRCU(String inputYaml) throws Exception {
    Map<String, Object> inputMap = TestUtils.loadYaml(inputYaml);
    String dbConnectString = (String) inputMap.get("rcuDatabaseURL");
    String rcuPrefix = (String) inputMap.get("rcuSchemaPrefix");
    runRCU(RCU_NAMESPACE, dbConnectString, rcuPrefix);
  }

  /**
   * run RCU script to load database schema
   *
   * @param rcuNamespace - namespace for rcu pod
   * @param dbConnectString - db connect string to load the database schema
   * @param rcuPrefix - rcu prefix for the db schema name
   * @throws Exception - if any error occurs
   */
  public static void runRCU(String rcuNamespace, String dbConnectString, String rcuPrefix)
      throws Exception {
    String rcuPodName = createRCUPod(rcuNamespace);
    logger.info("DEBUG: rcuPodName=" + rcuPodName);

    // create password file used for rcu script
    String rcuPwdCmd = "echo " + DEFAULT_RCU_SYS_PASSWORD + "> /u01/oracle/pwd.txt";
    TestUtils.kubectlexec(rcuPodName, rcuNamespace, " -- bash -c '" + rcuPwdCmd + "'");
    rcuPwdCmd = "echo " + DEFAULT_RCU_SYS_PASSWORD + ">> /u01/oracle/pwd.txt";
    TestUtils.kubectlexec(rcuPodName, rcuNamespace, " -- bash -c '" + rcuPwdCmd + "'");

    // create rcu script to run
    String rcuScript =
        "/u01/oracle/oracle_common/bin/rcu -silent -createRepository -databaseType ORACLE"
            + " -connectString "
            + dbConnectString
            + " -dbUser sys -dbRole sysdba"
            + " -useSamePasswordForAllSchemaUsers true -selectDependentsForComponents true   -schemaPrefix "
            + rcuPrefix
            + " -component MDS -component IAU -component IAU_APPEND -component IAU_VIEWER -component OPSS"
            + " -component WLS -component STB < /u01/oracle/pwd.txt";

    TestUtils.kubectlexec(rcuPodName, rcuNamespace, " -- bash -c '" + rcuScript + "'");
  }

  /**
   * create a rcu pod to run rcu script
   *
   * @param rcuNamespace - namespace for rcu pod
   * @return - rcu pod name
   * @throws Exception - if any error occurs
   */
  private static String createRCUPod(String rcuNamespace) throws Exception {
    // create a rcu deployment
    String cmd =
        "kubectl run rcu -n "
            + rcuNamespace
            + " --image "
            + DEFAULT_FMWINFRA_DOCKER_IMAGENAME
            + ":"
            + DEFAULT_FMWINFRA_DOCKER_IMAGETAG
            + " -- sleep 300";
    TestUtils.exec(cmd);

    // get rcu pod name
    cmd = "kubectl get pod -n " + rcuNamespace + " -o jsonpath=\"{.items[0].metadata.name}\"";
    logger.info("running command " + cmd);
    ExecResult result = TestUtils.exec(cmd);
    String podName = result.stdout();

    // check the pod is ready
    TestUtils.checkPodReady(podName, rcuNamespace);

    return podName;
  }

  /**
   * delete a namespace
   *
   * @param namespace - namespace to delete
   * @throws Exception - if any error occurs
   */
  public static void deleteNamespace(String namespace) throws Exception {
    if (!namespace.equalsIgnoreCase("default")) {
      String command = "kubectl delete ns " + namespace;
      logger.info("Running " + command);
      ExecCommand.exec(command);

      // verify the namespace is deleted
      TestUtils.checkNamespaceDeleted(namespace);
    }
  }

  /**
   * create a namespace
   *
   * @param namespace - namespace to create
   * @throws Exception - if any error occurs
   */
  public static void createNamespace(String namespace) throws Exception {
    if (!namespace.equalsIgnoreCase("default")) {
      String command = "kubectl create ns " + namespace;
      logger.info("Running " + command);
      TestUtils.exec(command);
    }
  }
}

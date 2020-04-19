// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Map;
import java.util.logging.Logger;

import oracle.kubernetes.operator.BaseTest;
import org.junit.jupiter.api.Assertions;

public class DbUtils {
  public static final String DEFAULT_FMWINFRA_DOCKER_IMAGENAME =
      "container-registry.oracle.com/middleware/fmw-infrastructure";
  public static final String DEFAULT_FMWINFRA_DOCKER_IMAGETAG = "12.2.1.4";
  public static final String DEFAULT_RCU_SCHEMA_USERNAME = "myrcuuser";
  public static final String DEFAULT_RCU_SCHEMA_PASSWORD = "Oradoc_db1";
  public static final String DEFAULT_RCU_SYS_USERNAME = "sys";
  public static final String DEFAULT_RCU_SYS_PASSWORD = "Oradoc_db1";
  public static final String DEFAULT_RCU_NAMESPACE = "rcu";
  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  /**
   * Create oracle db pod in the k8s cluster.
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
   * Create Oracle DB pod and service in the k8s cluster default namespace.
   * 
   * @param scriptDir script dir
   * @param dbPort NodePort of DB
   * @param dbnamespace namesspace that DB instance is going to start
   * @throws Exception if any error occurs when creating Oracle DB pod and service
   */
  public static void startOracleDB(String scriptsDir, String dbPort, String dbNamespace) throws Exception {
    String cmd1 = "sh "
        + scriptsDir
        + "/scripts/create-oracle-db-service/start-db-service.sh"
        + " -i " + BaseTest.getOracledbImageName() + ":" + BaseTest.getOracledbImageTag()
        + " -p " + dbPort
        + " -n " + dbNamespace;;
    
    try {
      TestUtils.exec(cmd1, true);
      String cmd2 = "kubectl get pod" + " -n " + dbNamespace + " | grep oracle-db | cut -f1 -d \" \" ";
      logger.info("DEBUG: command to get DB pod: " + cmd2);
      ExecResult result = TestUtils.exec(cmd2);
      String podName = result.stdout();

      logger.info("DEBUG: DB podname=" + podName + " namespace: " + dbNamespace);
      TestUtils.checkPodReady(podName, dbNamespace);

      // check the db is ready to use
      String cmd3 = "kubectl logs " + podName + " -n " + dbNamespace;
      TestUtils.checkCmdInLoop(cmd3, "The database is ready for use", podName);

    } catch (Exception ex) {
      ex.printStackTrace();
      Assertions.fail("Failed to start Oracle DB.\n", ex.getCause());
    } 
  }
  
  /**
   * Stop oracle service.
   * 
   * @param scriptDir script dir
   * @param dbnamespace namespace that DB instance is going to stop
   * @throws Exception if any error occurs when dropping Oracle DB service
   */
  public static void stopOracleDB(String scriptsDir, String dbNamespace) throws Exception {
    String cmd = "sh " 
        + scriptsDir
        + "/scripts/create-oracle-db-service/stop-db-service.sh"
        + " -n " + dbNamespace;
    try {
      TestUtils.exec(cmd, true);
    } catch (Exception ex) {
      ex.printStackTrace();
      Assertions.fail("Failed to excute command.\n", ex.getCause());
    } 
  }
  
  /**
   * Create Oracle rcu pod and load database schema in the k8s cluster default namespace.
   * 
   * @param scriptDir script dir
   * @param rcuSchemaPrefix rcu SchemaPrefixe
   * @param dbPort NodePort of DB
   * @param dbnamespace namesspace that DB instance is going to start
   * @throws Exception - if any error occurs when creating Oracle rcu pod
   */
  public static void createRcuSchema(String scriptsDir, String rcuSchemaPrefix, 
      String dbUrl, String dbNamespace) throws Exception {
    String cmd;
    if (dbUrl == null) {
      cmd = "sh " 
        + scriptsDir
        + "/scripts/create-rcu-schema/create-rcu-schema.sh -s "
        + rcuSchemaPrefix
        + " -i " + BaseTest.getfmwImageName() + ":" + BaseTest.getfmwImageTag()
        + " -n " + dbNamespace;
    } else {
      cmd = "sh " 
          + scriptsDir
          + "/scripts/create-rcu-schema/create-rcu-schema.sh -s "
          + rcuSchemaPrefix
          + " -d "
          + dbUrl
          + " -i " + BaseTest.getfmwImageName() + ":" + BaseTest.getfmwImageTag()
          + " -n " + dbNamespace;
    }
    
    try {
      TestUtils.exec(cmd, true);
    } catch (Exception ex) {
      ex.printStackTrace();
      Assertions.fail("Failed to excute command.\n", ex.getCause());
    } 
  }
  
  /**
   * Create Docker Registry Secret for the DB namespace.
   * 
   * @param namespace namespace where the docker registry secreted is going to create
   * @throws Exception when the kubectl create secret command fails
   */
  public static void createDockerRegistrySecret(String namespace) throws Exception {
    String secret = System.getenv("IMAGE_PULL_SECRET_FMWINFRA");
    if (secret == null) {
      secret = "docker-store";
    }
    String ocrserver = System.getenv("OCR_SERVER");
    if (ocrserver == null) {
      ocrserver = "container-registry.oracle.com";
    }
    try {
      TestUtils.createDockerRegistrySecret(
          secret,
          ocrserver,
          System.getenv("OCR_USERNAME"),
          System.getenv("OCR_PASSWORD"),
          System.getenv("OCR_USERNAME") + "@oracle.com",
          namespace);
    } catch (Exception ex) {
      ex.printStackTrace();
      Assertions.fail("Failed to excute command.\n", ex.getCause());
    } 
  }

  
  /**
   * Drop Oracle rcu schema.
   * 
   * @param scriptDir script dir
   * @param rcuSchemaPrefix rcu SchemaPrefixe
   * @param dbnamespace namesspace that DB instance is going to start
   * @throws Exception if any error occurs when dropping rcu schema
   */
  public static void dropRcuSchema(String scriptsDir, String rcuSchemaPrefix, String dbNamespace) throws Exception {
    String cmd = "sh " 
        + scriptsDir
        + "/scripts/create-rcu-schema/drop-rcu-schema.sh "
        + " -s " + rcuSchemaPrefix
        + " -n " + dbNamespace;
    TestUtils.exec(cmd, true);
    try {
      TestUtils.exec(cmd, true);
    } catch (Exception ex) {
      ex.printStackTrace();
      Assertions.fail("Failed to excute command.\n", ex.getCause());
    } 
  }
  
  /**
   * Delete RCU pod.
   *
   * @throws Exception - if any error occurs when deleting RCU pod
   */
  public static void deleteRcuPod(String scriptsDir) throws Exception {
    String cmd = "kubectl delete -f " 
        + scriptsDir
        + "/scripts/create-rcu-schema/common/rcu.yaml --ignore-not-found";
    TestUtils.exec(cmd, true);
  }
  
  /**
   * Delete DB pod.
   * 
   * @param scriptsDir script dir
   * @throws Exception if any error occurs when deleting DB pod
   */
  public static void deleteDbPod(String scriptsDir) throws Exception {
    String cmd = "kubectl delete -f " 
        + scriptsDir
        + "/scripts/create-oracle-db-service/common/oracle.db.yaml --ignore-not-found";
    TestUtils.exec(cmd, true);
    try {
      TestUtils.exec(cmd, true);
    } catch (Exception ex) {
      ex.printStackTrace();
      Assertions.fail("Failed to excute command.\n", ex.getCause());
    } 
  }

  /**
   * Run RCU script to load database schema.
   *
   * @param rcuPodName - rcu pod name
   * @param inputYaml  - create domain input file
   * @throws Exception - if any error occurs
   */
  public static void runRcu(String rcuPodName, String inputYaml) throws Exception {
    Map<String, Object> inputMap = TestUtils.loadYaml(inputYaml);
    runRcu(rcuPodName, inputMap);
  }

  /**
   * Run RCU script to load database schema.
   *
   * @param rcuPodName - rcu pod name
   * @param inputMap   - domain input map
   * @throws Exception - if any error occurs
   */
  public static void runRcu(String rcuPodName, Map<String, Object> inputMap) throws Exception {
    String dbConnectString = (String) inputMap.get("rcuDatabaseURL");
    String rcuPrefix = (String) inputMap.get("rcuSchemaPrefix");
    runRcu(rcuPodName, DEFAULT_RCU_NAMESPACE, dbConnectString, rcuPrefix);
  }

  /**
   * Run RCU script to load database schema.
   *
   * @param rcuNamespace    - namespace for rcu pod
   * @param dbConnectString - db connect string to load the database schema
   * @param rcuPrefix       - rcu prefix for the db schema name
   * @throws Exception - if any error occurs
   */
  private static void runRcu(
      String rcuPodName, String rcuNamespace, String dbConnectString, String rcuPrefix)
      throws Exception {

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
   * Create a rcu pod to run rcu script.
   *
   * @param rcuNamespace - namespace for rcu pod
   * @return - rcu pod name
   * @throws Exception - if any error occurs
   */
  public static String createRcuPod(String rcuNamespace) throws Exception {
    // create a rcu deployment
    String cmd =
        "kubectl run rcu -n "
            + rcuNamespace
            + " --image "
            + DEFAULT_FMWINFRA_DOCKER_IMAGENAME
            + ":"
            + DEFAULT_FMWINFRA_DOCKER_IMAGETAG
            + " -- sleep 100000";
    logger.info("running command " + cmd);
    TestUtils.exec(cmd);

    // get rcu pod name
    cmd = "kubectl get pod -n " + rcuNamespace + " -o jsonpath=\"{.items[0].metadata.name}\"";
    logger.info("running command " + cmd);
    ExecResult result = TestUtils.exec(cmd);
    String podName = result.stdout();
    logger.info("DEBUG: rcuPodName=" + podName);

    // check the pod is ready
    TestUtils.checkPodReady(podName, rcuNamespace);

    return podName;
  }

  /**
   * Delete a namespace.
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
   * Create a namespace.
   * 
   * @param namespace namespace to create
   * @throws Exception if any error occurs
   */
  public static void createNamespace(String namespace) throws Exception {
    if (!namespace.equalsIgnoreCase("default")) {
      try {
        String cmd1 = "kubectl delete ns " + namespace + " --ignore-not-found";
        logger.info("Running " + cmd1);
        TestUtils.exec(cmd1, true);
        String cmd2 = "kubectl create ns " + namespace;
        logger.info("Running " + cmd2);
        TestUtils.exec(cmd2, true);
      } catch (Exception ex) {
        ex.printStackTrace();
        Assertions.fail("Failed to create namespace.");
      }
    }
  }
  
  /**
   * Create Oracle rcu pod and load database schema in the k8s cluster default namespace.
   * 
   * @param scriptDir script dir
   * @param dbPort NodePort of DB
   * @param dbUrl URL of DB
   * @param rcuSchemaPrefix rcu SchemaPrefixe
   * @param dbNamespace namesspace that DB instance is going to start
   * @throws Exception if any error occurs when creating Oracle rcu pod
   */
  public static void createDbRcu(String scriptDir, int dbPort, String dbUrl, String rcuSchemaPrefix,
      String dbNamespace) throws Exception {  
    
    try {  
      //delete leftover pods caused by test being aborted
      deleteRcuPod(scriptDir);
      deleteDbPod(scriptDir);
         
      DbUtils.createDockerRegistrySecret(dbNamespace);
      DbUtils.startOracleDB(scriptDir, String.valueOf(dbPort), dbNamespace);
      DbUtils.createRcuSchema(scriptDir,rcuSchemaPrefix, dbUrl, dbNamespace);
    } catch (Exception ex) {
      ex.printStackTrace();
      Assertions.fail("Failed to start DB and create RCU schema.\n", ex.getCause());
    }
  }

}

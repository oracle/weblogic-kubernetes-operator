// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kubernetes.operator.BaseTest;

public class DbUtils {
  public static final String DEFAULT_FMWINFRA_DOCKER_IMAGENAME =
      "container-registry.oracle.com/middleware/fmw-infrastructure";
  public static final String DEFAULT_FMWINFRA_DOCKER_IMAGETAG = "12.2.1.4";
  public static final String DEFAULT_RCU_SCHEMA_USERNAME = "myrcuuser";
  public static final String DEFAULT_RCU_SCHEMA_PASSWORD = "Oradoc_db1";
  public static final String DEFAULT_RCU_SYS_USERNAME = "sys";
  public static final String DEFAULT_RCU_SYS_PASSWORD = "Oradoc_db1";
  public static final String DEFAULT_RCU_NAMESPACE = "rcu";
  public static final String DEFAULT_ImagePullSecret = "docker-store";
  public static final String DEFAULT_ImagePullPolicy = "Always";
  private static int count = 0;
  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  /**
   * Create Oracle db pod in the k8s cluster.
   *
   * @param dbPropsFile - db properties file
   * @return OracleDB instance
   * @throws Exception if any error occurs when creating Oracle DB pod
   */
  public static OracleDB createOracleDB(String dbPropsFile) throws Exception {
    OracleDB oracledb = new OracleDB(dbPropsFile);

    // check the db is ready
    String dbnamespace = oracledb.getNamespace();
    String cmd = "kubectl get pod -n " + dbnamespace + " -o jsonpath=\"{.items[0].metadata.name}\"";
    logger.info("running command " + cmd);
    ExecResult result = TestUtils.execOrAbortProcess(cmd, true);
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
   * Start Oracle DB pod and service in the specified namespace.
   * 
   * @param scriptsDir directory of scripts
   * @param dbPort NodePort of DB
   * @param dbNamespace namespace where DB instance is going to start
   * @throws Exception if any error occurs when creating Oracle DB pod and service
   */
  public static void startOracleDB(String scriptsDir, String dbPort, 
      String dbNamespace) throws Exception {
    String cmd1 = "sh "
        + scriptsDir
        + "/scripts/create-oracle-db-service/start-db-service.sh"
        + " -i " + BaseTest.getOracledbImageName() + ":" + BaseTest.getOracledbImageTag()
        + " -p " + dbPort
        + " -n " + dbNamespace;
    
    TestUtils.execOrAbortProcess(cmd1, true);
    String cmd2 = "kubectl get pod -n " 
        + dbNamespace 
        + " -o jsonpath=\"{.items[0].metadata.name}\"";
    logger.info("DEBUG: command to get DB pod: " + cmd2);
    ExecResult result = TestUtils.execOrAbortProcess(cmd2, true);
    String podName = result.stdout();

    logger.info("DEBUG: DB podname=" + podName + " namespace: " + dbNamespace);
    TestUtils.checkPodReady(podName, dbNamespace);

    // check the db is ready to use
    String cmd3 = "kubectl logs " + podName + " -n " + dbNamespace;
    TestUtils.checkCmdInLoop(cmd3, "The database is ready for use", podName);
  }
  
  /**
   * Stop Oracle DB service.
   * 
   * @param scriptsDir directory of scripts
   * @param dbNamespace namespace where DB instance was 
   * @throws Exception if any error occurs when stopping Oracle DB service
   */
  public static void stopOracleDB(String scriptsDir, String dbNamespace) throws Exception {
    String cmd = "sh " 
        + scriptsDir
        + "/scripts/create-oracle-db-service/stop-db-service.sh"
        + " -n " + dbNamespace;
   
    TestUtils.execOrAbortProcess(cmd, true);
  }
  
  /**
   * Create Oracle rcu pod and load database schema in the specified namespace.
   * 
   * @param scriptsDir directory of scripts
   * @param rcuSchemaPrefix prefix of RCU schema
   * @param namespace namespace where RCU schema is going to be created
   * @throws Exception if any error occurs when creating RCU schema
   */
  public static void createRcuSchema(String scriptsDir, String rcuSchemaPrefix, 
      String dbUrl, String namespace) throws Exception {
    String cmd;
    cmd = "sh " 
        + scriptsDir
        + "/scripts/create-rcu-schema/create-rcu-schema.sh -s "
        + rcuSchemaPrefix
        + " -d "
        + dbUrl
        + " -p "
        + DEFAULT_ImagePullSecret
        + " -i " 
        + BaseTest.getfmwImageName() + ":" + BaseTest.getfmwImageTag()
        + " -u "
        + DEFAULT_ImagePullPolicy
        + " -n " 
        + namespace
        + " -o "
        + scriptsDir + "/scripts/create-rcu-schema/rcuoutput";
    
    TestUtils.execOrAbortProcess(cmd, true);     
  }
  
  /**
   * Create Docker Registry Secret in the specified namespace.
   * 
   * @param namespace namespace where the docker registry secret is going to create
   * @throws Exception when the kubectl create secret command fails
   */
  public static void createDockerRegistrySecret(String namespace) throws Exception {
    String secret = System.getenv("IMAGE_PULL_SECRET_FMWINFRA");
    if (secret == null) {
      secret = DEFAULT_ImagePullSecret;
    }
    String ocrserver = System.getenv("OCR_SERVER");
    if (ocrserver == null) {
      ocrserver = "container-registry.oracle.com";
    }
   
    TestUtils.createDockerRegistrySecret(
          secret,
          ocrserver,
          System.getenv("OCR_USERNAME"),
          System.getenv("OCR_PASSWORD"),
          System.getenv("OCR_USERNAME"),
          namespace);
  }

  /**
   * Drop Oracle rcu schema.
   * 
   * @param scriptsDir directory of scripts
   * @param rcuSchemaPrefix prefix of RCU schema
   * @param namespace namespace where RCU schema was 
   * @throws Exception if any error occurs when dropping rcu schema
   */
  public static void dropRcuSchema(String scriptsDir, String rcuSchemaPrefix, 
      String namespace) throws Exception {
    String cmd = "sh " 
        + scriptsDir
        + "/scripts/create-rcu-schema/drop-rcu-schema.sh "
        + " -s " + rcuSchemaPrefix
        + " -n " + namespace;
    TestUtils.execOrAbortProcess(cmd, true);
  }
  
  /**
   * Delete RCU pod.
   * 
   * @param scriptsDir directory of scripts
   * @throws Exception - if any error occurs when deleting RCU pod
   */
  public static void deleteRcuPod(String scriptsDir) throws Exception {
    String cmd = "kubectl delete -f " 
        + scriptsDir
        + "/scripts/create-rcu-schema/rcuoutput/rcu.yaml --ignore-not-found";
    TestUtils.execOrAbortProcess(cmd, true);
  }
  
  /**
   * Delete DB pod.
   * 
   * @param scriptsDir directory of scripts
   * @throws Exception if any error occurs when deleting DB pod
   */
  public static void deleteDbPod(String scriptsDir) throws Exception {
    String cmd = "kubectl delete -f " 
        + scriptsDir
        + "/scripts/create-oracle-db-service/common/oracle.db.yaml --ignore-not-found";
    TestUtils.execOrAbortProcess(cmd, true);
  }
  
  /**
   * Delete a namespace.
   *
   * @param namespace namespace to delete
   * @throws Exception if any error occurs
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
      String cmd1 = "kubectl delete ns " + namespace + " --ignore-not-found";
      logger.info("Running " + cmd1);
      TestUtils.execOrAbortProcess(cmd1, true);
      String cmd2 = "kubectl create ns " + namespace;
      logger.info("Running " + cmd2);
      TestUtils.execOrAbortProcess(cmd2, true);
    }
  }
  
  /**
   * Start DB instance, create Oracle rcu pod and load database schema in the specified namespace.
   * 
   * @param scriptsDir directory of scripts
   * @param dbPort NodePort of DB
   * @param dbUrl URL of DB
   * @param rcuSchemaPrefix rcu SchemaPrefixe
   * @param namespace namespace where DB and RCU schema are going to start
   * @throws Exception if any error occurs when setting up RCU database
   */
  public static void setupRcuDatabase(String scriptsDir, int dbPort, String dbUrl, 
      String rcuSchemaPrefix, String namespace) throws Exception {  
    
    createNamespace(namespace);
    createDockerRegistrySecret(namespace);

    String cmd1 = "mkdir -p " + scriptsDir
        + "/scripts/create-rcu-schema/rcuoutput";
    TestUtils.execOrAbortProcess(cmd1);
    String cmd2 = "cp " 
        + scriptsDir
        + "/scripts/create-rcu-schema/common/rcu.yaml "
        + scriptsDir
        + "/scripts/create-rcu-schema/rcuoutput/";
    TestUtils.execOrAbortProcess(cmd2);
    createDBandRCUschema(scriptsDir, dbPort, dbUrl, rcuSchemaPrefix, namespace);
    LoggerHelper.getLocal().log(Level.INFO,"RCU schema is going to create for:" 
        + " namespace: " + namespace 
        + " dbUrl:" + dbUrl 
        + " dbPort: " + dbPort
        + " rcuSchemaPrefix: " + rcuSchemaPrefix); 
    
  }
  
  private static void createDBandRCUschema(String scriptsDir, int dbPort, String dbUrl, 
      String rcuSchemaPrefix, String namespace) throws Exception {
    count++;
    if (count < 2) {
      deleteRcuPod(scriptsDir);
      deleteDbPod(scriptsDir);
      try {
        startOracleDB(scriptsDir, String.valueOf(dbPort), namespace);
        createRcuSchema(scriptsDir,rcuSchemaPrefix, dbUrl, namespace);
        LoggerHelper.getLocal().log(Level.INFO,"RCU schema is created for:" 
            + " namespace: " + namespace 
            + " dbUrl:" + dbUrl 
            + " dbPort: " + dbPort
            + " rcuSchemaPrefix: " + rcuSchemaPrefix);
        LoggerHelper.getLocal().log(Level.INFO,"DEBUG in DBUtils count: " + count);
      } catch (Exception ex) {
        createDBandRCUschema(scriptsDir, dbPort, dbUrl, rcuSchemaPrefix, namespace);
      }
    }
    
  }

}

// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Properties;
import java.util.logging.Logger;

public class OracleDB {

  // DB default properties
  public static final String DEFAULT_DB_NAME = "infradb";
  public static final String DEFAULT_DB_NAMESPACE = "db";
  public static final String DEFAULT_DB_IMAGE =
      "container-registry.oracle.com/database/enterprise:12.2.0.1-slim";
  public static final String DEFAULT_DB_PORT = "1521";
  public static final String DEFAULT_DB_SID = "InfraDB";
  public static final String DEFAULT_DB_PDB = "InfraPDB1";
  public static final String DEFAULT_DB_DOMAIN = "us.oracle.com";
  public static final String DEFAULT_DB_BUNDLE = "basic";

  // db instance variables
  private String name;
  private String namespace;
  private String image;
  private String port;
  private String db_sid;
  private String db_pdb;
  private String db_domain;
  private String db_bundle;

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  public OracleDB(String dbPropFile) throws Exception {
    Properties dbprops = TestUtils.loadProps(dbPropFile);
    name = dbprops.getProperty("name", DEFAULT_DB_NAME);
    namespace = dbprops.getProperty("namespace", DEFAULT_DB_NAMESPACE);
    image = dbprops.getProperty("image", DEFAULT_DB_IMAGE);
    port = dbprops.getProperty("port", DEFAULT_DB_PORT);
    db_sid = dbprops.getProperty("db_sid", DEFAULT_DB_SID);
    db_pdb = dbprops.getProperty("db_pdb", DEFAULT_DB_PDB);
    db_domain = dbprops.getProperty("db_domain", DEFAULT_DB_DOMAIN);
    db_bundle = dbprops.getProperty("db_bundle", DEFAULT_DB_BUNDLE);

    // clean the db env first
    cleanDB();

    String command;
    // create the namespace
    DBUtils.createNamespace(namespace);

    // create db
    command =
        "kubectl run "
            + name
            + " -n "
            + namespace
            + " --image="
            + image
            + " --port="
            + port
            + " --env=\"DB_SID="
            + db_sid
            + "\" --env=\"DB_PDB="
            + db_pdb
            + "\" --env=\"DB_DOMAIN="
            + db_domain
            + "\" --env=\"DB_BUNDLE="
            + db_bundle
            + "\" --expose";
    logger.info("Running " + command);
    TestUtils.exec(command);
  }

  public String getName() {
    return name;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getPort() {
    return port;
  }

  public String getImage() {
    return image;
  }

  public String getDBSid() {
    return db_sid;
  }

  public String getDBPdb() {
    return db_pdb;
  }

  public String getDBDomain() {
    return db_domain;
  }

  public String getDBBundle() {
    return db_bundle;
  }

  private void cleanDB() throws Exception {
    // delete the namespace if the db namespace is not default
    String command;
    if (!namespace.equalsIgnoreCase("default")) {
      DBUtils.deleteNamespace(namespace);
    } else {
      // delete the deployment and service
      command = "kubectl delete deployment " + name + " -n " + namespace;
      logger.info("Running " + command);
      ExecCommand.exec(command);

      // delete the service
      command = "kubectl delete service " + name + " -n " + namespace;
      logger.info("Running " + command);
      ExecCommand.exec(command);
    }
  }
}

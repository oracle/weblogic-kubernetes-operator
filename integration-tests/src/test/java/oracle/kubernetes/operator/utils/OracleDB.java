// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Properties;
import java.util.logging.Level;

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
  private String dbsid;
  private String dbpdb;
  private String dbdomain;
  private String dbbundle;

  /**
   * Construct Oracle DB.
   * @param dbPropFile DB properties file
   * @throws Exception on failure
   */
  public OracleDB(String dbPropFile) throws Exception {
    Properties dbprops = TestUtils.loadProps(dbPropFile);
    name = dbprops.getProperty("name", DEFAULT_DB_NAME);
    namespace = dbprops.getProperty("namespace", DEFAULT_DB_NAMESPACE);
    image = dbprops.getProperty("image", DEFAULT_DB_IMAGE);
    port = dbprops.getProperty("port", DEFAULT_DB_PORT);
    dbsid = dbprops.getProperty("db_sid", DEFAULT_DB_SID);
    dbpdb = dbprops.getProperty("db_pdb", DEFAULT_DB_PDB);
    dbdomain = dbprops.getProperty("db_domain", DEFAULT_DB_DOMAIN);
    dbbundle = dbprops.getProperty("db_bundle", DEFAULT_DB_BUNDLE);

    // clean the db env first
    cleanDB();

    String command;
    // create the namespace
    DbUtils.createNamespace(namespace);

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
            + dbsid
            + "\" --env=\"DB_PDB="
            + dbpdb
            + "\" --env=\"DB_DOMAIN="
            + dbdomain
            + "\" --env=\"DB_BUNDLE="
            + dbbundle
            + "\" --expose";
    LoggerHelper.getLocal().log(Level.INFO, "Running " + command);
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

  public String getDbSid() {
    return dbsid;
  }

  public String getDbPdb() {
    return dbpdb;
  }

  public String getDbDomain() {
    return dbdomain;
  }

  public String getDbBundle() {
    return dbbundle;
  }

  private void cleanDB() throws Exception {
    // delete the namespace if the db namespace is not default
    String command;
    if (!namespace.equalsIgnoreCase("default")) {
      DbUtils.deleteNamespace(namespace);
    } else {
      // delete the deployment and service
      command = "kubectl delete deployment " + name + " -n " + namespace;
      LoggerHelper.getLocal().log(Level.INFO, "Running " + command);
      ExecCommand.exec(command);

      // delete the service
      command = "kubectl delete service " + name + " -n " + namespace;
      LoggerHelper.getLocal().log(Level.INFO, "Running " + command);
      ExecCommand.exec(command);
    }
  }
}

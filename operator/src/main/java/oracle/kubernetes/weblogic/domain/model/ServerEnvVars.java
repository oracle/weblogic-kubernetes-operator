// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Arrays;
import java.util.List;

/**
 * Environment variables defined for the startup script at operator/src/main/resources/scripts/startServer.sh.
 */
public class ServerEnvVars {

  public static final String DOMAIN_UID = "DOMAIN_UID";

  /** The name of a WebLogic domain. */
  public static final String DOMAIN_NAME = "DOMAIN_NAME";

  /** The path to the domain home, either in a PV or image. */
  public static final String DOMAIN_HOME = "DOMAIN_HOME";
  
  /** The path to the node manager home, either in a PV or image. */
  public static final String NODEMGR_HOME = "NODEMGR_HOME";

  /** The name of the managed server. */
  public static final String SERVER_NAME = "SERVER_NAME";

  /** The name of the server service. */
  public static final String SERVICE_NAME = "SERVICE_NAME";

  /** The name of the admin instance. */
  public static final String ADMIN_NAME = "ADMIN_NAME";

  /** The name of the server service for the admin server. */
  public static final String AS_SERVICE_NAME = "AS_SERVICE_NAME";

  /** The plaintext port on which the admin server is listening. */
  public static final String ADMIN_PORT = "ADMIN_PORT";

  /** The secure port on which the admin server is listening. */
  public static final String ADMIN_PORT_SECURE = "ADMIN_PORT_SECURE";

  /**
   * If defined, WebLogic Server sets a secure protocol(https/t3s) in the "AdminURL" property in NM startup.properties.
   * WebLogic Node Manager then sets the ADMIN_URL env variable before starting the managed server.
   */
  public static final String ADMIN_SERVER_PORT_SECURE = "ADMIN_SERVER_PORT_SECURE";

  /** The location for the logs. */
  public static final String LOG_HOME = "LOG_HOME";

  /** The location for the centralized data directory. */
  public static final String DATA_HOME = "DATA_HOME";

  /** 'true' or 'false' to indicate whether the server output should be included in the pod log. */
  public static final String SERVER_OUT_IN_POD_LOG = "SERVER_OUT_IN_POD_LOG";

  /** 'true' or 'false' to indicate whether the server HTTP access log should be include in the
   *  directory specified by LOG_HOME. */
  public static final String ACCESS_LOG_IN_LOG_HOME = "ACCESS_LOG_IN_LOG_HOME";

  /** If present, pod scripts will watch for changes to override configurations and move them into place. */
  public static final String DYNAMIC_CONFIG_OVERRIDE = "DYNAMIC_CONFIG_OVERRIDE";

  private static final List<String> RESERVED_NAMES = Arrays.asList(
        DOMAIN_UID, DOMAIN_NAME, DOMAIN_HOME, NODEMGR_HOME, SERVER_NAME, SERVICE_NAME,
        ADMIN_NAME, AS_SERVICE_NAME, ADMIN_PORT, ADMIN_PORT_SECURE, ADMIN_SERVER_PORT_SECURE,
        LOG_HOME, SERVER_OUT_IN_POD_LOG, DATA_HOME, ACCESS_LOG_IN_LOG_HOME, DYNAMIC_CONFIG_OVERRIDE);

  static boolean isReserved(String name) {
    return RESERVED_NAMES.contains(name);
  }
}

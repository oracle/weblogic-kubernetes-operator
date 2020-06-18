// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Arrays;
import java.util.List;

/**
 * Environment variables used in the introspection job.
 */
public class IntrospectorJobEnvVars {
  /**
   * The namespace in which the introspection job will run.
   */
  public static final String NAMESPACE = "NAMESPACE";

  /**
   * The path to the home directory for the introspection job.
   */
  public static final String INTROSPECT_HOME = "INTROSPECT_HOME";

  /**
   * The credentials used by the introspection job - weblogic credenitals.
   */
  public static final String CREDENTIALS_SECRET_NAME = "CREDENTIALS_SECRET_NAME";

  /**
   * The credentials used by the introspection job - opss key passphrase.
   */
  public static final String OPSS_KEY_SECRET_NAME = "OPSS_KEY_SECRET_NAME";

  /**
   * The credentials used by the introspection job - opss wallet file.
   */
  public static final String OPSS_WALLETFILE_SECRET_NAME = "OPSS_WALLETFILE_SECRET_NAME";


  /**
   * The credentials used by the introspection job - wdt encryption passphrase.
   */
  public static final String WDT_ENCRYPTION_PASSPHRASE_NAME = "WDT_ENCRYPTION_PASSPHRASE_NAME";

  /**
   * The credentials used by the introspection job - runtime encryption secret name.
   */
  public static final String RUNTIME_ENCRYPTION_SECRET_NAME = "RUNTIME_ENCRYPTION_SECRET_NAME";


  /**
   * The domain source type.
   */
  public static final String DOMAIN_SOURCE_TYPE = "DOMAIN_SOURCE_TYPE";

  /**
   * The wdt domain type.
   */
  public static final String WDT_DOMAIN_TYPE = "WDT_DOMAIN_TYPE";

  /**
   * Istio enabled.
   */
  public static final String ISTIO_ENABLED = "ISTIO_ENABLED";

  /**
   * Istio readiness port.
   */
  public static final String ISTIO_READINESS_PORT = "ISTIO_READINESS_PORT";

  /**
   * Returns true if the specified environment variable name is reserved by the operator for communication with
   * the introspection job.
   * @param name an environment variable name
   * @return true if the name is reserved
   */
  static boolean isReserved(String name) {
    return ServerEnvVars.isReserved(name) || RESERVED_NAMES.contains(name);
  }

  private static final List<String> RESERVED_NAMES = Arrays.asList(NAMESPACE, INTROSPECT_HOME, CREDENTIALS_SECRET_NAME);
}

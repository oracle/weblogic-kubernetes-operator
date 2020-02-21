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
   * The credentials used by the introspection job.
   */
  public static final String CREDENTIALS_SECRET_NAME = "CREDENTIALS_SECRET_NAME";

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

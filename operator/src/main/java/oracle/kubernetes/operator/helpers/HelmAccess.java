// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A class which allows the operator to retrieve variables from Helm.
 */
public class HelmAccess {

  /** Helm variable to specify list of domain namespace. */
  public static final String OPERATOR_DOMAIN_NAMESPACES = "OPERATOR_DOMAIN_NAMESPACES";

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"}) // Not final in order to allow unit tests to set its value
  private static Function<String,String> getHelmVariableFunction = System::getenv;

  /**
   * Returns the specified Helm variable.
   * @param variableName the name of the variable to return
   * @return Variable value
   */
  public static String getHelmVariable(String variableName) {
    return getHelmVariableFunction.apply(variableName);
  }

  /**
   * Return the comma-separated list of namespaces to be managed by the operator.
   * @return Domain namespace list
   */
  public static @Nullable String getHelmSpecifiedNamespaceList() {
    return getHelmVariable(OPERATOR_DOMAIN_NAMESPACES);
  }
}

// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.function.Function;

/**
 * A class which allows the operator to retrieve variables from Helm.
 */
public class HelmAccess {
  @SuppressWarnings("FieldMayBeFinal") // Not final in order to allow unit tests to set its value
  private static Function<String,String> getHelmVariableFunction = System::getenv;

  /**
   * Returns the specied Helm variable.
   * @param variableName the name of the variable to return
   */
  public static String getHelmVariable(String variableName) {
    return getHelmVariableFunction.apply(variableName);
  }
}

// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

/** Common log formatter to format log messages in JSON format. */
public class CommonLoggingFormatter extends BaseLoggingFormatter {

  @Override
  Object getCurrentFiberIfSet() {
    return null;
  }

  @Override
  String getFiber() {
    return null;
  }

  @Override
  protected String getDomainUid(Object fiber) {
    return null;
  }

  @Override
  String getNamespace(Object fiber) {
    return null;
  }
}

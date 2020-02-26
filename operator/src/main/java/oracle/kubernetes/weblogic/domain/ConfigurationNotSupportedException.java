// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

/**
 * An exception to be thrown on an attempt to configure a field not supported by the domain model.
 * Only used in unit testing.
 */
public class ConfigurationNotSupportedException extends RuntimeException {
  private final String context;
  private final String field;

  public ConfigurationNotSupportedException(String context, String field) {
    this.context = context;
    this.field = field;
  }

  @Override
  public String getMessage() {
    return String.format("Configuring field %s is not supported for a %s", field, context);
  }
}

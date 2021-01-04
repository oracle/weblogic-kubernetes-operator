// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

/**
 * Describes a problem with a domain resource.
 */
public class DomainValidationFailure {
  private final String reason;
  private final String message;

  DomainValidationFailure(String reason, String message) {
    this.reason = reason;
    this.message = message;
  }

  /**
   * Returns the reason for the failure. This is a camel-cased string with no spaces.
   * @return the reason code
   */
  public String getReason() {
    return reason;
  }

  /**
   * Returns a human-readable description of the problem.
   * @return the descriptive message
   */
  public String getMessage() {
    return message;
  }
}

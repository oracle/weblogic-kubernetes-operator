// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

/**
 * Describes a problem with a domain resource.
 */
public class DomainValidationFailure {
  private String reason;
  private String message;

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

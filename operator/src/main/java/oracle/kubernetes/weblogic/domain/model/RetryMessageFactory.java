// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

/**
 * An interface to allow the DomainStatus to generate a summary message based on configured retry parameters.
 */
interface RetryMessageFactory {

  /**
   * Returns a status summary message that indicates whether a retry is scheduled, and when.
   * @param domainStatus a status with a failure condition.
   * @param selected the failure condition selected to produce the summary message.
   */
  String createRetryMessage(DomainStatus domainStatus, DomainCondition selected);
}

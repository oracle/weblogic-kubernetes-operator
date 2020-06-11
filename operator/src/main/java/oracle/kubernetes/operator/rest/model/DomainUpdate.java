// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Describes an update to the domain applied by a human operator.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainUpdate {

  private final DomainUpdateType updateType;

  public DomainUpdate(DomainUpdateType updateType) {
    this.updateType = updateType;
  }

  public DomainUpdateType getUpdateType() {
    return updateType;
  }
}

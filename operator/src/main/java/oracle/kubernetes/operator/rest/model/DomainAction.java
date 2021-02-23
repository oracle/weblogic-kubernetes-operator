// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Describes an action to the domain that the operation should take.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainAction {

  private final DomainActionType action;

  public DomainAction(DomainActionType action) {
    this.action = action;
  }

  public DomainActionType getAction() {
    return action;
  }
}

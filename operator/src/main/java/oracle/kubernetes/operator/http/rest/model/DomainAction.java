// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Describes an action to the domain that the operation should take.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DomainAction(DomainActionType action) {

}

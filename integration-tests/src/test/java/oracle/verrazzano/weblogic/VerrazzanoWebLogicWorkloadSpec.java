// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import oracle.weblogic.domain.DomainResource;

@ApiModel(description = "VerrazzanoWebLogicWorkloadSpec is a description of a VerrazzanoWebLogicWorkloadSpec.")
public class VerrazzanoWebLogicWorkloadSpec {
  @ApiModelProperty("Specification for the VerrazzanoWebLogicWorkloadSpec.")
  private DomainResource template;
}

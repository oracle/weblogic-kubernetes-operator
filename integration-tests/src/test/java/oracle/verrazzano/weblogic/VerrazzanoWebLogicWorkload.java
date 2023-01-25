// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "ComponentSpec is a description of a component.")
public class VerrazzanoWebLogicWorkload {

  @ApiModelProperty("The API version for the Domain.")
  private String apiVersion;

  @ApiModelProperty("The type of resource. Must be 'Domain'.")
  private String kind;

  @ApiModelProperty("The domain meta-data. Must include the name and namespace.")
  private V1ObjectMeta metadata = new V1ObjectMeta();
  
  @ApiModelProperty("Configuration for the Workload.")
  private VerrazzanoWebLogicWorkloadSpec workLoadSpec;

}

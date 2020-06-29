// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ServerService extends KubernetesResource {

  @SerializedName("precreateService")
  @Description(
      "If true, the operator will create Services even for Managed Server instances without running Pods.")
  private Boolean isPrecreateService;

  void fillInFrom(ServerService serverService1) {
    super.fillInFrom(serverService1);
    this.isPrecreateService =
        Optional.ofNullable(isPrecreateService).orElse(serverService1.isPrecreateService);
  }

  public Boolean isPrecreateService() {
    return Optional.ofNullable(isPrecreateService).orElse(Boolean.FALSE);
  }

  public void setIsPrecreateService(Boolean isPrecreateService) {
    this.isPrecreateService = isPrecreateService;
  }

  public ServerService withIsPrecreateService(Boolean isPrecreateService) {
    this.isPrecreateService = isPrecreateService;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("isPrecreateService", isPrecreateService)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ServerService that = (ServerService) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(isPrecreateService, that.isPrecreateService)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(isPrecreateService)
        .toHashCode();
  }
}

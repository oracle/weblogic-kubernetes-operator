// Copyright (c) 2020, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Configuration {

  @Description("Configuration overrides")
  private Overrides overrides;

  @Description("Model in image model files and properties")
  private Model model;

  public Overrides getOverrides() {
    return this.overrides;
  }

  public void setOverrides(Overrides overrides) {
    this.overrides = overrides;
  }

  public Configuration withOverrides(Overrides overrides) {
    this.overrides = overrides;
    return this;
  }

  public Model getModel() {
    return model;
  }

  public void setModel(Model model) {
    this.model = model;
  }

  public Configuration withModel(Model model) {
    this.model = model;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("overrides", overrides)
            .append("model", model);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder().append(overrides).append(model);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Configuration)) {
      return false;
    }

    Configuration rhs = ((Configuration) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(overrides, rhs.overrides)
            .append(model, rhs.model);

    return builder.isEquals();
  }
}


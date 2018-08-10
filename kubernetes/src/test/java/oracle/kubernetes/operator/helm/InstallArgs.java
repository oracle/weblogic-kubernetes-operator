// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/** The arguments needed to install a helm chart */
@SuppressWarnings({"unchecked", "SameParameterValue"})
public class InstallArgs {
  private final String chartName;
  private final String releaseName;
  private final String namespace;
  private final Map<String, Object> valueOverrides;

  InstallArgs(
      String chartName, String releaseName, String namespace, Map<String, Object> valueOverrides) {
    this.chartName = chartName;
    this.releaseName = releaseName;
    this.namespace = namespace;
    this.valueOverrides = valueOverrides;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof InstallArgs) == false) {
      return false;
    }
    InstallArgs rhs = ((InstallArgs) other);
    if (!StringUtils.equals(this.chartName, rhs.chartName)) {
      return false;
    }
    if (!StringUtils.equals(this.releaseName, rhs.releaseName)) {
      return false;
    }
    if (!StringUtils.equals(this.namespace, rhs.namespace)) {
      return false;
    }
    return Objects.equals(this.valueOverrides, rhs.valueOverrides);
  }

  String getChartName() {
    return this.chartName;
  }

  String getReleaseName() {
    return this.releaseName;
  }

  String getNamespace() {
    return this.namespace;
  }

  Map<String, Object> getValueOverrides() {
    return this.valueOverrides;
  }
}

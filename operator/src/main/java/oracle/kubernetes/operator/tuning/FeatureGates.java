// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.tuning;

import java.util.Collection;

public interface FeatureGates {

  /**
   * Returns a collection of strings describing the enabled features.
   */
  Collection<String> getEnabledFeatures();

  /**
   * Returns true if the specified feature is enabled.
   * @param featureName the name of a feature
   */
  boolean isFeatureEnabled(String featureName);
}

// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Arrays;

import oracle.kubernetes.common.utils.SchemaConversionUtils;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainUpgradeConsistencyTest {

  @Test
  void upgradeUsesActualObsoleteConditions() {
    assertThat(SchemaConversionUtils.OBSOLETE_CONDITION_TYPES, containsInAnyOrder(getObsoleteConditionTypes()));
  }

  private String[] getObsoleteConditionTypes() {
    return Arrays.stream(DomainConditionType.values())
          .filter(DomainConditionType::isObsolete)
          .map(DomainConditionType::toString)
          .toArray(String[]::new);
  }

  @Test
  void upgradeUsesActualSupportedFailureReasons() {
    assertThat(SchemaConversionUtils.SUPPORTED_FAILURE_REASONS, containsInAnyOrder(getSupportedConditionReasons()));
  }

  private String[] getSupportedConditionReasons() {
    return Arrays.stream(DomainFailureReason.values())
          .map(DomainFailureReason::toString)
          .toArray(String[]::new);
  }
}

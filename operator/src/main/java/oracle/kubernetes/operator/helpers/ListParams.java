// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;

public class ListParams {
  public String fieldSelector;
  public String continueToken = "";

  /* Version */
  public String labelSelector;
  public Integer limit = 500;

  public String getFieldSelector() {
    return fieldSelector;
  }

  public String[] getLabelSelectors() {
    return Optional.ofNullable(labelSelector).map(this::splitSelector).orElse(null);
  }

  private String[] splitSelector(String selector) {
    return selector.split(",");
  }

  public Integer getLimit() {
    return limit;
  }
}
